package com.nutritionagent.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账户（FR-007）（{@code sys_user}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("sys_user")
public class SysUser {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名（必填） */
    private String username;

    /** 邮箱，可空 */
    private String email;

    /** BCrypt 散列，禁止明文（必填） */
    @TableField("password_hash")
    private String passwordHash;

    /** 昵称 */
    private String nickname;

    /** 头像地址 */
    @TableField("avatar_url")
    private String avatarUrl;

    /** ACTIVE / DISABLED / LOCKED（必填） */
    private String status;

    /** 列 {@code last_login_at}，可空。 */
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

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
