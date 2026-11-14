package dev.kirothon.resume.resume;

import dev.kirothon.resume.pdf.MaskRegionFinder;
import dev.kirothon.resume.pdf.PageRenderer;
import dev.kirothon.resume.pdf.PdfTypes.Box;
import dev.kirothon.resume.pdf.PdfTypes.LineText;
import dev.kirothon.resume.pdf.PdfTypes.MaskRegion;
import dev.kirothon.resume.pdf.PdfTypes.RegionType;
import dev.kirothon.resume.resume.PdfProcessor.Processed;
import dev.kirothon.resume.resume.ResumeViews.OwnerVersionView;
import dev.kirothon.resume.resume.ResumeViews.Preview;
import dev.kirothon.resume.resume.ResumeViews.PublishResponse;
import dev.kirothon.resume.resume.ResumeViews.ResumeDetail;
import dev.kirothon.resume.resume.ResumeViews.ResumeSummary;
import dev.kirothon.resume.resume.ResumeViews.UploadResponse;
import dev.kirothon.resume.resume.ResumeViews.VersionSummary;
import dev.kirothon.resume.review.ReviewRepository;
import dev.kirothon.resume.web.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 업로드·미리보기·가릴 단어·게시. 무거운 PDF 처리는 DB 트랜잭션 밖에서 하고,
 * 처리가 모두 성공한 뒤에만 한 트랜잭션으로 저장한다(거부 시 아무것도 저장 안 함).
 */
@Service
public class ResumeService {
    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    static final Duration DRAFT_TTL = Duration.ofHours(24);
    static final String WARNING_TEXT =
            "일부 글자를 읽지 못해 자동 마스킹이 빠질 수 있습니다. 미리보기를 꼼꼼히 확인하세요";
    private static final float EPS = 0.01f;

    private final ResumeRepository resumes;
    private final ResumeVersionRepository versions;
    private final DraftSourceRepository drafts;
    private final MaskTermRepository terms;
    private final MaskRegionRepository regions;
    private final PageImageRepository images;
    private final ReviewRepository reviews;
    private final PdfProcessor processor;
    private final MaskRegionFinder finder;
    private final TransactionTemplate tx;

