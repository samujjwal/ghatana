/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.reflex;

import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Production-grade ALERT action handler for the Reflex engine.
 *
 * <p>Replaces the stub ALERT handler with a full implementation that:
 * <ul>
 *   <li>Creates structured {@link Alert} records with rich metadata</li>
 *   <li>Validates severity levels (CRITICAL, HIGH, MEDIUM, LOW, INFO)</li>
 *   <li>Applies per-rule rate limiting to prevent alert fatigue</li>
 *   <li>Deduplicates identical alerts within a configurable window</li>
 *   <li>Stores alerts in-memory for querying and auditing</li>
 *   <li>Enriches alerts with trigger and rule context</li>
 *   <li>Provides query APIs: by tenant, severity, rule, time range</li>
 * </ul>
 *
 * <h2>Rate Limiting</h2>
 * <p>Each rule has a configurable rate limit window (default 60s). Alerts from
 * the same rule with the same severity within the window are rate-limited:
 * only the first alert fires immediately; subsequent duplicates are counted
 * but suppressed. When a suppressed alert's window expires, the next alert
 * includes the suppression count.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>All mutable state uses concurrent data structures ({@link ConcurrentHashMap},
 * {@link ConcurrentSkipListMap}, {@link AtomicLong}). Safe for multi-threaded
 * reflex engine use.</p>
 *
 * @doc.type class
 * @doc.purpose ALERT action handler with rate limiting and audit trail
 * @doc.layer core
 * @doc.pattern Strategy, Repository
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public class AlertActionHandler implements ReflexEngine.ActionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AlertActionHandler.class);

    // ═══════════════════════════════════════════════════════════════════════════
    // Configuration
    // ═══════════════════════════════════════════════════════════════════════════

    /** Default rate-limit window per rule+severity combination. */
    private static final Duration DEFAULT_RATE_LIMIT_WINDOW = Duration.ofSeconds(60);

    /** Maximum alerts to retain in memory per tenant. */
    private static final int DEFAULT_MAX_ALERTS_PER_TENANT = 10_000;

    private final Duration rateLimitWindow;
    private final int maxAlertsPerTenant;

    // ═══════════════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════════════

    /** Alert store: tenantId → (timestamp → Alert), ordered by time. */
    private final ConcurrentHashMap<String, ConcurrentSkipListMap<Instant, Alert>> alertStore;

    /** Rate-limit tracker: dedup key → RateLimitEntry. */
    private final ConcurrentHashMap<String, RateLimitEntry> rateLimitTracker;

    /** Metrics. */
    private final AtomicLong totalAlertsFired = new AtomicLong();
    private final AtomicLong totalAlertsSuppressed = new AtomicLong();
    private final AtomicLong totalAlertsStored = new AtomicLong();

    // ═══════════════════════════════════════════════════════════════════════════
    // Construction
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates an AlertActionHandler with default configuration.
     */
    public AlertActionHandler() {
        this(DEFAULT_RATE_LIMIT_WINDOW, DEFAULT_MAX_ALERTS_PER_TENANT);
    }

    /**
     * Creates an AlertActionHandler with custom configuration.
     *
     * @param rateLimitWindow    window for deduplication per rule+severity
     * @param maxAlertsPerTenant maximum alerts to retain per tenant
     */
    public AlertActionHandler(Duration rateLimitWindow, int maxAlertsPerTenant) {
        if (rateLimitWindow == null || rateLimitWindow.isNegative()) {
            throw new IllegalArgumentException("rateLimitWindow must be non-null and non-negative");
        }
        if (maxAlertsPerTenant < 1) {
            throw new IllegalArgumentException("maxAlertsPerTenant must be >= 1");
        }
        this.rateLimitWindow = rateLimitWindow;
        this.maxAlertsPerTenant = maxAlertsPerTenant;
        this.alertStore = new ConcurrentHashMap<>();
        this.rateLimitTracker = new ConcurrentHashMap<>();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ActionHandler Contract
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Promise<ReflexOutcome> execute(
            ReflexRule.Action action,
            ReflexTrigger trigger,
            ReflexRule rule) {

        Instant start = Instant.now();
        String tenantId = resolveTenantId(trigger);

        try {
            // 1. Extract and validate parameters
            Severity severity = extractSeverity(action);
            String message = extractMessage(action, rule);
            String channel = extractChannel(action);

            // 2. Check rate limiting
            String dedupKey = buildDedupKey(rule.getId(), severity, tenantId);
            RateLimitDecision decision = checkRateLimit(dedupKey, start);

            if (decision.suppressed) {
                totalAlertsSuppressed.incrementAndGet();
                LOG.debug("Alert suppressed (rate-limited): rule={}, severity={}, " +
                                "suppressedCount={}, tenant={}",
                        rule.getId(), severity, decision.suppressedCount, tenantId);

                return Promise.of(ReflexOutcome.builder()
                        .outcomeId("out-" + System.nanoTime())
                        .ruleId(rule.getId())
                        .triggerId(trigger.getTriggerId())
                        .actionType(ReflexRule.ActionType.ALERT)
                        .status(ReflexOutcome.Status.SKIPPED)
                        .startTime(start)
                        .endTime(Instant.now())
                        .duration(Duration.between(start, Instant.now()))
                        .output(Map.of(
                                "suppressed", true,
                                "reason", "rate_limited",
                                "suppressedCount", decision.suppressedCount,
                                "dedupKey", dedupKey
                        ))
                        .tenantId(tenantId)
                        .build());
            }

            // 3. Build the alert
            Alert alert = buildAlert(severity, message, channel, trigger, rule, tenantId,
                    decision.priorSuppressionCount, start);

            // 4. Store the alert
            storeAlert(alert, tenantId);
            totalAlertsFired.incrementAndGet();
            totalAlertsStored.incrementAndGet();

            // 5. Log at appropriate level based on severity
            logAlert(alert);

            // 6. Return success outcome with full alert details
            Map<String, Object> output = buildOutputMap(alert, decision.priorSuppressionCount);

            return Promise.of(ReflexOutcome.success(
                    rule.getId(),
                    trigger.getTriggerId(),
                    ReflexRule.ActionType.ALERT,
                    start,
                    output,
                    tenantId));

        } catch (Exception e) {
            LOG.error("Alert handler failed: rule={}, trigger={}, error={}",
                    rule.getId(), trigger.getTriggerId(), e.getMessage(), e);
            return Promise.of(ReflexOutcome.failure(
                    rule.getId(),
                    trigger.getTriggerId(),
                    ReflexRule.ActionType.ALERT,
                    start,
                    e,
                    tenantId));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Parameter Extraction & Validation
    // ═══════════════════════════════════════════════════════════════════════════

    Severity extractSeverity(ReflexRule.Action action) {
        Object raw = action.getParameters().get("severity");
        if (raw == null) {
            return Severity.INFO;
        }
        String val = raw.toString().trim();
        if (val.isEmpty()) {
            return Severity.INFO;
        }
        try {
            return Severity.valueOf(val.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            LOG.warn("Unknown alert severity '{}', defaulting to INFO", val);
            return Severity.INFO;
        }
    }

    String extractMessage(ReflexRule.Action action, ReflexRule rule) {
        Object raw = action.getParameters().get("message");
        if (raw != null && !raw.toString().isBlank()) {
            return raw.toString().trim();
        }
        // Fall back to rule name or a generic description
        if (rule.getName() != null && !rule.getName().isBlank()) {
            return "Alert from rule: " + rule.getName();
        }
        return "Alert triggered by rule " + rule.getId();
    }

    String extractChannel(ReflexRule.Action action) {
        Object raw = action.getParameters().get("channel");
        return raw != null ? raw.toString().trim() : "default";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rate Limiting
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Checks whether this alert should be rate-limited.
     *
     * @param dedupKey the deduplication key (ruleId:severity:tenantId)
     * @param now      the current time
     * @return decision indicating whether to suppress, and suppression counts
     */
    RateLimitDecision checkRateLimit(String dedupKey, Instant now) {
        if (rateLimitWindow.isZero()) {
            // Rate limiting disabled
            return new RateLimitDecision(false, 0, 0);
        }

        RateLimitEntry entry = rateLimitTracker.get(dedupKey);
        if (entry == null) {
            // First alert for this key — allow
            rateLimitTracker.put(dedupKey, new RateLimitEntry(now, 0));
            return new RateLimitDecision(false, 0, 0);
        }

        Duration elapsed = Duration.between(entry.firstSeen, now);
        if (elapsed.compareTo(rateLimitWindow) >= 0) {
            // Window expired — reset and allow, carrying forward suppression count
            int priorSuppressed = entry.suppressedCount;
            rateLimitTracker.put(dedupKey, new RateLimitEntry(now, 0));
            return new RateLimitDecision(false, 0, priorSuppressed);
        }

        // Within window — suppress
        entry.suppressedCount++;
        return new RateLimitDecision(true, entry.suppressedCount, 0);
    }

    String buildDedupKey(String ruleId, Severity severity, String tenantId) {
        return tenantId + ":" + ruleId + ":" + severity.name();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Alert Construction
    // ═══════════════════════════════════════════════════════════════════════════

    private Alert buildAlert(
            Severity severity,
            String message,
            String channel,
            ReflexTrigger trigger,
            ReflexRule rule,
            String tenantId,
            int priorSuppressionCount,
            Instant timestamp) {

        Alert.AlertBuilder builder = Alert.builder()
                .alertId("alert-" + System.nanoTime())
                .severity(severity)
                .message(message)
                .channel(channel)
                .tenantId(tenantId)
                .timestamp(timestamp)

                // Rule context
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .ruleCategory(rule.getCategory())
                .rulePriority(rule.getPriority())
                .ruleRiskLevel(rule.getRiskLevel())

                // Trigger context
                .triggerId(trigger.getTriggerId())
                .triggerType(trigger.getType())
                .triggerSource(trigger.getSource())
                .triggerConfidence(trigger.getConfidence())
                .triggerFeatures(Map.copyOf(trigger.getFeatures()));

        if (trigger.getPatternId() != null) {
            builder.patternId(trigger.getPatternId());
        }

        if (priorSuppressionCount > 0) {
            builder.priorSuppressionCount(priorSuppressionCount);
        }

        return builder.build();
    }

    private Map<String, Object> buildOutputMap(Alert alert, int priorSuppressionCount) {
        Map<String, Object> output = new HashMap<>();
        output.put("alertId", alert.getAlertId());
        output.put("severity", alert.getSeverity().name());
        output.put("message", alert.getMessage());
        output.put("channel", alert.getChannel());
        output.put("timestamp", alert.getTimestamp().toString());
        output.put("ruleId", alert.getRuleId());
        output.put("triggerId", alert.getTriggerId());
        output.put("triggerType", alert.getTriggerType().name());
        output.put("fired", true);

        if (priorSuppressionCount > 0) {
            output.put("priorSuppressedAlerts", priorSuppressionCount);
        }
        return Collections.unmodifiableMap(output);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Storage
    // ═══════════════════════════════════════════════════════════════════════════

    private void storeAlert(Alert alert, String tenantId) {
        ConcurrentSkipListMap<Instant, Alert> tenantAlerts =
                alertStore.computeIfAbsent(tenantId, k -> new ConcurrentSkipListMap<>());

        // Use nanos offset to avoid duplicate keys at same instant
        Instant key = alert.getTimestamp();
        while (tenantAlerts.containsKey(key)) {
            key = key.plusNanos(1);
        }
        tenantAlerts.put(key, alert);

        // Evict oldest if exceeding capacity
        while (tenantAlerts.size() > maxAlertsPerTenant) {
            tenantAlerts.pollFirstEntry();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Logging
    // ═══════════════════════════════════════════════════════════════════════════

    private void logAlert(Alert alert) {
        String logMsg = "Reflex ALERT [{}]: {} — rule={}, trigger={}, tenant={}";
        switch (alert.getSeverity()) {
            case CRITICAL:
                LOG.error(logMsg, alert.getSeverity(), alert.getMessage(),
                        alert.getRuleId(), alert.getTriggerId(), alert.getTenantId());
                break;
            case HIGH:
                LOG.warn(logMsg, alert.getSeverity(), alert.getMessage(),
                        alert.getRuleId(), alert.getTriggerId(), alert.getTenantId());
                break;
            case MEDIUM:
                LOG.warn(logMsg, alert.getSeverity(), alert.getMessage(),
                        alert.getRuleId(), alert.getTriggerId(), alert.getTenantId());
                break;
            case LOW:
                LOG.info(logMsg, alert.getSeverity(), alert.getMessage(),
                        alert.getRuleId(), alert.getTriggerId(), alert.getTenantId());
                break;
            case INFO:
            default:
                LOG.info(logMsg, alert.getSeverity(), alert.getMessage(),
                        alert.getRuleId(), alert.getTriggerId(), alert.getTenantId());
                break;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Query APIs
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets all stored alerts for a tenant, ordered by timestamp (newest first).
     *
     * @param tenantId the tenant ID
     * @return list of alerts, newest first
     */
    public List<Alert> getAlerts(String tenantId) {
        ConcurrentSkipListMap<Instant, Alert> tenantAlerts = alertStore.get(tenantId);
        if (tenantAlerts == null || tenantAlerts.isEmpty()) {
            return List.of();
        }
        List<Alert> result = new ArrayList<>(tenantAlerts.values());
        Collections.reverse(result);
        return Collections.unmodifiableList(result);
    }

    /**
     * Gets alerts for a tenant filtered by severity.
     *
     * @param tenantId the tenant ID
     * @param severity the severity to filter by
     * @return matching alerts, newest first
     */
    public List<Alert> getAlertsBySeverity(String tenantId, Severity severity) {
        return getAlerts(tenantId).stream()
                .filter(a -> a.getSeverity() == severity)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Gets alerts for a tenant filtered by rule ID.
     *
     * @param tenantId the tenant ID
     * @param ruleId   the rule ID
     * @return matching alerts, newest first
     */
    public List<Alert> getAlertsByRule(String tenantId, String ruleId) {
        return getAlerts(tenantId).stream()
                .filter(a -> ruleId.equals(a.getRuleId()))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Gets alerts within a time range for a tenant.
     *
     * @param tenantId the tenant ID
     * @param from     start time (inclusive)
     * @param to       end time (exclusive)
     * @return matching alerts, newest first
     */
    public List<Alert> getAlertsByTimeRange(String tenantId, Instant from, Instant to) {
        ConcurrentSkipListMap<Instant, Alert> tenantAlerts = alertStore.get(tenantId);
        if (tenantAlerts == null) {
            return List.of();
        }
        NavigableMap<Instant, Alert> sub = tenantAlerts.subMap(from, true, to, false);
        List<Alert> result = new ArrayList<>(sub.values());
        Collections.reverse(result);
        return Collections.unmodifiableList(result);
    }

    /**
     * Gets the total number of stored alerts for a tenant.
     *
     * @param tenantId the tenant ID
     * @return count of stored alerts
     */
    public int getAlertCount(String tenantId) {
        ConcurrentSkipListMap<Instant, Alert> tenantAlerts = alertStore.get(tenantId);
        return tenantAlerts != null ? tenantAlerts.size() : 0;
    }

    /**
     * Gets the total number of stored alerts across all tenants.
     *
     * @return total alert count
     */
    public int getTotalAlertCount() {
        return alertStore.values().stream().mapToInt(ConcurrentSkipListMap::size).sum();
    }

    /**
     * Clears all alerts for a tenant.
     *
     * @param tenantId the tenant ID
     */
    public void clearAlerts(String tenantId) {
        alertStore.remove(tenantId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Metrics
    // ═══════════════════════════════════════════════════════════════════════════

    /** Total alerts that passed rate limiting and were actually fired. */
    public long getTotalAlertsFired() { return totalAlertsFired.get(); }

    /** Total alerts that were suppressed by rate limiting. */
    public long getTotalAlertsSuppressed() { return totalAlertsSuppressed.get(); }

    /** Total alerts currently stored (across all tenants). */
    public long getTotalAlertsStored() { return totalAlertsStored.get(); }

    /** The rate limit window in use. */
    public Duration getRateLimitWindow() { return rateLimitWindow; }

    /** Maximum alerts per tenant. */
    public int getMaxAlertsPerTenant() { return maxAlertsPerTenant; }

    // ═══════════════════════════════════════════════════════════════════════════
    // Utility
    // ═══════════════════════════════════════════════════════════════════════════

    private String resolveTenantId(ReflexTrigger trigger) {
        return trigger.getTenantId() != null ? trigger.getTenantId() : "default";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Inner Types
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Alert severity levels, ordered from most to least critical.
     */
    public enum Severity {
        /** System-down or data-loss situations. */
        CRITICAL,
        /** Significant impact, needs immediate attention. */
        HIGH,
        /** Moderate impact, should be addressed. */
        MEDIUM,
        /** Minor, informational action needed. */
        LOW,
        /** Purely informational, no action required. */
        INFO
    }

    /**
     * An immutable alert record produced by the handler.
     */
    @Value
    @Builder
    public static class Alert {
        // ── Identity ──
        String alertId;
        Severity severity;
        String message;
        String channel;
        String tenantId;
        Instant timestamp;

        // ── Rule context ──
        String ruleId;
        String ruleName;
        String ruleCategory;
        int rulePriority;
        ReflexRule.RiskLevel ruleRiskLevel;

        // ── Trigger context ──
        String triggerId;
        ReflexTrigger.TriggerType triggerType;
        String triggerSource;
        float triggerConfidence;
        @Builder.Default
        Map<String, Object> triggerFeatures = Map.of();
        String patternId;

        // ── Suppression info ──
        @Builder.Default
        int priorSuppressionCount = 0;
    }

    /**
     * Mutable entry tracking rate-limit state for a dedup key.
     */
    static class RateLimitEntry {
        final Instant firstSeen;
        volatile int suppressedCount;

        RateLimitEntry(Instant firstSeen, int suppressedCount) {
            this.firstSeen = Objects.requireNonNull(firstSeen);
            this.suppressedCount = suppressedCount;
        }
    }

    /**
     * Immutable decision from the rate limiter.
     */
    static class RateLimitDecision {
        final boolean suppressed;
        final int suppressedCount;
        final int priorSuppressionCount;

        RateLimitDecision(boolean suppressed, int suppressedCount, int priorSuppressionCount) {
            this.suppressed = suppressed;
            this.suppressedCount = suppressedCount;
            this.priorSuppressionCount = priorSuppressionCount;
        }
    }
}
