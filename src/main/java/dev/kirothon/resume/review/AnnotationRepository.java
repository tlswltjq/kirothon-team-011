package dev.kirothon.resume.review;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnotationRepository extends JpaRepository<Annotation, Long> {
    List<Annotation> findByReviewIdInOrderByReviewIdAscSeqAsc(Collection<UUID> reviewIds);
}
