package com.carseller.common.security;

/**
 * JWT 校验通过后的用户上下文载体。
 *
 * <p>只承载网关与业务服务需要的身份字段，不含原始 Token 文本（禁止把 Token 写日志）。</p>
 *
 * <p>所在模块：{@code car-seller-common}，各业务微服务禁止复制本类。</p>
 *
 * @author car-seller
 */
public class JwtPayload {

    /** JWT sub：用户 ID */
    private final Long userId;

    /** JWT userName：用户展示名 */
    private final String userName;

    /** JWT jti：Token 唯一 ID（用于登出黑名单） */
    private final String tokenId;

    /** 过期时间戳（毫秒） */
    private final Long expireAt;

    public JwtPayload(Long userId, String userName, String tokenId, Long expireAt) {
        this.userId = userId;
        this.userName = userName;
        this.tokenId = tokenId;
        this.expireAt = expireAt;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getTokenId() {
        return tokenId;
    }

    public Long getExpireAt() {
        return expireAt;
    }

    @Override
    public String toString() {
        return "JwtPayload{" +
                "userId=" + userId +
                ", userName='" + userName + '\'' +
                ", tokenId='" + tokenId + '\'' +
                ", expireAt=" + expireAt +
                '}';
    }
}
