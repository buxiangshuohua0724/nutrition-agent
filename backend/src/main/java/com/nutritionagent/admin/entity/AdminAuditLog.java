package com.nutritionagent.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台操作审计（PRD 9.3）（{@code admin_audit_log}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("admin_audit_log")
public class AdminAuditLog {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 列 {@code operator_id}，可空。 */
    @TableField("operator_id")
    private Long operatorId;

    /** 冗余用户名，用户删除后审计仍可读 */
    @TableField("operator_name")
    private String operatorName;

    /** CREATE / UPDATE / DELETE / PUBLISH / OFFLINE / REVIEW / REINDEX / LOGIN（必填） */
    private String action;

    /** FOOD / VARIANT / NUTRITION / KNOWLEDGE / IMAGE / USER / CONFIG（必填） */
    @TableField("target_type")
    private String targetType;

    /** 列 {@code target_id}，可空。 */
    @TableField("target_id")
    private Long targetId;

    /** 变更前快照（PRD 2.2 / 9.3 要求可追溯修改前后） */
    @TableField("before_json")
    private String beforeJson;

    /** 变更后快照 */
    @TableField("after_json")
    private String afterJson;

    /** 列 {@code ip}，可空。 */
    private String ip;

    /** 列 {@code created_at}，非空。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
