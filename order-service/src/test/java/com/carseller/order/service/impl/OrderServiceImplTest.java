package com.carseller.order.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.carseller.common.exception.BusinessException;
import com.carseller.common.mq.TagConstants;
import com.carseller.common.result.PageResult;
import com.carseller.common.result.R;
import com.carseller.common.result.ResultCode;
import com.carseller.order.client.ProductClient;
import com.carseller.order.client.dto.ProductResponse;
import com.carseller.order.constant.OrderConstants;
import com.carseller.order.dto.CreateOrderItemRequest;
import com.carseller.order.dto.CreateOrderRequest;
import com.carseller.order.dto.OrderCreateResponse;
import com.carseller.order.dto.OrderDetailResponse;
import com.carseller.order.dto.OrderQuery;
import com.carseller.order.dto.OrderSummaryResponse;
import com.carseller.order.entity.Order;
import com.carseller.order.entity.OrderItem;
import com.carseller.order.enums.OrderStatus;
import com.carseller.order.enums.PaymentMethod;
import com.carseller.order.mapper.OrderItemMapper;
import com.carseller.order.mapper.OrderMapper;
import com.carseller.order.service.OrderOutboxService;
import com.carseller.order.service.OrderTxService;
import feign.FeignException;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link OrderServiceImpl} 单元测试：覆盖下单、查询、分页与取消的主干与异常分支。
 *
 * <p>不启动 Spring 容器：MyBatis-Plus Mapper、Feign 客户端、事务组件与 MQ 发布器全部 Mock，
 * 因此无需依赖运行中的 MySQL / Nacos / product-service / RocketMQ。</p>
 *
 * @author car-seller
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单服务单元测试")
class OrderServiceImplTest {

    private static final Long USER_ID = 1L;

    private static final Long OTHER_USER_ID = 2L;

    private static final Long ORDER_ID = 88L;

    private static final Long PRODUCT_ID = 1001L;

    private static final Long SECOND_PRODUCT_ID = 1002L;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private ProductClient productClient;

    @Mock
    private OrderTxService orderTxService;

