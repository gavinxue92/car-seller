package com.carseller.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录请求参数。
 *
 * <p>校验失败由 {@code com.carseller.common.exception.GlobalExceptionHandler} 统一转
 * {@code code=400} 的 {@code R}，Controller 内不写手写判空。</p>
 *
 * @author car-seller
 */
public class LoginRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录账号 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过 64 个字符")
    private String username;

    /** 登录密码（明文仅在传输层出现，入库为 BCrypt 哈希） */
    @NotBlank(message = "密码不能为空")
    @Size(max = 72, message = "密码长度不能超过 72 个字符")
    private String password;

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

    /**
     * 注意：刻意不输出 password，避免明文密码进入日志。
     */
    @Override
    public String toString() {
        return "LoginRequest{" +
                "username='" + username + '\'' +
                '}';
    }
}
