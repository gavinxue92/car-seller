package com.carseller.order.scheduler;

import com.carseller.order.constant.OrderConstants;
import com.carseller.order.service.OrderOutboxService;
import com.carseller.order.service.OrderStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单域定时任务（README Phase 3 第 5/6 条）。
 *
 * <p>两个任务职责分离、互不阻塞：</p>
 * <ol>
 *     <li><b>超时关单</b>：每 5 分钟扫描一次，关闭创建超过 30 分钟仍为 {@code PENDING} 的订单，
 *         并投递 {@code order-cancelled-topic} 让 inventory-service 回补库存；</li>
 *     <li><b>本地消息补偿</b>：每 5 分钟重发发送失败（{@code PENDING / FAILED}）且到达退避时间的消息，
 *         解决「订单已落库、消息没发出去」的最终一致问题（README 7.4）。</li>
 * </ol>
 *
 * <p>两个任务都是幂等的：重复执行只会命中更少的数据，不会重复关单、也不会重复回补库存
 * （状态条件更新 + {@code order_message_outbox(order_id, tag)} 唯一键双重保证）。</p>
 *
 * <p><b>注意</b>：单实例部署，未引入分布式调度；若后续多实例部署，需要接入 ShedLock / XXL-JOB，
 * 否则每个实例都会扫描（业务上仍幂等，只是浪费资源）。</p>
 *
 * @author car-seller
 */
@Component
public class OrderMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderMaintenanceScheduler.class);

    private final OrderStateService orderStateService;

    private final OrderOutboxService orderOutboxService;

    public OrderMaintenanceScheduler(OrderStateService orderStateService, OrderOutboxService orderOutboxService) {
        this.orderStateService = orderStateService;
        this.orderOutboxService = orderOutboxService;
    }

    /**
     * 超时未支付关单：默认每 5 分钟一次（{@link OrderConstants#TIMEOUT_SCAN_CRON}）。
     *
     * <p>异常必须在本方法内消化：定时任务抛出异常会被调度器吞掉且不再补跑，
     * 因此单次扫描失败只记录日志，等待下一轮。</p>
     */
    @Scheduled(cron = OrderConstants.TIMEOUT_SCAN_CRON)
    public void closeTimeoutOrders() {
        try {
            int closed = orderStateService.cancelTimeoutOrders();
            if (closed > 0) {
                log.info("超时关单任务完成: closed={}", closed);
            }
        } catch (Exception e) {
            log.error("超时关单任务执行失败，等待下一轮扫描", e);
        }
    }

    /**
     * 本地消息表补偿重发：默认每 5 分钟一次（{@link OrderConstants#OUTBOX_COMPENSATE_CRON}），
     * 与关单任务错开 30 秒启动，避免同一时刻争抢连接池。
     */
    @Scheduled(cron = OrderConstants.OUTBOX_COMPENSATE_CRON)
    public void compensateOutbox() {
        try {
            int resent = orderOutboxService.compensate();
            if (resent > 0) {
                log.info("本地消息补偿任务完成: resent={}", resent);
            }
        } catch (Exception e) {
            log.error("本地消息补偿任务执行失败，等待下一轮扫描", e);
        }
    }
}
