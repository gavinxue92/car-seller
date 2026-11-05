package com.carseller.cart.dto;

import com.carseller.cart.client.dto.ProductResponse;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 购物车条目响应。
 *
 * <p>Redis 只保存 {@code productId -> 数量}，商品名称、价格等信息在读取时通过 Feign 实时获取，
 * 因此商品已被删除或商品服务不可用时 {@code available=false}，其余商品字段为 {@code null}。</p>
 *
 * @author car-seller
 */
public class CartItemResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品 ID */
    private Long productId;

    /** 商品名称（实时取自 product-service） */
    private String productName;

    /** 商品图片地址 */
    private String imageUrl;

    /** 商品最新单价（Redis 中不保存价格，避免价格漂移） */
    private BigDecimal price;

    /** 展示用库存 */
    private Integer stock;

    /** 数量 */
    private Integer quantity;

    /** 是否选中 */
    private Boolean selected;

    /** 商品信息是否可用（false 表示商品已删除或商品服务不可用） */
    private Boolean available;

    /** 小计 = price × quantity，商品不可用时为 null */
    private BigDecimal subtotal;

    /**
     * 构建可用的购物车条目。
     *
     * @param product  商品最新信息
     * @param quantity 数量
     * @param selected 是否选中
     * @return 条目响应
     */
    public static CartItemResponse of(ProductResponse product, int quantity, boolean selected) {
        CartItemResponse response = new CartItemResponse();
        response.setProductId(product.getId());
        response.setProductName(product.getName());
        response.setImageUrl(product.getImageUrl());
        response.setPrice(product.getPrice());
        response.setStock(product.getStock());
        response.setQuantity(quantity);
        response.setSelected(selected);
        response.setAvailable(Boolean.TRUE);
        response.setSubtotal(product.getPrice() == null
                ? null
                : product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return response;
    }

    /**
     * 构建商品信息不可用的购物车条目（商品已删除或商品服务暂时不可用）。
     *
     * @param productId 商品 ID
     * @param quantity  数量
     * @param selected  是否选中
     * @return 条目响应
     */
    public static CartItemResponse unavailable(Long productId, int quantity, boolean selected) {
        CartItemResponse response = new CartItemResponse();
        response.setProductId(productId);
        response.setQuantity(quantity);
        response.setSelected(selected);
        response.setAvailable(Boolean.FALSE);
        return response;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
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

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Boolean getSelected() {
        return selected;
    }

    public void setSelected(Boolean selected) {
        this.selected = selected;
    }

    public Boolean getAvailable() {
        return available;
    }

    public void setAvailable(Boolean available) {
        this.available = available;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    @Override
    public String toString() {
        return "CartItemResponse{" +
                "productId=" + productId +
                ", productName='" + productName + '\'' +
                ", price=" + price +
                ", quantity=" + quantity +
                ", selected=" + selected +
                ", available=" + available +
                ", subtotal=" + subtotal +
                '}';
    }
}
