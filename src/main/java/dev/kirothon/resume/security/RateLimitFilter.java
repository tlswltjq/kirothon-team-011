package dev.kirothon.resume.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요구사항 2.3: POST /api/users(사용자 발급)와 PDF 업로드(POST /api/my/resumes, POST /api/my/resumes/{id}/versions)를
 * 클라이언트 IP(request.getRemoteAddr(), 프록시 헤더 처리는 server.forward-headers-strategy에 맡김)별로 제한한다.
 * 초과하면 429 RATE_LIMITED + Retry-After(초). 빈으로 등록하지 않고 SecurityConfig에서 체인에 넣는다.
 */
public class RateLimitFilter extends OncePerRequestFilter {
    static final String MESSAGE = "요청이 너무 많습니다. 잠시 후 다시 시도하세요.";

    private static final RequestMatcher USERS = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST, "/api/users");
    private static final RequestMatcher UPLOADS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/my/resumes"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/my/resumes/*/versions"));

    private final FixedWindowRateLimiter limiter;
    private final int usersPerMinute;
    private final int uploadsPerMinute;

    public RateLimitFilter(FixedWindowRateLimiter limiter, int usersPerMinute, int uploadsPerMinute) {
        this.limiter = limiter;
        this.usersPerMinute = usersPerMinute;
        this.uploadsPerMinute = uploadsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String bucket;
        int limit;
        if (USERS.matches(request)) {
            bucket = "users";
            limit = usersPerMinute;
        } else if (UPLOADS.matches(request)) {
            bucket = "uploads";
            limit = uploadsPerMinute;
        } else {
            chain.doFilter(request, response);
            return;
        }
        FixedWindowRateLimiter.Decision decision = limiter.tryAcquire(bucket + "|" + request.getRemoteAddr(), limit);
        if (!decision.allowed()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
            JsonErrorWriter.write(response, 429, "RATE_LIMITED", MESSAGE);
            return;
        }
        chain.doFilter(request, response);
    }
}
