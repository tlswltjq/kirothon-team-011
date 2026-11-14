package dev.kirothon.resume.web;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 서비스가 던지는 오류. GlobalExceptionHandler가 {code, message, fields}(+ failures)로 바꾼다. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fields;
    private final List<Map<String, Object>> failures;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of(), null);
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, String> fields,
                        List<Map<String, Object>> failures) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = fields;
        this.failures = failures;
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "찾을 수 없습니다.");
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값이 올바르지 않습니다.",
                Map.of(field, message), null);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, String> fields() {
        return fields;
    }

    public List<Map<String, Object>> failures() {
        return failures;
    }
}
