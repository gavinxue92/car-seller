package com.carseller.auth;

import com.carseller.common.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * 认证服务启动类（端口 8086，服务名 auth-service）。
 *
 * <p>公共组件（{@code com.carseller.common} 下的 GlobalExceptionHandler 等）位于本服务
 * 启动类所在包之外，因此必须显式声明 {@link ComponentScan} 覆盖两个包，
 * 否则 {@code @RestControllerAdvice} 等公共组件不会被注册。</p>
 *
 * <p>{@link JwtProperties} 属于 {@code car-seller-common} 的 {@code jwt.*} 配置绑定，
 * 需要在本启动类上显式开启注册；JWT 签发统一使用 common 的
 * {@code com.carseller.common.security.JwtUtils}，本服务禁止复制该类。</p>
 *
 * @author car-seller
 */
@SpringBootApplication
@ComponentScan({"com.carseller.auth", "com.carseller.common"})
@EnableConfigurationProperties(JwtProperties.class)
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
