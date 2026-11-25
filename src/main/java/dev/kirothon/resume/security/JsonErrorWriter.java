package dev.kirothon.resume.security;

import dev.kirothon.resume.web.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/** 보안 필터(MVC 밖)에서 ApiError 형식({code,message,fields}) JSON을 쓴다. */
final class JsonErrorWriter {
    static final String UNAUTHENTICATED_MESSAGE = "익명 토큰이 없거나 올바르지 않습니다.";

    private JsonErrorWriter() {
    }

    static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(ApiError.json(code, message));
    }
}
