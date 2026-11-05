package com.carseller.order.client.dto;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品信息（product-service {@code GET /api/products/{id}} 的 {@code data} 结构）。
 *
 * <p>下单只依赖商品名称快照与最新价格，其余字段一律不取，多余字段由 Jackson 忽略。
 * 商品库与本服务的订单库互不直连（README 4.2），价格必须实时从 product-service 获取。</p>
 *
 * @author car-seller
 */
public class ProductResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品 ID */
    private Long id;

    /** 商品名称（写入 order_items.product_name 快照） */
    private String name;

    /** 商品最新价格（下单成交价） */
    private BigDecimal price;

    /** 展示用库存（库存真源在 inventory-service，下单不以此为准） */
    private Integer stock;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    @Override
    public String toString() {
        return "ProductResponse{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", stock=" + stock +
                '}';
    }
}
