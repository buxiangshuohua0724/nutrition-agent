package com.nutritionagent.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nutritionagent.user.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;

/** 食品收藏。取消收藏是逻辑删除（deleted_at 置值）。 */
@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {
}
