package com.carseller.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.carseller.auth.constant.UserConstants;
import com.carseller.auth.dto.LoginRequest;
import com.carseller.auth.dto.RegisterRequest;
import com.carseller.auth.dto.UserResponse;
import com.carseller.auth.dto.UserUpdateRequest;
import com.carseller.auth.entity.User;
import com.carseller.auth.entity.UserLoginLog;
import com.carseller.auth.mapper.UserLoginLogMapper;
import com.carseller.auth.mapper.UserMapper;
import com.carseller.auth.service.AuthService;
import com.carseller.auth.support.JwtTokenProvider;
import com.carseller.common.exception.BusinessException;
import com.carseller.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 认证与用户信息业务实现。
 *
 * <p>要点：</p>
 * <ul>
 *     <li>密码只存 BCrypt 哈希；登录失败统一提示「用户名或密码错误」，不区分账号不存在与密码错误（防账号枚举）；</li>
 *     <li>登录成功/失败均写入 {@code user_login_logs} 审计日志，审计失败不影响主流程；</li>
 *     <li>用户身份只来自网关透传的 {@code X-User-Id}，禁止从请求体取 userId（防越权）。</li>
 * </ul>
 *
 * @author car-seller
 */
@Service
public class AuthServiceImpl extends ServiceImpl<UserMapper, User> implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    /** 审计日志字段截断长度，与 sql/auth_db.sql 中列定义保持一致 */
    private static final int MAX_IP_LENGTH = 64;

    private static final int MAX_USER_AGENT_LENGTH = 500;

    private static final int MAX_FAIL_REASON_LENGTH = 255;

    private final PasswordEncoder passwordEncoder;

    private final JwtTokenProvider jwtTokenProvider;

    private final UserLoginLogMapper userLoginLogMapper;

    public AuthServiceImpl(PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           UserLoginLogMapper userLoginLogMapper) {
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userLoginLogMapper = userLoginLogMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse register(RegisterRequest request) {
        String username = trimToNull(request.getUsername());
        String phone = trimToNull(request.getPhone());
        String email = trimToNull(request.getEmail());
        checkUsernameAvailable(username);
        checkPhoneAvailable(phone, null);
        checkEmailAvailable(email, null);

        String nickname = trimToNull(request.getNickname());
        User user = new User();
        user.setUsername(username);
        // 只保存 BCrypt 哈希，明文密码不落库、不写日志
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(nickname == null ? username : nickname);
        user.setPhone(phone);
        user.setEmail(email);
        user.setRole(UserConstants.ROLE_USER);
        user.setStatus(UserConstants.STATUS_ENABLED);
        user.setPasswordUpdatedAt(LocalDateTime.now());

        if (!save(user)) {
            throw new BusinessException("用户注册失败，请稍后重试");
        }
        log.info("用户注册成功: id={}, username={}", user.getId(), user.getUsername());
        return UserResponse.from(getById(user.getId()));
    }

    @Override
    public String login(LoginRequest request, String loginIp, String userAgent) {
        String username = trimToNull(request.getUsername());
        User user = loadByUsername(username);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordLoginLog(user == null ? null : user.getId(), username, loginIp, userAgent,
                    UserConstants.LOGIN_RESULT_FAILURE, "用户名或密码错误");
            log.warn("登录失败：账号或密码错误, username={}, ip={}", username, loginIp);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != UserConstants.STATUS_ENABLED) {
            recordLoginLog(user.getId(), username, loginIp, userAgent,
                    UserConstants.LOGIN_RESULT_FAILURE, "账号已被禁用");
            log.warn("登录失败：账号已被禁用, id={}, username={}", user.getId(), username);
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }

        refreshLastLogin(user.getId(), loginIp);
        recordLoginLog(user.getId(), user.getUsername(), loginIp, userAgent,
                UserConstants.LOGIN_RESULT_SUCCESS, null);

        // 校验通过：由 car-seller-common 的 JwtUtils 签发 JWT（本服务不复制 JWT 工具类）
        String token = jwtTokenProvider.issueToken(user.getId(), displayName(user));
        log.info("用户登录成功: id={}, username={}, ip={}", user.getId(), user.getUsername(), loginIp);
        return token;
    }

    @Override
    public UserResponse getCurrentUser(Long userId) {
        return UserResponse.from(requireUser(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse updateCurrentUser(Long userId, UserUpdateRequest request) {
        User existing = requireUser(userId);

        LambdaUpdateWrapper<User> wrapper = Wrappers.<User>lambdaUpdate().eq(User::getId, existing.getId());
        boolean changed = false;

        String nickname = trimToNull(request.getNickname());
        if (nickname != null) {
            wrapper.set(User::getNickname, nickname);
            changed = true;
        }
        if (request.getPhone() != null) {
            String phone = trimToNull(request.getPhone());
            checkPhoneAvailable(phone, existing.getId());
            wrapper.set(User::getPhone, phone);
            changed = true;
        }
        if (request.getEmail() != null) {
            String email = trimToNull(request.getEmail());
            checkEmailAvailable(email, existing.getId());
            wrapper.set(User::getEmail, email);
            changed = true;
        }
        String avatarUrl = trimToNull(request.getAvatarUrl());
        if (avatarUrl != null) {
            wrapper.set(User::getAvatarUrl, avatarUrl);
            changed = true;
        }

        if (!changed) {
            // 没有任何可更新字段时直接返回当前信息，避免空 SET 语句
            return UserResponse.from(existing);
        }
        if (!update(wrapper)) {
            throw new BusinessException("用户信息更新失败，请稍后重试");
        }
        log.info("更新用户信息成功: id={}", existing.getId());
        return UserResponse.from(getById(existing.getId()));
    }

    /**
     * 按用户名查询未删除的用户。
     */
    private User loadByUsername(String username) {
        if (username == null) {
            return null;
        }
        return getOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username), false);
    }

    /**
     * 查询当前用户：身份缺失按 401、用户不存在按 404 处理。
     */
    private User requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户身份，请先登录");
        }
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在，id=" + userId);
        }
        return user;
    }

    private void checkUsernameAvailable(String username) {
        if (existsBy(User::getUsername, username)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名已被占用：" + username);
        }
    }

    /**
     * 手机号唯一性校验；{@code excludeUserId} 为修改场景下的自身 ID，避免与自己冲突。
     */
    private void checkPhoneAvailable(String phone, Long excludeUserId) {
        if (phone == null) {
            return;
        }
        User other = getOne(Wrappers.<User>lambdaQuery().eq(User::getPhone, phone), false);
        if (other != null && !other.getId().equals(excludeUserId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "手机号已被占用：" + phone);
        }
    }

    /**
     * 邮箱唯一性校验；{@code excludeUserId} 为修改场景下的自身 ID，避免与自己冲突。
     */
    private void checkEmailAvailable(String email, Long excludeUserId) {
        if (email == null) {
            return;
        }
        User other = getOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email), false);
        if (other != null && !other.getId().equals(excludeUserId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邮箱已被占用：" + email);
        }
    }

    private boolean existsBy(SFunction<User, ?> column, Object value) {
        return count(Wrappers.<User>lambdaQuery().eq(column, value)) > 0;
    }

    /**
     * 刷新最近登录时间与 IP（仅更新这两列）。
     */
    private void refreshLastLogin(Long userId, String loginIp) {
        User update = new User();
        update.setId(userId);
        update.setLastLoginAt(LocalDateTime.now());
        update.setLastLoginIp(truncate(loginIp, MAX_IP_LENGTH));
        if (!updateById(update)) {
            log.warn("刷新最近登录信息失败（不影响本次登录）: id={}", userId);
        }
    }

    /**
     * 写入登录审计日志：审计失败只告警，不能影响登录主流程。
     */
    private void recordLoginLog(Long userId, String username, String loginIp, String userAgent,
                                int loginResult, String failReason) {
        try {
            UserLoginLog entry = new UserLoginLog();
            entry.setUserId(userId);
            entry.setUsername(truncate(username, UserConstants.USERNAME_MAX_LENGTH));
            entry.setLoginIp(truncate(loginIp, MAX_IP_LENGTH));
            entry.setUserAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH));
            entry.setLoginResult(loginResult);
            entry.setFailReason(truncate(failReason, MAX_FAIL_REASON_LENGTH));
            userLoginLogMapper.insert(entry);
        } catch (Exception e) {
            // 审计日志属于旁路能力：失败只记录告警，不回滚、不阻断登录
            log.warn("写入登录日志失败: username={}, loginResult={}", username, loginResult, e);
        }
    }

    /**
     * JWT 中的展示名：优先昵称，其次用户名。
     */
    private static String displayName(User user) {
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * 去除首尾空白，空白串统一转为 null。
     */
    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
