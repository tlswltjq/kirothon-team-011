package dev.kirothon.resume.resume;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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

    /** bytea(PostgreSQL)·VARBINARY(H2). @Lob은 PostgreSQL에서 oid(대형 객체)로 매핑되므로 쓰지 않는다. */
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
