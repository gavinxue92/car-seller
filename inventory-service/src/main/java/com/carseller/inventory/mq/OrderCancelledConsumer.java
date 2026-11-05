package com.carseller.inventory.mq;

import com.carseller.common.mq.TagConstants;
import com.carseller.common.mq.TopicConstants;
import com.carseller.common.mq.event.OrderCancelledEvent;
import com.carseller.inventory.constant.InventoryConstants;
import com.carseller.inventory.service.InventoryService;
import com.carseller.inventory.vo.InventoryChangeResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 订单取消事件消费者：回补库存（{@code order-cancelled-topic}，Tag {@code ORDER_CANCELLED}）。
 *
 * <p>消费组固定为 {@link InventoryConstants#RESTORE_CONSUMER_GROUP}
 * （README 7.3 事件表：order-cancelled-topic → inventory-restore-group）。</p>
 *
 * <p>可靠性（README 7.4）：本方法<b>不捕获异常</b>，
 * 系统级失败（DB / Redis 不可用）交给 RocketMQ 重试直到 16 次后进死信队列；
 * 重复投递由服务层的 Redis SETNX + {@code inventory_deduct_records} 状态条件更新（0 行即 ACK）保证幂等。</p>
 *
 * @author car-seller
 */
@Component
@RocketMQMessageListener(
        topic = TopicConstants.ORDER_CANCELLED_TOPIC,
        selectorExpression = TagConstants.ORDER_CANCELLED,
        consumerGroup = InventoryConstants.RESTORE_CONSUMER_GROUP)
public class OrderCancelledConsumer implements RocketMQListener<OrderCancelledEvent> {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledConsumer.class);

    private final InventoryService inventoryService;

    public OrderCancelledConsumer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    public void onMessage(OrderCancelledEvent event) {
        if (event == null || event.getOrderId() == null) {
            // 消息体非法属不可恢复错误，重试无意义，记录后丢弃
            log.warn("收到无效的订单取消事件，直接丢弃: {}", event);
            return;
        }
        Long orderId = event.getOrderId();
        log.info("收到订单取消事件，开始回补库存 orderId={}, reason={}", orderId, event.getReason());
        InventoryChangeResult result = inventoryService.restore(orderId);
        log.info("库存回补流程完成 orderId={}, changed={}, message={}",
                orderId, result.getChanged(), result.getMessage());
    }
}
