package com.nutritionagent.common.security;

import com.nutritionagent.common.api.ErrorCode;
import com.nutritionagent.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 角色拦截器：读取 {@link RequireRole} 并判定当前调用者是否满足。
 *
 * <p>类级与方法级注解都支持，**方法级优先**（方法级可以比类级更严，例如后台
 * 发布接口要求 SYS_ADMIN，而同类的列表接口只要求 CONTENT_ADMIN）。
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRole annotation = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (annotation == null) {
            return true;
        }

        UserPrincipal principal = UserContext.current();
        if (!principal.isLoggedIn()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!principal.hasRoleAtLeast(annotation.value())) {
            // 不区分"角色不够"与"资源不属于你"，统一 40301，避免探测他人资源
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return true;
    }
}
