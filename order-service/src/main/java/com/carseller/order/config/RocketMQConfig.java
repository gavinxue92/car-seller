package com.carseller.order.config;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * RocketMQ 显式装配。
 *
 * <p>rocketmq-spring-boot 2.2.3 仅通过 {@code META-INF/spring.factories} 注册自动配置，
 * 而 Spring Boot 3 只识别 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}，
 * 因此在此显式 {@link Import}，保证 {@code RocketMQTemplate} 与消费者监听容器可用
 * （与 inventory-service 的 {@code RocketMQConfig} 保持一致）。</p>
 *
 * <p>本服务同时扮演生产方（{@code order-created-topic} / {@code order-cancelled-topic}）
 * 与消费方（{@code order-paid-topic} / {@code inventory-deduct-result-topic}）。</p>
 *
 * @author car-seller
 */
@Configuration
@Import(RocketMQAutoConfiguration.class)
public class RocketMQConfig {
}
