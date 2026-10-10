package com.nutritionagent.common.security;

import java.util.Set;

/**
 * 当前请求的调用者身份。
 *
 * <p>{@code tokenId} 为 {@code null} 表示游客（无令牌或令牌无效但接口允许匿名访问）。
 * 需要登录的接口必须显式调用 {@link #requireLoggedIn()}，不允许"取不到用户就当游客"
 * 这种默认放行。
 *
 * @param tokenId 令牌记录 id，游客为 null
 * @param userId  用户 id，游客为 null
 * @param username 登录名，游客为 null
 * @param nickname 昵称，可为 null
 * @param roles   角色集合，游客为空集
 */
public record UserPrincipal(
        Long tokenId,
        Long userId,
        String username,
        String nickname,
        Set<UserRole> roles
) {

    public static final UserPrincipal GUEST =
            new UserPrincipal(null, null, null, null, Set.of());

    public boolean isLoggedIn() {
        return userId != null;
    }

    public boolean isGuest() {
        return userId == null;
    }

    /** 是否具备给定档位及以上的角色。游客一律 false。 */
    public boolean hasRoleAtLeast(UserRole required) {
        return roles.stream().anyMatch(role -> role.atLeast(required));
    }

    public boolean hasExactRole(UserRole role) {
        return roles.contains(role);
    }
}
