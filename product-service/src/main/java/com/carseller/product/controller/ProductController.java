package com.carseller.product.controller;

import com.carseller.common.result.PageResult;
import com.carseller.common.result.R;
import com.carseller.product.dto.ProductQuery;
import com.carseller.product.dto.ProductRequest;
import com.carseller.product.entity.Product;
import com.carseller.product.service.ProductService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品对外 RESTful 接口。
 *
 * <p>统一前缀 {@code /api/products}，与 api-gateway 中配置的路由
 * {@code Path=/api/products/**} 保持一致（网关未配置 StripPrefix，直连与经网关访问路径相同）。</p>
 *
 * <ul>
 *     <li>{@code GET    /api/products/{id}} 查询商品详情</li>
 *     <li>{@code GET    /api/products}      商品列表分页查询</li>
 *     <li>{@code POST   /api/products}      新增商品</li>
 *     <li>{@code PUT    /api/products/{id}} 更新商品</li>
 *     <li>{@code DELETE /api/products/{id}} 删除商品</li>
 * </ul>
 *
 * @author car-seller
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 根据 ID 查询商品详情。
     */
    @GetMapping("/{id}")
    public R<Product> getProductDetail(@PathVariable("id") Long id) {
        log.info("查询商品详情, id={}", id);
        return R.ok(productService.getProductDetail(id));
    }

    /**
     * 商品列表分页查询。
     *
     * <p>示例：{@code GET /api/products?pageNum=1&pageSize=10&keyword=iPhone&category=手机}</p>
     */
    @GetMapping
    public R<PageResult<Product>> pageProducts(ProductQuery query) {
        log.info("分页查询商品, pageNum={}, pageSize={}, keyword={}, category={}",
                query.getPageNum(), query.getPageSize(), query.getKeyword(), query.getCategory());
        return R.ok(productService.pageProducts(query));
    }

    /**
     * 新增商品。
     */
    @PostMapping
    public R<Product> createProduct(@Valid @RequestBody ProductRequest request) {
        log.info("新增商品, name={}", request.getName());
        return R.ok("商品创建成功", productService.createProduct(request));
    }

    /**
     * 更新商品（仅更新请求中携带的字段）。
     */
    @PutMapping("/{id}")
    public R<Product> updateProduct(@PathVariable("id") Long id, @Valid @RequestBody ProductRequest request) {
        log.info("更新商品, id={}, name={}", id, request.getName());
        return R.ok("商品更新成功", productService.updateProduct(id, request));
    }

    /**
     * 删除商品。
     */
    @DeleteMapping("/{id}")
    public R<Void> deleteProduct(@PathVariable("id") Long id) {
        log.info("删除商品, id={}", id);
        productService.removeProduct(id);
        return R.ok("商品删除成功", null);
    }
}
