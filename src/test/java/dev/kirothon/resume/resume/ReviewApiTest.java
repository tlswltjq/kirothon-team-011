package dev.kirothon.resume.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import dev.kirothon.resume.review.RandomPicker;

/** 작업 7·8: 무작위 평가 대상, 평가 제출, 주인 평가 조회, 접근 경계. 전용 H2 메모리 DB를 쓴다. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:review-api-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReviewApiTest.FixedPickerConfig.class)
class ReviewApiTest {
    static final String SUMMARY = "구조가 깔끔하고 핵심 성과가 잘 드러나는 이력서입니다. 수치를 더 넣어 보세요.";
    static final String TITLE = "고유제목-ZXQ";
    static final String NICK_PREFIX = "닉네임";

    /** 테스트가 index를 바꿔 무작위 선택을 고정한다. */
    static class FixedPicker implements RandomPicker {
        volatile int index;

        @Override
        public int pick(int size) {
            return Math.min(index, size - 1);
        }
    }

    @TestConfiguration
    static class FixedPickerConfig {
        @Bean
        @Primary
        FixedPicker fixedPicker() {
            return new FixedPicker();
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    FixedPicker picker;

    record Pub(String resumeId, UUID versionId, int versionNo) {
    }

    int seq;

    @BeforeEach
    void clean() {
        for (String t : List.of("annotation", "review", "page_image", "mask_region", "mask_term", "draft_source",
                "resume_version", "resume", "anon_user")) {
            jdbc.update("delete from " + t);
        }
        picker.index = 0;
    }

    // ---------- 도우미 ----------

    private String token() throws Exception {
        String nick = NICK_PREFIX + (++seq);
        String body = mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"" + nick + "\"}")).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static MockMultipartFile file(byte[] b) {
        return new MockMultipartFile("file", "r.pdf", "application/pdf", b);
    }

    private UUID versionId(String resumeId, int no) {
        return jdbc.queryForObject("select id from resume_version where resume_id = ? and version_no = ?",
                UUID.class, UUID.fromString(resumeId), no);
    }

    private void publish(String token, String resumeId, int no, String hash) throws Exception {
        MvcResult r = mvc.perform(post("/api/my/resumes/" + resumeId + "/versions/" + no + "/publish")
                .header("X-Anon-Token", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"previewHash\":\"" + hash + "\",\"confirmed\":true}")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
    }

    /** 새 이력서 v1을 올리고 게시한다. */
    private Pub publishedResume(String token, String extra) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/my/resumes").file(file(ResumePdfs.resume(extra)))
                .param("title", TITLE).param("targetRole", "백엔드 개발자").header("X-Anon-Token", token))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        String body = r.getResponse().getContentAsString();
        String rid = JsonPath.read(body, "$.resumeId");
        publish(token, rid, 1, JsonPath.read(body, "$.version.preview.previewHash"));
        return new Pub(rid, versionId(rid, 1), 1);
    }

    /** 이미 있는 이력서에 새 버전을 올려 게시한다. */
    private Pub publishedVersion(String token, String resumeId, String extra) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/my/resumes/" + resumeId + "/versions")
                .file(file(ResumePdfs.resume(extra))).header("X-Anon-Token", token)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        String body = r.getResponse().getContentAsString();
        int no = JsonPath.read(body, "$.versionNo");
        publish(token, resumeId, no, JsonPath.read(body, "$.version.preview.previewHash"));
        return new Pub(resumeId, versionId(resumeId, no), no);
    }

    private Pub draftResume(String token) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/my/resumes").file(file(ResumePdfs.resume("draft")))
                .param("title", TITLE).param("targetRole", "백엔드 개발자").header("X-Anon-Token", token))
                .andReturn();
        String rid = JsonPath.read(r.getResponse().getContentAsString(), "$.resumeId");
        return new Pub(rid, versionId(rid, 1), 1);
    }

    private MvcResult submit(String token, UUID versionId, String json) throws Exception {
        return mvc.perform(post("/api/versions/" + versionId + "/reviews").header("X-Anon-Token", token)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private static String body(Object score, String summary, String pinsJson) {
        return "{\"totalScore\":" + score + ",\"summary\":\"" + summary + "\",\"pins\":" + pinsJson + "}";
    }

    private static String pin(Object page, Object x, Object y, String comment) {
        return "{\"page\":" + page + ",\"x\":" + x + ",\"y\":" + y + ",\"comment\":\"" + comment + "\"}";
    }

    private MvcResult next(String token, UUID skip) throws Exception {
        return mvc.perform(get("/api/review-queue/next" + (skip == null ? "" : "?skip=" + skip))
                .header("X-Anon-Token", token)).andReturn();
    }

    private String json(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString();
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private void review(String token, Pub p, int score) throws Exception {
        assertThat(submit(token, p.versionId(), body(score, SUMMARY, "[]")).getResponse().getStatus())
                .isEqualTo(201);
    }

    // ---------- 6: 무작위 평가 대상 ----------

    @Test
    void req6_1_6_3_nextReturnsOnlyFourFieldsAndExcludesOwnAndDraft() throws Exception {
        String a = token();
        String b = token();
        String c = token();
        Pub pa = publishedResume(a, "a");
        Pub pb = publishedResume(b, "b");
        draftResume(c);

        // a의 후보는 b의 이력서뿐(내 것·초안 제외)
        MvcResult r = next(a, null);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        String body = json(r);
        assertThat((Object) JsonPath.read(body, "$.versionId")).isEqualTo(pb.versionId().toString());
        assertThat((Object) JsonPath.read(body, "$.versionNo")).isEqualTo(1);
        assertThat((Object) JsonPath.read(body, "$.targetRole")).isEqualTo("백엔드 개발자");
        assertThat((Object) JsonPath.read(body, "$.pageCount")).isEqualTo(1);
        assertThat(JsonPath.<java.util.Map<String, Object>>read(body, "$").keySet())
                .containsExactlyInAnyOrder("versionId", "versionNo", "targetRole", "pageCount");

        // c는 초안만 있으므로 후보 둘, 고정 인덱스로 선택 확인
        picker.index = 0;
        assertThat((Object) JsonPath.read(json(next(c, null)), "$.versionId")).isEqualTo(pa.versionId().toString());
        picker.index = 1;
        assertThat((Object) JsonPath.read(json(next(c, null)), "$.versionId")).isEqualTo(pb.versionId().toString());
    }

    @Test
    void req6_3_alreadyReviewedExcludedAndNoCandidateIs204() throws Exception {
        String a = token();
        String b = token();
        Pub pb = publishedResume(b, "b");
        assertThat(next(a, null).getResponse().getStatus()).isEqualTo(200);
        review(a, pb, 4);
        MvcResult r = next(a, null);
        assertThat(r.getResponse().getStatus()).isEqualTo(204);
        assertThat(json(r)).isEmpty();
        // 내 이력서뿐인 사용자도 204
        assertThat(next(b, null).getResponse().getStatus()).isEqualTo(204);
        // 이력서가 없는 사용자는 후보가 하나 있고 평가 횟수와 무관하게 받는다(6.4)
        assertThat(next(token(), null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void req6_5_skipExcludedWhenTwoOrMoreCandidates_keptWhenOnlyOne() throws Exception {
        String a = token();
        String b = token();
        String c = token();
        Pub pa = publishedResume(a, "a");
        Pub pb = publishedResume(b, "b");
        picker.index = 0;
        // c: 후보 2개, 인덱스 0은 pa. pa를 skip하면 pb
        assertThat((Object) JsonPath.read(json(next(c, pa.versionId())), "$.versionId"))
                .isEqualTo(pb.versionId().toString());
        // 후보가 1개면 skip해도 그대로
        assertThat((Object) JsonPath.read(json(next(a, pa.versionId())), "$.versionId"))
                .isEqualTo(pb.versionId().toString());
        assertThat((Object) JsonPath.read(json(next(b, pa.versionId())), "$.versionId"))
                .isNotNull();
    }

    @Test
    void req6_3_previousPublishedVersionStaysCandidate() throws Exception {
        String a = token();
        String b = token();
        Pub v1 = publishedResume(a, "one");
        Pub v2 = publishedVersion(a, v1.resumeId(), "two");
        assertThat(v2.versionNo()).isEqualTo(2);
        picker.index = 0;
        assertThat((Object) JsonPath.read(json(next(b, null)), "$.versionId")).isEqualTo(v1.versionId().toString());
        picker.index = 1;
        assertThat((Object) JsonPath.read(json(next(b, null)), "$.versionId")).isEqualTo(v2.versionId().toString());
    }

    // ---------- 11.5: 평가자용 페이지 이미지 ----------

    @Test
    void req11_5_pageImageOnlyForPublished() throws Exception {
        String a = token();
        String b = token();
        Pub pub = publishedResume(a, "a");
        Pub draft = draftResume(a);
        MvcResult ok = mvc.perform(get("/api/versions/" + pub.versionId() + "/pages/1").header("X-Anon-Token", b))
                .andReturn();
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(ok.getResponse().getContentType()).isEqualTo("image/png");
        byte[] png = ok.getResponse().getContentAsByteArray();
        assertThat(png[1]).isEqualTo((byte) 'P');
        assertThat(new String(png, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("%PDF-");
        assertThat(mvc.perform(get("/api/versions/" + pub.versionId() + "/pages/2").header("X-Anon-Token", b))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(get("/api/versions/" + pub.versionId() + "/pages/0").header("X-Anon-Token", b))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        MvcResult d = mvc.perform(get("/api/versions/" + draft.versionId() + "/pages/1")
                .header("X-Anon-Token", b)).andReturn();
        assertThat(d.getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(get("/api/versions/" + UUID.randomUUID() + "/pages/1").header("X-Anon-Token", b))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    // ---------- 8: 제출 ----------

    @Test
    void req8_1_submitCreatesReviewAndPinsAndReturns201WithNoBody() throws Exception {
        String a = token();
        String b = token();
        Pub p = publishedResume(a, "a");
        MvcResult r = submit(b, p.versionId(), body(4, "  " + SUMMARY + "  ",
                "[" + pin(1, 0.25, 0.75, "  첫 핀  ") + "," + pin(1, 0.5, 0.125, "둘째 핀") + "]"));
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        assertThat(json(r)).isEmpty();
        assertThat(count("review")).isEqualTo(1);
        assertThat(count("annotation")).isEqualTo(2);

        String list = json(mvc.perform(get("/api/my/resumes/" + p.resumeId() + "/versions/1/reviews")
                .header("X-Anon-Token", a)).andReturn());
        assertThat((Object) JsonPath.read(list, "$[0].totalScore")).isEqualTo(4);
        assertThat((Object) JsonPath.read(list, "$[0].summary")).isEqualTo(SUMMARY);
        assertThat((Object) JsonPath.read(list, "$[0].pins[0].seq")).isEqualTo(1);
        assertThat((Object) JsonPath.read(list, "$[0].pins[0].x")).isEqualTo(0.25);
        assertThat((Object) JsonPath.read(list, "$[0].pins[0].y")).isEqualTo(0.75);
        assertThat((Object) JsonPath.read(list, "$[0].pins[0].comment")).isEqualTo("첫 핀");
        assertThat((Object) JsonPath.read(list, "$[0].pins[1].y")).isEqualTo(0.125);
        assertThat((Object) JsonPath.read(list, "$[0].pins[1].page")).isEqualTo(1);
    }

    @Test
    void req7_2_8_1_boundaryValuesAccepted() throws Exception {
        String a = token();
        Pub p = publishedResume(a, "a");
        String c500 = "가".repeat(500);
        List<String> accepted = new ArrayList<>(List.of(
                body(1, SUMMARY, "[]"),
                body(5, SUMMARY, "[" + pin(1, 0, 0, "가") + "]"),
                body(3, SUMMARY, "[" + pin(1, 1, 1, c500) + "]"),
                body(3, "가".repeat(20), "[]"),
                body(3, "가".repeat(1000), "[]")));
        StringBuilder fifty = new StringBuilder("[");
        for (int i = 0; i < 50; i++) {
            fifty.append(i == 0 ? "" : ",").append(pin(1, 0.5, 0.5, "핀" + i));
        }
        accepted.add(body(3, SUMMARY, fifty.append("]").toString()));
        for (String json : accepted) {
            MvcResult r = submit(token(), p.versionId(), json);
            assertThat(r.getResponse().getStatus()).as(json.substring(0, Math.min(90, json.length()))).isEqualTo(201);
        }
        assertThat(count("review")).isEqualTo(accepted.size());
        assertThat(jdbc.queryForObject("select max(seq) from annotation", Integer.class)).isEqualTo(50);
    }

    @Test
    void req7_5_7_6_8_2_8_3_8_4_invalidRejectedWithZeroRows() throws Exception {
        String a = token();
        Pub p = publishedResume(a, "a");
        String ok = pin(1, 0.5, 0.5, "정상 핀");
        StringBuilder fiftyOne = new StringBuilder("[");
        for (int i = 0; i < 51; i++) {
            fiftyOne.append(i == 0 ? "" : ",").append(ok);
        }
        fiftyOne.append("]");
        // 입력 -> 기대하는 필드 키(null이면 필드 키는 확인하지 않음)
        List<String[]> cases = List.of(
                new String[] {body(3, SUMMARY, "[" + ok + "," + pin(1, -0.01, 0.5, "x") + "]"), "pins[1].x"},
                new String[] {body(3, SUMMARY, "[" + pin(1, 1.01, 0.5, "x") + "]"), "pins[0].x"},
                new String[] {body(3, SUMMARY, "[" + pin(1, 0.5, -0.01, "x") + "]"), "pins[0].y"},
                new String[] {body(3, SUMMARY, "[" + pin(1, 0.5, 1.01, "x") + "]"), "pins[0].y"},
                new String[] {body(3, SUMMARY, "[{\"page\":1,\"x\":0.5,\"comment\":\"x\"}]"), "pins[0].y"},
                new String[] {body(3, SUMMARY, "[" + pin(1, "\"NaN\"", 0.5, "x") + "]"), null},
                new String[] {body(3, SUMMARY, "[" + pin(1, "\"abc\"", 0.5, "x") + "]"), null},
                new String[] {body(3, SUMMARY, "[" + pin(0, 0.5, 0.5, "x") + "]"), "pins[0].page"},
                new String[] {body(3, SUMMARY, "[" + pin(2, 0.5, 0.5, "x") + "]"), "pins[0].page"},
                new String[] {body(3, SUMMARY, "[" + pin(1.5, 0.5, 0.5, "x") + "]"), "pins[0].page"},
                new String[] {body(3, SUMMARY, "[" + pin(1, 0.5, 0.5, "   ") + "]"), "pins[0].comment"},
                new String[] {body(3, SUMMARY, "[" + pin(1, 0.5, 0.5, "가".repeat(501)) + "]"), "pins[0].comment"},
                new String[] {body(3, SUMMARY, fiftyOne.toString()), "pins"},
                new String[] {body(0, SUMMARY, "[]"), "totalScore"},
                new String[] {body(6, SUMMARY, "[]"), "totalScore"},
                new String[] {body(3.5, SUMMARY, "[]"), "totalScore"},
                new String[] {"{\"summary\":\"" + SUMMARY + "\",\"pins\":[]}", "totalScore"},
                new String[] {body(3, "가".repeat(19), "[]"), "summary"},
                new String[] {body(3, "  " + "가".repeat(19) + "  ", "[]"), "summary"},
                new String[] {body(3, "가".repeat(1001), "[]"), "summary"},
                new String[] {"{\"totalScore\":3,\"pins\":[]}", "summary"},
                new String[] {"not json", null});
        for (String[] c : cases) {
            MvcResult r = submit(token(), p.versionId(), c[0]);
            String label = c[0].length() > 120 ? c[0].substring(0, 120) : c[0];
            assertThat(r.getResponse().getStatus()).as(label).isEqualTo(400);
            assertThat((Object) JsonPath.read(json(r), "$.code")).as(label).isEqualTo("VALIDATION_FAILED");
            if (c[1] != null) {
                assertThat(JsonPath.<java.util.Map<String, Object>>read(json(r), "$.fields").keySet())
                        .as(label).contains(c[1]);
            }
        }
        assertThat(count("review")).isZero();
        assertThat(count("annotation")).isZero();
    }

    @Test
    void req8_5_duplicateReviewIs409AndKeepsFirst() throws Exception {
        String a = token();
        String b = token();
        Pub p = publishedResume(a, "a");
        assertThat(submit(b, p.versionId(), body(5, SUMMARY, "[" + pin(1, 0.1, 0.1, "처음 핀") + "]"))
                .getResponse().getStatus()).isEqualTo(201);
        MvcResult dup = submit(b, p.versionId(), body(1, SUMMARY, "[" + pin(1, 0.9, 0.9, "두번째") + "]"));
        assertThat(dup.getResponse().getStatus()).isEqualTo(409);
        assertThat((Object) JsonPath.read(json(dup), "$.code")).isEqualTo("DUPLICATE_REVIEW");
        assertThat(count("review")).isEqualTo(1);
        assertThat(count("annotation")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select total_score from review", Integer.class)).isEqualTo(5);
    }

    @Test
    void req8_5_concurrentDuplicateOnly1of2Succeeds() throws Exception {
        String a = token();
        String b = token();
        Pub p = publishedResume(a, "a");
        String payload = body(4, SUMMARY, "[" + pin(1, 0.2, 0.2, "동시 핀") + "]");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> fs = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                fs.add(pool.submit(() -> {
                    go.await();
                    return submit(b, p.versionId(), payload).getResponse().getStatus();
                }));
            }
            go.countDown();
            List<Integer> statuses = List.of(fs.get(0).get(), fs.get(1).get());
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("review")).isEqualTo(1);
        assertThat(count("annotation")).isEqualTo(1);
    }

    @Test
    void req8_7_selfReviewIs403() throws Exception {
        String a = token();
        Pub p = publishedResume(a, "a");
        MvcResult r = submit(a, p.versionId(), body(3, SUMMARY, "[]"));
        assertThat(r.getResponse().getStatus()).isEqualTo(403);
        assertThat((Object) JsonPath.read(json(r), "$.code")).isEqualTo("SELF_REVIEW");
        assertThat(count("review")).isZero();
    }

    @Test
    void req8_8_draftOrMissingVersionIs404() throws Exception {
        String a = token();
        String b = token();
        Pub draft = draftResume(a);
        MvcResult r = submit(b, draft.versionId(), body(3, SUMMARY, "[]"));
        assertThat(r.getResponse().getStatus()).isEqualTo(404);
        assertThat((Object) JsonPath.read(json(r), "$.code")).isEqualTo("NOT_FOUND");
        assertThat(submit(b, UUID.randomUUID(), body(3, SUMMARY, "[]")).getResponse().getStatus()).isEqualTo(404);
        assertThat(count("review")).isZero();
    }

    @Test
    void req8_8_missingOrDraftTargetIs404EvenWithInvalidBody() throws Exception {
        String a = token();
        String b = token();
        Pub draft = draftResume(a);
        String bad = body(9, "짧음", "[" + pin(99, 2, 2, "") + "]");
        assertThat(submit(b, draft.versionId(), bad).getResponse().getStatus()).isEqualTo(404);
        assertThat(submit(b, UUID.randomUUID(), bad).getResponse().getStatus()).isEqualTo(404);
        Pub pub = publishedResume(a, "self");
        assertThat(submit(a, pub.versionId(), bad).getResponse().getStatus()).isEqualTo(403);
        assertThat(submit(b, pub.versionId(), bad).getResponse().getStatus()).isEqualTo(400);
        assertThat(submit(b, pub.versionId(), "null").getResponse().getStatus()).isEqualTo(400);
        assertThat(count("review")).isZero();
    }

    @Test
    void req4_concurrentVersionUploadAfterPublishedNeverReturns500() throws Exception {
        String a = token();
        Pub p = publishedResume(a, "base");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> fs = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String extra = "c" + i;
                fs.add(pool.submit(() -> {
                    go.await();
                    return mvc.perform(multipart("/api/my/resumes/" + p.resumeId() + "/versions")
                            .file(file(ResumePdfs.resume(extra))).header("X-Anon-Token", a))
                            .andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
            assertThat(List.of(fs.get(0).get(), fs.get(1).get())).containsOnly(201);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from resume_version where resume_id = ?",
                Integer.class, UUID.fromString(p.resumeId()))).isEqualTo(2);
    }

    @Test
    void req8_9_reviewOfPreviousVersionSavedToThatVersion() throws Exception {
        String a = token();
        String b = token();
        Pub v1 = publishedResume(a, "one");
        publishedVersion(a, v1.resumeId(), "two");
        assertThat(submit(b, v1.versionId(), body(2, SUMMARY, "[" + pin(1, 0.3, 0.3, "v1 핀") + "]"))
                .getResponse().getStatus()).isEqualTo(201);
        String l1 = json(mvc.perform(get("/api/my/resumes/" + v1.resumeId() + "/versions/1/reviews")
                .header("X-Anon-Token", a)).andReturn());
        String l2 = json(mvc.perform(get("/api/my/resumes/" + v1.resumeId() + "/versions/2/reviews")
                .header("X-Anon-Token", a)).andReturn());
        assertThat(l1).contains("v1 핀");
        // 9.8: 핀은 남겨진 버전에만 있고 다른 버전으로 이월되지 않는다
        assertThat(l2).isEqualTo("[]");
    }

    @Test
    void req7_2_dbCheckRejectsOutOfRangeCoordinates() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(
                "insert into annotation (review_id, seq, page_no, x, y, comment) values (?, 1, 1, 1.5, 0.5, 'c')",
                UUID.randomUUID()))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(
                "insert into review (id, version_id, reviewer_id, total_score, summary, sample, created_at) "
                        + "values (?, ?, ?, 6, 's', false, current_timestamp)",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))).isInstanceOf(
                DataIntegrityViolationException.class);
    }

    // ---------- 11.2: 평가자 응답 비노출 ----------

    @Test
    void req11_2_reviewerResponsesLeakNothing() throws Exception {
        String a = token();
        String b = token();
        String c = token();
        Pub p = publishedResume(a, "a");
        assertThat(submit(b, p.versionId(), body(3, SUMMARY, "[" + pin(1, 0.5, 0.5, "비공개-타인핀-QWE") + "]"))
                .getResponse().getStatus()).isEqualTo(201);

        List<String> bodies = new ArrayList<>();
        MvcResult nx = next(c, null);
        bodies.add(json(nx));
        MvcResult pg = mvc.perform(get("/api/versions/" + p.versionId() + "/pages/1").header("X-Anon-Token", c))
                .andReturn();
        assertThat(pg.getResponse().getContentType()).isNotEqualTo("application/pdf");
        MvcResult sub = submit(c, p.versionId(), body(4, SUMMARY, "[]"));
        bodies.add(json(sub));
        assertThat(sub.getResponse().getContentType()).isNull();
        List<String> nicknames = jdbc.queryForList("select nickname from anon_user", String.class);
        List<String> forbidden = new ArrayList<>(List.of(TITLE, p.resumeId(), "%PDF-", ResumePdfs.EMAIL,
                ResumePdfs.PHONE, ResumePdfs.NAME, "비공개-타인핀-QWE", "Backend engineer", "resumeId", "title"));
        forbidden.addAll(nicknames);
        forbidden.addAll(jdbc.queryForList("select cast(id as varchar) from anon_user", String.class));
        forbidden.addAll(jdbc.queryForList("select token_hash from anon_user", String.class));
        forbidden.add(a);
        forbidden.add(b);
        for (String body : bodies) {
            for (String f : forbidden) {
                assertThat(body).as(f).doesNotContain(f);
            }
        }
        assertThat(new String(pg.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("%PDF-");
    }

    // ---------- 9, 11.3: 주인 조회 ----------

    @Test
    void req9_5_11_3_ownerSeesLabelsLatestFirstWithoutReviewerIdentity() throws Exception {
        String a = token();
        String b = token();
        String c = token();
        Pub p = publishedResume(a, "a");
        assertThat(submit(b, p.versionId(), body(2, SUMMARY + " 첫째", "[" + pin(1, 0.1, 0.2, "b-핀1") + ","
                + pin(1, 0.3, 0.4, "b-핀2") + "]")).getResponse().getStatus()).isEqualTo(201);
        Thread.sleep(5);
        assertThat(submit(c, p.versionId(), body(5, SUMMARY + " 둘째", "[]")).getResponse().getStatus())
                .isEqualTo(201);

        MvcResult r = mvc.perform(get("/api/my/resumes/" + p.resumeId() + "/versions/1/reviews")
                .header("X-Anon-Token", a)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        String body = json(r);
        assertThat((Object) JsonPath.read(body, "$[0].reviewerLabel")).isEqualTo(2);
        assertThat((Object) JsonPath.read(body, "$[0].totalScore")).isEqualTo(5);
        assertThat(JsonPath.<List<Object>>read(body, "$[0].pins")).isEmpty();
        assertThat((Object) JsonPath.read(body, "$[1].reviewerLabel")).isEqualTo(1);
        assertThat((Object) JsonPath.read(body, "$[1].pins[1].seq")).isEqualTo(2);
        assertThat((Object) JsonPath.read(body, "$[1].pins[1].comment")).isEqualTo("b-핀2");
        assertThat((Object) JsonPath.read(body, "$[1].sample")).isEqualTo(false);
        assertThat((Object) JsonPath.read(body, "$[1].createdAt")).isNotNull();
        assertThat(JsonPath.<java.util.Map<String, Object>>read(body, "$[0]").keySet())
                .containsExactlyInAnyOrder("reviewerLabel", "totalScore", "summary", "createdAt", "sample", "pins");
        List<String> forbidden = new ArrayList<>(jdbc.queryForList("select nickname from anon_user", String.class));
        forbidden.addAll(jdbc.queryForList("select cast(id as varchar) from anon_user", String.class));
        forbidden.addAll(jdbc.queryForList("select token_hash from anon_user", String.class));
        forbidden.addAll(List.of(a, b, c, "reviewerId", "reviewer_id"));
        for (String f : forbidden) {
            assertThat(body).as(f).doesNotContain(f);
        }
    }

    @Test
    void req9_2_averageRoundedHalfUpAndCounts() throws Exception {
        String a = token();
        Pub p = publishedResume(a, "a");
        int[] scores = {5, 5, 5, 5, 4, 3, 3, 3}; // 33/8 = 4.125 -> 4.13
        for (int s : scores) {
            review(token(), p, s);
        }
        String detail = json(mvc.perform(get("/api/my/resumes/" + p.resumeId()).header("X-Anon-Token", a))
                .andReturn());
        assertThat((Object) JsonPath.read(detail, "$.versions[0].reviewCount")).isEqualTo(8);
        assertThat((Object) JsonPath.read(detail, "$.versions[0].averageScore")).isEqualTo(4.13);
        assertThat((Object) JsonPath.read(detail, "$.versions[0].status")).isEqualTo("PUBLISHED");
        String list = json(mvc.perform(get("/api/my/resumes").header("X-Anon-Token", a)).andReturn());
        assertThat((Object) JsonPath.read(list, "$[0].reviewCount")).isEqualTo(8);
        assertThat((Object) JsonPath.read(list, "$[0].latestVersionNo")).isEqualTo(1);
    }

    @Test
    void req9_2_thirdsRoundTwoDecimals() throws Exception {
        String a = token();
        Pub p = publishedResume(a, "a");
        review(token(), p, 4);
        review(token(), p, 4);
        review(token(), p, 5); // 13/3 = 4.333..
        String detail = json(mvc.perform(get("/api/my/resumes/" + p.resumeId()).header("X-Anon-Token", a))
                .andReturn());
        assertThat((Object) JsonPath.read(detail, "$.versions[0].averageScore")).isEqualTo(4.33);
    }

    @Test
    void req9_2_zeroReviewsAverageIsNullAndCountPerVersion() throws Exception {
        String a = token();
        Pub v1 = publishedResume(a, "one");
        publishedVersion(a, v1.resumeId(), "two");
        review(token(), v1, 3);
        String detail = json(mvc.perform(get("/api/my/resumes/" + v1.resumeId()).header("X-Anon-Token", a))
                .andReturn());
        assertThat((Object) JsonPath.read(detail, "$.versions[0].reviewCount")).isEqualTo(1);
        assertThat((Object) JsonPath.read(detail, "$.versions[0].averageScore")).isEqualTo(3.0);
        assertThat((Object) JsonPath.read(detail, "$.versions[1].reviewCount")).isEqualTo(0);
        assertThat(detail).contains("\"averageScore\":null");
        assertThat(JsonPath.<Object>read(detail, "$.versions[1].averageScore")).isNull();
        String list = json(mvc.perform(get("/api/my/resumes").header("X-Anon-Token", a)).andReturn());
        assertThat((Object) JsonPath.read(list, "$[0].reviewCount")).isEqualTo(1);
    }

    // ---------- 11.1: 다른 토큰 ----------

    @Test
    void req11_1_otherTokenGets404OnAllMyPaths() throws Exception {
        String a = token();
        String other = token();
        Pub p = publishedResume(a, "a");
        review(other, p, 3);
        String base = "/api/my/resumes/" + p.resumeId();
        for (String path : List.of(base, base + "/versions/1", base + "/versions/1/pages/1",
                base + "/versions/1/reviews")) {
            MvcResult r = mvc.perform(get(path).header("X-Anon-Token", other)).andReturn();
            assertThat(r.getResponse().getStatus()).as(path).isEqualTo(404);
            assertThat(json(r)).as(path).doesNotContain(TITLE).doesNotContain(SUMMARY);
        }
        // 목록은 내 것만
        assertThat(json(mvc.perform(get("/api/my/resumes").header("X-Anon-Token", other)).andReturn()))
                .isEqualTo("[]");
        // 토큰 없음은 401
        assertThat(mvc.perform(get(base + "/versions/1/reviews")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
        assertThat(mvc.perform(get("/api/review-queue/next")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
    }
}
