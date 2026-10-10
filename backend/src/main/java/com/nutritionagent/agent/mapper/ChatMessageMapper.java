package com.nutritionagent.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nutritionagent.agent.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/** 会话消息。结构化输出与工具轨迹以 JSON 文本存储。 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
