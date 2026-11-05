package com.carseller.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.carseller.order.entity.OrderMessageOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 本地消息表 Mapper（{@code order_db.order_message_outbox}）。
 *
 * <p>状态回写使用<b>条件更新</b>：{@code markSent} 带上 {@code status <> 'SENT'} 条件，
 * 保证并发重复标记（立即发送与补偿扫描同时命中）也只有一次生效。</p>
 *
 * @author car-seller
 */
@Mapper
public interface OrderMessageOutboxMapper extends BaseMapper<OrderMessageOutbox> {

    /**
     * 标记消息已发送（已发送的记录不会被重复改写）。
     *
     * @param id         主键
     * @param sentStatus 已发送状态值（避免 SQL 中出现魔法值）
     * @return 影响行数，0 表示已被并发标记或记录不存在
     */
    @Update("UPDATE order_message_outbox SET status = #{sentStatus}, sent_at = NOW(), "
            + "next_retry_at = NULL, last_error = NULL "
            + "WHERE id = #{id} AND status <> #{sentStatus}")
    int markSent(@Param("id") Long id, @Param("sentStatus") String sentStatus);

    /**
     * 标记发送失败并登记下次重发时间（指数退避由 Service 计算后传入）。
     *
     * @param id          主键
     * @param status      失败状态值
     * @param retryCount  累计尝试次数
     * @param nextRetryAt 下次允许重发的时间
     * @param lastError   失败原因（截断后写入）
     * @return 影响行数
     */
    @Update("UPDATE order_message_outbox SET status = #{status}, retry_count = #{retryCount}, "
            + "next_retry_at = #{nextRetryAt}, last_error = #{lastError} "
            + "WHERE id = #{id} AND status <> #{sentStatus}")
    int markFailed(@Param("id") Long id,
                   @Param("status") String status,
                   @Param("retryCount") Integer retryCount,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt,
                   @Param("lastError") String lastError,
                   @Param("sentStatus") String sentStatus);
}
