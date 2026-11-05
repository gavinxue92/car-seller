package com.carseller.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.carseller.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单明细 Mapper。
 *
 * @author car-seller
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
