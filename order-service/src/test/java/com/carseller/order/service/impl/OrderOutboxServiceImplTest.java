package com.carseller.order.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carseller.common.mq.TagConstants;
import com.carseller.common.mq.TopicConstants;
import com.carseller.common.mq.event.OrderCancelledEvent;
import com.carseller.common.mq.event.OrderCreatedEvent;
import com.carseller.order.constant.OrderConstants;
import com.carseller.order.entity.Order;
import com.carseller.order.entity.OrderItem;
import com.carseller.order.entity.OrderMessageOutbox;
import com.carseller.order.mapper.OrderMessageOutboxMapper;
import com.carseller.order.mq.OrderEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link OrderOutboxServiceImpl} 单元测试：本地消息表登记 / 立即投递 / 失败补偿。
 *
 * <p>覆盖的契约（README 7.1 ③ / 7.4）：</p>
 * <ul>
 *     <li>登记与订单状态变更同事务：{@code PENDING} + {@code messageKey=orderId} + JSON 快照；</li>
 *     <li>唯一键 {@code uk_order_tag} 命中即视为「已登记」，重复登记返回 {@code false}（一单一条）；</li>
 *     <li>立即投递失败重试 {@code OUTBOX_MAX_SEND_ATTEMPTS} 次后置 {@code FAILED} 等待补偿；</li>
 *     <li>补偿扫描按快照重发并回写 {@code SENT}。</li>
 * </ul>
 *
 * @author car-seller
 */
@ExtendWith(MockitoExtension.class)
class OrderOutboxServiceImplTest {

    private static final Long ORDER_ID = 88L;

    private static final Long USER_ID = 7L;

    private static final Long OUTBOX_ID = 501L;

    private static final Long PRODUCT_ID = 1L;

    private static final String CANCEL_REASON = "TIMEOUT";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OrderMessageOutboxMapper outboxMapper;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    private OrderOutboxServiceImpl orderOutboxService;

    @BeforeEach
    void setUp() {
        orderOutboxService = new OrderOutboxServiceImpl(outboxMapper, orderEventPublisher, objectMapper);
    }

    @Test
    @DisplayName("登记订单创建事件：PENDING + topic/tag/key/JSON 快照一次写入")
    void enqueueOrderCreatedShouldInsertPendingRecord() {
        when(outboxMapper.insert(any(OrderMessageOutbox.class))).thenReturn(1);

        orderOutboxService.enqueueOrderCreated(buildOrder(), List.of(buildItem(PRODUCT_ID, 2)));

        ArgumentCaptor<OrderMessageOutbox> captor = ArgumentCaptor.forClass(OrderMessageOutbox.class);
        verify(outboxMapper).insert(captor.capture());
        OrderMessageOutbox saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(saved.getTopic()).isEqualTo(TopicConstants.ORDER_CREATED_TOPIC);
        assertThat(saved.getTag()).isEqualTo(TagConstants.ORDER_CREATED);
        assertThat(saved.getMessageKey()).isEqualTo(String.valueOf(ORDER_ID));
        assertThat(saved.getStatus()).isEqualTo(OrderConstants.OUTBOX_STATUS_PENDING);
        assertThat(saved.getRetryCount()).isZero();
        // 快照必须能还原成事件（补偿重发依赖快照，不回查订单表）
        assertThat(saved.getPayload()).contains("\"orderId\":88").contains("\"productId\":1");
    }

    @Test
    @DisplayName("登记取消事件：唯一键冲突说明已登记过，返回 false 且不抛异常")
    void enqueueOrderCancelledShouldReturnFalseOnDuplicateKey() {
        when(outboxMapper.insert(any(OrderMessageOutbox.class)))
                .thenThrow(new DuplicateKeyException("uk_order_tag"));

        assertThat(orderOutboxService.enqueueOrderCancelled(ORDER_ID, USER_ID, CANCEL_REASON)).isFalse();
    }

