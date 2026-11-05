package com.carseller.order.dto;

import com.carseller.order.constant.OrderConstants;
import com.carseller.order.enums.PaymentMethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 创建订单请求体（{@code POST /api/orders}，README 8.6）。
 *
 * <p>userId 不在此结构中：身份只信任网关透传的 {@code X-User-Id} 请求头，
 * 不允许客户端自报用户身份（README 6.2 红线）。</p>
 *
 * @author car-seller
 */
public class CreateOrderRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 下单商品明细 */
    @NotEmpty(message = "订单商品不能为空")
    @Size(max = OrderConstants.MAX_ORDER_ITEMS,
            message = "单笔订单最多 " + OrderConstants.MAX_ORDER_ITEMS + " 种商品")
    @Valid
    private List<CreateOrderItemRequest> items;

    /** 收货地址 */
    @NotBlank(message = "收货地址不能为空")
    @Size(max = 500, message = "收货地址长度不能超过 500 个字符")
    private String shippingAddress;

    /** 联系电话 */
    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "联系电话格式不正确")
    private String contactPhone;

    /** 支付方式（ALIPAY / WECHAT） */
    @NotNull(message = "支付方式不能为空")
    private PaymentMethod paymentMethod;

    public List<CreateOrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<CreateOrderItemRequest> items) {
        this.items = items;
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

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    @Override
    public String toString() {
        return "CreateOrderRequest{" +
                "items=" + items +
                ", shippingAddress='" + shippingAddress + '\'' +
                ", paymentMethod=" + paymentMethod +
                '}';
    }
}
