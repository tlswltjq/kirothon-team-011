package dev.kirothon.resume;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ConfigTest {
    @Value("${server.address}")
    String address;
    @Value("${management.endpoints.web.exposure.include}")
    String exposure;

    @Test
    void req11_8_bindsLoopbackAndExposesHealthOnly() {
        assertThat(address).isEqualTo("127.0.0.1");
        assertThat(exposure).isEqualTo("health");
    }
}
