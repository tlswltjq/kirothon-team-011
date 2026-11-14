package dev.kirothon.resume.demo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * 데모용 가상 인물 이력서 PDF 생성기(작업 11). 런타임이 아니라 개발자가 한 번 실행해 결과를 커밋한다.
 *
 * <p>실행: {@code ./gradlew generateDemoPdfs} (출력: src/main/resources/demo/). 
 * 직접 실행하려면 인자로 출력 폴더를 준다. 일반 {@code test} 실행에서는 호출되지 않는다.
 *
 * <p>글꼴: 시스템의 Noto Sans CJK KR(.ttc)에서 KR 글꼴을 꺼내, 이 문서에 쓰인 글자만 남긴 서브셋을 PDF에 포함한다.
 * Noto CJK는 CFF(OTF)라서 PDFBox가 직접 서브셋·포함하지 못한다. 그래서 fontTools(Python)로
 * 글자만 남기고 외곽선을 TrueType으로 바꾼 작은 TTF를 만든 뒤 PDType0Font.load로 포함한다.
 * 필요: fontTools가 import되는 python(환경변수 DEMO_PYTHON 또는 속성 demo.python, 기본 python3; 필요하면 PYTHONPATH도 지정).
 * 글꼴 파일 자체는 저장소에 두지 않는다. 경로는 {@code demo.font.regular}, {@code demo.font.bold} 속성으로 바꿀 수 있다.
 * 모든 인물·학교·회사·연락처는 가상이다(example.com, 010-0000-xxxx).
 */
public final class DemoPdfGenerator {

    static final String REGULAR = "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc";
    static final String BOLD = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc";

    record Job(String company, String period, String title, List<String> bullets) {
    }

