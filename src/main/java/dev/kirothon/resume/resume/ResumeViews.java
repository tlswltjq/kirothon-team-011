package dev.kirothon.resume.resume;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 응답 DTO(design.md JSON 계약). 엔티티를 직접 직렬화하지 않는다. */
public final class ResumeViews {
    private ResumeViews() {
    }

    public record Preview(int pageCount, Map<String, Integer> counts, List<String> unmatchedTerms,
                          String nameCandidate, List<String> warnings, String previewHash) {
    }

    /** OwnerVersionView + {versionNo, publishedAt}. */
    public record OwnerVersionView(String status, String extractedText, List<String> maskTerms, Preview preview,
                                   int versionNo, Instant publishedAt) {
    }

    public record UploadResponse(UUID resumeId, int versionNo, OwnerVersionView version) {
    }

    public record PublishResponse(int versionNo, String status, Instant publishedAt) {
    }

    public record ResumeSummary(UUID resumeId, String title, String targetRole, int latestVersionNo,
                                int reviewCount) {
    }

    public record VersionSummary(int versionNo, String status, Instant publishedAt, int pageCount,
                                 int reviewCount, Double averageScore) {
    }

    public record ResumeDetail(UUID resumeId, String title, String targetRole, List<VersionSummary> versions) {
    }

    public record TermsRequest(List<String> terms) {
    }

    public record PublishRequest(String previewHash, Boolean confirmed) {
    }
}
