package com.carseller.cart.dto;

import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.io.Serializable;

/**
 * 选中 / 取消选中购物车商品请求参数：{@code PUT /api/cart/{productId}/selected} 的请求体。
 *
 * @author car-seller
 */
public class UpdateCartSelectedRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** true=选中，false=取消选中 */
    @NotNull(message = "选中状态不能为空")
    private Boolean selected;

    public Boolean getSelected() {
        return selected;
    }

    public void setSelected(Boolean selected) {
        this.selected = selected;
    }
}
