package com.fishmatrix.common.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 可预期的业务异常。所有服务统一抛出本类型（或其子类），
 * 由 {@link GlobalExceptionHandler} 转换成稳定的错误响应。
 *
 * <p>5xx 的 message 会原样返回给客户端，构造时不要放入 SQL、堆栈或内部地址。</p>
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of(), null);
    }

    public ApiException(HttpStatus status, String code, String message, Throwable cause) {
        this(status, code, message, Map.of(), cause);
    }

    public ApiException(
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> details,
            Throwable cause) {
        super(message, cause);
        this.status = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        this.code = code == null || code.isBlank() ? defaultCode(this.status) : code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }

    public static ApiException of(HttpStatus status, String code, String message) {
        return new ApiException(status, code, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, message);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHORIZED, message);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.FORBIDDEN, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException tooManyRequests(String code, String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }

    public static ApiException unavailable(String code, String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    public static ApiException internal(String message, Throwable cause) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, message, cause);
    }

    private static String defaultCode(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> ErrorCodes.BAD_REQUEST;
            case 401 -> ErrorCodes.UNAUTHORIZED;
            case 403 -> ErrorCodes.FORBIDDEN;
            case 404 -> ErrorCodes.NOT_FOUND;
            case 405 -> ErrorCodes.METHOD_NOT_ALLOWED;
            case 406 -> ErrorCodes.NOT_ACCEPTABLE;
            case 409 -> ErrorCodes.CONFLICT;
            case 413 -> ErrorCodes.PAYLOAD_TOO_LARGE;
            case 415 -> ErrorCodes.UNSUPPORTED_MEDIA_TYPE;
            case 429 -> ErrorCodes.RATE_LIMITED;
            case 503, 504 -> ErrorCodes.SERVICE_UNAVAILABLE;
            default -> status.is4xxClientError() ? ErrorCodes.BAD_REQUEST : ErrorCodes.INTERNAL_ERROR;
        };
    }
}
