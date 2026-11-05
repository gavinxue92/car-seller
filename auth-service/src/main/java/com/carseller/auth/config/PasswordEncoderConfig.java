package com.carseller.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码哈希配置。
 *
 * <p>按 README 10.4 要求，密码必须使用 <b>BCrypt</b>（cost &gt;= 10）存储，
 * 禁止 MD5/SHA1 等可逆或弱哈希。BCrypt 自带随机盐，同一明文多次加密结果不同，
 * 校验统一走 {@link PasswordEncoder#matches(CharSequence, String)}。</p>
 *
 * @author car-seller
 */
@Configuration
public class PasswordEncoderConfig {

    /** BCrypt 强度（cost），生产环境不得低于 10 */
    private static final int BCRYPT_STRENGTH = 10;

    /**
     * BCrypt 密码编码器。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
