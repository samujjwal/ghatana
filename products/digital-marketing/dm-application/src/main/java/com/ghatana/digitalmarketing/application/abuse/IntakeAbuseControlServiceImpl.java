/**
 * P1-038: Implementation of public intake abuse control service.
 *
 * @doc.type class
 * @doc.purpose In-memory abuse control implementation for public intake
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
package com.ghatana.digitalmarketing.application.abuse;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory implementation of abuse control service.
 * 
 * <p>P1-038: For production, this should be replaced with a distributed
 * implementation using Redis or similar for horizontal scaling.</p>
 */
public final class IntakeAbuseControlServiceImpl implements IntakeAbuseControlService {

    private static final Logger LOG = LoggerFactory.getLogger(IntakeAbuseControlServiceImpl.class);

    private final AbuseControlConfig config;
    private final Eventloop eventloop;

    // In-memory rate limit tracking (key: ip or email)
    private final Map<String, RateLimitTracker> rateLimitTrackers = new ConcurrentHashMap<>();

    // Duplicate submission tracking (key: email:formHash)
    private final Map<String, Instant> recentSubmissions = new ConcurrentHashMap<>();

    public IntakeAbuseControlServiceImpl(AbuseControlConfig config, Eventloop eventloop) {
        this.config = config != null ? config : AbuseControlConfig.defaults();
        this.eventloop = eventloop;
    }

    @Override
    public Promise<AbuseCheckResult> checkAbuse(
        DmOperationContext ctx,
        String clientIp,
        String email,
        Map<String, String> formData
    ) {
        return Promise.ofBlocking(eventloop, () -> {
            // Check rate limits per IP
            RateLimitTracker ipTracker = rateLimitTrackers.computeIfAbsent(
                "ip:" + clientIp,
                k -> new RateLimitTracker(config)
            );
            if (ipTracker.isRateLimited()) {
                LOG.warn("[P1-038] Rate limited for IP: {}", clientIp);
                return AbuseCheckResult.blocked(
                    "Rate limit exceeded",
                    "RATE_LIMITED",
                    60
                );
            }

            // Check rate limits per email if provided
            if (email != null && !email.isBlank()) {
                RateLimitTracker emailTracker = rateLimitTrackers.computeIfAbsent(
                    "email:" + email,
                    k -> new RateLimitTracker(config)
                );
                if (emailTracker.isRateLimited()) {
                    LOG.warn("[P1-038] Rate limited for email: {}", redactEmail(email));
                    return AbuseCheckResult.blocked(
                        "Rate limit exceeded",
                        "RATE_LIMITED",
                        60
                    );
                }
            }

            // Check honeypot if enabled
            if (config.enableHoneypot() && formData != null) {
                String honeypot = formData.get("website_url"); // Common honeypot field name
                if (!validateHoneypot(honeypot)) {
                    LOG.warn("[P1-038] Honeypot triggered for IP: {}", clientIp);
                    return AbuseCheckResult.blocked(
                        "Bot detected via honeypot",
                        "HONEYPOT_TRIGGERED",
                        3600
                    );
                }
            }

            // Check for suspicious patterns
            if (formData != null && hasSuspiciousPatterns(formData)) {
                LOG.warn("[P1-038] Suspicious patterns detected for IP: {}", clientIp);
                return AbuseCheckResult.blocked(
                    "Suspicious submission pattern",
                    "SUSPICIOUS_PATTERN",
                    300
                );
            }

            return AbuseCheckResult.allowed();
        });
    }

    @Override
    public Promise<Void> recordSubmission(DmOperationContext ctx, String clientIp, String email) {
        return Promise.ofBlocking(eventloop, () -> {
            // Increment IP rate limit
            RateLimitTracker ipTracker = rateLimitTrackers.computeIfAbsent(
                "ip:" + clientIp,
                k -> new RateLimitTracker(config)
            );
            ipTracker.recordRequest();

            // Increment email rate limit if provided
            if (email != null && !email.isBlank()) {
                RateLimitTracker emailTracker = rateLimitTrackers.computeIfAbsent(
                    "email:" + email,
                    k -> new RateLimitTracker(config)
                );
                emailTracker.recordRequest();
            }
        });
    }

    @Override
    public boolean validateHoneypot(String honeypotValue) {
        // Honeypot field should be empty - if filled, it's a bot
        return honeypotValue == null || honeypotValue.isBlank();
    }

    @Override
    public Promise<Boolean> isDuplicateSubmission(DmOperationContext ctx, String email, String formHash) {
        if (!config.enableDuplicateDetection() || email == null || email.isBlank()) {
            return Promise.of(false);
        }

        return Promise.ofBlocking(eventloop, () -> {
            String key = email + ":" + formHash;
            Instant lastSubmission = recentSubmissions.get(key);
            Instant cutoff = Instant.now().minus(Duration.ofMinutes(config.duplicateWindowMinutes()));

            if (lastSubmission != null && lastSubmission.isAfter(cutoff)) {
                LOG.warn("[P1-038] Duplicate submission detected for email: {}", redactEmail(email));
                return true;
            }

            // Record this submission
            recentSubmissions.put(key, Instant.now());
            return false;
        });
    }

    private boolean hasSuspiciousPatterns(Map<String, String> formData) {
        // Check for common spam patterns
        if (formData == null) {
            return false;
        }

        // Check for excessive capitalization
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            String value = entry.getValue();
            if (value != null && value.length() > 5) {
                int upperCount = (int) value.chars().filter(Character::isUpperCase).count();
                if (upperCount > value.length() * 0.7) {
                    return true;
                }
            }
        }

        // Check for URL spam
        long urlCount = formData.values().stream()
            .filter(v -> v != null && v.matches(".*https?://.*"))
            .count();
        if (urlCount > 3) {
            return true;
        }

        return false;
    }

    /**
     * Redacts a raw email address for safe logging (P1-015 PII hardening).
     *
     * <p>Returns the first character of the local part, followed by asterisks and the domain.
     * Example: {@code j***@example.com}. Never logs the full address in plaintext.</p>
     */
    private static String redactEmail(String email) {
        if (email == null) {
            return "<null>";
        }
        int atIdx = email.indexOf('@');
        if (atIdx <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIdx);
    }

    /**
     * In-memory rate limit tracker.
     */
    private static class RateLimitTracker {
        private final AtomicInteger minuteCounter = new AtomicInteger(0);
        private final AtomicInteger hourCounter = new AtomicInteger(0);
        private final AtomicInteger dayCounter = new AtomicInteger(0);
        private final Instant createdAt;
        private final AbuseControlConfig config;

        RateLimitTracker(AbuseControlConfig config) {
            this.config = config;
            this.createdAt = Instant.now();
        }

        void recordRequest() {
            minuteCounter.incrementAndGet();
            hourCounter.incrementAndGet();
            dayCounter.incrementAndGet();
        }

        boolean isRateLimited() {
            Instant now = Instant.now();
            Duration age = Duration.between(createdAt, now);

            // Reset counters based on time windows
            if (age.toMinutes() >= 1) {
                minuteCounter.set(0);
            }
            if (age.toHours() >= 1) {
                hourCounter.set(0);
            }
            if (age.toDays() >= 1) {
                dayCounter.set(0);
            }

            // Check limits
            return minuteCounter.get() >= config.maxRequestsPerMinute() ||
                   hourCounter.get() >= config.maxRequestsPerHour() ||
                   dayCounter.get() >= config.maxRequestsPerDay();
        }
    }
}
