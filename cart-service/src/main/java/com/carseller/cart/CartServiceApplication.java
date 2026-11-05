package com.carseller.cart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 购物车服务启动类。
 *
 * <p>公共组件（{@code com.carseller.common} 下的 GlobalExceptionHandler 等）位于本服务
 * 启动类所在包之外，因此必须显式声明 {@link ComponentScan} 覆盖两个包，
 * 否则 {@code @RestControllerAdvice} 等公共组件不会被注册。</p>
 *
 * <p>远程调用 {@code product-service} 的 Feign 客户端统一放在
 * {@code com.carseller.cart.client} 包下，由 {@link EnableFeignClients} 扫描注册。</p>
 *
 * @author car-seller
 */
@SpringBootApplication
@ComponentScan({"com.carseller.cart", "com.carseller.common"})
@EnableFeignClients(basePackages = "com.carseller.cart.client")
public class CartServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CartServiceApplication.class, args);
    }
}