package dev.kirothon.resume.security;

import jakarta.servlet.DispatcherType;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * 요구사항 2.1~2.4. 체인 두 개:
 * 1) API 문서(Swagger UI·/v3/api-docs) 전용: 인증 없음, Swagger UI가 동작하도록 CSP만 최소 완화.
 * 2) 나머지 전체: /api/** 는 익명 토큰 인증 필수(POST /api/users, GET /api/demo 제외), 그 밖은 기존처럼 통과.
 */
@Configuration
public class SecurityConfig {
    /** 기존 SecurityHeadersFilter와 같은 값. */
    static final String CSP = "default-src 'self'; img-src 'self' blob:; script-src 'self'; style-src 'self'; "
            + "object-src 'none'; frame-ancestors 'none'";
    /** Swagger UI는 인라인 스크립트·스타일과 data: 이미지를 쓴다. 이 경로에만 적용한다. */
    static final String API_DOCS_CSP = "default-src 'self'; img-src 'self' data:; script-src 'self' 'unsafe-inline'; "
            + "style-src 'self' 'unsafe-inline'; object-src 'none'; frame-ancestors 'none'";

    static final String[] API_DOCS_PATHS = {"/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
            "/swagger-ui/**", "/swagger-ui.html"};

    @Bean
    FixedWindowRateLimiter fixedWindowRateLimiter() {
        return new FixedWindowRateLimiter(Clock.systemUTC());
    }

    @Bean
    @Order(1)
    SecurityFilterChain apiDocsSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(API_DOCS_PATHS)
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        common(http, API_DOCS_CSP);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain appSecurityFilterChain(HttpSecurity http, AnonTokenAuthenticationProvider provider,
                                               FixedWindowRateLimiter limiter,
                                               @Value("${app.rate-limit.users-per-minute:10}") int usersPerMinute,
                                               @Value("${app.rate-limit.uploads-per-minute:20}") int uploadsPerMinute)
            throws Exception {
        AuthenticationManager authenticationManager = new ProviderManager(provider);
        http.authorizeHttpRequests(a -> a
                        // 오류 디스패치(/error)는 원래 요청의 상태 코드를 그대로 보여 줘야 한다.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/error").permitAll()
                        // 인증 없이 쓰는 API
                        .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/demo").permitAll()
                        // 그 밖의 /api/** 는 매핑이 없어도 먼저 401(기존 동작: 토큰 있으면 404)
                        .requestMatchers("/api", "/api/**").authenticated()
                        // 정적 자원·헬스·지표(prometheus는 prod에서 관리 포트에만 노출)
                        .requestMatchers("/", "/*.html", "/css/**", "/js/**", "/img/**", "/favicon.ico").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                        // 기존 AnonTokenFilter는 /api 밖을 전부 통과시켰다. 없는 경로가 401이 아니라 404로 나도록 유지한다.
                        // (actuator는 노출 설정(management.endpoints.web.exposure.include)으로 제한된다.)
                        .anyRequest().permitAll())
                .addFilterBefore(new AnonTokenFilter(authenticationManager), AnonymousAuthenticationFilter.class)
                .addFilterBefore(new RateLimitFilter(limiter, usersPerMinute, uploadsPerMinute), AnonTokenFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(unauthenticated())
                        .accessDeniedHandler(forbidden()));
        common(http, CSP);
        return http.build();
    }

    private static void common(HttpSecurity http, String csp) throws Exception {
        http
                // 세션을 만들지 않는다(2.4). 인증은 매 요청의 X-Anon-Token 헤더로만 한다.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF 끔(2.4): 자격 증명이 쿠키가 아니라 JS가 직접 붙이는 X-Anon-Token 헤더라서
                // 다른 사이트가 브라우저로 위조 요청을 보내도 토큰이 자동 전송되지 않는다.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                // 보안 헤더(2.2). HSTS는 Spring 기본(HTTPS 요청에만)을 그대로 둔다.
                .headers(h -> h
                        .contentSecurityPolicy(c -> c.policyDirectives(csp))
                        .contentTypeOptions(c -> { })
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)));
    }

    private static AuthenticationEntryPoint unauthenticated() {
        return (request, response, e) -> JsonErrorWriter.write(response, HttpStatus.UNAUTHORIZED.value(),
                "UNAUTHENTICATED", JsonErrorWriter.UNAUTHENTICATED_MESSAGE);
    }

    private static AccessDeniedHandler forbidden() {
        return (request, response, e) -> JsonErrorWriter.write(response, HttpStatus.FORBIDDEN.value(),
                "FORBIDDEN", "접근할 수 없습니다.");
    }
}
