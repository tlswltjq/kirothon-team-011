package dev.kirothon.resume.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kirothon.resume.pdf.MaskRegionFinder;
import dev.kirothon.resume.pdf.PageRenderer;
import dev.kirothon.resume.pdf.PdfIngestor;
import dev.kirothon.resume.pdf.PdfTypes.IngestResult;
import dev.kirothon.resume.pdf.PdfTypes.PdfRejectedException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PdfProcessorLimitTest {

    @Test
    void req2_9_concurrencyIsBoundedAndExcessGets503() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PdfIngestor blocking = new PdfIngestor() {
            @Override
            public IngestResult ingest(byte[] pdf) {
                started.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new PdfRejectedException(422, "PDF_PARSE_FAILED", "stub");
            }
        };
        PdfProcessor processor = new PdfProcessor(blocking, new MaskRegionFinder(), new PageRenderer(),
                30_000, 1, 1);
        try {
            CompletableFuture<Object> running = CompletableFuture.supplyAsync(() -> run(processor));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Object> queued = CompletableFuture.supplyAsync(() -> run(processor));
            Thread.sleep(300);
            assertThatThrownBy(() -> processor.process(new byte[] {1}, List.of()))
                    .isInstanceOfSatisfying(PdfRejectedException.class, e -> {
                        assertThat(e.status()).isEqualTo(503);
                        assertThat(e.code()).isEqualTo("PDF_BUSY");
                    });
            release.countDown();
            running.get(5, TimeUnit.SECONDS);
            queued.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            processor.shutdown();
        }
    }

    private static Object run(PdfProcessor processor) {
        try {
            processor.process(new byte[] {1}, List.of());
        } catch (PdfRejectedException e) {
            return e.code();
        }
        return "ok";
    }
}
