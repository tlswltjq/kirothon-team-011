package dev.kirothon.resume.resume;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DraftSourceRepository extends JpaRepository<DraftSource, UUID> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DraftSource d where d.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DraftSource d where d.versionId = :versionId")
    int deleteByVersion(@Param("versionId") UUID versionId);
}
