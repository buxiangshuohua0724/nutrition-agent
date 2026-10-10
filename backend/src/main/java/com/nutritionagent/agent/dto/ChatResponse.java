package com.nutritionagent.agent.dto;

/**
 * {@code POST /agent/chat} 的响应体（契约 4.3）。
 *
 * <p>为什么要在 {@link AgentAnswer} 外面再包一层：多轮会话需要把
 * {@code sessionId} 交回前端，而首次提问时后端会按问题自动建会话——前端
 * 拿不到这个 id 就只能每一轮都开一个新会话。会话元信息属于「这次请求的
 * 结果」，不属于「回答内容」，混进 {@code AgentAnswer} 会让这个结构在存进
 * {@code chat_message.structured_output} 时也带上一个易过期的会话字段。
 */
public record ChatResponse(
        Long sessionId,
        String sessionTitle,
        AgentAnswer answer
) {
}
