package com.carseller.auth.controller;

import com.carseller.auth.dto.LoginRequest;
import com.carseller.auth.dto.RegisterRequest;
import com.carseller.auth.dto.UserResponse;
import com.carseller.auth.dto.UserUpdateRequest;
import com.carseller.auth.service.AuthService;
import com.carseller.common.result.R;
import com.carseller.common.security.UserHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证服务对外 RESTful 接口。
 *
 * <p>统一前缀 {@code /api/auth}，与 api-gateway 中配置的路由
 * {@code Path=/api/auth/**} 保持一致（网关未配置 StripPrefix，直连与经网关访问路径相同）。</p>
 *
 * <ul>
 *     <li>{@code POST /api/auth/register} 注册（网关白名单，免登录）</li>
 *     <li>{@code POST /api/auth/login}    登录并颁发 JWT（网关白名单，免登录）</li>
 *     <li>{@code GET  /api/auth/me}       查询当前用户信息（需登录）</li>
 *     <li>{@code PUT  /api/auth/me}       修改当前用户信息（需登录）</li>
 * </ul>
 *
 * <p>用户身份只从网关透传的 {@code X-User-Id} 读取，客户端自带的同名头会被网关删除，
 * 因此直连本服务端口无法伪造身份（生产环境业务端口不对外暴露）。</p>
 *
 * @author car-seller
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** 无法识别 IP 时的占位值 */
    private static final String UNKNOWN_IP = "unknown";

    /** 多级代理场景下 X-Forwarded-For 的分隔符 */
    private static final String IP_SEPARATOR = ",";

    /** User-Agent 最大记录长度（与审计表列宽一致） */
    private static final int MAX_USER_AGENT_LENGTH = 500;

    /** 客户端真实 IP 请求头（按优先级依次尝试） */
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    private static final String HEADER_X_REAL_IP = "X-Real-IP";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户注册。
     */
    @PostMapping("/register")
    public R<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("用户注册, username={}", request.getUsername());
        return R.ok("注册成功", authService.register(request));
    }

    /**
     * 账号密码登录：校验通过后返回 JWT（放在 {@code R.data} 中）。
     */
    @PostMapping("/login")
    public R<String> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String loginIp = resolveClientIp(servletRequest);
        log.info("用户登录, username={}, ip={}", request.getUsername(), loginIp);
        String token = authService.login(request, loginIp, resolveUserAgent(servletRequest));
        return R.ok("登录成功", token);
    }

    /**
     * 查询当前登录用户信息。
     */
    @GetMapping("/me")
    public R<UserResponse> currentUser(@RequestHeader(value = UserHeaders.X_USER_ID, required = false) Long userId) {
        log.info("查询当前用户信息, userId={}", userId);
        return R.ok(authService.getCurrentUser(userId));
    }

    /**
     * 修改当前登录用户信息（仅更新请求中非空字段）。
     */
    @PutMapping("/me")
    public R<UserResponse> updateCurrentUser(
            @RequestHeader(value = UserHeaders.X_USER_ID, required = false) Long userId,
            @Valid @RequestBody UserUpdateRequest request) {
        log.info("修改当前用户信息, userId={}", userId);
        return R.ok("用户信息更新成功", authService.updateCurrentUser(userId, request));
    }

    /**
     * 解析客户端 IP：优先 X-Forwarded-For 首个地址，其次 X-Real-IP，最后为连接地址。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(IP_SEPARATOR)[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String realIp = request.getHeader(HEADER_X_REAL_IP);
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isBlank() ? UNKNOWN_IP : remoteAddr;
    }

    /**
     * 解析 User-Agent，超长截断，避免超出审计表列宽。
     */
    private String resolveUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= MAX_USER_AGENT_LENGTH
                ? userAgent
                : userAgent.substring(0, MAX_USER_AGENT_LENGTH);
    }
}
