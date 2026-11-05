-- 库存数据库 inventory_db
CREATE DATABASE IF NOT EXISTS inventory_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE inventory_db;

-- 库存表
CREATE TABLE IF NOT EXISTS inventories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    quantity INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 初始化库存数据（与商品表关联）
INSERT INTO inventories (product_id, quantity) VALUES
(1, 50), (2, 30), (3, 40), (4, 100), (5, 25), (6, 45), (7, 35), (8, 28), (9, 60), (10, 20)
ON DUPLICATE KEY UPDATE quantity = quantity;

-- 库存变动日志表
CREATE TABLE IF NOT EXISTS inventory_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    change_quantity INT NOT NULL,
    change_type VARCHAR(20) NOT NULL COMMENT 'INCREASE/DECREASE',
    remark VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 库存扣减记录表（README 7.4 幂等兜底：唯一键 uk_order_product 保证同一订单同一商品只扣减一次）
CREATE TABLE IF NOT EXISTS inventory_deduct_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '订单号',
    product_id BIGINT NOT NULL COMMENT '商品 ID',
    quantity INT NOT NULL DEFAULT 0 COMMENT '本次扣减数量',
    status VARCHAR(20) NOT NULL DEFAULT 'DEDUCTED' COMMENT 'DEDUCTED 已扣减 / RESTORED 已回补',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_product (order_id, product_id),
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;