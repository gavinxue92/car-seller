package com.carseller.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单明细实体（{@code order_db.order_items}）。
 *
 * <p>下单时对商品名称与单价做<b>快照</b>：商品后续改价、改名不影响历史订单展示。</p>
 *
 * @author car-seller
 */
@TableName("order_items")
public class OrderItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 明细 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属订单 ID */
    @TableField("order_id")
    private Long orderId;

    /** 商品 ID */
    @TableField("product_id")
    private Long productId;

    /** 商品名称快照 */
    @TableField("product_name")
    private String productName;

    /** 成交单价快照 */
    @TableField("price")
    private BigDecimal price;

    /** 购买数量 */
    @TableField("quantity")
    private Integer quantity;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return "OrderItem{" +
                "id=" + id +
                ", orderId=" + orderId +
                ", productId=" + productId +
                ", productName='" + productName + '\'' +
                ", price=" + price +
                ", quantity=" + quantity +
                '}';
    }
}
