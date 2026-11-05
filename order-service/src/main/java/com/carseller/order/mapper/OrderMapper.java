package com.carseller.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.carseller.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单主表 Mapper。
 *
 * <p>状态变更必须使用带状态的幂等条件更新（0 行即表示状态已被其他线程改变），
 * 禁止 {@code updateById(order)} 这种覆盖式全量更新。</p>
 *
 * @author car-seller
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
