package dev.kirothon.resume.review;

import dev.kirothon.resume.review.ReviewViews.ReviewRequest;
import dev.kirothon.resume.review.ReviewViews.ReviewTargetView;
import dev.kirothon.resume.security.AnonTokenFilter;
import dev.kirothon.resume.user.AnonUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 평가자용 /api/review-queue, /api/versions/**. 응답에 이력서 원문·제목·id·닉네임이 없다. */
@Tag(name = "평가", description = "평가자용 무작위 평가 대상, 마스킹 이미지, 평가 제출")
@RestController
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @Operation(summary = "평가할 이력서 버전을 무작위로 받는다(없으면 204)")
    @GetMapping("/api/review-queue/next")
    public ResponseEntity<ReviewTargetView> next(@RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user,
                                                 @RequestParam(value = "skip", required = false) String skip) {
        return service.next(user.getId(), parse(skip))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "게시된 마스킹 페이지 PNG를 받는다")
    @GetMapping(value = "/api/versions/{versionId}/pages/{p}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> page(@PathVariable UUID versionId, @PathVariable int p) {
        byte[] png = service.publishedPage(versionId, p);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(png);
    }

    @Operation(summary = "핀 첨삭·총평·총점을 한 번에 제출한다")
    @PostMapping("/api/versions/{versionId}/reviews")
    public ResponseEntity<Void> submit(@RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user,
                                       @PathVariable UUID versionId, @RequestBody ReviewRequest request) {
        service.submit(user.getId(), versionId, request);
        return ResponseEntity.status(201).build();
    }

    /** 형식이 틀린 skip은 없는 것으로 본다. */
    private static UUID parse(String skip) {
        if (skip == null || skip.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(skip.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
