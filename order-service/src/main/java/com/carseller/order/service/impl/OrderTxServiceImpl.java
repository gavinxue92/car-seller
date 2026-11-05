package com.carseller.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carseller.common.exception.BusinessException;
import com.carseller.order.constant.OrderConstants;
import com.carseller.order.dto.CreateOrderRequest;
import com.carseller.order.entity.Order;
import com.carseller.order.entity.OrderItem;
import com.carseller.order.enums.OrderStatus;
import com.carseller.order.mapper.OrderItemMapper;
import com.carseller.order.mapper.OrderMapper;
import com.carseller.order.service.OrderOutboxService;
import com.carseller.order.service.OrderTxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单本地事务实现（纯数据库操作）。
 *
 * @author car-seller
 */
@Service
public class OrderTxServiceImpl implements OrderTxService {

    private static final Logger log = LoggerFactory.getLogger(OrderTxServiceImpl.class);

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    private final OrderOutboxService orderOutboxService;

    public OrderTxServiceImpl(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                              OrderOutboxService orderOutboxService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderOutboxService = orderOutboxService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order savePendingOrder(Long userId, CreateOrderRequest request, BigDecimal totalPrice,
                                 List<OrderItem> orderItems) {
        Order order = new Order();
        order.setUserId(userId);
        order.setTotalPrice(totalPrice);
        order.setStatus(OrderStatus.PENDING);
        order.setShippingAddress(request.getShippingAddress());
        order.setContactPhone(request.getContactPhone());
        order.setPaymentMethod(request.getPaymentMethod().name());
        // created_at / updated_at 由数据库默认值维护，应用侧不写，避免多服务时钟不一致

        int orderRows = orderMapper.insert(order);
        if (orderRows != 1 || order.getId() == null) {
            throw new BusinessException("订单创建失败，请稍后重试");
        }

        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            int itemRows = orderItemMapper.insert(item);
            if (itemRows != 1) {
                throw new BusinessException("订单明细保存失败，orderId=" + order.getId());
            }
        }

        // 同一事务内登记本地消息（README 7.1 ③）：本地事务成功 ≈ 消息一定已登记，
        // 消息何时真正发出由 OrderOutboxService 的重试 + 补偿扫描保证（README 7.4）
        orderOutboxService.enqueueOrderCreated(order, orderItems);

        log.info("订单落库成功: orderId={}, userId={}, totalPrice={}, itemCount={}",
                order.getId(), userId, totalPrice, orderItems.size());
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelPendingOrder(Long orderId, Long userId) {
        // 幂等条件更新：只有 PENDING 才能流转到 CANCELLED，影响 0 行即表示状态已被其他请求改变
        int rows = orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .set(Order::getStatus, OrderStatus.CANCELLED.name())
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, OrderStatus.PENDING.name()));
        log.info("订单取消条件更新: orderId={}, userId={}, affectedRows={}", orderId, userId, rows);
        if (rows <= 0) {
            return false;
        }
        // 只有真正关单成功的一方登记取消消息 → 一个订单只会发一次 order-cancelled-topic（领域契约 3）
        orderOutboxService.enqueueOrderCancelled(orderId, userId, OrderConstants.CANCEL_REASON_USER);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markOrderPaid(Long orderId, String paymentMethod) {
        LambdaUpdateWrapper<Order> update = Wrappers.<Order>lambdaUpdate()
                .set(Order::getStatus, OrderStatus.PAID.name())
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.PENDING.name());
        if (StringUtils.hasText(paymentMethod)) {
            update.set(Order::getPaymentMethod, paymentMethod);
        }
        // orders 表没有 pay_time 列，支付时间由 updated_at 承载（README 6.2，禁止为此加列）
        int rows = orderMapper.update(null, update);
        log.info("订单支付状态条件更新: orderId={}, paymentMethod={}, affectedRows={}",
                orderId, paymentMethod, rows);
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelTimeoutOrder(Long orderId, Long userId) {
        int rows = closePendingOrder(orderId);
        if (rows <= 0) {
            log.info("超时关单条件更新影响 0 行（订单已被支付或取消），按幂等跳过: orderId={}", orderId);
            return false;
        }
        orderOutboxService.enqueueOrderCancelled(orderId, userId, OrderConstants.CANCEL_REASON_TIMEOUT);
        log.info("超时关单成功: orderId={}, userId={}", orderId, userId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOnDeductFailed(Long orderId, String reason) {
        int rows = closePendingOrder(orderId);
        if (rows <= 0) {
            log.info("库存扣减失败关单条件更新影响 0 行（订单已非 PENDING），按幂等跳过: orderId={}", orderId);
            return false;
        }
        // 库存本次并没有扣减成功，因此不登记 order-cancelled-topic，
        // 否则 inventory-service 会回补出多余库存（README 领域契约 3）
        log.warn("库存扣减失败，订单已关闭且不回补库存: orderId={}, reason={}", orderId, reason);
        return true;
    }

    /**
     * {@code PENDING → CANCELLED} 的幂等条件更新（超时关单 / 扣减失败关单共用）。
     *
     * @return 影响行数，0 表示订单已不在 PENDING
     */
    private int closePendingOrder(Long orderId) {
        return orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .set(Order::getStatus, OrderStatus.CANCELLED.name())
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.PENDING.name()));
    }
}
