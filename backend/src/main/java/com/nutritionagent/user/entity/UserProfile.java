package com.nutritionagent.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户资料与饮食偏好（FR-007，全部业务字段可空）（{@code user_profile}）。
 *
 * <p>本文件由 scripts/gen_entities.py 依据 docs/db/schema.sql 生成，
 * 列名与类型以 schema 为唯一权威来源；schema 变更后请重新生成，不要手改。
 */
@Data
@TableName("user_profile")
public class UserProfile {

    /** 列 {@code id}，非空。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 列 {@code user_id}，非空。 */
    @TableField("user_id")
    private Long userId;

    /** 年龄区间，不存精确生日（最小化收集） */
    @TableField("age_range")
    private String ageRange;

    /** MALE / FEMALE / UNDISCLOSED */
    private String gender;

    /** 用户主动提供 */
    @TableField("height_cm")
    private BigDecimal heightCm;

    /** 用户主动提供 */
    @TableField("weight_kg")
    private BigDecimal weightKg;

    /** SEDENTARY / LIGHT / MODERATE / ACTIVE / VERY_ACTIVE */
    @TableField("activity_level")
    private String activityLevel;

    /** WEIGHT_LOSS / MAINTAIN / MUSCLE_GAIN / ... */
    @TableField("diet_goal")
    private String dietGoal;

    /** 过敏原列表，用户主动提供 */
    private String allergens;

    /** 饮食偏好（素食等） */
    @TableField("diet_preferences")
    private String dietPreferences;

    /** 用户明确授权时间（PRD 5.7 要求主动提供或明确授权） */
    @TableField("consent_at")
    private LocalDateTime consentAt;

    /** 列 {@code created_at}，非空。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 列 {@code updated_at}，非空。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
