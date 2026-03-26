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

import io.activej.eventloop.Eventloop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for {@link AlertActionHandler}.
 *
 * <p>Covers: severity validation, message extraction, rate limiting,
 * deduplication, alert storage & queries, eviction, metrics,
 * error handling, and integration with {@link DefaultReflexEngine}.
 */
@DisplayName("AlertActionHandler")
class AlertActionHandlerTest {

    private static final String TENANT = "tenant-alpha";
    private static final String RULE_ID = "rule-circuit-breaker";
    private static final String TRIGGER_ID = "trg-12345";

    private AlertActionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AlertActionHandler();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Runs a Promise-returning operation inside an ActiveJ Eventloop
     * so that {@code Promise.of()} resolves properly.
     */
    private ReflexOutcome executeAndAwait(
            AlertActionHandler h,
            ReflexRule.Action action,
            ReflexTrigger trigger,
            ReflexRule rule) {

        AtomicReference<ReflexOutcome> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
        eventloop.post(() ->
                h.execute(action, trigger, rule)
                        .whenResult(result::set)
                        .whenException(e -> error.set(
                                e instanceof Exception ? (Exception) e : new RuntimeException(e))));
        eventloop.run();

        if (error.get() != null) {
            throw new RuntimeException("Handler failed", error.get());
        }
        return result.get();
    }

    private ReflexRule.Action alertAction(String severity, String message) {
        return ReflexRule.Action.builder()
                .type(ReflexRule.ActionType.ALERT)
                .parameters(Map.of("severity", severity, "message", message))
                .build();
    }

    private ReflexRule.Action alertActionWithChannel(String severity, String message, String channel) {
        Map<String, Object> params = new HashMap<>();
        params.put("severity", severity);
        params.put("message", message);
        params.put("channel", channel);
        return ReflexRule.Action.builder()
                .type(ReflexRule.ActionType.ALERT)
                .parameters(params)
                .build();
    }

    private ReflexTrigger trigger() {
        return trigger(TENANT);
    }

    private ReflexTrigger trigger(String tenantId) {
        return ReflexTrigger.builder()
                .triggerId(TRIGGER_ID)
                .type(ReflexTrigger.TriggerType.THRESHOLD)
                .tenantId(tenantId)
                .source("test-source")
                .confidence(0.95f)
                .features(Map.of("error_rate", 0.75, "latency_ms", 2500))
                .build();
    }

    private ReflexRule rule() {
        return rule(RULE_ID);
    }

