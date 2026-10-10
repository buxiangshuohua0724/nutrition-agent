package com.nutritionagent.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文档（{@code knowledge_doc}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("knowledge_doc")
public class KnowledgeDoc {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 列 {@code title}，非空。 */
    private String title;

    /** 列 {@code category}，非空。 */
    private String category;

    /** 列 {@code summary}，可空。 */
    private String summary;

    /** 原始来源 */
    @TableField("source_id")
    private Long sourceId;

    /** 原文出处定位 */
    @TableField("source_excerpt")
    private String sourceExcerpt;

    /** 适用人群/条件，如"成人一般人群" */
    @TableField("applicable_scope")
    private String applicableScope;

    /** 列 {@code embedding_model}，可空。 */
    @TableField("embedding_model")
    private String embeddingModel;

    /** 列 {@code review_status}，非空。 */
    @TableField("review_status")
    private String reviewStatus;

    /** 列 {@code publish_status}，非空。 */
    @TableField("publish_status")
    private String publishStatus;

    /** 列 {@code published_at}，可空。 */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /** 过期时间，超期内容不应再被引用（PRD 风险表） */
    @TableField("valid_until")
    private LocalDateTime validUntil;

    /** 列 {@code created_by}，可空。 */
    @TableField("created_by")
    private Long createdBy;

    /** 列 {@code updated_by}，可空。 */
    @TableField("updated_by")
    private Long updatedBy;

    /** 列 {@code reviewed_by}，可空。 */
    @TableField("reviewed_by")
    private Long reviewedBy;

    /** 列 {@code reviewed_at}，可空。 */
    @TableField("reviewed_at")
    private LocalDateTime reviewedAt;

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
