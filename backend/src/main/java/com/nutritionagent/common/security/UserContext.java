package com.nutritionagent.common.security;

import com.nutritionagent.common.api.ErrorCode;
import com.nutritionagent.common.exception.BusinessException;

/**
 * 当前请求的调用者上下文（ThreadLocal）。
 *
 * <p>由 {@link AuthTokenFilter} 在请求进入时写入、在 finally 中清除——**必须清除**，
 * 否则线程复用会把上一个请求的身份带给下一个请求（这是比"取不到就当游客"更危险的
 * 失效方式）。
 */
public final class UserContext {

    private static final ThreadLocal<UserPrincipal> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(UserPrincipal principal) {
        HOLDER.set(principal);
    }

    /** 取当前调用者；没有显式设置时返回游客，绝不返回 null。 */
    public static UserPrincipal current() {
        UserPrincipal principal = HOLDER.get();
        return principal == null ? UserPrincipal.GUEST : principal;
    }

    /** 取当前调用者，未登录直接抛 40101。 */
    public static UserPrincipal requireLoggedIn() {
        UserPrincipal principal = current();
        if (!principal.isLoggedIn()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
