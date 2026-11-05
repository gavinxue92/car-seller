package com.carseller.order.dto;

import com.carseller.order.entity.Order;
import com.carseller.order.enums.OrderStatus;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 创建订单响应（README 8.6：{@code {orderId, totalPrice, status}}）。
 *
 * @author car-seller
 */
public class OrderCreateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 新订单 ID（后续支付、查询、取消均使用该 ID） */
    private Long orderId;

    /** 订单总金额（服务端计算的成交价合计） */
    private BigDecimal totalPrice;

    /** 订单状态，下单后固定为 PENDING */
    private OrderStatus status;

    public OrderCreateResponse() {
    }

    public OrderCreateResponse(Long orderId, BigDecimal totalPrice, OrderStatus status) {
        this.orderId = orderId;
        this.totalPrice = totalPrice;
        this.status = status;
    }

    /**
     * 由订单实体组装创建响应。
     */
    public static OrderCreateResponse from(Order order) {
        return new OrderCreateResponse(order.getId(), order.getTotalPrice(), order.getStatus());
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "OrderCreateResponse{" +
                "orderId=" + orderId +
                ", totalPrice=" + totalPrice +
                ", status=" + status +
                '}';
    }
}
