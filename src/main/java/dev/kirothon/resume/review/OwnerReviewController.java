package dev.kirothon.resume.review;

import dev.kirothon.resume.review.ReviewViews.OwnerReviewView;
import dev.kirothon.resume.security.AnonTokenFilter;
import dev.kirothon.resume.user.AnonUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/** 주인 전용 평가·핀 목록. 남의 이력서는 404(11.1). */
@Tag(name = "내 이력서", description = "주인 전용 이력서·버전·받은 평가")
@RestController
public class OwnerReviewController {
    private final ReviewService service;

    public OwnerReviewController(ReviewService service) {
        this.service = service;
    }

    @Operation(summary = "내 버전이 받은 평가와 핀 목록을 조회한다")
    @GetMapping("/api/my/resumes/{resumeId}/versions/{no}/reviews")
    public ResponseEntity<List<OwnerReviewView>> reviews(
            @RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user,
            @PathVariable UUID resumeId, @PathVariable int no) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.ownerReviews(user.getId(), resumeId, no));
    }
}
