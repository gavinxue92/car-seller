package com.carseller.common.mq;

/**
 * 全局 RocketMQ Tag 常量。
 *
 * <p>发送时 destination 写作 {@code topic + ":" + tag}，
 * 消费端使用 {@code selectorExpression = TagConstants.XXX} 精确订阅。</p>
 *
 * <p>所在模块：{@code car-seller-common}，各业务微服务禁止复制本类。</p>
 *
 * @author car-seller
 */
public final class TagConstants {

    /** 订单已创建 */
    public static final String ORDER_CREATED = "ORDER_CREATED";

    /** 订单已支付 */
    public static final String ORDER_PAID = "ORDER_PAID";

    /** 订单已取消 */
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    /** 库存扣减成功 */
    public static final String DEDUCT_SUCCESS = "DEDUCT_SUCCESS";

    /** 库存扣减失败 */
    public static final String DEDUCT_FAILED = "DEDUCT_FAILED";

    private TagConstants() {
    }
}
