package com.carseller.auth.support;

import com.carseller.common.security.JwtProperties;
import com.carseller.common.security.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT 签发器：包装 {@code car-seller-common} 的 {@link JwtUtils}，统一读取 {@code jwt.*} 配置。
 *
 * <p>约定（README 10.3）：算法 HS256、Claims 含 {@code sub}=userId、{@code userName}、
 * {@code iat}/{@code exp}/{@code jti}；网关侧使用同一份 {@code jwt.secret} 校验，
 * 因此本服务与网关的密钥必须保持一致（均由环境变量 {@code JWT_SECRET} 覆盖）。</p>
 *
 * <p>本类只做「签发」，解析与校验由网关负责，业务服务禁止复制 {@link JwtUtils}。</p>
 *
 * @author car-seller
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final JwtProperties jwtProperties;

    private final Duration ttl;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        // 启动即校验密钥合法性（空值或不足 32 字节直接抛错），避免运行期才发现鉴权不可用
        JwtUtils.hmacKey(jwtProperties.getSecret());
        long expireHours = jwtProperties.getExpireHours() > 0
                ? jwtProperties.getExpireHours()
                : JwtProperties.DEFAULT_EXPIRE_HOURS;
        this.ttl = Duration.ofHours(expireHours);
        if (JwtProperties.DEFAULT_SECRET.equals(jwtProperties.getSecret())) {
            log.warn("auth-service 正在使用默认 jwt.secret（仅限本地开发），生产环境请通过环境变量 JWT_SECRET 覆盖");
        }
        log.info("JWT 签发器已就绪：有效期 {} 小时", expireHours);
    }

    /**
     * 签发 Access Token。
     *
     * @param userId   用户 ID，写入 JWT {@code sub}
     * @param userName 用户展示名（昵称），写入 {@code userName} 声明
     * @return 紧凑格式 JWT
     */
    public String issueToken(Long userId, String userName) {
        return JwtUtils.generateToken(userId, userName, jwtProperties.getSecret(), ttl);
    }

    /**
     * Token 有效期（秒），用于前端倒计时或后续 Redis 黑名单 TTL。
     */
    public long getExpireSeconds() {
        return ttl.toSeconds();
    }
}
