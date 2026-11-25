package dev.kirothon.resume.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.Deflater;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** 테스트용 PDF 생성 도우미. 쪽 크기는 US Letter(612x792pt), 영문 Standard14 글꼴. */
final class PdfFixtures {

    static final float PAGE_W = 612f;
    static final float PAGE_H = 792f;
    static final String LONG_TEXT =
            "Experienced backend engineer with ten years of building reliable services and mentoring teams across several companies.";

    private PdfFixtures() {
    }

    /** 영역: 텍스트 줄(왼쪽 아래 기준 x, y)과 이미지. */
    record TextAt(String text, float x, float y, float size) {
    }

    record ImageAt(float x, float y, float w, float h) {
    }

    record PageSpec(List<TextAt> texts, List<ImageAt> images, int rotation) {
        static PageSpec text(String text) {
            return new PageSpec(List.of(new TextAt(text, 50, 700, 12)), List.of(), 0);
        }
    }

    static byte[] build(PageSpec... specs) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            fill(doc, specs);
            return save(doc);
        }
    }

    /** 글자가 있는 정상 PDF지만 페이지 한 장이 30000x30000pt. */
    static byte[] hugePage() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(30000f, 30000f));
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(LONG_TEXT);
                cs.endText();
            }
            return save(doc);
        }
    }

    static byte[] longText() throws IOException {
        return build(PageSpec.text(LONG_TEXT));
    }

    static byte[] pages(int n) throws IOException {
        PageSpec[] specs = new PageSpec[n];
        for (int i = 0; i < n; i++) {
            specs[i] = PageSpec.text(LONG_TEXT);
        }
        return build(specs);
    }

    static byte[] encrypted() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            fill(doc, PageSpec.text(LONG_TEXT));
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("owner-pw", "user-pw", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            return save(doc);
        }
    }

    static byte[] rotated() throws IOException {
        return build(new PageSpec(List.of(new TextAt(LONG_TEXT, 50, 700, 12)), List.of(), 90));
    }

    /** 이미지만 있고 텍스트 층이 없는 쪽(스캔본 모양). */
    static byte[] imageOnly() throws IOException {
        return build(new PageSpec(List.of(), List.of(new ImageAt(50, 100, 200, 100)), 0));
    }

    /** 가로x세로 픽셀이 큰 1비트 회색 이미지를 작은 크기로 그린다. */
    static byte[] hugeImage() throws IOException {
        return buildHuge();
    }

    /** 글자 하나가 글리프 이름 없는 인코딩을 쓰는 글꼴 줄(유니코드 변환 불가)과 일반 줄을 가진 PDF. */
    static byte[] unmappableText() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(PAGE_W, PAGE_H));
            doc.addPage(page);
            // ToUnicode가 코드 0x41을 사설 영역 문자 U+E000으로 매핑하는 글꼴(2.13 경고 대상).
            String cmap = "/CIDInit /ProcSet findresource begin 12 dict begin begincmap\n"
                    + "/CMapName /Adobe-Identity-UCS def /CMapType 2 def\n"
                    + "1 begincodespacerange <00> <FF> endcodespacerange\n"
                    + "1 beginbfchar <41> <E000> endbfchar\n"
                    + "endcmap CMapName currentdict /CMap defineresource pop end end\n";
            org.apache.pdfbox.pdmodel.common.PDStream toUni = new org.apache.pdfbox.pdmodel.common.PDStream(doc);
            try (java.io.OutputStream os = toUni.createOutputStream()) {
                os.write(cmap.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }
            COSDictionary fd = new COSDictionary();
            fd.setItem(COSName.TYPE, COSName.FONT);
            fd.setItem(COSName.SUBTYPE, COSName.TYPE1);
            fd.setItem(COSName.BASE_FONT, COSName.getPDFName("Helvetica"));
            fd.setItem(COSName.TO_UNICODE, toUni.getCOSObject());
            PDFont bad = new PDType1Font(fd);
            PDFont good = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(good, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(LONG_TEXT);
                cs.endText();
                cs.beginText();
                cs.setFont(bad, 12);
                cs.newLineAtOffset(50, 650);
                cs.appendRawCommands("(AAAA) Tj\n");
                cs.endText();
            }
            return save(doc);
        }
    }

    private static byte[] buildHuge() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(PAGE_W, PAGE_H));
            doc.addPage(page);
            int w = 5001;
            int h = 5001;
            // 0으로 채운 1비트 래스터를 Flate로 압축해 메모리를 아낀다.
            Deflater deflater = new Deflater();
            deflater.setInput(new byte[(w + 7) / 8 * h]);
            deflater.finish();
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            while (!deflater.finished()) {
                raw.write(buf, 0, deflater.deflate(buf));
            }
            deflater.end();
            PDImageXObject img = new PDImageXObject(doc,
                    new java.io.ByteArrayInputStream(raw.toByteArray()), COSName.FLATE_DECODE,
                    w, h, 1, PDDeviceGray.INSTANCE);
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(LONG_TEXT);
                cs.endText();
                cs.drawImage(img, 50, 100, 100, 100);
            }
            return save(doc);
        }
    }

    private static void fill(PDDocument doc, PageSpec... specs) throws IOException {
        PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        for (PageSpec spec : specs) {
            PDPage page = new PDPage(new PDRectangle(PAGE_W, PAGE_H));
            page.setRotation(spec.rotation());
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (TextAt t : spec.texts()) {
                    cs.beginText();
                    cs.setFont(font, t.size());
                    cs.newLineAtOffset(t.x(), t.y());
                    cs.showText(t.text());
                    cs.endText();
                }
                for (ImageAt im : spec.images()) {
                    cs.drawImage(tinyImage(doc), im.x(), im.y(), im.w(), im.h());
                }
            }
        }
    }

    private static PDImageXObject tinyImage(PDDocument doc) throws IOException {
        java.awt.image.BufferedImage bi =
                new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
        return org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, bi);
    }

    private static byte[] save(PDDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }
}
