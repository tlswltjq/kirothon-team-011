package dev.kirothon.resume.security;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 요구사항 2.3: 키(구분+클라이언트 IP)별 1분 고정 창 카운터. 외부 의존성 없이 단일 인스턴스 기준으로 센다.
 * 지난 창의 키는 창이 바뀐 뒤 첫 호출에서 한꺼번에 지운다(메모리 상한은 "1분 동안 본 IP 수").
 */
public class FixedWindowRateLimiter {
    static final long WINDOW_MILLIS = 60_000L;

    /** allowed=false면 retryAfterSeconds 뒤에 다시 시도할 수 있다. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private record Window(long start, AtomicLong count) {
    }

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupWindow = new AtomicLong(-1);

    public FixedWindowRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public Decision tryAcquire(String key, int limitPerWindow) {
        long now = clock.millis();
        long start = now - Math.floorMod(now, WINDOW_MILLIS);
        cleanup(start);
        // 창은 앞으로만 바꾼다: 분 경계에서 늦게 도착한(이전 창 start를 계산한) 요청이 새 창을 덮어써
        // 카운터를 되돌리지 못하게 한다. 그런 요청은 이미 있는 더 새로운 창에 센다.
        Window window = windows.compute(key, (k, w) -> w == null || w.start() < start
                ? new Window(start, new AtomicLong()) : w);
        long used = window.count().incrementAndGet();
        if (used <= limitPerWindow) {
            return new Decision(true, 0);
        }
        long retryAfter = Math.max(1, (window.start() + WINDOW_MILLIS - now + 999) / 1000);
        return new Decision(false, retryAfter);
    }

    private void cleanup(long currentStart) {
        long last = lastCleanupWindow.get();
        if (last != currentStart && lastCleanupWindow.compareAndSet(last, currentStart)) {
            windows.values().removeIf(w -> w.start() < currentStart);
        }
    }

    int trackedKeys() {
        return windows.size();
    }
}