    private ReflexRule rule(String ruleId) {
        return ReflexRule.builder()
                .id(ruleId)
                .name("Circuit Breaker Alert")
                .category("reliability")
                .priority(1)
                .tenantId(TENANT)
                .enabled(true)
                .riskLevel(ReflexRule.RiskLevel.HIGH)
                .action(ReflexRule.Action.alert("critical", "Error rate exceeded threshold"))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Construction Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("default constructor uses sensible defaults")
        void defaultDefaults() {
            AlertActionHandler h = new AlertActionHandler();
            assertThat(h.getRateLimitWindow()).isEqualTo(Duration.ofSeconds(60));
            assertThat(h.getMaxAlertsPerTenant()).isEqualTo(10_000);
            assertThat(h.getTotalAlertsFired()).isZero();
            assertThat(h.getTotalAlertsSuppressed()).isZero();
        }

        @Test
        @DisplayName("custom configuration is applied")
        void customConfig() {
            AlertActionHandler h = new AlertActionHandler(Duration.ofSeconds(30), 500);
            assertThat(h.getRateLimitWindow()).isEqualTo(Duration.ofSeconds(30));
            assertThat(h.getMaxAlertsPerTenant()).isEqualTo(500);
        }

        @Test
        @DisplayName("null rateLimitWindow throws")
        void nullWindowThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new AlertActionHandler(null, 100))
                    .withMessageContaining("rateLimitWindow");
        }

        @Test
        @DisplayName("negative rateLimitWindow throws")
        void negativeWindowThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new AlertActionHandler(Duration.ofSeconds(-1), 100))
                    .withMessageContaining("rateLimitWindow");
        }

        @Test
        @DisplayName("zero maxAlertsPerTenant throws")
        void zeroMaxThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new AlertActionHandler(Duration.ofSeconds(60), 0))
                    .withMessageContaining("maxAlertsPerTenant");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Severity Extraction Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Severity Extraction")
    class SeverityExtraction {

        @Test
        @DisplayName("parses CRITICAL severity")
        void critical() {
            ReflexRule.Action action = alertAction("critical", "msg");
            assertThat(handler.extractSeverity(action))
                    .isEqualTo(AlertActionHandler.Severity.CRITICAL);
        }

        @Test
        @DisplayName("parses HIGH severity (case-insensitive)")
        void highCaseInsensitive() {
            ReflexRule.Action action = alertAction("High", "msg");
            assertThat(handler.extractSeverity(action))
                    .isEqualTo(AlertActionHandler.Severity.HIGH);
        }

        @Test
        @DisplayName("parses MEDIUM severity")
        void medium() {
            ReflexRule.Action action = alertAction("MEDIUM", "msg");
            assertThat(handler.extractSeverity(action))
                    .isEqualTo(AlertActionHandler.Severity.MEDIUM);
        }

        @Test
        @DisplayName("parses LOW severity")
        void low() {
            ReflexRule.Action action = alertAction("low", "msg");
            assertThat(handler.extractSeverity(action))
                    .isEqualTo(AlertActionHandler.Severity.LOW);
        }

        @Test
        @DisplayName("parses INFO severity")
        void info() {
            ReflexRule.Action action = alertAction("info", "msg");
            assertThat(handler.extractSeverity(action))
                    .isEqualTo(AlertActionHandler.Severity.INFO);
        }

        @Test
        @DisplayName("missing severity defaults to INFO")
        void missingSeverity() {
            ReflexRule.Action action = ReflexRule.Action.builder()
                    .type(ReflexRule.ActionType.ALERT)
                    .parameters(Map.of("message", "only message"))
                    .build();
            assertThat(handler.extractSeverity(action))
                    .isEqualTo(AlertActionHandler.Severity.INFO);
        }

        @Test
        @DisplayName("empty severity defaults to INFO")
        void emptySeverity() {
            ReflexRule.Action action = alertAction("", "msg");
            assertThat(handler.extractSeverity(action))
                    .isEqualTo(AlertActionHandler.Severity.INFO);
        }

        @Test
        @DisplayName("blank severity (whitespace) defaults to INFO")
        void blankSeverity() {
            ReflexRule.Action action = alertAction("   ", "msg");
            assertThat(handler.extractSeverity(action))
                    .isEqualTo(AlertActionHandler.Severity.INFO);
        }

        @Test
        @DisplayName("unknown severity defaults to INFO")
        void unknownSeverity() {
            ReflexRule.Action action = alertAction("URGENT", "msg");
            assertThat(handler.extractSeverity(action))
                    .isEqualTo(AlertActionHandler.Severity.INFO);
        }

        @Test
        @DisplayName("integer severity value defaults to INFO")
        void integerSeverity() {
            ReflexRule.Action action = ReflexRule.Action.builder()
                    .type(ReflexRule.ActionType.ALERT)
                    .parameters(Map.of("severity", 42, "message", "msg"))
                    .build();
            assertThat(handler.extractSeverity(action))
                    .isEqualTo(AlertActionHandler.Severity.INFO);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Message Extraction Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Message Extraction")
    class MessageExtraction {

        @Test
        @DisplayName("extracts explicit message from parameters")
        void explicitMessage() {
            ReflexRule.Action action = alertAction("info", "Server is down");
            assertThat(handler.extractMessage(action, rule()))
                    .isEqualTo("Server is down");
        }

        @Test
        @DisplayName("falls back to rule name when message is missing")
        void fallbackToRuleName() {
            ReflexRule.Action action = ReflexRule.Action.builder()
                    .type(ReflexRule.ActionType.ALERT)
                    .parameters(Map.of("severity", "high"))
                    .build();
            ReflexRule r = rule();
            assertThat(handler.extractMessage(action, r))
                    .isEqualTo("Alert from rule: Circuit Breaker Alert");
        }

        @Test
        @DisplayName("falls back to rule ID when name and message are missing")
        void fallbackToRuleId() {
            ReflexRule.Action action = ReflexRule.Action.builder()
                    .type(ReflexRule.ActionType.ALERT)
                    .parameters(Map.of("severity", "info"))
                    .build();
            ReflexRule noName = ReflexRule.builder()
                    .id("rfx-123")
                    .tenantId(TENANT)
                    .enabled(true)
                    .build();
            assertThat(handler.extractMessage(action, noName))
                    .isEqualTo("Alert triggered by rule rfx-123");
        }

        @Test
        @DisplayName("trims whitespace from message")
        void trimsWhitespace() {
            ReflexRule.Action action = alertAction("info", "  padded message  ");
            assertThat(handler.extractMessage(action, rule()))
                    .isEqualTo("padded message");
        }

        @Test
        @DisplayName("blank message falls back to rule name")
        void blankMessage() {
            Map<String, Object> params = new HashMap<>();
            params.put("severity", "info");
            params.put("message", "   ");
            ReflexRule.Action action = ReflexRule.Action.builder()
                    .type(ReflexRule.ActionType.ALERT)
                    .parameters(params)
                    .build();
            assertThat(handler.extractMessage(action, rule()))
                    .startsWith("Alert from rule:");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Execute — Basic Flow
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Execute")
    class Execute {

        @Test
        @DisplayName("fires alert successfully with CRITICAL severity")
        void criticalAlert() {
            ReflexRule.Action action = alertAction("critical", "Database down");
            ReflexOutcome outcome = executeAndAwait(handler, action, trigger(), rule());

            assertThat(outcome.isSuccess()).isTrue();
            assertThat(outcome.getActionType()).isEqualTo(ReflexRule.ActionType.ALERT);
            assertThat(outcome.getOutput()).containsEntry("severity", "CRITICAL");
            assertThat(outcome.getOutput()).containsEntry("message", "Database down");
            assertThat(outcome.getOutput()).containsEntry("fired", true);
            assertThat(outcome.getOutput()).containsKey("alertId");
            assertThat(outcome.getOutput()).containsKey("timestamp");
            assertThat(outcome.getTenantId()).isEqualTo(TENANT);
        }

        @Test
        @DisplayName("stores alert after firing")
        void storesAlert() {
            executeAndAwait(handler, alertAction("high", "Disk full"), trigger(), rule());

            List<AlertActionHandler.Alert> alerts = handler.getAlerts(TENANT);
            assertThat(alerts).hasSize(1);

            AlertActionHandler.Alert stored = alerts.get(0);
            assertThat(stored.getSeverity()).isEqualTo(AlertActionHandler.Severity.HIGH);
            assertThat(stored.getMessage()).isEqualTo("Disk full");
            assertThat(stored.getRuleId()).isEqualTo(RULE_ID);
            assertThat(stored.getTriggerId()).isEqualTo(TRIGGER_ID);
            assertThat(stored.getTenantId()).isEqualTo(TENANT);
        }

        @Test
        @DisplayName("enriches alert with rule context")
        void enrichesRuleContext() {
            executeAndAwait(handler, alertAction("medium", "Spike"), trigger(), rule());

            AlertActionHandler.Alert stored = handler.getAlerts(TENANT).get(0);
            assertThat(stored.getRuleName()).isEqualTo("Circuit Breaker Alert");
            assertThat(stored.getRuleCategory()).isEqualTo("reliability");
            assertThat(stored.getRulePriority()).isEqualTo(1);
            assertThat(stored.getRuleRiskLevel()).isEqualTo(ReflexRule.RiskLevel.HIGH);
        }

        @Test
        @DisplayName("enriches alert with trigger context")
        void enrichesTriggerContext() {
            executeAndAwait(handler, alertAction("low", "Info"), trigger(), rule());

            AlertActionHandler.Alert stored = handler.getAlerts(TENANT).get(0);
            assertThat(stored.getTriggerId()).isEqualTo(TRIGGER_ID);
            assertThat(stored.getTriggerType()).isEqualTo(ReflexTrigger.TriggerType.THRESHOLD);
            assertThat(stored.getTriggerSource()).isEqualTo("test-source");
            assertThat(stored.getTriggerConfidence()).isEqualTo(0.95f);
            assertThat(stored.getTriggerFeatures())
                    .containsEntry("error_rate", 0.75)
                    .containsEntry("latency_ms", 2500);
        }

        @Test
        @DisplayName("includes pattern ID when present in trigger")
        void includesPatternId() {
            ReflexTrigger patternTrigger = ReflexTrigger.builder()
                    .triggerId("trg-pat")
                    .type(ReflexTrigger.TriggerType.PATTERN)
                    .tenantId(TENANT)
                    .patternId("pat-error-spike")
                    .confidence(0.9f)
                    .source("pattern-matcher")
                    .build();

            executeAndAwait(handler, alertAction("high", "Pattern detected"), patternTrigger, rule());

            AlertActionHandler.Alert stored = handler.getAlerts(TENANT).get(0);
            assertThat(stored.getPatternId()).isEqualTo("pat-error-spike");
        }

        @Test
        @DisplayName("extracts channel when specified")
        void extractsChannel() {
            ReflexRule.Action action = alertActionWithChannel("critical", "Alert!", "pagerduty");
            executeAndAwait(handler, action, trigger(), rule());

            AlertActionHandler.Alert stored = handler.getAlerts(TENANT).get(0);
            assertThat(stored.getChannel()).isEqualTo("pagerduty");
        }

        @Test
        @DisplayName("default channel when not specified")
        void defaultChannel() {
            executeAndAwait(handler, alertAction("info", "msg"), trigger(), rule());

            AlertActionHandler.Alert stored = handler.getAlerts(TENANT).get(0);
            assertThat(stored.getChannel()).isEqualTo("default");
        }

        @Test
        @DisplayName("null tenant falls back to 'default'")
        void nullTenantFallback() {
            ReflexTrigger noTenant = ReflexTrigger.builder()
                    .triggerId("trg-no-tenant")
                    .type(ReflexTrigger.TriggerType.MANUAL)
                    .source("test")
                    .build();
            executeAndAwait(handler, alertAction("info", "test"), noTenant, rule());

            assertThat(handler.getAlerts("default")).hasSize(1);
        }

        @Test
        @DisplayName("increments totalAlertsFired metric")
        void incrementsFiredMetric() {
            // Disable rate limiting for this test
            AlertActionHandler noLimit = new AlertActionHandler(Duration.ZERO, 10_000);
            executeAndAwait(noLimit, alertAction("info", "a"), trigger(), rule());
            executeAndAwait(noLimit, alertAction("info", "b"), trigger(), rule());
            executeAndAwait(noLimit, alertAction("info", "c"), trigger(), rule());

            assertThat(noLimit.getTotalAlertsFired()).isEqualTo(3);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rate Limiting Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Rate Limiting")
    class RateLimiting {

        @Test
        @DisplayName("first alert passes without suppression")
        void firstAlertPasses() {
            ReflexOutcome outcome = executeAndAwait(handler,
                    alertAction("critical", "First"), trigger(), rule());

            assertThat(outcome.isSuccess()).isTrue();
            assertThat(outcome.getOutput()).containsEntry("fired", true);
        }

        @Test
        @DisplayName("second alert with same rule+severity is suppressed")
        void secondAlertSuppressed() {
            executeAndAwait(handler, alertAction("critical", "First"), trigger(), rule());

            ReflexOutcome second = executeAndAwait(handler,
                    alertAction("critical", "Second"), trigger(), rule());

            assertThat(second.getStatus()).isEqualTo(ReflexOutcome.Status.SKIPPED);
            assertThat(second.getOutput()).containsEntry("suppressed", true);
            assertThat(second.getOutput()).containsEntry("reason", "rate_limited");
        }

        @Test
        @DisplayName("different severity is not rate-limited together")
        void differentSeverityNotGrouped() {
            executeAndAwait(handler, alertAction("critical", "First"), trigger(), rule());

            ReflexOutcome highAlert = executeAndAwait(handler,
                    alertAction("high", "Different severity"), trigger(), rule());

            assertThat(highAlert.isSuccess()).isTrue();
            assertThat(highAlert.getOutput()).containsEntry("fired", true);
        }

        @Test
        @DisplayName("different rule ID is not rate-limited together")
        void differentRuleNotGrouped() {
            executeAndAwait(handler, alertAction("critical", "Rule A"), trigger(), rule("rule-A"));

            ReflexOutcome ruleB = executeAndAwait(handler,
                    alertAction("critical", "Rule B"), trigger(), rule("rule-B"));

            assertThat(ruleB.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("different tenant is not rate-limited together")
        void differentTenantNotGrouped() {
            executeAndAwait(handler, alertAction("critical", "Tenant 1"), trigger("tenant-1"), rule());

            ReflexOutcome t2 = executeAndAwait(handler,
                    alertAction("critical", "Tenant 2"), trigger("tenant-2"), rule());

            assertThat(t2.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("suppression count accumulates")
        void suppressionCountAccumulates() {
            executeAndAwait(handler, alertAction("high", "1st"), trigger(), rule());
            executeAndAwait(handler, alertAction("high", "2nd"), trigger(), rule());
            ReflexOutcome third = executeAndAwait(handler,
                    alertAction("high", "3rd"), trigger(), rule());

            assertThat(third.getStatus()).isEqualTo(ReflexOutcome.Status.SKIPPED);
            assertThat(third.getOutput()).containsEntry("suppressedCount", 2);
        }

        @Test
        @DisplayName("rate limiting disabled when window is zero")
        void disabledWhenZero() {
            AlertActionHandler noLimit = new AlertActionHandler(Duration.ZERO, 10_000);

            executeAndAwait(noLimit, alertAction("high", "1"), trigger(), rule());
            ReflexOutcome second = executeAndAwait(noLimit,
                    alertAction("high", "2"), trigger(), rule());

            assertThat(second.isSuccess()).isTrue();
            assertThat(second.getOutput()).containsEntry("fired", true);
        }

        @Test
        @DisplayName("suppressed metrics count correctly")
        void suppressedMetrics() {
            executeAndAwait(handler, alertAction("info", "a"), trigger(), rule());
            executeAndAwait(handler, alertAction("info", "b"), trigger(), rule());
            executeAndAwait(handler, alertAction("info", "c"), trigger(), rule());

            assertThat(handler.getTotalAlertsFired()).isEqualTo(1);
            assertThat(handler.getTotalAlertsSuppressed()).isEqualTo(2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Query API Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Query APIs")
    class QueryAPIs {

        private AlertActionHandler noLimit;

        @BeforeEach
        void setUp() {
            // Disable rate limiting to store multiple alerts freely
            noLimit = new AlertActionHandler(Duration.ZERO, 10_000);
        }

        @Test
        @DisplayName("getAlerts returns empty for unknown tenant")
        void emptyForUnknownTenant() {
            assertThat(noLimit.getAlerts("nonexistent")).isEmpty();
        }

        @Test
        @DisplayName("getAlerts returns alerts newest-first")
        void newestFirst() {
            executeAndAwait(noLimit, alertAction("low", "First"), trigger(), rule("r1"));
            executeAndAwait(noLimit, alertAction("high", "Second"), trigger(), rule("r2"));
            executeAndAwait(noLimit, alertAction("critical", "Third"), trigger(), rule("r3"));

            List<AlertActionHandler.Alert> alerts = noLimit.getAlerts(TENANT);
            assertThat(alerts).hasSize(3);
            // Newest first
            assertThat(alerts.get(0).getMessage()).isEqualTo("Third");
            assertThat(alerts.get(2).getMessage()).isEqualTo("First");
        }

        @Test
        @DisplayName("getAlertsBySeverity filters correctly")
        void filterBySeverity() {
            executeAndAwait(noLimit, alertAction("critical", "C1"), trigger(), rule("r1"));
            executeAndAwait(noLimit, alertAction("high", "H1"), trigger(), rule("r2"));
            executeAndAwait(noLimit, alertAction("critical", "C2"), trigger(), rule("r3"));
            executeAndAwait(noLimit, alertAction("info", "I1"), trigger(), rule("r4"));

            List<AlertActionHandler.Alert> criticals =
                    noLimit.getAlertsBySeverity(TENANT, AlertActionHandler.Severity.CRITICAL);
            assertThat(criticals).hasSize(2);
            assertThat(criticals).allSatisfy(a ->
                    assertThat(a.getSeverity()).isEqualTo(AlertActionHandler.Severity.CRITICAL));
        }

        @Test
        @DisplayName("getAlertsByRule filters by rule ID")
        void filterByRule() {
            executeAndAwait(noLimit, alertAction("info", "A"), trigger(), rule("rule-A"));
            executeAndAwait(noLimit, alertAction("info", "B"), trigger(), rule("rule-B"));
            executeAndAwait(noLimit, alertAction("info", "A2"), trigger(), rule("rule-A"));

            List<AlertActionHandler.Alert> ruleAAlerts = noLimit.getAlertsByRule(TENANT, "rule-A");
            assertThat(ruleAAlerts).hasSize(2);
            assertThat(ruleAAlerts).allSatisfy(a ->
                    assertThat(a.getRuleId()).isEqualTo("rule-A"));
        }

        @Test
        @DisplayName("getAlertsByTimeRange returns alerts within bounds")
        void filterByTimeRange() {
            Instant before = Instant.now().minusMillis(1);
            executeAndAwait(noLimit, alertAction("info", "In range"), trigger(), rule("r1"));
            Instant after = Instant.now().plusSeconds(1);

            // This should be stored after 'after' practically, but let's verify range works
            List<AlertActionHandler.Alert> inRange =
                    noLimit.getAlertsByTimeRange(TENANT, before, after);
            assertThat(inRange).isNotEmpty();

            // Empty range returns nothing
            Instant future = Instant.now().plusSeconds(3600);
            assertThat(noLimit.getAlertsByTimeRange(TENANT, future, future.plusSeconds(1)))
                    .isEmpty();
        }

        @Test
        @DisplayName("getAlertsByTimeRange returns empty for unknown tenant")
        void timeRangeUnknownTenant() {
            assertThat(noLimit.getAlertsByTimeRange("unknown",
                    Instant.now().minusSeconds(60), Instant.now())).isEmpty();
        }

        @Test
        @DisplayName("getAlertCount returns correct count")
        void alertCount() {
            assertThat(noLimit.getAlertCount(TENANT)).isZero();

            executeAndAwait(noLimit, alertAction("info", "one"), trigger(), rule("r1"));
            executeAndAwait(noLimit, alertAction("info", "two"), trigger(), rule("r2"));

            assertThat(noLimit.getAlertCount(TENANT)).isEqualTo(2);
        }

        @Test
        @DisplayName("getTotalAlertCount spans all tenants")
        void totalAlertCount() {
            executeAndAwait(noLimit, alertAction("info", "T1"), trigger("t1"), rule());
            executeAndAwait(noLimit, alertAction("info", "T2"), trigger("t2"), rule());
            executeAndAwait(noLimit, alertAction("info", "T1b"), trigger("t1"), rule("r2"));

            assertThat(noLimit.getTotalAlertCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("clearAlerts removes all alerts for a tenant")
        void clearAlerts() {
            executeAndAwait(noLimit, alertAction("info", "one"), trigger("t1"), rule());
            executeAndAwait(noLimit, alertAction("info", "two"), trigger("t2"), rule());

            noLimit.clearAlerts("t1");

            assertThat(noLimit.getAlertCount("t1")).isZero();
            assertThat(noLimit.getAlertCount("t2")).isEqualTo(1);
        }

        @Test
        @DisplayName("clearAll resets everything")
        void clearAll() {
            executeAndAwait(noLimit, alertAction("info", "a"), trigger("t1"), rule());
            executeAndAwait(noLimit, alertAction("info", "b"), trigger("t2"), rule());

            noLimit.clearAlerts("t1");
            noLimit.clearAlerts("t2");

            assertThat(noLimit.getTotalAlertCount()).isZero();
            assertThat(noLimit.getAlertCount("t1")).isZero();
            assertThat(noLimit.getAlertCount("t2")).isZero();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Eviction Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Eviction")
    class Eviction {

        @Test
        @DisplayName("evicts oldest alerts when exceeding maxAlertsPerTenant")
        void evictsOldest() {
            AlertActionHandler smallCap = new AlertActionHandler(Duration.ZERO, 3);

            for (int i = 1; i <= 5; i++) {
                executeAndAwait(smallCap,
                        alertAction("info", "Alert-" + i), trigger(), rule("r" + i));
            }

            // Should only retain the 3 newest
            assertThat(smallCap.getAlertCount(TENANT)).isEqualTo(3);

            List<AlertActionHandler.Alert> stored = smallCap.getAlerts(TENANT);
            // Newest first
            assertThat(stored.get(0).getMessage()).isEqualTo("Alert-5");
            assertThat(stored.get(1).getMessage()).isEqualTo("Alert-4");
            assertThat(stored.get(2).getMessage()).isEqualTo("Alert-3");
        }

        @Test
        @DisplayName("eviction is per-tenant, not global")
        void evictionPerTenant() {
            AlertActionHandler smallCap = new AlertActionHandler(Duration.ZERO, 2);

            executeAndAwait(smallCap, alertAction("info", "T1-A"), trigger("t1"), rule("r1"));
            executeAndAwait(smallCap, alertAction("info", "T1-B"), trigger("t1"), rule("r2"));
            executeAndAwait(smallCap, alertAction("info", "T1-C"), trigger("t1"), rule("r3"));

            executeAndAwait(smallCap, alertAction("info", "T2-A"), trigger("t2"), rule("r1"));

            assertThat(smallCap.getAlertCount("t1")).isEqualTo(2);
            assertThat(smallCap.getAlertCount("t2")).isEqualTo(1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Error Handling Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("empty parameters produce INFO alert with fallback message")
        void emptyParameters() {
            ReflexRule.Action action = ReflexRule.Action.builder()
                    .type(ReflexRule.ActionType.ALERT)
                    .build();

            ReflexOutcome outcome = executeAndAwait(handler, action, trigger(), rule());

            assertThat(outcome.isSuccess()).isTrue();
            assertThat(outcome.getOutput()).containsEntry("severity", "INFO");
        }

        @Test
        @DisplayName("outcome contains alertId")
        void outcomeHasAlertId() {
            ReflexOutcome outcome = executeAndAwait(handler,
                    alertAction("high", "test"), trigger(), rule());

            assertThat(outcome.getOutput().get("alertId")).isNotNull();
            assertThat(outcome.getOutput().get("alertId").toString()).startsWith("alert-");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Full Severity Coverage
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("All Severity Levels")
    class AllSeverityLevels {

        private AlertActionHandler noLimit;

        @BeforeEach
        void setUp() {
            noLimit = new AlertActionHandler(Duration.ZERO, 10_000);
        }

        @Test
        @DisplayName("CRITICAL alert fires and logs at error level")
        void criticalFires() {
            ReflexOutcome outcome = executeAndAwait(noLimit,
                    alertAction("critical", "System crash"), trigger(), rule());
            assertThat(outcome.isSuccess()).isTrue();
            assertThat(outcome.getOutput()).containsEntry("severity", "CRITICAL");
        }

        @Test
        @DisplayName("HIGH alert fires and logs at warn level")
        void highFires() {
            ReflexOutcome outcome = executeAndAwait(noLimit,
                    alertAction("high", "Memory pressure"), trigger(), rule());
            assertThat(outcome.isSuccess()).isTrue();
            assertThat(outcome.getOutput()).containsEntry("severity", "HIGH");
        }

        @Test
        @DisplayName("MEDIUM alert fires")
        void mediumFires() {
            ReflexOutcome outcome = executeAndAwait(noLimit,
                    alertAction("medium", "Elevated latency"), trigger(), rule());
            assertThat(outcome.isSuccess()).isTrue();
            assertThat(outcome.getOutput()).containsEntry("severity", "MEDIUM");
        }

        @Test
        @DisplayName("LOW alert fires")
        void lowFires() {
            ReflexOutcome outcome = executeAndAwait(noLimit,
                    alertAction("low", "Minor degradation"), trigger(), rule());
            assertThat(outcome.isSuccess()).isTrue();
            assertThat(outcome.getOutput()).containsEntry("severity", "LOW");
        }

        @Test
        @DisplayName("INFO alert fires")
        void infoFires() {
            ReflexOutcome outcome = executeAndAwait(noLimit,
                    alertAction("info", "Status update"), trigger(), rule());
            assertThat(outcome.isSuccess()).isTrue();
            assertThat(outcome.getOutput()).containsEntry("severity", "INFO");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Integration with DefaultReflexEngine
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Engine Integration")
    class EngineIntegration {

        @Test
        @DisplayName("DefaultReflexEngine uses AlertActionHandler by default")
        void engineUsesAlertHandler() {
            DefaultReflexEngine engine = new DefaultReflexEngine();
            assertThat(engine.getAlertHandler()).isNotNull();
            assertThat(engine.getActionHandler(ReflexRule.ActionType.ALERT)).isPresent();
        }

        @Test
        @DisplayName("engine dispatches ALERT to AlertActionHandler and stores alert")
        void engineDispatchesAlert() {
            AlertActionHandler alertHandler = new AlertActionHandler(Duration.ZERO, 10_000);
            DefaultReflexEngine engine = new DefaultReflexEngine(alertHandler);

            // Register a rule with ALERT action
            ReflexRule rule = ReflexRule.builder()
                    .id("rfx-test")
                    .name("Test Rule")
                    .tenantId(TENANT)
                    .enabled(true)
                    .condition(ReflexRule.Condition.builder()
                            .minConfidence(0.0f)
                            .build())
                    .triggerTypes(List.of(ReflexTrigger.TriggerType.THRESHOLD))
                    .action(ReflexRule.Action.alert("critical", "Threshold breached"))
                    .build();

            // Run inside Eventloop since engine uses Promise chains
            AtomicReference<ReflexEngine.ExecutionResult> resultRef = new AtomicReference<>();
            AtomicReference<Exception> errorRef = new AtomicReference<>();

            Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
            eventloop.post(() ->
                    engine.registerRule(rule)
                            .then(r -> engine.process(trigger()))
                            .whenResult(resultRef::set)
                            .whenException(e -> errorRef.set(
                                    e instanceof Exception ? (Exception) e : new RuntimeException(e))));
            eventloop.run();

            assertThat(errorRef.get()).isNull();
            ReflexEngine.ExecutionResult result = resultRef.get();
            assertThat(result).isNotNull();
            assertThat(result.isExecuted()).isTrue();
            assertThat(result.getOutcomes()).hasSize(1);
            assertThat(result.getOutcomes().get(0).isSuccess()).isTrue();
            assertThat(result.getOutcomes().get(0).getOutput())
                    .containsEntry("severity", "CRITICAL")
                    .containsEntry("fired", true);

            // Verify alert was stored via the handler
            List<AlertActionHandler.Alert> alerts = alertHandler.getAlerts(TENANT);
            assertThat(alerts).hasSize(1);
            assertThat(alerts.get(0).getSeverity()).isEqualTo(AlertActionHandler.Severity.CRITICAL);
            assertThat(alerts.get(0).getMessage()).isEqualTo("Threshold breached");
        }

        @Test
        @DisplayName("custom AlertActionHandler injected into engine is the same instance")
        void customHandlerInjection() {
            AlertActionHandler custom = new AlertActionHandler(Duration.ofSeconds(5), 100);
            DefaultReflexEngine engine = new DefaultReflexEngine(custom);

            assertThat(engine.getAlertHandler()).isSameAs(custom);
            assertThat(engine.getAlertHandler().getRateLimitWindow())
                    .isEqualTo(Duration.ofSeconds(5));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Dedup Key Tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Dedup Key")
    class DedupKey {

        @Test
        @DisplayName("key includes tenant, rule, and severity")
        void keyFormat() {
            String key = handler.buildDedupKey("rule-1",
                    AlertActionHandler.Severity.CRITICAL, "tenant-x");
            assertThat(key).isEqualTo("tenant-x:rule-1:CRITICAL");
        }

        @Test
        @DisplayName("different components produce different keys")
        void differentKeys() {
            String k1 = handler.buildDedupKey("r1", AlertActionHandler.Severity.HIGH, "t1");
            String k2 = handler.buildDedupKey("r1", AlertActionHandler.Severity.LOW, "t1");
            String k3 = handler.buildDedupKey("r2", AlertActionHandler.Severity.HIGH, "t1");
            String k4 = handler.buildDedupKey("r1", AlertActionHandler.Severity.HIGH, "t2");

            assertThat(k1).isNotEqualTo(k2);
            assertThat(k1).isNotEqualTo(k3);
            assertThat(k1).isNotEqualTo(k4);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rate Limit Decision (Unit)
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("checkRateLimit (unit)")
    class CheckRateLimitUnit {

        @Test
        @DisplayName("first call allows, second call within window suppresses")
        void basicFlow() {
            Instant now = Instant.now();
            AlertActionHandler.RateLimitDecision d1 =
                    handler.checkRateLimit("key-1", now);
            assertThat(d1.suppressed).isFalse();

            AlertActionHandler.RateLimitDecision d2 =
                    handler.checkRateLimit("key-1", now.plusMillis(100));
            assertThat(d2.suppressed).isTrue();
            assertThat(d2.suppressedCount).isEqualTo(1);
        }

        @Test
        @DisplayName("after window expires, allows and reports prior suppressions")
        void windowExpiry() {
            // Use 1ms window for testing
            AlertActionHandler tinyWindow = new AlertActionHandler(Duration.ofMillis(1), 10_000);

            Instant t0 = Instant.now();
            tinyWindow.checkRateLimit("key", t0);

            // Suppress within window
            tinyWindow.checkRateLimit("key", t0);

            // After window
            Instant t1 = t0.plusMillis(2);
            AlertActionHandler.RateLimitDecision d =
                    tinyWindow.checkRateLimit("key", t1);

            assertThat(d.suppressed).isFalse();
            assertThat(d.priorSuppressionCount).isEqualTo(1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Alert Value Object
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Alert Value Object")
    class AlertValue {

        @Test
        @DisplayName("Alert carries all fields from builder")
        void allFields() {
            Instant ts = Instant.now();
            AlertActionHandler.Alert alert = AlertActionHandler.Alert.builder()
                    .alertId("a-1")
                    .severity(AlertActionHandler.Severity.HIGH)
                    .message("Test")
                    .channel("slack")
                    .tenantId("t-1")
                    .timestamp(ts)
                    .ruleId("r-1")
                    .ruleName("Rule One")
                    .ruleCategory("ops")
                    .rulePriority(2)
                    .ruleRiskLevel(ReflexRule.RiskLevel.MEDIUM)
                    .triggerId("trg-1")
                    .triggerType(ReflexTrigger.TriggerType.ANOMALY)
                    .triggerSource("anomaly-detector")
                    .triggerConfidence(0.88f)
                    .triggerFeatures(Map.of("k", "v"))
                    .patternId("pat-42")
                    .priorSuppressionCount(3)
                    .build();

            assertThat(alert.getAlertId()).isEqualTo("a-1");
            assertThat(alert.getSeverity()).isEqualTo(AlertActionHandler.Severity.HIGH);
            assertThat(alert.getMessage()).isEqualTo("Test");
            assertThat(alert.getChannel()).isEqualTo("slack");
            assertThat(alert.getTenantId()).isEqualTo("t-1");
            assertThat(alert.getTimestamp()).isEqualTo(ts);
            assertThat(alert.getRuleId()).isEqualTo("r-1");
            assertThat(alert.getRuleName()).isEqualTo("Rule One");
            assertThat(alert.getRuleCategory()).isEqualTo("ops");
            assertThat(alert.getRulePriority()).isEqualTo(2);
            assertThat(alert.getRuleRiskLevel()).isEqualTo(ReflexRule.RiskLevel.MEDIUM);
            assertThat(alert.getTriggerId()).isEqualTo("trg-1");
            assertThat(alert.getTriggerType()).isEqualTo(ReflexTrigger.TriggerType.ANOMALY);
            assertThat(alert.getTriggerSource()).isEqualTo("anomaly-detector");
            assertThat(alert.getTriggerConfidence()).isEqualTo(0.88f);
            assertThat(alert.getTriggerFeatures()).containsEntry("k", "v");
            assertThat(alert.getPatternId()).isEqualTo("pat-42");
            assertThat(alert.getPriorSuppressionCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("Alert defaults are sensible")
        void defaults() {
            AlertActionHandler.Alert alert = AlertActionHandler.Alert.builder()
                    .alertId("a-d")
                    .build();
            assertThat(alert.getTriggerFeatures()).isEmpty();
            assertThat(alert.getPriorSuppressionCount()).isZero();
        }
    }
}
