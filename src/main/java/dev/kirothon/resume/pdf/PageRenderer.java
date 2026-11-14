package dev.kirothon.resume.pdf;

import dev.kirothon.resume.pdf.PdfTypes.Box;
import dev.kirothon.resume.pdf.PdfTypes.MaskRegion;
import dev.kirothon.resume.pdf.PdfTypes.RenderResult;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

/** 요구사항 3.4, 3.5, 4.4: 150DPI PNG 렌더링, 검은 박스 칠하기, 픽셀 검사. */
@Component
public class PageRenderer {

    public static final float DPI = 150f;
    public static final double SCALE = DPI / 72.0;

    public RenderResult render(byte[] pdf, List<MaskRegion> regions) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            renderer.setAnnotationsFilter(a -> false);
            List<byte[]> pngs = new ArrayList<>();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, DPI, ImageType.RGB);
                List<Rectangle> rects = pixelRects(regions, i + 1, image.getWidth(), image.getHeight());
                Graphics2D g = image.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                    g.setColor(new Color(0, 0, 0));
                    for (Rectangle r : rects) {
                        g.fillRect(r.x, r.y, r.width, r.height);
                    }
                } finally {
                    g.dispose();
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "png", out)) {
                    throw new IOException("PNG writer unavailable");
                }
                byte[] png = out.toByteArray();
                image.flush();
                digest.update(png);
                pngs.add(png);
            }
            return new RenderResult(pngs, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 포인트 영역을 픽셀 사각형으로 바꾼다: 왼쪽·위 floor, 오른쪽·아래 ceil, 이미지 경계로 자른다. 비면 너비 또는 높이가 0. */
    public static Rectangle toPixelRect(Box box, int imageWidth, int imageHeight) {
        int x0 = clamp((int) Math.floor(box.x() * SCALE), 0, imageWidth);
        int y0 = clamp((int) Math.floor(box.y() * SCALE), 0, imageHeight);
        int x1 = clamp((int) Math.ceil((box.x() + box.width()) * SCALE), 0, imageWidth);
        int y1 = clamp((int) Math.ceil((box.y() + box.height()) * SCALE), 0, imageHeight);
        return new Rectangle(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    /** 한 페이지(1부터)에 속한 영역만 픽셀 사각형으로 바꾼다. */
    public static List<Rectangle> pixelRects(List<MaskRegion> regions, int page, int imageWidth, int imageHeight) {
        List<Rectangle> rects = new ArrayList<>();
        for (MaskRegion region : regions) {
            if (region.page() == page) {
                rects.add(toPixelRect(region.box(), imageWidth, imageHeight));
            }
        }
        return rects;
    }

    /** PNG를 해독해 한 페이지의 영역(포인트)을 픽셀로 바꾸고 모두 검은색인지 검사한다. */
    public static boolean verifyBlack(byte[] png, List<MaskRegion> regions, int page) {
        BufferedImage image = decode(png);
        return verifyBlack(image, pixelRects(regions, page, image.getWidth(), image.getHeight()));
    }

    /** 각 사각형의 모든 픽셀 RGB가 0x000000인지. 이미지 밖은 잘라서 검사한다. */
    public static boolean verifyBlack(byte[] png, List<Rectangle> pixelRects) {
        return verifyBlack(decode(png), pixelRects);
    }

    private static boolean verifyBlack(BufferedImage image, List<Rectangle> pixelRects) {
        Rectangle bounds = new Rectangle(0, 0, image.getWidth(), image.getHeight());
        for (Rectangle rect : pixelRects) {
            Rectangle r = rect.intersection(bounds);
            if (r.isEmpty()) {
                continue;
            }
            for (int y = r.y; y < r.y + r.height; y++) {
                for (int x = r.x; x < r.x + r.width; x++) {
                    if ((image.getRGB(x, y) & 0xFFFFFF) != 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static BufferedImage decode(byte[] png) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                throw new IllegalArgumentException("not a PNG image");
            }
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
