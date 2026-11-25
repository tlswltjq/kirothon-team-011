package dev.kirothon.resume.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** 요구사항 2.3: 고정 창 경계, Retry-After 계산, 지난 창 키 정리. */
class FixedWindowRateLimiterTest {
    static final class MutableClock extends Clock {
        long millis;

        MutableClock(long millis) {
            this.millis = millis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }

    @Test
    void req2_3_windowResetsAndRetryAfterCountsDownToWindowEnd() {
        MutableClock clock = new MutableClock(1_800_000_000_000L + 15_500); // 창 시작 + 15.5초
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(clock);
        assertThat(limiter.tryAcquire("k", 2).allowed()).isTrue();
        assertThat(limiter.tryAcquire("k", 2).allowed()).isTrue();
        FixedWindowRateLimiter.Decision denied = limiter.tryAcquire("k", 2);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isEqualTo(45);

        clock.millis += 45_000; // 다음 창
        assertThat(limiter.tryAcquire("k", 2).allowed()).isTrue();
    }

    @Test
    void req2_3_staleKeysAreRemovedInNextWindow() {
        MutableClock clock = new MutableClock(1_800_000_000_000L);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(clock);
        for (int i = 0; i < 100; i++) {
            limiter.tryAcquire("ip-" + i, 10);
        }
        assertThat(limiter.trackedKeys()).isEqualTo(100);
        clock.millis += 60_000;
        limiter.tryAcquire("new", 10);
        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }
}
