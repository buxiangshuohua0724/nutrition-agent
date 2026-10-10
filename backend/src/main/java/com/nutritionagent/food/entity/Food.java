package com.nutritionagent.food.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 食品主表（FR-003）— 只存“食品是什么”，不存任何营养数值（{@code food}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("food")
public class Food {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编码，种子数据与导入用（必填） */
    private String code;

    /** 列 {@code category_id}，可空。 */
    @TableField("category_id")
    private Long categoryId;

    /** 中文标准名称（必填） */
    private String name;

    /** 英文名称 */
    @TableField("en_name")
    private String enName;

    /** 学名，用于相似品种区分 */
    @TableField("scientific_name")
    private String scientificName;

    /** 食品介绍 */
    private String intro;

    /** 常见食用方式 */
    @TableField("common_usage")
    private String commonUsage;

    /** 产地/来源描述 */
    private String origin;

    /** 储存方式 */
    @TableField("storage_method")
    private String storageMethod;

    /** 适宜人群（有依据时填写） */
    @TableField("suitable_for")
    private String suitableFor;

    /** 注意事项 */
    private String cautions;

    /** 搜索加权词（去空格分隔） */
    @TableField("search_keywords")
    private String searchKeywords;

    /** DRAFT / PENDING / APPROVED / REJECTED（必填） */
    @TableField("review_status")
    private String reviewStatus;

    /** UNPUBLISHED / PUBLISHED / OFFLINE（必填） */
    @TableField("publish_status")
    private String publishStatus;

    /** 列 {@code published_at}，可空。 */
    @TableField("published_at")
    private LocalDateTime publishedAt;

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
