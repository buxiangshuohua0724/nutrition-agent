package com.nutritionagent.common.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * 角色码（{@code sys_role.code}）。
 *
 * <p>PRD 3.1 要求四类角色在**权限设计上必须可区分**，因此这里用显式枚举承载，
 * 而不是到处写字符串。权限判定统一走 {@link #atLeast(UserRole)}，避免散落的
 * {@code isAdmin()} 之类布尔判断（那种写法无法表达"系统管理员能做的事内容管理员
 * 不一定能做"）。
 */
public enum UserRole {

    /** 游客：未登录，仅有有限次数的 AI 问答与图片识别。 */
    GUEST(0),

    /** 注册用户：收藏、历史、个人资料。 */
    USER(1),

    /** 内容管理员：可维护食品/营养/知识库内容并审核，但不能发布或下架。 */
    CONTENT_ADMIN(2),

    /** 系统管理员：拥有发布/下架、用户管理、系统设置权限。 */
    SYS_ADMIN(3);

    private final int level;

    UserRole(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    /** 角色是否达到给定档位（含自身）。SYS_ADMIN.atLeast(CONTENT_ADMIN) 为 true。 */
    public boolean atLeast(UserRole required) {
        return this.level >= required.level;
    }

    public static Optional<UserRole> of(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(role -> role.name().equalsIgnoreCase(code.trim()))
                .findFirst();
    }
}
