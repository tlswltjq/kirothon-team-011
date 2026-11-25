package dev.kirothon.resume.review;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 요청·응답 DTO. 엔티티를 직접 직렬화하지 않는다. */
public final class ReviewViews {
    private ReviewViews() {
    }

    /** 평가자용: 이 네 필드만 내보낸다(11.2). */
    public record ReviewTargetView(UUID versionId, int versionNo, String targetRole, int pageCount) {
    }

    /** 숫자는 Double로 받아 정수 여부·NaN·범위를 서비스에서 직접 검사한다. */
    public record PinRequest(Double page, Double x, Double y, String comment) {
    }

    public record ReviewRequest(Double totalScore, String summary, List<PinRequest> pins) {
    }

    public record PinView(int seq, int page, double x, double y, String comment) {
    }

    /** 주인용: 평가자 id·닉네임·토큰 없이 라벨(제출 순서)만. */
    public record OwnerReviewView(int reviewerLabel, int totalScore, String summary, Instant createdAt,
                                  boolean sample, List<PinView> pins) {
    }
}
