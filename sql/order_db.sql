-- 订单数据库 order_db
CREATE DATABASE IF NOT EXISTS order_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE order_db;

-- 订单表
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    shipping_address VARCHAR(500),
    contact_phone VARCHAR(20),
    payment_method VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 订单明细表
CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id),
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 本地消息表（README 7.1 ③ / 7.4：解决「订单已落库、消息没发出去」）
-- 设计说明：
--   1. uk_order_tag (order_id, tag) UNIQUE：同一订单的同一类事件只允许登记一条，
--      是「超时/用户取消只发一次 order-cancelled-topic」的第二道防线（第一道是状态条件更新）；
--   2. topic / tag / message_key 存完整投递目的地，补偿时无需回查订单表；
--      message_key 恒为 orderId（与 RocketMQHeaders.KEYS 一致），便于按业务键排查；
--   3. payload 存事件 JSON 快照：补偿重发用快照，避免订单后续状态变化导致消息体漂移；
--   4. status + next_retry_at + retry_count 支撑「失败重试 3 次（指数退避）」与定时补偿扫描：
--      扫描条件为 status IN ('PENDING','FAILED') AND next_retry_at <= NOW()；
--      idx_status_next_retry 为该扫描条件专用索引，idx_status 满足「按状态可查询」的排查/告警需求；
--   5. last_error / sent_at 用于失败定位与发送时间审计；status 取值：PENDING / SENT / FAILED。
CREATE TABLE IF NOT EXISTS order_message_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '订单号，同时作为 MQ 消息 Key',
    topic VARCHAR(100) NOT NULL COMMENT '目标 Topic',
    tag VARCHAR(50) NOT NULL COMMENT '目标 Tag（ORDER_CREATED / ORDER_CANCELLED）',
    message_key VARCHAR(64) NOT NULL COMMENT 'MQ 消息 Key，恒为 orderId',
    payload TEXT NOT NULL COMMENT '事件 JSON 快照，补偿重发直接使用',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / SENT / FAILED',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '累计发送尝试次数',
    next_retry_at DATETIME DEFAULT NULL COMMENT '下次允许重发时间（指数退避 1→2→4…分钟，上限 30 分钟）',
    last_error VARCHAR(255) DEFAULT NULL COMMENT '最近一次发送失败原因',
    sent_at DATETIME DEFAULT NULL COMMENT '发送成功时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_tag (order_id, tag),
    INDEX idx_status (status),
    INDEX idx_status_next_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入测试订单数据
INSERT INTO orders (user_id, total_price, status, shipping_address, contact_phone, payment_method) VALUES
(1, 7999.00, 'PENDING', '北京市朝阳区XX路XX号', '13800138000', 'ALIPAY'),
(2, 8999.00, 'PAID', '上海市浦东新区XX路XX号', '13900139000', 'WECHAT'),
(3, 1899.00, 'SHIPPED', '广州市天河区XX路XX号', '13700137000', 'ALIPAY'),
(1, 6499.00, 'DELIVERED', '北京市朝阳区XX路XX号', '13800138000', 'WECHAT'),
(2, 2999.00, 'CANCELLED', '上海市浦东新区XX路XX号', '13900139000', 'ALIPAY');