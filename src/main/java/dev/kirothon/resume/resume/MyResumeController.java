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
@RestController
@RequestMapping("/api/my/resumes")
public class MyResumeController {
    private final ResumeService service;

    public MyResumeController(ResumeService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> create(
            @RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "targetRole", required = false) String targetRole,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.status(201).body(service.createResume(user.getId(), title, targetRole, bytes(file)));
    }

    @GetMapping
    public List<ResumeSummary> list(@RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user) {
        return service.list(user.getId());
    }

    @GetMapping("/{resumeId}")
    public ResumeDetail detail(@RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user,
                               @PathVariable UUID resumeId) {
        return service.detail(user.getId(), resumeId);
    }

    @PostMapping(value = "/{resumeId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> addVersion(
            @RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user,
            @PathVariable UUID resumeId,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return ResponseEntity.status(201).body(service.addVersion(user.getId(), resumeId, bytes(file)));
    }

    @GetMapping("/{resumeId}/versions/{no}")
    public OwnerVersionView version(@RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user,
                                    @PathVariable UUID resumeId, @PathVariable int no) {
        return service.getVersion(user.getId(), resumeId, no);
    }

    @GetMapping(value = "/{resumeId}/versions/{no}/pages/{p}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> page(@RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user,
                                       @PathVariable UUID resumeId, @PathVariable int no, @PathVariable int p) {
        byte[] png = service.getPagePng(user.getId(), resumeId, no, p);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).body(png);
    }

    @PutMapping("/{resumeId}/versions/{no}/mask-terms")
    public OwnerVersionView maskTerms(@RequestAttribute(AnonTokenFilter.USER_ATTRIBUTE) AnonUser user,
                                      @PathVariable UUID resumeId, @PathVariable int no,
                                      @RequestBody TermsRequest request) {
        return service.replaceTerms(user.getId(), resumeId, no, request.terms());
    }

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
