package com.nutritionagent.food.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nutritionagent.food.entity.FoodStateVariant;
import org.apache.ibatis.annotations.Mapper;

/** 食品形态，营养数据的最小承载单元。 */
@Mapper
public interface FoodStateVariantMapper extends BaseMapper<FoodStateVariant> {
}
