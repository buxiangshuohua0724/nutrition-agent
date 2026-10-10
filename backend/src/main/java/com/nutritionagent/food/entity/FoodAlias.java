package com.nutritionagent.food.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 食品别名（FR-002 搜索范围）（{@code food_alias}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("food_alias")
public class FoodAlias {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 列 {@code food_id}，非空。 */
    @TableField("food_id")
    private Long foodId;

    /** 别名/俗称/英文别名/拼音（必填） */
    private String alias;

    /** 列 {@code alias_type}，非空。 */
    @TableField("alias_type")
    private String aliasType;

    /** 别名匹配权重，越大越优先（必填） */
    private Integer weight;

    /** 列 {@code created_at}，非空。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 列 {@code deleted_at}，可空。 */
    @TableLogic(value = "null", delval = "now()")
    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}
