/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.hitl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default implementation of smart notification service.
 * <p>
 * Uses rule-based heuristics to determine when notifications are needed:
 * - Priority-based notification rules
 * - Rate limiting to avoid notification fatigue
 * - Time-based batching for lower-priority items
 * - Context-aware notification channels
 *
 * @doc.type class
 * @doc.purpose Rule-based smart notification service
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class DefaultSmartNotificationService implements SmartNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultSmartNotificationService.class);

    private final Map<String, TenantNotificationStats> statsByTenant = new ConcurrentHashMap<>();

    // Notification rules by priority
    private static final Map<Priority, NotificationRule> RULES = Map.of(
        Priority.CRITICAL, new NotificationRule(true, "in-app", Duration.ofMinutes(0), 1.0),
        Priority.HIGH, new NotificationRule(true, "in-app", Duration.ofMinutes(5), 0.9),
        Priority.MEDIUM, new NotificationRule(true, "in-app", Duration.ofMinutes(15), 0.7),
        Priority.LOW, new NotificationRule(true, "email", Duration.ofHours(1), 0.5),
        Priority.INFO, new NotificationRule(false, "digest", Duration.ofHours(4), 0.3)
    );

    @Override
    public NotificationDecision shouldNotify(String itemId, Priority priority, Map<String, Object> context) {
        String tenantId = context != null ? (String) context.get("tenantId") : "default";
        TenantNotificationStats stats = statsByTenant.computeIfAbsent(tenantId, ignored -> new TenantNotificationStats());

        NotificationRule rule = RULES.getOrDefault(priority, RULES.get(Priority.INFO));

        // Check rate limiting
        if (!checkRateLimit(stats, rule)) {
            logger.debug("Rate limited notification for tenant={}, priority={}", tenantId, priority);
            return new NotificationDecision(false, null, "rate_limited", Map.of());
        }

        // Check if notification is enabled for this priority
        if (!rule.enabled()) {
            logger.debug("Notification disabled for priority={}", priority);
            return new NotificationDecision(false, null, "priority_disabled", Map.of());
        }

        // Check confidence threshold
        if (context != null && context.containsKey("confidence")) {
            double confidence = ((Number) context.get("confidence")).doubleValue();
            if (confidence < rule.confidenceThreshold()) {
                logger.debug("Confidence below threshold for priority={}: {} < {}", priority, confidence, rule.confidenceThreshold());
                return new NotificationDecision(false, null, "low_confidence", Map.of("confidence", confidence));
            }
        }

        // Determine channel based on priority and context
        String channel = determineChannel(priority, context);

        stats.incrementSent();
        logger.info("Notification sent: tenant={}, itemId={}, priority={}, channel={}", tenantId, itemId, priority, channel);

        return new NotificationDecision(true, channel, "notification_required", Map.of(
            "priority", priority.name(),
            "ruleEnabled", rule.enabled(),
            "minInterval", rule.minInterval().toMinutes()
        ));
    }

    @Override
    public void recordResponseTime(String itemId, long responseTimeMs) {
        // Extract tenant from itemId (format: tenantId-itemId)
        String tenantId = extractTenantId(itemId);
        if (tenantId != null) {
            TenantNotificationStats stats = statsByTenant.get(tenantId);
            if (stats != null) {
                stats.recordResponseTime(responseTimeMs);
                stats.incrementResponded();
                logger.debug("Recorded response time: tenant={}, time={}ms", tenantId, responseTimeMs);
            }
        }
    }

    @Override
    public void recordDismissal(String itemId, String reason) {
        String tenantId = extractTenantId(itemId);
        if (tenantId != null) {
            TenantNotificationStats stats = statsByTenant.get(tenantId);
            if (stats != null) {
                stats.incrementDismissed();
                logger.debug("Recorded dismissal: tenant={}, reason={}", tenantId, reason);
            }
        }
    }

    @Override
    public NotificationStats getStats(String tenantId) {
        TenantNotificationStats stats = statsByTenant.getOrDefault(tenantId, new TenantNotificationStats());
        return new NotificationStats(
            stats.totalSent(),
            stats.totalDismissed(),
            stats.totalResponded(),
            stats.averageResponseTimeMs(),
            stats.notificationRate()
        );
    }

    private boolean checkRateLimit(TenantNotificationStats stats, NotificationRule rule) {
        Instant lastNotification = stats.lastNotificationTime();
        if (lastNotification == null) {
            return true;
        }

        Duration timeSinceLast = Duration.between(lastNotification, Instant.now());
        return timeSinceLast.compareTo(rule.minInterval()) >= 0;
    }

    private String determineChannel(Priority priority, Map<String, Object> context) {
        // Default channel from rule
        String channel = RULES.getOrDefault(priority, RULES.get(Priority.INFO)).channel();

        // Override based on context
        if (context != null) {
            Boolean userOnline = (Boolean) context.get("userOnline");
            if (Boolean.FALSE.equals(userOnline) && priority == Priority.HIGH) {
                channel = "email"; // Send email if user is offline
            }
            
            String preferredChannel = (String) context.get("preferredChannel");
            if (preferredChannel != null) {
                channel = preferredChannel;
            }
        }

        return channel;
    }

    private String extractTenantId(String itemId) {
        if (itemId == null) return null;
        int dashIndex = itemId.indexOf('-');
        if (dashIndex > 0) {
            return itemId.substring(0, dashIndex);
        }
        return null;
    }

    private static class NotificationRule {
        private final boolean enabled;
        private final String channel;
        private final Duration minInterval;
        private final double confidenceThreshold;

        NotificationRule(boolean enabled, String channel, Duration minInterval, double confidenceThreshold) {
            this.enabled = enabled;
            this.channel = channel;
            this.minInterval = minInterval;
            this.confidenceThreshold = confidenceThreshold;
        }

        boolean enabled() { return enabled; }
        String channel() { return channel; }
        Duration minInterval() { return minInterval; }
        double confidenceThreshold() { return confidenceThreshold; }
    }

    private static class TenantNotificationStats {
        private final AtomicLong totalSent = new AtomicLong(0);
        private final AtomicLong totalDismissed = new AtomicLong(0);
        private final AtomicLong totalResponded = new AtomicLong(0);
        private final AtomicLong totalResponseTimeMs = new AtomicLong(0);
        private final AtomicLong responseCount = new AtomicLong(0);
        private volatile Instant lastNotificationTime;

        void incrementSent() {
            totalSent.incrementAndGet();
            lastNotificationTime = Instant.now();
        }

        void incrementDismissed() { totalDismissed.incrementAndGet(); }
        void incrementResponded() { totalResponded.incrementAndGet(); }

        void recordResponseTime(long responseTimeMs) {
            totalResponseTimeMs.addAndGet(responseTimeMs);
            responseCount.incrementAndGet();
        }

        long totalSent() { return totalSent.get(); }
        long totalDismissed() { return totalDismissed.get(); }
        long totalResponded() { return totalResponded.get(); }

        Instant lastNotificationTime() { return lastNotificationTime; }

        double averageResponseTimeMs() {
            long count = responseCount.get();
            if (count == 0) return 0.0;
            return (double) totalResponseTimeMs.get() / count;
        }

        double notificationRate() {
            long sent = totalSent.get();
            long dismissed = totalDismissed.get();
            if (sent == 0) return 0.0;
            return (double) (sent - dismissed) / sent;
        }
    }
}
