package dev.kirothon.resume.resume;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/** 실제 포트로 multipart 한도(5MB)를 검사한다. MockMvc는 컨테이너 한도를 적용하지 않는다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UploadLimitPortTest {
    @LocalServerPort
    int port;
    @Autowired
    ResumeRepository resumes;

    @Test
    void req2_4_realServerRejectsOver5MbWith413() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> user = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"nickname\":\"큰파일\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        String token = JsonPath.read(user.body(), "$.token");

        String boundary = "----kirothonBoundary";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\nt\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"targetRole\"\r\n\r\nr\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] big = new byte[5 * 1024 * 1024 + 1024];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII), 0, big, 0, 5);
        body.write(big);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        long before = resumes.count();
        HttpResponse<String> res = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/my/resumes"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Anon-Token", token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(413);
        assertThat(JsonPath.<String>read(res.body(), "$.code")).isEqualTo("FILE_TOO_LARGE");
        assertThat(resumes.count()).isEqualTo(before);
    }
}
