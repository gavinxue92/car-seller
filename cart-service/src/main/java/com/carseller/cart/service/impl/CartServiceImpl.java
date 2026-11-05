package com.carseller.cart.service.impl;

import com.carseller.cart.client.ProductClient;
import com.carseller.cart.client.dto.ProductResponse;
import com.carseller.cart.constant.CartConstants;
import com.carseller.cart.dto.AddCartItemRequest;
import com.carseller.cart.dto.CartItemResponse;
import com.carseller.cart.dto.CartResponse;
import com.carseller.cart.dto.UpdateCartQuantityRequest;
import com.carseller.cart.dto.UpdateCartSelectedRequest;
import com.carseller.cart.service.CartService;
import com.carseller.common.exception.BusinessException;
import com.carseller.common.result.R;
import com.carseller.common.result.ResultCode;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 购物车服务实现。
 *
 * <p>存储方案（README 4.2.3 / 11.2）：Redis Hash {@code cart:{userId}}
 * （field=productId，value=数量）+ Set {@code cart:selected:{userId}}（选中商品），
 * TTL 7 天且每次写入续期；<b>不访问数据库</b>。</p>
 *
 * <p>商品存在性与价格由 OpenFeign 调用 product-service 实时获取，购物车中不保存价格快照，
 * 避免商品调价后购物车展示旧价格。</p>
 *
 * @author car-seller
 */
@Service
public class CartServiceImpl implements CartService {

    private static final Logger log = LoggerFactory.getLogger(CartServiceImpl.class);

    private final StringRedisTemplate redisTemplate;

    private final ProductClient productClient;

    public CartServiceImpl(StringRedisTemplate redisTemplate, ProductClient productClient) {
        this.redisTemplate = redisTemplate;
        this.productClient = productClient;
    }

    @Override
    public CartResponse getCart(Long userId) {
        Long uid = requireUserId(userId);
        String cartKey = CartConstants.cartKey(uid);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cartKey);
        if (entries == null || entries.isEmpty()) {
            log.debug("购物车为空, userId={}", uid);
            return CartResponse.empty(uid);
        }

