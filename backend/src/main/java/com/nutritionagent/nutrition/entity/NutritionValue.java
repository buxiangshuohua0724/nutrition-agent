package com.nutritionagent.nutrition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 营养数值 [D2]：三态 + 来源必填（{@code nutrition_value}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("nutrition_value")
public class NutritionValue {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 营养数据永远挂在形态上，不挂食品 [D1]（必填） */
    @TableField("variant_id")
    private Long variantId;

    /** 列 {@code nutrient_id}，非空。 */
    @TableField("nutrient_id")
    private Long nutrientId;

    /** 参考重量，如 100 表示每 100g（必填） */
    @TableField("reference_amount")
    private BigDecimal referenceAmount;

    /** g / ml / 份（必填） */
    @TableField("reference_unit")
    private String referenceUnit;

    /** 口径补充，如"每100g可食部" */
    @TableField("reference_note")
    private String referenceNote;

    /** PRESENT / ZERO / MISSING [D2]（必填） */
    @TableField("value_status")
    private String valueStatus;

    /** PRESENT/ZERO 时非空；MISSING 时为 NULL */
    private BigDecimal amount;

    /** 来源可追溯率达 100% 的前提 [D4]（必填） */
    @TableField("source_id")
    private Long sourceId;

    /** 来源中的具体表/页码定位 */
    @TableField("source_detail")
    private String sourceDetail;

    /** 列 {@code review_status}，非空。 */
    @TableField("review_status")
    private String reviewStatus;

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
