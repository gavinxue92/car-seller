package com.carseller.inventory.service;

import com.carseller.common.result.PageResult;
import com.carseller.inventory.dto.InventoryDeductRequest;
import com.carseller.inventory.dto.InventoryLogQuery;
import com.carseller.inventory.entity.InventoryLog;
import com.carseller.inventory.vo.InventoryChangeResult;

import java.util.Collection;
import java.util.Map;

/**
 * 库存服务：扣减 / 恢复 / 日志查询。
 *
 * <p>幂等策略（README 7.4，两者都必须有）：</p>
 * <ul>
 *     <li>Redis SETNX 幂等键：{@code inventory:deduct:{orderId}}、{@code inventory:restore:{orderId}}</li>
 *     <li>数据库唯一键：{@code inventory_deduct_records.uk_order_product(order_id, product_id)}
 *         + 状态条件更新（DEDUCTED → RESTORED，0 行即 ACK）</li>
 * </ul>
 *
 * @author car-seller
 */
public interface InventoryService {

    /**
     * 扣减库存（同一订单幂等）。
     *
     * @param request 扣减请求
     * @return 扣减结果
     * @throws com.carseller.common.exception.BusinessException 库存不足（50001）或重复扣减（50002）
     */
    InventoryChangeResult deduct(InventoryDeductRequest request);

    /**
     * 恢复库存（按订单回补其全部已扣减记录，同一订单幂等）。
     *
     * @param orderId 订单号
     * @return 恢复结果，{@code changed=false} 表示幂等跳过
     */
    InventoryChangeResult restore(Long orderId);

    /**
     * 分页查询库存变动日志。
     *
     * @param query 查询条件（页码、每页条数、商品 ID）
     * @return 分页结果
     */
    PageResult<InventoryLog> pageLogs(InventoryLogQuery query);

    /**
     * 批量查询当前可用库存（用于失败事件回放 before 快照）。
     *
     * @param productIds 商品 ID 集合
     * @return 商品 ID → 当前库存
     */
    Map<Long, Integer> currentStock(Collection<Long> productIds);
}
