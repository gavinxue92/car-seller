package com.carseller.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.io.Serializable;

/**
 * 恢复库存请求（{@code POST /api/inventory/restore}）。
 *
 * @author car-seller
 */
public class InventoryRestoreRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单号：按订单回补其全部已扣减记录 */
    @NotNull(message = "订单号不能为空")
    @Min(value = 1, message = "订单号必须大于 0")
    private Long orderId;

    public InventoryRestoreRequest() {
    }

    public InventoryRestoreRequest(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    @Override
    public String toString() {
        return "InventoryRestoreRequest{" +
                "orderId=" + orderId +
                '}';
    }
}
