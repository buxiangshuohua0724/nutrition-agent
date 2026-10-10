package com.nutritionagent.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 食品收藏（FR-008）（{@code user_favorite}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("user_favorite")
public class UserFavorite {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 列 {@code user_id}，非空。 */
    @TableField("user_id")
    private Long userId;

    /** 列 {@code food_id}，非空。 */
    @TableField("food_id")
    private Long foodId;

    /** 列 {@code created_at}，非空。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 列 {@code deleted_at}，可空。 */
    @TableLogic(value = "null", delval = "now()")
    @TableField("deleted_at")
    private LocalDateTime deletedAt;
}
