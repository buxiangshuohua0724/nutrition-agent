package com.nutritionagent.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nutritionagent.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/** 用户账户。逻辑删除由 deletedAt 上的 @TableLogic 自动生效。 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
