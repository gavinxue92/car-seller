package com.carseller.cart.service;

import com.carseller.cart.dto.AddCartItemRequest;
import com.carseller.cart.dto.CartItemResponse;
import com.carseller.cart.dto.CartResponse;
import com.carseller.cart.dto.UpdateCartQuantityRequest;
import com.carseller.cart.dto.UpdateCartSelectedRequest;

/**
 * 购物车服务。
 *
 * <p>数据以 Redis 为准（Hash {@code cart:{userId}} + Set {@code cart:selected:{userId}}），
 * 不访问数据库；商品存在性与价格通过 OpenFeign 调用 product-service 实时校验。</p>
 *
 * <p>所有方法的 {@code userId} 只能来自网关透传的 {@code X-User-Id}，缺失或非法一律按 401 处理。</p>
 *
 * @author car-seller
 */
public interface CartService {

    /**
     * 查询当前用户购物车（商品名称、价格等由 Feign 实时补全）。
     *
     * @param userId 用户 ID（来自 {@code X-User-Id}）
     * @return 购物车明细与汇总，购物车为空时返回空结构（不抛异常）
     */
    CartResponse getCart(Long userId);

    /**
     * 加入购物车：校验商品存在后按商品 ID 累加数量，新条目默认选中。
     *
     * @param userId  用户 ID
     * @param request 商品 ID 与加购数量
     * @return 加购后的条目（含最新价格）
     */
    CartItemResponse addItem(Long userId, AddCartItemRequest request);

    /**
     * 修改购物车中指定商品的数量（最终数量，非增量）。
     *
     * @param userId    用户 ID
     * @param productId 商品 ID
     * @param request   修改后的数量
     * @return 修改后的条目
     */
    CartItemResponse updateQuantity(Long userId, Long productId, UpdateCartQuantityRequest request);

    /**
     * 选中 / 取消选中购物车中的指定商品。
     *
     * @param userId    用户 ID
     * @param productId 商品 ID
     * @param request   选中状态
     * @return 修改后的条目
     */
    CartItemResponse updateSelected(Long userId, Long productId, UpdateCartSelectedRequest request);

    /**
     * 删除购物车单项。
     *
     * @param userId    用户 ID
     * @param productId 商品 ID
     */
    void removeItem(Long userId, Long productId);

    /**
     * 清空当前用户购物车（幂等，购物车已为空时不报错）。
     *
     * @param userId 用户 ID
     */
    void clearCart(Long userId);
}
