package dev.kirothon.resume.resume;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaskTermRepository extends JpaRepository<MaskTerm, Long> {
    List<MaskTerm> findByVersionIdOrderByIdAsc(UUID versionId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MaskTerm t where t.versionId = :versionId")
    void deleteByVersion(@Param("versionId") UUID versionId);
}
