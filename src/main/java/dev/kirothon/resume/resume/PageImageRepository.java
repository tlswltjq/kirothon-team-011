package dev.kirothon.resume.resume;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PageImageRepository extends JpaRepository<PageImage, PageImage.Key> {
    Optional<PageImage> findByVersionIdAndPageNo(UUID versionId, int pageNo);

    List<PageImage> findByVersionIdOrderByPageNoAsc(UUID versionId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PageImage p where p.versionId = :versionId")
    void deleteByVersion(@Param("versionId") UUID versionId);
}
