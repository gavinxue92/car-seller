package com.carseller.order.service;

import com.carseller.order.dto.CreateOrderRequest;
import com.carseller.order.entity.Order;
import com.carseller.order.entity.OrderItem;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单本地事务接口（只包含数据库写操作，<b>禁止出现 Feign / HTTP / MQ 调用</b>）。
 *
 * <p>为什么单独抽出：{@code @Transactional} 只能注解在公有方法上，
 * 且事务内一旦混入远程调用就会长时间占用数据库连接（README 9.1 红线）。
 * 因此由 {@link OrderService} 负责校验与远程调用，本接口负责纯 DB 事务，
 * 类内自我调用不会被代理拦截，所以拆成独立 Bean。</p>
 *
 * @author car-seller
 */
public interface OrderTxService {

    /**
     * 在一个事务内写入订单主表与全部明细。
     *
     * @param userId      下单用户 ID
     * @param request     下单请求（取收货信息与支付方式）
     * @param totalPrice  已计算好的订单总金额
     * @param orderItems  已填充商品快照的明细（{@code orderId} 由本方法回填）
     * @return 落库后的订单实体（含自增主键）
     */
    Order savePendingOrder(Long userId, CreateOrderRequest request, BigDecimal totalPrice, List<OrderItem> orderItems);

    /**
     * 幂等取消订单：条件更新 {@code status=PENDING → CANCELLED}。
     *
     * @param orderId 订单 ID
     * @param userId  当前登录用户 ID（一并作为更新条件，防止越权）
     * @return {@code true} 表示本次请求真正完成了状态变更；{@code false} 表示状态已非 PENDING
     */
    boolean cancelPendingOrder(Long orderId, Long userId);

    /**
     * 支付成功：条件更新 {@code status=PENDING → PAID}，并写回支付方式。
     *
     * <p>{@code orders} 表没有 {@code pay_time} 列（README 6.2），支付时间由 {@code updated_at} 承载，
     * 禁止为了存支付时间而加列。</p>
     *
     * @param orderId       订单 ID
     * @param paymentMethod 支付方式（为空则不覆盖）
     * @return {@code true} 表示本次真正完成了状态推进；{@code false} 表示已处理或已非 PENDING
     */
    boolean markOrderPaid(Long orderId, String paymentMethod);

    /**
     * 超时未支付关单：条件更新 {@code status=PENDING → CANCELLED}，
     * 并在同一事务内登记 {@code order-cancelled-topic}（库存回补）消息。
     *
     * @param orderId 订单 ID
     * @param userId  下单用户（写入取消消息）
     * @return {@code true} 表示本次真正关闭了订单
     */
    boolean cancelTimeoutOrder(Long orderId, Long userId);

    /**
     * 库存扣减失败关单：条件更新 {@code status=PENDING → CANCELLED}。
     *
     * <p><b>不登记也不发送</b> {@code order-cancelled-topic}：库存本次并没有扣减成功，
     * 回补会造成库存虚增（README 领域契约 3）。</p>
     *
     * @param orderId 订单 ID
     * @param reason  失败原因（仅用于日志）
     * @return {@code true} 表示本次真正关闭了订单
     */
    boolean cancelOnDeductFailed(Long orderId, String reason);
}
