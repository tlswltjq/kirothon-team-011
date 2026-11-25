package dev.kirothon.resume.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 요구사항 2.1·2.2·2.4: Spring Security 체인의 인증·헤더·세션 동작. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityApiTest {
    @Autowired
    MockMvc mvc;

    private String newToken() throws Exception {
        MvcResult r = mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"보안테스트\"}")).andExpect(status().isCreated()).andReturn();
        return r.getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void req2_1_unauthenticatedApiReturnsSame401Json() throws Exception {
        for (String path : new String[] {"/api/users/me", "/api/my/resumes", "/api/review-queue/next", "/api"}) {
            mvc.perform(get(path).header(AnonTokenFilter.HEADER, "wrong"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Content-Type", startsWith("application/json")))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.message").value("익명 토큰이 없거나 올바르지 않습니다."))
                    .andExpect(jsonPath("$.fields").isMap());
        }
        mvc.perform(get("/api/users/me").header(AnonTokenFilter.HEADER, "x".repeat(129)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void req2_1_validTokenAuthenticatesAndUnmappedApiIs404() throws Exception {
        String token = newToken();
        mvc.perform(get("/api/users/me").header(AnonTokenFilter.HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("보안테스트"));
        mvc.perform(get("/api/nope").header(AnonTokenFilter.HEADER, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void req2_1_permittedPathsNeedNoToken() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/api/demo")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").isBoolean());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        // 토큰이 틀려도 허용 경로는 통과한다(기존 shouldNotFilter와 같음).
        mvc.perform(post("/api/users").header(AnonTokenFilter.HEADER, "wrong").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"허용경로\"}")).andExpect(status().isCreated());
        // /api 밖의 없는 경로는 401이 아니라 404.
        mvc.perform(get("/no-such-page")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(r ->
                assertThat(r.getResponse().getStatus()).isNotEqualTo(401));
        mvc.perform(get("/v3/api-docs")).andExpect(r ->
                assertThat(r.getResponse().getStatus()).isNotEqualTo(401));
    }

    @Test
    void req2_2_securityHeadersOnApiAndStatic() throws Exception {
        for (String path : new String[] {"/api/users/me", "/index.html", "/api/demo"}) {
            mvc.perform(get(path))
                    .andExpect(header().string("Content-Security-Policy", SecurityConfig.CSP))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"))
                    .andExpect(header().doesNotExist("Strict-Transport-Security"));
        }
    }

    @Test
    void req2_2_hstsOnlyOnHttps() throws Exception {
        mvc.perform(get("/index.html").secure(true))
                .andExpect(header().exists("Strict-Transport-Security"));
    }

    @Test
    void req2_2_swaggerUiGetsRelaxedCspOnlyThere() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(header().string("Content-Security-Policy", SecurityConfig.API_DOCS_CSP))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        assertThat(SecurityConfig.CSP).doesNotContain("unsafe-inline");
    }

    @Test
    void req2_4_noSessionOrCookieCreated() throws Exception {
        String token = newToken();
        MvcResult r = mvc.perform(get("/api/users/me").header(AnonTokenFilter.HEADER, token))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        assertThat(r.getRequest().getSession(false)).isNull();
        MvcResult denied = mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertThat(denied.getRequest().getSession(false)).isNull();
    }

    @Test
    void req2_4_csrfDisabledForHeaderTokenApi() throws Exception {
        // CSRF 토큰 없이 POST가 403이 아니라 정상 처리된다.
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"씨에스알\"}"))
                .andExpect(status().isCreated());
    }
}
