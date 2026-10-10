package com.nutritionagent.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nutritionagent.agent.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/** 问答会话，含游客会话。 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
