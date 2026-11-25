package dev.kirothon.resume.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 요구사항 2.3: IP별 분당 한도 초과 시 429 RATE_LIMITED + Retry-After. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:rate-limit-test;DB_CLOSE_DELAY=-1",
        "app.rate-limit.users-per-minute=3",
        "app.rate-limit.uploads-per-minute=2"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitApiTest {
    @Autowired
    MockMvc mvc;

    private static RequestPostProcessor ip(String addr) {
        return r -> {
            r.setRemoteAddr(addr);
            return r;
        };
    }

    private MvcResult createUser(String addr) throws Exception {
        return mvc.perform(post("/api/users").with(ip(addr)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"한도테스트\"}")).andReturn();
    }

    @Test
    void req2_3_createUserOverLimitIs429WithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(createUser("10.0.0.1").getResponse().getStatus()).isEqualTo(201);
        }
        MvcResult over = mvc.perform(post("/api/users").with(ip("10.0.0.1")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"한도테스트\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.fields").isMap())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        long retryAfter = Long.parseLong(over.getResponse().getHeader("Retry-After"));
        assertThat(retryAfter).isBetween(1L, 60L);
        // 다른 IP는 영향받지 않는다.
        assertThat(createUser("10.0.0.2").getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void req2_3_uploadOverLimitIs429() throws Exception {
        String body = createUser("10.0.0.9").getResponse().getContentAsString();
        String token = body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        MockMultipartFile notPdf = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        // 업로드 두 경로(새 이력서, 새 버전)가 같은 한도를 나눠 쓴다. 한도 안에서는 정상 검증 오류가 난다.
        int first = mvc.perform(multipart("/api/my/resumes").file(notPdf).param("title", "t").param("targetRole", "r")
                .header(AnonTokenFilter.HEADER, token).with(ip("10.0.0.9"))).andReturn().getResponse().getStatus();
        int second = mvc.perform(multipart("/api/my/resumes/" + UUID.randomUUID() + "/versions").file(notPdf)
                .header(AnonTokenFilter.HEADER, token).with(ip("10.0.0.9"))).andReturn().getResponse().getStatus();
        assertThat(first).isNotEqualTo(429);
        assertThat(second).isNotEqualTo(429);
        mvc.perform(multipart("/api/my/resumes").file(notPdf).param("title", "t").param("targetRole", "r")
                        .header(AnonTokenFilter.HEADER, token).with(ip("10.0.0.9")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }
}
