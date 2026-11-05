package com.carseller.inventory;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 库存服务启动类。
 *
 * <p>端口 8084，库 inventory_db，网关路由 {@code /api/inventory/**} → {@code lb://inventory-service}。</p>
 *
 * <p>{@link ComponentScan} 必须显式包含 {@code com.carseller.common}，
 * 否则公共模块的全局异常处理器等组件不会注册。</p>
 *
 * @author car-seller
 */
@SpringBootApplication
@ComponentScan({"com.carseller.inventory", "com.carseller.common"})
@MapperScan("com.carseller.inventory.mapper")
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}