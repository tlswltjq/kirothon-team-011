package dev.kirothon.resume.pdf;

import dev.kirothon.resume.pdf.PdfTypes.Box;
import dev.kirothon.resume.pdf.PdfTypes.ImageBox;
import dev.kirothon.resume.pdf.PdfTypes.IngestResult;
import dev.kirothon.resume.pdf.PdfTypes.LineText;
import dev.kirothon.resume.pdf.PdfTypes.PageSize;
import dev.kirothon.resume.pdf.PdfTypes.PdfRejectedException;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

/** PDF 검사와 좌표 포함 텍스트·이미지 위치 추출(요구사항 2). 20초 제한은 호출하는 쪽(작업 5)이 감싼다. */
@Component
public class PdfIngestor {

    static final int MAX_PAGES = 10;
    static final int MIN_TEXT_CHARS = 100;
    static final long MAX_IMAGE_PIXELS = 25_000_000L;
    /** 150DPI 렌더링 결과의 변 길이와 전체 픽셀 상한. 렌더링 전에 막아 메모리 고갈을 피한다. */
    static final double MAX_PAGE_SIDE_PX = 5000;
    static final double MAX_TOTAL_PAGE_PIXELS = 25_000_000d;
    private static final byte[] MAGIC = {'%', 'P', 'D', 'F', '-'};

