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
 * 营养素字典 [D3]：单位与换算在此定义（{@code nutrient}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("nutrient")
public class Nutrient {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** ENERGY / PROTEIN / FAT / CARBOHYDRATE / ...（必填） */
    private String code;

    /** 中文名，如 蛋白质（必填） */
    private String name;

    /** 列 {@code en_name}，可空。 */
    @TableField("en_name")
    private String enName;

    /** 标准单位 g / mg / ug / kcal / kJ（必填） */
    private String unit;

    /** 单位换算基准，通常为 100（每 100g）（必填） */
    @TableField("base_amount")
    private BigDecimal baseAmount;

    /** 列 {@code group_code}，非空。 */
    @TableField("group_code")
    private String groupCode;

    /** 列 {@code display_order}，非空。 */
    @TableField("display_order")
    private Integer displayOrder;

    /** 汇总基准单位，用于跨单位统一（必填） */
    @TableField("base_unit")
    private String baseUnit;

    /** amount × unit_factor = base_unit 下的值（必填） */
    @TableField("unit_factor")
    private BigDecimal unitFactor;

    /** 列 {@code is_energy}，非空。 */
    @TableField("is_energy")
    private Boolean isEnergy;

    /** 营养素的通俗解释（PRD FR-010 营养素基本知识） */
    private String description;

    /** 列 {@code enabled}，非空。 */
    private Boolean enabled;

    /** 列 {@code created_at}，非空。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 列 {@code updated_at}，非空。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
