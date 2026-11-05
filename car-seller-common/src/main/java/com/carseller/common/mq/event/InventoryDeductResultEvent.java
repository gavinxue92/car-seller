package com.carseller.common.mq.event;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 库存扣减结果事件消息体（{@code inventory-deduct-result-topic}，
 * Tag = {@code DEDUCT_SUCCESS} / {@code DEDUCT_FAILED}）。
 *
 * <p>生产方：inventory-service；消费方：order-service。order-service 依据本事件推进订单状态
 * （成功 → 待支付，失败 → 关单），消费端同样以 {@code orderId} 做幂等。</p>
 *
 * <p>JSON 结构固定为（<b>只增不改</b>）：</p>
 * <pre>
 * {
 *   "orderId": 1001,
 *   "success": true,
 *   "reason": "库存扣减成功",
 *   "items": [ { "productId": 1, "required": 1, "before": 50, "after": 49 } ],
 *   "finishedAt": 1762308000000
 * }
 * </pre>
 *
 * <p>失败场景（如库存不足）{@code before} 为扣减前可用库存快照、{@code after} 可为 {@code null}。</p>
 *
 * <p>所在模块：{@code car-seller-common}，生产方与消费方共用本类，禁止在业务服务内复制。</p>
 *
 * @author car-seller
 */
public class InventoryDeductResultEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单号，同时作为消息 Key */
    private Long orderId;

    /** 扣减是否成功 */
    private Boolean success;

    /** 结果说明：成功提示或失败原因（如「商品[1]库存不足，可用 3，需要 5」） */
    private String reason;

    /** 明细：每个商品的需求量、扣减前后库存 */
    private List<Item> items;

    /** 处理完成时间（毫秒时间戳） */
    private Long finishedAt;

    public InventoryDeductResultEvent() {
    }

    public InventoryDeductResultEvent(Long orderId, Boolean success, String reason, List<Item> items, Long finishedAt) {
        this.orderId = orderId;
        this.success = success;
        this.reason = reason;
        this.items = items;
        this.finishedAt = finishedAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public Long getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Long finishedAt) {
        this.finishedAt = finishedAt;
    }

    @Override
    public String toString() {
        return "InventoryDeductResultEvent{" +
                "orderId=" + orderId +
                ", success=" + success +
                ", reason='" + reason + '\'' +
                ", items=" + items +
                ", finishedAt=" + finishedAt +
                '}';
    }

    /**
     * 库存扣减结果明细（{@code items} 数组元素）。
     */
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 商品 ID */
        private Long productId;

        /** 本次需求量 */
        private Integer required;

        /** 扣减前可用库存（失败场景为快照值） */
        private Integer before;

        /** 扣减后可用库存（失败场景可为 null） */
        private Integer after;

        public Item() {
        }

        public Item(Long productId, Integer required, Integer before, Integer after) {
            this.productId = productId;
            this.required = required;
            this.before = before;
            this.after = after;
        }

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public Integer getRequired() {
            return required;
        }

        public void setRequired(Integer required) {
            this.required = required;
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
                    ", required=" + required +
                    ", before=" + before +
                    ", after=" + after +
                    '}';
        }
    }
}
