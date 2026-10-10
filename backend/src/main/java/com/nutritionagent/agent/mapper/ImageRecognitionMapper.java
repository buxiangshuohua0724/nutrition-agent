package com.nutritionagent.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nutritionagent.agent.entity.ImageRecognition;
import org.apache.ibatis.annotations.Mapper;

/** 图片识别记录。只存候选食品，不存任何营养数值。 */
@Mapper
public interface ImageRecognitionMapper extends BaseMapper<ImageRecognition> {
}
