package dev.kirothon.resume.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.kirothon.resume.pdf.PageRenderer;
import dev.kirothon.resume.pdf.PdfTypes.MaskRegion;
import dev.kirothon.resume.pdf.PdfTypes.RegionType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MyResumeApiTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ResumeRepository resumes;
    @Autowired
    ResumeVersionRepository versions;
    @Autowired
    DraftSourceRepository drafts;
    @Autowired
    MaskRegionRepository regions;
    @Autowired
    PageImageRepository images;
    @Autowired
    DraftSourceCleaner cleaner;
    @Autowired
    JdbcTemplate jdbc;

    record Up(String resumeId, int versionNo, String hash, UUID versionId) {
    }

    private String newToken() throws Exception {
        String body = mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"테스터\"}")).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "resume.pdf", "application/pdf", bytes);
    }

    private MvcResult upload(String token, byte[] pdf) throws Exception {
        return mvc.perform(multipart("/api/my/resumes").file(file(pdf)).param("title", "백엔드 이력서")
                .param("targetRole", "백엔드 개발자").header("X-Anon-Token", token)).andReturn();
    }

    private Up created(String token, byte[] pdf) throws Exception {
        MvcResult r = upload(token, pdf);
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        return up(r);
    }

    private Up up(MvcResult r) throws Exception {
        String body = r.getResponse().getContentAsString();
        String resumeId = JsonPath.read(body, "$.resumeId");
        int no = JsonPath.read(body, "$.versionNo");
        String hash = JsonPath.read(body, "$.version.preview.previewHash");
        UUID vid = versions.findByResumeIdAndVersionNo(UUID.fromString(resumeId), no).orElseThrow().getId();
        return new Up(resumeId, no, hash, vid);
    }

    private int draftRows(String resumeId) {
        return jdbc.queryForObject("select count(*) from draft_source d join resume_version v "
                + "on d.version_id = v.id where v.resume_id = ?", Integer.class, UUID.fromString(resumeId));
    }

    private String base(Up u) {
        return "/api/my/resumes/" + u.resumeId() + "/versions/" + u.versionNo();
    }

    private MockHttpServletRequestBuilder publishReq(String token, Up u, String hash, boolean confirmed) {
        return post(base(u) + "/publish").header("X-Anon-Token", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"previewHash\":" + (hash == null ? "null" : "\"" + hash + "\"")
                        + ",\"confirmed\":" + confirmed + "}");
    }

    private MockHttpServletRequestBuilder termsReq(String token, Up u, String json) {
        return put(base(u) + "/mask-terms").header("X-Anon-Token", token)
                .contentType(MediaType.APPLICATION_JSON).content(json);
    }

    // ---------- 업로드 ----------

    @Test
    void req2_1_uploadCreatesResumeAndDraftWithPreview() throws Exception {
        String token = newToken();
        MvcResult r = mvc.perform(multipart("/api/my/resumes").file(file(ResumePdfs.resume("")))
                        .param("title", "  백엔드 이력서 ").param("targetRole", "백엔드 개발자")
                        .header("X-Anon-Token", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.version.status").value("DRAFT"))
                .andExpect(jsonPath("$.version.maskTerms").isEmpty())
                .andExpect(jsonPath("$.version.extractedText").value(org.hamcrest.Matchers.containsString("Backend")))
                .andExpect(jsonPath("$.version.preview.pageCount").value(1))
                .andExpect(jsonPath("$.version.preview.counts.EMAIL").value(1))
                .andExpect(jsonPath("$.version.preview.counts.PHONE").value(1))
                .andExpect(jsonPath("$.version.preview.counts.IMAGE").value(1))
                .andExpect(jsonPath("$.version.preview.counts.TERM").value(0))
                .andExpect(jsonPath("$.version.preview.nameCandidate").value(ResumePdfs.NAME))
                .andExpect(jsonPath("$.version.preview.previewHash").isString())
                .andReturn();
        Up u = up(r);
        assertThat(u.hash()).hasSize(64);
        assertThat(drafts.existsById(u.versionId())).isTrue();
        assertThat(images.findByVersionIdAndPageNo(u.versionId(), 1)).isPresent();
        // 응답 어디에도 원본 PDF 바이트 표식이 없다.
        assertThat(r.getResponse().getContentAsString()).doesNotContain("%PDF-");
    }

    @Test
    void req2_3_nonPdfRejectedAndNothingStored() throws Exception {
        String token = newToken();
        long before = resumes.count();
        mvc.perform(multipart("/api/my/resumes").file(new MockMultipartFile("file", "a.pdf", "application/pdf",
                        "hello not a pdf".getBytes())).param("title", "t").param("targetRole", "r")
                        .header("X-Anon-Token", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_PDF"));
        assertThat(resumes.count()).isEqualTo(before);
    }

    @Test
    void req2_4_oversizedFileRejected() throws Exception {
        String token = newToken();
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        big[0] = '%';
        big[1] = 'P';
        big[2] = 'D';
        big[3] = 'F';
        big[4] = '-';
        mvc.perform(multipart("/api/my/resumes").file(file(big)).param("title", "t").param("targetRole", "r")
                        .header("X-Anon-Token", token))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void req2_1_missingFileAndBadFieldsRejected() throws Exception {
        String token = newToken();
        mvc.perform(multipart("/api/my/resumes").param("title", "t").param("targetRole", "r")
                        .header("X-Anon-Token", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        byte[] pdf = ResumePdfs.resume("");
        mvc.perform(multipart("/api/my/resumes").file(file(pdf)).param("title", " ").param("targetRole", "r")
                        .header("X-Anon-Token", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.title").exists());
        mvc.perform(multipart("/api/my/resumes").file(file(pdf)).param("title", "x".repeat(61))
                        .param("targetRole", "r").header("X-Anon-Token", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.title").exists());
    }

    @Test
    void req2_12_targetRoleWithContactPatternRejected() throws Exception {
        String token = newToken();
        mvc.perform(multipart("/api/my/resumes").file(file(ResumePdfs.resume(""))).param("title", "t")
                        .param("targetRole", "dev a@b.com").header("X-Anon-Token", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.targetRole").exists());
    }

    @Test
    void req11_unauthenticatedUploadRejected() throws Exception {
        mvc.perform(multipart("/api/my/resumes").file(file(ResumePdfs.resume(""))).param("title", "t")
                        .param("targetRole", "r"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 가릴 단어 ----------

    @Test
    void req3_3_termsMaskNameAndReportUnmatched() throws Exception {
        String token = newToken();
        Up u = created(token, ResumePdfs.resume(""));
        mvc.perform(termsReq(token, u, "{\"terms\":[\"gasang  kim\",\"Nobody Here\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskTerms[0]").value("gasang  kim"))
                .andExpect(jsonPath("$.preview.counts.TERM").value(1))
                .andExpect(jsonPath("$.preview.unmatchedTerms[0]").value("Nobody Here"))
                .andExpect(jsonPath("$.preview.nameCandidate").doesNotExist())
                .andExpect(jsonPath("$.preview.previewHash").value(org.hamcrest.Matchers.not(u.hash())));
        MvcResult after = mvc.perform(get(base(u)).header("X-Anon-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.publishedAt").doesNotExist())
                .andExpect(jsonPath("$.preview.unmatchedTerms[0]").value("Nobody Here")).andReturn();
        assertThat(after.getResponse().getContentAsString()).contains("\"counts\"");
    }

    @Test
    void req3_9_invalidTermsRejectedAndPreviewKept() throws Exception {
        String token = newToken();
        Up u = created(token, ResumePdfs.resume(""));
        String tooMany = "{\"terms\":[" + java.util.stream.IntStream.range(0, 51)
                .mapToObj(i -> "\"term" + i + "\"").collect(Collectors.joining(",")) + "]}";
        for (String body : List.of("{\"terms\":[\"a\"]}", "{\"terms\":[\"" + "x".repeat(31) + "\"]}",
                "{\"terms\":[\" \"]}", tooMany, "{}")) {
            mvc.perform(termsReq(token, u, body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        mvc.perform(get(base(u)).header("X-Anon-Token", token))
                .andExpect(jsonPath("$.preview.previewHash").value(u.hash()))
                .andExpect(jsonPath("$.maskTerms").isEmpty());
    }

    // ---------- 접근 경계 ----------

    @Test
    void req11_1_otherUsersGet404OnEveryOwnerPath() throws Exception {
        String owner = newToken();
        String other = newToken();
        Up u = created(owner, ResumePdfs.resume(""));
        String res = "/api/my/resumes/" + u.resumeId();
        mvc.perform(get(res).header("X-Anon-Token", other)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get(base(u)).header("X-Anon-Token", other)).andExpect(status().isNotFound());
        mvc.perform(get(base(u) + "/pages/1").header("X-Anon-Token", other)).andExpect(status().isNotFound());
        mvc.perform(termsReq(other, u, "{\"terms\":[\"Gasang Kim\"]}")).andExpect(status().isNotFound());
        mvc.perform(publishReq(other, u, u.hash(), true)).andExpect(status().isNotFound());
        mvc.perform(multipart(res + "/versions").file(file(ResumePdfs.resume("")))
                .header("X-Anon-Token", other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/my/resumes").header("X-Anon-Token", other)).andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/my/resumes/not-a-uuid").header("X-Anon-Token", owner))
                .andExpect(status().isNotFound());
        // 주인 본인은 정상
        mvc.perform(get(base(u)).header("X-Anon-Token", owner)).andExpect(status().isOk());
    }

    @Test
    void req3_6_ownerPageIsPngNoStoreAndRangeChecked() throws Exception {
        String token = newToken();
        Up u = created(token, ResumePdfs.resume(""));
        MvcResult r = mvc.perform(get(base(u) + "/pages/1").header("X-Anon-Token", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();
        BufferedImage png = ImageIO.read(new ByteArrayInputStream(r.getResponse().getContentAsByteArray()));
        assertThat(png.getWidth()).isEqualTo(Math.round(612 * 150 / 72f));
        mvc.perform(get(base(u) + "/pages/2").header("X-Anon-Token", token)).andExpect(status().isNotFound());
        mvc.perform(get(base(u) + "/pages/0").header("X-Anon-Token", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/my/resumes/" + u.resumeId() + "/versions/9").header("X-Anon-Token", token))
                .andExpect(status().isNotFound());
    }

    // ---------- 게시 ----------

    @Test
    void req4_2_publishWithoutOrWithStaleHashRejected() throws Exception {
        String token = newToken();
        Up u = created(token, ResumePdfs.resume(""));
        mvc.perform(publishReq(token, u, null, true)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREVIEW_OUTDATED"));
        mvc.perform(publishReq(token, u, "0".repeat(64), true)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREVIEW_OUTDATED"));
        // 가릴 단어를 바꾸면 이전 해시는 오래된 것이 된다.
        mvc.perform(termsReq(token, u, "{\"terms\":[\"Gasang Kim\"]}")).andExpect(status().isOk());
        mvc.perform(publishReq(token, u, u.hash(), true)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREVIEW_OUTDATED"));
        assertThat(versions.findById(u.versionId()).orElseThrow().isPublished()).isFalse();
        assertThat(drafts.existsById(u.versionId())).isTrue();
    }

    @Test
    void req4_3_publishWithoutConfirmationRejected() throws Exception {
        String token = newToken();
        Up u = created(token, ResumePdfs.resume(""));
        mvc.perform(publishReq(token, u, u.hash(), false)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post(base(u) + "/publish").header("X-Anon-Token", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"previewHash\":\"" + u.hash() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void req4_5_maskingIncompleteWhenStoredRegionsRemoved() throws Exception {
        String token = newToken();
        Up u = created(token, ResumePdfs.resume(""));
        jdbc.update("delete from mask_region where version_id = ?", u.versionId());
        mvc.perform(publishReq(token, u, u.hash(), true)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MASKING_INCOMPLETE"))
                .andExpect(jsonPath("$.failures[0].page").value(1))
                .andExpect(jsonPath("$.failures[*].type").value(org.hamcrest.Matchers.hasItems("EMAIL", "PHONE")));
        assertThat(versions.findById(u.versionId()).orElseThrow().isPublished()).isFalse();
        assertThat(drafts.existsById(u.versionId())).isTrue();
    }

    @Test
    void req4_5_maskingIncompleteWhenStoredPngNotBlack() throws Exception {
        String token = newToken();
        Up u = created(token, ResumePdfs.resume(""));
        // 저장된 PNG를 마스킹 전 렌더링(영역 없음)으로 바꿔 놓는다.
        byte[] unmasked = new PageRenderer().render(drafts.findById(u.versionId()).orElseThrow().getPdf(),
                List.of()).pngs().get(0);
        jdbc.update("update page_image set png = ? where version_id = ?", unmasked, u.versionId());
        mvc.perform(publishReq(token, u, u.hash(), true)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MASKING_INCOMPLETE"));
        assertThat(drafts.existsById(u.versionId())).isTrue();
    }

    @Test
    void req4_1_4_4_4_6_publishFreezesMaskedPngAndDeletesSource() throws Exception {
        String token = newToken();
        Up u = created(token, ResumePdfs.resume(""));
        mvc.perform(termsReq(token, u, "{\"terms\":[\"Gasang Kim\"]}")).andExpect(status().isOk());
        String hash = JsonPath.read(mvc.perform(get(base(u)).header("X-Anon-Token", token)).andReturn()
                .getResponse().getContentAsString(), "$.preview.previewHash");

        mvc.perform(publishReq(token, u, hash, true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isString());

        assertThat(draftRows(u.resumeId())).isZero();
        assertThat(drafts.existsById(u.versionId())).isFalse();

        // 게시된 PNG의 모든 마스킹 영역 픽셀이 검은색이고, 이메일·전화·이미지·이름 영역이 모두 있다.
        List<MaskRegion> stored = regions.findByVersionIdOrderByIdAsc(u.versionId()).stream()
                .map(MaskRegionRow::toRegion).toList();
        assertThat(stored).extracting(MaskRegion::type)
                .contains(RegionType.EMAIL, RegionType.PHONE, RegionType.IMAGE, RegionType.TERM);
        byte[] png = mvc.perform(get(base(u) + "/pages/1").header("X-Anon-Token", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(PageRenderer.verifyBlack(png, stored, 1)).isTrue();
        for (MaskRegion region : stored) {
            assertThat(PageRenderer.verifyBlack(png, List.of(region), 1)).as(region.type().name()).isTrue();
        }
        // 이미지 전체가 검은 것은 아니다(본문 배경은 흰색).
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(img.getRGB(img.getWidth() - 5, img.getHeight() - 5) & 0xFFFFFF).isEqualTo(0xFFFFFF);

        // 게시 후 재게시·단어 변경 거부, 이미지는 그대로.
        mvc.perform(publishReq(token, u, hash, true)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_PUBLISHED"));
        mvc.perform(termsReq(token, u, "{\"terms\":[\"Other Term\"]}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_PUBLISHED"));
        assertThat(images.findByVersionIdAndPageNo(u.versionId(), 1).orElseThrow().getPng()).isEqualTo(png);
        mvc.perform(get(base(u)).header("X-Anon-Token", token))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isString())
                .andExpect(jsonPath("$.preview.nameCandidate").doesNotExist());
    }

    @Test
    void req4_8_expiredDraftSourceBlocksTermsAndPublish() throws Exception {
        String token = newToken();
        Up u = created(token, ResumePdfs.resume(""));
        jdbc.update("update draft_source set expires_at = ? where version_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)), u.versionId());
        assertThat(cleaner.purgeExpired(Instant.now())).isGreaterThanOrEqualTo(1);
        assertThat(drafts.existsById(u.versionId())).isFalse();
        mvc.perform(termsReq(token, u, "{\"terms\":[\"Gasang Kim\"]}")).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("DRAFT_SOURCE_EXPIRED"));
        mvc.perform(publishReq(token, u, u.hash(), true)).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("DRAFT_SOURCE_EXPIRED"));
    }

    @Test
    void req4_7_expiredSourceRejectedAndDeletedWithoutCleanerRun() throws Exception {
        String token = newToken();
        Up u = created(token, ResumePdfs.resume(""));
        jdbc.update("update draft_source set expires_at = ? where version_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), u.versionId());
        mvc.perform(termsReq(token, u, "{\"terms\":[\"Gasang Kim\"]}")).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("DRAFT_SOURCE_EXPIRED"));
        assertThat(drafts.existsById(u.versionId())).isFalse();
        Up u2 = created(token, ResumePdfs.resume(""));
        jdbc.update("update draft_source set expires_at = ? where version_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), u2.versionId());
        mvc.perform(publishReq(token, u2, u2.hash(), true)).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("DRAFT_SOURCE_EXPIRED"));
        assertThat(drafts.existsById(u2.versionId())).isFalse();
    }

    @Test
    void req4_7_cleanerKeepsFreshDrafts() throws Exception {
        String token = newToken();
        Up u = created(token, ResumePdfs.resume(""));
        cleaner.purgeExpired(Instant.now());
        assertThat(drafts.existsById(u.versionId())).isTrue();
        assertThat(drafts.findById(u.versionId()).orElseThrow().getExpiresAt())
                .isAfter(Instant.now().plusSeconds(23 * 3600));
    }

    // ---------- 버전 ----------

    @Test
    void req5_newVersionCarriesTermsKeepsOldVersionAndReplacesDraft() throws Exception {
        String token = newToken();
        Up v1 = created(token, ResumePdfs.resume("first"));
        mvc.perform(termsReq(token, v1, "{\"terms\":[\"Gasang Kim\"]}")).andExpect(status().isOk());
        String h1 = JsonPath.read(mvc.perform(get(base(v1)).header("X-Anon-Token", token)).andReturn()
                .getResponse().getContentAsString(), "$.preview.previewHash");
        mvc.perform(publishReq(token, v1, h1, true)).andExpect(status().isOk());
        byte[] v1Png = images.findByVersionIdAndPageNo(v1.versionId(), 1).orElseThrow().getPng();
        String v1Text = versions.findById(v1.versionId()).orElseThrow().getExtractedText();

        String res = "/api/my/resumes/" + v1.resumeId();
        MvcResult r2 = mvc.perform(multipart(res + "/versions").file(file(ResumePdfs.resume("second")))
                        .header("X-Anon-Token", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.version.status").value("DRAFT"))
                .andExpect(jsonPath("$.version.maskTerms[0]").value("Gasang Kim"))
                .andExpect(jsonPath("$.version.preview.counts.TERM").value(1))
                .andExpect(jsonPath("$.version.extractedText").value(org.hamcrest.Matchers.containsString("second")))
                .andReturn();
        Up v2 = up(r2);
        assertThat(drafts.existsById(v2.versionId())).isTrue();

        // 초안이 있는 상태에서 다시 올리면 새 버전 없이 교체되고 단어는 유지된다.
        mvc.perform(termsReq(token, v2, "{\"terms\":[\"Gasang Kim\",\"Backend\"]}")).andExpect(status().isOk());
        MvcResult r3 = mvc.perform(multipart(res + "/versions").file(file(ResumePdfs.resume("third")))
                        .header("X-Anon-Token", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.version.maskTerms.length()").value(2))
                .andExpect(jsonPath("$.version.extractedText").value(org.hamcrest.Matchers.containsString("third")))
                .andReturn();
        assertThat(versions.findByResumeIdOrderByVersionNoAsc(UUID.fromString(v1.resumeId()))).hasSize(2);
        assertThat(draftRows(v1.resumeId())).isEqualTo(1);

        // 실패한 업로드는 기존 초안을 바꾸지 않는다(5.4).
        String hashBefore = up(r3).hash();
        mvc.perform(multipart(res + "/versions").file(file("nope".getBytes())).header("X-Anon-Token", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_PDF"));
        mvc.perform(get(res + "/versions/2").header("X-Anon-Token", token))
                .andExpect(jsonPath("$.preview.previewHash").value(hashBefore))
                .andExpect(jsonPath("$.extractedText").value(org.hamcrest.Matchers.containsString("third")));

        // v1은 그대로 보존(5.5).
        assertThat(images.findByVersionIdAndPageNo(v1.versionId(), 1).orElseThrow().getPng()).isEqualTo(v1Png);
        assertThat(versions.findById(v1.versionId()).orElseThrow().getExtractedText()).isEqualTo(v1Text);
        assertThat(versions.findById(v1.versionId()).orElseThrow().isPublished()).isTrue();
    }

    @Test
    void req5_secondVersionPublishesAndListMatchesContract() throws Exception {
        String token = newToken();
        Up v1 = created(token, ResumePdfs.resume("first"));
        mvc.perform(publishReq(token, v1, v1.hash(), true)).andExpect(status().isOk());
        Up v2 = up(mvc.perform(multipart("/api/my/resumes/" + v1.resumeId() + "/versions")
                .file(file(ResumePdfs.resume("second"))).header("X-Anon-Token", token)).andReturn());
        assertThat(v2.versionNo()).isEqualTo(2);
        mvc.perform(publishReq(token, v2, v2.hash(), true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(2));
        assertThat(draftRows(v1.resumeId())).isZero();

        mvc.perform(get("/api/my/resumes").header("X-Anon-Token", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].resumeId").value(v1.resumeId()))
                .andExpect(jsonPath("$[0].title").value("백엔드 이력서"))
                .andExpect(jsonPath("$[0].targetRole").value("백엔드 개발자"))
                .andExpect(jsonPath("$[0].latestVersionNo").value(2))
                .andExpect(jsonPath("$[0].reviewCount").value(0));
        mvc.perform(get("/api/my/resumes/" + v1.resumeId()).header("X-Anon-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions.length()").value(2))
                .andExpect(jsonPath("$.versions[0].versionNo").value(1))
                .andExpect(jsonPath("$.versions[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.versions[0].pageCount").value(1))
                .andExpect(jsonPath("$.versions[0].reviewCount").value(0))
                .andExpect(jsonPath("$.versions[0].averageScore").doesNotExist())
                .andExpect(jsonPath("$.versions[1].versionNo").value(2));
    }
}
