package com.carseller.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体，对应 auth_db.users 表（见 sql/auth_db.sql）。
 *
 * <pre>
 * CREATE TABLE users (
 *     id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
 *     username            VARCHAR(64)  NOT NULL,
 *     password            VARCHAR(100) NOT NULL,
 *     nickname            VARCHAR(64),
 *     phone               VARCHAR(20),
 *     email               VARCHAR(128),
 *     avatar_url          VARCHAR(500),
 *     role                VARCHAR(32)  NOT NULL DEFAULT 'USER',
 *     status              TINYINT      NOT NULL DEFAULT 1,
 *     last_login_at       DATETIME,
 *     last_login_ip       VARCHAR(64),
 *     password_updated_at DATETIME,
 *     created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *     updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 *     deleted             TINYINT NOT NULL DEFAULT 0
 * );
 * </pre>
 *
 * <p>本实体包含密码哈希字段，<b>禁止直接对外返回</b>，对外统一使用
 * {@code com.carseller.auth.dto.UserResponse}（见 README 9.1 第 6 条）。</p>
 *
 * @author car-seller
 */
@TableName("users")
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录账号（唯一） */
    private String username;

    /** 密码哈希（BCrypt，禁止明文） */
    private String password;

    /** 昵称（展示名，写入 JWT 的 userName 声明） */
    private String nickname;

    /** 手机号（唯一索引，可为空） */
    private String phone;

    /** 邮箱（唯一索引，可为空） */
    private String email;

    /** 头像地址 */
    @TableField("avatar_url")
    private String avatarUrl;

    /** 角色：USER / ADMIN */
    private String role;

    /** 账号状态：1 启用、0 禁用 */
    private Integer status;

    /** 最近登录时间 */
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    /** 最近登录 IP */
    @TableField("last_login_ip")
    private String lastLoginIp;

    /** 密码最近修改时间（改密后可用于判定旧 Token 失效） */
    @TableField("password_updated_at")
    private LocalDateTime passwordUpdatedAt;

    /** 创建时间（数据库默认值维护） */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间（数据库 ON UPDATE 维护） */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：0 未删除、1 已删除 */
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public void setLastLoginIp(String lastLoginIp) {
        this.lastLoginIp = lastLoginIp;
    }

    public LocalDateTime getPasswordUpdatedAt() {
        return passwordUpdatedAt;
    }

    public void setPasswordUpdatedAt(LocalDateTime passwordUpdatedAt) {
        this.passwordUpdatedAt = passwordUpdatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    /**
     * 注意：刻意不输出 password，避免密码哈希随日志泄露。
     */
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", nickname='" + nickname + '\'' +
                ", phone='" + phone + '\'' +
                ", email='" + email + '\'' +
                ", role='" + role + '\'' +
                ", status=" + status +
                ", lastLoginAt=" + lastLoginAt +
                ", createdAt=" + createdAt +
                ", deleted=" + deleted +
                '}';
    }
}
