package com.carseller.inventory.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 库存变更结果（扣减 / 恢复共用，同时作为 MQ 结果事件的数据来源）。
 *
 * @author car-seller
 */
public class InventoryChangeResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单号 */
    private Long orderId;

    /** 本次是否真正发生了库存变更（幂等跳过时为 false） */
    private Boolean changed;

    /** 结果说明 */
    private String message;

    /** 变更明细 */
    private List<Item> items;

    public InventoryChangeResult() {
    }

    public InventoryChangeResult(Long orderId, Boolean changed, String message, List<Item> items) {
        this.orderId = orderId;
        this.changed = changed;
        this.message = message;
        this.items = items;
    }

    /**
     * 构建「已发生变更」的结果。
     */
    public static InventoryChangeResult changed(Long orderId, String message, List<Item> items) {
        return new InventoryChangeResult(orderId, Boolean.TRUE, message, items);
    }

    /**
     * 构建「幂等跳过」的结果。
     */
    public static InventoryChangeResult skipped(Long orderId, String message) {
        return new InventoryChangeResult(orderId, Boolean.FALSE, message, Collections.emptyList());
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Boolean getChanged() {
        return changed;
    }

    public void setChanged(Boolean changed) {
        this.changed = changed;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    @Override
    public String toString() {
        return "InventoryChangeResult{" +
                "orderId=" + orderId +
                ", changed=" + changed +
                ", message='" + message + '\'' +
                ", items=" + items +
                '}';
    }

    /**
     * 变更明细项。
     */
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 商品 ID */
        private Long productId;

        /** 本次变更数量（扣减/回补的绝对值） */
        private Integer quantity;

        /** 变更前库存 */
        private Integer before;

        /** 变更后库存 */
        private Integer after;

        public Item() {
        }

        public Item(Long productId, Integer quantity, Integer before, Integer after) {
            this.productId = productId;
            this.quantity = quantity;
            this.before = before;
            this.after = after;
        }

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public Integer getBefore() {
            return before;
        }

        public void setBefore(Integer before) {
            this.before = before;
        }

        public Integer getAfter() {
            return after;
        }

        public void setAfter(Integer after) {
            this.after = after;
        }

        @Override
        public String toString() {
            return "Item{" +
                    "productId=" + productId +
                    ", quantity=" + quantity +
                    ", before=" + before +
                    ", after=" + after +
                    '}';
        }
    }
}
