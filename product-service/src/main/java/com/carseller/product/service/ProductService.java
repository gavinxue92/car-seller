package com.carseller.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.carseller.common.result.PageResult;
import com.carseller.product.dto.ProductQuery;
import com.carseller.product.dto.ProductRequest;
import com.carseller.product.entity.Product;

/**
 * 商品服务。
 *
 * <p>继承 {@link IService} 以复用 MyBatis-Plus 提供的通用单表能力，
 * 同时对外暴露商品业务方法。</p>
 *
 * @author car-seller
 */
public interface ProductService extends IService<Product> {

    /**
     * 根据 ID 查询商品详情。
     *
     * @param id 商品 ID
     * @return 商品信息，不存在时抛出业务异常（404）
     */
    Product getProductDetail(Long id);

    /**
     * 商品列表分页查询。
     *
     * @param query 分页与筛选条件，可为 {@code null}
     * @return 分页结果
     */
    PageResult<Product> pageProducts(ProductQuery query);

    /**
     * 新增商品。
     *
     * @param request 商品信息
     * @return 新增后的商品（含自增主键与数据库生成的时间字段）
     */
    Product createProduct(ProductRequest request);

    /**
     * 更新商品（全量覆盖可编辑字段）。
     *
     * @param id      商品 ID
     * @param request 商品信息
     * @return 更新后的商品
     */
    Product updateProduct(Long id, ProductRequest request);

    /**
     * 删除商品。
     *
     * @param id 商品 ID
     */
    void removeProduct(Long id);
}
