package dev.kirothon.resume.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** prod 프로필: actuator는 별도 관리 포트에만 있고 Prometheus 지표를 낸다(요구사항 3.2). DB는 H2 메모리로 덮어쓴다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        // prod와 같게 Flyway + validate로 띄우되 DB만 PostgreSQL 모드 H2로 바꾼다.
        "spring.datasource.url=jdbc:h2:mem:ops-prod-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@ActiveProfiles("prod")
class ProdManagementPortTest {
    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    int serverPort;
    @Value("${local.management.port}")
    int managementPort;

    private HttpResponse<String> get(int port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void req3_2_managementOnSeparatePort() {
        assertThat(managementPort).isPositive().isNotEqualTo(serverPort);
    }

    @Test
    void req3_2_prometheusOnManagementPort() throws Exception {
        HttpResponse<String> r = get(managementPort, "/actuator/prometheus");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("jvm_memory_used_bytes");
    }

    @Test
    void req3_2_readinessOnManagementPortWithoutDetails() throws Exception {
        HttpResponse<String> r = get(managementPort, "/actuator/health/readiness");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("\"UP\"").doesNotContain("components");
    }

    @Test
    void req3_2_actuatorNotOnAppPort() throws Exception {
        assertThat(get(serverPort, "/actuator/prometheus").statusCode()).isNotEqualTo(200);
        assertThat(get(serverPort, "/actuator/health").statusCode()).isNotEqualTo(200);
    }

    @Test
    void req3_3_requestIdOnAppPort() throws Exception {
        HttpResponse<String> r = get(serverPort, "/");
        assertThat(r.headers().firstValue(RequestIdFilter.HEADER)).isPresent();
    }
}
