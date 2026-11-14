package dev.kirothon.resume.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnonUserRepository extends JpaRepository<AnonUser, UUID> {
    Optional<AnonUser> findByTokenHash(String tokenHash);
}
