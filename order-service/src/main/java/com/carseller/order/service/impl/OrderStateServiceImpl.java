package com.carseller.order.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carseller.common.mq.TagConstants;
import com.carseller.common.mq.event.InventoryDeductResultEvent;
import com.carseller.common.mq.event.OrderPaidEvent;
import com.carseller.order.constant.OrderConstants;
import com.carseller.order.entity.Order;
import com.carseller.order.enums.OrderStatus;
import com.carseller.order.mapper.OrderMapper;
import com.carseller.order.service.OrderOutboxService;
import com.carseller.order.service.OrderStateService;
import com.carseller.order.service.OrderTxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单状态推进实现（MQ 消费 + 定时关单的编排层）。
 *
 * <p>设计要点：</p>
 * <ul>
 *     <li>所有状态推进都是条件更新（{@code WHERE id=? AND status=PENDING}），
 *         影响 0 行即「已被处理」，直接 ACK，绝不抛异常触发 MQ 重试；</li>
 *     <li>{@code DEDUCT_FAILED} 关单<b>不</b>登记 {@code order-cancelled-topic}：
 *         库存本就没扣成功，回补会多加库存（领域契约 3）；</li>
 *     <li>超时关单与用户取消才登记并投递取消消息，且条件更新成功的一方才会走这一步，
 *         叠加 {@code order_message_outbox(order_id, tag)} 唯一键 → 一个订单只发一次。</li>
 * </ul>
 *
 * @author car-seller
 */
@Service
public class OrderStateServiceImpl implements OrderStateService {

    private static final Logger log = LoggerFactory.getLogger(OrderStateServiceImpl.class);

    private final OrderMapper orderMapper;

    private final OrderTxService orderTxService;

    private final OrderOutboxService orderOutboxService;

    public OrderStateServiceImpl(OrderMapper orderMapper,
                                 OrderTxService orderTxService,
                                 OrderOutboxService orderOutboxService) {
        this.orderMapper = orderMapper;
        this.orderTxService = orderTxService;
        this.orderOutboxService = orderOutboxService;
    }

    @Override
    public boolean markPaid(OrderPaidEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("忽略无效的支付成功事件: {}", event);
            return false;
        }
        boolean updated = orderTxService.markOrderPaid(event.getOrderId(), event.getPaymentMethod());
        if (!updated) {
            log.info("支付成功事件未变更订单（已处理或订单已非待支付），按幂等 ACK: orderId={}, payNo={}",
                    event.getOrderId(), event.getPayNo());
            return false;
        }
        log.info("订单已置为 PAID: orderId={}, payNo={}, amount={}, paymentMethod={}",
                event.getOrderId(), event.getPayNo(), event.getAmount(), event.getPaymentMethod());
        return true;
    }

    @Override
    public boolean cancelOnDeductFailed(InventoryDeductResultEvent event) {
        if (event == null || event.getOrderId() == null) {
            log.warn("忽略无效的库存扣减结果事件: {}", event);
            return false;
        }
        boolean cancelled = orderTxService.cancelOnDeductFailed(event.getOrderId(), event.getReason());
        if (!cancelled) {
            log.info("扣减失败事件未触发关单（订单已非待支付），按幂等 ACK: orderId={}", event.getOrderId());
            return false;
        }
        log.warn("库存扣减失败，订单已关闭；库存未扣成功，故不回补、不发 order-cancelled-topic: orderId={}, reason={}",
                event.getOrderId(), event.getReason());
        return true;
    }

    @Override
    public int cancelTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(OrderConstants.ORDER_TIMEOUT_MINUTES);
        Page<Order> page = orderMapper.selectPage(new Page<>(1, OrderConstants.TIMEOUT_SCAN_BATCH_SIZE),
                Wrappers.<Order>lambdaQuery()
                        .eq(Order::getStatus, OrderStatus.PENDING.name())
                        .lt(Order::getCreatedAt, deadline)
                        .orderByAsc(Order::getId));
        List<Order> candidates = page.getRecords() == null ? List.of() : page.getRecords();
        if (candidates.isEmpty()) {
            log.debug("超时关单扫描无待处理订单: deadline={}", deadline);
            return 0;
        }
        int closed = 0;
        for (Order order : candidates) {
            if (!orderTxService.cancelTimeoutOrder(order.getId(), order.getUserId())) {
                // 条件更新影响 0 行：该单已被支付或用户取消，跳过即可（不是异常）
                log.debug("订单状态已被并发推进，跳过超时关单: orderId={}", order.getId());
                continue;
            }
            // 只有真正关单成功的一方登记并投递取消消息 → 保证「超时关单只发一次」（领域契约 3）
            orderOutboxService.dispatch(order.getId(), TagConstants.ORDER_CANCELLED);
            closed++;
        }
        log.info("超时关单扫描结束: 候选={}, 实际关闭={}, deadline={}", candidates.size(), closed, deadline);
        return closed;
    }
}
