package dev.kirothon.resume.resume;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, UUID> {
    /** 초안 교체·단어 변경·게시가 같은 행 잠금으로 직렬화되도록 쓴다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ResumeVersion v where v.id = :id")
    Optional<ResumeVersion> findByIdForUpdate(@Param("id") UUID id);

    Optional<ResumeVersion> findByResumeIdAndVersionNo(UUID resumeId, int versionNo);

    Optional<ResumeVersion> findFirstByResumeIdAndStatus(UUID resumeId, String status);

    List<ResumeVersion> findByResumeIdOrderByVersionNoAsc(UUID resumeId);
}