    public ResumeService(ResumeRepository resumes, ResumeVersionRepository versions, DraftSourceRepository drafts,
                         MaskTermRepository terms, MaskRegionRepository regions, PageImageRepository images,
                         ReviewRepository reviews, PdfProcessor processor, MaskRegionFinder finder, PlatformTransactionManager txManager) {
        this.resumes = resumes;
        this.versions = versions;
        this.drafts = drafts;
        this.terms = terms;
        this.regions = regions;
        this.images = images;
        this.reviews = reviews;
        this.processor = processor;
        this.finder = finder;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---------- 업로드 ----------

    public UploadResponse createResume(UUID ownerId, String title, String targetRole, byte[] pdf) {
        String t = title == null ? "" : title.strip();
        String role = targetRole == null ? "" : targetRole.strip();
        if (t.isEmpty() || t.length() > 60) {
            throw ApiException.validation("title", "제목은 1~60자여야 합니다.");
        }
        if (role.isEmpty() || role.length() > 40) {
            throw ApiException.validation("targetRole", "지원 직무는 1~40자여야 합니다.");
        }
        if (finder.containsContactPattern(role)) {
            throw ApiException.validation("targetRole", "지원 직무에는 이메일·전화번호·URL을 쓸 수 없습니다.");
        }
        checkFile(pdf);
        Processed p = processor.process(pdf, List.of());
        return tx.execute(status -> {
            Resume resume = resumes.save(new Resume(ownerId, t, role));
            ResumeVersion v = new ResumeVersion(resume.getId(), 1);
            storePreview(v, p, pdf);
            return new UploadResponse(resume.getId(), 1, view(v));
        });
    }

    public UploadResponse addVersion(UUID ownerId, UUID resumeId, byte[] pdf) {
        Resume resume = owned(ownerId, resumeId);
        checkFile(pdf);
        // 동시 업로드로 초안 상태가 바뀌면 새 상태 기준으로 다시 처리한다.
        for (int attempt = 0; attempt < 3; attempt++) {
            ResumeVersion draft = versions.findFirstByResumeIdAndStatus(resume.getId(), ResumeVersion.DRAFT)
                    .orElse(null);
            UUID draftId = draft == null ? null : draft.getId();
            List<String> carried;
            if (draft != null) {
                carried = termList(draft.getId());
            } else {
                ResumeVersion last = lastVersion(resume.getId());
                carried = last == null ? List.of() : termList(last.getId());
            }
            Processed p = processor.process(pdf, carried);
            UploadResponse out = tx.execute(status -> {
                // Resume 행 잠금으로 같은 이력서의 버전 생성·교체를 직렬화하고 초안을 다시 조회한다.
                resumes.findByIdForUpdate(resume.getId()).orElseThrow(ApiException::notFound);
                ResumeVersion cur = versions.findFirstByResumeIdAndStatus(resume.getId(), ResumeVersion.DRAFT)
                        .orElse(null);
                if (!Objects.equals(draftId, cur == null ? null : cur.getId())) {
                    return null;
                }
                ResumeVersion v;
                if (cur != null) {
                    UUID id = cur.getId();
                    v = versions.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
                    if (v.isPublished()) {
                        throw new ApiException(HttpStatus.CONFLICT, "ALREADY_PUBLISHED", "이미 게시된 버전입니다.");
                    }
                    regions.deleteByVersion(id);
                    images.deleteByVersion(id);
                    drafts.deleteByVersion(id);
                    v = versions.findById(id).orElseThrow(ApiException::notFound);
                } else {
                    int next = versions.findByResumeIdOrderByVersionNoAsc(resume.getId()).stream()
                            .mapToInt(ResumeVersion::getVersionNo).max().orElse(0) + 1;
                    v = new ResumeVersion(resume.getId(), next);
                }
                storePreview(v, p, pdf);
                if (cur == null) {
                    saveTerms(v.getId(), carried);
                }
                return new UploadResponse(resume.getId(), v.getVersionNo(), view(v));
            });
            if (out != null) {
                return out;
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, "CONCURRENT_UPLOAD", "동시에 다른 업로드가 진행 중입니다. 다시 시도하세요.");
    }

    // ---------- 조회 ----------

    public OwnerVersionView getVersion(UUID ownerId, UUID resumeId, int no) {
        return view(ownedVersion(ownerId, resumeId, no));
    }

    public byte[] getPagePng(UUID ownerId, UUID resumeId, int no, int page) {
        ResumeVersion v = ownedVersion(ownerId, resumeId, no);
        return images.findByVersionIdAndPageNo(v.getId(), page).map(PageImage::getPng)
                .orElseThrow(ApiException::notFound);
    }

    public List<ResumeSummary> list(UUID ownerId) {
        List<ResumeSummary> out = new ArrayList<>();
        for (Resume r : resumes.findByOwnerIdOrderByCreatedAtDesc(ownerId)) {
            ResumeVersion last = lastVersion(r.getId());
            out.add(new ResumeSummary(r.getId(), r.getTitle(), r.getTargetRole(),
                    last == null ? 0 : last.getVersionNo(), (int) reviews.countByResume(r.getId())));
        }
        return out;
    }

    public ResumeDetail detail(UUID ownerId, UUID resumeId) {
        Resume r = owned(ownerId, resumeId);
        List<VersionSummary> list = versions.findByResumeIdOrderByVersionNoAsc(r.getId()).stream()
                .map(v -> {
                    BigDecimal avg = reviews.averageScore(v.getId());
                    return new VersionSummary(v.getVersionNo(), v.getStatus(), v.getPublishedAt(),
                            v.getPageCount(), (int) reviews.countByVersion(v.getId()),
                            avg == null ? null : avg.doubleValue());
                })
                .toList();
        return new ResumeDetail(r.getId(), r.getTitle(), r.getTargetRole(), list);
    }

    // ---------- 가릴 단어 ----------

    public OwnerVersionView replaceTerms(UUID ownerId, UUID resumeId, int no, List<String> requested) {
        ResumeVersion v = ownedVersion(ownerId, resumeId, no);
        List<String> clean = validateTerms(requested);
        if (v.isPublished()) {
            throw alreadyPublished();
        }
        DraftSource src = liveDraft(v.getId());
        Processed p = processor.process(src.getPdf(), clean);
        UUID id = v.getId();
        String seenHash = v.getPreviewHash();
        return tx.execute(status -> {
            ResumeVersion cur = versions.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
            if (cur.isPublished()) {
                throw alreadyPublished();
            }
            DraftSource now = drafts.findById(id).orElseThrow(ResumeService::expired);
            if (isExpired(now)) {
                drafts.deleteByVersion(id);
                throw expired();
            }
            if (!Objects.equals(seenHash, cur.getPreviewHash()) || !Arrays.equals(src.getPdf(), now.getPdf())) {
                throw outdated();
            }
            terms.deleteByVersion(id);
            regions.deleteByVersion(id);
            images.deleteByVersion(id);
            saveTerms(id, clean);
            cur = versions.findById(id).orElseThrow(ApiException::notFound);
            storePreview(cur, p, null);
            return view(cur);
        });
    }

    // ---------- 게시 ----------

    public PublishResponse publish(UUID ownerId, UUID resumeId, int no, String previewHash, Boolean confirmed) {
        ResumeVersion v = ownedVersion(ownerId, resumeId, no);
        if (!Boolean.TRUE.equals(confirmed)) {
            throw ApiException.validation("confirmed", "모든 페이지를 확인했다는 확인값이 필요합니다.");
        }
        if (v.isPublished()) {
            throw alreadyPublished();
        }
        DraftSource src = liveDraft(v.getId());
        if (previewHash == null || !previewHash.equals(v.getPreviewHash())) {
            throw outdated();
        }
        List<String> verifiedTerms = termList(v.getId());
        verifyMasking(v, src.getPdf());
        UUID id = v.getId();
        return tx.execute(status -> {
            // 검증 이후 다른 요청이 초안·단어를 바꿨는지 행 잠금 아래에서 다시 확인한다.
            ResumeVersion cur = versions.findByIdForUpdate(id).orElseThrow(ApiException::notFound);
            if (cur.isPublished()) {
                throw alreadyPublished();
            }
            DraftSource now = drafts.findById(id).orElseThrow(ResumeService::expired);
            if (isExpired(now)) {
                drafts.deleteByVersion(id);
                throw expired();
            }
            if (!previewHash.equals(cur.getPreviewHash()) || !Arrays.equals(src.getPdf(), now.getPdf())
                    || !verifiedTerms.equals(termList(id))) {
                throw outdated();
            }
            cur.publish();
            versions.save(cur);
            drafts.deleteByVersion(id);
            return new PublishResponse(cur.getVersionNo(), cur.getStatus(), cur.getPublishedAt());
        });
    }

    /** 4.4: 원본과 저장된 단어로 영역을 다시 계산해 저장된 영역이 덮는지, 게시할 PNG의 영역 픽셀이 검은색인지 검사. */
    private void verifyMasking(ResumeVersion v, byte[] pdf) {
        List<MaskRegion> stored = regions.findByVersionIdOrderByIdAsc(v.getId()).stream()
                .map(MaskRegionRow::toRegion).toList();
        Processed fresh = processor.analyze(pdf, termList(v.getId()));
        Map<Integer, byte[]> pngs = new LinkedHashMap<>();
        for (PageImage pi : images.findByVersionIdOrderByPageNoAsc(v.getId())) {
            pngs.put(pi.getPageNo(), pi.getPng());
        }
        List<Map<String, Object>> failures = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (MaskRegion need : fresh.plan().regions()) {
            boolean covered = stored.stream().anyMatch(s -> s.page() == need.page() && covers(s.box(), need.box()));
            byte[] png = pngs.get(need.page());
            boolean black = png != null && PageRenderer.verifyBlack(png, List.of(need), need.page());
            if ((!covered || !black) && seen.add(need.page() + ":" + need.type())) {
                failures.add(failure(need.page(), need.type()));
            }
        }
        for (MaskRegion s : stored) {
            byte[] png = pngs.get(s.page());
            if ((png == null || !PageRenderer.verifyBlack(png, List.of(s), s.page()))
                    && seen.add(s.page() + ":" + s.type())) {
                failures.add(failure(s.page(), s.type()));
            }
        }
        if (pngs.size() != v.getPageCount()) {
            for (int p = 1; p <= v.getPageCount(); p++) {
                if (!pngs.containsKey(p) && seen.add(p + ":PAGE")) {
                    failures.add(Map.of("page", p, "type", "PAGE"));
                }
            }
        }
        if (!failures.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MASKING_INCOMPLETE",
                    "가림이 덜 된 영역이 있어 게시할 수 없습니다.", Map.of(), failures);
        }
    }

    private static Map<String, Object> failure(int page, RegionType type) {
        return Map.of("page", page, "type", type.name());
    }

    private static boolean covers(Box outer, Box inner) {
        return inner.x() >= outer.x() - EPS && inner.y() >= outer.y() - EPS
                && inner.x() + inner.width() <= outer.x() + outer.width() + EPS
                && inner.y() + inner.height() <= outer.y() + outer.height() + EPS;
    }

    // ---------- 내부 ----------

    private void checkFile(byte[] pdf) {
        if (pdf == null) {
            throw ApiException.validation("file", "PDF 파일을 선택하세요.");
        }
        if (pdf.length > MAX_FILE_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "파일은 5MB 이하여야 합니다.");
        }
    }

