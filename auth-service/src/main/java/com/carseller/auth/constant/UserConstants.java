package com.carseller.auth.constant;

/**
 * 用户域常量：禁止在业务代码中散落魔法值（状态、角色、登录结果）。
 *
 * @author car-seller
 */
public final class UserConstants {

    /** 默认角色：普通用户 */
    public static final String ROLE_USER = "USER";

    /** 管理员角色 */
    public static final String ROLE_ADMIN = "ADMIN";

    /** 账号状态：启用（可登录） */
    public static final int STATUS_ENABLED = 1;

    /** 账号状态：禁用（禁止登录） */
    public static final int STATUS_DISABLED = 0;

    /** 登录日志结果：成功 */
    public static final int LOGIN_RESULT_SUCCESS = 1;

    /** 登录日志结果：失败 */
    public static final int LOGIN_RESULT_FAILURE = 0;

    /** 未删除标记 */
    public static final int NOT_DELETED = 0;

    /** 用户名最大长度 */
    public static final int USERNAME_MAX_LENGTH = 64;

    /** 密码最短长度 */
    public static final int PASSWORD_MIN_LENGTH = 6;

    /** 密码最长长度（BCrypt 只取前 72 字节，超长直接拒绝，避免静默截断） */
    public static final int PASSWORD_MAX_LENGTH = 72;

    private UserConstants() {
    }
}
