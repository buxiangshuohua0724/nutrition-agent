package com.nutritionagent.food.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 食品形态 [D1]：营养数据的最小承载单元（{@code food_state_variant}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("food_state_variant")
public class FoodStateVariant {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 列 {@code food_id}，非空。 */
    @TableField("food_id")
    private Long foodId;

    /** RAW / COOKED / PEELED / PROCESSED / DRIED / FROZEN（必填） */
    private String state;

    /** 煮/蒸/炒/炸/烤，state=COOKED 时必填 */
    @TableField("cooking_method")
    private String cookingMethod;

    /** 展示用后缀，如"（可食部）""（炒）" */
    @TableField("name_suffix")
    private String nameSuffix;

    /** 可食部比例，如 0.94 表示 94% 可食（缺失时 NULL，不填 1.0） */
    @TableField("edible_ratio")
    private BigDecimal edibleRatio;

    /** 形态说明 */
    private String description;

    /** 详情页默认选中形态（必填） */
    @TableField("is_default")
    private Boolean isDefault;

    /** 列 {@code review_status}，非空。 */
    @TableField("review_status")
    private String reviewStatus;

    /** 列 {@code publish_status}，非空。 */
    @TableField("publish_status")
    private String publishStatus;

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
