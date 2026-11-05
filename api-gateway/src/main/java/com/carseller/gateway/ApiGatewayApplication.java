package com.carseller.gateway;

import com.carseller.common.security.JwtProperties;
import com.carseller.gateway.config.GatewayAuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 网关服务启动类（唯一入口，端口 9000）。
 *
 * <p>职责边界：只做路由转发、JWT 鉴权、限流，禁止业务逻辑、禁止连接数据库。</p>
 *
 * <p>说明：本服务基于 WebFlux，<b>不</b>对 {@code com.carseller.common} 做 {@code @ComponentScan}——
 * 该包下的 {@code GlobalExceptionHandler} 属于 Servlet 体系（{@code @RestControllerAdvice}），
 * 在 WebFlux 环境下不应被注册；网关只复用公共模块中的 {@code R}/{@code ResultCode} 与
 * {@code com.carseller.common.security} 下的 JWT 工具、请求头常量（均为无状态类，不依赖组件扫描）。</p>
 *
 * <p>同时严禁引入 {@code spring-boot-starter-web}，否则会因 Spring MVC 与 Gateway 冲突而启动失败。</p>
 *
 * @author car-seller
 */
@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, GatewayAuthProperties.class})
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}