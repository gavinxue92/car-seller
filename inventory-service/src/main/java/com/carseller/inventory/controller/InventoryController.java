package com.carseller.inventory.controller;

import com.carseller.common.result.PageResult;
import com.carseller.common.result.R;
import com.carseller.inventory.dto.InventoryDeductRequest;
import com.carseller.inventory.dto.InventoryLogQuery;
import com.carseller.inventory.dto.InventoryRestoreRequest;
import com.carseller.inventory.entity.InventoryLog;
import com.carseller.inventory.service.InventoryService;
import com.carseller.inventory.vo.InventoryChangeResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库存接口（README 8：inventory-service 端点清单）。
 *
 * <ul>
 *     <li>{@code GET /api/inventory/{productId}}：查询可售库存（库存真源 {@code inventories.quantity}）；</li>
 *     <li>{@code POST /api/inventory/deduct}：同步扣减（供 Feign 兜底 / 管理端）；</li>
 *     <li>{@code POST /api/inventory/restore}：按订单恢复库存；</li>
 *     <li>{@code GET /api/inventory/logs}：库存变动流水分页。</li>
 * </ul>
 *
 * <p>网关未配置 StripPrefix，因此服务内自带宽 {@code /api/inventory} 前缀；
 * 返回一律 HTTP 200 + {@link R}，业务结果看 {@code code}。</p>
 *
 * @author car-seller
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * 查询单个商品当前可售库存（商品不存在返回 0）。
     *
     * @param productId 商品 ID
     * @return 当前可售库存
     */
    @GetMapping("/{productId}")
    public R<Integer> getStock(@PathVariable("productId") Long productId) {
        if (productId == null || productId <= 0L) {
            return R.fail("商品ID必须大于 0");
        }
        return R.ok(inventoryService.currentStock(List.of(productId)).getOrDefault(productId, 0));
    }

    /**
     * 扣减库存（同一订单幂等：Redis SETNX + 唯一键双保险）。
     *
     * @param request 订单号 + 扣减明细
     * @return 扣减前后库存明细
     */
    @PostMapping("/deduct")
    public R<InventoryChangeResult> deduct(@Valid @RequestBody InventoryDeductRequest request) {
        return R.ok("库存扣减成功", inventoryService.deduct(request));
    }

    /**
     * 恢复库存：按订单回补其全部已扣减记录（同一订单幂等，重复调用返回 {@code changed=false}）。
     *
     * @param request 订单号
     * @return 回补前后的库存明细
     */
    @PostMapping("/restore")
    public R<InventoryChangeResult> restore(@Valid @RequestBody InventoryRestoreRequest request) {
        return R.ok("库存恢复成功", inventoryService.restore(request.getOrderId()));
    }

    /**
     * 分页查询库存变动流水。
     *
     * @param query 商品 ID（可选）+ 页码 / 每页条数（默认 1 / 10，上限 100）
     * @return 分页结果
     */
    @GetMapping("/logs")
    public R<PageResult<InventoryLog>> pageLogs(InventoryLogQuery query) {
        return R.ok(inventoryService.pageLogs(query));
    }
}