    record Person(String file, String name, String role, String email, String phone, String url,
                  String school, String major, String schoolPeriod, String summary,
                  List<Job> jobs, List<String> skills, Color accent, Color suit) {
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "src/main/resources/demo");
        Files.createDirectories(out);
        String chars = LABELS + people();
        Path tmp = Files.createTempDirectory("demo-fonts");
        try {
            Path reg = subset(new File(System.getProperty("demo.font.regular", REGULAR)), chars, tmp.resolve("r.ttf"));
            Path bld = subset(new File(System.getProperty("demo.font.bold", BOLD)), chars, tmp.resolve("b.ttf"));
            for (Person p : people()) {
                write(out.resolve(p.file()), reg, bld, p, p.file().equals("owner-v1.pdf"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } finally {
            try (var st = Files.walk(tmp)) {
                st.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
            }
        }
    }

    static List<Person> people() {
        List<Person> l = new ArrayList<>();
        l.add(new Person("owner-v1.pdf", "김하늘", "백엔드 개발자", "haneul.kim@example.com", "010-0000-1234",
                "https://github.com/example-haneul", "가나다대학교", "컴퓨터공학과 학사", "2016.03 - 2020.02",
                "다양한 경험을 가진 열심히 하는 개발자입니다. 맡은 일은 끝까지 책임지고 팀과 소통하며 일합니다.",
                List.of(new Job("누리소프트(가상)", "2020.03 - 2023.06", "서버 개발자", List.of(
                                "여러 프로젝트에 참여하여 서버 개발을 담당함",
                                "성능 개선 및 장애 대응 업무를 수행함",
                                "API 개발과 유지보수를 진행함",
                                "코드 리뷰 문화 정착에 기여함")),
                        new Job("바다테크(가상)", "2023.07 - 현재", "백엔드 개발자", List.of(
                                "결제 관련 서비스 개발에 참여함",
                                "데이터베이스 관련 작업을 맡아서 진행함",
                                "팀원들과 협업하여 좋은 결과를 얻음"))),
                List.of("Java", "Spring", "MySQL", "Redis", "Docker", "Git", "AWS", "Kafka"),
                new Color(0x2F5D8A), new Color(0x35506B)));
        l.add(new Person("owner-v2.pdf", "김하늘", "백엔드 개발자", "haneul.kim@example.com", "010-0000-1234",
                "https://github.com/example-haneul", "가나다대학교", "컴퓨터공학과 학사", "2016.03 - 2020.02",
                "결제·주문 도메인에서 5년간 Java/Spring 서버를 설계하고 운영했습니다. 응답 시간과 장애 복구 시간을 숫자로 관리합니다.",
                List.of(new Job("바다테크(가상)", "2023.07 - 현재", "백엔드 개발자", List.of(
                                "결제 승인 API의 N+1 쿼리와 인덱스를 정리해 평균 응답 시간을 40% 단축(420ms에서 250ms)",
                                "Kafka 기반 이벤트 재처리 구조를 도입해 결제 누락 건을 월 30건에서 0건으로 감소",
                                "주문 DB 조회를 Redis 캐시로 분리해 피크 시간 DB CPU 사용률을 85%에서 55%로 낮춤")),
                        new Job("누리소프트(가상)", "2020.03 - 2023.06", "서버 개발자", List.of(
                                "회원 서비스 API 30여 개를 개발하고 단위 테스트 커버리지를 45%에서 78%로 향상",
                                "장애 대응 절차와 알림 기준을 정비해 평균 복구 시간을 50분에서 20분으로 단축"))),
                List.of("Java", "Spring", "MySQL", "Redis", "Docker", "Git", "AWS", "Kafka"),
                new Color(0x2F5D8A), new Color(0x35506B)));
        l.add(new Person("frontend-park.pdf", "박서준", "프론트엔드 개발자", "seojun.park@example.com", "010-0000-2345",
                "https://github.com/example-seojun", "라마대학교", "정보통신공학과 학사", "2017.03 - 2021.02",
                "사용자가 체감하는 속도와 접근성을 중시하는 프론트엔드 개발자입니다.",
                List.of(new Job("모아웹(가상)", "2021.03 - 현재", "프론트엔드 개발자", List.of(
                                "디자인 시스템 컴포넌트 42개를 구축해 화면 개발 시간을 35% 줄임",
                                "번들 분할과 이미지 지연 로딩으로 첫 화면 표시 시간을 3.1초에서 1.8초로 개선",
                                "웹 접근성 점검 체계를 도입해 접근성 점수를 72점에서 95점으로 향상"))),
                List.of("TypeScript", "React", "Next.js", "Vite", "Jest", "Playwright", "Figma"),
                new Color(0x7A3E9D), new Color(0x4B3F72)));
        l.add(new Person("data-lee.pdf", "이지우", "데이터 분석가", "jiwoo.lee@example.com", "010-0000-3456",
                "https://github.com/example-jiwoo", "마바대학교", "통계학과 석사", "2018.03 - 2020.02",
                "지표 설계와 실험 분석으로 의사결정을 돕는 데이터 분석가입니다.",
                List.of(new Job("데이터숲(가상)", "2020.03 - 현재", "데이터 분석가", List.of(
                                "A/B 테스트 설계·분석 18건을 수행해 전환율을 12% 개선",
                                "이탈 예측 모델을 만들어 재방문율을 8%p 높임",
                                "주간 지표 대시보드를 자동화해 보고 준비 시간을 주 6시간에서 1시간으로 단축"))),
                List.of("SQL", "Python", "pandas", "Tableau", "R", "Airflow"),
                new Color(0x1E7A5A), new Color(0x3B6B5E)));
        return l;
    }

    // ---------- 글꼴 ----------

    static final String LABELS = "경력소개학력기술이메일전화자기소개지원 직무 기타 팀 프로젝트와 동아리 활동 다수 경험 성실하고 책임감 있음 "
            + "새로운 학습에 적극적임 커뮤니케이션 능력 보유 다양한 분야에 관심이 많음 사용 가능 등 이력서 가상 데이터 ·•|-:,.()/%";

    /** 글꼴 모음에서 KR 글꼴의 번호를 찾는다. */
    static int krIndex(File ttc) throws IOException {
        try (TrueTypeCollection c = new TrueTypeCollection(ttc)) {
            int[] idx = {0, -1};
            c.processAllFonts(f -> {
                try {
                    String n = f.getName();
                    if (idx[1] < 0 && n != null && n.toLowerCase().contains("kr")) {
                        idx[1] = idx[0];
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
                idx[0]++;
            });
            if (idx[1] < 0) {
                throw new IOException("KR 글꼴 없음: " + ttc);
            }
            return idx[1];
        }
    }

    /**
     * fontTools로 필요한 글자만 남기고(subset) 글자 외곽선을 CFF(3차)에서 TrueType(2차)으로 바꾼다.
     * PDFBox는 CID 키드 CFF 글꼴을 포함하지 못하고, CFF를 직접 서브셋하지도 못하기 때문이다.
     */
    static final String PY = """
import sys
from fontTools import subset
from fontTools.ttLib import TTFont, newTable
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
src, num, txt, dst = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4]
opt = subset.Options()
opt.layout_features = []
opt.hinting = False
opt.notdef_outline = True
opt.font_number = num
font = subset.load_font(src, opt)
ss = subset.Subsetter(opt)
ss.populate(text=open(txt, encoding='utf-8').read())
ss.subset(font)
order = font.getGlyphOrder()
gs = font.getGlyphSet()
glyphs = {}
for n in order:
    pen = TTGlyphPen(gs)
    gs[n].draw(Cu2QuPen(pen, 1.0, reverse_direction=True))
    glyphs[n] = pen.glyph()
font['loca'] = newTable('loca')
font['glyf'] = glyf = newTable('glyf')
glyf.glyphOrder = order
glyf.glyphs = glyphs
del font['CFF ']
if 'VORG' in font:
    del font['VORG']
for n, g in glyf.glyphs.items():
    g.recalcBounds(glyf)
    font['hmtx'][n] = (font['hmtx'][n][0], getattr(g, 'xMin', 0))
glyf.compile(font)
maxp = font['maxp'] = newTable('maxp')
maxp.tableVersion = 0x00010000
maxp.maxZones = 1
for k in ('maxTwilightPoints', 'maxStorage', 'maxFunctionDefs', 'maxInstructionDefs', 'maxStackElements',
          'maxSizeOfInstructions', 'maxComponentElements'):
    setattr(maxp, k, 0)
maxp.numGlyphs = len(order)
maxp.maxPoints = maxp.maxContours = maxp.maxCompositePoints = maxp.maxCompositeContours = 0
maxp.maxComponentDepth = 0
post = font['post']
post.formatType = 2.0
post.extraNames = []
post.mapping = {}
post.glyphOrder = order
font.sfntVersion = '\\000\\001\\000\\000'
font.save(dst)
""";

    static Path subset(File ttc, String chars, Path dest) throws IOException, InterruptedException {
        Path txt = Files.createTempFile("demo-chars", ".txt");
        Files.writeString(txt, chars);
        Path py = Files.createTempFile("demo-subset", ".py");
        Files.writeString(py, PY);
        Process pr = new ProcessBuilder(System.getProperty("demo.python", System.getenv().getOrDefault("DEMO_PYTHON", "python3")),
                py.toString(), ttc.getPath(), String.valueOf(krIndex(ttc)), txt.toString(), dest.toString())
                .redirectErrorStream(true).start();
        String log = new String(pr.getInputStream().readAllBytes());
        if (pr.waitFor() != 0) {
            throw new IOException("글꼴 서브셋 실패: " + log);
        }
        Files.delete(txt);
        Files.delete(py);
        return dest;
    }

    // ---------- 그리기 ----------

    static void write(Path file, Path regular, Path bold, Person p, boolean dense)
            throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDFont reg = loadWhole(doc, regular);
            PDFont bld = loadWhole(doc, bold);
            PDImageXObject photo = LosslessFactory.createFromImage(doc, silhouette(p.suit()));
            Canvas c = new Canvas(doc, reg, bld, p, photo);
            if (dense) {
                c.dense();
            } else {
                c.airy();
            }
            c.close();
            doc.getDocumentInformation().setTitle("이력서(가상 데이터) " + p.name());
            doc.save(file.toFile());
        }
    }

    /** 서브셋된 TrueType 글꼴을 PDF에 포함한다. */
    static PDFont loadWhole(PDDocument doc, Path otf) throws IOException {
        try (var in = Files.newInputStream(otf)) {
            return PDType0Font.load(doc, in, true);
        }
    }

    static BufferedImage silhouette(Color suit) {
        int w = 300, h = 400;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xDCE6F0));
        g.fillRect(0, 0, w, h);
        g.setColor(suit);
        g.fillRoundRect(-40, 290, 380, 260, 140, 140);
        g.setColor(Color.WHITE);
        g.fillPolygon(new int[] {120, 180, 150}, new int[] {290, 290, 350}, 3);
        g.setColor(new Color(0xE8C4A0));
        g.fillRect(132, 215, 36, 80);
        g.fillOval(88, 80, 124, 150);
        g.setColor(new Color(0x2B2B2B));
        g.fillArc(84, 66, 132, 110, 0, 180);
        g.setStroke(new BasicStroke(3));
        g.dispose();
        return img;
    }

