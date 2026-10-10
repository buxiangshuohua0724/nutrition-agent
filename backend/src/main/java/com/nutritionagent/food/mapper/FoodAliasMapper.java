package com.nutritionagent.food.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nutritionagent.food.entity.FoodAlias;
import org.apache.ibatis.annotations.Mapper;

/** 食品别名。别名不得劫持标准名，排序权重在服务层处理。 */
@Mapper
public interface FoodAliasMapper extends BaseMapper<FoodAlias> {
}
