package com.carseller.common.result;

/**
 * 统一业务状态码。
 *
 * <p>基础段 200/400/401/403/404/500 为全系统通用码；
 * 扩展段按域划分：1xxxx 通用、2xxxx 商品、3xxxx 购物车、4xxxx 订单、5xxxx 库存、6xxxx 支付，
 * 统一在本枚举中登记，禁止在业务服务内自建状态码枚举。</p>
 *
 * <p>所在模块：{@code car-seller-common}，各业务微服务禁止复制本类。</p>
 *
 * @author car-seller
 */
public enum ResultCode {

    /** 成功 */
    SUCCESS(200, "操作成功"),

    /** 请求参数错误 */
    BAD_REQUEST(400, "请求参数错误"),

    /** 未认证或登录已过期 */
    UNAUTHORIZED(401, "未认证或登录已过期"),

    /** 无访问权限 */
    FORBIDDEN(403, "无访问权限"),

    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在"),

    /** 操作失败 / 系统内部错误 */
    FAILURE(500, "操作失败"),

    /** 购物车条目不存在 */
    CART_ITEM_NOT_FOUND(30001, "购物车条目不存在"),

    /** 超出单商品数量上限 */
    CART_QUANTITY_EXCEEDED(30002, "超出单商品数量上限"),

    /** 购物车条目数量已达上限 */
    CART_ITEM_LIMIT_EXCEEDED(30003, "购物车条目数量已达上限"),

    /** 订单不存在 */
    ORDER_NOT_FOUND(40001, "订单不存在"),

    /** 订单状态不允许该操作 */
    ORDER_STATUS_INVALID(40002, "订单状态不允许该操作"),

    /** 商品库存不足 */
    INVENTORY_NOT_ENOUGH(50001, "商品库存不足"),

    /** 重复扣减库存 */
    INVENTORY_DUPLICATE_DEDUCT(50002, "重复扣减库存");

    private final Integer code;

    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
