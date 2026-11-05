package com.carseller.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类（HS256 对称签名，jjwt 0.11.5 实现）。
 *
 * <p>约定（见 README 10.3）：</p>
 * <ul>
 *     <li>Claims：{@code sub}=userId（字符串）、{@code userName}、{@code iat}、{@code exp}、{@code jti}</li>
 *     <li>算法：HS256，密钥长度须 &gt;= 32 字节</li>
 *     <li>有效期：默认 2 小时（{@link JwtProperties#getExpireHours()}）</li>
 * </ul>
 *
 * <p>本类为无状态静态工具：网关只做「解析 + 校验」，签发由后续用户中心/登录接口负责。
 * 校验失败统一抛 {@link io.jsonwebtoken.JwtException} 的子类
 * （过期抛 {@link io.jsonwebtoken.ExpiredJwtException}），由调用方转换为 401。</p>
 *
 * <p>所在模块：{@code car-seller-common}，各业务微服务禁止复制本类。</p>
 *
 * @author car-seller
 */
public final class JwtUtils {

    /** JWT 中承载用户展示名的自定义 Claim */
    public static final String CLAIM_USER_NAME = "userName";

    /** HS256 允许的最短密钥长度（字节） */
    public static final int MIN_SECRET_BYTES = 32;

    private JwtUtils() {
    }

    /**
     * 由配置的密钥字符串构造 HS256 签名密钥。
     *
     * @param secret 密钥（&gt;= 32 字节）
     * @return 签名密钥
     * @throws IllegalArgumentException 密钥为空或长度不足时抛出，属配置错误，应在启动阶段暴露
     */
    public static SecretKey hmacKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("jwt.secret 未配置");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("jwt.secret 长度不足 " + MIN_SECRET_BYTES + " 字节，无法用于 HS256");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 签发 Token（登录接口使用）。
     *
     * @param userId   用户 ID，写入 {@code sub}
     * @param userName 用户展示名，可为 null
     * @param secret   密钥
     * @param ttl      有效期
     * @return 紧凑格式的 JWT
     */
    public static String generateToken(Long userId, String userName, String secret, Duration ttl) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Token 有效期必须大于 0");
        }
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + ttl.toMillis());
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim(CLAIM_USER_NAME, userName)
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(issuedAt)
                .setExpiration(expiration)
                .signWith(hmacKey(secret), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 校验 Token 的签名与有效期，并解析出用户上下文。
     *
     * @param token 紧凑格式的 JWT
     * @param secret 密钥
     * @return 用户上下文
     * @throws io.jsonwebtoken.ExpiredJwtException Token 已过期
     * @throws io.jsonwebtoken.JwtException        签名错误、结构非法等
     * @throws IllegalArgumentException            参数缺失
     */
    public static JwtPayload parseToken(String token, String secret) {
        return parseToken(token, hmacKey(secret));
    }

    /**
     * 校验 Token 的签名与有效期，并解析出用户上下文（复用已构造的密钥，适合网关热路径）。
     *
     * @param token 紧凑格式的 JWT
     * @param key   HS256 签名密钥
     * @return 用户上下文
     */
    public static JwtPayload parseToken(String token, SecretKey key) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token 不能为空");
        }
        if (key == null) {
            throw new IllegalArgumentException("JWT 签名密钥不能为空");
        }
        Jws<Claims> jws = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
        return toPayload(jws.getBody());
    }

    private static JwtPayload toPayload(Claims claims) {
        Long userId = parseUserId(claims.getSubject());
        Object rawUserName = claims.get(CLAIM_USER_NAME);
        String userName = rawUserName == null ? null : String.valueOf(rawUserName);
        Date expiration = claims.getExpiration();
        return new JwtPayload(userId, userName, claims.getId(), expiration == null ? null : expiration.getTime());
    }

    private static Long parseUserId(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new MalformedJwtException("Token 缺少用户标识（sub）");
        }
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            throw new MalformedJwtException("Token 中的用户标识不合法：" + subject);
        }
    }
}