    private static List<String> validateTerms(List<String> requested) {
        if (requested == null) {
            throw ApiException.validation("terms", "가릴 단어 목록이 필요합니다.");
        }
        if (requested.size() > 50) {
            throw ApiException.validation("terms", "가릴 단어는 50개까지 가능합니다.");
        }
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < requested.size(); i++) {
            String t = requested.get(i) == null ? "" : requested.get(i).strip();
            if (t.length() < 2 || t.length() > 30) {
                throw ApiException.validation("terms[" + i + "]", "가릴 단어는 2~30자여야 합니다.");
            }
            if (t.chars().anyMatch(Character::isISOControl)) {
                throw ApiException.validation("terms[" + i + "]", "가릴 단어에 제어 문자를 쓸 수 없습니다.");
            }
            out.add(t);
        }
        return List.copyOf(out);
    }

    private void storePreview(ResumeVersion v, Processed p, byte[] pdfOrNull) {
        String text = p.ingest().lines().stream().map(LineText::text).collect(Collectors.joining("\n"));
        String warnings = p.ingest().warningPages().stream().map(String::valueOf)
                .collect(Collectors.joining(","));
        v.applyPreview(text, p.ingest().pages().size(), p.render().previewHash(), warnings,
                p.plan().unmatchedTerms(), p.plan().nameCandidate());
        versions.save(v);
        UUID id = v.getId();
        List<MaskRegionRow> rows = p.plan().regions().stream().map(r -> new MaskRegionRow(id, r)).toList();
        regions.saveAll(rows);
        List<byte[]> pngs = p.render().pngs();
        List<PageImage> pageImages = new ArrayList<>();
        for (int i = 0; i < pngs.size(); i++) {
            BufferedImage bi = decode(pngs.get(i));
            pageImages.add(new PageImage(id, i + 1, pngs.get(i), bi.getWidth(), bi.getHeight()));
        }
        images.saveAll(pageImages);
        if (pdfOrNull != null) {
            drafts.save(new DraftSource(id, pdfOrNull, Instant.now().plus(DRAFT_TTL)));
        }
    }

    private static BufferedImage decode(byte[] png) {
        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void saveTerms(UUID versionId, List<String> list) {
        terms.saveAll(list.stream().map(t -> new MaskTerm(versionId, t)).toList());
    }

    private List<String> termList(UUID versionId) {
        return terms.findByVersionIdOrderByIdAsc(versionId).stream().map(MaskTerm::getTerm).toList();
    }

    private ResumeVersion lastVersion(UUID resumeId) {
        List<ResumeVersion> all = versions.findByResumeIdOrderByVersionNoAsc(resumeId);
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    private Resume owned(UUID ownerId, UUID resumeId) {
        return resumes.findByIdAndOwnerId(resumeId, ownerId).orElseThrow(ApiException::notFound);
    }

    private ResumeVersion ownedVersion(UUID ownerId, UUID resumeId, int no) {
        Resume r = owned(ownerId, resumeId);
        return versions.findByResumeIdAndVersionNo(r.getId(), no).orElseThrow(ApiException::notFound);
    }

    private OwnerVersionView view(ResumeVersion v) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RegionType t : List.of(RegionType.EMAIL, RegionType.PHONE, RegionType.URL, RegionType.TERM,
                RegionType.IMAGE)) {
            counts.put(t.name(), 0);
        }
        for (MaskRegionRow r : regions.findByVersionIdOrderByIdAsc(v.getId())) {
            counts.computeIfPresent(r.getType().name(), (k, n) -> n + 1);
        }
        List<String> warnings = new ArrayList<>();
        if (v.getWarnings() != null && !v.getWarnings().isEmpty()) {
            for (String page : v.getWarnings().split(",")) {
                warnings.add(page + "쪽: " + WARNING_TEXT);
            }
        }
        Preview preview = new Preview(v.getPageCount(), counts, v.getUnmatchedTermList(), v.getNameCandidate(),
                warnings, v.getPreviewHash());
        return new OwnerVersionView(v.getStatus(), v.getExtractedText(), termList(v.getId()), preview,
                v.getVersionNo(), v.getPublishedAt());
    }

    private static ApiException alreadyPublished() {
        return new ApiException(HttpStatus.CONFLICT, "ALREADY_PUBLISHED", "이미 게시된 버전입니다.");
    }

    /** 만료 시각이 지난 원본은 정리 작업을 기다리지 않고 즉시 지우고 410으로 거부한다(4.7). */
    private DraftSource liveDraft(UUID versionId) {
        DraftSource src = drafts.findById(versionId).orElseThrow(ResumeService::expired);
        if (isExpired(src)) {
            tx.executeWithoutResult(s -> drafts.deleteByVersion(versionId));
            throw expired();
        }
        return src;
    }

    private static boolean isExpired(DraftSource src) {
        return !src.getExpiresAt().isAfter(Instant.now());
    }

    private static ApiException outdated() {
        return new ApiException(HttpStatus.CONFLICT, "PREVIEW_OUTDATED",
                "미리보기가 최신이 아닙니다. 미리보기를 다시 확인한 뒤 게시하세요.");
    }

    private static ApiException expired() {
        return new ApiException(HttpStatus.GONE, "DRAFT_SOURCE_EXPIRED",
                "초안 원본이 만료되었습니다. 같은 PDF를 다시 올려 주세요.");
    }
}
