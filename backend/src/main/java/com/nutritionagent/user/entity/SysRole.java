package com.nutritionagent.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色字典（PRD 3.1，四类角色权限必须可区分）（{@code sys_role}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("sys_role")
public class SysRole {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** GUEST / USER / CONTENT_ADMIN / SYS_ADMIN（必填） */
    private String code;

    /** 角色显示名（必填） */
    private String name;

    /** 列 {@code description}，可空。 */
    private String description;

    /** 列 {@code created_at}，非空。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
