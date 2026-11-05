package com.carseller.order.mq;

import com.carseller.order.constant.OrderConstants;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 订单域 MQ 事件发布器。
 *
 * <p><b>一致性策略（README 7.1 / 7.3）</b>：本地事务先落库，<b>提交成功之后</b>才发送消息
 * （{@code OrderTxService} 返回后调用），不做 "先发消息后落库"，
 * 因此不引入 RocketMQ 事务消息（半消息 + 回查）：
 * <ul>
 *   <li>消费端（inventory-service）以 {@code orderId} 做幂等扣减，重复投递天然安全；</li>
 *   <li>发送失败只造成"消息丢失"，由 send 失败重试 + 消费端按订单补偿扫描兜底，
 *       不产生"订单已扣库存但订单不存在"的脏扣减（事务消息反而会有这个风险面）；</li>
 *   <li>发送失败与「消息未登记」的窗口由本地消息表 {@code order_message_outbox} + 补偿扫描兜底（README 7.4）。</li>
 * </ul>
 *
 * <p>发送规则：destination = {@code topic:tag}，消息 Key = {@code orderId}，
 * 保证同一订单的消息进入同一队列，消费侧顺序与幂等都更简单。</p>
 *
 * @author car-seller
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RocketMQTemplate rocketMQTemplate;

    public OrderEventPublisher(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 单次同步发送消息（<b>不重试</b>：重试与补偿由 {@code OrderOutboxService} 统一负责）。
     *
     * <p>本方法<b>不抛异常</b>：调用方需要拿到确定的成功/失败结果来回写本地消息状态，
     * 异常向上抛出会导致消息状态无法回写。</p>
     *
     * @param topic   Topic 常量
     * @param tag     Tag 常量
     * @param orderId 业务主键，作为消息 Key
     * @param payload 事件载荷
     * @return {@code true} 表示本次发送成功
     */
    public boolean send(String topic, String tag, Long orderId, Object payload) {
        String destination = topic + ":" + tag;
        Message<Object> message = MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, String.valueOf(orderId))
                .build();
        try {
            SendResult result = rocketMQTemplate.syncSend(destination, message, OrderConstants.SEND_TIMEOUT_MILLIS);
            log.info("消息发送成功: destination={}, orderId={}, msgId={}, sendStatus={}",
                    destination, orderId, result.getMsgId(), result.getSendStatus());
            return true;
        } catch (Exception e) {
            // NameServer 不可达 / Broker 抖动 / 发送超时：按失败返回，由 outbox 重试与补偿扫描兜底
            log.error("消息发送失败: destination={}, orderId={}, reason={}",
                    destination, orderId, e.getMessage());
            return false;
        }
    }
}
