package com.carseller.order.dto;

import com.carseller.order.constant.OrderConstants;

import java.io.Serial;
import java.io.Serializable;

/**
 * 订单分页查询条件（{@code GET /api/orders} 的 query 参数，README 8.6）。
 *
 * <p>分页默认值与上限的收敛在 Service 层统一处理（pageNum 默认 1，pageSize 默认 10、上限 100），
 * 这里只承载客户端传入的原始值。</p>
 *
 * @author car-seller
 */
public class OrderQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 页码，从 1 开始，默认 1 */
    private Integer pageNum = OrderConstants.DEFAULT_PAGE_NUM;

    /** 每页条数，默认 10，上限 100 */
    private Integer pageSize = OrderConstants.DEFAULT_PAGE_SIZE;

    /** 订单状态过滤（PENDING / PAID / SHIPPED / DELIVERED / CANCELLED），为空表示全部 */
    private String status;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "OrderQuery{" +
                "pageNum=" + pageNum +
                ", pageSize=" + pageSize +
                ", status='" + status + '\'' +
                '}';
    }
}
