package com.nutritionagent.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答会话（含游客会话）（{@code chat_session}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("chat_session")
public class ChatSession {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** NULL = 未登录游客会话（PRD 角色A 有限次试用） */
    @TableField("user_id")
    private Long userId;

    /** 游客标识，用于限制试用次数 */
    @TableField("guest_key")
    private String guestKey;

    /** 会话标题，取首条提问摘要 */
    private String title;

    /** 历史对话列表按此排序（FR-008） */
    @TableField("last_message_at")
    private LocalDateTime lastMessageAt;

    /** 列 {@code message_count}，非空。 */
    @TableField("message_count")
    private Integer messageCount;

    /** 列 {@code created_at}，非空。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 列 {@code updated_at}，非空。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 列 {@code deleted_at}，可空。 */
    @TableLogic(value = "null", delval = "now()")
    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}
