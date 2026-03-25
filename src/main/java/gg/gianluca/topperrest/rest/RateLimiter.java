package gg.gianluca.topperrest.rest;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple sliding-window rate limiter keyed by IP address.
 */
public class RateLimiter {

    private final int maxRequests;
    private final long windowMillis;

    private final ConcurrentHashMap<String, WindowEntry> windows = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, int windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000L;
    }

    /**
     * Returns true if the request should be allowed, false if rate limited.
     */
    public boolean tryAcquire(String ip) {
        long now = System.currentTimeMillis();
        WindowEntry entry = windows.compute(ip, (k, v) -> {
            if (v == null || now >= v.windowEnd) {
                return new WindowEntry(now + windowMillis, new AtomicInteger(1));
            }
            v.count.incrementAndGet();
            return v;
        });
        return entry.count.get() <= maxRequests;
    }

    /** Remove stale entries to prevent unbounded growth. */
    public void evictExpired() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> now >= e.getValue().windowEnd);
    }

    private static final class WindowEntry {
        final long windowEnd;
        final AtomicInteger count;

        WindowEntry(long windowEnd, AtomicInteger count) {
            this.windowEnd = windowEnd;
            this.count = count;
        }
    }
}
