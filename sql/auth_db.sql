-- =====================================================================
-- 认证服务数据库 auth_db（auth-service : 8086）
-- 说明：
--   1. 只存本域数据，禁止跨库 JOIN，其他服务需要用户信息只能通过网关或 Feign 调用 auth-service。
--   2. 密码只允许存放 BCrypt 哈希（cost = 10），严禁明文、MD5/SHA1。
--   3. 时间字段由数据库默认值维护，应用侧不手工赋值（last_login_at / password_updated_at 除外）。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS auth_db
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE auth_db;

-- ---------------------------------------------------------------------
-- 用户表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS users;
CREATE TABLE users
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username            VARCHAR(64)  NOT NULL COMMENT '登录账号（唯一）',
    password            VARCHAR(100) NOT NULL COMMENT '密码哈希（BCrypt，cost=10，禁止明文）',
    nickname            VARCHAR(64)           DEFAULT NULL COMMENT '昵称（展示名，写入 JWT userName）',
    phone               VARCHAR(20)           DEFAULT NULL COMMENT '手机号（唯一，可为空）',
    email               VARCHAR(128)          DEFAULT NULL COMMENT '邮箱（唯一，可为空）',
    avatar_url          VARCHAR(500)          DEFAULT NULL COMMENT '头像地址',
    role                VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER-普通用户，ADMIN-管理员',
    status              TINYINT      NOT NULL DEFAULT 1 COMMENT '账号状态：1-启用，0-禁用',
    last_login_at       DATETIME              DEFAULT NULL COMMENT '最近登录时间',
    last_login_ip       VARCHAR(64)           DEFAULT NULL COMMENT '最近登录IP',
    password_updated_at DATETIME              DEFAULT NULL COMMENT '密码最近修改时间',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone),
    UNIQUE KEY uk_email (email),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户表';

-- ---------------------------------------------------------------------
-- 用户登录审计日志表（登录成功/失败都记录，用于安全审计与风控）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS user_login_logs;
CREATE TABLE user_login_logs
(
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    user_id      BIGINT                DEFAULT NULL COMMENT '用户ID（账号不存在时为 NULL）',
    username     VARCHAR(64)  NOT NULL COMMENT '本次登录提交的账号',
    login_ip     VARCHAR(64)           DEFAULT NULL COMMENT '客户端IP',
    user_agent   VARCHAR(500)          DEFAULT NULL COMMENT '客户端 User-Agent',
    login_result TINYINT      NOT NULL DEFAULT 1 COMMENT '登录结果：1-成功，0-失败',
    fail_reason  VARCHAR(255)          DEFAULT NULL COMMENT '失败原因（账号不存在/密码错误/账号禁用）',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_username (username),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户登录审计日志表';

-- ---------------------------------------------------------------------
-- 初始数据
--   id 与 cart_db / order_db 演示数据中的 user_id（1、2、3）保持一致
--   admin    明码 admin123
--   zhangsan 明码 user123456
--   lisi     明码 user123456
--   以上账号仅用于本地演示，上线前必须重置密码或删除
-- ---------------------------------------------------------------------
INSERT INTO users (id, username, password, nickname, phone, email, role, status, password_updated_at)
VALUES (1, 'admin', '$2a$10$8Y1TyNNirdtYP.wWrMbk.uFYi3vOEmiO0kAFqPgET.U3TjS0ZpsSm', '系统管理员',
        '13800138000', 'admin@carseller.local', 'ADMIN', 1, NOW()),
       (2, 'zhangsan', '$2a$10$Twj19.IFrMCKYlc92VEf8uTLVwn6i9IJGW1.Im8EPGtv.3Rfwu1ea', '张三',
        '13900139000', 'zhangsan@carseller.local', 'USER', 1, NOW()),
       (3, 'lisi', '$2a$10$Twj19.IFrMCKYlc92VEf8uTLVwn6i9IJGW1.Im8EPGtv.3Rfwu1ea', '李四',
        '13700137000', 'lisi@carseller.local', 'USER', 1, NOW());
