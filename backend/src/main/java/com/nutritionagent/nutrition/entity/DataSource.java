package com.nutritionagent.nutrition.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 数据来源 [D4]：营养数据可追溯率 100% 的基础（{@code data_source}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("data_source")
public class DataSource {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 列 {@code code}，非空。 */
    private String code;

    /** 如《中国食物成分表》第6版（必填） */
    private String name;

    /** 列 {@code source_type}，非空。 */
    @TableField("source_type")
    private String sourceType;

    /** 发布机构 */
    private String publisher;

    /** 版本/版次 */
    private String version;

    /** 发布日期（PRD 5.10 要求记录发布日期或版本） */
    @TableField("publish_date")
    private LocalDate publishDate;

    /** 可核查来源地址 */
    private String url;

    /** 适用范围（PRD 8.2：必须注明适用条件） */
    @TableField("applicable_scope")
    private String applicableScope;

    /** A 级来源须在此说明 data-source-policy.md 1.4 五条判据的满足情况 */
    private String note;

    /** 列 {@code created_at}，非空。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 列 {@code updated_at}，非空。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
