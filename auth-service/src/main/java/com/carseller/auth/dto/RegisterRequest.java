package com.carseller.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;

/**
 * 注册请求参数。
 *
 * @author car-seller
 */
public class RegisterRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录账号：字母开头，允许字母/数字/下划线，4~64 位 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 64, message = "用户名长度必须在 4~64 个字符之间")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{3,63}$",
            message = "用户名必须以字母开头，且只能包含字母、数字、下划线")
    private String username;

    /** 登录密码：6~72 位（BCrypt 只使用前 72 字节，超长直接拒绝） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 72, message = "密码长度必须在 6~72 个字符之间")
    private String password;

    /** 昵称（可选，为空时默认使用用户名） */
    @Size(max = 64, message = "昵称长度不能超过 64 个字符")
    private String nickname;

    /** 手机号（可选） */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 邮箱（可选） */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
    private String email;

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

    /**
     * 注意：刻意不输出 password，避免明文密码进入日志。
     */
    @Override
    public String toString() {
        return "RegisterRequest{" +
                "username='" + username + '\'' +
                ", nickname='" + nickname + '\'' +
                ", phone='" + phone + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}
