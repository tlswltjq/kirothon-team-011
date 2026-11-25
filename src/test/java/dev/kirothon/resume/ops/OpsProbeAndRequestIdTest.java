package dev.kirothon.resume.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpsProbeAndRequestIdTest {
    @Autowired
    MockMvc mvc;
    @Value("${server.shutdown}")
    String shutdown;

    @Test
    void req3_1_livenessProbeIsUp() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void req3_1_readinessProbeIsUp() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void req3_3_generatesRequestIdWhenMissing() throws Exception {
        String id = mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(id).matches("[0-9a-f-]{36}");
    }

    @Test
    void req3_3_propagatesSafeRequestId() throws Exception {
        mvc.perform(get("/actuator/health").header(RequestIdFilter.HEADER, "abc-123_XYZ"))
                .andExpect(header().string(RequestIdFilter.HEADER, "abc-123_XYZ"));
    }

    @Test
    void req3_3_replacesUnsafeRequestId() throws Exception {
        for (String bad : new String[] {"bad id\r\nx", "a".repeat(65), "<script>", ""}) {
            String id = mvc.perform(get("/actuator/health").header(RequestIdFilter.HEADER, bad))
                    .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);
            assertThat(id).isNotEqualTo(bad).matches("[0-9a-f-]{36}");
        }
    }

    @Test
    void req3_3_requestIdOnApiErrorResponse() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().is4xxClientError())
                .andExpect(header().exists(RequestIdFilter.HEADER));
    }

    @Test
    void req3_4_gracefulShutdownEnabled() {
        assertThat(shutdown).isEqualTo("graceful");
    }
}
