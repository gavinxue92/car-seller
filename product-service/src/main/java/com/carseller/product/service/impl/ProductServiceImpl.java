package com.carseller.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.carseller.common.exception.BusinessException;
import com.carseller.common.result.PageResult;
import com.carseller.common.result.ResultCode;
import com.carseller.product.dto.ProductQuery;
import com.carseller.product.dto.ProductRequest;
import com.carseller.product.entity.Product;
import com.carseller.product.mapper.ProductMapper;
import com.carseller.product.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 商品服务实现。
 *
 * @author car-seller
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    /** 默认页码 */
    private static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页最大条数 */
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public Product getProductDetail(Long id) {
        return getExistingProduct(id);
    }

    @Override
    public PageResult<Product> pageProducts(ProductQuery query) {
        ProductQuery condition = query == null ? new ProductQuery() : query;
        long pageNum = normalizePageNum(condition.getPageNum());
        long pageSize = normalizePageSize(condition.getPageSize());

        LambdaQueryWrapper<Product> wrapper = Wrappers.lambdaQuery();
        String keyword = trimToNull(condition.getKeyword());
        if (keyword != null) {
            // 名称或描述模糊匹配
            wrapper.and(w -> w.like(Product::getName, keyword)
                    .or()
                    .like(Product::getDescription, keyword));
        }
        String category = trimToNull(condition.getCategory());
        if (category != null) {
            wrapper.eq(Product::getCategory, category);
        }
        // 最新上架的商品排在前面
        wrapper.orderByDesc(Product::getId);

        IPage<Product> page = page(new Page<>(pageNum, pageSize), wrapper);
        log.debug("分页查询商品完成: pageNum={}, pageSize={}, total={}", pageNum, pageSize, page.getTotal());

        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Product createProduct(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);

        if (!save(product)) {
            throw new BusinessException("商品创建失败");
        }
        log.info("新增商品成功: id={}, name={}", product.getId(), product.getName());

        // 重新查询，带回数据库默认值生成的 created_at / updated_at
        return getById(product.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Product updateProduct(Long id, ProductRequest request) {
        getExistingProduct(id);

        // 只提交可编辑字段，created_at / updated_at 交给数据库维护
        Product product = new Product();
        product.setId(id);
        applyRequest(product, request);

        if (!updateById(product)) {
            throw new BusinessException("商品更新失败");
        }
        log.info("更新商品成功: id={}, name={}", id, product.getName());

        return getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeProduct(Long id) {
        Product product = getExistingProduct(id);

        if (!removeById(id)) {
            throw new BusinessException("商品删除失败");
        }
        log.info("删除商品成功: id={}, name={}", id, product.getName());
    }

    /**
     * 查询商品，不存在时抛出 404 业务异常。
     */
    private Product getExistingProduct(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品ID不合法");
        }
        Product product = getById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在，id=" + id);
        }
        return product;
    }

    /**
     * 将请求参数写入实体（不包含 id 与时间字段）。
     */
    private void applyRequest(Product product, ProductRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品信息不能为空");
        }
        product.setName(trimToNull(request.getName()));
        product.setDescription(trimToNull(request.getDescription()));
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setCategory(trimToNull(request.getCategory()));
        product.setImageUrl(trimToNull(request.getImageUrl()));
    }

    private static long normalizePageNum(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return DEFAULT_PAGE_NUM;
        }
        return pageNum;
    }

    private static long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 去除首尾空白，空白串统一转为 null。
     */
    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