    public IngestResult ingest(byte[] pdf) {
        if (pdf == null || pdf.length < MAGIC.length) {
            throw notPdf();
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (pdf[i] != MAGIC[i]) {
                throw notPdf();
            }
        }
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            if (doc.isEncrypted()) {
                throw encrypted();
            }
            int pageCount = doc.getNumberOfPages();
            if (pageCount > MAX_PAGES) {
                throw new PdfRejectedException(422, "TOO_MANY_PAGES", "PDF는 10쪽 이하여야 합니다.");
            }
            if (pageCount == 0) {
                throw parseFailed();
            }
            List<PageSize> pages = new ArrayList<>();
            double totalPixels = 0;
            for (int i = 0; i < pageCount; i++) {
                PDPage page = doc.getPage(i);
                if (page.getRotation() != 0) {
                    throw new PdfRejectedException(422, "UNSUPPORTED_PAGE_ROTATION",
                            (i + 1) + "쪽이 회전되어 있습니다. 회전 없이 다시 저장해서 올려 주세요.");
                }
                PDRectangle box = page.getCropBox();
                double wPx = box.getWidth() * PageRenderer.SCALE;
                double hPx = box.getHeight() * PageRenderer.SCALE;
                totalPixels += wPx * hPx;
                if (!(wPx > 0) || !(hPx > 0) || wPx > MAX_PAGE_SIDE_PX || hPx > MAX_PAGE_SIDE_PX
                        || totalPixels > MAX_TOTAL_PAGE_PIXELS) {
                    throw new PdfRejectedException(422, "PDF_TOO_COMPLEX",
                            (i + 1) + "쪽의 페이지 크기가 너무 커서 처리할 수 없습니다.");
                }
                pages.add(new PageSize(i + 1, box.getWidth(), box.getHeight()));
            }

            LineCollector collector = new LineCollector();
            collector.setSortByPosition(true);
            collector.getText(doc);
            List<LineText> lines = collector.lines;

            long nonSpace = lines.stream().flatMap(l -> l.text().chars().boxed())
                    .filter(c -> !Character.isWhitespace(c)).count();
            if (nonSpace < MIN_TEXT_CHARS) {
                throw new PdfRejectedException(422, "NO_TEXT_LAYER",
                        "텍스트를 읽을 수 없습니다. 스캔본은 자동 마스킹을 할 수 없어 지원하지 않습니다. "
                                + "텍스트가 선택되는 PDF로 다시 저장해서 올려 주세요.");
            }

            List<ImageBox> images = new ArrayList<>();
            for (int i = 0; i < pageCount; i++) {
                new ImageCollector(doc.getPage(i), i + 1, images).processPage(doc.getPage(i));
            }
            return new IngestResult(List.copyOf(pages), List.copyOf(lines), List.copyOf(images),
                    List.copyOf(collector.warningPages));
        } catch (InvalidPasswordException e) {
            throw encrypted();
        } catch (PdfRejectedException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw parseFailed();
        }
    }

    private static PdfRejectedException notPdf() {
        return new PdfRejectedException(400, "NOT_PDF", "PDF 파일이 아닙니다.");
    }

    private static PdfRejectedException encrypted() {
        return new PdfRejectedException(422, "ENCRYPTED_PDF", "암호화된 PDF입니다. 암호를 해제한 PDF를 올려 주세요.");
    }

    private static PdfRejectedException parseFailed() {
        return new PdfRejectedException(422, "PDF_PARSE_FAILED", "PDF를 해석할 수 없습니다.");
    }

    /** 줄마다 LineText를 만든다. 단어 사이 공백은 상자 null. */
    private static final class LineCollector extends PDFTextStripper {
        final List<LineText> lines = new ArrayList<>();
        final TreeSet<Integer> warningPages = new TreeSet<>();
        private final StringBuilder text = new StringBuilder();
        private final List<Box> boxes = new ArrayList<>();

        LineCollector() throws IOException {
            super();
        }

        @Override
        protected void writeString(String string, List<TextPosition> positions) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                throw new IOException("cancelled");
            }
            for (TextPosition tp : positions) {
                String uni = tp.getUnicode();
                boolean bad = uni == null || uni.isEmpty();
                if (!bad) {
                    for (int i = 0; i < uni.length(); i++) {
                        char c = uni.charAt(i);
                        if (c == '\uFFFD' || (c >= '\uE000' && c <= '\uF8FF')) {
                            bad = true;
                        }
                    }
                }
                if (bad) {
                    warningPages.add(getCurrentPageNo());
                    uni = "\uFFFD";
                }
                float h = tp.getHeightDir();
                Box box = new Box(tp.getXDirAdj(), tp.getYDirAdj() - h * 1.2f,
                        Math.max(0f, tp.getWidthDirAdj()), Math.max(0f, h * 1.4f));
                for (int i = 0; i < uni.length(); i++) {
                    text.append(uni.charAt(i));
                    boxes.add(box);
                }
            }
        }

        @Override
        protected void writeWordSeparator() throws IOException {
            if (text.length() > 0) {
                text.append(' ');
                boxes.add(null);
            }
        }

        @Override
        protected void writeLineSeparator() throws IOException {
            flush();
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            flush();
        }

        private void flush() {
            // 줄 끝의 스트리퍼 공백은 버린다.
            int end = text.length();
            while (end > 0 && boxes.get(end - 1) == null) {
                end--;
            }
            if (end > 0) {
                lines.add(new LineText(getCurrentPageNo(), text.substring(0, end),
                        new ArrayList<>(boxes.subList(0, end))));
            }
            text.setLength(0);
            boxes.clear();
        }
    }

    /** drawImage의 CTM으로 이미지 표시 영역(왼쪽 위 원점)을 구한다. */
    private static final class ImageCollector extends PDFGraphicsStreamEngine {
        private final int pageNo;
        private final PDRectangle crop;
        private final List<ImageBox> out;

        ImageCollector(PDPage page, int pageNo, List<ImageBox> out) {
            super(page);
            this.pageNo = pageNo;
            this.crop = page.getCropBox();
            this.out = out;
        }

        @Override
        public void drawImage(PDImage image) throws IOException {
            if ((long) image.getWidth() * image.getHeight() > MAX_IMAGE_PIXELS) {
                throw new PdfRejectedException(422, "PDF_TOO_COMPLEX", "PDF에 너무 큰 이미지가 있습니다.");
            }
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
            float[][] corners = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (float[] c : corners) {
                Point2D.Float p = ctm.transformPoint(c[0], c[1]);
                minX = Math.min(minX, p.x);
                maxX = Math.max(maxX, p.x);
                minY = Math.min(minY, p.y);
                maxY = Math.max(maxY, p.y);
            }
            out.add(new ImageBox(pageNo, new Box(minX - crop.getLowerLeftX(),
                    crop.getUpperRightY() - maxY, maxX - minX, maxY - minY)));
        }

        @Override public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) { }
        @Override public void clip(int windingRule) { }
        @Override public void moveTo(float x, float y) { }
        @Override public void lineTo(float x, float y) { }
        @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) { }
        @Override public Point2D getCurrentPoint() { return new Point2D.Float(); }
        @Override public void closePath() { }
        @Override public void endPath() { }
        @Override public void strokePath() { }
        @Override public void fillPath(int windingRule) { }
        @Override public void fillAndStrokePath(int windingRule) { }
        @Override public void shadingFill(org.apache.pdfbox.cos.COSName shadingName) { }
    }
}
