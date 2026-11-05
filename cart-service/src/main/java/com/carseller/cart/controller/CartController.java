package com.carseller.cart.controller;

import com.carseller.cart.dto.AddCartItemRequest;
import com.carseller.cart.dto.CartItemResponse;
import com.carseller.cart.dto.CartResponse;
import com.carseller.cart.dto.UpdateCartQuantityRequest;
import com.carseller.cart.dto.UpdateCartSelectedRequest;
import com.carseller.cart.service.CartService;
import com.carseller.common.result.R;
import com.carseller.common.security.UserHeaders;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 购物车接口（README 8.6 / 8.7）。
 *
 * <ul>
 *     <li>统一前缀 {@code /api/cart}（网关未配置 StripPrefix，服务内自带 /api 前缀）；</li>
 *     <li>{@code userId} 只取网关透传的 {@code X-User-Id}，禁止从请求体或参数接收；</li>
 *     <li>返回值一律 HTTP 200 + {@link R}，业务结果看 {@code code}；</li>
 *     <li>状态变更使用 POST / PUT / DELETE，参数校验由 {@code GlobalExceptionHandler} 统一转 400。</li>
 * </ul>
 *
 * @author car-seller
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * 查询当前用户购物车。
     *
     * @param userId 网关透传的用户 ID（缺失时由 Service 抛 401）
     * @return 购物车明细与汇总金额
     */
    @GetMapping
    public R<CartResponse> getCart(
            @RequestHeader(value = UserHeaders.X_USER_ID, required = false) Long userId) {
        return R.ok(cartService.getCart(userId));
    }

    /**
     * 加入购物车：同一商品重复加购累加数量。
     *
     * @param userId  网关透传的用户 ID
     * @param request 商品 ID 与数量
     * @return 加购后的条目
     */
    @PostMapping
    public R<CartItemResponse> addItem(
            @RequestHeader(value = UserHeaders.X_USER_ID, required = false) Long userId,
            @Valid @RequestBody AddCartItemRequest request) {
        return R.ok("加入购物车成功", cartService.addItem(userId, request));
    }

    /**
     * 修改购物车中指定商品的数量。
     *
     * @param userId    网关透传的用户 ID
     * @param productId 商品 ID
     * @param request   修改后的数量
     * @return 修改后的条目
     */
    @PutMapping("/{productId}")
    public R<CartItemResponse> updateQuantity(
            @RequestHeader(value = UserHeaders.X_USER_ID, required = false) Long userId,
            @PathVariable("productId") Long productId,
            @Valid @RequestBody UpdateCartQuantityRequest request) {
        return R.ok("修改购物车数量成功", cartService.updateQuantity(userId, productId, request));
    }

    /**
     * 选中 / 取消选中购物车中的指定商品。
     *
     * @param userId    网关透传的用户 ID
     * @param productId 商品 ID
     * @param request   选中状态
     * @return 修改后的条目
     */
    @PutMapping("/{productId}/selected")
    public R<CartItemResponse> updateSelected(
            @RequestHeader(value = UserHeaders.X_USER_ID, required = false) Long userId,
            @PathVariable("productId") Long productId,
            @Valid @RequestBody UpdateCartSelectedRequest request) {
        return R.ok("修改选中状态成功", cartService.updateSelected(userId, productId, request));
    }

    /**
     * 删除购物车单项。
     *
     * @param userId    网关透传的用户 ID
     * @param productId 商品 ID
     * @return 成功响应（无数据）
     */
    @DeleteMapping("/{productId}")
    public R<Void> removeItem(
            @RequestHeader(value = UserHeaders.X_USER_ID, required = false) Long userId,
            @PathVariable("productId") Long productId) {
        cartService.removeItem(userId, productId);
        return R.ok("删除购物车商品成功", null);
    }

    /**
     * 清空购物车（幂等）。
     *
     * @param userId 网关透传的用户 ID
     * @return 成功响应（无数据）
     */
    @DeleteMapping
    public R<Void> clearCart(
            @RequestHeader(value = UserHeaders.X_USER_ID, required = false) Long userId) {
        cartService.clearCart(userId);
        return R.ok("清空购物车成功", null);
    }
}
