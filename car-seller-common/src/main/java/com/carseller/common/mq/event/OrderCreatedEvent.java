package com.carseller.common.mq.event;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 订单创建事件消息体（{@code order-created-topic}，Tag = {@code ORDER_CREATED}）。
 *
 * <p>契约见 README 7.3，JSON 结构固定为（<b>只增不改</b>）：</p>
 * <pre>
 * {
 *   "orderId": 1001,
 *   "userId": 1,
 *   "totalPrice": 7999.00,
 *   "items": [ { "productId": 1, "quantity": 1 } ],
 *   "createdAt": 1762308000000
 * }
 * </pre>
 *
 * <p>所在模块：{@code car-seller-common}，生产方（order-service）与消费方（inventory-service）
 * 共用本类，禁止各自复制一份，避免契约漂移。</p>
 *
 * @author car-seller
 */
public class OrderCreatedEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单号，同时作为消息 Key（同一订单的消息落到同一队列） */
    private Long orderId;

    /** 下单用户 */
    private Long userId;

    /** 订单总金额（两位小数） */
    private BigDecimal totalPrice;

    /** 订单明细：仅携带扣减库存所需的最小字段 */
    private List<Item> items;

    /** 订单创建时间（毫秒时间戳） */
    private Long createdAt;

    public OrderCreatedEvent() {
    }

    public OrderCreatedEvent(Long orderId, Long userId, BigDecimal totalPrice, List<Item> items, Long createdAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.totalPrice = totalPrice;
        this.items = items;
        this.createdAt = createdAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "OrderCreatedEvent{" +
                "orderId=" + orderId +
                ", userId=" + userId +
                ", totalPrice=" + totalPrice +
                ", items=" + items +
                ", createdAt=" + createdAt +
                '}';
    }

    /**
     * 订单明细载荷（{@code items} 数组元素）。
     */
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 商品 ID */
        private Long productId;

        /** 购买数量 */
        private Integer quantity;

        public Item() {
        }

        public Item(Long productId, Integer quantity) {
            this.productId = productId;
            this.quantity = quantity;
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

        @Override
        public String toString() {
            return "Item{" +
                    "productId=" + productId +
                    ", quantity=" + quantity +
                    '}';
        }
    }
}
