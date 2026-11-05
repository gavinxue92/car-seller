package com.carseller.cart.service.impl;

import com.carseller.cart.client.ProductClient;
import com.carseller.cart.client.dto.ProductResponse;
import com.carseller.cart.constant.CartConstants;
import com.carseller.cart.dto.AddCartItemRequest;
import com.carseller.cart.dto.CartItemResponse;
import com.carseller.cart.dto.CartResponse;
import com.carseller.cart.dto.UpdateCartQuantityRequest;
import com.carseller.cart.dto.UpdateCartSelectedRequest;
import com.carseller.common.exception.BusinessException;
import com.carseller.common.result.R;
import com.carseller.common.result.ResultCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link CartServiceImpl} 单元测试：覆盖正常分支与各类业务异常分支。
 *
 * <p>不启动 Spring 容器，Redis 与 product-service 均以 Mock 替代，
 * 因此无需依赖运行中的 Redis / Nacos / product-service。</p>
 *
 * @author car-seller
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("购物车服务单元测试")
class CartServiceImplTest {

    private static final Long USER_ID = 9L;

    private static final Long PRODUCT_ID = 1001L;

    private static final String CART_KEY = "cart:9";

    private static final String SELECTED_KEY = "cart:selected:9";

    private static final String FIELD = "1001";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ProductClient productClient;

    private CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartServiceImpl(redisTemplate, productClient);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    @DisplayName("查询购物车：缺少 X-User-Id 时抛 401，且不访问 Redis")
    void getCartShouldThrowUnauthorizedWhenUserIdMissing() {
        assertThatThrownBy(() -> cartService.getCart(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.UNAUTHORIZED.getCode());
        verifyNoInteractions(redisTemplate, productClient);
    }

    @Test
    @DisplayName("查询购物车：Redis 无数据时返回空汇总而不是报错")
    void getCartShouldReturnEmptySummaryWhenCartIsEmpty() {
        when(hashOperations.entries(CART_KEY)).thenReturn(Collections.emptyMap());

        CartResponse response = cartService.getCart(USER_ID);

        assertThat(response.getUserId()).isEqualTo(USER_ID);
        assertThat(response.getItemCount()).isZero();
        assertThat(response.getTotalQuantity()).isZero();
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getItems()).isEmpty();
        verifyNoInteractions(productClient);
    }

