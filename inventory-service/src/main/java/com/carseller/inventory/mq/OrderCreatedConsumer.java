package com.carseller.inventory.mq;

import com.carseller.common.exception.BusinessException;
import com.carseller.common.mq.TagConstants;
import com.carseller.common.mq.TopicConstants;
import com.carseller.common.mq.event.InventoryDeductResultEvent;
import com.carseller.common.mq.event.OrderCreatedEvent;
import com.carseller.common.result.ResultCode;
import com.carseller.inventory.constant.InventoryConstants;
import com.carseller.inventory.dto.InventoryDeductItem;
import com.carseller.inventory.dto.InventoryDeductRequest;
import com.carseller.inventory.service.InventoryService;
import com.carseller.inventory.vo.InventoryChangeResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 订单创建事件消费者：幂等扣减库存（{@code order-created-topic}，Tag {@code ORDER_CREATED}）。
 *
 * <p>消费组固定为 {@link InventoryConstants#DEDUCT_CONSUMER_GROUP}（README 7.3 事件表）。</p>
 *
 * <p>失败处理策略（README 7.4）：</p>
 * <ul>
 *     <li><b>确定性业务失败</b>（库存不足 50001）：回发 {@code DEDUCT_FAILED} 结果事件后 ACK，
 *         因为重试也不会变成功，必须由 order-service 依据失败结果关单；</li>
 *     <li><b>重复扣减</b>（50002，消息重投命中 Redis 幂等键）：说明此前已扣减成功，
 *         补发 {@code DEDUCT_SUCCESS} 帮助 order-service 收敛，然后 ACK；</li>
 *     <li><b>系统级失败</b>（DB / Redis / MQ 发送失败）：不捕获，抛异常让 RocketMQ 重试。</li>
 * </ul>
 *
 * @author car-seller
 */
@Component
@RocketMQMessageListener(
        topic = TopicConstants.ORDER_CREATED_TOPIC,
        selectorExpression = TagConstants.ORDER_CREATED,
        consumerGroup = InventoryConstants.DEDUCT_CONSUMER_GROUP)
public class OrderCreatedConsumer implements RocketMQListener<OrderCreatedEvent> {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    private final InventoryService inventoryService;
    private final InventoryEventPublisher eventPublisher;

    public OrderCreatedConsumer(InventoryService inventoryService, InventoryEventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void onMessage(OrderCreatedEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("收到无效的订单创建事件，直接丢弃: {}", event);
            return;
        }
        Long orderId = event.getOrderId();
        log.info("收到订单创建事件，开始扣减库存 orderId={}, items={}", orderId, event.getItems());
        try {
            InventoryChangeResult result = inventoryService.deduct(toRequest(event));
            eventPublisher.publishDeductResult(deductedEvent(orderId, result));
            log.info("库存扣减流程完成 orderId={}, details={}", orderId, result.getItems());
        } catch (BusinessException ex) {
            handleBusinessFailure(orderId, event, ex);
        }
    }

    private void handleBusinessFailure(Long orderId, OrderCreatedEvent event, BusinessException ex) {
        if (ResultCode.INVENTORY_DUPLICATE_DEDUCT.getCode().equals(ex.getCode())) {
            log.info("订单已扣减库存（幂等命中），补发成功结果 orderId={}", orderId);
            eventPublisher.publishDeductResult(
                    snapshotEvent(orderId, event.getItems(), true, "库存扣减成功（幂等命中）"));
            return;
        }
        log.warn("库存扣减失败，回发失败结果 orderId={}, reason={}", orderId, ex.getMessage());
        eventPublisher.publishDeductResult(snapshotEvent(orderId, event.getItems(), false, ex.getMessage()));
    }

    private InventoryDeductRequest toRequest(OrderCreatedEvent event) {
        List<InventoryDeductItem> items = new ArrayList<>();
        if (event.getItems() != null) {
            for (OrderCreatedEvent.Item item : event.getItems()) {
                items.add(new InventoryDeductItem(item.getProductId(), item.getQuantity()));
            }
        }
        InventoryDeductRequest request = new InventoryDeductRequest();
        request.setOrderId(event.getOrderId());
        request.setItems(items);
        return request;
    }

    /**
     * 由扣减结果构建成功事件：before / after 取自事务内行锁快照，最准确。
     */
    private InventoryDeductResultEvent deductedEvent(Long orderId, InventoryChangeResult result) {
        List<InventoryDeductResultEvent.Item> items = new ArrayList<>();
        if (result.getItems() != null) {
            for (InventoryChangeResult.Item item : result.getItems()) {
                items.add(new InventoryDeductResultEvent.Item(item.getProductId(), item.getQuantity(),
                        item.getBefore(), item.getAfter()));
            }
        }
        return new InventoryDeductResultEvent(orderId, Boolean.TRUE, result.getMessage(), items,
                System.currentTimeMillis());
    }

    /**
     * 无扣减结果可用时（幂等命中 / 库存不足），用当前库存快照补齐 before，after 仅成功场景填充。
     */
    private InventoryDeductResultEvent snapshotEvent(Long orderId, List<OrderCreatedEvent.Item> orderItems,
                                                     boolean success, String reason) {
        List<Long> productIds = new ArrayList<>();
        List<InventoryDeductResultEvent.Item> items = new ArrayList<>();
        if (orderItems != null) {
            for (OrderCreatedEvent.Item item : orderItems) {
                productIds.add(item.getProductId());
            }
        }
        Map<Long, Integer> stock = inventoryService.currentStock(productIds);
        if (orderItems != null) {
            for (OrderCreatedEvent.Item item : orderItems) {
                Integer current = stock.get(item.getProductId());
                items.add(new InventoryDeductResultEvent.Item(item.getProductId(), item.getQuantity(), current,
                        success ? current : null));
            }
        }
        return new InventoryDeductResultEvent(orderId, success, reason, items, System.currentTimeMillis());
    }
}
