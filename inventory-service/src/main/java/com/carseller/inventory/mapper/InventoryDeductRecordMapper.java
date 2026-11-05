package com.carseller.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.carseller.inventory.entity.InventoryDeductRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 库存扣减记录 Mapper（幂等兜底表）。
 *
 * @author car-seller
 */
public interface InventoryDeductRecordMapper extends BaseMapper<InventoryDeductRecord> {

    /**
     * 条件更新状态 DEDUCTED → RESTORED。
     *
     * @param id 记录主键
     * @return 受影响行数，0 表示已被并发回补（幂等 ACK）
     */
    @Update("UPDATE inventory_deduct_records SET status = 'RESTORED' "
            + "WHERE id = #{id} AND status = 'DEDUCTED'")
    int markRestored(@Param("id") Long id);
}
