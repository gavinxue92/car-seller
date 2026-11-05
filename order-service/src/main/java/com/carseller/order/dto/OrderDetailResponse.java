package com.carseller.order.dto;

import com.carseller.order.entity.Order;
import com.carseller.order.entity.OrderItem;
import com.carseller.order.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 订单详情响应（{@code GET /api/orders/{id}}，含明细）。
 *
 * @author car-seller
 */
public class OrderDetailResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单 ID */
    private Long id;

    /** 订单总金额 */
    private BigDecimal totalPrice;

    /** 订单状态 */
    private OrderStatus status;

    /** 订单状态中文描述（便于前端直接展示） */
    private String statusText;

    /** 收货地址 */
    private String shippingAddress;

    /** 联系电话 */
    private String contactPhone;

    /** 支付方式 */
    private String paymentMethod;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /** 订单明细 */
    private List<OrderItemResponse> items;

    public OrderDetailResponse() {
    }

    /**
     * 由订单实体 + 明细列表组装详情响应。
     */
    public static OrderDetailResponse from(Order order, List<OrderItem> items) {
        OrderDetailResponse response = new OrderDetailResponse();
        response.setId(order.getId());
        response.setTotalPrice(order.getTotalPrice());
        response.setStatus(order.getStatus());
        response.setStatusText(order.getStatus() == null ? null : order.getStatus().getDescription());
        response.setShippingAddress(order.getShippingAddress());
        response.setContactPhone(order.getContactPhone());
        response.setPaymentMethod(order.getPaymentMethod());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        List<OrderItem> source = items == null ? Collections.emptyList() : items;
        response.setItems(source.stream().map(OrderItemResponse::from).toList());
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

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<OrderItemResponse> getItems() {
        return items;
    }

    public void setItems(List<OrderItemResponse> items) {
        this.items = items;
    }

    @Override
    public String toString() {
        return "OrderDetailResponse{" +
                "id=" + id +
                ", totalPrice=" + totalPrice +
                ", status=" + status +
                ", items=" + (items == null ? 0 : items.size()) +
                '}';
    }
}
