package dev.kirothon.resume.resume;

import dev.kirothon.resume.pdf.PdfTypes.Box;
import dev.kirothon.resume.pdf.PdfTypes.MaskRegion;
import dev.kirothon.resume.pdf.PdfTypes.RegionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

/** mask_region 테이블 행(PDF 포인트, 왼쪽 위 원점). 주인 전용. */
@Entity
@Table(name = "mask_region", indexes = @Index(name = "idx_region_version", columnList = "version_id"))
public class MaskRegionRow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "page_no", nullable = false)
    private int pageNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RegionType type;

    @Column(nullable = false)
    private double x;
    @Column(nullable = false)
    private double y;
    @Column(nullable = false)
    private double w;
    @Column(nullable = false)
    private double h;

    protected MaskRegionRow() {
    }

    public MaskRegionRow(UUID versionId, MaskRegion r) {
        this.versionId = versionId;
        this.pageNo = r.page();
        this.type = r.type();
        this.x = r.box().x();
        this.y = r.box().y();
        this.w = r.box().width();
        this.h = r.box().height();
    }

    public MaskRegion toRegion() {
        return new MaskRegion(pageNo, type, new Box((float) x, (float) y, (float) w, (float) h));
    }

    public int getPageNo() {
        return pageNo;
    }

    public RegionType getType() {
        return type;
    }
}
