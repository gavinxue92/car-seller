package com.carseller.order.service;

import com.carseller.order.entity.Order;
import com.carseller.order.entity.OrderItem;

import java.util.List;

/**
 * 本地消息表服务（{@code order_db.order_message_outbox}，README 7.1 ③ / 7.4）。
 *
 * <p>职责边界：</p>
 * <ul>
 *     <li>{@code enqueueXxx}：<b>必须在订单本地事务内调用</b>（由 {@link OrderTxService} 负责），
 *         保证「订单状态变更」与「消息登记」同生共死；</li>
 *     <li>{@code dispatch}：事务提交后立即尝试发送，失败按指数退避重试 3 次，
 *         仍失败则置 {@code FAILED} 等待补偿扫描；</li>
 *     <li>{@code compensate}：定时扫描 {@code PENDING / FAILED} 且到达重发时间的记录并重发。</li>
 * </ul>
 *
 * <p>幂等前提：消费端（inventory-service）以 {@code orderId} 做幂等扣减，
 * 因此重复发送不会造成重复扣减（README 7.4）。</p>
 *
 * @author car-seller
 */
public interface OrderOutboxService {

    /**
     * 登记订单创建事件（{@code order-created-topic}），须在订单落库的同一事务内调用。
     *
     * @param order      已落库的订单（含自增主键）
     * @param orderItems 已落库的明细（取商品与数量）
     */
    void enqueueOrderCreated(Order order, List<OrderItem> orderItems);

    /**
     * 登记订单取消事件（{@code order-cancelled-topic}），须在关单的同一事务内调用。
     *
     * <p>同一订单只登记一次（唯一键 {@code order_id + tag}）；库存扣减失败导致的关单
     * <b>不允许</b>调用本方法（库存未扣成功，回补会多加库存）。</p>
     *
     * @param orderId 订单 ID
     * @param userId  下单用户
     * @param reason  取消原因（{@code USER_CANCEL} / {@code TIMEOUT}）
     * @return {@code true} 表示本次新登记；{@code false} 表示该订单已有取消消息记录
     */
    boolean enqueueOrderCancelled(Long orderId, Long userId, String reason);

    /**
     * 立即尝试发送指定订单的某类事件（事务提交后调用）。
     *
     * @param orderId 订单 ID
     * @param tag     事件 Tag（{@code ORDER_CREATED} / {@code ORDER_CANCELLED}）
     * @return {@code true} 表示已发送成功或此前已发送
     */
    boolean dispatch(Long orderId, String tag);

    /**
     * 补偿扫描：重发到期待发送的消息。
     *
     * @return 本次成功重发的条数
     */
    int compensate();
}
