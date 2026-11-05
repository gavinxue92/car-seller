package com.carseller.order.mq;

import com.carseller.common.mq.TagConstants;
import com.carseller.common.mq.TopicConstants;
import com.carseller.common.mq.event.OrderPaidEvent;
import com.carseller.order.constant.OrderConstants;
import com.carseller.order.service.OrderStateService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 支付成功事件消费者：{@code order-paid-topic}，Tag {@code ORDER_PAID}。
 *
 * <p>消费组固定为 {@link OrderConstants#ORDER_PAID_CONSUMER_GROUP}（README 7.3，禁止改名）。</p>
 *
 * <p><b>幂等与失败策略（README 7.4）</b>：</p>
 * <ul>
 *     <li>状态推进是条件更新 {@code status=PENDING → PAID}，重复投递影响 0 行，
 *         视为已处理并正常 ACK，<b>不抛异常</b>（否则会被无限重试）；</li>
 *     <li>数据库异常等系统级失败不捕获，抛出让 RocketMQ 重试，耗尽进 {@code %DLQ%}；</li>
 *     <li>消费端<b>不做</b>任何反向 Feign 调用，单条处理远小于 1s。</li>
 * </ul>
 *
 * @author car-seller
 */
@Component
@RocketMQMessageListener(
        topic = TopicConstants.ORDER_PAID_TOPIC,
        selectorExpression = TagConstants.ORDER_PAID,
        consumerGroup = OrderConstants.ORDER_PAID_CONSUMER_GROUP)
public class OrderPaidConsumer implements RocketMQListener<OrderPaidEvent> {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidConsumer.class);

    private final OrderStateService orderStateService;

    public OrderPaidConsumer(OrderStateService orderStateService) {
        this.orderStateService = orderStateService;
    }

    @Override
    public void onMessage(OrderPaidEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("收到无效的支付成功事件，直接丢弃: {}", event);
            return;
        }
        log.info("收到支付成功事件: orderId={}, payNo={}, amount={}, paymentMethod={}",
                event.getOrderId(), event.getPayNo(), event.getAmount(), event.getPaymentMethod());
        boolean updated = orderStateService.markPaid(event);
        if (!updated) {
            log.info("支付成功事件未产生状态变更（已处理或订单已非待支付），正常 ACK: orderId={}, payNo={}",
                    event.getOrderId(), event.getPayNo());
        }
    }
}
