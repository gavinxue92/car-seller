package com.carseller.cart.client.dto;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品信息（product-service {@code GET /api/products/{id}} 的 {@code data} 结构）。
 *
 * <p>字段以被调用方的接口语义为准（README 8.7），只取购物车需要的字段，
 * 多余字段由 Jackson 忽略。</p>
 *
 * @author car-seller
 */
public class ProductResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品 ID */
    private Long id;

    /** 商品名称 */
    private String name;

    /** 商品价格（最新价，购物车不落库，读取时实时取） */
    private BigDecimal price;

    /** 展示用库存（库存真源在 inventory-service） */
    private Integer stock;

    /** 商品分类 */
    private String category;

    /** 商品图片地址 */
    private String imageUrl;

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    @Override
    public String toString() {
        return "ProductResponse{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", stock=" + stock +
                ", category='" + category + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                '}';
    }
}
