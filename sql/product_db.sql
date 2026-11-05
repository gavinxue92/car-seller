-- 商品数据库 product_db
CREATE DATABASE IF NOT EXISTS product_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE product_db;

-- 商品表
CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    stock INT NOT NULL DEFAULT 0,
    category VARCHAR(100),
    image_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name),
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入测试数据
INSERT INTO products (name, description, price, stock, category, image_url) VALUES
('iPhone 15 Pro', 'Apple最新旗舰手机，A17 Pro芯片', 7999.00, 50, '手机', '/images/iphone15.jpg'),
('MacBook Air M3', '轻薄笔记本，M3芯片', 8999.00, 30, '电脑', '/images/macbook-air.jpg'),
('iPad Pro 12.9', '专业级平板电脑', 6499.00, 40, '平板', '/images/ipad-pro.jpg'),
('AirPods Pro 2', '主动降噪无线耳机', 1899.00, 100, '音频', '/images/airpods-pro.jpg'),
('Apple Watch Ultra', '户外运动智能手表', 6299.00, 25, '手表', '/images/watch-ultra.jpg'),
('华为Mate 60 Pro', '国产旗舰手机，卫星通话', 6999.00, 45, '手机', '/images/huawei-mate60.jpg'),
('小米14 Ultra', '徕卡光学旗舰手机', 5999.00, 35, '手机', '/images/xiaomi14.jpg'),
('联想小新Pro 16', '高性能轻薄本', 5699.00, 28, '电脑', '/images/lenovo-xiaoxin.jpg'),
('索尼WH-1000XM5', '旗舰降噪耳机', 2999.00, 60, '音频', '/images/sony-xm5.jpg'),
('大疆Mini 4 Pro', '便携航拍无人机', 5999.00, 20, '数码', '/images/dji-mini4.jpg');