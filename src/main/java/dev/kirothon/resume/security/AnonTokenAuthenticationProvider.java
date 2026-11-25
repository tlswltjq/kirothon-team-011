package dev.kirothon.resume.security;

import dev.kirothon.resume.user.AnonUserRepository;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 토큰 원문의 SHA-256 해시로 AnonUser를 찾는다(DB에는 해시만 있다).
 * AuthenticationProvider 빈이 있으므로 Spring Boot의 기본 InMemoryUserDetailsManager(생성 비밀번호 로그)는 만들어지지 않는다.
 */
@Component
public class AnonTokenAuthenticationProvider implements AuthenticationProvider {
    private final AnonUserRepository users;

    public AnonTokenAuthenticationProvider(AnonUserRepository users) {
        this.users = users;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        Object token = authentication.getCredentials();
        if (!(token instanceof String raw) || raw.isBlank() || raw.length() > AnonTokenFilter.MAX_TOKEN_LENGTH) {
            throw new BadCredentialsException("invalid token");
        }
        return users.findByTokenHash(TokenHasher.sha256Hex(raw))
                .map(AnonTokenAuthentication::authenticated)
                .orElseThrow(() -> new BadCredentialsException("invalid token"));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return AnonTokenAuthentication.class.isAssignableFrom(authentication);
    }
}
