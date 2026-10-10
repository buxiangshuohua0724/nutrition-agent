package com.nutritionagent.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志（禁止写入敏感信息）（{@code operation_log}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("operation_log")
public class OperationLog {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 链路追踪 ID */
    @TableField("trace_id")
    private String traceId;

    /** 列 {@code user_id}，可空。 */
    @TableField("user_id")
    private Long userId;

    /** FOOD / NUTRITION / AGENT / RECOGNITION / AUTH / ADMIN（必填） */
    private String module;

    /** 列 {@code action}，非空。 */
    private String action;

    /** INFO / WARN / ERROR（必填） */
    private String level;

    /** 列 {@code success}，非空。 */
    private Boolean success;

    /** 列 {@code error_code}，可空。 */
    @TableField("error_code")
    private String errorCode;

    /** 列 {@code message}，可空。 */
    private String message;

    /** 列 {@code duration_ms}，可空。 */
    @TableField("duration_ms")
    private Integer durationMs;

    /** 列 {@code created_at}，非空。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
