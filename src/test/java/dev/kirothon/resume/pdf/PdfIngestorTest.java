package dev.kirothon.resume.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kirothon.resume.pdf.PdfFixtures.ImageAt;
import dev.kirothon.resume.pdf.PdfFixtures.PageSpec;
import dev.kirothon.resume.pdf.PdfFixtures.TextAt;
import dev.kirothon.resume.pdf.PdfTypes.Box;
import dev.kirothon.resume.pdf.PdfTypes.IngestResult;
import dev.kirothon.resume.pdf.PdfTypes.LineText;
import dev.kirothon.resume.pdf.PdfTypes.PdfRejectedException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PdfIngestorTest {

    private final PdfIngestor ingestor = new PdfIngestor();

    private void assertRejected(byte[] pdf, int status, String code) {
        assertThatThrownBy(() -> ingestor.ingest(pdf))
                .isInstanceOfSatisfying(PdfRejectedException.class, e -> {
                    assertThat(e.status()).isEqualTo(status);
                    assertThat(e.code()).isEqualTo(code);
                    assertThat(e.getMessage()).isNotBlank();
                });
    }

    @Test
    void req2_3_notPdfRejectedByMagicBytes() {
        assertRejected("hello, not a pdf".getBytes(), 400, "NOT_PDF");
        assertRejected(new byte[0], 400, "NOT_PDF");
        assertRejected("%PDF".getBytes(), 400, "NOT_PDF");
    }

    @Test
    void req2_8_brokenPdfIsParseFailed() {
        assertRejected("%PDF-1.4\nthis is garbage".getBytes(), 422, "PDF_PARSE_FAILED");
    }

    @Test
    void req2_6_encryptedPdfRejected() throws IOException {
        assertRejected(PdfFixtures.encrypted(), 422, "ENCRYPTED_PDF");
    }

    @Test
    void req2_5_elevenPagesRejectedTenAccepted() throws IOException {
        assertRejected(PdfFixtures.pages(11), 422, "TOO_MANY_PAGES");
        assertThat(ingestor.ingest(PdfFixtures.pages(10)).pages()).hasSize(10);
    }

    @Test
    void req2_10_rotatedPageRejected() throws IOException {
        assertRejected(PdfFixtures.rotated(), 422, "UNSUPPORTED_PAGE_ROTATION");
    }

    @Test
    void req2_7_noTextLayerRejected() throws IOException {
        assertRejected(PdfFixtures.imageOnly(), 422, "NO_TEXT_LAYER");
        assertRejected(PdfFixtures.build(PageSpec.text("too short")), 422, "NO_TEXT_LAYER");
    }

    @Test
    void req2_9_hugeImageIsTooComplex() throws IOException {
        assertRejected(PdfFixtures.hugeImage(), 422, "PDF_TOO_COMPLEX");
    }

    @Test
    void req2_2_lineTextAndCharBoxesMatch() throws IOException {
        IngestResult r = ingestor.ingest(PdfFixtures.longText());
        assertThat(r.pages()).hasSize(1);
        assertThat(r.pages().get(0).width()).isEqualTo(PdfFixtures.PAGE_W);
        assertThat(r.pages().get(0).height()).isEqualTo(PdfFixtures.PAGE_H);
        assertThat(r.warningPages()).isEmpty();
        assertThat(r.lines()).hasSize(1);
        LineText line = r.lines().get(0);
        assertThat(line.page()).isEqualTo(1);
        assertThat(line.text()).isEqualTo(PdfFixtures.LONG_TEXT);
        assertThat(line.charBoxes()).hasSize(line.text().length());
        for (int i = 0; i < line.text().length(); i++) {
            if (line.text().charAt(i) != ' ') {
                assertThat(line.charBoxes().get(i)).as("char %d", i).isNotNull();
            }
        }
        // 12pt 글꼴, 기준선 y=700 (왼쪽 위 원점으로 792-700=92). 첫 글자 'E'는 x=50에서 시작.
        Box first = line.charBoxes().get(0);
        assertThat(first.x()).isCloseTo(50f, org.assertj.core.data.Offset.offset(0.5f));
        assertThat(first.y()).isLessThan(92f);
        assertThat(first.y() + first.height()).isGreaterThan(92f);
        // 글자 상자는 왼쪽에서 오른쪽으로 놓이고, 'x' 위치가 Helvetica 폭표와 맞는다.
        Box prev = null;
        for (Box b : line.charBoxes()) {
            if (b == null) {
                continue;
            }
            if (prev != null) {
                assertThat(b.x()).isGreaterThanOrEqualTo(prev.x());
            }
            prev = b;
        }
        // 'E'(667/1000*12) 다음 글자 'x'는 x = 50 + 8.004
        assertThat(line.charBoxes().get(1).x()).isCloseTo(58.0f, org.assertj.core.data.Offset.offset(0.5f));
    }

    @Test
    void req2_2_multiPageLinesKeepPageNumbers() throws IOException {
        byte[] pdf = PdfFixtures.build(PageSpec.text(PdfFixtures.LONG_TEXT),
                new PageSpec(List.of(new TextAt("Second page line number two goes on and on for a good while to reach length enough",
                        100, 500, 12)), List.of(), 0));
        IngestResult r = ingestor.ingest(pdf);
        assertThat(r.lines()).extracting(LineText::page).containsExactly(1, 2);
        Box b = r.lines().get(1).charBoxes().get(0);
        assertThat(b.x()).isCloseTo(100f, org.assertj.core.data.Offset.offset(0.5f));
    }

    @Test
    void req2_9_imageBoxPositionTopLeftOrigin() throws IOException {
        byte[] pdf = PdfFixtures.build(new PageSpec(
                List.of(new TextAt(PdfFixtures.LONG_TEXT, 50, 700, 12)),
                List.of(new ImageAt(100, 300, 150, 80)), 0));
        IngestResult r = ingestor.ingest(pdf);
        assertThat(r.images()).hasSize(1);
        assertThat(r.images().get(0).page()).isEqualTo(1);
        Box b = r.images().get(0).box();
        assertThat(b.x()).isCloseTo(100f, org.assertj.core.data.Offset.offset(0.01f));
        assertThat(b.y()).isCloseTo(792f - 380f, org.assertj.core.data.Offset.offset(0.01f));
        assertThat(b.width()).isCloseTo(150f, org.assertj.core.data.Offset.offset(0.01f));
        assertThat(b.height()).isCloseTo(80f, org.assertj.core.data.Offset.offset(0.01f));
    }

    @Test
    void req2_13_unmappableGlyphsWarnPage() throws IOException {
        IngestResult r = ingestor.ingest(PdfFixtures.unmappableText());
        assertThat(r.warningPages()).containsExactly(1);
    }
}
