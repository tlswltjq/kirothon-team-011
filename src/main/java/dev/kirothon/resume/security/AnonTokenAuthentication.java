package dev.kirothon.resume.security;

import dev.kirothon.resume.user.AnonUser;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

/** 인증 전에는 토큰 원문을, 인증 후에는 AnonUser를 principal로 담는다. 토큰 원문은 인증 후 지운다. */
public final class AnonTokenAuthentication extends AbstractAuthenticationToken {
    private final Object principal;
    private String token;

    private AnonTokenAuthentication(String token) {
        super(List.of());
        this.principal = null;
        this.token = token;
        setAuthenticated(false);
    }

    private AnonTokenAuthentication(AnonUser user) {
        super(AuthorityUtils.createAuthorityList("ROLE_ANON_USER"));
        this.principal = user;
        this.token = null;
        super.setAuthenticated(true);
    }

    public static AnonTokenAuthentication unauthenticated(String token) {
        return new AnonTokenAuthentication(token);
    }

    public static AnonTokenAuthentication authenticated(AnonUser user) {
        return new AnonTokenAuthentication(user);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
        if (authenticated) {
            throw new IllegalArgumentException("authenticated(AnonUser)로만 인증 상태를 만든다.");
        }
        super.setAuthenticated(false);
    }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        token = null;
    }
}
