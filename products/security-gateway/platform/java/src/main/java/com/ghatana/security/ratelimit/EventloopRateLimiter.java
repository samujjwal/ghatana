package com.ghatana.security.ratelimit;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enhanced rate limiter with security-specific features.
 * Combines event-core implementation with security module requirements.
 
 *
 * @doc.type class
 * @doc.purpose Eventloop rate limiter
 * @doc.layer core
 * @doc.pattern Component
*/
public class EventloopRateLimiter {
    private static final Logger logger = LoggerFactory.getLogger(EventloopRateLimiter.class);
    
    private final int maxAttempts;
    private final Duration resetInterval;
    private final Map<String, AttemptCounter> attemptCounters = new ConcurrentHashMap<>();
    private final Eventloop eventloop;
    
    public EventloopRateLimiter(int maxAttempts, Duration resetInterval, Eventloop eventloop) {
        this.maxAttempts = maxAttempts;
        this.resetInterval = resetInterval;
        this.eventloop = eventloop;
        scheduleCleanup();
    }
    
    public Promise<Boolean> allow(String key) {
        AttemptCounter counter = attemptCounters.computeIfAbsent(
            key, k -> new AttemptCounter()
        );
        
        if (counter.incrementAndGet() > maxAttempts) {
            logger.warn("Rate limit exceeded for key: {}", key);
            return Promise.of(false);
        }
        return Promise.of(true);
    }
    
    private void scheduleCleanup() {
        eventloop.delay(resetInterval, () -> {
            attemptCounters.clear();
            scheduleCleanup();
        });
    }
    
    private static class AttemptCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        
        int incrementAndGet() {
            return count.incrementAndGet();
        }
    }
}
