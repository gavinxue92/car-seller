package com.carseller.inventory.mq;

import com.carseller.common.mq.TagConstants;
import com.carseller.common.mq.TopicConstants;
import com.carseller.common.mq.event.InventoryDeductResultEvent;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 库存域 MQ 事件发布器。
 *
 * <p>发送规则与 {@code OrderEventPublisher} 保持一致（README 7.3）：
 * destination = {@code topic:tag}，消息 Key = {@code orderId}，
 * 同一订单的消息落到同一队列，order-service 消费侧顺序性与幂等都更简单。</p>
 *
 * <p>库存扣减是异步链路的中间环节，结果事件必须送达，因此发送失败按指数退避重试，
 * 超过上限后抛出异常由 RocketMQ 消费重试兜底（扣减端幂等，重投无副作用）。</p>
 *
 * @author car-seller
 */
@Component
public class InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublisher.class);

    /** 单条消息最大发送尝试次数 */
    private static final int MAX_ATTEMPTS = 3;

    /** 首次重试等待毫秒数，之后按 2 倍指数退避 */
    private static final long BASE_BACKOFF_MILLIS = 200L;

    /** 单条消息发送超时（毫秒），与 application.yml 的 send-message-timeout 保持一致 */
    private static final long SEND_TIMEOUT_MILLIS = 3000L;

    private final RocketMQTemplate rocketMQTemplate;

    public InventoryEventPublisher(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 发布库存扣减结果事件（{@code inventory-deduct-result-topic}）。
     *
     * <p>Tag：成功 {@code DEDUCT_SUCCESS}、失败 {@code DEDUCT_FAILED}。</p>
     *
     * @param event 结果事件，{@code orderId} 必填
     * @throws IllegalStateException 重试耗尽仍未发送成功
     */
    public void publishDeductResult(InventoryDeductResultEvent event) {
        String tag = Boolean.TRUE.equals(event.getSuccess())
                ? TagConstants.DEDUCT_SUCCESS
                : TagConstants.DEDUCT_FAILED;
        String destination = TopicConstants.INVENTORY_DEDUCT_RESULT_TOPIC + ":" + tag;
        Message<InventoryDeductResultEvent> message = MessageBuilder.withPayload(event)
                .setHeader(RocketMQHeaders.KEYS, String.valueOf(event.getOrderId()))
                .build();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                SendResult result = rocketMQTemplate.syncSend(destination, message, SEND_TIMEOUT_MILLIS);
                log.info("库存扣减结果发送成功: destination={}, orderId={}, msgId={}, sendStatus={}",
                        destination, event.getOrderId(), result.getMsgId(), result.getSendStatus());
                return;
            } catch (Exception e) {
                log.warn("库存扣减结果发送失败，准备重试: destination={}, orderId={}, attempt={}/{}",
                        destination, event.getOrderId(), attempt, MAX_ATTEMPTS, e);
                if (attempt == MAX_ATTEMPTS) {
                    throw new IllegalStateException(
                            "库存扣减结果事件发送失败, orderId=" + event.getOrderId(), e);
                }
                backoff(attempt);
            }
        }
    }

    /**
     * 指数退避等待：200ms、400ms……
     */
    private static void backoff(int attempt) {
        try {
            Thread.sleep(BASE_BACKOFF_MILLIS * (1L << (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("库存结果事件发送重试等待被中断, attempt={}", attempt);
        }
    }
}
