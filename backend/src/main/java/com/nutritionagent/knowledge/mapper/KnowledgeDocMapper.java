package com.nutritionagent.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nutritionagent.knowledge.entity.KnowledgeDoc;
import org.apache.ibatis.annotations.Mapper;

/** 知识文档。检索只返回 PUBLISHED + APPROVED 且未过期的内容。 */
@Mapper
public interface KnowledgeDocMapper extends BaseMapper<KnowledgeDoc> {
}
