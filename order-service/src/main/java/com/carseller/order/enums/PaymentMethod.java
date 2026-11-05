package com.carseller.order.enums;

/**
 * 支付方式。
 *
 * <p>数据库 {@code orders.payment_method VARCHAR(50)} 存储枚举名，
 * 实体中保持 {@code String} 以兼容历史数据，请求体使用本枚举保证取值合法。</p>
 *
 * @author car-seller
 */
public enum PaymentMethod {

    /** 支付宝（模拟） */
    ALIPAY("支付宝"),

    /** 微信支付（模拟） */
    WECHAT("微信支付");

    private final String description;

    PaymentMethod(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
