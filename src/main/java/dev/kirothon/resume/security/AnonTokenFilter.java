package dev.kirothon.resume.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요구사항 2.1: SecurityFilterChain 안의 인증 필터. X-Anon-Token을 SHA-256 해시로 조회해
 * principal이 AnonUser인 Authentication을 SecurityContext에 넣는다.
 * 기존 컨트롤러 호환을 위해 같은 AnonUser를 request attribute "anonUser"에도 둔다.
 * 토큰이 없거나 틀리면 아무것도 하지 않고, 인가 단계에서 401(AnonAuthenticationEntryPoint)이 난다.
 * 빈으로 등록하지 않는다(서블릿 필터로 한 번 더 등록되지 않게 SecurityConfig에서 직접 만든다).
 */
public class AnonTokenFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Anon-Token";
    public static final String USER_ATTRIBUTE = "anonUser";
    static final int MAX_TOKEN_LENGTH = 128;

    private final AuthenticationManager authenticationManager;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public AnonTokenFilter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        if (token != null && !token.isBlank() && token.length() <= MAX_TOKEN_LENGTH) {
            try {
                Authentication result = authenticationManager.authenticate(AnonTokenAuthentication.unauthenticated(token));
                SecurityContext context = contextHolder.createEmptyContext();
                context.setAuthentication(result);
                contextHolder.setContext(context);
                request.setAttribute(USER_ATTRIBUTE, result.getPrincipal());
            } catch (AuthenticationException e) {
                contextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
