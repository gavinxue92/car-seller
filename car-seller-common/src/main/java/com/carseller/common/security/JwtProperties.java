package com.carseller.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置项（对应 application.yml 中的 {@code jwt.*}）。
 *
 * <p>密钥必须通过环境变量 {@code JWT_SECRET} 或 Nacos 配置中心注入，
 * 代码与仓库中禁止出现生产密钥；{@link #DEFAULT_SECRET} 仅用于本地开发。</p>
 *
 * <p>使用方式：启动类上声明
 * {@code @EnableConfigurationProperties(JwtProperties.class)} 完成注册。</p>
 *
 * <p>所在模块：{@code car-seller-common}，各业务微服务禁止复制本类。</p>
 *
 * @author car-seller
 */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 本地开发默认密钥（生产必须用环境变量 JWT_SECRET 覆盖） */
    public static final String DEFAULT_SECRET = "car-seller-dev-secret-key-please-change-in-prod-32bytes";

    /** 默认有效期（小时） */
    public static final long DEFAULT_EXPIRE_HOURS = 2L;

    /** HS256 对称签名密钥，长度须 >= 32 字节 */
    private String secret = DEFAULT_SECRET;

    /** Token 有效期（小时），默认 2 小时 */
    private long expireHours = DEFAULT_EXPIRE_HOURS;

    /** 携带 Token 的请求头名 */
    private String header = UserHeaders.AUTHORIZATION;

    /** Token 前缀（含尾部空格） */
    private String prefix = UserHeaders.BEARER_PREFIX;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpireHours() {
        return expireHours;
    }

    public void setExpireHours(long expireHours) {
        this.expireHours = expireHours;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
