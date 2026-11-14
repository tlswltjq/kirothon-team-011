package dev.kirothon.resume.resume;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** 초안 동안만 두는 원본 PDF. 어떤 응답에도 넣지 않는다. */
@Entity
@Table(name = "draft_source")
public class DraftSource {
    @Id
    @Column(name = "version_id")
    private UUID versionId;

    @Lob
    @Column(nullable = false)
    private byte[] pdf;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected DraftSource() {
    }

    public DraftSource(UUID versionId, byte[] pdf, Instant expiresAt) {
        this.versionId = versionId;
        this.pdf = pdf;
        this.expiresAt = expiresAt;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public byte[] getPdf() {
        return pdf;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
