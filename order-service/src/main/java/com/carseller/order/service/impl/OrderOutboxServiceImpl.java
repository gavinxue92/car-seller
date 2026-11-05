package com.carseller.order.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carseller.common.exception.BusinessException;
import com.carseller.common.mq.TagConstants;
import com.carseller.common.mq.TopicConstants;
import com.carseller.common.mq.event.OrderCancelledEvent;
import com.carseller.common.mq.event.OrderCreatedEvent;
import com.carseller.common.result.ResultCode;
import com.carseller.order.constant.OrderConstants;
import com.carseller.order.entity.Order;
import com.carseller.order.entity.OrderItem;
import com.carseller.order.entity.OrderMessageOutbox;
import com.carseller.order.mapper.OrderMessageOutboxMapper;
import com.carseller.order.mq.OrderEventPublisher;
import com.carseller.order.service.OrderOutboxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地消息表实现（{@code order_db.order_message_outbox}，README 7.1 ③ / 7.4）。
 *
 * <p><b>发送三段式</b>：</p>
 * <ol>
 *     <li>① 业务事务内登记消息（{@code enqueueXxx}）：与订单状态变更同生共死，
 *         不存在「订单已落库、消息没登记」的窗口；</li>
 *     <li>② 事务提交后立即投递（{@code dispatch}）：失败按 200ms / 400ms 指数退避重试 3 次；</li>
 *     <li>③ 定时补偿（{@code compensate}）：扫描 {@code PENDING / FAILED} 且
 *         {@code next_retry_at <= now()} 的记录重发，退避 1 → 2 → 4 … 分钟（上限 30 分钟）。</li>
 * </ol>
 *
 * <p><b>幂等</b>：重复发送不会重复扣/补库存（消费端以 {@code orderId} 幂等）；
 * 状态回写使用条件更新，并发重复标记只有一次生效。</p>
 *
 * @author car-seller
 */
@Service
public class OrderOutboxServiceImpl implements OrderOutboxService {

    private static final Logger log = LoggerFactory.getLogger(OrderOutboxServiceImpl.class);

    /** 发送失败写入 last_error 的固定文案（发送器只返回布尔值，异常不向上抛） */
    private static final String SEND_FAILED_MESSAGE = "MQ 发送失败，等待补偿重发";

    private final OrderMessageOutboxMapper outboxMapper;

    private final OrderEventPublisher orderEventPublisher;

    private final ObjectMapper objectMapper;

