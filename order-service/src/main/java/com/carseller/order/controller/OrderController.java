package com.carseller.order.controller;

import com.carseller.common.result.PageResult;
import com.carseller.common.result.R;
import com.carseller.common.security.UserHeaders;
import com.carseller.order.dto.CreateOrderRequest;
import com.carseller.order.dto.OrderCreateResponse;
import com.carseller.order.dto.OrderDetailResponse;
import com.carseller.order.dto.OrderQuery;
import com.carseller.order.dto.OrderSummaryResponse;
import com.carseller.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口（README 8.6）。
 *
 * <ul>
 *     <li>统一前缀 {@code /api/orders}（网关未配置 StripPrefix，服务内自带 /api 前缀）；</li>
 *     <li>{@code userId} 只取网关透传的 {@code X-User-Id}，禁止从请求体或参数接收；</li>
 *     <li>返回值一律 HTTP 200 + {@link R}，业务结果看 {@code code}；</li>
 *     <li>状态变更使用 POST 子资源（{@code /{id}/cancel}），参数校验由 {@code GlobalExceptionHandler} 统一转 400。</li>
 * </ul>
 *
 * @author car-seller
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 创建订单（下单）。
     *
     * @param userId  网关透传的用户 ID（缺失时由 Service 抛 401）
     * @param request 下单请求：商品明细、收货地址、联系电话、支付方式
     * @return 订单 ID、总金额与初始状态 PENDING
     */
    @PostMapping
    public R<OrderCreateResponse> createOrder(
            @RequestHeader(value = UserHeaders.X_USER_ID, required = false) Long userId,
            @Valid @RequestBody CreateOrderRequest request) {
        return R.ok("下单成功", orderService.createOrder(userId, request));
    }

    /**
     * 查询当前用户的订单列表（分页 + 状态过滤）。
     *
     * @param userId 网关透传的用户 ID
     * @param query  分页参数（pageNum 默认 1，pageSize 默认 10、上限 100）与状态过滤
     * @return 分页结果 {@code PageResult{records,total,pageNum,pageSize,pages}}
     */
    @GetMapping
    public R<PageResult<OrderSummaryResponse>> pageOrders(
            @RequestHeader(value = UserHeaders.X_USER_ID, required = false) Long userId,
            OrderQuery query) {
        return R.ok(orderService.pageOrders(userId, query));
    }

    /**
     * 查询订单详情（仅限本人订单）。
     *
     * @param userId 网关透传的用户 ID
     * @param id     订单 ID
     * @return 订单详情（含明细）
     */
    @GetMapping("/{id}")
    public R<OrderDetailResponse> getOrderDetail(
            @RequestHeader(value = UserHeaders.X_USER_ID, required = false) Long userId,
            @PathVariable("id") Long id) {
        return R.ok(orderService.getOrderDetail(userId, id));
    }

    /**
     * 取消订单（仅待支付订单可取消，幂等：重复取消返回成功）。
     *
     * @param userId 网关透传的用户 ID
     * @param id     订单 ID
     * @return 成功响应（无数据）
     */
    @PostMapping("/{id}/cancel")
    public R<Void> cancelOrder(
            @RequestHeader(value = UserHeaders.X_USER_ID, required = false) Long userId,
            @PathVariable("id") Long id) {
        orderService.cancelOrder(userId, id);
        return R.ok("订单取消成功", null);
    }
}
