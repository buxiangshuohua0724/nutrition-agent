package com.nutritionagent.nutrition.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nutritionagent.nutrition.entity.DataSource;
import org.apache.ibatis.annotations.Mapper;

/** 数据来源。该表无逻辑删除列：来源不允许删除，只能停用。 */
@Mapper
public interface DataSourceMapper extends BaseMapper<DataSource> {
}
