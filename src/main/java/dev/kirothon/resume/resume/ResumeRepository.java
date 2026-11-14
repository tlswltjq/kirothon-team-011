package dev.kirothon.resume.resume;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {
    Optional<Resume> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Resume> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
