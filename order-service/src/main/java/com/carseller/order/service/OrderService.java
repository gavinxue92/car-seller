package com.carseller.order.service;

import com.carseller.common.result.PageResult;
import com.carseller.order.dto.CreateOrderRequest;
import com.carseller.order.dto.OrderCreateResponse;
import com.carseller.order.dto.OrderDetailResponse;
import com.carseller.order.dto.OrderQuery;
import com.carseller.order.dto.OrderSummaryResponse;

/**
 * 订单服务接口（README 8.6）。
 *
 * <p>职责边界：Feign 调用（取商品价格）、参数与业务校验、事务编排、MQ 事件发布。
 * 用户身份一律由 Controller 从 {@code X-User-Id} 取到后传入，本层不再读取请求上下文。</p>
 *
 * @author car-seller
 */
public interface OrderService {

    /**
     * 创建订单。
     *
     * <p>流程：校验入参 → 逐个调用 product-service 校验商品并取最新价格 →
     * 计算总金额 → 本地事务写入 orders / order_items（状态 PENDING）→
     * 事务提交后发送 {@code order-created-topic}（触发库存异步扣减）。</p>
     *
     * @param userId  当前登录用户 ID（来自 X-User-Id）
     * @param request 下单请求
     * @return 订单 ID、总金额与初始状态
     */
    OrderCreateResponse createOrder(Long userId, CreateOrderRequest request);

    /**
     * 查询订单详情（仅限本人订单）。
     *
     * @param userId  当前登录用户 ID
     * @param orderId 订单 ID
     * @return 订单详情（含明细）
     */
    OrderDetailResponse getOrderDetail(Long userId, Long orderId);

    /**
     * 分页查询当前用户的订单（支持按状态过滤）。
     *
     * @param userId 当前登录用户 ID
     * @param query  分页与状态过滤条件
     * @return 分页结果
     */
    PageResult<OrderSummaryResponse> pageOrders(Long userId, OrderQuery query);

    /**
     * 取消订单（仅 PENDING 可取消，幂等）。
     *
     * <p>通过 {@code UPDATE ... WHERE id=? AND status='PENDING'} 条件更新实现幂等：
     * 影响行数为 0 表示状态已被其他请求推进；已取消订单再次取消视为成功（幂等），
     * 其他状态抛出 40002 业务异常。</p>
     *
     * @param userId  当前登录用户 ID
     * @param orderId 订单 ID
     */
    void cancelOrder(Long userId, Long orderId);
}