    public OrderOutboxServiceImpl(OrderMessageOutboxMapper outboxMapper,
                                  OrderEventPublisher orderEventPublisher,
                                  ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.orderEventPublisher = orderEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enqueueOrderCreated(Order order, List<OrderItem> orderItems) {
        List<OrderCreatedEvent.Item> eventItems = new ArrayList<>();
        if (orderItems != null) {
            for (OrderItem item : orderItems) {
                eventItems.add(new OrderCreatedEvent.Item(item.getProductId(), item.getQuantity()));
            }
        }
        OrderCreatedEvent event = new OrderCreatedEvent(order.getId(), order.getUserId(), order.getTotalPrice(),
                eventItems, System.currentTimeMillis());
        savePending(order.getId(), TopicConstants.ORDER_CREATED_TOPIC, TagConstants.ORDER_CREATED, event);
    }

    @Override
    public boolean enqueueOrderCancelled(Long orderId, Long userId, String reason) {
        OrderCancelledEvent event = new OrderCancelledEvent(orderId, userId, reason, System.currentTimeMillis());
        try {
            savePending(orderId, TopicConstants.ORDER_CANCELLED_TOPIC, TagConstants.ORDER_CANCELLED, event);
            return true;
        } catch (DuplicateKeyException e) {
            // 唯一键 uk_order_tag 命中：同一订单的取消消息已登记过，绝不重复登记 → 保证「只发一次」
            log.info("该订单已存在取消消息记录，跳过重复登记: orderId={}", orderId);
            return false;
        }
    }

    @Override
    public boolean dispatch(Long orderId, String tag) {
        OrderMessageOutbox outbox = outboxMapper.selectOne(Wrappers.<OrderMessageOutbox>lambdaQuery()
                .eq(OrderMessageOutbox::getOrderId, orderId)
                .eq(OrderMessageOutbox::getTag, tag));
        if (outbox == null) {
            log.error("未找到本地消息记录，本次消息未发送，需人工核查: orderId={}, tag={}", orderId, tag);
            return false;
        }
        if (OrderConstants.OUTBOX_STATUS_SENT.equals(outbox.getStatus())) {
            log.debug("本地消息此前已发送，跳过重复投递: orderId={}, tag={}", orderId, tag);
            return true;
        }
        if (trySendWithRetry(outbox)) {
            markSent(outbox.getId());
            return true;
        }
        markFailed(outbox, OrderConstants.OUTBOX_MAX_SEND_ATTEMPTS);
        log.error("消息立即投递失败，已登记等待补偿扫描: orderId={}, tag={}", orderId, tag);
        return false;
    }

    @Override
    public int compensate() {
        Page<OrderMessageOutbox> page = outboxMapper.selectPage(
                new Page<>(1, OrderConstants.OUTBOX_COMPENSATE_BATCH_SIZE),
                Wrappers.<OrderMessageOutbox>lambdaQuery()
                        .in(OrderMessageOutbox::getStatus,
                                List.of(OrderConstants.OUTBOX_STATUS_PENDING, OrderConstants.OUTBOX_STATUS_FAILED))
                        .le(OrderMessageOutbox::getNextRetryAt, LocalDateTime.now())
                        .orderByAsc(OrderMessageOutbox::getId));
        List<OrderMessageOutbox> candidates = page.getRecords() == null ? List.of() : page.getRecords();
        if (candidates.isEmpty()) {
            log.debug("本地消息补偿扫描无待发送记录");
            return 0;
        }
        int resent = 0;
        for (OrderMessageOutbox outbox : candidates) {
            // 补偿阶段每次只尝试 1 次：重发节奏由 next_retry_at 的分钟级退避控制
            if (trySendOnce(outbox)) {
                markSent(outbox.getId());
                resent++;
                log.info("本地消息补偿发送成功: orderId={}, tag={}, retryCount={}",
                        outbox.getOrderId(), outbox.getTag(), outbox.getRetryCount());
            } else {
                markFailed(outbox, 1);
            }
        }
        log.info("本地消息补偿扫描结束: 候选={}, 成功={}", candidates.size(), resent);
        return resent;
    }

    /**
     * 登记一条待发送消息（调用方必须在业务事务内调用）。
     */
    private void savePending(Long orderId, String topic, String tag, Object event) {
        OrderMessageOutbox outbox = new OrderMessageOutbox();
        outbox.setOrderId(orderId);
        outbox.setTopic(topic);
        outbox.setTag(tag);
        outbox.setMessageKey(String.valueOf(orderId));
        outbox.setPayload(toJson(event));
        outbox.setStatus(OrderConstants.OUTBOX_STATUS_PENDING);
        outbox.setRetryCount(0);
        outbox.setNextRetryAt(LocalDateTime.now());
        int rows = outboxMapper.insert(outbox);
        if (rows != 1) {
            throw new BusinessException(ResultCode.FAILURE, "本地消息登记失败，orderId=" + orderId);
        }
        log.info("本地消息已登记: orderId={}, destination={}:{}, outboxId={}", orderId, topic, tag, outbox.getId());
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.FAILURE, "本地消息序列化失败，orderId 无法登记");
        }
    }

    /**
     * 立即投递阶段的重试：共 {@code OUTBOX_MAX_SEND_ATTEMPTS} 次，间隔 200ms → 400ms。
     */
    private boolean trySendWithRetry(OrderMessageOutbox outbox) {
        for (int attempt = 1; attempt <= OrderConstants.OUTBOX_MAX_SEND_ATTEMPTS; attempt++) {
            if (trySendOnce(outbox)) {
                return true;
            }
            if (attempt < OrderConstants.OUTBOX_MAX_SEND_ATTEMPTS) {
                sleepBeforeRetry(attempt);
            }
        }
        return false;
    }

    /**
     * 单次投递：从消息快照还原事件（不回查订单表，避免后续订单状态变化导致消息体漂移）。
     */
    private boolean trySendOnce(OrderMessageOutbox outbox) {
        Object payload;
        try {
            payload = parsePayload(outbox.getTag(), outbox.getPayload());
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("本地消息载荷无法还原，本条消息无法发送，需人工处理: orderId={}, tag={}",
                    outbox.getOrderId(), outbox.getTag(), e);
            return false;
        }
        return orderEventPublisher.send(outbox.getTopic(), outbox.getTag(), outbox.getOrderId(), payload);
    }

    private Object parsePayload(String tag, String payload) throws JsonProcessingException {
        if (TagConstants.ORDER_CREATED.equals(tag)) {
            return objectMapper.readValue(payload, OrderCreatedEvent.class);
        }
        if (TagConstants.ORDER_CANCELLED.equals(tag)) {
            return objectMapper.readValue(payload, OrderCancelledEvent.class);
        }
        throw new IllegalArgumentException("未知的消息 Tag: " + tag);
    }

    private void markSent(Long outboxId) {
        int rows = outboxMapper.markSent(outboxId, OrderConstants.OUTBOX_STATUS_SENT);
        if (rows == 0) {
            log.debug("本地消息状态已被并发标记为已发送: outboxId={}", outboxId);
        }
    }

    /**
     * 标记发送失败并计算下一次补偿时间（指数退避）。
     *
     * @param outbox        消息记录
     * @param addedAttempts 本次新增的尝试次数
     */
    private void markFailed(OrderMessageOutbox outbox, int addedAttempts) {
        int baseRetryCount = outbox.getRetryCount() == null ? 0 : outbox.getRetryCount();
        int retryCount = baseRetryCount + addedAttempts;
        LocalDateTime nextRetryAt = nextRetryAt(retryCount);
        int rows = outboxMapper.markFailed(outbox.getId(), OrderConstants.OUTBOX_STATUS_FAILED, retryCount,
                nextRetryAt, SEND_FAILED_MESSAGE, OrderConstants.OUTBOX_STATUS_SENT);
        log.warn("本地消息发送失败，等待补偿: outboxId={}, orderId={}, tag={}, retryCount={}, nextRetryAt={}, affectedRows={}",
                outbox.getId(), outbox.getOrderId(), outbox.getTag(), retryCount, nextRetryAt, rows);
    }

    /**
     * 指数退避：1 → 2 → 4 … 分钟，上限 {@code OUTBOX_RETRY_BACKOFF_MAX_MINUTES}。
     */
    private static LocalDateTime nextRetryAt(int retryCount) {
        long factor = 1L << Math.min(Math.max(retryCount - 1, 0), 5);
        long minutes = Math.min(OrderConstants.OUTBOX_RETRY_BACKOFF_MINUTES * factor,
                OrderConstants.OUTBOX_RETRY_BACKOFF_MAX_MINUTES);
        return LocalDateTime.now().plusMinutes(minutes);
    }

    private static void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(OrderConstants.OUTBOX_SEND_BACKOFF_MILLIS * (1L << (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("消息发送重试等待被中断, attempt={}", attempt);
        }
    }
}