        Set<String> selectedIds = redisTemplate.opsForSet().members(CartConstants.selectedKey(uid));
        List<CartItemResponse> items = new ArrayList<>(entries.size());
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            Long productId = toProductId(entry.getKey());
            Integer quantity = toQuantity(entry.getValue());
            if (productId == null || quantity == null) {
                log.warn("购物车存在脏数据，已跳过, userId={}, field={}, value={}",
                        uid, entry.getKey(), entry.getValue());
                continue;
            }
            boolean selected = selectedIds != null && selectedIds.contains(String.valueOf(productId));
            items.add(buildItemForRead(productId, quantity, selected));
        }
        items.sort(Comparator.comparing(CartItemResponse::getProductId));

        CartResponse response = summarize(uid, items);
        log.debug("查询购物车完成, userId={}, itemCount={}, totalQuantity={}",
                uid, response.getItemCount(), response.getTotalQuantity());
        return response;
    }

    @Override
    public CartItemResponse addItem(Long userId, AddCartItemRequest request) {
        Long uid = requireUserId(userId);
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "加购参数不能为空");
        }
        Long productId = requireProductId(request.getProductId());
        int quantity = requireQuantity(request.getQuantity());
        // 远程校验：商品不存在返回 404，同时拿到最新价格
        ProductResponse product = fetchProduct(productId);

        String cartKey = CartConstants.cartKey(uid);
        String selectedKey = CartConstants.selectedKey(uid);
        String field = String.valueOf(productId);
        Integer existingQuantity = readQuantity(cartKey, field);
        long mergedQuantity = (existingQuantity == null ? 0L : existingQuantity) + quantity;
        checkQuantityLimit(mergedQuantity);

        boolean selected;
        if (existingQuantity == null) {
            Long itemCount = redisTemplate.opsForHash().size(cartKey);
            if (itemCount != null && itemCount >= CartConstants.MAX_ITEM_COUNT) {
                throw new BusinessException(ResultCode.CART_ITEM_LIMIT_EXCEEDED,
                        "购物车最多容纳 " + CartConstants.MAX_ITEM_COUNT + " 种商品");
            }
            // 新加购的条目默认选中
            selected = true;
            redisTemplate.opsForSet().add(selectedKey, field);
        } else {
            selected = isSelected(uid, productId);
        }

        redisTemplate.opsForHash().put(cartKey, field, String.valueOf(mergedQuantity));
        refreshExpire(cartKey, selectedKey);
        log.info("加入购物车成功, userId={}, productId={}, quantity={}", uid, productId, mergedQuantity);
        return CartItemResponse.of(product, (int) mergedQuantity, selected);
    }

    @Override
    public CartItemResponse updateQuantity(Long userId, Long productId, UpdateCartQuantityRequest request) {
        Long uid = requireUserId(userId);
        Long pid = requireProductId(productId);
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品数量不能为空");
        }
        int quantity = requireQuantity(request.getQuantity());
        checkQuantityLimit(quantity);

        String cartKey = CartConstants.cartKey(uid);
        String field = String.valueOf(pid);
        if (readQuantity(cartKey, field) == null) {
            throw new BusinessException(ResultCode.CART_ITEM_NOT_FOUND, "购物车中没有该商品，productId=" + pid);
        }
        // 取最新价格；商品已被删除时按 404 返回，避免购物车保留失效商品
        ProductResponse product = fetchProduct(pid);

        redisTemplate.opsForHash().put(cartKey, field, String.valueOf(quantity));
        refreshExpire(cartKey, CartConstants.selectedKey(uid));
        log.info("修改购物车数量成功, userId={}, productId={}, quantity={}", uid, pid, quantity);
        return CartItemResponse.of(product, quantity, isSelected(uid, pid));
    }

    @Override
    public CartItemResponse updateSelected(Long userId, Long productId, UpdateCartSelectedRequest request) {
        Long uid = requireUserId(userId);
        Long pid = requireProductId(productId);
        if (request == null || request.getSelected() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "选中状态不能为空");
        }

        String cartKey = CartConstants.cartKey(uid);
        String selectedKey = CartConstants.selectedKey(uid);
        String field = String.valueOf(pid);
        Integer quantity = readQuantity(cartKey, field);
        if (quantity == null) {
            throw new BusinessException(ResultCode.CART_ITEM_NOT_FOUND, "购物车中没有该商品，productId=" + pid);
        }

        boolean selected = request.getSelected();
        if (selected) {
            redisTemplate.opsForSet().add(selectedKey, field);
        } else {
            redisTemplate.opsForSet().remove(selectedKey, field);
        }
        refreshExpire(cartKey, selectedKey);
        log.info("修改购物车选中状态成功, userId={}, productId={}, selected={}", uid, pid, selected);
        return buildItemForRead(pid, quantity, selected);
    }

    @Override
    public void removeItem(Long userId, Long productId) {
        Long uid = requireUserId(userId);
        Long pid = requireProductId(productId);

        String cartKey = CartConstants.cartKey(uid);
        String selectedKey = CartConstants.selectedKey(uid);
        String field = String.valueOf(pid);
        Long removed = redisTemplate.opsForHash().delete(cartKey, field);
        redisTemplate.opsForSet().remove(selectedKey, field);
        if (removed == null || removed == 0) {
            throw new BusinessException(ResultCode.CART_ITEM_NOT_FOUND, "购物车中没有该商品，productId=" + pid);
        }

        Long remainCount = redisTemplate.opsForHash().size(cartKey);
        if (remainCount == null || remainCount == 0) {
            // 购物车已空，直接回收选中集合，避免残留空 Key
            redisTemplate.delete(selectedKey);
        } else {
            refreshExpire(cartKey, selectedKey);
        }
        log.info("删除购物车商品成功, userId={}, productId={}", uid, pid);
    }

    @Override
    public void clearCart(Long userId) {
        Long uid = requireUserId(userId);
        Long removedKeys = redisTemplate.delete(List.of(
                CartConstants.cartKey(uid), CartConstants.selectedKey(uid)));
        log.info("清空购物车成功, userId={}, removedKeys={}", uid, removedKeys);
    }

    /**
     * 查询商品最新信息：HTTP 层错误、商品不存在、业务错误统一转换为 {@code BusinessException}。
     */
    private ProductResponse fetchProduct(Long productId) {
        R<ProductResponse> response;
        try {
            response = productClient.getProduct(productId);
        } catch (FeignException e) {
            log.error("调用商品服务失败, productId={}, status={}", productId, e.status(), e);
            throw new BusinessException(ResultCode.FAILURE, "商品服务暂不可用，请稍后重试");
        }
        if (response == null) {
            throw new BusinessException(ResultCode.FAILURE, "商品服务返回数据为空，请稍后重试");
        }
        if (!response.isSuccess()) {
            // product-service 的商品不存在以 HTTP 200 + code=404 返回，这里透传为 404 业务异常
            if (ResultCode.NOT_FOUND.getCode().equals(response.getCode())) {
                throw new BusinessException(ResultCode.NOT_FOUND, response.getMessage());
            }
            log.warn("商品服务返回业务错误, productId={}, code={}, message={}",
                    productId, response.getCode(), response.getMessage());
            throw new BusinessException(ResultCode.FAILURE, "商品服务返回异常，请稍后重试");
        }
        ProductResponse product = response.getData();
        if (product == null || product.getId() == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在，id=" + productId);
        }
        return product;
    }

    /**
     * 读取购物车条目并补全商品信息；商品已删除或商品服务不可用时降级返回，不影响整个购物车查询。
     */
    private CartItemResponse buildItemForRead(Long productId, int quantity, boolean selected) {
        try {
            return CartItemResponse.of(fetchProduct(productId), quantity, selected);
        } catch (BusinessException e) {
            log.warn("购物车商品信息获取失败, productId={}, code={}, message={}",
                    productId, e.getCode(), e.getMessage());
            return CartItemResponse.unavailable(productId, quantity, selected);
        }
    }

    /**
     * 汇总购物车数量与金额。
     */
    private static CartResponse summarize(Long userId, List<CartItemResponse> items) {
        long totalQuantity = 0L;
        long selectedQuantity = 0L;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal selectedAmount = BigDecimal.ZERO;
        for (CartItemResponse item : items) {
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            BigDecimal subtotal = item.getSubtotal() == null ? BigDecimal.ZERO : item.getSubtotal();
            totalQuantity += quantity;
            totalAmount = totalAmount.add(subtotal);
            if (Boolean.TRUE.equals(item.getSelected())) {
                selectedQuantity += quantity;
                selectedAmount = selectedAmount.add(subtotal);
            }
        }
        return CartResponse.of(userId, items, totalQuantity, selectedQuantity, totalAmount, selectedAmount);
    }

    /**
     * 判断商品是否已选中。
     */
    private boolean isSelected(Long userId, Long productId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet()
                .isMember(CartConstants.selectedKey(userId), String.valueOf(productId)));
    }

    /**
     * 读取购物车中指定商品的数量，不存在或格式非法时返回 {@code null}。
     */
    private Integer readQuantity(String cartKey, String field) {
        return toQuantity(redisTemplate.opsForHash().get(cartKey, field));
    }

    /**
     * 购物车两个 Key 同时续期（每次写入续期）。
     */
    private void refreshExpire(String cartKey, String selectedKey) {
        redisTemplate.expire(cartKey, CartConstants.CART_TTL);
        redisTemplate.expire(selectedKey, CartConstants.CART_TTL);
    }

    /**
     * 用户身份缺失或非法：统一按 401 处理（身份只信任网关透传的 X-User-Id）。
     */
    private static Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户身份，请先登录");
        }
        return userId;
    }

    private static Long requireProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品ID不合法");
        }
        return productId;
    }

    private static int requireQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品数量必须大于 0");
        }
        return quantity;
    }

    /**
     * 单商品数量上限校验（30002）。
     */
    private static void checkQuantityLimit(long quantity) {
        if (quantity > CartConstants.MAX_QUANTITY_PER_ITEM) {
            throw new BusinessException(ResultCode.CART_QUANTITY_EXCEEDED,
                    "单个商品最多 " + CartConstants.MAX_QUANTITY_PER_ITEM + " 件");
        }
    }

    private static Long toProductId(Object field) {
        if (field == null) {
            return null;
        }
        try {
            long productId = Long.parseLong(field.toString().trim());
            return productId > 0 ? productId : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toQuantity(Object value) {
        if (value == null) {
            return null;
        }
        try {
            int quantity = Integer.parseInt(value.toString().trim());
            return quantity < 1 ? null : quantity;
        } catch (NumberFormatException e) {
            log.warn("购物车数量格式非法, value={}", value);
            return null;
        }
    }
}
