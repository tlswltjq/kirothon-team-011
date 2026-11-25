package dev.kirothon.resume.resume;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "mask_term", indexes = @Index(name = "idx_term_version", columnList = "version_id"))
public class MaskTerm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(nullable = false, length = 30)
    private String term;

    protected MaskTerm() {
    }

    public MaskTerm(UUID versionId, String term) {
        this.versionId = versionId;
        this.term = term;
    }

    public String getTerm() {
        return term;
    }
}
