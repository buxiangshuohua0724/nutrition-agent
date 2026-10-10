package com.nutritionagent.food.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 食品图片（FR-003）（{@code food_image}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("food_image")
public class FoodImage {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 列 {@code food_id}，非空。 */
    @TableField("food_id")
    private Long foodId;

    /** 列 {@code variant_id}，可空。 */
    @TableField("variant_id")
    private Long variantId;

    /** 图片地址（本地磁盘或对象存储）（必填） */
    private String url;

    /** 缩略图地址 */
    @TableField("thumb_url")
    private String thumbUrl;

    /** 列 {@code image_type}，非空。 */
    @TableField("image_type")
    private String imageType;

    /** 图片描述 */
    private String caption;

    /** 图片来源说明（PRD 11.2 必须保存来源） */
    @TableField("source_note")
    private String sourceNote;

    /** 授权/使用条件说明（PRD 风险表：图片版权） */
    @TableField("license_note")
    private String licenseNote;

    /** 是否封面图（必填） */
    @TableField("is_cover")
    private Boolean isCover;

    /** 列 {@code sort_order}，非空。 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 图片同样需要审核（必填） */
    @TableField("review_status")
    private String reviewStatus;

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
