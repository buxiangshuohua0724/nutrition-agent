package com.nutritionagent.food.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nutritionagent.food.entity.FoodCategory;
import org.apache.ibatis.annotations.Mapper;

/** 食品分类。 */
@Mapper
public interface FoodCategoryMapper extends BaseMapper<FoodCategory> {
}
