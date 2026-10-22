package com.fishmatrix.common.autoconfigure;

import com.fishmatrix.common.error.ErrorCodes;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fm.common")
public class FishCommonProperties {

    private final Error error = new Error();
    private final Log log = new Log();

    public Error getError() {
        return error;
    }

    public Log getLog() {
        return log;
    }

    public static class Error {

        /** 数据库等依赖不可用时返回的业务码，各服务可覆盖（如 USER_UNAVAILABLE）。 */
        private String serviceUnavailableCode = ErrorCodes.SERVICE_UNAVAILABLE;

        private String serviceUnavailableMessage = "服务暂不可用，请稍后重试";

        private boolean exposeValidationDetails = true;

        public String getServiceUnavailableCode() {
            return serviceUnavailableCode;
        }

        public void setServiceUnavailableCode(String serviceUnavailableCode) {
            this.serviceUnavailableCode = serviceUnavailableCode;
        }

        public String getServiceUnavailableMessage() {
            return serviceUnavailableMessage;
        }

        public void setServiceUnavailableMessage(String serviceUnavailableMessage) {
            this.serviceUnavailableMessage = serviceUnavailableMessage;
        }

        public boolean isExposeValidationDetails() {
            return exposeValidationDetails;
        }

        public void setExposeValidationDetails(boolean exposeValidationDetails) {
            this.exposeValidationDetails = exposeValidationDetails;
        }
    }

    public static class Log {

        private boolean accessLogEnabled = true;

        public boolean isAccessLogEnabled() {
            return accessLogEnabled;
        }

        public void setAccessLogEnabled(boolean accessLogEnabled) {
            this.accessLogEnabled = accessLogEnabled;
        }
    }
}
