package com.nutritionagent.nutrition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nutritionagent.nutrition.entity.Nutrient;
import org.apache.ibatis.annotations.Mapper;

/** 营养素字典，单位与换算在此定义，营养数据不重复存单位。 */
@Mapper
public interface NutrientMapper extends BaseMapper<Nutrient> {
}
