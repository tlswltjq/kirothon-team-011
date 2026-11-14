package dev.kirothon.resume.review;

import dev.kirothon.resume.review.ReviewViews.ReviewRequest;
import dev.kirothon.resume.review.ReviewViews.ReviewTargetView;
import dev.kirothon.resume.security.AnonTokenFilter;
import dev.kirothon.resume.user.AnonUser;
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
@RestController
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @GetMapping("/api/review-queue/next")
    public ResponseEntity<ReviewTargetView> next(@RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user,
                                                 @RequestParam(value = "skip", required = false) String skip) {
        return service.next(user.getId(), parse(skip))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping(value = "/api/versions/{versionId}/pages/{p}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> page(@PathVariable UUID versionId, @PathVariable int p) {
        byte[] png = service.publishedPage(versionId, p);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(png);
    }

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
