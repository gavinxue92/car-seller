package com.carseller.product.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品分页查询参数（通过 URL 查询串绑定，如
 * {@code GET /api/products?pageNum=1&pageSize=10&keyword=iPhone&category=手机}）。
 *
 * @author car-seller
 */
public class ProductQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 页码，从 1 开始，默认 1 */
    private Integer pageNum = 1;

    /** 每页条数，默认 10，最大 100 */
    private Integer pageSize = 10;

    /** 关键字：模糊匹配商品名称或描述 */
    private String keyword;

    /** 商品分类：精确匹配 */
    private String category;

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
