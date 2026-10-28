package com.fishmatrix.common.auth;

import com.fishmatrix.common.autoconfigure.FishAuthProperties;
import com.fishmatrix.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * REST 请求统一入口：
 * <ol>
 *   <li>优先校验网关注入的签名身份头；</li>
 *   <li>没有身份头时，可选用 Bearer token 向用户中台 introspection；</li>
 *   <li>校验 {@link RequirePermission}；</li>
 *   <li>写入 {@link UserContext}，请求结束后清理。</li>
 * </ol>
 */
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final IdentitySigner signer;
    private final AuthClient authClient;
    private final FishAuthProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) {
        String path = request.getRequestURI();
        if (!(handler instanceof HandlerMethod handlerMethod) || !path.startsWith("/api/")
                || isPublic(request, path)) {
            return true;
        }

        CurrentUser user = resolveIdentity(request);
        UserContext.set(user);

        RequirePermission required = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequirePermission.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(), RequirePermission.class);
        }
        if (required != null && !user.hasAnyPermission(required.value())) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "PERMISSION_DENIED", "没有执行这个操作的权限");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception exception) {
        UserContext.clear();
    }

    private CurrentUser resolveIdentity(HttpServletRequest request) {
        if (hasIdentityHeaders(request)) {
            return signer.verify(request);
        }
        if (!properties.isAllowBearerFallback()) {
            throw ApiException.unauthorized("请先登录");
        }
        String token = bearerToken(request.getHeader("Authorization"));
        if (token == null) {
            throw ApiException.unauthorized("请先登录");
        }
        return authClient.introspect(token);
    }

    private boolean isPublic(HttpServletRequest request, String path) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || "HEAD".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        List<String> patterns = properties.getPublicPaths();
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, path)) return true;
        }
        return false;
    }

    private static boolean hasIdentityHeaders(HttpServletRequest request) {
        return request.getHeader(IdentityHeaders.USER_ID) != null
                || request.getHeader(IdentityHeaders.SIGNATURE) != null
                || request.getHeader(IdentityHeaders.PERMISSIONS) != null
                || request.getHeader(IdentityHeaders.EXPIRES_AT) != null;
    }

    private static String bearerToken(String authorization) {
        if (authorization == null) return null;
        String value = authorization.trim();
        if (!value.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = value.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
