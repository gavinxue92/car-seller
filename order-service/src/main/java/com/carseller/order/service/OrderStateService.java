package com.carseller.order.service;

import com.carseller.common.mq.event.InventoryDeductResultEvent;
import com.carseller.common.mq.event.OrderPaidEvent;

/**
 * 订单状态推进服务（消费 MQ 消息 + 定时关单，对应 README Phase 3 第 4/6 条）。
 *
 * <p>与 {@link OrderTxService} 的分工：本接口负责「消息 → 状态机决策 → 编排」，
 * 真正的数据库写操作与消息登记全部委托给 {@link OrderTxService}（事务边界清晰，
 * 消费线程内不做 Feign 同步调用，符合 README 7.4）。</p>
 *
 * <p><b>幂等约定</b>：所有状态推进都是条件更新，影响 0 行表示已被处理过，
 * 直接视为成功 ACK，<b>不抛异常、不触发 MQ 重试</b>。</p>
 *
 * @author car-seller
 */
public interface OrderStateService {

    /**
     * 处理支付成功消息：{@code PENDING → PAID}。
     *
     * @param event 支付成功事件（order-paid-topic）
     * @return {@code true} 表示本次真正完成了状态推进；{@code false} 表示已处理或订单已非待支付
     */
    boolean markPaid(OrderPaidEvent event);

    /**
     * 处理库存扣减失败消息：{@code PENDING → CANCELLED}，且<b>不发</b> {@code order-cancelled-topic}
     * （库存本就没扣成功，回补会多加库存，README 领域契约 3）。
     *
     * @param event 扣减结果事件（inventory-deduct-result-topic）
     * @return {@code true} 表示本次真正关闭了订单
     */
    boolean cancelOnDeductFailed(InventoryDeductResultEvent event);

    /**
     * 扫描并在一个批次内关闭超时未支付订单（阈值 {@code ORDER_TIMEOUT_MINUTES}）。
     *
     * <p>每成功关闭一单发且仅发一次 {@code order-cancelled-topic}（库存回补）：
     * 状态条件更新成功的线程才会登记并投递取消消息，表上 {@code order_id + tag} 唯一键再兜一层。</p>
     *
     * @return 本批次实际关闭的订单数
     */
    int cancelTimeoutOrders();
}
