package dev.kirothon.resume.demo;

import dev.kirothon.resume.resume.Resume;
import dev.kirothon.resume.resume.ResumeRepository;
import dev.kirothon.resume.resume.ResumeService;
import dev.kirothon.resume.resume.ResumeViews.OwnerVersionView;
import dev.kirothon.resume.resume.ResumeViews.UploadResponse;
import dev.kirothon.resume.resume.ResumeVersion;
import dev.kirothon.resume.resume.ResumeVersionRepository;
import dev.kirothon.resume.review.ReviewRepository;
import dev.kirothon.resume.review.ReviewService;
import dev.kirothon.resume.review.ReviewViews.PinRequest;
import dev.kirothon.resume.review.ReviewViews.ReviewRequest;
import dev.kirothon.resume.security.TokenHasher;
import dev.kirothon.resume.user.AnonUser;
import dev.kirothon.resume.user.AnonUserRepository;
import dev.kirothon.resume.web.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 요구사항 12: 샘플 사용자 3명(가상)과 요청자의 샘플 이력서 v1, 핀 포함 샘플 평가를 만든다.
 * 미리 만든 PDF를 실제 {@link ResumeService}(추출, 마스킹, 게시)와 {@link ReviewService} 경로로 투입한다.
 * 요청자 v2는 만들지 않는다(시연에서 직접 올린다).
 */
@Service
@Profile("demo")
public class DemoSeeder {
    public record SeedResult(UUID resumeId, int reviews) {
    }

    /** 샘플 사용자: 닉네임, 이력서 PDF(없으면 평가만 함), 지원 직무, 가릴 이름. */
    private record Person(String nickname, String pdf, String role, String name) {
    }

    private static final List<Person> PEOPLE = List.of(
            new Person("샘플 박서준", "frontend-park.pdf", "프론트엔드 개발자", "박서준"),
            new Person("샘플 이지우", "data-lee.pdf", "데이터 분석가", "이지우"),
            new Person("샘플 최민준", null, null, null));

    private record SampleReview(int score, String summary, List<PinRequest> pins) {
    }

    private static PinRequest pin(double x, double y, String comment) {
        return new PinRequest(1.0, x, y, comment);
    }

    /** 요청자 v1(빽빽한 단일 열, 성과 수치 없음)에 대한 평가. 평균 (3+3+2)/3 = 2.67. */
    private static final List<SampleReview> V1_REVIEWS = List.of(
            new SampleReview(3, "구조는 읽을 만하지만 문단이 빽빽하고 성과가 숫자로 드러나지 않아 인상이 약합니다. 경력 요약부터 줄여 보세요.",
                    List.of(pin(0.50, 0.09, "경력 요약이 너무 깁니다. 핵심 두세 줄로 줄여 보세요."),
                            pin(0.40, 0.17, "담당한 일만 있고 결과가 없습니다. 성과에 수치를 넣어 주세요."))),
            new SampleReview(3, "기술 스택은 분명하게 보입니다. 다만 같은 문장 패턴이 반복되어 무엇을 개선했는지 알기 어렵습니다.",
                    List.of(pin(0.40, 0.19, "'참여했다', '진행했다'만 반복됩니다. 본인이 한 일을 구체적으로 써 주세요."),
                            pin(0.35, 0.26, "기술 스택이 한 줄이라 숙련도를 알 수 없습니다. 사용 기간이나 수준을 적어 보세요."),
                            pin(0.55, 0.14, "경력 항목의 역할이 모호합니다."))),
            new SampleReview(2, "여백이 거의 없고 제목과 본문의 위계가 약해 첫눈에 읽히지 않습니다. 성과 중심으로 다시 써 보세요.",
                    List.of(pin(0.50, 0.12, "여백이 없어 눈이 피로합니다. 항목 사이에 간격을 두세요."),
                            pin(0.45, 0.21, "결제 관련 개발이라고만 되어 있어 규모와 결과를 알 수 없습니다."))));

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final String ownerV1Path;
    private final List<String> ownerTerms;
    private final AnonUserRepository users;
    private final ResumeRepository resumes;
    private final ResumeVersionRepository versions;
    private final ReviewRepository reviews;
    private final ResumeService resumeService;
    private final ReviewService reviewService;

