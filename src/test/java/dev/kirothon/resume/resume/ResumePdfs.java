package dev.kirothon.resume.resume;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

/** HTTP 테스트용 PDF: 가상 이름·이메일·전화·본문 + 흰색 사진 이미지. 저장소에 이진 파일을 두지 않는다. */
final class ResumePdfs {
    static final String NAME = "Gasang Kim";
    static final String EMAIL = "gasang.kim@example.com";
    static final String PHONE = "010-1234-5678";

    private ResumePdfs() {
    }

    /** extra: 본문 첫 줄에 넣을 추가 문구(버전 구분용). */
    static byte[] resume(String extra) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(612f, 792f));
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            BufferedImage photo = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    photo.setRGB(x, y, Color.WHITE.getRGB());
                }
            }
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                String[] lines = {
                    NAME,
                    "Email " + EMAIL,
                    "Phone " + PHONE,
                    "Backend engineer with ten years of building reliable services. " + extra,
                    "Led a team that cut deployment time and improved the stability of payment systems."
                };
                float y = 740;
                for (String line : lines) {
                    cs.beginText();
                    cs.setFont(font, 12);
                    cs.newLineAtOffset(50, y);
                    cs.showText(line);
                    cs.endText();
                    y -= 22;
                }
                cs.drawImage(LosslessFactory.createFromImage(doc, photo), 440, 650, 100, 100);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
