package com.carseller.order.constant;

/**
 * 订单域常量（禁止在业务代码中出现魔法值，README 9.1）。
 *
 * @author car-seller
 */
public final class OrderConstants {

    /** 默认页码 */
    public static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限（README 6.2：pageSize 默认 10、上限 100） */
    public static final int MAX_PAGE_SIZE = 100;

    /** 单笔订单最多商品种类数 */
    public static final int MAX_ORDER_ITEMS = 50;

    /** 单个商品最多购买数量 */
    public static final int MAX_ITEM_QUANTITY = 99;

    /** 取消原因：用户主动取消 */
    public static final String CANCEL_REASON_USER = "USER_CANCEL";

    /** 取消原因：超时未支付关单 */
    public static final String CANCEL_REASON_TIMEOUT = "TIMEOUT";

    /** 取消原因：库存扣减失败关单（此时库存未扣成功，<b>禁止</b>回补，也不发取消消息） */
    public static final String CANCEL_REASON_DEDUCT_FAILED = "DEDUCT_FAILED";

    /** 超时未支付关单阈值（分钟，README Phase 3 第 6 条） */
    public static final long ORDER_TIMEOUT_MINUTES = 30L;

    /** 超时关单扫描 cron：每 5 分钟一次 */
    public static final String TIMEOUT_SCAN_CRON = "0 0/5 * * * ?";

    /** 本地消息补偿扫描 cron：每 5 分钟一次，与关单任务错开 30 秒 */
    public static final String OUTBOX_COMPENSATE_CRON = "30 0/5 * * * ?";

    /** 单次超时关单扫描最多处理的订单数 */
    public static final long TIMEOUT_SCAN_BATCH_SIZE = 200L;

    /** 单次补偿扫描最多处理的消息数 */
    public static final long OUTBOX_COMPENSATE_BATCH_SIZE = 100L;

    /** 立即发送阶段的最大尝试次数（README 7.4：失败重试 3 次） */
    public static final int OUTBOX_MAX_SEND_ATTEMPTS = 3;

    /** 立即重试的指数退避基数（毫秒）：200ms → 400ms */
    public static final long OUTBOX_SEND_BACKOFF_MILLIS = 200L;

    /** 补偿重发的指数退避基数（分钟）：1 → 2 → 4 … */
    public static final long OUTBOX_RETRY_BACKOFF_MINUTES = 1L;

    /** 补偿重发的退避上限（分钟） */
    public static final long OUTBOX_RETRY_BACKOFF_MAX_MINUTES = 30L;

    /** 数据库写入的失败原因最大长度（超出截断，避免超长文本写库失败） */
    public static final int OUTBOX_LAST_ERROR_MAX_LENGTH = 200;

    /** 单次消息发送超时（毫秒），与 application.yml 的 send-message-timeout 一致 */
    public static final long SEND_TIMEOUT_MILLIS = 3000L;

    /** outbox 状态：待发送 */
    public static final String OUTBOX_STATUS_PENDING = "PENDING";

    /** outbox 状态：已发送 */
    public static final String OUTBOX_STATUS_SENT = "SENT";

    /** outbox 状态：发送失败，等待补偿扫描 */
    public static final String OUTBOX_STATUS_FAILED = "FAILED";

    /** 消费组：消费 order-paid-topic（README 7.3 固定，禁止改名） */
    public static final String ORDER_PAID_CONSUMER_GROUP = "order-paid-consumer-group";

    /** 消费组：消费 inventory-deduct-result-topic（README 7.3 固定，禁止改名） */
    public static final String DEDUCT_RESULT_CONSUMER_GROUP = "order-deduct-result-group";

    private OrderConstants() {
    }
}
