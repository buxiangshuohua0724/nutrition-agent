package com.nutritionagent.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录令牌（可撤销、可过期）（{@code sys_auth_token}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("sys_auth_token")
public class SysAuthToken {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 列 {@code user_id}，非空。 */
    @TableField("user_id")
    private Long userId;

    /** 令牌散列，禁止存储原始令牌（必填） */
    @TableField("token_hash")
    private String tokenHash;

    /** 列 {@code issued_at}，非空。 */
    @TableField("issued_at")
    private LocalDateTime issuedAt;

    /** 列 {@code expires_at}，非空。 */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /** 列 {@code revoked_at}，可空。 */
    @TableField("revoked_at")
    private LocalDateTime revokedAt;
}
