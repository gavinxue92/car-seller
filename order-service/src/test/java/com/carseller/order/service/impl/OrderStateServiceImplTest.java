package com.carseller.order.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carseller.common.mq.TagConstants;
import com.carseller.common.mq.event.InventoryDeductResultEvent;
import com.carseller.common.mq.event.OrderPaidEvent;
import com.carseller.order.entity.Order;
import com.carseller.order.enums.OrderStatus;
import com.carseller.order.mapper.OrderMapper;
import com.carseller.order.service.OrderOutboxService;
import com.carseller.order.service.OrderTxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link OrderStateServiceImpl} 单元测试：MQ 消费推进订单状态 + 定时超时关单。
 *
 * <p>覆盖的领域契约（README 6.4 / 7.3）：</p>
 * <ul>
 *     <li>支付成功事件按 {@code orderId} 幂等：条件更新 0 行即视为已处理，正常 ACK 不抛异常；</li>
 *     <li>库存扣减失败关单<b>不</b>发 {@code order-cancelled-topic}（库存未扣成功，回补会多加库存）；</li>
 *     <li>超时关单对同一订单只登记/投递一次取消消息。</li>
 * </ul>
 *
 * @author car-seller
 */
@ExtendWith(MockitoExtension.class)
class OrderStateServiceImplTest {

    private static final Long ORDER_ID = 88L;

    private static final Long ANOTHER_ORDER_ID = 99L;

    private static final Long USER_ID = 7L;

    private static final String PAY_NO = "PAY20261105000001";

    private static final String TRADE_NO = "MOCK_TRADE_88237612";

    private static final String PAYMENT_METHOD = "ALIPAY";

    private static final String DEDUCT_FAILED_REASON = "商品[1]库存不足，可用 3，需要 5";

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderTxService orderTxService;

    @Mock
    private OrderOutboxService orderOutboxService;

    private OrderStateServiceImpl orderStateService;

    @BeforeEach
    void setUp() {
        orderStateService = new OrderStateServiceImpl(orderMapper, orderTxService, orderOutboxService);
    }

    @Test
    @DisplayName("支付成功：条件更新 PENDING→PAID 成功，返回 true 且不触碰 outbox")
    void markPaidShouldUpdateStatusWhenPending() {
        when(orderTxService.markOrderPaid(ORDER_ID, PAYMENT_METHOD)).thenReturn(true);

        boolean updated = orderStateService.markPaid(buildPaidEvent());

        assertThat(updated).isTrue();
        verify(orderTxService).markOrderPaid(ORDER_ID, PAYMENT_METHOD);
        verifyNoInteractions(orderOutboxService);
    }

    @Test
    @DisplayName("支付成功：重复投递（条件更新 0 行）按幂等 ACK，不抛异常")
    void markPaidShouldAckIdempotentlyWhenAlreadyPaid() {
        when(orderTxService.markOrderPaid(ORDER_ID, PAYMENT_METHOD)).thenReturn(false);

        boolean updated = orderStateService.markPaid(buildPaidEvent());

        assertThat(updated).isFalse();
        verify(orderTxService).markOrderPaid(ORDER_ID, PAYMENT_METHOD);
        verifyNoInteractions(orderOutboxService);
    }

    @Test
    @DisplayName("支付成功：缺少 orderId 的非法事件直接忽略，不访问数据库")
    void markPaidShouldIgnoreInvalidEvent() {
        assertThat(orderStateService.markPaid(new OrderPaidEvent())).isFalse();
        assertThat(orderStateService.markPaid(null)).isFalse();

        verifyNoInteractions(orderTxService, orderOutboxService);
    }

    @Test
    @DisplayName("库存扣减失败：订单关闭但绝不发送 order-cancelled-topic（禁止回补库存）")
    void cancelOnDeductFailedShouldNotSendCancelMessage() {
        when(orderTxService.cancelOnDeductFailed(ORDER_ID, DEDUCT_FAILED_REASON)).thenReturn(true);

        boolean cancelled = orderStateService.cancelOnDeductFailed(buildDeductFailedEvent());

        assertThat(cancelled).isTrue();
        verify(orderTxService).cancelOnDeductFailed(ORDER_ID, DEDUCT_FAILED_REASON);
        // 关键断言：库存未扣成功，若发取消消息会让 inventory-service 回补出不存在的库存
        verifyNoInteractions(orderOutboxService);
    }

    @Test
    @DisplayName("库存扣减失败：重复投递（条件更新 0 行）按幂等 ACK")
    void cancelOnDeductFailedShouldAckIdempotently() {
        when(orderTxService.cancelOnDeductFailed(ORDER_ID, DEDUCT_FAILED_REASON)).thenReturn(false);

        assertThat(orderStateService.cancelOnDeductFailed(buildDeductFailedEvent())).isFalse();

        verifyNoInteractions(orderOutboxService);
    }

    @Test
    @DisplayName("超时关单：只有真正关单成功的订单才投递一次取消消息")
    void cancelTimeoutOrdersShouldSendCancelMessageOncePerClosedOrder() {
        Page<Order> page = new Page<>(1, 200L);
        page.setRecords(List.of(buildPendingOrder(ORDER_ID), buildPendingOrder(ANOTHER_ORDER_ID)));
        doReturn(page).when(orderMapper).selectPage(any(), any());
        when(orderTxService.cancelTimeoutOrder(ORDER_ID, USER_ID)).thenReturn(true);
        // 第二单已被支付/用户取消：条件更新 0 行，跳过且不得发消息
        when(orderTxService.cancelTimeoutOrder(ANOTHER_ORDER_ID, USER_ID)).thenReturn(false);

        int closed = orderStateService.cancelTimeoutOrders();

        assertThat(closed).isEqualTo(1);
        verify(orderOutboxService, times(1)).dispatch(ORDER_ID, TagConstants.ORDER_CANCELLED);
        verify(orderOutboxService, never()).dispatch(eq(ANOTHER_ORDER_ID), anyString());
    }

    @Test
    @DisplayName("超时关单：无候选订单时不产生任何状态变更与消息投递")
    void cancelTimeoutOrdersShouldDoNothingWhenNoCandidate() {
        Page<Order> page = new Page<>(1, 200L);
        page.setRecords(List.of());
        doReturn(page).when(orderMapper).selectPage(any(), any());

        assertThat(orderStateService.cancelTimeoutOrders()).isZero();

        verifyNoInteractions(orderTxService, orderOutboxService);
    }

    private OrderPaidEvent buildPaidEvent() {
        return new OrderPaidEvent(ORDER_ID, PAY_NO, TRADE_NO, new BigDecimal("199.00"),
                PAYMENT_METHOD, 1762308010000L);
    }

    private InventoryDeductResultEvent buildDeductFailedEvent() {
        return new InventoryDeductResultEvent(ORDER_ID, false, DEDUCT_FAILED_REASON,
                List.of(new InventoryDeductResultEvent.Item(1L, 5, 3, null)), 1762308000000L);
    }

    private Order buildPendingOrder(Long orderId) {
        Order order = new Order();
        order.setId(orderId);
        order.setUserId(USER_ID);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalPrice(new BigDecimal("199.00"));
        return order;
    }
}
