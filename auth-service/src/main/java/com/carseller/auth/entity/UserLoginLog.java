package com.carseller.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户登录审计日志，对应 auth_db.user_login_logs 表（见 sql/auth_db.sql）。
 *
 * <p>生产环境登录行为必须留痕：成功与失败都要记录，便于排查撞库、暴力破解。
 * 日志只记录 IP、User-Agent 与失败原因，<b>禁止记录密码或 Token</b>。</p>
 *
 * @author car-seller
 */
@TableName("user_login_logs")
public class UserLoginLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户 ID；账号不存在时为 null（失败尝试同样留痕） */
    @TableField("user_id")
    private Long userId;

    /** 登录时提交的账号 */
    private String username;

    /** 客户端 IP */
    @TableField("login_ip")
    private String loginIp;

    /** 客户端 User-Agent */
    @TableField("user_agent")
    private String userAgent;

    /** 登录结果：1 成功、0 失败 */
    @TableField("login_result")
    private Integer loginResult;

    /** 失败原因（成功时为空） */
    @TableField("fail_reason")
    private String failReason;

    /** 记录时间（数据库默认值维护） */
    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getLoginIp() {
        return loginIp;
    }

    public void setLoginIp(String loginIp) {
        this.loginIp = loginIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Integer getLoginResult() {
        return loginResult;
    }

    public void setLoginResult(Integer loginResult) {
        this.loginResult = loginResult;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "UserLoginLog{" +
                "id=" + id +
                ", userId=" + userId +
                ", username='" + username + '\'' +
                ", loginIp='" + loginIp + '\'' +
                ", loginResult=" + loginResult +
                ", failReason='" + failReason + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
