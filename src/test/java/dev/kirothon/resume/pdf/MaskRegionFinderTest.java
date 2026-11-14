package dev.kirothon.resume.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kirothon.resume.pdf.PdfTypes.Box;
import dev.kirothon.resume.pdf.PdfTypes.ImageBox;
import dev.kirothon.resume.pdf.PdfTypes.LineText;
import dev.kirothon.resume.pdf.PdfTypes.MaskPlan;
import dev.kirothon.resume.pdf.PdfTypes.MaskRegion;
import dev.kirothon.resume.pdf.PdfTypes.RegionType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MaskRegionFinderTest {

    private static final float W = 10f;
    private static final float H = 12f;

    private final MaskRegionFinder finder = new MaskRegionFinder();

    /** 글자마다 폭 10pt 상자. 공백은 null (스트리퍼가 넣은 공백). */
    private static LineText line(int page, float y, String text) {
        List<Box> boxes = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            boxes.add(text.charAt(i) == ' ' ? null : new Box(i * W, y, W, H));
        }
        return new LineText(page, text, boxes);
    }

    private static LineText line(String text) {
        return line(1, 100f, text);
    }

    private MaskPlan find(String text, String... terms) {
        return finder.find(List.of(line(text)), List.of(), List.of(terms));
    }

    private static int count(MaskPlan plan, RegionType type) {
        return plan.counts().get(type);
    }

    // --- 이메일 / URL ---

    @ParameterizedTest
    @ValueSource(strings = {"hong.gildong+x@naver.com", "a_b@sub.example.co.kr"})
    void email_3_1(String email) {
        MaskPlan plan = find("연락 " + email + " 입니다");
        assertThat(plan.regions()).hasSize(1);
        MaskRegion r = plan.regions().get(0);
        assertThat(r.type()).isEqualTo(RegionType.EMAIL);
        int start = 3;
        assertThat(r.box().x()).isEqualTo(start * W - 1);
        assertThat(r.box().width()).isEqualTo(email.length() * W + 2);
        assertThat(r.box().y()).isEqualTo(99f);
        assertThat(r.box().height()).isEqualTo(H + 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://github.com/hong", "http://a.b/c?d=1", "www.example.com", "github.com/hong/repo",
            "blog.naver.co.kr/hong"})
    void url_3_1(String url) {
        MaskPlan plan = find("링크 " + url);
        assertThat(count(plan, RegionType.URL)).isEqualTo(1);
        assertThat(plan.regions()).hasSize(1);
        assertThat(plan.regions().get(0).box().width()).isEqualTo(url.length() * W + 2);
    }

    @Test
    void url_trailingPunctuationNotMasked() {
        MaskPlan plan = find("see https://x.com/a.");
        assertThat(plan.regions().get(0).box().width()).isEqualTo("https://x.com/a".length() * W + 2);
    }

    @Test
    void bareDomainWithoutPathIsNotUrl() {
        assertThat(find("Spring.io 와 Node.js 사용").regions()).isEmpty();
    }

    @Test
    void emailNotDuplicatedAsUrl() {
        MaskPlan plan = find("a@b.com www.b.com/x");
        assertThat(count(plan, RegionType.EMAIL)).isEqualTo(1);
        assertThat(count(plan, RegionType.URL)).isEqualTo(1);
    }

    // --- 전화 ---

    @ParameterizedTest
    @ValueSource(strings = {
            "010-1234-5678", "010.1234.5678", "010 1234 5678", "01012345678", "011-123-4567",
            "02-123-4567", "02 1234 5678", "031-123-4567", "031.123.4567",
            "(02) 123-4567", "+82-10-1234-5678", "+82 10 1234 5678", "+82 (0)10-1234-5678", "+82-2-123-4567"})
    void phone_3_1(String phone) {
        MaskPlan plan = find("전화 " + phone + " 끝");
        assertThat(count(plan, RegionType.PHONE)).as(phone).isEqualTo(1);
        assertThat(plan.regions()).hasSize(1);
        assertThat(plan.regions().get(0).box().width()).isEqualTo(phone.length() * W + 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2024-01-01", "2024.01.01", "2020-2024", "2020.03 - 2024.02", "20190101", "2019010123",
            "학번 20240101", "01-01-2024", "1234-5678", "123-4567-8901", "점수 100-200-3000",
            "v1.2.3", "2024-12-31 ~ 2025-01-31"})
    void notPhone_falsePositives(String text) {
        assertThat(find(text).regions()).as(text).isEmpty();
    }

    @Test
    void phoneInsideLongerDigitRunIsNotMatched() {
        assertThat(find("9010-1234-5678").regions()).isEmpty();
        assertThat(find("010-1234-56789").regions()).isEmpty();
    }

    @Test
    void containsContactPattern_2_12() {
        assertThat(finder.containsContactPattern("백엔드 a@b.com")).isTrue();
        assertThat(finder.containsContactPattern("010-1234-5678")).isTrue();
        assertThat(finder.containsContactPattern("https://x.com")).isTrue();
        assertThat(finder.containsContactPattern("www.x.com")).isTrue();
        assertThat(finder.containsContactPattern("백엔드 개발자 (2024-01-01)")).isFalse();
        assertThat(finder.containsContactPattern("프론트엔드")).isFalse();
        assertThat(finder.containsContactPattern("")).isFalse();
        assertThat(finder.containsContactPattern(null)).isFalse();
    }

    // --- 가릴 단어 ---

    @Test
    void term_caseInsensitive_3_3() {
        MaskPlan plan = find("Name: HONG Gildong", "hong");
        assertThat(plan.regions()).hasSize(1);
        assertThat(plan.regions().get(0).type()).isEqualTo(RegionType.TERM);
        assertThat(plan.regions().get(0).box().x()).isEqualTo(6 * W - 1);
        assertThat(plan.regions().get(0).box().width()).isEqualTo(4 * W + 2);
    }

    @Test
    void term_spacesBetweenLetters_nullBoxesSkipped() {
        // "홍 길 동": 공백 null 상자는 합집합에서 제외, 영역은 홍~동 글자 상자 합
        MaskPlan plan = find("홍 길 동 입니다", "홍길동");
        assertThat(plan.regions()).hasSize(1);
        Box b = plan.regions().get(0).box();
        assertThat(b.x()).isEqualTo(-1f);
        assertThat(b.width()).isEqualTo(5 * W + 2);
    }

    @Test
    void term_termItselfContainingSpacesMatchesCompact() {
        assertThat(find("홍길동", "홍 길동").regions()).hasSize(1);
    }

    @Test
    void term_longerFirstThenContainedIsNotDuplicated() {
        MaskPlan plan = find("홍길동 입니다", "홍길", "홍길동");
        assertThat(plan.regions()).hasSize(1);
        assertThat(plan.regions().get(0).box().width()).isEqualTo(3 * W + 2);
        assertThat(plan.unmatchedTerms()).isEmpty();
        assertThat(count(plan, RegionType.TERM)).isEqualTo(1);
    }

    @Test
    void term_multipleOccurrencesAcrossLines() {
        MaskPlan plan = finder.find(
                List.of(line(1, 10f, "ACME 근무"), line(2, 10f, "acme 인턴 Acme")), List.of(), List.of("Acme"));
        assertThat(plan.regions()).hasSize(3);
        assertThat(count(plan, RegionType.TERM)).isEqualTo(3);
    }

    @Test
    void term_overlappingPartially_bothKept() {
        MaskPlan plan = find("abcdef", "abcd", "cdef");
        assertThat(count(plan, RegionType.TERM)).isEqualTo(2);
    }

    @Test
    void term_regexMetaCharsAreLiteral() {
        MaskPlan plan = find("C++ and a.b", "c++", "a.b");
        assertThat(count(plan, RegionType.TERM)).isEqualTo(2);
        assertThat(find("axb", "a.b").regions()).isEmpty();
    }

    @Test
    void term_noMatch_3_7() {
        MaskPlan plan = find("아무 내용", "없는단어", "내용");
        assertThat(plan.unmatchedTerms()).containsExactly("없는단어");
        assertThat(count(plan, RegionType.TERM)).isEqualTo(1);
    }

    @Test
    void term_blankAndNullIgnored() {
        MaskPlan plan = finder.find(List.of(line("abc")), List.of(), Arrays.asList(" ", "", null));
        assertThat(plan.regions()).isEmpty();
        assertThat(plan.unmatchedTerms()).isEmpty();
    }

    @Test
    void term_insideEmail_bothRegionsKept() {
        MaskPlan plan = find("hong@x.com", "hong");
        // 단어 영역이 이메일 전체를 덮지 않으므로 이메일 영역은 따로 유지된다.
        assertThat(count(plan, RegionType.TERM)).isEqualTo(1);
        assertThat(count(plan, RegionType.EMAIL)).isEqualTo(1);
    }

    @Test
    void sameEmailTwice_twoRegions() {
        MaskPlan plan = find("a@b.com a@b.com");
        assertThat(count(plan, RegionType.EMAIL)).isEqualTo(2);
    }

    // --- 일치 없음 / 이미지 / counts ---

    @Test
    void noMatch_empty() {
        MaskPlan plan = find("아무 개인정보 없는 문장");
        assertThat(plan.regions()).isEmpty();
        assertThat(plan.unmatchedTerms()).isEmpty();
        assertThat(plan.counts().values()).allMatch(v -> v == 0);
    }

    @Test
    void images_3_2() {
        Box box = new Box(50, 60, 100, 120);
        MaskPlan plan = finder.find(List.of(line("본문")), List.of(new ImageBox(2, box), new ImageBox(1, box)), List.of());
        assertThat(plan.regions()).containsExactly(
                new MaskRegion(2, RegionType.IMAGE, box), new MaskRegion(1, RegionType.IMAGE, box));
        assertThat(count(plan, RegionType.IMAGE)).isEqualTo(2);
    }

    @Test
    void counts_perType() {
        MaskPlan plan = finder.find(
                List.of(line("홍길동"), line(1, 120f, "a@b.com 010-1234-5678 www.x.com"), line(1, 140f, "Acme Acme")),
                List.of(new ImageBox(1, new Box(0, 0, 10, 10))),
                List.of("홍길동", "Acme"));
        assertThat(count(plan, RegionType.TERM)).isEqualTo(3);
        assertThat(count(plan, RegionType.EMAIL)).isEqualTo(1);
        assertThat(count(plan, RegionType.PHONE)).isEqualTo(1);
        assertThat(count(plan, RegionType.URL)).isEqualTo(1);
        assertThat(count(plan, RegionType.IMAGE)).isEqualTo(1);
        assertThat(count(plan, RegionType.MANUAL)).isZero();
        assertThat(plan.regions()).hasSize(7);
    }

    @Test
    void regionStaysOnItsPage() {
        MaskPlan plan = finder.find(List.of(line(3, 10f, "a@b.com")), List.of(), List.of());
        assertThat(plan.regions().get(0).page()).isEqualTo(3);
    }

    // --- 이름 후보 ---

    @Test
    void nameCandidate_firstNonBlankLineUncovered_3_8() {
        MaskPlan plan = finder.find(List.of(line(" "), line(1, 20f, "홍길동"), line(1, 40f, "백엔드")), List.of(), List.of());
        assertThat(plan.nameCandidate()).isEqualTo("홍길동");
    }

    @Test
    void nameCandidate_nullWhenFirstLineFullyCovered() {
        MaskPlan plan = finder.find(List.of(line("홍길동"), line(1, 40f, "백엔드")), List.of(), List.of("홍길동"));
        assertThat(plan.nameCandidate()).isNull();
    }

    @Test
    void nameCandidate_partiallyCoveredLineIsCandidate() {
        MaskPlan plan = find("홍길동 a@b.com");
        assertThat(plan.nameCandidate()).isEqualTo("홍길동 a@b.com");
    }

    @Test
    void nameCandidate_coveredByImage() {
        MaskPlan plan = finder.find(List.of(line("홍길동")), List.of(new ImageBox(1, new Box(0, 90, 100, 40))), List.of());
        assertThat(plan.nameCandidate()).isNull();
    }

    @Test
    void nameCandidate_truncatedTo30() {
        MaskPlan plan = find("가".repeat(45));
        assertThat(plan.nameCandidate()).isEqualTo("가".repeat(30));
    }

    @Test
    void nameCandidate_noLines() {
        MaskPlan plan = finder.find(List.of(), List.of(), List.of());
        assertThat(plan.nameCandidate()).isNull();
        assertThat(plan.regions()).isEmpty();
    }
}
