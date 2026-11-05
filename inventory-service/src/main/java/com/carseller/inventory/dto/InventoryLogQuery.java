package com.carseller.inventory.dto;

import com.carseller.inventory.constant.InventoryConstants;

import java.io.Serial;
import java.io.Serializable;

/**
 * 库存日志分页查询条件（{@code GET /api/inventory/logs}）。
 *
 * <p>pageNum 默认 1；pageSize 默认 10、上限 100（越界由服务层纠正）。</p>
 *
 * @author car-seller
 */
public class InventoryLogQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品 ID，为空表示不按商品过滤 */
    private Long productId;

    /** 页码，从 1 开始 */
    private long pageNum = InventoryConstants.DEFAULT_PAGE_NUM;

    /** 每页条数 */
    private long pageSize = InventoryConstants.DEFAULT_PAGE_SIZE;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public long getPageNum() {
        return pageNum;
    }

    public void setPageNum(long pageNum) {
        this.pageNum = pageNum;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public String toString() {
        return "InventoryLogQuery{" +
                "productId=" + productId +
                ", pageNum=" + pageNum +
                ", pageSize=" + pageSize +
                '}';
    }
}
