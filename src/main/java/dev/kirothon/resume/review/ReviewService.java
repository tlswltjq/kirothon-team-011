package dev.kirothon.resume.review;

import dev.kirothon.resume.resume.PageImage;
import dev.kirothon.resume.resume.PageImageRepository;
import dev.kirothon.resume.resume.Resume;
import dev.kirothon.resume.resume.ResumeRepository;
import dev.kirothon.resume.resume.ResumeVersion;
import dev.kirothon.resume.resume.ResumeVersionRepository;
import dev.kirothon.resume.review.ReviewViews.OwnerReviewView;
import dev.kirothon.resume.review.ReviewViews.PinRequest;
import dev.kirothon.resume.review.ReviewViews.PinView;
import dev.kirothon.resume.review.ReviewViews.ReviewRequest;
import dev.kirothon.resume.review.ReviewViews.ReviewTargetView;
import dev.kirothon.resume.web.ApiException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 무작위 평가 대상, 평가 제출, 주인용 평가 조회(요구사항 6~9, 11). */
@Service
public class ReviewService {
    static final int MAX_PINS = 50;

    private final ReviewRepository reviews;
    private final AnnotationRepository annotations;
    private final ResumeRepository resumes;
    private final ResumeVersionRepository versions;
    private final PageImageRepository images;
    private final RandomPicker picker;
    private final TransactionTemplate tx;

