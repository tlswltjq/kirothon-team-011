package dev.kirothon.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kirothon.resume.security.TokenHasher;
import dev.kirothon.resume.user.AnonUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnonUserApiTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    AnonUserRepository users;

    private MvcResult create(String nickname) throws Exception {
        return mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nickname + "\"}")).andReturn();
    }

    private String tokenOf(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void req1_2_issuesTokenAndStoresOnlyHash() throws Exception {
        MvcResult r = mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"  취준생A  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value("취준생A"))
                .andExpect(jsonPath("$.token").isString())
                .andReturn();
        String token = tokenOf(r);
        assertThat(token).hasSize(43); // 32바이트 Base64url
        assertThat(users.findByTokenHash(TokenHasher.sha256Hex(token))).isPresent();
        assertThat(users.findByTokenHash(token)).isEmpty();

        mvc.perform(get("/api/users/me").header("X-Anon-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("취준생A"));
    }

    @Test
    void req1_2_tokensAreUniquePerRequest() throws Exception {
        assertThat(tokenOf(create("닉네임"))).isNotEqualTo(tokenOf(create("닉네임")));
    }

    @Test
    void req1_4_nicknameBoundaries() throws Exception {
        assertThat(create("가나").getResponse().getStatus()).isEqualTo(201);
        assertThat(create("가".repeat(20)).getResponse().getStatus()).isEqualTo(201);
        assertThat(create(" 가나 ").getResponse().getStatus()).isEqualTo(201);

        for (String bad : new String[] {"", "   ", "가", " 가 ", "가".repeat(21)}) {
            long before = users.count();
            mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nickname\":\"" + bad + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fields.nickname").isString());
            assertThat(users.count()).isEqualTo(before);
        }
    }

    @Test
    void req1_4_missingNicknameOrBadJsonRejected() throws Exception {
        long before = users.count();
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(users.count()).isEqualTo(before);
    }

    @Test
    void req1_5_missingTokenIs401() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.fields").isMap());
    }

    @Test
    void req1_5_unknownTokenIs401() throws Exception {
        mvc.perform(get("/api/users/me").header("X-Anon-Token", "unknown-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/users/me").header("X-Anon-Token", " "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void req1_5_unknownApiPathWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void req11_6_unmappedPathIs404WithErrorFormat() throws Exception {
        String token = tokenOf(create("닉네임"));
        mvc.perform(get("/api/does-not-exist").header("X-Anon-Token", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.fields").isMap());
        mvc.perform(get("/nothing-here.html"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void req11_8_securityHeadersPresent() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'self'; img-src 'self' blob:; script-src 'self'; style-src 'self'; "
                                + "object-src 'none'; frame-ancestors 'none'"));
    }

    @Test
    void req11_8_onlyHealthActuatorExposed() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/beans")).andExpect(status().isNotFound());
    }
}