    public DemoSeeder(AnonUserRepository users, ResumeRepository resumes, ResumeVersionRepository versions,
                      ReviewRepository reviews, ResumeService resumeService, ReviewService reviewService,
                      @Value("${app.demo.owner-v1-path:}") String ownerV1Path,
                      @Value("${app.demo.owner-terms:}") String ownerTerms) {
        this.ownerV1Path = ownerV1Path == null ? "" : ownerV1Path.trim();
        this.ownerTerms = Arrays.stream((ownerTerms == null ? "" : ownerTerms).split(","))
                .map(String::trim).filter(t -> !t.isEmpty()).toList();
        this.users = users;
        this.resumes = resumes;
        this.versions = versions;
        this.reviews = reviews;
        this.resumeService = resumeService;
        this.reviewService = reviewService;
    }

    /** 샘플 사용자는 앱 전체에서 한 번만 만들기 때문에 시드는 한 번에 하나씩 실행한다. */
    public synchronized SeedResult seed(UUID requesterId) {
        if (resumes.existsByOwnerIdAndSampleTrue(requesterId)) {
            throw new ApiException(HttpStatus.CONFLICT, "DEMO_ALREADY_SEEDED", "이미 샘플 데이터를 넣었습니다.");
        }
        List<UUID> reviewers = PEOPLE.stream().map(this::ensureSampleUser).toList();

        UploadResponse up = resumeService.createResume(requesterId, "백엔드 개발자 이력서 (샘플)", "백엔드 개발자",
                loadOwnerV1());
        resumes.markSample(up.resumeId());
        publish(requesterId, up, ownerTermsOrDefault());

        UUID versionId = versionId(up.resumeId(), 1);
        for (int i = 0; i < V1_REVIEWS.size(); i++) {
            SampleReview r = V1_REVIEWS.get(i);
            UUID reviewer = reviewers.get(i);
            reviewService.submit(reviewer, versionId,
                    new ReviewRequest((double) r.score(), r.summary(), r.pins()));
            reviews.markSample(versionId, reviewer);
        }
        return new SeedResult(up.resumeId(), V1_REVIEWS.size());
    }

    /** 샘플 사용자가 없으면 만들고(토큰은 버린다), 이력서 PDF가 있는 사람은 게시까지 한다. */
    private UUID ensureSampleUser(Person p) {
        AnonUser existing = users.findBySampleTrueAndNickname(p.nickname()).orElse(null);
        if (existing != null) {
            return existing.getId();
        }
        AnonUser u = users.save(new AnonUser(p.nickname(), TokenHasher.sha256Hex(TokenHasher.newToken())));
        users.markSample(u.getId());
        if (p.pdf() != null) {
            UploadResponse up = resumeService.createResume(u.getId(), p.role() + " 이력서 (샘플)", p.role(),
                    load(p.pdf()));
            resumes.markSample(up.resumeId());
            publish(u.getId(), up, List.of(p.name()));
        }
        return u.getId();
    }

    private void publish(UUID ownerId, UploadResponse up, List<String> terms) {
        OwnerVersionView masked = resumeService.replaceTerms(ownerId, up.resumeId(), up.versionNo(), terms);
        resumeService.publish(ownerId, up.resumeId(), up.versionNo(), masked.preview().previewHash(), true);
    }

    private UUID versionId(UUID resumeId, int no) {
        Resume r = resumes.findById(resumeId).orElseThrow(ApiException::notFound);
        ResumeVersion v = versions.findByResumeIdAndVersionNo(r.getId(), no).orElseThrow(ApiException::notFound);
        return v.getId();
    }

    /** 선택 설정이 있으면 그 PDF를, 없거나 읽을 수 없으면 클래스패스 샘플을 쓴다. 경고 로그에는 경로만 남긴다. */
    private byte[] loadOwnerV1() {
        if (!ownerV1Path.isEmpty()) {
            try {
                return Files.readAllBytes(Path.of(ownerV1Path));
            } catch (IOException | RuntimeException e) {
                log.warn("app.demo.owner-v1-path 파일을 읽지 못해 기본 샘플을 사용합니다: {}", ownerV1Path);
            }
        }
        return load("owner-v1.pdf");
    }

    /** 설정한 PDF를 실제로 쓸 때만 설정한 가릴 단어를 쓴다. 기본 샘플은 가상 인물 이름이다. */
    private List<String> ownerTermsOrDefault() {
        return usingCustomV1() && !ownerTerms.isEmpty() ? ownerTerms : List.of("김하늘");
    }

    private boolean usingCustomV1() {
        return !ownerV1Path.isEmpty() && Files.isReadable(Path.of(ownerV1Path));
    }

    private static byte[] load(String name) {
        try (InputStream in = new ClassPathResource("demo/" + name).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
