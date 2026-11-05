package com.carseller.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.carseller.auth.entity.UserLoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录审计日志 Mapper。
 *
 * @author car-seller
 */
@Mapper
public interface UserLoginLogMapper extends BaseMapper<UserLoginLog> {
}
