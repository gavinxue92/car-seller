package com.carseller.inventory.constant;

import java.time.Duration;

/**
 * 库存服务常量（禁止在业务代码中出现魔法值）。
 *
 * <p>幂等键与状态值需与 README 7.4、{@code sql/inventory_db.sql} 保持一致。</p>
 *
 * @author car-seller
 */
public final class InventoryConstants {

    /** 扣减幂等键前缀：inventory:deduct:{orderId} */
    public static final String DEDUCT_IDEMPOTENT_KEY_PREFIX = "inventory:deduct:";

    /** 恢复幂等键前缀：inventory:restore:{orderId} */
    public static final String RESTORE_IDEMPOTENT_KEY_PREFIX = "inventory:restore:";

    /** 幂等键有效期（天级，覆盖订单生命周期内可能的消息重复） */
    public static final Duration IDEMPOTENT_TTL = Duration.ofHours(24);

    /** 幂等键占位值 */
    public static final String IDEMPOTENT_FLAG = "1";

    /** 扣减消费组：消费 order-created-topic */
    public static final String DEDUCT_CONSUMER_GROUP = "inventory-consumer-group";

    /** 恢复消费组：消费 order-cancelled-topic */
    public static final String RESTORE_CONSUMER_GROUP = "inventory-restore-group";

    /** 库存日志类型：增加 */
    public static final String CHANGE_TYPE_INCREASE = "INCREASE";

    /** 库存日志类型：减少 */
    public static final String CHANGE_TYPE_DECREASE = "DECREASE";

    /** 扣减记录状态：已扣减（待回补） */
    public static final String RECORD_STATUS_DEDUCTED = "DEDUCTED";

    /** 扣减记录状态：已回补 */
    public static final String RECORD_STATUS_RESTORED = "RESTORED";

    /** 分页默认页码 */
    public static final long DEFAULT_PAGE_NUM = 1L;

    /** 分页默认每页条数 */
    public static final long DEFAULT_PAGE_SIZE = 10L;

    /** 分页每页条数上限 */
    public static final long MAX_PAGE_SIZE = 100L;

    private InventoryConstants() {
    }
}
