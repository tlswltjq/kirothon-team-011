package dev.kirothon.resume.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kirothon.resume.pdf.PdfTypes.Box;
import dev.kirothon.resume.pdf.PdfTypes.MaskRegion;
import dev.kirothon.resume.pdf.PdfTypes.RegionType;
import dev.kirothon.resume.pdf.PdfTypes.RenderResult;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquare;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

class PageRendererTest {

    private static final PDRectangle SIZE = new PDRectangle(200, 100); // 417x208 px at 150DPI
    private final PageRenderer renderer = new PageRenderer();

    /** 흰 배경 위 위쪽 기준 (10,10)-(60,40) pt 에 파란 사각형을 그린 n쪽 PDF. */
    private static byte[] pdf(int pages, boolean withAnnotation) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(SIZE);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.setNonStrokingColor(0f, 0f, 1f);
                    cs.addRect(10, 60, 50, 30);
                    cs.fill();
                }
                if (withAnnotation && i == 0) {
                    PDAnnotationSquare sq = new PDAnnotationSquare();
                    sq.setRectangle(new PDRectangle(100, 20, 50, 30)); // 위쪽 기준 50..80pt
                    PDColor red = new PDColor(new float[] {1f, 0f, 0f}, PDDeviceRGB.INSTANCE);
                    sq.setColor(red);
                    sq.setInteriorColor(red);
                    sq.constructAppearances(doc);
                    page.getAnnotations().add(sq);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static MaskRegion region(int page, float x, float y, float w, float h) {
        return new MaskRegion(page, RegionType.MANUAL, new Box(x, y, w, h));
    }

    private static BufferedImage decode(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    private static int px(float pt) {
        return Math.round(pt * 150 / 72f);
    }

    @Test
    void req34_regionPixelsAreAllBlack() throws IOException {
        List<MaskRegion> regions = List.of(region(1, 10.3f, 10.3f, 49.4f, 29.4f));
        RenderResult r = renderer.render(pdf(1, false), regions);
        BufferedImage img = decode(r.pngs().get(0));
        Rectangle rect = PageRenderer.toPixelRect(regions.get(0).box(), img.getWidth(), img.getHeight());
        assertTrue(rect.width > 0 && rect.height > 0);
        assertTrue(PageRenderer.verifyBlack(r.pngs().get(0), List.of(rect)));
        assertTrue(PageRenderer.verifyBlack(r.pngs().get(0), regions, 1));
        for (int y = rect.y; y < rect.y + rect.height; y++) {
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                assertEquals(0, img.getRGB(x, y) & 0xFFFFFF);
            }
        }
    }

    @Test
    void req34_pixelsOutsideRegionKeepOriginalContent() throws IOException {
        byte[] pdf = pdf(1, false);
        List<MaskRegion> regions = List.of(region(1, 100, 10, 40, 20));
        BufferedImage masked = decode(renderer.render(pdf, regions).pngs().get(0));
        BufferedImage plain = decode(renderer.render(pdf, List.of()).pngs().get(0));
        Rectangle rect = PageRenderer.toPixelRect(regions.get(0).box(), masked.getWidth(), masked.getHeight());
        int outside = 0;
        for (int y = 0; y < masked.getHeight(); y++) {
            for (int x = 0; x < masked.getWidth(); x++) {
                if (!rect.contains(x, y)) {
                    assertEquals(plain.getRGB(x, y), masked.getRGB(x, y));
                    outside++;
                }
            }
        }
        assertTrue(outside > 0);
        assertEquals(0xFFFFFF, masked.getRGB(masked.getWidth() - 1, masked.getHeight() - 1) & 0xFFFFFF);
        assertEquals(0x0000FF, masked.getRGB(px(30), px(25)) & 0xFFFFFF);
        // 가리지 않은 이미지에서는 같은 영역이 검정이 아니므로 검사가 실패해야 한다
        Rectangle blue = PageRenderer.toPixelRect(new Box(10, 10, 50, 30), plain.getWidth(), plain.getHeight());
        assertFalse(PageRenderer.verifyBlack(renderer.render(pdf, List.of()).pngs().get(0), List.of(blue)));
    }

    @Test
    void req35_annotationsAreNotDrawn() throws IOException {
        byte[] pdf = pdf(1, true);
        // 대조: 기본 렌더링에서는 주석 위치가 빨갛다
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            BufferedImage withAnnots = new PDFRenderer(doc).renderImageWithDPI(0, 150, ImageType.RGB);
            assertEquals(0xFF0000, withAnnots.getRGB(px(125), px(65)) & 0xFFFFFF);
        }
        BufferedImage img = decode(renderer.render(pdf, List.of()).pngs().get(0));
        assertEquals(0xFFFFFF, img.getRGB(px(125), px(65)) & 0xFFFFFF);
    }

    @Test
    void sameInputGivesSameHashAndDifferentRegionsDiffer() throws IOException {
        byte[] pdf = pdf(2, false);
        List<MaskRegion> regions = List.of(region(1, 10, 10, 30, 20), region(2, 20, 20, 30, 20));
        RenderResult a = renderer.render(pdf, regions);
        RenderResult b = renderer.render(pdf, regions);
        assertEquals(a.previewHash(), b.previewHash());
        assertTrue(a.previewHash().matches("[0-9a-f]{64}"));
        assertFalse(a.previewHash().equals(renderer.render(pdf, List.of()).previewHash()));
    }

    @Test
    void regionBeyondPageIsClippedWithoutException() throws IOException {
        List<MaskRegion> regions = List.of(
                region(1, 150, 60, 500, 500),
                region(1, -50, -50, 80, 80),
                region(1, 1000, 1000, 10, 10));
        RenderResult r = renderer.render(pdf(1, false), regions);
        BufferedImage img = decode(r.pngs().get(0));
        assertEquals(0, img.getRGB(img.getWidth() - 1, img.getHeight() - 1) & 0xFFFFFF);
        assertEquals(0, img.getRGB(0, 0) & 0xFFFFFF);
        assertTrue(PageRenderer.verifyBlack(r.pngs().get(0), regions, 1));
        Rectangle outside = PageRenderer.toPixelRect(regions.get(2).box(), img.getWidth(), img.getHeight());
        assertTrue(outside.isEmpty());
    }

    @Test
    void multiplePagesAreMaskedPerPage() throws IOException {
        List<MaskRegion> regions = List.of(region(2, 10, 10, 50, 30));
        RenderResult r = renderer.render(pdf(3, false), regions);
        assertEquals(3, r.pngs().size());
        BufferedImage p1 = decode(r.pngs().get(0));
        BufferedImage p2 = decode(r.pngs().get(1));
        BufferedImage p3 = decode(r.pngs().get(2));
        assertEquals(0x0000FF, p1.getRGB(px(30), px(25)) & 0xFFFFFF);
        assertEquals(0x000000, p2.getRGB(px(30), px(25)) & 0xFFFFFF);
        assertEquals(0x0000FF, p3.getRGB(px(30), px(25)) & 0xFFFFFF);
        assertEquals(1, PageRenderer.pixelRects(regions, 2, p2.getWidth(), p2.getHeight()).size());
        assertEquals(0, PageRenderer.pixelRects(regions, 1, p1.getWidth(), p1.getHeight()).size());
    }

    @Test
    void req44_verifyBlackDetectsNonBlackPixel() throws IOException {
        byte[] png = renderer.render(pdf(1, false), List.of()).pngs().get(0);
        BufferedImage img = decode(png);
        Rectangle whiteArea = new Rectangle(img.getWidth() - 10, img.getHeight() - 10, 10, 10);
        assertFalse(PageRenderer.verifyBlack(png, List.of(whiteArea)));
    }

    @Test
    void toPixelRectUsesFloorAndCeil() {
        Rectangle r = PageRenderer.toPixelRect(new Box(10.1f, 10.1f, 10f, 10f), 1000, 1000);
        double s = 150 / 72.0;
        assertEquals((int) Math.floor(10.1f * s), r.x);
        assertEquals((int) Math.ceil(20.1f * s), r.x + r.width);
    }
}
