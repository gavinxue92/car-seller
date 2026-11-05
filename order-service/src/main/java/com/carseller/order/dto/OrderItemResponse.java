package com.carseller.order.dto;

import com.carseller.order.entity.OrderItem;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 订单明细响应（商品名称与单价均为下单时快照）。
 *
 * @author car-seller
 */
public class OrderItemResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 明细 ID */
    private Long id;

    /** 商品 ID */
    private Long productId;

    /** 商品名称快照 */
    private String productName;

    /** 成交单价 */
    private BigDecimal price;

    /** 购买数量 */
    private Integer quantity;

    /** 小计 = 单价 × 数量 */
    private BigDecimal subtotal;

    public OrderItemResponse() {
    }

    /**
     * 由明细实体组装响应（小计在此统一计算，避免前端重复算金额）。
     */
    public static OrderItemResponse from(OrderItem item) {
        OrderItemResponse response = new OrderItemResponse();
        response.setId(item.getId());
        response.setProductId(item.getProductId());
        response.setProductName(item.getProductName());
        response.setPrice(item.getPrice());
        response.setQuantity(item.getQuantity());
        BigDecimal price = item.getPrice() == null ? BigDecimal.ZERO : item.getPrice();
        int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
        response.setSubtotal(price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP));
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    @Override
    public String toString() {
        return "OrderItemResponse{" +
                "id=" + id +
                ", productId=" + productId +
                ", productName='" + productName + '\'' +
                ", price=" + price +
                ", quantity=" + quantity +
                ", subtotal=" + subtotal +
                '}';
    }
}
