package com.fishmatrix.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fishmatrix.common.autoconfigure.FishCommonProperties;
import com.fishmatrix.common.trace.TraceIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new FishCommonProperties());
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void businessExceptionKeepsCodeAndAddsTraceId() {
        MDC.put(TraceIds.MDC_TRACE_ID, "4bf92f3577b34da6a3ce929d0e0e4736");
        MDC.put(TraceIds.MDC_REQUEST_ID, "4bf92f3577b34da6a3ce929d0e0e4736");

        ResponseEntity<ApiErrorResponse> response = handler.handleApi(
                new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "这个名号已经有人用了"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("USERNAME_TAKEN");
        assertThat(response.getBody().message()).isEqualTo("这个名号已经有人用了");
        assertThat(response.getBody().traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(response.getBody().requestId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/test");
        // 响应头只由 TraceContextFilter 写入，避免重复；处理器不重复设置。
        assertThat(response.getHeaders().containsKey("X-Trace-ID")).isFalse();
    }

    @Test
    void unexpectedExceptionNeverLeaksInternalMessage() {
        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                new IllegalStateException("password=secret jdbc:mysql://internal/db"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.INTERNAL_ERROR);
        assertThat(response.getBody().message()).doesNotContain("secret").doesNotContain("jdbc");
        assertThat(response.getBody().traceId()).matches("[0-9a-f]{32}");
    }

    @Test
    void dataAccessFailureUsesServiceSpecificCode() {
        FishCommonProperties properties = new FishCommonProperties();
        properties.getError().setServiceUnavailableCode("USER_UNAVAILABLE");
        properties.getError().setServiceUnavailableMessage("用户服务暂不可用");
        GlobalExceptionHandler custom = new GlobalExceptionHandler(properties);

        ResponseEntity<ApiErrorResponse> response = custom.handleDataAccess(
                new DataAccessResourceFailureException("db down"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("USER_UNAVAILABLE");
        assertThat(response.getBody().message()).isEqualTo("用户服务暂不可用");
    }

    @Test
    void validationErrorsExposeOnlyFieldNamesAndMessages() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new RegisterForm(), "request");
        binding.rejectValue("username", "NotBlank", "名号不能为空");

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(new BindException(binding), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo(ErrorCodes.VALIDATION_FAILED);
        assertThat(response.getBody().details()).containsKey("fields");
    }

    static class RegisterForm {

        private String username;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }
}
