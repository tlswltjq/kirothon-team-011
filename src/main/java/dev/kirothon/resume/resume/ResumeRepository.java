package dev.kirothon.resume.resume;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {
    Optional<Resume> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Resume> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    boolean existsByOwnerIdAndSampleTrue(UUID ownerId);

    @Transactional
    @Modifying
    @Query("update Resume r set r.sample = true where r.id = :id")
    int markSample(@Param("id") UUID id);
}
