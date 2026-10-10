package com.nutritionagent.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注接口所需的最低角色档位。
 *
 * <p>由 {@link RoleInterceptor} 统一拦截判定，未登录返回 40101、档位不足返回 40301
 * （PRD 12.4：普通用户访问后台接口必须 40301）。
 *
 * <p>设计取舍：用注解而不是在每个 Controller 里写 {@code if (!isAdmin())}，
 * 是为了让"哪些接口属于后台"这件事在代码里可被机械检索与审计。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequireRole {

    /** 所需最低角色。默认内容管理员（后台接口门槛）。 */
    UserRole value() default UserRole.CONTENT_ADMIN;
}
