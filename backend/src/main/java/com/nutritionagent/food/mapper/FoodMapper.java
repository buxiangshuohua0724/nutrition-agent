package com.nutritionagent.food.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nutritionagent.food.entity.Food;
import org.apache.ibatis.annotations.Mapper;

/** 食品主表。公开查询必须带 publish_status/review_status/deleted_at 三重条件。 */
@Mapper
public interface FoodMapper extends BaseMapper<Food> {
}
