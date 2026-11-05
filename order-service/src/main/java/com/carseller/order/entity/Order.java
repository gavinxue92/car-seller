package com.carseller.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.carseller.order.enums.OrderStatus;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表实体（{@code order_db.orders}）。
 *
 * <p>金额统一使用 {@link BigDecimal}，数据库为 {@code DECIMAL(10,2)}；
 * {@code createdAt / updatedAt} 由数据库默认值与 {@code ON UPDATE} 维护，
 * 应用侧不做写入，避免各服务时钟不一致。</p>
 *
 * @author car-seller
 */
@TableName("orders")
public class Order implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单 ID（自增，同时作为 MQ 消息 Key） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 下单用户 ID（来自网关透传的 X-User-Id） */
    @TableField("user_id")
    private Long userId;

    /** 订单总金额，由 product-service 返回的最新单价 × 数量求和得出 */
    @TableField("total_price")
    private BigDecimal totalPrice;

    /** 订单状态，数据库存枚举名 */
    @TableField("status")
    private OrderStatus status;

    /** 收货地址 */
    @TableField("shipping_address")
    private String shippingAddress;

    /** 联系电话 */
    @TableField("contact_phone")
    private String contactPhone;

    /** 支付方式（ALIPAY / WECHAT） */
    @TableField("payment_method")
    private String paymentMethod;

    /** 创建时间（数据库默认值维护） */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间（数据库 ON UPDATE 维护） */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", userId=" + userId +
                ", totalPrice=" + totalPrice +
                ", status=" + status +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
