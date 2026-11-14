package dev.kirothon.resume.resume;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 업로드 후 24시간 지난 원본 PDF를 지운다(요구사항 4.7). */
@Component
@EnableScheduling
public class DraftSourceCleaner {
    private static final Logger log = LoggerFactory.getLogger(DraftSourceCleaner.class);

    private final DraftSourceRepository drafts;

    public DraftSourceCleaner(DraftSourceRepository drafts) {
        this.drafts = drafts;
    }

    @Scheduled(fixedDelayString = "${app.draft-source.cleanup-millis:600000}",
            initialDelayString = "${app.draft-source.cleanup-millis:600000}")
    public void scheduled() {
        purgeExpired(Instant.now());
    }

    @Transactional
    public int purgeExpired(Instant now) {
        int n = drafts.deleteExpired(now);
        if (n > 0) {
            log.info("DRAFT_SOURCE_PURGED count={}", n);
        }
        return n;
    }
}
