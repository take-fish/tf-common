package com.fishmatrix.common.auth;

/**
 * 网关与服务之间的内部身份头。网关必须剥离客户端伪造的同名头，
 * 服务只接受带合法签名且未过期的身份头。
 */
public final class IdentityHeaders {

    public static final String USER_ID = "X-FM-User-Id";
    public static final String USERNAME = "X-FM-Username";
    public static final String NICKNAME = "X-FM-Nickname";
    public static final String ROLE = "X-FM-Role";
    public static final String ROLES = "X-FM-Roles";
    public static final String PERMISSIONS = "X-FM-Permissions";
    public static final String EXPIRES_AT = "X-FM-Exp";
    public static final String SIGNATURE = "X-FM-Signature";

    /** 身份头的最大有效期，网关每次代理时重新签发。 */
    public static final long MAX_TTL_SECONDS = 300;

    private IdentityHeaders() {
    }
}
