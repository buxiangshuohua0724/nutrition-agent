package com.nutritionagent.user.dto;

import java.util.List;
import java.util.Map;

/**
 * 当前用户信息的对外视图。
 *
 * <p>刻意<b>不包含</b> {@code passwordHash}、{@code status}、{@code deletedAt} 等内部字段：
 * 对外 DTO 与数据库实体分离，避免将来给实体加一个内部字段就意外把它泄露到接口上。
 */
public record UserView(
        Long id,
        String username,
        String nickname,
        String email,
        String avatarUrl,
        List<String> roles,
        List<String> roleLabels,
        Map<String, Object> preferences,
        String createdAt
) {
}
