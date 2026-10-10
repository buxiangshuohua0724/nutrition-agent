package com.nutritionagent.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nutritionagent.user.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;

/** 用户资料。该表无逻辑删除列，随账户物理删除。 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
