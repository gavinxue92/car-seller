package com.carseller.order.dto;

import com.carseller.order.constant.OrderConstants;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.Serial;
import java.io.Serializable;

/**
 * 下单商品条目请求体。
 *
 * <p><b>只接收 productId 与 quantity</b>：单价与名称由服务端调用 product-service 实时获取，
 * 绝不信任客户端传来的价格，防止篡改金额。</p>
 *
 * @author car-seller
 */
public class CreateOrderItemRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品 ID */
    @NotNull(message = "商品ID不能为空")
    @Positive(message = "商品ID必须为正整数")
    private Long productId;

    /** 购买数量 */
    @NotNull(message = "商品数量不能为空")
    @Min(value = 1, message = "商品数量必须大于 0")
    @Max(value = OrderConstants.MAX_ITEM_QUANTITY, message = "单个商品最多 " + OrderConstants.MAX_ITEM_QUANTITY + " 件")
    private Integer quantity;

    public CreateOrderItemRequest() {
    }

    public CreateOrderItemRequest(Long productId, Integer quantity) {
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
        return "CreateOrderItemRequest{" +
                "productId=" + productId +
                ", quantity=" + quantity +
                '}';
    }
}
