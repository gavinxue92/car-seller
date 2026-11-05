package com.carseller.order.dto;

import com.carseller.order.entity.Order;
import com.carseller.order.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表项响应（{@code GET /api/orders} 分页 records 元素）。
 *
 * <p>列表不携带明细，明细通过 {@code GET /api/orders/{id}} 单独获取，
 * 避免分页接口产生 N+1 查询与超大响应体。</p>
 *
 * @author car-seller
 */
public class OrderSummaryResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long id;

    /** 订单总金额 */
    private BigDecimal totalPrice;

    /** 订单状态 */
    private OrderStatus status;

    /** 订单状态中文描述 */
    private String statusText;

    /** 支付方式 */
    private String paymentMethod;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public OrderSummaryResponse() {
    }

    /**
     * 由订单实体组装列表项。
     */
    public static OrderSummaryResponse from(Order order) {
        OrderSummaryResponse response = new OrderSummaryResponse();
        response.setId(order.getId());
        response.setTotalPrice(order.getTotalPrice());
        response.setStatus(order.getStatus());
        response.setStatusText(order.getStatus() == null ? null : order.getStatus().getDescription());
        response.setPaymentMethod(order.getPaymentMethod());
        response.setCreatedAt(order.getCreatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "OrderSummaryResponse{" +
                "id=" + id +
                ", totalPrice=" + totalPrice +
                ", status=" + status +
                ", createdAt=" + createdAt +
                '}';
    }
}
