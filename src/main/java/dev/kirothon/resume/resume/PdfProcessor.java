package dev.kirothon.resume.resume;

import dev.kirothon.resume.pdf.MaskRegionFinder;
import dev.kirothon.resume.pdf.PageRenderer;
import dev.kirothon.resume.pdf.PdfIngestor;
import dev.kirothon.resume.pdf.PdfTypes.IngestResult;
import dev.kirothon.resume.pdf.PdfTypes.MaskPlan;
import dev.kirothon.resume.pdf.PdfTypes.PdfRejectedException;
import dev.kirothon.resume.pdf.PdfTypes.RenderResult;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 추출 → 마스킹 영역 계산 → 렌더링을 단일 스레드 ExecutorService에서 제한 시간 안에 실행한다(요구사항 2.9). */
@Component
public class PdfProcessor {
    public record Processed(IngestResult ingest, MaskPlan plan, RenderResult render) {
    }

    private final PdfIngestor ingestor;
    private final MaskRegionFinder finder;
    private final PageRenderer renderer;
    private final long timeoutMillis;
    private final ThreadPoolExecutor executor;

    public PdfProcessor(PdfIngestor ingestor, MaskRegionFinder finder, PageRenderer renderer,
                        @Value("${app.pdf.timeout-millis:20000}") long timeoutMillis,
                        @Value("${app.pdf.threads:2}") int threads,
                        @Value("${app.pdf.queue:2}") int queue) {
        this.executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queue), r -> {
                    Thread t = new Thread(r, "pdf-processor");
                    t.setDaemon(true);
                    return t;
                });
        this.ingestor = ingestor;
        this.finder = finder;
        this.renderer = renderer;
        this.timeoutMillis = timeoutMillis;
    }

    /** 렌더링까지 한다. */
    public Processed process(byte[] pdf, List<String> terms) {
        return run(pdf, terms, true);
    }

    /** 렌더링 없이 추출과 영역 계산만 한다(게시 검증용). */
    public Processed analyze(byte[] pdf, List<String> terms) {
        return run(pdf, terms, false);
    }

    private Processed run(byte[] pdf, List<String> terms, boolean render) {
        Future<Processed> future;
        try {
            future = executor.submit(() -> {
                IngestResult ingest = ingestor.ingest(pdf);
                MaskPlan plan = finder.find(ingest.lines(), ingest.images(), terms);
                RenderResult rendered = render ? renderer.render(pdf, plan.regions()) : null;
                return new Processed(ingest, plan, rendered);
            });
        } catch (RejectedExecutionException e) {
            throw new PdfRejectedException(503, "PDF_BUSY", "PDF 처리 요청이 많습니다. 잠시 후 다시 시도하세요.");
        }
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw tooComplex();
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw tooComplex();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof PdfRejectedException rejected) {
                throw rejected;
            }
            if (cause instanceof OutOfMemoryError) {
                throw tooComplex();
            }
            throw new PdfRejectedException(422, "PDF_PARSE_FAILED", "PDF를 해석할 수 없습니다.");
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static PdfRejectedException tooComplex() {
        return new PdfRejectedException(422, "PDF_TOO_COMPLEX", "PDF가 너무 복잡해 20초 안에 처리하지 못했습니다.");
    }
}
