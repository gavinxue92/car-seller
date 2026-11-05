package com.carseller.cart.constant;

import java.time.Duration;

/**
 * 购物车常量：Redis Key 模式、TTL 与业务上限。
 *
 * <p>Redis 结构（README 11.2）：</p>
 * <ul>
 *     <li>{@code cart:{userId}} —— Hash，field=productId，value=数量；</li>
 *     <li>{@code cart:selected:{userId}} —— Set，member=已选中的 productId。</li>
 * </ul>
 *
 * <p>Key 统一在本类生成，禁止在业务代码中拼字符串（README 9.1 禁止魔法值）。</p>
 *
 * @author car-seller
 */
public final class CartConstants {

    /** 购物车 Hash Key 前缀 */
    public static final String CART_KEY_PREFIX = "cart:";

    /** 购物车选中商品集合 Key 前缀 */
    public static final String CART_SELECTED_KEY_PREFIX = "cart:selected:";

    /** 购物车缓存有效期：7 天，每次写入续期（README 11.2） */
    public static final Duration CART_TTL = Duration.ofDays(7);

    /** 单个商品数量上限（README Phase 2：单商品数量上限 99） */
    public static final int MAX_QUANTITY_PER_ITEM = 99;

    /** 购物车条目（商品种类）上限（README Phase 2：购物车条目上限 50） */
    public static final int MAX_ITEM_COUNT = 50;

    private CartConstants() {
    }

    /**
     * 购物车 Hash Key。
     *
     * @param userId 用户 ID
     * @return {@code cart:{userId}}
     */
    public static String cartKey(Long userId) {
        return CART_KEY_PREFIX + userId;
    }

    /**
     * 购物车选中集合 Key。
     *
     * @param userId 用户 ID
     * @return {@code cart:selected:{userId}}
     */
    public static String selectedKey(Long userId) {
        return CART_SELECTED_KEY_PREFIX + userId;
    }
}
