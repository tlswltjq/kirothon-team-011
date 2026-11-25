package dev.kirothon.resume.web;

import dev.kirothon.resume.pdf.PdfTypes.PdfRejectedException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_FAILED", "입력값이 올바르지 않습니다.", ApiError.sorted(fields)));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestValueException.class})
    public ResponseEntity<ApiError> unreadable(Exception e) {
        // 본문 내용은 메시지에 넣지 않는다.
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_FAILED", "요청 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ApiError.of("FILE_TOO_LARGE", "파일은 5MB 이하여야 합니다."));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> api(ApiException e) {
        if (e.failures() != null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", e.code());
            body.put("message", e.getMessage());
            body.put("fields", e.fields());
            body.put("failures", e.failures());
            return ResponseEntity.status(e.status()).body(body);
        }
        return ResponseEntity.status(e.status())
                .body(new ApiError(e.code(), e.getMessage(), ApiError.sorted(e.fields())));
    }

    /** 경로 변수(UUID·번호) 형식 오류는 존재하지 않는 자원과 같게 404로 처리한다. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> badPathVariable(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("NOT_FOUND", "찾을 수 없습니다."));
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class,
            HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ApiError> badMultipart(Exception e) {
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_FAILED", "요청 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(PdfRejectedException.class)
    public ResponseEntity<ApiError> pdfRejected(PdfRejectedException e) {
        return ResponseEntity.status(e.status()).body(ApiError.of(e.code(), e.getMessage()));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("NOT_FOUND", "찾을 수 없습니다."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiError.of("METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        // 스택·메시지에 사용자 입력이 섞일 수 있어 예외 클래스 이름만 남긴다.
        log.error("INTERNAL_ERROR type={}", e.getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "서버 오류가 발생했습니다."));
    }
}
