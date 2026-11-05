package com.carseller.cart.client;

import com.carseller.cart.client.dto.ProductResponse;
import com.carseller.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 商品服务 Feign 客户端。
 *
 * <p>按服务名调用 {@code product-service}（Nacos 服务名=spring.application.name），
 * 接口语义与 product-service 的 {@code GET /api/products/{id}} 完全一致（README 8.6）。</p>
 *
 * <p>注意：product-service 的业务错误同样以 HTTP 200 + {@code R.code} 返回，
 * 调用方必须判断 {@code R.code}，不能只看 HTTP 状态码。</p>
 *
 * @author car-seller
 */
@FeignClient(name = ProductClient.SERVICE_NAME, path = ProductClient.BASE_PATH)
public interface ProductClient {

    /** 目标服务名（禁止改名） */
    String SERVICE_NAME = "product-service";

    /** 商品接口统一前缀（网关未配置 StripPrefix，服务内路径带 /api 前缀） */
    String BASE_PATH = "/api/products";

    /**
     * 查询商品详情：用于校验商品存在性并获取最新价格。
     *
     * @param id 商品 ID
     * @return 统一响应，商品不存在时 {@code code=404}
     */
    @GetMapping("/{id}")
    R<ProductResponse> getProduct(@PathVariable("id") Long id);
}
