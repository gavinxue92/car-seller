package com.carseller.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.io.Serial;
import java.io.Serializable;

/**
 * 修改购物车商品数量请求参数：{@code PUT /api/cart/{productId}} 的请求体。
 *
 * @author car-seller
 */
public class UpdateCartQuantityRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 修改后的数量（最终数量，不是增量） */
    @NotNull(message = "商品数量不能为空")
    @Positive(message = "商品数量必须大于 0")
    private Integer quantity;

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