    static final class Canvas {
        static final float W = PDRectangle.A4.getWidth();
        static final float H = PDRectangle.A4.getHeight();

        final PDDocument doc;
        final PDFont reg;
        final PDFont bld;
        final Person p;
        final PDImageXObject photo;
        PDPageContentStream cs;
        float y;
        float left;
        float right;
        float bottom;
        float lineGap;
        float size;

        Canvas(PDDocument doc, PDFont reg, PDFont bld, Person p, PDImageXObject photo) {
            this.doc = doc;
            this.reg = reg;
            this.bld = bld;
            this.p = p;
            this.photo = photo;
        }

        void newPage(float top) throws IOException {
            if (cs != null) {
                cs.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = H - top;
        }

        void close() throws IOException {
            cs.close();
        }

        void text(float x, float yy, PDFont f, float s, Color c, String t) throws IOException {
            cs.beginText();
            cs.setFont(f, s);
            cs.setNonStrokingColor(c);
            cs.newLineAtOffset(x, yy);
            cs.showText(t);
            cs.endText();
        }

        void rect(float x, float yy, float w, float h, Color c) throws IOException {
            cs.setNonStrokingColor(c);
            cs.addRect(x, yy, w, h);
            cs.fill();
        }

        List<String> wrap(String t, PDFont f, float s, float width) throws IOException {
            List<String> out = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < t.length(); i++) {
                String next = cur.toString() + t.charAt(i);
                if (f.getStringWidth(next) / 1000f * s > width && cur.length() > 0) {
                    out.add(cur.toString());
                    cur = new StringBuilder();
                }
                cur.append(t.charAt(i));
            }
            if (cur.length() > 0) {
                out.add(cur.toString());
            }
            return out;
        }

