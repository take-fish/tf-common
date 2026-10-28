package com.fishmatrix.common.error;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.fishmatrix.common.autoconfigure.FishCommonProperties;
import com.fishmatrix.common.trace.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全服务统一异常出口。任何异常都必须：
 * <ul>
 *   <li>返回固定的 error/message 结构，并附带 traceId；</li>
 *   <li>5xx 只记录服务端日志，不把内部异常信息返回给客户端；</li>
 *   <li>4xx 业务异常记 INFO，便于统计但不制造告警噪音。</li>
 * </ul>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final int MAX_FIELD_ERRORS = 20;

    private final FishCommonProperties properties;

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException exception, HttpServletRequest request) {
        HttpStatus status = exception.status();
        String message = defaultMessage(exception.getMessage(), status);
        log(binding(status, exception.code(), message, request, exception.details()));
        return build(status, exception.code(), message, request, exception.details());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiErrorResponse> handleValidation(BindException exception, HttpServletRequest request) {
        Map<String, Object> details = validationDetails(exception);
        log(binding(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "请求参数校验未通过", request, details));
        return build(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "请求参数校验未通过", request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraint(
            ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            if (fields.size() >= MAX_FIELD_ERRORS) {
                break;
            }
            fields.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        Map<String, Object> details = properties.getError().isExposeValidationDetails()
                ? Map.of("fields", fields)
                : Map.of();
        log(binding(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "请求参数校验未通过", request, details));
        return build(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "请求参数校验未通过", request, details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(
            HandlerMethodValidationException exception, HttpServletRequest request) {
        return simple(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "请求参数校验未通过", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception, HttpServletRequest request) {
        String message = defaultMessage(exception.getMessage(), HttpStatus.BAD_REQUEST);
        return simple(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, message, request);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class,
        MissingRequestHeaderException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        return simple(HttpStatus.BAD_REQUEST, ErrorCodes.BAD_REQUEST, "请求参数格式不正确", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return simple(HttpStatus.METHOD_NOT_ALLOWED, ErrorCodes.METHOD_NOT_ALLOWED, "不支持该请求方法", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMedia(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return simple(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCodes.UNSUPPORTED_MEDIA_TYPE, "不支持该内容类型", request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException exception, HttpServletRequest request) {
        return simple(HttpStatus.NOT_ACCEPTABLE, ErrorCodes.NOT_ACCEPTABLE, "无法生成客户端可接受的响应", request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(Exception exception, HttpServletRequest request) {
        return simple(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "请求的资源不存在", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleTooLarge(
            MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return simple(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCodes.PAYLOAD_TOO_LARGE, "文件大小超过限制", request);
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleAsyncTimeout(
            AsyncRequestTimeoutException exception, HttpServletRequest request) {
        return simple(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.REQUEST_TIMEOUT, "请求处理超时，请稍后重试", request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicate(
            DuplicateKeyException exception, HttpServletRequest request) {
        log.warn("数据唯一键冲突", kv("event", "api.duplicate_key"), exception);
        return simple(HttpStatus.CONFLICT, ErrorCodes.CONFLICT, "数据已存在", request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDataAccess(
            DataAccessException exception, HttpServletRequest request) {
        String code = properties.getError().getServiceUnavailableCode();
        String message = properties.getError().getServiceUnavailableMessage();
        Binding binding = binding(HttpStatus.SERVICE_UNAVAILABLE, code, message, request, Map.of());
        logServerError(binding, exception);
        return build(HttpStatus.SERVICE_UNAVAILABLE, code, message, request, Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = status.is5xxServerError()
                ? defaultMessage(null, status)
                : defaultMessage(exception.getReason(), status);
        String code = "HTTP_" + status.value();
        if (status.is5xxServerError()) {
            Binding binding = binding(status, code, message, request, Map.of());
            logServerError(binding, exception);
            return build(status, code, message, request, Map.of());
        }
        log(binding(status, code, message, request, Map.of()));
        return build(status, code, message, request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        String message = "服务开小差了，请稍后重试";
        Binding binding = binding(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, message, request, Map.of());
        logServerError(binding, exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, message, request, Map.of());
    }

    private ResponseEntity<ApiErrorResponse> simple(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        Binding binding = binding(status, code, message, request, Map.of());
        log(binding);
        return build(status, code, message, request, Map.of());
    }

    private void log(Binding binding) {
        if (binding.status.is5xxServerError()) {
            log.error("接口返回服务端错误", kv("event", "api.error"),
                    kv("error_code", binding.code), kv("error_message", binding.message),
                    kv("http_method", binding.method), kv("http_path", binding.path),
                    kv("http_status", binding.status.value()));
        } else {
            log.info("接口返回业务错误", kv("event", "api.rejected"),
                    kv("error_code", binding.code), kv("error_message", binding.message),
                    kv("http_method", binding.method), kv("http_path", binding.path),
                    kv("http_status", binding.status.value()));
        }
    }

    private void logServerError(Binding binding, Exception exception) {
        log.error("接口处理失败", kv("event", "api.failure"),
                kv("error_code", binding.code), kv("error_message", binding.message),
                kv("http_method", binding.method), kv("http_path", binding.path),
                kv("http_status", binding.status.value()), exception);
    }

    private Binding binding(
            HttpStatus status, String code, String message, HttpServletRequest request, Map<String, Object> details) {
        return new Binding(status, code, message, request.getMethod(), safePath(request), details);
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status, String code, String message, HttpServletRequest request, Map<String, Object> details) {
        String traceId = TraceIds.ensureTraceId();
        String requestId = TraceIds.currentRequestId();
        ApiErrorResponse body = new ApiErrorResponse(
                code, message, traceId, requestId, safePath(request), Instant.now(), details);
        // 响应头由 TraceContextFilter 统一写入；这里再写会与过滤器重复（HTTP 头会出现多份）。
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    private Map<String, Object> validationDetails(BindException exception) {
        if (!properties.getError().isExposeValidationDetails()) {
            return Map.of();
        }
        Map<String, Object> fields = exception.getBindingResult().getFieldErrors().stream()
                .limit(MAX_FIELD_ERRORS)
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> defaultMessage(error.getDefaultMessage(), HttpStatus.BAD_REQUEST),
                        (first, second) -> first,
                        LinkedHashMap::new));
        return fields.isEmpty() ? Map.of() : Map.of("fields", fields);
    }

    private static String defaultMessage(String message, HttpStatus status) {
        if (message != null && !message.isBlank()) {
            return message;
        }
        return switch (status.value()) {
            case 400 -> "请求参数不正确";
            case 401 -> "请先登录";
            case 403 -> "没有权限执行该操作";
            case 404 -> "请求的资源不存在";
            case 405 -> "不支持该请求方法";
            case 406 -> "无法生成客户端可接受的响应";
            case 409 -> "请求冲突";
            case 413 -> "文件大小超过限制";
            case 415 -> "不支持的内容类型";
            case 429 -> "请求太频繁，请稍后再试";
            case 500 -> "服务开小差了，请稍后重试";
            case 503, 504 -> "服务暂不可用，请稍后重试";
            default -> status.is4xxClientError() ? "请求不正确" : "服务开小差了，请稍后重试";
        };
    }

    private static String safePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "/";
        }
        return uri.length() > 512 ? uri.substring(0, 512) : uri;
    }

    private record Binding(
            HttpStatus status, String code, String message, String method, String path, Map<String, Object> details) {
    }
}
