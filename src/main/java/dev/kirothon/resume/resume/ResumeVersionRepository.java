package dev.kirothon.resume.resume;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, UUID> {
    Optional<ResumeVersion> findByResumeIdAndVersionNo(UUID resumeId, int versionNo);

    Optional<ResumeVersion> findFirstByResumeIdAndStatus(UUID resumeId, String status);

    List<ResumeVersion> findByResumeIdOrderByVersionNoAsc(UUID resumeId);
}
