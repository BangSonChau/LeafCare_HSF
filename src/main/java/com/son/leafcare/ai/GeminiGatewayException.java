package com.son.leafcare.ai;

public class GeminiGatewayException extends RuntimeException {

    private final ErrorCode errorCode;

    public enum ErrorCode {
        QUOTA_EXCEEDED("Đã hết hạn mức gọi Gemini API tạm thời. Vui lòng đợi một lát rồi thử lại."),
        TIMEOUT("Yêu cầu tới Gemini quá thời gian chờ. Vui lòng thử lại."),
        AUTH_ERROR("Chưa cấu hình hoặc khóa GEMINI_API_KEY không hợp lệ."),
        PARSE_ERROR("Phản hồi từ AI không đúng định dạng kiểm duyệt."),
        UPSTREAM_ERROR("Không thể kết nối tới dịch vụ Gemini lúc này. Vui lòng thử lại.");

        private final String userMessage;

        ErrorCode(String userMessage) {
            this.userMessage = userMessage;
        }

        public String getUserMessage() {
            return userMessage;
        }
    }

    public GeminiGatewayException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public GeminiGatewayException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}

