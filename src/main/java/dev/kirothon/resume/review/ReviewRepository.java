package dev.kirothon.resume.review;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    boolean existsByVersionIdAndReviewerId(UUID versionId, UUID reviewerId);

    List<Review> findByVersionIdOrderByCreatedAtAscIdAsc(UUID versionId);

    /** 후보: 게시됨 + 내 이력서 아님 + 내가 평가하지 않음. id만 읽는다(6.3). */
    @Query("select v.id from ResumeVersion v, Resume r where v.resumeId = r.id and v.status = 'PUBLISHED' "
            + "and r.ownerId <> :me and not exists "
            + "(select 1 from Review rv where rv.versionId = v.id and rv.reviewerId = :me) "
            + "order by v.publishedAt asc, v.id asc")
    List<UUID> candidateVersionIds(@Param("me") UUID me);

    @Query("select count(rv) from Review rv where rv.versionId = :versionId")
    long countByVersion(@Param("versionId") UUID versionId);

    @Query("select coalesce(sum(rv.totalScore), 0) from Review rv where rv.versionId = :versionId")
    long sumScoreByVersion(@Param("versionId") UUID versionId);

    @Query("select count(rv) from Review rv where rv.versionId in "
            + "(select v.id from ResumeVersion v where v.resumeId = :resumeId)")
    long countByResume(@Param("resumeId") UUID resumeId);

    @Transactional
    @Modifying
    @Query("update Review rv set rv.sample = true where rv.versionId = :versionId and rv.reviewerId = :reviewerId")
    int markSample(@Param("versionId") UUID versionId, @Param("reviewerId") UUID reviewerId);

    /** 평균 총점(소수 둘째 자리 HALF_UP). 평가 0개면 null. */
    default BigDecimal averageScore(UUID versionId) {
        long n = countByVersion(versionId);
        if (n == 0) {
            return null;
        }
        return BigDecimal.valueOf(sumScoreByVersion(versionId)).divide(BigDecimal.valueOf(n), 2,
                RoundingMode.HALF_UP);
    }
}
