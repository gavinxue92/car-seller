package com.carseller.common.mq.event;

import java.io.Serial;
import java.io.Serializable;

/**
 * 订单取消事件消息体（{@code order-cancelled-topic}，Tag = {@code ORDER_CANCELLED}）。
 *
 * <p>触发时机（README 7.3）：用户主动取消、超时未支付关单。
 * 消费方 inventory-service 依据 {@code orderId} 做幂等回补库存。</p>
 *
 * <p>所在模块：{@code car-seller-common}，生产方与消费方共用，禁止复制。</p>
 *
 * @author car-seller
 */
public class OrderCancelledEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单号，同时作为消息 Key */
    private Long orderId;

    /** 下单用户 */
    private Long userId;

    /** 取消原因：USER_CANCEL（用户取消） / TIMEOUT（超时未支付） */
    private String reason;

    /** 取消时间（毫秒时间戳） */
    private Long cancelledAt;

    public OrderCancelledEvent() {
    }

    public OrderCancelledEvent(Long orderId, Long userId, String reason, Long cancelledAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.reason = reason;
        this.cancelledAt = cancelledAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Long cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    @Override
    public String toString() {
        return "OrderCancelledEvent{" +
                "orderId=" + orderId +
                ", userId=" + userId +
                ", reason='" + reason + '\'' +
                ", cancelledAt=" + cancelledAt +
                '}';
    }
}
