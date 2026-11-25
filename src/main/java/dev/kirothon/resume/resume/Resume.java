package dev.kirothon.resume.resume;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resume", indexes = @Index(name = "idx_resume_owner", columnList = "owner_id"))
public class Resume {
    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(name = "target_role", nullable = false, length = 40)
    private String targetRole;

    @Column(nullable = false)
    private boolean sample;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Resume() {
    }

    public Resume(UUID ownerId, String title, String targetRole) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.title = title;
        this.targetRole = targetRole;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getTitle() {
        return title;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public boolean isSample() {
        return sample;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
