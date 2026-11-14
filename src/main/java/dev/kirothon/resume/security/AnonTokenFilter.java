package dev.kirothon.resume.security;

import dev.kirothon.resume.user.AnonUser;
import dev.kirothon.resume.user.AnonUserRepository;
import dev.kirothon.resume.web.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** /api/** 요청의 X-Anon-Token 해시로 사용자를 찾는다. 실패하면 401 UNAUTHENTICATED. */
@Component
public class AnonTokenFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Anon-Token";
    public static final String USER_ATTRIBUTE = "anonUser";
    private static final int MAX_TOKEN_LENGTH = 128;

    private final AnonUserRepository users;

    public AnonTokenFilter(AnonUserRepository users) {
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") && !path.equals("/api")) {
            return true;
        }
        String method = request.getMethod();
        if (path.equals("/api/users") && HttpMethod.POST.matches(method)) {
            return true;
        }
        return path.equals("/api/demo") && HttpMethod.GET.matches(method);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        Optional<AnonUser> user = Optional.empty();
        if (token != null && !token.isBlank() && token.length() <= MAX_TOKEN_LENGTH) {
            user = users.findByTokenHash(TokenHasher.sha256Hex(token));
        }
        if (user.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(ApiError.json("UNAUTHENTICATED", "익명 토큰이 없거나 올바르지 않습니다."));
            return;
        }
        request.setAttribute(USER_ATTRIBUTE, user.get());
        chain.doFilter(request, response);
    }
}
