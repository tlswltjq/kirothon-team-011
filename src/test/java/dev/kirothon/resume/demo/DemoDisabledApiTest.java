package dev.kirothon.resume.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 요구사항 12.2: 데모 프로필이 꺼지면 상태는 enabled=false, 시드 API는 404 NOT_FOUND. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:demo-disabled-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoDisabledApiTest {
    @Autowired
    MockMvc mvc;

    @Test
    void req12_2_demoDisabledHidesSeed() throws Exception {
        MvcResult st = mvc.perform(get("/api/demo")).andReturn();
        assertThat(st.getResponse().getStatus()).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(st.getResponse().getContentAsString(), "$.enabled")).isFalse();

        String body = mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"일반사용자\"}")).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        MvcResult seed = mvc.perform(post("/api/demo/seed").header("X-Anon-Token", token)).andReturn();
        assertThat(seed.getResponse().getStatus()).isEqualTo(404);
        assertThat((String) JsonPath.read(seed.getResponse().getContentAsString(), "$.code")).isEqualTo("NOT_FOUND");
    }
}
