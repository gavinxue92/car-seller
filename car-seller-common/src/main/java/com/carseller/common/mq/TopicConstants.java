package com.carseller.common.mq;

/**
 * 全局 RocketMQ Topic 常量。
 *
 * <p>命名规则（README 7.3）：{@code Topic = <域>-<事件>-topic}，
 * Tag = 事件大写，Key 统一使用业务主键 {@code orderId}。</p>
 *
 * <p>所在模块：{@code car-seller-common}，各业务微服务禁止复制本类，
 * 也禁止在业务代码中直接书写 Topic 字符串（README 9.1 禁止魔法值）。</p>
 *
 * @author car-seller
 */
public final class TopicConstants {

    /** 订单创建事件：order-service 生产，inventory-service 消费（异步扣减库存） */
    public static final String ORDER_CREATED_TOPIC = "order-created-topic";

    /** 库存扣减结果事件：inventory-service 生产，order-service 消费 */
    public static final String INVENTORY_DEDUCT_RESULT_TOPIC = "inventory-deduct-result-topic";

    /** 支付成功事件：payment-service 生产，order-service 消费 */
    public static final String ORDER_PAID_TOPIC = "order-paid-topic";

    /** 订单取消事件：order-service 生产，inventory-service 消费（回补库存） */
    public static final String ORDER_CANCELLED_TOPIC = "order-cancelled-topic";

    private TopicConstants() {
    }
}
