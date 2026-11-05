package com.carseller.cart.dto;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 购物车响应：{@code GET /api/cart} 的 {@code data} 结构。
 *
 * @author car-seller
 */
public class CartResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 购物车条目数（商品种类数） */
    private Integer itemCount;

    /** 全部商品总数量 */
    private Long totalQuantity;

    /** 已选中商品总数量 */
    private Long selectedQuantity;

    /** 全部商品总金额 */
    private BigDecimal totalAmount;

    /** 已选中商品总金额 */
    private BigDecimal selectedAmount;

    /** 购物车条目明细（按 productId 升序） */
    private List<CartItemResponse> items;

    /**
     * 构建空购物车。
     *
     * @param userId 用户 ID
     * @return 空购物车响应
     */
    public static CartResponse empty(Long userId) {
        return of(userId, Collections.emptyList(), 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * 构建购物车响应。
     *
     * @param userId           用户 ID
     * @param items            条目明细
     * @param totalQuantity    全部商品总数量
     * @param selectedQuantity 已选中商品总数量
     * @param totalAmount      全部商品总金额
     * @param selectedAmount   已选中商品总金额
     * @return 购物车响应
     */
    public static CartResponse of(Long userId,
                                  List<CartItemResponse> items,
                                  long totalQuantity,
                                  long selectedQuantity,
                                  BigDecimal totalAmount,
                                  BigDecimal selectedAmount) {
        CartResponse response = new CartResponse();
        response.setUserId(userId);
        response.setItems(items);
        response.setItemCount(items.size());
        response.setTotalQuantity(totalQuantity);
        response.setSelectedQuantity(selectedQuantity);
        response.setTotalAmount(totalAmount);
        response.setSelectedAmount(selectedAmount);
        return response;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }

    public Long getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Long totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public Long getSelectedQuantity() {
        return selectedQuantity;
    }

    public void setSelectedQuantity(Long selectedQuantity) {
        this.selectedQuantity = selectedQuantity;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getSelectedAmount() {
        return selectedAmount;
    }

    public void setSelectedAmount(BigDecimal selectedAmount) {
        this.selectedAmount = selectedAmount;
    }

    public List<CartItemResponse> getItems() {
        return items;
    }

    public void setItems(List<CartItemResponse> items) {
        this.items = items;
    }
}
