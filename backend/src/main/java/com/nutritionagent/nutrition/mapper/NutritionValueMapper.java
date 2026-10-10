package com.nutritionagent.nutrition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nutritionagent.nutrition.entity.NutritionValue;
import org.apache.ibatis.annotations.Mapper;

/** 营养数值。三态由 value_status 表达，amount 的合法性由 CHECK 约束兜底。 */
@Mapper
public interface NutritionValueMapper extends BaseMapper<NutritionValue> {
}
