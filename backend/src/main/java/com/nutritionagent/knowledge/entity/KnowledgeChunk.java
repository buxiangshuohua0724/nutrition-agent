package com.nutritionagent.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识分块（检索与引用的最小粒度）（{@code knowledge_chunk}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 列 {@code doc_id}，非空。 */
    @TableField("doc_id")
    private Long docId;

    /** 文档内顺序，保证拼接上下文时序正确（必填） */
    @TableField("chunk_index")
    private Integer chunkIndex;

    /** 所属小节标题，用于引用时展示 */
    private String heading;

    /** 分段正文（必填） */
    private String content;

    /** 估算 token 数，用于拼装预算控制 */
    @TableField("token_count")
    private Integer tokenCount;

    /** 抽取的关键词，去空格分隔 */
    private String keywords;

    /** 列 {@code created_at}，非空。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 列 {@code updated_at}，非空。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
