package dev.kirothon.resume.resume;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

/** 마스킹을 칠한 페이지 PNG. 게시 후 불변. */
@Entity
@Table(name = "page_image")
@IdClass(PageImage.Key.class)
public class PageImage {
    public record Key(UUID versionId, int pageNo) implements Serializable {
    }

    @Id
    @Column(name = "version_id")
    private UUID versionId;

    @Id
    @Column(name = "page_no")
    private int pageNo;

    /** bytea(PostgreSQL)·VARBINARY(H2). @Lob은 PostgreSQL에서 oid(대형 객체)로 매핑되므로 쓰지 않는다. */
    @Column(nullable = false)
    private byte[] png;

    @Column(name = "width_px", nullable = false)
    private int widthPx;

    @Column(name = "height_px", nullable = false)
    private int heightPx;

    protected PageImage() {
    }

    public PageImage(UUID versionId, int pageNo, byte[] png, int widthPx, int heightPx) {
        this.versionId = versionId;
        this.pageNo = pageNo;
        this.png = png;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }

    public int getPageNo() {
        return pageNo;
    }

    public byte[] getPng() {
        return png;
    }
}
