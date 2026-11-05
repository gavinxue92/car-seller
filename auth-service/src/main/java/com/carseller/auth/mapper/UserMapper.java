package com.carseller.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.carseller.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 *
 * <p>继承 {@link BaseMapper} 后即可获得单表增删改查能力，
 * 如需自定义 SQL 可以在 XML（resources/mapper）或注解方式补充。</p>
 *
 * @author car-seller
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
