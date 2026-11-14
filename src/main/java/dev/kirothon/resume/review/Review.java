package dev.kirothon.resume.review;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** 평가 한 건. reviewer_id는 응답에 절대 내보내지 않는다(11.3). */
@Entity
@Table(name = "review",
        uniqueConstraints = @UniqueConstraint(name = "uk_review_version_reviewer",
                columnNames = {"version_id", "reviewer_id"}),
        indexes = @Index(name = "idx_review_version", columnList = "version_id"),
        check = @CheckConstraint(name = "ck_review_score", constraint = "total_score between 1 and 5"))
public class Review {
    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Column(name = "total_score", nullable = false)
    private short totalScore;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(nullable = false)
    private boolean sample;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Review() {
    }

    public Review(UUID versionId, UUID reviewerId, int totalScore, String summary) {
        this.id = UUID.randomUUID();
        this.versionId = versionId;
        this.reviewerId = reviewerId;
        this.totalScore = (short) totalScore;
        this.summary = summary;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public String getSummary() {
        return summary;
    }

    public boolean isSample() {
        return sample;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
