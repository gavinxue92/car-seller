package com.carseller.order.mq;

import com.carseller.common.mq.TagConstants;
import com.carseller.common.mq.TopicConstants;
import com.carseller.common.mq.event.InventoryDeductResultEvent;
import com.carseller.order.constant.OrderConstants;
import com.carseller.order.service.OrderStateService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 库存扣减结果消费者：{@code inventory-deduct-result-topic}，
 * Tag {@code DEDUCT_SUCCESS} / {@code DEDUCT_FAILED}。
 *
 * <p>消费组固定为 {@link OrderConstants#DEDUCT_RESULT_CONSUMER_GROUP}（README 7.3，禁止改名）。</p>
 *
 * <p><b>状态机语义（README 7.1 ⑦ / 领域契约 3）</b>：</p>
 * <ul>
 *     <li>{@code DEDUCT_SUCCESS}：订单保持 {@code PENDING} 等待支付，只记录日志，不回写任何状态；</li>
 *     <li>{@code DEDUCT_FAILED}：订单 {@code PENDING → CANCELLED}（库存不足），
 *         此时库存<b>没有</b>被扣减成功，因此<b>禁止</b>发送 {@code order-cancelled-topic}，
 *         否则 inventory-service 会回补出多余的库存；</li>
 *     <li>条件更新影响 0 行（订单已支付/已取消）→ 直接 ACK，不抛异常、不依赖 MQ 重试。</li>
 * </ul>
 *
 * @author car-seller
 */
@Component
@RocketMQMessageListener(
        topic = TopicConstants.INVENTORY_DEDUCT_RESULT_TOPIC,
        selectorExpression = TagConstants.DEDUCT_SUCCESS + " || " + TagConstants.DEDUCT_FAILED,
        consumerGroup = OrderConstants.DEDUCT_RESULT_CONSUMER_GROUP)
public class InventoryDeductResultConsumer implements RocketMQListener<InventoryDeductResultEvent> {

    private static final Logger log = LoggerFactory.getLogger(InventoryDeductResultConsumer.class);

    private final OrderStateService orderStateService;

    public InventoryDeductResultConsumer(OrderStateService orderStateService) {
        this.orderStateService = orderStateService;
    }

    @Override
    public void onMessage(InventoryDeductResultEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("收到无效的库存扣减结果事件，直接丢弃: {}", event);
            return;
        }
        if (Boolean.TRUE.equals(event.getSuccess())) {
            // 扣减成功：订单仍是待支付，无需改状态；此日志用于对账（orderId → 已占库存）
            log.info("库存扣减成功，订单保持待支付: orderId={}, items={}", event.getOrderId(), event.getItems());
            return;
        }
        log.warn("库存扣减失败，准备关单: orderId={}, reason={}", event.getOrderId(), event.getReason());
        boolean cancelled = orderStateService.cancelOnDeductFailed(event);
        if (!cancelled) {
            log.info("扣减失败事件未触发关单（订单已非待支付），正常 ACK: orderId={}", event.getOrderId());
        }
    }
}
