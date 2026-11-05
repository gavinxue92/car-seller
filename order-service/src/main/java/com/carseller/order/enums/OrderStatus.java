package com.carseller.order.enums;

/**
 * 订单状态（README 7.2.1 订单状态机）。
 *
 * <p>数据库以 {@code orders.status VARCHAR(50)} 存储，MyBatis 默认枚举处理器按
 * {@link #name()} 写入/读取，因此枚举常量名必须与数据库及前端约定完全一致，禁止改名。</p>
 *
 * <p>流转铁律：状态只能单向流转，每次变更必须写成幂等条件更新
 * （{@code UPDATE orders SET status=? WHERE id=? AND status=?}）。</p>
 *
 * @author car-seller
 */
public enum OrderStatus {

    /** 待支付：下单成功，库存异步扣减中或已完成 */
    PENDING("待支付"),

    /** 已支付：消费 order-paid-topic 后由 order-service 独占更新 */
    PAID("已支付"),

    /** 已发货 */
    SHIPPED("已发货"),

    /** 已完成（终态） */
    DELIVERED("已完成"),

    /** 已取消（终态）：库存扣减失败 / 超时未支付 / 用户主动取消 */
    CANCELLED("已取消");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
