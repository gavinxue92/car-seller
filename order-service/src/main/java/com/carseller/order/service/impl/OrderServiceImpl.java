package com.carseller.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import com.carseller.order.mapper.OrderItemMapper;
import com.carseller.order.mapper.OrderMapper;
import com.carseller.order.service.OrderOutboxService;
import com.carseller.order.service.OrderService;
import com.carseller.order.service.OrderTxService;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 订单服务实现。
 *
 * <p><b>一致性设计（README 4.3 / 7.1）</b>：
 * <ol>
 *   <li>商品价格等查询走同步 Feign（product-service），订单库不保存价格真源；</li>
 *   <li>写订单是本地事务（{@link OrderTxService}），事务内<b>严禁</b>任何远程调用；</li>
 *   <li>事务提交后发送 {@code order-created-topic}，库存扣减由 inventory-service 异步完成（最终一致）；</li>
 *   <li>状态变更一律幂等条件更新，影响 0 行即表示状态已被其他请求推进。</li>
 * </ol>
 * 因此写方法<b>不加</b> {@code @Transactional}，避免把 Feign 调用包进事务长时间占用连接。</p>
 *
 * @author car-seller
 */
@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    private final ProductClient productClient;

    private final OrderTxService orderTxService;

    private final OrderOutboxService orderOutboxService;

    public OrderServiceImpl(OrderMapper orderMapper,
                            OrderItemMapper orderItemMapper,
                            ProductClient productClient,
                            OrderTxService orderTxService,
                            OrderOutboxService orderOutboxService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productClient = productClient;
        this.orderTxService = orderTxService;
        this.orderOutboxService = orderOutboxService;
    }

    @Override
    public OrderCreateResponse createOrder(Long userId, CreateOrderRequest request) {
        Long uid = requireUserId(userId);
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "下单请求不能为空");
        }
        List<CreateOrderItemRequest> requestedItems = requireItems(request.getItems());
        if (!StringUtils.hasText(request.getShippingAddress())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "收货地址不能为空");
        }
        if (request.getPaymentMethod() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "支付方式不能为空");
        }

        BigDecimal totalPrice = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>(requestedItems.size());
        for (CreateOrderItemRequest requested : requestedItems) {
            ProductResponse product = loadProduct(requested.getProductId());
            BigDecimal price = product.getPrice().setScale(2, RoundingMode.HALF_UP);
            int quantity = requested.getQuantity();
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            totalPrice = totalPrice.add(subtotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.getId());
            orderItem.setProductName(product.getName());
            orderItem.setPrice(price);
            orderItem.setQuantity(quantity);
            orderItems.add(orderItem);
        }
        totalPrice = totalPrice.setScale(2, RoundingMode.HALF_UP);

        // 1. 本地事务落库（订单主表 + 明细）
        Order order = orderTxService.savePendingOrder(uid, request, totalPrice, orderItems);
        log.info("订单创建成功: orderId={}, userId={}, totalPrice={}, itemCount={}",
                order.getId(), uid, totalPrice, orderItems.size());

        // 2. 事务提交后立即投递：消息登记已随订单落库在同一事务内完成（本地消息表，README 7.1 ③），
        //    此处投递失败也不会丢消息，由 OrderOutboxService 的指数退避重试 + 定时补偿兜底（README 7.4）
        orderOutboxService.dispatch(order.getId(), TagConstants.ORDER_CREATED);

        return OrderCreateResponse.from(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(Long userId, Long orderId) {
        Long uid = requireUserId(userId);
        Order order = requireOwnedOrder(orderId, uid);
        List<OrderItem> items = orderItemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId));
        log.debug("查询订单详情完成: orderId={}, userId={}, itemCount={}", orderId, uid, items.size());
        return OrderDetailResponse.from(order, items);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OrderSummaryResponse> pageOrders(Long userId, OrderQuery query) {
        Long uid = requireUserId(userId);
        OrderQuery condition = query == null ? new OrderQuery() : query;
        long pageNum = normalizePageNum(condition.getPageNum());
        long pageSize = normalizePageSize(condition.getPageSize());
        OrderStatus status = parseStatus(condition.getStatus());

        LambdaQueryWrapper<Order> wrapper = Wrappers.lambdaQuery(Order.class)
                .eq(Order::getUserId, uid);
        if (status != null) {
            wrapper.eq(Order::getStatus, status.name());
        }
        wrapper.orderByDesc(Order::getId);

        Page<Order> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<Order> records = page.getRecords() == null ? List.of() : page.getRecords();
        List<OrderSummaryResponse> result = records.stream().map(OrderSummaryResponse::from).toList();
        log.debug("查询订单分页完成: userId={}, status={}, pageNum={}, pageSize={}, total={}",
                uid, status, pageNum, pageSize, page.getTotal());
        return PageResult.of(result, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public void cancelOrder(Long userId, Long orderId) {
        Long uid = requireUserId(userId);
        Order order = requireOwnedOrder(orderId, uid);
        OrderStatus status = order.getStatus();

        // 幂等分支 1：已取消，直接返回成功
        if (status == OrderStatus.CANCELLED) {
            log.info("订单已取消，按幂等处理直接返回: orderId={}, userId={}", orderId, uid);
            return;
        }
        if (status != OrderStatus.PENDING) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID,
                    "订单当前状态为「" + describe(status) + "」，不允许取消");
        }

        // 幂等条件更新：仅 PENDING → CANCELLED，影响 0 行说明状态已被其他请求推进
        boolean cancelled = orderTxService.cancelPendingOrder(orderId, uid);
        if (!cancelled) {
            Order latest = orderMapper.selectById(orderId);
            if (latest == null) {
                throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "订单不存在，id=" + orderId);
            }
            if (latest.getStatus() != OrderStatus.CANCELLED) {
                throw new BusinessException(ResultCode.ORDER_STATUS_INVALID,
                        "订单当前状态为「" + describe(latest.getStatus()) + "」，不允许取消");
            }
            // 幂等分支 2：并发下已被其他请求取消
            log.info("订单取消命中并发幂等分支: orderId={}, userId={}", orderId, uid);
            return;
        }

        log.info("订单取消成功: orderId={}, userId={}", orderId, uid);
        // 取消消息已在 cancelPendingOrder 的事务内登记（唯一键 order_id+tag 保证只登记一次），
        // 此处只做立即投递，失败由补偿扫描重发（README 7.4）
        orderOutboxService.dispatch(orderId, TagConstants.ORDER_CANCELLED);
    }

    /**
     * 组装订单创建事件（只携带扣减库存所需的最小字段：商品与数量）。
     */

    /**
     * 调用 product-service 获取商品最新信息，并把远程异常翻译为统一的业务码。
     *
     * <p>product-service 的业务失败同样是 HTTP 200 + {@code R.code}，因此需要双重判断：
     * HTTP 404 或 {@code code=404} 都翻译为 404 商品不存在。</p>
     */
    private ProductResponse loadProduct(Long productId) {
        R<ProductResponse> response;
        try {
            response = productClient.getProduct(productId);
        } catch (FeignException e) {
            if (e.status() == HttpStatus.NOT_FOUND.value()) {
                throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在，productId=" + productId);
            }
            log.error("调用商品服务失败, productId={}, status={}", productId, e.status(), e);
            throw new BusinessException(ResultCode.FAILURE, "商品服务暂不可用，请稍后重试");
        }
        if (response == null) {
            log.error("商品服务返回空响应, productId={}", productId);
            throw new BusinessException(ResultCode.FAILURE, "商品服务暂无响应，请稍后重试");
        }
        if (!response.isSuccess()) {
            if (ResultCode.NOT_FOUND.getCode().equals(response.getCode())) {
                throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在，productId=" + productId);
            }
            log.error("商品服务返回业务失败, productId={}, code={}, message={}",
                    productId, response.getCode(), response.getMessage());
            throw new BusinessException(ResultCode.FAILURE, "商品服务返回异常，请稍后重试");
        }
        ProductResponse product = response.getData();
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在，productId=" + productId);
        }
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            log.error("商品价格非法, productId={}, price={}", productId, product.getPrice());
            throw new BusinessException(ResultCode.FAILURE, "商品价格异常，请稍后重试");
        }
        return product;
    }

    /**
     * 下单商品条目校验：非空、数量上限、同一商品不允许重复提交。
     */
    private static List<CreateOrderItemRequest> requireItems(List<CreateOrderItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "订单商品不能为空");
        }
        if (items.size() > OrderConstants.MAX_ORDER_ITEMS) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "单笔订单最多 " + OrderConstants.MAX_ORDER_ITEMS + " 种商品");
        }
        Set<Long> productIds = new HashSet<>(items.size());
        for (CreateOrderItemRequest item : items) {
            if (item == null || item.getProductId() == null || item.getProductId() <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "商品ID不合法");
            }
            if (!productIds.add(item.getProductId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "同一商品不能重复提交，productId=" + item.getProductId());
            }
            Integer quantity = item.getQuantity();
            if (quantity == null || quantity < 1) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "商品数量必须大于 0");
            }
            if (quantity > OrderConstants.MAX_ITEM_QUANTITY) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "单个商品最多 " + OrderConstants.MAX_ITEM_QUANTITY + " 件");
            }
        }
        return items;
    }

    /**
     * 查询订单并校验归属：不存在 → 40001，非本人 → 403。
     */
    private Order requireOwnedOrder(Long orderId, Long userId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "订单ID不合法");
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND, "订单不存在，id=" + orderId);
        }
        if (!userId.equals(order.getUserId())) {
            log.warn("越权访问订单: orderId={}, currentUserId={}, ownerId={}", orderId, userId, order.getUserId());
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该订单");
        }
        return order;
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

    /**
     * 页码归一：为空或小于 1 时取默认值 1。
     */
    private static long normalizePageNum(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return OrderConstants.DEFAULT_PAGE_NUM;
        }
        return pageNum;
    }

    /**
     * 每页条数归一：为空或小于 1 取默认 10，超过上限按 100 处理（README 6.2）。
     */
    private static long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return OrderConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, OrderConstants.MAX_PAGE_SIZE);
    }

    /**
     * 解析状态过滤条件：空表示不过滤，非法值按 400 处理。
     */
    private static OrderStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String trimmed = status.trim().toUpperCase(Locale.ROOT);
        for (OrderStatus value : OrderStatus.values()) {
            if (value.name().equals(trimmed)) {
                return value;
            }
        }
        throw new BusinessException(ResultCode.BAD_REQUEST,
                "订单状态不合法，可选值：PENDING、PAID、SHIPPED、DELIVERED、CANCELLED");
    }

    /**
     * 状态的展示文案。
     */
    private static String describe(OrderStatus status) {
        return status == null ? "未知" : status.getDescription();
    }
}
