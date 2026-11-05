package com.carseller.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 扣减库存请求（{@code POST /api/inventory/deduct}）。
 *
 * @author car-seller
 */
public class InventoryDeductRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单号（幂等键与消息 Key） */
    @NotNull(message = "订单号不能为空")
    @Min(value = 1, message = "订单号必须大于 0")
    private Long orderId;

    /** 扣减明细 */
    @NotEmpty(message = "扣减明细不能为空")
    @Valid
    private List<InventoryDeductItem> items;

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public List<InventoryDeductItem> getItems() {
        return items;
    }

    public void setItems(List<InventoryDeductItem> items) {
        this.items = items;
    }

    @Override
    public String toString() {
        return "InventoryDeductRequest{" +
                "orderId=" + orderId +
                ", items=" + items +
                '}';
    }
}
