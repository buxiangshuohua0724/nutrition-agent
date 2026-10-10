package com.nutritionagent.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息（结构化输出 + 工具轨迹）（{@code chat_message}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 列 {@code session_id}，非空。 */
    @TableField("session_id")
    private Long sessionId;

    /** 会话内序号，替代仅靠时间戳排序（同秒并发会乱序）（必填） */
    private Integer seq;

    /** USER / ASSISTANT / SYSTEM / TOOL（必填） */
    private String role;

    /** 自然语言内容（对应 Agent 输出的 answer 字段） */
    private String content;

    /** 列 {@code structured_output}，可空。 */
    @TableField("structured_output")
    private String structuredOutput;

    /** 识别出的意图，用于评估与统计（PRD 2.2 工具调用成功率） */
    private String intent;

    /** 工具调用轨迹，用于可解释性与回归测试 */
    @TableField("tool_calls")
    private String toolCalls;

    /** 实际使用的模型标识 */
    @TableField("model_name")
    private String modelName;

    /** 列 {@code latency_ms}，可空。 */
    @TableField("latency_ms")
    private Integer latencyMs;

    /** 列 {@code token_usage}，可空。 */
    @TableField("token_usage")
    private Integer tokenUsage;

    /** SUCCESS / FAILED / TIMEOUT / DEGRADED（必填） */
    private String status;

    /** 失败时的错误码（9 类异常之一） */
    @TableField("error_code")
    private String errorCode;

    /** 列 {@code created_at}，非空。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