    public ReviewService(ReviewRepository reviews, AnnotationRepository annotations, ResumeRepository resumes,
                         ResumeVersionRepository versions, PageImageRepository images, RandomPicker picker,
                         PlatformTransactionManager txManager) {
        this.reviews = reviews;
        this.annotations = annotations;
        this.resumes = resumes;
        this.versions = versions;
        this.images = images;
        this.picker = picker;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---------- 무작위 평가 대상 (6) ----------

    public Optional<ReviewTargetView> next(UUID me, UUID skip) {
        List<UUID> candidates = new ArrayList<>(reviews.candidateVersionIds(me));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (skip != null && candidates.size() >= 2) {
            candidates.remove(skip);
        }
        UUID chosen = candidates.get(picker.pick(candidates.size()));
        ResumeVersion v = versions.findById(chosen).orElseThrow(ApiException::notFound);
        Resume r = resumes.findById(v.getResumeId()).orElseThrow(ApiException::notFound);
        return Optional.of(new ReviewTargetView(v.getId(), v.getVersionNo(), r.getTargetRole(), v.getPageCount()));
    }

    /** 게시된 마스킹 PNG만. 초안·없는 버전·없는 페이지는 404(11.5). */
    public byte[] publishedPage(UUID versionId, int page) {
        ResumeVersion v = published(versionId);
        return images.findByVersionIdAndPageNo(v.getId(), page).map(PageImage::getPng)
                .orElseThrow(ApiException::notFound);
    }

    // ---------- 평가 제출 (7, 8) ----------

    public void submit(UUID reviewerId, UUID versionId, ReviewRequest req) {
        if (req == null) {
            throw validation(Map.of("body", "요청 본문이 필요합니다."));
        }
        // 대상 상태(404)와 소유자(403)를 입력 검증(400)보다 먼저 확인한다(8.8).
        ResumeVersion v = published(versionId);
        Resume owner = resumes.findById(v.getResumeId()).orElseThrow(ApiException::notFound);
        if (owner.getOwnerId().equals(reviewerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SELF_REVIEW", "자기 이력서는 평가할 수 없습니다.");
        }
        List<PinRequest> pins = req.pins() == null ? List.of() : req.pins();
        Map<String, String> errors = new LinkedHashMap<>();
        Integer score = integral(req.totalScore(), 1, 5);
        if (score == null) {
            errors.put("totalScore", "총점은 1~5 정수여야 합니다.");
        }
        String summary = req.summary() == null ? "" : req.summary().strip();
        if (summary.length() < 20 || summary.length() > 1000) {
            errors.put("summary", "총평은 앞뒤 공백을 뺀 20~1000자여야 합니다.");
        }
        if (pins.size() > MAX_PINS) {
            errors.put("pins", "핀은 " + MAX_PINS + "개까지 가능합니다.");
        }
        validatePins(pins, errors);
        if (!errors.isEmpty()) {
            throw validation(errors);
        }

        for (int i = 0; i < pins.size(); i++) {
            if (pins.get(i).page().intValue() > v.getPageCount()) {
                errors.put("pins[" + i + "].page", "페이지 번호는 1~" + v.getPageCount() + " 범위여야 합니다.");
            }
        }
        if (!errors.isEmpty()) {
            throw validation(errors);
        }
        if (reviews.existsByVersionIdAndReviewerId(versionId, reviewerId)) {
            throw duplicate();
        }

        try {
            tx.executeWithoutResult(status -> {
                Review review = new Review(versionId, reviewerId, score, summary);
                reviews.saveAndFlush(review);
                List<Annotation> rows = new ArrayList<>();
                for (int i = 0; i < pins.size(); i++) {
                    PinRequest p = pins.get(i);
                    rows.add(new Annotation(review.getId(), i + 1, p.page().intValue(), p.x(), p.y(),
                            p.comment().strip()));
                }
                annotations.saveAll(rows);
                annotations.flush();
            });
        } catch (DataIntegrityViolationException e) {
            // (version_id, reviewer_id) 유일 제약 위반 = 동시 중복 제출(8.5). 롤백되어 행이 남지 않는다.
            throw duplicate();
        }
    }

    private static void validatePins(List<PinRequest> pins, Map<String, String> errors) {
        for (int i = 0; i < pins.size(); i++) {
            PinRequest p = pins.get(i);
            String at = "pins[" + i + "]";
            if (p == null) {
                errors.put(at, "핀 값이 필요합니다.");
                continue;
            }
            if (integral(p.page(), 1, Integer.MAX_VALUE) == null) {
                errors.put(at + ".page", "페이지 번호는 1 이상의 정수여야 합니다.");
            }
            if (!unit(p.x())) {
                errors.put(at + ".x", "x는 0 이상 1 이하의 숫자여야 합니다.");
            }
            if (!unit(p.y())) {
                errors.put(at + ".y", "y는 0 이상 1 이하의 숫자여야 합니다.");
            }
            String c = p.comment() == null ? "" : p.comment().strip();
            if (c.isEmpty() || c.length() > 500) {
                errors.put(at + ".comment", "코멘트는 앞뒤 공백을 뺀 1~500자여야 합니다.");
            }
        }
    }

    private static boolean unit(Double d) {
        return d != null && !d.isNaN() && !d.isInfinite() && d >= 0.0 && d <= 1.0;
    }

    private static Integer integral(Double d, int min, int max) {
        if (d == null || d.isNaN() || d.isInfinite() || d != Math.rint(d) || d < min || d > max) {
            return null;
        }
        return d.intValue();
    }

    private static ApiException validation(Map<String, String> errors) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값이 올바르지 않습니다.", errors, null);
    }

    private static ApiException duplicate() {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_REVIEW", "이미 평가한 이력서입니다.");
    }

    private ResumeVersion published(UUID versionId) {
        ResumeVersion v = versions.findById(versionId).orElseThrow(ApiException::notFound);
        if (!v.isPublished()) {
            throw ApiException.notFound();
        }
        return v;
    }

    // ---------- 주인용 조회 (9) ----------

    /** 최신순. 라벨은 버전 안 제출 순서(1..). 평가자 식별 정보는 읽지 않는다. */
    public List<OwnerReviewView> ownerReviews(UUID ownerId, UUID resumeId, int no) {
        Resume r = resumes.findByIdAndOwnerId(resumeId, ownerId).orElseThrow(ApiException::notFound);
        ResumeVersion v = versions.findByResumeIdAndVersionNo(r.getId(), no).orElseThrow(ApiException::notFound);
        List<Review> ordered = reviews.findByVersionIdOrderByCreatedAtAscIdAsc(v.getId());
        if (ordered.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<PinView>> pinsByReview = annotations
                .findByReviewIdInOrderByReviewIdAscSeqAsc(ordered.stream().map(Review::getId).toList()).stream()
                .collect(Collectors.groupingBy(Annotation::getReviewId, LinkedHashMap::new,
                        Collectors.mapping(a -> new PinView(a.getSeq(), a.getPageNo(), a.getX(), a.getY(),
                                a.getComment()), Collectors.toList())));
        List<OwnerReviewView> out = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            Review rv = ordered.get(i);
            out.add(new OwnerReviewView(i + 1, rv.getTotalScore(), rv.getSummary(), rv.getCreatedAt(),
                    rv.isSample(), pinsByReview.getOrDefault(rv.getId(), List.of())));
        }
        Collections.reverse(out);
        return out;
    }
}
