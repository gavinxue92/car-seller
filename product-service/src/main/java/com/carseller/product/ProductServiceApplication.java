package com.carseller.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 商品服务启动类。
 *
 * <p>公共组件（{@code com.carseller.common} 下的 GlobalExceptionHandler 等）位于本服务
 * 启动类所在包之外，因此必须显式声明 {@link ComponentScan} 覆盖两个包，
 * 否则 {@code @RestControllerAdvice} 等公共组件不会被注册。</p>
 *
 * @author car-seller
 */
@SpringBootApplication
@ComponentScan({"com.carseller.product", "com.carseller.common"})
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}