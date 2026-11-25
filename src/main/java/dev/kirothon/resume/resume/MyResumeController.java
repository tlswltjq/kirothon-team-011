package dev.kirothon.resume.resume;

import dev.kirothon.resume.resume.ResumeViews.OwnerVersionView;
import dev.kirothon.resume.resume.ResumeViews.PublishRequest;
import dev.kirothon.resume.resume.ResumeViews.PublishResponse;
import dev.kirothon.resume.resume.ResumeViews.ResumeDetail;
import dev.kirothon.resume.resume.ResumeViews.ResumeSummary;
import dev.kirothon.resume.resume.ResumeViews.TermsRequest;
import dev.kirothon.resume.resume.ResumeViews.UploadResponse;
import dev.kirothon.resume.security.AnonTokenFilter;
import dev.kirothon.resume.user.AnonUser;
import dev.kirothon.resume.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 주인 전용 /api/my/** (작업 5·6, 8의 목록 경로). 남의 이력서는 항상 404. */
@Tag(name = "내 이력서", description = "주인 전용 이력서·버전·받은 평가")
@RestController
@RequestMapping("/api/my/resumes")
public class MyResumeController {
    private final ResumeService service;

    public MyResumeController(ResumeService service) {
        this.service = service;
    }

    @Operation(summary = "PDF를 올려 새 이력서와 v1 초안을 만든다")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> create(
            @RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "targetRole", required = false) String targetRole,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.status(201).body(service.createResume(user.getId(), title, targetRole, bytes(file)));
    }

    @Operation(summary = "내 이력서 목록을 조회한다")
    @GetMapping
    public List<ResumeSummary> list(@RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user) {
        return service.list(user.getId());
    }

    @Operation(summary = "이력서 상세와 버전별 평균 총점을 조회한다")
    @GetMapping("/{resumeId}")
    public ResumeDetail detail(@RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user,
                               @PathVariable UUID resumeId) {
        return service.detail(user.getId(), resumeId);
    }

    @Operation(summary = "PDF를 올려 새 버전 초안을 만든다")
    @PostMapping(value = "/{resumeId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> addVersion(
            @RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user,
            @PathVariable UUID resumeId,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.status(201).body(service.addVersion(user.getId(), resumeId, bytes(file)));
    }

    @Operation(summary = "버전 상태와 마스킹 미리보기 정보를 조회한다")
    @GetMapping("/{resumeId}/versions/{no}")
    public OwnerVersionView version(@RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user,
                                    @PathVariable UUID resumeId, @PathVariable int no) {
        return service.getVersion(user.getId(), resumeId, no);
    }

    @Operation(summary = "내 버전의 마스킹 페이지 PNG를 받는다")
    @GetMapping(value = "/{resumeId}/versions/{no}/pages/{p}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> page(@RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user,
                                       @PathVariable UUID resumeId, @PathVariable int no, @PathVariable int p) {
        byte[] png = service.getPagePng(user.getId(), resumeId, no, p);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(png);
    }

    @Operation(summary = "지정 마스킹 단어를 바꾸고 미리보기를 다시 만든다")
    @PutMapping("/{resumeId}/versions/{no}/mask-terms")
    public OwnerVersionView maskTerms(@RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user,
                                      @PathVariable UUID resumeId, @PathVariable int no,
                                      @RequestBody TermsRequest request) {
        return service.replaceTerms(user.getId(), resumeId, no, request.terms());
    }

    @Operation(summary = "확인한 미리보기로 버전을 게시한다")
    @PostMapping("/{resumeId}/versions/{no}/publish")
    public PublishResponse publish(@RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user,
                                   @PathVariable UUID resumeId, @PathVariable int no,
                                   @RequestBody PublishRequest request) {
        return service.publish(user.getId(), resumeId, no, request.previewHash(), request.confirmed());
    }

    private static byte[] bytes(MultipartFile file) {
        if (file == null) {
            throw ApiException.validation("file", "PDF 파일을 선택하세요.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw ApiException.validation("file", "파일을 읽을 수 없습니다.");
        }
    }
}