        void paragraph(float x, float width, PDFont f, float s, float gap, Color c, String t, String bullet)
                throws IOException {
            float indent = bullet == null ? 0 : 10;
            List<String> lines = wrap(t, f, s, width - indent);
            for (int i = 0; i < lines.size(); i++) {
                if (y < bottom) {
                    newPage(56);
                }
                if (i == 0 && bullet != null) {
                    text(x, y, f, s, c, bullet);
                }
                text(x + indent, y, f, s, c, lines.get(i));
                y -= gap;
            }
        }

        /** v1: 한 쪽에 빽빽하게. 제목 계층 없음, 작은 글자, 좁은 여백, 모호한 문장. */
        void dense() throws IOException {
            left = 28;
            right = W - 28;
            bottom = 24;
            newPage(30);
            float s = 8.5f;
            float g = 10.5f;
            cs.drawImage(photo, right - 66, H - 30 - 88, 66, 88);
            text(left, y - 10, bld, 12, Color.BLACK, p.name() + "  " + p.role() + " 지원");
            y -= 24;
            text(left, y, reg, s, Color.BLACK, "이메일 " + p.email() + "  전화 " + p.phone() + "  " + p.url());
            y -= g;
            text(left, y, reg, s, Color.BLACK, "학력 " + p.school() + " " + p.major() + " " + p.schoolPeriod());
            y -= g;
            paragraph(left, right - left - 76, reg, s, g, Color.BLACK, "자기소개 " + p.summary(), null);
            y = Math.min(y, H - 30 - 88 - 8);
            for (Job j : p.jobs()) {
                text(left, y, bld, s, Color.BLACK, "경력 " + j.company() + " " + j.period() + " " + j.title());
                y -= g;
                for (String b : j.bullets()) {
                    paragraph(left, right - left, reg, s, g, Color.BLACK, b, "-");
                }
            }
            paragraph(left, right - left, reg, s, g, Color.BLACK,
                    "기술 " + String.join(", ", p.skills()) + " 등 다양한 기술 사용 가능", null);
            paragraph(left, right - left, reg, s, g, Color.BLACK,
                    "기타 팀 프로젝트와 동아리 활동 다수 경험, 성실하고 책임감 있음, 새로운 기술 학습에 적극적임, "
                            + "커뮤니케이션 능력 보유, 다양한 분야에 관심이 많음.", null);
        }

