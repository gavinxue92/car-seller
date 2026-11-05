package com.carseller.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.carseller.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 库存表 Mapper。
 *
 * <p>扣减/回补均使用「带条件的原子 UPDATE」，避免先查后改导致超卖；
 * 受影响行数为 0 表示库存不足或商品不存在，由调用方转换为业务异常。</p>
 *
 * @author car-seller
 */
public interface InventoryMapper extends BaseMapper<Inventory> {

    /**
     * 原子扣减库存（数量不足时不更新）。
     *
     * @param productId 商品 ID
     * @param quantity  扣减数量
     * @return 受影响行数，0 表示库存不足
     */
    @Update("UPDATE inventories SET quantity = quantity - #{quantity} "
            + "WHERE product_id = #{productId} AND quantity >= #{quantity}")
    int deductQuantity(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 回补库存。
     *
     * @param productId 商品 ID
     * @param quantity  回补数量
     * @return 受影响行数，0 表示库存记录不存在
     */
    @Update("UPDATE inventories SET quantity = quantity + #{quantity} WHERE product_id = #{productId}")
    int restoreQuantity(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 加行锁读取当前库存（事务内调用，保证 before/after 快照准确）。
     *
     * @param productId 商品 ID
     * @return 当前库存，商品不存在时返回 {@code null}
     */
    @Select("SELECT quantity FROM inventories WHERE product_id = #{productId} FOR UPDATE")
    Integer selectQuantityForUpdate(@Param("productId") Long productId);
}
