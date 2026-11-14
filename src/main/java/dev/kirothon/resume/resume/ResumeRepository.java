package dev.kirothon.resume.resume;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {
    Optional<Resume> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Resume> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    /** 같은 이력서의 버전 업로드를 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resume r where r.id = :id")
    Optional<Resume> findByIdForUpdate(@Param("id") UUID id);
}