        /** v2 및 다른 인물: 여백, 제목 계층, 구체적 성과. */
        void airy() throws IOException {
            left = 56;
            right = W - 56;
            bottom = 60;
            newPage(0);
            Color accent = p.accent();
            rect(0, H - 150, W, 150, accent);
            cs.drawImage(photo, left, H - 128, 78, 104);
            text(left + 100, H - 70, bld, 26, Color.WHITE, p.name());
            text(left + 100, H - 96, reg, 13, Color.WHITE, p.role() + " 지원");
            text(left + 100, H - 118, reg, 10, Color.WHITE, p.email() + "   " + p.phone());
            text(left + 100, H - 133, reg, 10, Color.WHITE, p.url());
            y = H - 190;

            section("소개");
            paragraph(left, right - left, reg, 10.5f, 17, Color.DARK_GRAY, p.summary(), null);
            y -= 12;

            section("경력");
            for (Job j : p.jobs()) {
                text(left, y, bld, 11.5f, Color.BLACK, j.company() + "  |  " + j.title());
                text(right - 110, y, reg, 10, Color.GRAY, j.period());
                y -= 20;
                for (String b : j.bullets()) {
                    paragraph(left, right - left, reg, 10.5f, 17, Color.DARK_GRAY, b, "•");
                }
                y -= 10;
            }

            section("학력");
            text(left, y, bld, 11.5f, Color.BLACK, p.school() + "  " + p.major());
            text(right - 110, y, reg, 10, Color.GRAY, p.schoolPeriod());
            y -= 30;

            section("기술");
            paragraph(left, right - left, reg, 10.5f, 17, Color.DARK_GRAY, String.join("  ·  ", p.skills()), null);
        }

        void section(String title) throws IOException {
            if (y < bottom + 60) {
                newPage(56);
            }
            text(left, y, bld, 14, p.accent(), title);
            y -= 6;
            rect(left, y, right - left, 1.2f, p.accent());
            y -= 20;
        }
    }

    private DemoPdfGenerator() {
    }
}
