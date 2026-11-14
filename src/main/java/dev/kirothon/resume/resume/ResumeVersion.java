package dev.kirothon.resume.resume;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "resume_version",
        uniqueConstraints = @UniqueConstraint(columnNames = {"resume_id", "version_no"}),
        indexes = @Index(name = "idx_version_resume", columnList = "resume_id"))
public class ResumeVersion {
    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";

    /** unmatched_terms 컬럼 구분자(가릴 단어에는 제어 문자를 허용하지 않는다). */
    static final String SEP = "\u001F";

    @Id
    private UUID id;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(nullable = false, length = 10)
    private String status;

    @Lob
    @Column(name = "extracted_text", nullable = false)
    private String extractedText;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "preview_hash", nullable = false, length = 64)
    private String previewHash;

    /** 글자를 읽지 못한 페이지 번호(쉼표 구분). */
    @Column(length = 1000)
    private String warnings;

    /** 데이터 모델에 없는 보조 컬럼: 미리보기의 "일치 없음" 단어(주인 전용). */
    @Column(name = "unmatched_terms", length = 2000)
    private String unmatchedTerms;

    /** 데이터 모델에 없는 보조 컬럼: 이름 후보(주인 전용, 게시 시 지움). */
    @Column(name = "name_candidate", length = 100)
    private String nameCandidate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected ResumeVersion() {
    }

    public ResumeVersion(UUID resumeId, int versionNo) {
        this.id = UUID.randomUUID();
        this.resumeId = resumeId;
        this.versionNo = versionNo;
        this.status = DRAFT;
        this.createdAt = Instant.now();
    }

    public void applyPreview(String extractedText, int pageCount, String previewHash, String warnings,
                             List<String> unmatched, String nameCandidate) {
        this.extractedText = extractedText;
        this.pageCount = pageCount;
        this.previewHash = previewHash;
        this.warnings = warnings;
        this.unmatchedTerms = String.join(SEP, unmatched);
        this.nameCandidate = nameCandidate;
    }

    public void publish() {
        this.status = PUBLISHED;
        this.publishedAt = Instant.now();
        this.nameCandidate = null;
    }

    public boolean isPublished() {
        return PUBLISHED.equals(status);
    }

    public UUID getId() {
        return id;
    }

    public UUID getResumeId() {
        return resumeId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getStatus() {
        return status;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public int getPageCount() {
        return pageCount;
    }

    public String getPreviewHash() {
        return previewHash;
    }

    public String getWarnings() {
        return warnings;
    }

    public List<String> getUnmatchedTermList() {
        return unmatchedTerms == null || unmatchedTerms.isEmpty() ? List.of() : List.of(unmatchedTerms.split(SEP));
    }

    public String getNameCandidate() {
        return nameCandidate;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
