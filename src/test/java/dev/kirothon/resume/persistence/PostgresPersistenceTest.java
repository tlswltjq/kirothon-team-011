package dev.kirothon.resume.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kirothon.resume.pdf.PdfTypes.Box;
import dev.kirothon.resume.pdf.PdfTypes.MaskRegion;
import dev.kirothon.resume.pdf.PdfTypes.RegionType;
import dev.kirothon.resume.resume.DraftSource;
import dev.kirothon.resume.resume.DraftSourceRepository;
import dev.kirothon.resume.resume.MaskRegionRepository;
import dev.kirothon.resume.resume.MaskRegionRow;
import dev.kirothon.resume.resume.PageImage;
import dev.kirothon.resume.resume.PageImageRepository;
import dev.kirothon.resume.resume.Resume;
import dev.kirothon.resume.resume.ResumeRepository;
import dev.kirothon.resume.resume.ResumeVersion;
import dev.kirothon.resume.resume.ResumeVersionRepository;
import dev.kirothon.resume.review.Annotation;
import dev.kirothon.resume.review.AnnotationRepository;
import dev.kirothon.resume.review.Review;
import dev.kirothon.resume.review.ReviewRepository;
import dev.kirothon.resume.user.AnonUser;
import dev.kirothon.resume.user.AnonUserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 요구사항 1.1·1.2·1.4: 실제 PostgreSQL 16에서 prod 프로필 그대로(DB_URL·DB_USERNAME·DB_PASSWORD) 기동해
 * Flyway 마이그레이션 + Hibernate validate가 통과하고, 핵심 저장소 왕복이 동작하는지 확인한다.
 */
@Testcontainers
@SpringBootTest(properties = "management.server.port=0")
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class PostgresPersistenceTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void dbEnv(DynamicPropertyRegistry r) {
        r.add("DB_URL", POSTGRES::getJdbcUrl);
        r.add("DB_USERNAME", POSTGRES::getUsername);
        r.add("DB_PASSWORD", POSTGRES::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    MockMvc mvc;
    @Autowired
    AnonUserRepository users;
    @Autowired
    ResumeRepository resumes;
    @Autowired
    ResumeVersionRepository versions;
    @Autowired
    PageImageRepository images;
    @Autowired
    DraftSourceRepository drafts;
    @Autowired
    MaskRegionRepository regions;
    @Autowired
    ReviewRepository reviews;
    @Autowired
    AnnotationRepository annotations;

    @Test
    void req1_1_flywayCreatesSchemaOnPostgres() {
        assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL 16");
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success", Integer.class);
        assertThat(applied).isEqualTo(1);
        List<String> binaryTypes = jdbc.queryForList(
                "select data_type from information_schema.columns where table_name in ('page_image', 'draft_source')"
                        + " and column_name in ('png', 'pdf')", String.class);
        assertThat(binaryTypes).containsExactly("bytea", "bytea");
    }

    @Test
    void req1_4_createUserOverHttp() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"pg사용자\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nickname").value("pg사용자"))
                .andExpect(jsonPath("$.token").isString());
        assertThat(jdbc.queryForObject("select count(*) from anon_user where nickname = 'pg사용자'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void req1_4_repositoryRoundTripOnPostgres() {
        byte[] png = new byte[300_000];
        new Random(1).nextBytes(png);
        byte[] pdf = "%PDF-1.7 test".getBytes();

        AnonUser owner = users.save(new AnonUser("주인", "a".repeat(64)));
        AnonUser reviewer = users.save(new AnonUser("평가자", "b".repeat(64)));
        Resume resume = resumes.save(new Resume(owner.getId(), "백엔드 이력서", "백엔드"));
        UUID versionId = tx.execute(s -> {
            ResumeVersion v = new ResumeVersion(resume.getId(), 1);
            v.applyPreview("추출 텍스트 ".repeat(5_000), 1, "c".repeat(64), null, List.of("홍길동"), "홍길동");
            versions.save(v);
            drafts.save(new DraftSource(v.getId(), pdf, Instant.now().plus(1, ChronoUnit.DAYS)));
            images.save(new PageImage(v.getId(), 1, png, 1240, 1754));
            regions.save(new MaskRegionRow(v.getId(), new MaskRegion(1, RegionType.EMAIL, new Box(10, 20, 30, 40))));
            return v.getId();
        });

        assertThat(users.findByTokenHash("a".repeat(64))).map(AnonUser::getNickname).hasValue("주인");
        assertThat(drafts.findById(versionId)).get().extracting(DraftSource::getPdf).isEqualTo(pdf);
        assertThat(images.findByVersionIdAndPageNo(versionId, 1)).get().extracting(PageImage::getPng).isEqualTo(png);
        assertThat(regions.findByVersionIdOrderByIdAsc(versionId)).singleElement()
                .extracting(MaskRegionRow::getType).isEqualTo(RegionType.EMAIL);

        tx.executeWithoutResult(s -> {
            ResumeVersion v = versions.findByIdForUpdate(versionId).orElseThrow();
            v.publish();
            drafts.deleteByVersion(versionId);
        });
        ResumeVersion published = versions.findById(versionId).orElseThrow();
        assertThat(published.isPublished()).isTrue();
        assertThat(published.getExtractedText()).hasSize("추출 텍스트 ".length() * 5_000);
        assertThat(published.getUnmatchedTermList()).containsExactly("홍길동");
        assertThat(drafts.findById(versionId)).isEmpty();

        assertThat(reviews.candidateVersionIds(reviewer.getId())).contains(versionId);
        Review review = reviews.save(new Review(versionId, reviewer.getId(), 4, "구조가 깔끔하고 성과가 잘 보입니다."));
        annotations.save(new Annotation(review.getId(), 1, 1, 0.25, 0.75, "이 문장에 수치를 넣어 보세요."));

        assertThat(reviews.candidateVersionIds(reviewer.getId())).doesNotContain(versionId);
        assertThat(reviews.averageScore(versionId)).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(annotations.findByReviewIdInOrderByReviewIdAscSeqAsc(List.of(review.getId())))
                .singleElement().satisfies(a -> {
                    assertThat(a.getX()).isEqualTo(0.25);
                    assertThat(a.getComment()).isEqualTo("이 문장에 수치를 넣어 보세요.");
                });
    }
}
