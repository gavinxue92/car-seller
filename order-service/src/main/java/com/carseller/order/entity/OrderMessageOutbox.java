package com.carseller.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 本地消息表实体（{@code order_db.order_message_outbox}，README 6.5 / 7.4）。
 *
 * <p>作用：消除「订单已落库、消息未发出」的窗口。消息<b>与订单状态变更在同一个本地事务内</b>登记，
 * 事务提交后立即尝试发送；发送失败按指数退避重试 3 次，仍失败则置 {@code FAILED}，
 * 由定时补偿扫描继续重发（见 {@code OrderOutboxService}）。</p>
 *
 * <p>字段设计理由：</p>
 * <ul>
 *     <li>{@code orderId + tag} 唯一键：同一订单同一类事件只允许一条记录，天然保证
 *         「超时/用户取消只发一次 order-cancelled-topic」；</li>
 *     <li>{@code topic / tag / messageKey}：发送所需的完整目的地信息，不依赖订单表当前状态，
 *         补偿时可直接构造 {@code topic:tag} 目的地与 {@code RocketMQHeaders.KEYS}；</li>
 *     <li>{@code payload}：事件 JSON 快照。补偿重发使用快照而不是重新查订单表，
 *         避免订单后续状态变化导致消息体漂移；</li>
 *     <li>{@code status + nextRetryAt + retryCount}：支撑「失败重试 3 次（指数退避）」与
 *         「定时扫描补发」（{@code WHERE status IN ('PENDING','FAILED') AND next_retry_at <= now()}）；</li>
 *     <li>{@code status} 单独建索引，满足「按状态可查询」的排查/告警需求；</li>
 *     <li>{@code lastError / sentAt}：失败定位与发送时间审计。</li>
 * </ul>
 *
 * <p>{@code status} 取值见 {@code OrderConstants.OUTBOX_STATUS_*}：PENDING / SENT / FAILED。</p>
 *
 * @author car-seller
 */
@TableName("order_message_outbox")
public class OrderMessageOutbox implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 业务主键：订单 ID（同时作为 MQ 消息 Key，等值于 message_key） */
    @TableField("order_id")
    private Long orderId;

    /** 目标 Topic */
    @TableField("topic")
    private String topic;

    /** 目标 Tag */
    @TableField("tag")
    private String tag;

    /** MQ 消息 Key（恒为 orderId，独立存储便于按业务键排查） */
    @TableField("message_key")
    private String messageKey;

    /** 事件 JSON 快照（补偿重发直接使用，不再回查订单） */
    @TableField("payload")
    private String payload;

    /** 发送状态：PENDING / SENT / FAILED */
    @TableField("status")
    private String status;

    /** 累计发送尝试次数（立即重试 + 补偿重发的总次数） */
    @TableField("retry_count")
    private Integer retryCount;

    /** 下次允许重发的时间（指数退避：1 → 2 → 4 … 分钟，上限 30 分钟） */
    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;

    /** 最近一次发送失败原因 */
    @TableField("last_error")
    private String lastError;

    /** 发送成功时间 */
    @TableField("sent_at")
    private LocalDateTime sentAt;

    /** 创建时间（数据库默认值维护） */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间（数据库 ON UPDATE 维护） */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

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

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "OrderMessageOutbox{" +
                "id=" + id +
                ", orderId=" + orderId +
                ", topic='" + topic + '\'' +
                ", tag='" + tag + '\'' +
                ", status='" + status + '\'' +
                ", retryCount=" + retryCount +
                ", nextRetryAt=" + nextRetryAt +
                ", sentAt=" + sentAt +
                '}';
    }
}
