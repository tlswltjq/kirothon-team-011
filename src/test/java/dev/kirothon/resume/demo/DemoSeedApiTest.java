package dev.kirothon.resume.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 요구사항 12: 데모 프로필에서 샘플 데이터 시드. 실제 업로드·마스킹·게시·평가 경로를 거친다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:demo-seed-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
class DemoSeedApiTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    int seq;

    @BeforeEach
    void clean() {
        for (String t : List.of("annotation", "review", "page_image", "mask_region", "mask_term", "draft_source",
                "resume_version", "resume", "anon_user")) {
            jdbc.update("delete from " + t);
        }
    }

    private String token() throws Exception {
        String body = mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"요청자" + (++seq) + "\"}")).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private MvcResult seed(String token) throws Exception {
        return mvc.perform(post("/api/demo/seed").header("X-Anon-Token", token)).andReturn();
    }

    private String json(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString();
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void req12_2_demoStatusIsPublicAndEnabled() throws Exception {
        MvcResult r = mvc.perform(get("/api/demo")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat((Boolean) JsonPath.read(json(r), "$.enabled")).isTrue();
    }

    @Test
    void req12_3_seedNeedsToken() throws Exception {
        MvcResult r = mvc.perform(post("/api/demo/seed")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertThat(count("select count(*) from anon_user where sample = true")).isZero();
    }

    @Test
    void req12_3_seedCreatesSampleUsersResumesAndReviewsWithPins() throws Exception {
        String me = token();
        MvcResult r = seed(me);
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        String resumeId = JsonPath.read(json(r), "$.resumeId");

        // 샘플 사용자 3명, 샘플 이력서 3개(요청자 1 + 샘플 사용자 2), 모두 게시됨
        assertThat(count("select count(*) from anon_user where sample = true")).isEqualTo(3);
        assertThat(count("select count(*) from resume where sample = true")).isEqualTo(3);
        assertThat(count("select count(*) from resume_version where status = 'PUBLISHED'")).isEqualTo(3);
        assertThat(count("select count(*) from draft_source")).as("게시 후 원본 PDF 삭제").isZero();

        // 요청자 소유 v1만 있고 v2는 없다. 평가 3건, 평균 약 2.7
        String detail = json(mvc.perform(get("/api/my/resumes/" + resumeId).header("X-Anon-Token", me)).andReturn());
        assertThat((List<?>) JsonPath.read(detail, "$.versions")).hasSize(1);
        assertThat((String) JsonPath.read(detail, "$.versions[0].status")).isEqualTo("PUBLISHED");
        assertThat((Integer) JsonPath.read(detail, "$.versions[0].reviewCount")).isEqualTo(3);
        assertThat(((Number) JsonPath.read(detail, "$.versions[0].averageScore")).doubleValue()).isEqualTo(2.67);

        String reviews = json(mvc.perform(get("/api/my/resumes/" + resumeId + "/versions/1/reviews")
                .header("X-Anon-Token", me)).andReturn());
        List<Map<String, Object>> list = JsonPath.read(reviews, "$");
        assertThat(list).hasSize(3);
        for (Map<String, Object> rv : list) {
            assertThat(rv.get("sample")).isEqualTo(true);
            assertThat((List<?>) rv.get("pins")).hasSizeBetween(2, 4);
        }
        // 요청자 이력서 이름은 게시본 텍스트가 아니라 마스킹 대상이다: 가릴 단어로 저장됨
        assertThat(count("select count(*) from mask_term where term = '김하늘'")).isEqualTo(1);
    }

    @Test
    void req12_5_secondSeedBySameUserIsConflict() throws Exception {
        String me = token();
        assertThat(seed(me).getResponse().getStatus()).isEqualTo(201);
        MvcResult again = seed(me);
        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        assertThat((String) JsonPath.read(json(again), "$.code")).isEqualTo("DEMO_ALREADY_SEEDED");
        assertThat(count("select count(*) from resume where sample = true")).isEqualTo(3);
        assertThat(count("select count(*) from review where sample = true")).isEqualTo(3);
    }

    @Test
    void req12_5_sampleUsersAreCreatedOnlyOncePerApp() throws Exception {
        assertThat(seed(token()).getResponse().getStatus()).isEqualTo(201);
        assertThat(seed(token()).getResponse().getStatus()).isEqualTo(201);
        assertThat(count("select count(*) from anon_user where sample = true")).isEqualTo(3);
        // 샘플 사용자 이력서 2개 + 요청자 2명의 샘플 v1
        assertThat(count("select count(*) from resume where sample = true")).isEqualTo(4);
        assertThat(count("select count(*) from review where sample = true")).isEqualTo(6);
    }

    @Test
    void req12_3_sampleResumesAreInOtherUsersReviewQueue() throws Exception {
        seed(token());
        String stranger = token();
        MvcResult next = mvc.perform(get("/api/review-queue/next").header("X-Anon-Token", stranger)).andReturn();
        assertThat(next.getResponse().getStatus()).isEqualTo(200);
    }
}
