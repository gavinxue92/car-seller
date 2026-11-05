package com.carseller.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.io.Serial;
import java.io.Serializable;

/**
 * 加入购物车请求参数：{@code POST /api/cart} 的请求体。
 *
 * <p>只做格式校验；商品是否存在、单商品数量上限等业务规则在 Service 层校验。</p>
 *
 * @author car-seller
 */
public class AddCartItemRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品 ID */
    @NotNull(message = "商品ID不能为空")
    @Positive(message = "商品ID必须为正整数")
    private Long productId;

    /** 加购数量 */
    @NotNull(message = "商品数量不能为空")
    @Positive(message = "商品数量必须大于 0")
    private Integer quantity;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
