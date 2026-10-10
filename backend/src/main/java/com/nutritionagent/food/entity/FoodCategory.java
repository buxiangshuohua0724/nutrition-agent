package com.nutritionagent.food.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 食品分类（FR-001/FR-002）（{@code food_category}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("food_category")
public class FoodCategory {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 支持多级分类，顶级为 NULL */
    @TableField("parent_id")
    private Long parentId;

    /** URL 友好标识（必填） */
    private String slug;

    /** 分类名，如 蔬菜（必填） */
    private String name;

    /** 前端图标标识 */
    private String icon;

    /** 分类介绍 */
    private String intro;

    /** 列 {@code sort_order}，非空。 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 列 {@code enabled}，非空。 */
    private Boolean enabled;

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
