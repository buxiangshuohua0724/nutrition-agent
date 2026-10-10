package com.nutritionagent.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nutritionagent.user.entity.SysAuthToken;
import org.apache.ibatis.annotations.Mapper;

/** 登录令牌。该表无逻辑删除列，吊销靠 revokedAt。 */
@Mapper
public interface SysAuthTokenMapper extends BaseMapper<SysAuthToken> {
}