    @Mock
    private OrderOutboxService orderOutboxService;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderMapper, orderItemMapper, productClient,
                orderTxService, orderOutboxService);
    }

    @Test
    @DisplayName("创建订单：缺少 X-User-Id 时抛 401，且不调用商品服务与写库")
    void createOrderShouldThrowUnauthorizedWhenUserIdMissing() {
        CreateOrderRequest request = buildRequest(PRODUCT_ID, 2);

        assertBusinessCode(() -> orderService.createOrder(null, request), ResultCode.UNAUTHORIZED);

        verifyNoInteractions(productClient, orderTxService, orderOutboxService);
    }

    @Test
    @DisplayName("创建订单：商品不存在时抛 404，且不落库、不发消息")
    void createOrderShouldThrowNotFoundWhenProductMissing() {
        CreateOrderRequest request = buildRequest(PRODUCT_ID, 1);
        when(productClient.getProduct(PRODUCT_ID))
                .thenReturn(R.fail(ResultCode.NOT_FOUND, "商品不存在"));

        assertBusinessCode(() -> orderService.createOrder(USER_ID, request), ResultCode.NOT_FOUND);

        verifyNoInteractions(orderTxService, orderOutboxService);
    }

    @Test
    @DisplayName("创建订单：商品服务不可用时抛 500，且不落库、不发消息")
    void createOrderShouldThrowFailureWhenProductServiceUnavailable() {
        CreateOrderRequest request = buildRequest(PRODUCT_ID, 1);
        FeignException feignException = mock(FeignException.class);
        when(feignException.status()).thenReturn(503);
        when(productClient.getProduct(PRODUCT_ID)).thenThrow(feignException);

        assertBusinessCode(() -> orderService.createOrder(USER_ID, request), ResultCode.FAILURE);

        verifyNoInteractions(orderTxService, orderOutboxService);
    }

    @Test
    @DisplayName("创建订单：商品明细为空或同一商品重复提交时抛 400")
    void createOrderShouldThrowBadRequestWhenItemsInvalid() {
        CreateOrderRequest emptyItems = buildRequest(PRODUCT_ID, 1);
        emptyItems.setItems(List.of());

        assertBusinessCode(() -> orderService.createOrder(USER_ID, emptyItems), ResultCode.BAD_REQUEST);

        CreateOrderRequest duplicated = buildRequest(PRODUCT_ID, 1);
        duplicated.setItems(List.of(new CreateOrderItemRequest(PRODUCT_ID, 1),
                new CreateOrderItemRequest(PRODUCT_ID, 2)));

        assertBusinessCode(() -> orderService.createOrder(USER_ID, duplicated), ResultCode.BAD_REQUEST);

        verifyNoInteractions(productClient, orderTxService, orderOutboxService);
    }

    @Test
    @DisplayName("创建订单：按商品最新价计算总金额，落库后按序发布 order-created 事件")
    void createOrderShouldComputeTotalAndPublishEvent() {
        CreateOrderRequest request = buildRequest(PRODUCT_ID, 3);
        request.setItems(List.of(new CreateOrderItemRequest(PRODUCT_ID, 3),
                new CreateOrderItemRequest(SECOND_PRODUCT_ID, 2)));
        request.setPaymentMethod(PaymentMethod.WECHAT);

        when(productClient.getProduct(PRODUCT_ID))
                .thenReturn(R.ok(buildProduct(PRODUCT_ID, "宝马X5脚垫", "100.00")));
        when(productClient.getProduct(SECOND_PRODUCT_ID))
                .thenReturn(R.ok(buildProduct(SECOND_PRODUCT_ID, "奥迪A6L座垫", "50.50")));

        Order saved = buildOrder(ORDER_ID, USER_ID, OrderStatus.PENDING);
        saved.setTotalPrice(new BigDecimal("401.00"));
        when(orderTxService.savePendingOrder(eq(USER_ID), eq(request), any(BigDecimal.class), anyList()))
                .thenReturn(saved);

        OrderCreateResponse response = orderService.createOrder(USER_ID, request);

        ArgumentCaptor<BigDecimal> totalCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderTxService).savePendingOrder(eq(USER_ID), eq(request), totalCaptor.capture(), itemsCaptor.capture());

        assertThat(totalCaptor.getValue()).isEqualByComparingTo("401.00");
        List<OrderItem> savedItems = itemsCaptor.getValue();
        assertThat(savedItems).hasSize(2);
        assertThat(savedItems.get(0).getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(savedItems.get(0).getProductName()).isEqualTo("宝马X5脚垫");
        assertThat(savedItems.get(0).getPrice()).isEqualByComparingTo("100.00");
        assertThat(savedItems.get(0).getQuantity()).isEqualTo(3);
        assertThat(savedItems.get(1).getProductId()).isEqualTo(SECOND_PRODUCT_ID);
        assertThat(savedItems.get(1).getPrice()).isEqualByComparingTo("50.50");
        assertThat(savedItems.get(1).getQuantity()).isEqualTo(2);

        assertThat(response.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getTotalPrice()).isEqualByComparingTo("401.00");

        // 事件内容由 OrderTxServiceImpl 在事务内写入本地消息表（由 OrderOutboxServiceImplTest 覆盖），
        // 此处只验证铁律：先本地事务落库提交，再投递 MQ 消息
        InOrder sequence = inOrder(orderTxService, orderOutboxService);
        sequence.verify(orderTxService).savePendingOrder(any(), any(), any(), anyList());
        sequence.verify(orderOutboxService).dispatch(ORDER_ID, TagConstants.ORDER_CREATED);
    }

    @Test
    @DisplayName("订单详情：订单不存在时抛 40001，且不查询明细")
    void getOrderDetailShouldThrowNotFoundWhenOrderMissing() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(null);

        assertBusinessCode(() -> orderService.getOrderDetail(USER_ID, ORDER_ID), ResultCode.ORDER_NOT_FOUND);

        verifyNoInteractions(orderItemMapper);
    }

    @Test
    @DisplayName("订单详情：访问他人订单时抛 403，且不查询明细")
    void getOrderDetailShouldThrowForbiddenWhenOrderOwnedByOthers() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(buildOrder(ORDER_ID, OTHER_USER_ID, OrderStatus.PENDING));

        assertBusinessCode(() -> orderService.getOrderDetail(USER_ID, ORDER_ID), ResultCode.FORBIDDEN);

        verifyNoInteractions(orderItemMapper);
    }

    @Test
    @DisplayName("订单详情：返回订单信息与明细，小计由单价×数量统一计算")
    void getOrderDetailShouldReturnOrderWithItems() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(buildOrder(ORDER_ID, USER_ID, OrderStatus.PENDING));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(
                buildOrderItem(11L, PRODUCT_ID, "100.00", 2),
                buildOrderItem(12L, SECOND_PRODUCT_ID, "50.50", 3)));

        OrderDetailResponse detail = orderService.getOrderDetail(USER_ID, ORDER_ID);

        assertThat(detail.getId()).isEqualTo(ORDER_ID);
        assertThat(detail.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(detail.getStatusText()).isEqualTo(OrderStatus.PENDING.getDescription());
        assertThat(detail.getPaymentMethod()).isEqualTo(PaymentMethod.ALIPAY.name());
        assertThat(detail.getItems()).hasSize(2);
        assertThat(detail.getItems().get(0).getSubtotal()).isEqualByComparingTo("200.00");
        assertThat(detail.getItems().get(1).getSubtotal()).isEqualByComparingTo("151.50");
    }

    @Test
    @DisplayName("订单分页：pageSize 超上限按 100 处理，状态参数归一为大写过滤")
    void pageOrdersShouldCapPageSizeAndFilterByStatus() {
        OrderQuery query = new OrderQuery();
        query.setPageNum(2);
        query.setPageSize(1000);
        query.setStatus(" pending ");

        Order pending = buildOrder(ORDER_ID, USER_ID, OrderStatus.PENDING);
        when(orderMapper.selectPage(ArgumentMatchers.<Page<Order>>any(), any()))
                .thenAnswer(invocation -> {
                    Page<Order> param = invocation.getArgument(0);
                    param.setRecords(List.of(pending));
                    param.setTotal(201L);
                    return param;
                });

        PageResult<OrderSummaryResponse> result = orderService.pageOrders(USER_ID, query);

        assertThat(result.getPageNum()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(100);
        assertThat(result.getTotal()).isEqualTo(201L);
        assertThat(result.getPages()).isEqualTo(3);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getId()).isEqualTo(ORDER_ID);
        assertThat(result.getRecords().get(0).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("订单分页：非法状态过滤值抛 400，且不查询数据库")
    void pageOrdersShouldThrowBadRequestWhenStatusInvalid() {
        OrderQuery query = new OrderQuery();
        query.setStatus("UNKNOWN");

        assertBusinessCode(() -> orderService.pageOrders(USER_ID, query), ResultCode.BAD_REQUEST);

        verifyNoInteractions(orderMapper);
    }

    @Test
    @DisplayName("取消订单：非待支付状态抛 40002，且不执行条件更新、不发消息")
    void cancelOrderShouldThrowStatusInvalidWhenNotPending() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(buildOrder(ORDER_ID, USER_ID, OrderStatus.PAID));

        assertBusinessCode(() -> orderService.cancelOrder(USER_ID, ORDER_ID), ResultCode.ORDER_STATUS_INVALID);

        verify(orderTxService, never()).cancelPendingOrder(ORDER_ID, USER_ID);
        verifyNoInteractions(orderOutboxService);
    }

    @Test
    @DisplayName("取消订单：条件更新成功后发布 order-cancelled 事件")
    void cancelOrderShouldUpdateStatusAndPublishEvent() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(buildOrder(ORDER_ID, USER_ID, OrderStatus.PENDING));
        when(orderTxService.cancelPendingOrder(ORDER_ID, USER_ID)).thenReturn(true);

        orderService.cancelOrder(USER_ID, ORDER_ID);

        // 取消消息由 cancelPendingOrder 在事务内登记（唯一键 order_id+tag 保证一单一条），
        // 此处只验证「事务提交后立即投递」且只投递一次
        verify(orderOutboxService).dispatch(ORDER_ID, TagConstants.ORDER_CANCELLED);
    }

    @Test
    @DisplayName("取消订单：条件更新 0 行且已被并发取消时幂等返回成功，不重复发消息")
    void cancelOrderShouldBeIdempotentWhenCancelledByConcurrentRequest() {
        when(orderMapper.selectById(ORDER_ID))
                .thenReturn(buildOrder(ORDER_ID, USER_ID, OrderStatus.PENDING))
                .thenReturn(buildOrder(ORDER_ID, USER_ID, OrderStatus.CANCELLED));
        when(orderTxService.cancelPendingOrder(ORDER_ID, USER_ID)).thenReturn(false);

        orderService.cancelOrder(USER_ID, ORDER_ID);

        verify(orderTxService).cancelPendingOrder(ORDER_ID, USER_ID);
        verifyNoInteractions(orderOutboxService);
    }

    /**
     * 断言抛出指定业务码的 {@link BusinessException}。
     */
    private static void assertBusinessCode(ThrowableAssert.ThrowingCallable callable, ResultCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", expected.getCode());
    }

    /**
     * 构造仅含一个商品的下单请求（字段取值均为合法值，便于各用例按需覆盖）。
     */
    private static CreateOrderRequest buildRequest(Long productId, int quantity) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(new ArrayList<>(List.of(new CreateOrderItemRequest(productId, quantity))));
        request.setShippingAddress("北京市朝阳区建国路 88 号");
        request.setContactPhone("13800138000");
        request.setPaymentMethod(PaymentMethod.ALIPAY);
        return request;
    }

    private static ProductResponse buildProduct(Long productId, String name, String price) {
        ProductResponse product = new ProductResponse();
        product.setId(productId);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStock(100);
        return product;
    }

    private static Order buildOrder(Long orderId, Long ownerId, OrderStatus status) {
        Order order = new Order();
        order.setId(orderId);
        order.setUserId(ownerId);
        order.setStatus(status);
        order.setTotalPrice(new BigDecimal("199.00"));
        order.setPaymentMethod(PaymentMethod.ALIPAY.name());
        order.setShippingAddress("北京市朝阳区建国路 88 号");
        order.setContactPhone("13800138000");
        return order;
    }

    private static OrderItem buildOrderItem(Long id, Long productId, String price, int quantity) {
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrderId(ORDER_ID);
        item.setProductId(productId);
        item.setProductName("商品-" + productId);
        item.setPrice(new BigDecimal(price));
        item.setQuantity(quantity);
        return item;
    }
}
