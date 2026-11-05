package com.carseller.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品新增/修改请求参数。
 *
 * @author car-seller
 */
public class ProductRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品名称 */
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 255, message = "商品名称长度不能超过 255 个字符")
    private String name;

    /** 商品描述 */
    @Size(max = 2000, message = "商品描述长度不能超过 2000 个字符")
    private String description;

    /** 商品价格 */
    @NotNull(message = "商品价格不能为空")
    @DecimalMin(value = "0.00", message = "商品价格不能小于 0")
    @Digits(integer = 8, fraction = 2, message = "商品价格最多 8 位整数、2 位小数")
    private BigDecimal price;

    /** 库存数量 */
    @NotNull(message = "商品库存不能为空")
    @Min(value = 0, message = "商品库存不能小于 0")
    private Integer stock;

    /** 商品分类 */
    @Size(max = 100, message = "商品分类长度不能超过 100 个字符")
    private String category;

    /** 商品图片地址 */
    @Size(max = 500, message = "商品图片地址长度不能超过 500 个字符")
    private String imageUrl;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
}
