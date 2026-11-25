package dev.kirothon.resume.ops;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 모든 요청에 X-Request-Id를 붙인다(요구사항 3.3). 안전한 형식의 값만 이어 받고, 아니면 새로 만든다.
 * 값은 MDC {@code requestId}와 응답 헤더에 넣는다. 본문·토큰은 다루지 않는다.
 * Spring Security 체인(order -100)보다 먼저 돈다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    private static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".id";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 오류 디스패치에서는 처음 정한 값을 그대로 쓴다.
        String requestId = request.getAttribute(ATTRIBUTE) instanceof String existing ? existing : resolve(request);
        request.setAttribute(ATTRIBUTE, requestId);
        MDC.put(MDC_KEY, requestId);
        if (!response.isCommitted()) {
            response.setHeader(HEADER, requestId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolve(HttpServletRequest request) {
        String incoming = request.getHeader(HEADER);
        return incoming != null && SAFE.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
