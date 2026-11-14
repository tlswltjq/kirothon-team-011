package dev.kirothon.resume.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AnonUserRepository extends JpaRepository<AnonUser, UUID> {
    Optional<AnonUser> findByTokenHash(String tokenHash);

    Optional<AnonUser> findBySampleTrueAndNickname(String nickname);

    @Transactional
    @Modifying
    @Query("update AnonUser u set u.sample = true where u.id = :id")
    int markSample(@Param("id") UUID id);
}
