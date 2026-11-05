package com.carseller.order.client;

import com.carseller.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 库存服务 Feign 客户端（同步查询，README 4.3：查询走同步/Feign）。
 *
 * <p>按服务名调用 {@code inventory-service}，语义与其 {@code GET /api/inventory/{productId}}
 * 完全一致；网关无 StripPrefix，因此路径自带 {@code /api} 前缀。</p>
 *
 * <p><b>只用于查询</b>：库存扣减 / 恢复一律走 MQ（README 7.1 / 7.4 禁止消费端反向同步调用），
 * 本客户端仅供下单前的可售库存校验（{@code inventories.quantity} 是库存唯一真源）与查询侧补全，
 * 绝不能作为扣减手段。</p>
 *
 * <p>注意：inventory-service 的业务错误同样是 HTTP 200 + {@code R.code} 返回，
 * 调用方必须判断 {@code R.code}，不能只看 HTTP 状态码。</p>
 *
 * @author car-seller
 */
@FeignClient(name = InventoryClient.SERVICE_NAME, path = InventoryClient.BASE_PATH)
public interface InventoryClient {

    /** 目标服务名（禁止改名） */
    String SERVICE_NAME = "inventory-service";

    /** 库存接口统一前缀 */
    String BASE_PATH = "/api/inventory";

    /**
     * 查询商品当前可售库存（库存真源 {@code inventories.quantity}，无库存记录时服务端返回 0）。
     *
     * @param productId 商品 ID
     * @return 统一响应，{@code data} 为可售数量
     */
    @GetMapping("/{productId}")
    R<Integer> getStock(@PathVariable("productId") Long productId);
}