    @Test
    @DisplayName("立即投递：本地消息不存在时返回 false 且不发送")
    void dispatchShouldReturnFalseWhenRecordMissing() {
        when(outboxMapper.selectOne(any())).thenReturn(null);

        assertThat(orderOutboxService.dispatch(ORDER_ID, TagConstants.ORDER_CREATED)).isFalse();

        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    @DisplayName("立即投递：已发送过的记录不重复投递")
    void dispatchShouldSkipAlreadySentRecord() {
        when(outboxMapper.selectOne(any()))
                .thenReturn(buildOutbox(OrderConstants.OUTBOX_STATUS_SENT, TagConstants.ORDER_CANCELLED));

        assertThat(orderOutboxService.dispatch(ORDER_ID, TagConstants.ORDER_CANCELLED)).isTrue();

        verifyNoInteractions(orderEventPublisher);
        verify(outboxMapper, never()).markSent(anyLong(), anyString());
    }

    @Test
    @DisplayName("立即投递：发送成功则回写 SENT")
    void dispatchShouldMarkSentWhenPublisherSucceeds() {
        when(outboxMapper.selectOne(any()))
                .thenReturn(buildOutbox(OrderConstants.OUTBOX_STATUS_PENDING, TagConstants.ORDER_CREATED));
        when(orderEventPublisher.send(eq(TopicConstants.ORDER_CREATED_TOPIC), eq(TagConstants.ORDER_CREATED),
                eq(ORDER_ID), any())).thenReturn(true);
        when(outboxMapper.markSent(OUTBOX_ID, OrderConstants.OUTBOX_STATUS_SENT)).thenReturn(1);

        assertThat(orderOutboxService.dispatch(ORDER_ID, TagConstants.ORDER_CREATED)).isTrue();

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(orderEventPublisher).send(eq(TopicConstants.ORDER_CREATED_TOPIC), eq(TagConstants.ORDER_CREATED),
                eq(ORDER_ID), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isInstanceOf(OrderCreatedEvent.class);
        verify(outboxMapper).markSent(OUTBOX_ID, OrderConstants.OUTBOX_STATUS_SENT);
    }

    @Test
    @DisplayName("立即投递：连续失败 3 次后置 FAILED 并登记下次重发时间")
    void dispatchShouldMarkFailedAfterMaxAttempts() {
        OrderMessageOutbox outbox = buildOutbox(OrderConstants.OUTBOX_STATUS_PENDING, TagConstants.ORDER_CANCELLED);
        // 首次投递：此前没有任何失败记录，累计尝试次数从 0 开始
        outbox.setRetryCount(0);
        when(outboxMapper.selectOne(any())).thenReturn(outbox);
        when(orderEventPublisher.send(anyString(), anyString(), anyLong(), any())).thenReturn(false);
        when(outboxMapper.markFailed(anyLong(), anyString(), anyInt(), any(), anyString(), anyString()))
                .thenReturn(1);

        assertThat(orderOutboxService.dispatch(ORDER_ID, TagConstants.ORDER_CANCELLED)).isFalse();

        verify(orderEventPublisher, times(OrderConstants.OUTBOX_MAX_SEND_ATTEMPTS))
                .send(anyString(), anyString(), anyLong(), any());
        verify(outboxMapper).markFailed(eq(OUTBOX_ID), eq(OrderConstants.OUTBOX_STATUS_FAILED),
                eq(OrderConstants.OUTBOX_MAX_SEND_ATTEMPTS), any(LocalDateTime.class), anyString(),
                eq(OrderConstants.OUTBOX_STATUS_SENT));
        verify(outboxMapper, never()).markSent(anyLong(), anyString());
    }

    @Test
    @DisplayName("补偿扫描：按快照重发 FAILED 记录并回写 SENT")
    void compensateShouldResendDueFailedRecords() {
        Page<OrderMessageOutbox> page = new Page<>(1, 100L);
        page.setRecords(List.of(buildOutbox(OrderConstants.OUTBOX_STATUS_FAILED, TagConstants.ORDER_CANCELLED)));
        doReturn(page).when(outboxMapper).selectPage(any(), any());
        when(orderEventPublisher.send(eq(TopicConstants.ORDER_CANCELLED_TOPIC), eq(TagConstants.ORDER_CANCELLED),
                eq(ORDER_ID), any())).thenReturn(true);
        when(outboxMapper.markSent(OUTBOX_ID, OrderConstants.OUTBOX_STATUS_SENT)).thenReturn(1);

        assertThat(orderOutboxService.compensate()).isEqualTo(1);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(orderEventPublisher).send(eq(TopicConstants.ORDER_CANCELLED_TOPIC), eq(TagConstants.ORDER_CANCELLED),
                eq(ORDER_ID), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).isInstanceOf(OrderCancelledEvent.class);
        verify(outboxMapper).markSent(OUTBOX_ID, OrderConstants.OUTBOX_STATUS_SENT);
    }

    @Test
    @DisplayName("补偿扫描：重发仍失败则累计尝试次数并重新登记退避时间")
    void compensateShouldRescheduleWhenResendFails() {
        Page<OrderMessageOutbox> page = new Page<>(1, 100L);
        page.setRecords(List.of(buildOutbox(OrderConstants.OUTBOX_STATUS_FAILED, TagConstants.ORDER_CANCELLED)));
        doReturn(page).when(outboxMapper).selectPage(any(), any());
        when(orderEventPublisher.send(anyString(), anyString(), anyLong(), any())).thenReturn(false);
        when(outboxMapper.markFailed(anyLong(), anyString(), anyInt(), any(), anyString(), anyString()))
                .thenReturn(1);

        assertThat(orderOutboxService.compensate()).isZero();

        verify(orderEventPublisher, times(1)).send(anyString(), anyString(), anyLong(), any());
        // 原 retryCount=3，补偿阶段每次只尝试 1 次 → 4
        verify(outboxMapper).markFailed(eq(OUTBOX_ID), eq(OrderConstants.OUTBOX_STATUS_FAILED), eq(4),
                any(LocalDateTime.class), anyString(), eq(OrderConstants.OUTBOX_STATUS_SENT));
    }

    @Test
    @DisplayName("补偿扫描：无到期待发记录时直接返回 0")
    void compensateShouldDoNothingWhenNoCandidate() {
        Page<OrderMessageOutbox> page = new Page<>(1, 100L);
        page.setRecords(List.of());
        doReturn(page).when(outboxMapper).selectPage(any(), any());

        assertThat(orderOutboxService.compensate()).isZero();

        verifyNoInteractions(orderEventPublisher);
    }

    /**
     * 构造一条本地消息记录：payload 使用真实序列化结果，保证补偿重发能还原事件。
     */
    private OrderMessageOutbox buildOutbox(String status, String tag) {
        boolean created = TagConstants.ORDER_CREATED.equals(tag);
        Object event = created
                ? new OrderCreatedEvent(ORDER_ID, USER_ID, new BigDecimal("199.00"),
                        List.of(new OrderCreatedEvent.Item(PRODUCT_ID, 2)), 1762308000000L)
                : new OrderCancelledEvent(ORDER_ID, USER_ID, CANCEL_REASON, 1762308000000L);
        OrderMessageOutbox outbox = new OrderMessageOutbox();
        outbox.setId(OUTBOX_ID);
        outbox.setOrderId(ORDER_ID);
        outbox.setTopic(created ? TopicConstants.ORDER_CREATED_TOPIC : TopicConstants.ORDER_CANCELLED_TOPIC);
        outbox.setTag(tag);
        outbox.setMessageKey(String.valueOf(ORDER_ID));
        outbox.setPayload(writeJson(event));
        outbox.setStatus(status);
        outbox.setRetryCount(3);
        outbox.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        return outbox;
    }

    private String writeJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("测试数据构造失败", e);
        }
    }

    private Order buildOrder() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setUserId(USER_ID);
        order.setTotalPrice(new BigDecimal("199.00"));
        return order;
    }

    private OrderItem buildItem(Long productId, Integer quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }
}
