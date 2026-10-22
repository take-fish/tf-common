package com.fishmatrix.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * 统一的错误响应体。保留 error/message 字段以兼容现有前端，
 * 新增 traceId/requestId/path/timestamp 用于定位日志。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String error,
        String message,
        String traceId,
        String requestId,
        String path,
        Instant timestamp,
        Map<String, Object> details) {

    public ApiErrorResponse {
        message = message == null ? "" : message;
        details = details == null || details.isEmpty() ? null : Map.copyOf(details);
    }
}
