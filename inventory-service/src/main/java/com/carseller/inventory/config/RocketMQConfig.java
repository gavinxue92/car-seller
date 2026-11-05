package com.carseller.inventory.config;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * RocketMQ 显式装配。
 *
 * <p>rocketmq-spring-boot 2.2.3 仅通过 {@code META-INF/spring.factories} 注册自动配置，
 * 而 Spring Boot 3 只识别 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}，
 * 因此在此显式 {@link Import}，保证 {@code RocketMQTemplate} 与消费者监听容器可用。</p>
 *
 * <p>版本仍由项目基线固定为 2.2.3（不升版本），避免与其他服务产物不一致。</p>
 *
 * @author car-seller
 */
@Configuration
@Import(RocketMQAutoConfiguration.class)
public class RocketMQConfig {
}
