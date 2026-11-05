package com.carseller.auth.service;

import com.carseller.auth.dto.LoginRequest;
import com.carseller.auth.dto.RegisterRequest;
import com.carseller.auth.dto.UserResponse;
import com.carseller.auth.dto.UserUpdateRequest;

/**
 * 认证与用户信息业务接口。
 *
 * @author car-seller
 */
public interface AuthService {

    /**
     * 注册新用户（默认角色 USER、状态启用，密码 BCrypt 入库）。
     *
     * @param request 注册参数
     * @return 注册后的用户信息
     */
    UserResponse register(RegisterRequest request);

    /**
     * 账号密码登录，校验通过后颁发 JWT。
     *
     * @param request   登录参数
     * @param loginIp   客户端 IP（仅用于审计日志）
     * @param userAgent 客户端 User-Agent（仅用于审计日志）
     * @return JWT 令牌
     */
    String login(LoginRequest request, String loginIp, String userAgent);

    /**
     * 查询当前登录用户信息。
     *
     * @param userId 网关透传的 X-User-Id
     * @return 用户信息
     */
    UserResponse getCurrentUser(Long userId);

    /**
     * 修改当前登录用户信息（仅更新请求中非空字段）。
     *
     * @param userId  网关透传的 X-User-Id
     * @param request 修改参数
     * @return 更新后的用户信息
     */
    UserResponse updateCurrentUser(Long userId, UserUpdateRequest request);
}
