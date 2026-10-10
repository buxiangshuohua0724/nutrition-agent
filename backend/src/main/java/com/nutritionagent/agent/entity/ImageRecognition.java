package com.nutritionagent.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图片识别记录（只存候选，不存营养数值）（{@code image_recognition}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("image_recognition")
public class ImageRecognition {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 列 {@code user_id}，可空。 */
    @TableField("user_id")
    private Long userId;

    /** 识别后继续追问时关联会话 */
    @TableField("session_id")
    private Long sessionId;

    /** 存储路径（必填） */
    @TableField("storage_path")
    private String storagePath;

    /** 列 {@code original_name}，可空。 */
    @TableField("original_name")
    private String originalName;

    /** 列 {@code mime_type}，非空。 */
    @TableField("mime_type")
    private String mimeType;

    /** 字节（必填） */
    @TableField("file_size")
    private Long fileSize;

    /** SHA-256，用于去重与审计 */
    @TableField("file_hash")
    private String fileHash;

    /** 候选列表 [{foodId, foodName, confidence, reason}] */
    private String candidates;

    /** 用户确认的食品（PRD 5.6 流程第 7 步） */
    @TableField("confirmed_food_id")
    private Long confirmedFoodId;

    /** 列 {@code status}，非空。 */
    private String status;

    /** 失败原因，如 格式不支持/超过大小限制 */
    @TableField("failure_reason")
    private String failureReason;

    /** 列 {@code model_name}，可空。 */
    @TableField("model_name")
    private String modelName;

    /** 列 {@code latency_ms}，可空。 */
    @TableField("latency_ms")
    private Integer latencyMs;

    /** 列 {@code created_at}，非空。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 列 {@code updated_at}，非空。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
