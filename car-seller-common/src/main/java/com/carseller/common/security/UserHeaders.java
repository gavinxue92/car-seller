package com.carseller.common.security;

/**
 * 用户上下文相关的 HTTP 请求头常量。
 *
 * <p>约定（见 README 10.2）：{@code X-User-Id} 是下游服务**唯一**的用户身份来源，
 * 只能由 {@code api-gateway} 校验 JWT 后注入；客户端自带的同名头一律不可信，必须先删除再注入。</p>
 *
 * <p>所在模块：{@code car-seller-common}，各业务微服务禁止复制本类。</p>
 *
 * @author car-seller
 */
public final class UserHeaders {

    /** 用户 ID（JWT 的 sub），下游据此判断资源归属 */
    public static final String X_USER_ID = "X-User-Id";

    /** 用户展示名（JWT 的 userName），网关侧做 URL 编码后透传 */
    public static final String X_USER_NAME = "X-User-Name";

    /** 全链路请求 ID，由网关生成 */
    public static final String X_REQUEST_ID = "X-Request-Id";

    /** 携带 Token 的请求头 */
    public static final String AUTHORIZATION = "Authorization";

    /** Token 前缀 */
    public static final String BEARER_PREFIX = "Bearer ";

    private UserHeaders() {
    }
}
