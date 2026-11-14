package dev.kirothon.resume.pdf;

import dev.kirothon.resume.pdf.PdfTypes.Box;
import dev.kirothon.resume.pdf.PdfTypes.ImageBox;
import dev.kirothon.resume.pdf.PdfTypes.LineText;
import dev.kirothon.resume.pdf.PdfTypes.MaskPlan;
import dev.kirothon.resume.pdf.PdfTypes.MaskRegion;
import dev.kirothon.resume.pdf.PdfTypes.RegionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 줄 텍스트와 이미지 상자에서 마스킹 영역을 계산하는 상태 없는 순수 함수 (요구사항 3). */
@Component
public class MaskRegionFinder {

    static final float MARGIN = 1f;
    static final int NAME_CANDIDATE_MAX = 30;

    private static final String URL_CHARS = "[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]";

    static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)+");

    static final Pattern URL = Pattern.compile(
            "(?:https?://|www\\.)" + URL_CHARS + "+"
                    + "|(?<![\\w@.\\-])(?:[A-Za-z0-9\\-]+\\.)+(?:co\\.kr|com|net|org|io|dev|me|kr)/"
                    + URL_CHARS + "*",
            Pattern.CASE_INSENSITIVE);

    private static final String SEP = "[-. ]";
    private static final String PHONE_BODY = "\\d{3,4}" + SEP + "?\\d{4}";

    static final Pattern PHONE = Pattern.compile(
            "(?<![\\d+])(?:"
                    // +82 (국가번호), 선택적 (0)
                    + "\\+82" + SEP + "?(?:\\(0\\))?" + "(?:1[016789]|\\d{1,2})" + SEP + "?" + PHONE_BODY
                    // 휴대폰: 구분자 선택
                    + "|01[016789]" + SEP + "?" + PHONE_BODY
                    // 지역번호: 구분자 필수 (날짜·학번 오탐 방지)
                    + "|0\\d{1,2}" + SEP + "\\d{3,4}" + SEP + "\\d{4}"
                    // (02) 123-4567
                    + "|\\(0\\d{1,2}\\) ?\\d{3,4}" + SEP + "\\d{4}"
                    + ")(?!\\d)");

    /** 지원 직무 검사(요구사항 2.12): 이메일·URL·전화 패턴이 하나라도 있으면 true. */
    public boolean containsContactPattern(String text) {
        if (text == null) {
            return false;
        }
        return EMAIL.matcher(text).find() || URL.matcher(text).find() || PHONE.matcher(text).find();
    }

    public MaskPlan find(List<LineText> lines, List<ImageBox> images, List<String> terms) {
        List<MaskRegion> regions = new ArrayList<>();

        // 1. 가릴 단어: 긴 단어 우선
        Set<String> cleaned = new LinkedHashSet<>();
        if (terms != null) {
            for (String t : terms) {
                String s = t == null ? "" : t.replaceAll("\\s+", "");
                if (!s.isEmpty()) {
                    cleaned.add(s);
                }
            }
        }
        List<String> byLength = new ArrayList<>(cleaned);
        byLength.sort(Comparator.comparingInt(String::length).reversed());
        Set<String> matchedTerms = new LinkedHashSet<>();
        for (String term : byLength) {
            Pattern p = termPattern(term);
            for (LineText line : lines) {
                if (scan(line, p, RegionType.TERM, regions)) {
                    matchedTerms.add(term);
                }
            }
        }
        List<String> unmatched = new ArrayList<>();
        if (terms != null) {
            for (String t : terms) {
                String s = t == null ? "" : t.replaceAll("\\s+", "");
                if (!s.isEmpty() && !matchedTerms.contains(s) && !unmatched.contains(t)) {
                    unmatched.add(t);
                }
            }
        }

        // 2. 이메일 -> URL -> 전화
        for (LineText line : lines) {
            scan(line, EMAIL, RegionType.EMAIL, regions);
        }
        for (LineText line : lines) {
            scan(line, URL, RegionType.URL, regions);
        }
        for (LineText line : lines) {
            scan(line, PHONE, RegionType.PHONE, regions);
        }

        // 3. 이미지
        if (images != null) {
            for (ImageBox img : images) {
                regions.add(new MaskRegion(img.page(), RegionType.IMAGE, img.box()));
            }
        }

        Map<RegionType, Integer> counts = new EnumMap<>(RegionType.class);
        for (RegionType type : RegionType.values()) {
            counts.put(type, 0);
        }
        for (MaskRegion r : regions) {
            counts.merge(r.type(), 1, Integer::sum);
        }

        return new MaskPlan(List.copyOf(regions), counts, unmatched, nameCandidate(lines, regions));
    }

    private static Pattern termPattern(String term) {
        StringBuilder sb = new StringBuilder();
        term.codePoints().forEach(cp -> {
            if (sb.length() > 0) {
                sb.append("\\s*");
            }
            sb.append(Pattern.quote(new String(Character.toChars(cp))));
        });
        return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** 줄에서 일치를 찾아 영역을 추가한다. 일치가 하나라도 있으면 true(중복 영역이라도). */
    private static boolean scan(LineText line, Pattern pattern, RegionType type, List<MaskRegion> regions) {
        Matcher m = pattern.matcher(line.text());
        boolean found = false;
        while (m.find()) {
            int start = m.start();
            int end = trimEnd(line.text(), m.end(), type);
            if (end <= start) {
                continue;
            }
            found = true;
            Box union = null;
            for (int i = start; i < end; i++) {
                Box b = line.charBoxes().get(i);
                if (b != null) {
                    union = union == null ? b : union.union(b);
                }
            }
            if (union == null) {
                continue;
            }
            Box box = union.expand(MARGIN);
            if (!covered(regions, line.page(), box)) {
                regions.add(new MaskRegion(line.page(), type, box));
            }
        }
        return found;
    }

    private static int trimEnd(String text, int end, RegionType type) {
        if (type != RegionType.URL) {
            return end;
        }
        while (end > 0 && ".,;:!'\")".indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return end;
    }

    private static boolean covered(List<MaskRegion> regions, int page, Box box) {
        for (MaskRegion r : regions) {
            if (r.page() == page && r.box().contains(box)) {
                return true;
            }
        }
        return false;
    }

    private static String nameCandidate(List<LineText> lines, List<MaskRegion> regions) {
        for (LineText line : lines) {
            if (line.text().isBlank()) {
                continue;
            }
            for (int i = 0; i < line.text().length(); i++) {
                Box b = line.charBoxes().get(i);
                if (b != null && !Character.isWhitespace(line.text().charAt(i))
                        && !covered(regions, line.page(), b)) {
                    String t = line.text().strip();
                    return t.length() > NAME_CANDIDATE_MAX ? t.substring(0, NAME_CANDIDATE_MAX) : t;
                }
            }
            return null;
        }
        return null;
    }
}
