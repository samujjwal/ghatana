package com.ghatana.datacloud.security.policies;

import com.ghatana.datacloud.security.SecurityPolicy;
import com.ghatana.datacloud.security.SecurityPolicyService;
import com.ghatana.platform.governance.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiting security policy for Data Cloud endpoints.
 *
 * <p><b>Purpose</b><br>
 * Implements token bucket rate limiting to prevent abuse and ensure fair usage
 * of API resources. Supports different rate limits based on user roles and tenant tiers.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RateLimitPolicy policy = new RateLimitPolicy(100, Duration.ofMinutes(1));
 * 
 * SecurityPolicyService service = SecurityPolicyService.builder()
 *     .addCustomPolicy("rate-limit", policy)
 *     .build();
 * }</pre>
 *
 * @see SecurityPolicy
 * @doc.type class
 * @doc.purpose Rate limiting security policy implementation
 * @doc.layer product
 * @doc.pattern Rate Limiter
 */
public class RateLimitPolicy implements SecurityPolicy {

    private static final Logger log = LoggerFactory.getLogger(RateLimitPolicy.class);

    private final int requestsPerWindow;
    private final Duration windowDuration;
    private final Map<String, RateLimiterBucket> buckets = new ConcurrentHashMap<>();
    private final boolean enabled;

    /**
     * Creates a rate limit policy with specified parameters.
     *
     * @param requestsPerWindow maximum requests allowed per time window
     * @param windowDuration time window for rate limiting
     */
    public RateLimitPolicy(int requestsPerWindow, Duration windowDuration) {
        this(requestsPerWindow, windowDuration, true);
    }

    /**
     * Creates a rate limit policy with enabled flag.
     *
     * @param requestsPerWindow maximum requests allowed per time window
     * @param windowDuration time window for rate limiting
     * @param enabled whether rate limiting is enabled
     */
    public RateLimitPolicy(int requestsPerWindow, Duration windowDuration, boolean enabled) {
        if (requestsPerWindow <= 0) {
            throw new IllegalArgumentException("Requests per window must be positive");
        }
        if (windowDuration == null || windowDuration.isNegative() || windowDuration.isZero()) {
            throw new IllegalArgumentException("Window duration must be positive");
        }

        this.requestsPerWindow = requestsPerWindow;
        this.windowDuration = windowDuration;
        this.enabled = enabled;
    }

    @Override
    public SecurityPolicyService.SecurityEvaluationResult evaluate(
            Principal principal,
            String method,
            String path,
            Map<String, String> headers) {

        if (!enabled) {
            return SecurityPolicyService.SecurityEvaluationResult.builder()
                .allowed(true)
                .reason("Rate limiting disabled")
                .build();
        }

        String bucketKey = getBucketKey(principal, headers);
        RateLimiterBucket bucket = buckets.computeIfAbsent(bucketKey, k -> new RateLimiterBucket());

        synchronized (bucket) {
            Instant now = Instant.now();
            
            // Reset window if expired
            if (bucket.windowStart.plus(windowDuration).isBefore(now)) {
                bucket.reset(now);
            }

            if (bucket.requests.get() >= requestsPerWindow) {
                log.warn("Rate limit exceeded for bucket: {} ({} requests in {}ms window)", 
                        bucketKey, bucket.requests.get(), windowDuration.toMillis());

                return SecurityPolicyService.SecurityEvaluationResult.builder()
                    .allowed(false)
                    .reason("Rate limit exceeded. Maximum " + requestsPerWindow + 
                           " requests per " + windowDuration.toSeconds() + " seconds")
                    .errorCode("RATE_LIMIT_EXCEEDED")
                    .build();
            }

            bucket.requests.incrementAndGet();
            
            return SecurityPolicyService.SecurityEvaluationResult.builder()
                .allowed(true)
                .reason("Rate limit check passed")
                .metadata(Map.of(
                    "remainingRequests", requestsPerWindow - bucket.requests.get(),
                    "windowResetTime", bucket.windowStart.plus(windowDuration).toString()
                ))
                .build();
        }
    }

    @Override
    public boolean appliesTo(String method, String path) {
        // Apply rate limiting to all API endpoints except health checks
        return !path.startsWith("/health") && 
               !path.startsWith("/metrics") && 
               !path.startsWith("/ready") &&
               !path.startsWith("/live");
    }

    @Override
    public int getPriority() {
        return 100; // High priority to evaluate early
    }

    /**
     * Gets the bucket key for rate limiting based on principal and headers.
     */
    private String getBucketKey(Principal principal, Map<String, String> headers) {
        // Use tenant ID + user ID for granular rate limiting
        if (principal != null) {
            return principal.getTenantId() + ":" + principal.getName();
        }

        // Fallback to IP address for unauthenticated requests
        String clientIp = headers.get("X-Forwarded-For");
        if (clientIp == null) {
            clientIp = headers.get("X-Real-IP");
        }
        if (clientIp == null) {
            clientIp = "unknown";
        }

        return "anonymous:" + clientIp;
    }

    /**
     * Gets current rate limit statistics.
     */
    public RateLimitStats getStats() {
        int totalBuckets = buckets.size();
        int activeBuckets = 0;
        int totalRequests = 0;

        Instant now = Instant.now();
        for (RateLimiterBucket bucket : buckets.values()) {
            if (bucket.windowStart.plus(windowDuration).isAfter(now)) {
                activeBuckets++;
                totalRequests += bucket.requests.get();
            }
        }

        return new RateLimitStats(totalBuckets, activeBuckets, totalRequests, requestsPerWindow, windowDuration);
    }

    /**
     * Cleans up expired buckets to prevent memory leaks.
     */
    public void cleanupExpiredBuckets() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(windowDuration.multipliedBy(2)); // Keep buckets for 2 windows

        buckets.entrySet().removeIf(entry -> {
            RateLimiterBucket bucket = entry.getValue();
            return bucket.windowStart.isBefore(cutoff);
        });
    }

    /**
     * Rate limiter bucket using token bucket algorithm.
     */
    private static class RateLimiterBucket {
        final AtomicInteger requests = new AtomicInteger(0);
        volatile Instant windowStart = Instant.now();

        void reset(Instant newWindowStart) {
            this.windowStart = newWindowStart;
            this.requests.set(0);
        }
    }

    /**
     * Rate limiting statistics.
     */
    public static class RateLimitStats {
        private final int totalBuckets;
        private final int activeBuckets;
        private final int totalRequests;
        private final int requestsPerWindow;
        private final Duration windowDuration;

        public RateLimitStats(int totalBuckets, int activeBuckets, int totalRequests, 
                             int requestsPerWindow, Duration windowDuration) {
            this.totalBuckets = totalBuckets;
            this.activeBuckets = activeBuckets;
            this.totalRequests = totalRequests;
            this.requestsPerWindow = requestsPerWindow;
            this.windowDuration = windowDuration;
        }

        public int getTotalBuckets() {
            return totalBuckets;
        }

        public int getActiveBuckets() {
            return activeBuckets;
        }

        public int getTotalRequests() {
            return totalRequests;
        }

        public int getRequestsPerWindow() {
            return requestsPerWindow;
        }

        public Duration getWindowDuration() {
            return windowDuration;
        }

        @Override
        public String toString() {
            return String.format("RateLimitStats{buckets=%d, active=%d, requests=%d, limit=%d/%ds}",
                    totalBuckets, activeBuckets, totalRequests, requestsPerWindow, windowDuration.getSeconds());
        }
    }
}
