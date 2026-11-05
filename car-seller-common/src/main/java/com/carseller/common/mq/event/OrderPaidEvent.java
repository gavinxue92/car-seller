package com.carseller.common.mq.event;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单支付成功事件消息体（{@code order-paid-topic}，Tag = {@code ORDER_PAID}）。
 *
 * <p>生产方：payment-service；消费方：order-service（消费组 {@code order-paid-consumer-group}）。
 * order-service 收到后把订单从 {@code PENDING} 条件更新为 {@code PAID}，并以 {@code orderId} 做幂等。</p>
 *
 * <p>JSON 结构固定为（README 7.3，<b>只增不改</b>）：</p>
 * <pre>
 * {
 *   "orderId": 1001,
 *   "payNo": "PAY20261105000001",
 *   "tradeNo": "MOCK_TRADE_88237612",
 *   "amount": 7999.00,
 *   "paymentMethod": "ALIPAY",
 *   "paidAt": 1762308010000
 * }
 * </pre>
 *
 * <p>注：{@code orders} 表没有 {@code pay_time} 列（README 6.2），支付时间由 {@code updated_at}
 * 承载，因此 {@code paidAt} 只用于日志与审计，不落库。</p>
 *
 * <p>所在模块：{@code car-seller-common}，生产方与消费方共用本类，禁止在业务服务内复制。</p>
 *
 * @author car-seller
 */
public class OrderPaidEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单号，同时作为消息 Key */
    private Long orderId;

    /** 支付流水号（payment-service 生成） */
    private String payNo;

    /** 第三方交易号（模拟支付时为 MOCK_ 前缀） */
    private String tradeNo;

    /** 支付金额（两位小数） */
    private BigDecimal amount;

    /** 支付方式：ALIPAY / WECHAT，写回 {@code orders.payment_method} */
    private String paymentMethod;

    /** 支付成功时间（毫秒时间戳） */
    private Long paidAt;

    public OrderPaidEvent() {
    }

    public OrderPaidEvent(Long orderId, String payNo, String tradeNo, BigDecimal amount,
                          String paymentMethod, Long paidAt) {
        this.orderId = orderId;
        this.payNo = payNo;
        this.tradeNo = tradeNo;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.paidAt = paidAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getPayNo() {
        return payNo;
    }

    public void setPayNo(String payNo) {
        this.payNo = payNo;
    }

    public String getTradeNo() {
        return tradeNo;
    }

    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public Long getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(Long paidAt) {
        this.paidAt = paidAt;
    }

    @Override
    public String toString() {
        return "OrderPaidEvent{" +
                "orderId=" + orderId +
                ", payNo='" + payNo + '\'' +
                ", tradeNo='" + tradeNo + '\'' +
                ", amount=" + amount +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", paidAt=" + paidAt +
                '}';
    }
}
