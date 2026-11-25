package dev.kirothon.resume.demo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kirothon.resume.pdf.MaskRegionFinder;
import dev.kirothon.resume.pdf.PageRenderer;
import dev.kirothon.resume.pdf.PdfIngestor;
import dev.kirothon.resume.pdf.PdfTypes.IngestResult;
import dev.kirothon.resume.pdf.PdfTypes.MaskPlan;
import dev.kirothon.resume.pdf.PdfTypes.RegionType;
import dev.kirothon.resume.pdf.PdfTypes.RenderResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 요구사항 12: 미리 만든 데모 PDF가 실제 업로드 검사·마스킹 경로를 통과한다. 파일은 다시 쓰지 않는다. */
class DemoPdfTest {

    private final PdfIngestor ingestor = new PdfIngestor();
    private final MaskRegionFinder finder = new MaskRegionFinder();

    private static byte[] load(String name) throws IOException {
        try (InputStream in = DemoPdfTest.class.getResourceAsStream("/demo/" + name)) {
            assertThat(in).as("리소스 /demo/" + name).isNotNull();
            return in.readAllBytes();
        }
    }

    @ParameterizedTest(name = "12.x {0}")
    @CsvSource({
            "owner-v1.pdf, 김하늘",
            "owner-v2.pdf, 김하늘",
            "frontend-park.pdf, 박서준",
            "data-lee.pdf, 이지우"})
    void demoPdfIngestsAndMasks(String file, String name) throws IOException {
        byte[] pdf = load(file);
        assertThat(pdf.length).isLessThan(5 * 1024 * 1024);

        IngestResult ing = ingestor.ingest(pdf);
        assertThat(ing.pages()).hasSizeBetween(1, 2);
        assertThat(ing.warningPages()).as("한글 글자 매핑 경고 없음").isEmpty();
        assertThat(ing.images()).as("증명사진 이미지").isNotEmpty();

        MaskPlan plan = finder.find(ing.lines(), ing.images(), List.of(name));
        assertThat(plan.counts().get(RegionType.EMAIL)).isGreaterThanOrEqualTo(1);
        assertThat(plan.counts().get(RegionType.PHONE)).isGreaterThanOrEqualTo(1);
        assertThat(plan.counts().get(RegionType.URL)).isGreaterThanOrEqualTo(1);
        assertThat(plan.counts().get(RegionType.IMAGE)).isGreaterThanOrEqualTo(1);
        assertThat(plan.counts().get(RegionType.TERM)).as("이름 일치").isGreaterThanOrEqualTo(1);
        assertThat(plan.unmatchedTerms()).isEmpty();

        RenderResult rendered = new PageRenderer().render(pdf, plan.regions());
        assertThat(rendered.pngs()).hasSize(ing.pages().size());
        for (int p = 1; p <= ing.pages().size(); p++) {
            assertThat(PageRenderer.verifyBlack(rendered.pngs().get(p - 1), plan.regions(), p)).isTrue();
        }
        savePreviews(file, rendered);
    }

    @Test
    void ownerV1AndV2AreSamePersonWithDifferentLayout() throws IOException {
        IngestResult v1 = ingestor.ingest(load("owner-v1.pdf"));
        IngestResult v2 = ingestor.ingest(load("owner-v2.pdf"));
        String t1 = String.join("\n", v1.lines().stream().map(l -> l.text()).toList());
        String t2 = String.join("\n", v2.lines().stream().map(l -> l.text()).toList());
        assertThat(t1).contains("여러 프로젝트에 참여");
        assertThat(t2).contains("40% 단축").doesNotContain("여러 프로젝트에 참여");
        assertThat(t1).isNotEqualTo(t2);
    }

    /** 눈으로 확인하는 용도: build/demo-preview/ (커밋 대상 아님). */
    private static void savePreviews(String file, RenderResult rendered) throws IOException {
        Path dir = Path.of("build", "demo-preview");
        Files.createDirectories(dir);
        for (int i = 0; i < rendered.pngs().size(); i++) {
            Files.write(dir.resolve(file.replace(".pdf", "") + "-p" + (i + 1) + ".png"), rendered.pngs().get(i));
        }
    }
}
