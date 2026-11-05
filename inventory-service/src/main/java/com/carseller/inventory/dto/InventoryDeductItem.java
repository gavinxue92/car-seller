package com.carseller.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.io.Serializable;

/**
 * 扣减明细请求项。
 *
 * @author car-seller
 */
public class InventoryDeductItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品 ID */
    @NotNull(message = "商品ID不能为空")
    @Min(value = 1, message = "商品ID必须大于 0")
    private Long productId;

    /** 扣减数量 */
    @NotNull(message = "扣减数量不能为空")
    @Min(value = 1, message = "扣减数量必须大于 0")
    private Integer quantity;

    public InventoryDeductItem() {
    }

    public InventoryDeductItem(Long productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

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

    @Override
    public String toString() {
        return "InventoryDeductItem{" +
                "productId=" + productId +
                ", quantity=" + quantity +
                '}';
    }
}
