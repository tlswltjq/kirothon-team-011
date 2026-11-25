package dev.kirothon.resume.pdf;

import java.util.List;
import java.util.Map;

/**
 * 작업 2(PdfIngestor)·3(MaskRegionFinder)·4(PageRenderer)가 공유하는 타입 규약.
 * 좌표 단위는 PDF 포인트(1/72인치), 원점은 페이지 왼쪽 위, y는 아래로 증가한다.
 * 이 파일의 시그니처를 바꾸려면 세 작업 모두에 알리고 함께 바꾼다.
 */
public final class PdfTypes {

    private PdfTypes() {
    }

    /** 사각형(포인트). */
    public record Box(float x, float y, float width, float height) {
        public Box {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("negative size");
            }
        }

        public Box union(Box other) {
            float minX = Math.min(x, other.x);
            float minY = Math.min(y, other.y);
            float maxX = Math.max(x + width, other.x + other.width);
            float maxY = Math.max(y + height, other.y + other.height);
            return new Box(minX, minY, maxX - minX, maxY - minY);
        }

        public Box expand(float margin) {
            return new Box(x - margin, y - margin, width + 2 * margin, height + 2 * margin);
        }

        public boolean contains(Box other) {
            return other.x >= x && other.y >= y
                    && other.x + other.width <= x + width
                    && other.y + other.height <= y + height;
        }
    }

    /** 페이지 크기(포인트). page는 1부터. */
    public record PageSize(int page, float width, float height) {
    }

    /**
     * 한 줄 텍스트. charBoxes.size() == text.length() 이고,
     * 스트리퍼가 넣은 공백처럼 위치가 없는 글자는 null 이다.
     */
    public record LineText(int page, String text, List<Box> charBoxes) {
        public LineText {
            if (charBoxes.size() != text.length()) {
                throw new IllegalArgumentException("charBoxes must match text length");
            }
        }
    }

    /** 페이지에 그려진 래스터 이미지의 표시 영역. */
    public record ImageBox(int page, Box box) {
    }

    /** PdfIngestor 결과. warningPages: 글자 매핑 불가(2.13) 경고 페이지. */
    public record IngestResult(List<PageSize> pages, List<LineText> lines, List<ImageBox> images,
                               List<Integer> warningPages) {
    }

    public enum RegionType {
        EMAIL, URL, PHONE, TERM, IMAGE, MANUAL
    }

    /** 검게 칠할 영역(포인트). */
    public record MaskRegion(int page, RegionType type, Box box) {
    }

    /**
     * MaskRegionFinder 결과.
     * counts: 유형별 영역 수, unmatchedTerms: 한 번도 일치하지 않은 가릴 단어,
     * nameCandidate: 가려지지 않은 첫 줄(이름 후보, 최대 30자) 또는 null.
     */
    public record MaskPlan(List<MaskRegion> regions, Map<RegionType, Integer> counts,
                           List<String> unmatchedTerms, String nameCandidate) {
    }

    /** PageRenderer 결과. pngs는 페이지 순서, previewHash는 SHA-256(png1‖png2‖…) hex. */
    public record RenderResult(List<byte[]> pngs, String previewHash) {
    }

    /** 업로드 검사 실패. code는 design.md 오류 표의 안정적인 코드(NOT_PDF 등), status는 HTTP 상태. */
    public static class PdfRejectedException extends RuntimeException {
        private final int status;
        private final String code;

        public PdfRejectedException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        public int status() {
            return status;
        }

        public String code() {
            return code;
        }
    }
}
