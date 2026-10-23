package com.fishmatrix.common.auth;

import java.util.List;
import java.util.Set;

/**
 * 服务端可信身份。来源只有两种：网关注入的 HMAC 签名身份头，
 * 或本服务携带 Bearer token 向用户中台 introspection 的结果。
 */
public record CurrentUser(
        long id,
        String username,
        String nickname,
        String role,
        List<String> roles,
        Set<String> permissions) {

    public CurrentUser {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /** 业务侧统一使用的用户标识，与聊天模块历史数据保持一致。 */
    public String userId() {
        return "u" + id;
    }

    public boolean isAdmin() {
        return roles.contains("ADMIN") || "ADMIN".equals(role);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean hasAnyPermission(String... required) {
        if (required == null || required.length == 0) return true;
        for (String permission : required) {
            if (permissions.contains(permission)) return true;
        }
        return false;
    }
}
