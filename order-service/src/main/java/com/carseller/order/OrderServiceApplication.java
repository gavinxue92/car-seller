package com.carseller.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单服务启动类。
 *
 * <p>公共组件（{@code com.carseller.common} 下的 GlobalExceptionHandler、MQ 常量与事件契约）
 * 位于本服务启动类所在包之外，因此必须显式声明 {@link ComponentScan} 覆盖两个包，
 * 否则 {@code @RestControllerAdvice} 等公共组件不会被注册。</p>
 *
 * <p>远程调用 {@code product-service} 的 Feign 客户端统一放在
 * {@code com.carseller.order.client} 包下，由 {@link EnableFeignClients} 扫描注册。</p>
 *
 * <p>定时任务（超时未支付关单、本地消息补偿重发，README Phase 3 第 5/6 条）依赖
 * {@link EnableScheduling}，对应 {@code com.carseller.order.scheduler.OrderMaintenanceScheduler}。</p>
 *
 * @author car-seller
 */
@SpringBootApplication
@ComponentScan({"com.carseller.order", "com.carseller.common"})
@EnableFeignClients(basePackages = "com.carseller.order.client")
@EnableScheduling
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
