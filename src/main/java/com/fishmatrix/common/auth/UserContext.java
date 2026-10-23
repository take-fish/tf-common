package com.fishmatrix.common.auth;

import com.fishmatrix.common.error.ApiException;

/**
 * 当前请求的身份上下文。由 {@link AuthInterceptor} 写入并在请求结束时清理；
 * 业务代码禁止从查询参数、请求体或客户端自定义头读取用户标识。
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(CurrentUser user) {
        if (user == null) {
            HOLDER.remove();
        } else {
            HOLDER.set(user);
        }
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static CurrentUser require() {
        CurrentUser user = HOLDER.get();
        if (user == null) {
            throw ApiException.unauthorized("请先登录");
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
