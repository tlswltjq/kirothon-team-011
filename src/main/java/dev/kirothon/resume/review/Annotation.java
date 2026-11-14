package dev.kirothon.resume.review;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/** 첨삭 핀. 좌표는 이미지 너비·높이 대비 0~1 정규화 값. */
@Entity
@Table(name = "annotation",
        uniqueConstraints = @UniqueConstraint(name = "uk_annotation_review_seq", columnNames = {"review_id", "seq"}),
        indexes = @Index(name = "idx_annotation_review", columnList = "review_id"),
        check = @CheckConstraint(name = "ck_annotation_range",
                constraint = "x between 0 and 1 and y between 0 and 1 and page_no >= 1"))
public class Annotation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(nullable = false)
    private int seq;

    @Column(name = "page_no", nullable = false)
    private int pageNo;

    @Column(nullable = false)
    private double x;

    @Column(nullable = false)
    private double y;

    @Column(nullable = false, length = 500)
    private String comment;

    protected Annotation() {
    }

    public Annotation(UUID reviewId, int seq, int pageNo, double x, double y, String comment) {
        this.reviewId = reviewId;
        this.seq = seq;
        this.pageNo = pageNo;
        this.x = x;
        this.y = y;
        this.comment = comment;
    }

    public UUID getReviewId() {
        return reviewId;
    }

    public int getSeq() {
        return seq;
    }

    public int getPageNo() {
        return pageNo;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public String getComment() {
        return comment;
    }
}