    @Test
    @DisplayName("查询购物车：按 Redis 数量补全商品信息并汇总选中金额")
    void getCartShouldEnrichProductAndSummarizeAmount() {
        Map<Object, Object> entries = new HashMap<>();
        entries.put(FIELD, "2");
        when(hashOperations.entries(CART_KEY)).thenReturn(entries);
        when(setOperations.members(SELECTED_KEY)).thenReturn(Set.of(FIELD));
        when(productClient.getProduct(PRODUCT_ID)).thenReturn(R.ok(product()));

        CartResponse response = cartService.getCart(USER_ID);

        assertThat(response.getItemCount()).isEqualTo(1);
        assertThat(response.getTotalQuantity()).isEqualTo(2L);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("200.00");
        assertThat(response.getSelectedQuantity()).isEqualTo(2L);
        assertThat(response.getSelectedAmount()).isEqualByComparingTo("200.00");
        CartItemResponse item = response.getItems().get(0);
        assertThat(item.getAvailable()).isTrue();
        assertThat(item.getSelected()).isTrue();
        assertThat(item.getSubtotal()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("查询购物车：商品服务异常时降级为 available=false，不影响整体查询")
    void getCartShouldDegradeWhenProductServiceFails() {
        Map<Object, Object> entries = new HashMap<>();
        entries.put(FIELD, "2");
        when(hashOperations.entries(CART_KEY)).thenReturn(entries);
        when(setOperations.members(SELECTED_KEY)).thenReturn(Collections.emptySet());
        when(productClient.getProduct(PRODUCT_ID))
                .thenReturn(R.fail(ResultCode.FAILURE, "商品服务返回异常，请稍后重试"));

        CartResponse response = cartService.getCart(USER_ID);

        assertThat(response.getItems()).hasSize(1);
        CartItemResponse item = response.getItems().get(0);
        assertThat(item.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(item.getAvailable()).isFalse();
        assertThat(item.getPrice()).isNull();
        assertThat(item.getSubtotal()).isNull();
        assertThat(response.getTotalQuantity()).isEqualTo(2L);
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("加入购物车：商品不存在时抛 404，且不写入 Redis")
    void addItemShouldThrowNotFoundWhenProductMissing() {
        when(productClient.getProduct(PRODUCT_ID))
                .thenReturn(R.fail(ResultCode.NOT_FOUND, "商品不存在"));

        assertThatThrownBy(() -> cartService.addItem(USER_ID, addRequest(2)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.NOT_FOUND.getCode());
        verify(hashOperations, never()).put(anyString(), any(), any());
    }

    @Test
    @DisplayName("加入购物车：新条目写入数量并默认选中，同时刷新 TTL")
    void addItemShouldSaveQuantityAndSelectNewItem() {
        when(productClient.getProduct(PRODUCT_ID)).thenReturn(R.ok(product()));
        when(hashOperations.get(CART_KEY, FIELD)).thenReturn(null);
        when(hashOperations.size(CART_KEY)).thenReturn(0L);

        CartItemResponse response = cartService.addItem(USER_ID, addRequest(2));

        assertThat(response.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(response.getQuantity()).isEqualTo(2);
        assertThat(response.getSelected()).isTrue();
        assertThat(response.getSubtotal()).isEqualByComparingTo("200.00");
        verify(hashOperations).put(CART_KEY, FIELD, "2");
        verify(setOperations).add(SELECTED_KEY, FIELD);
        verify(redisTemplate).expire(CART_KEY, CartConstants.CART_TTL);
        verify(redisTemplate).expire(SELECTED_KEY, CartConstants.CART_TTL);
    }

    @Test
    @DisplayName("加入购物车：累加后超过单商品上限时抛 30002")
    void addItemShouldThrowWhenQuantityExceeded() {
        when(productClient.getProduct(PRODUCT_ID)).thenReturn(R.ok(product()));
        when(hashOperations.get(CART_KEY, FIELD)).thenReturn("98");

        assertThatThrownBy(() -> cartService.addItem(USER_ID, addRequest(2)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.CART_QUANTITY_EXCEEDED.getCode());
        verify(hashOperations, never()).put(anyString(), any(), any());
    }

    @Test
    @DisplayName("加入购物车：条目数量达到上限时抛 30003")
    void addItemShouldThrowWhenItemLimitExceeded() {
        when(productClient.getProduct(PRODUCT_ID)).thenReturn(R.ok(product()));
        when(hashOperations.get(CART_KEY, FIELD)).thenReturn(null);
        when(hashOperations.size(CART_KEY)).thenReturn((long) CartConstants.MAX_ITEM_COUNT);

        assertThatThrownBy(() -> cartService.addItem(USER_ID, addRequest(1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.CART_ITEM_LIMIT_EXCEEDED.getCode());
        verify(hashOperations, never()).put(anyString(), any(), any());
    }

    @Test
    @DisplayName("修改数量：商品不在购物车时抛 30001，且不调用商品服务")
    void updateQuantityShouldThrowWhenItemAbsent() {
        when(hashOperations.get(CART_KEY, FIELD)).thenReturn(null);

        assertThatThrownBy(() -> cartService.updateQuantity(USER_ID, PRODUCT_ID, quantityRequest(3)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.CART_ITEM_NOT_FOUND.getCode());
        verifyNoInteractions(productClient);
    }

    @Test
    @DisplayName("修改数量：覆盖为最终数量并返回最新价格小计")
    void updateQuantityShouldOverwriteQuantity() {
        when(hashOperations.get(CART_KEY, FIELD)).thenReturn("2");
        when(productClient.getProduct(PRODUCT_ID)).thenReturn(R.ok(product()));
        when(setOperations.isMember(SELECTED_KEY, FIELD)).thenReturn(Boolean.TRUE);

        CartItemResponse response = cartService.updateQuantity(USER_ID, PRODUCT_ID, quantityRequest(5));

        assertThat(response.getQuantity()).isEqualTo(5);
        assertThat(response.getSubtotal()).isEqualByComparingTo("500.00");
        assertThat(response.getSelected()).isTrue();
        verify(hashOperations).put(CART_KEY, FIELD, "5");
    }

    @Test
    @DisplayName("选中状态：取消选中时从选中集合移除")
    void updateSelectedShouldRemoveFromSelectedSet() {
        when(hashOperations.get(CART_KEY, FIELD)).thenReturn("2");
        when(productClient.getProduct(PRODUCT_ID)).thenReturn(R.ok(product()));

        CartItemResponse response = cartService.updateSelected(USER_ID, PRODUCT_ID, selectedRequest(false));

        assertThat(response.getSelected()).isFalse();
        assertThat(response.getQuantity()).isEqualTo(2);
        verify(setOperations).remove(SELECTED_KEY, FIELD);
    }

    @Test
    @DisplayName("删除单项：条目不存在时抛 30001")
    void removeItemShouldThrowWhenItemAbsent() {
        when(hashOperations.delete(CART_KEY, FIELD)).thenReturn(0L);

        assertThatThrownBy(() -> cartService.removeItem(USER_ID, PRODUCT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.CART_ITEM_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("删除单项：购物车清空后回收选中集合 Key")
    void removeItemShouldDropSelectedKeyWhenCartBecomesEmpty() {
        when(hashOperations.delete(CART_KEY, FIELD)).thenReturn(1L);
        when(hashOperations.size(CART_KEY)).thenReturn(0L);

        cartService.removeItem(USER_ID, PRODUCT_ID);

        verify(setOperations).remove(SELECTED_KEY, FIELD);
        verify(redisTemplate).delete(SELECTED_KEY);
    }

    @Test
    @DisplayName("清空购物车：同时删除购物车与选中集合两个 Key")
    void clearCartShouldDeleteBothKeys() {
        when(redisTemplate.delete(anyList())).thenReturn(2L);

        cartService.clearCart(USER_ID);

        verify(redisTemplate).delete(List.of(CART_KEY, SELECTED_KEY));
    }

    private ProductResponse product() {
        ProductResponse product = new ProductResponse();
        product.setId(PRODUCT_ID);
        product.setName("奔驰 C200L");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(5);
        product.setImageUrl("https://img.carseller.com/c200l.jpg");
        return product;
    }

    private AddCartItemRequest addRequest(int quantity) {
        AddCartItemRequest request = new AddCartItemRequest();
        request.setProductId(PRODUCT_ID);
        request.setQuantity(quantity);
        return request;
    }

    private UpdateCartQuantityRequest quantityRequest(int quantity) {
        UpdateCartQuantityRequest request = new UpdateCartQuantityRequest();
        request.setQuantity(quantity);
        return request;
    }

    private UpdateCartSelectedRequest selectedRequest(boolean selected) {
        UpdateCartSelectedRequest request = new UpdateCartSelectedRequest();
        request.setSelected(selected);
        return request;
    }
}
