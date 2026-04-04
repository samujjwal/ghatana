/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.reflex;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultReflexEngine}.
 *
 * @doc.type class
 * @doc.purpose Test coverage for DefaultReflexEngine rule management, matching, and actions
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("DefaultReflexEngine")
class DefaultReflexEngineTest extends EventloopTestBase {

    private DefaultReflexEngine engine;
    private static final String TENANT = "tenant-test";

    @BeforeEach
    void setUp() {
        engine = new DefaultReflexEngine();
    }

    // ─── Rule Registration ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rule Registration")
    class RuleRegistrationTests {

        @Test
        @DisplayName("registers a rule and retrieves it")
        void registersAndRetrievesRule() throws Exception {
            ReflexRule rule = buildAlertRule("rfx-001", TENANT);

            ReflexRule registered = runPromise(() -> engine.registerRule(rule));
            Optional<ReflexRule> fetched = runPromise(() -> engine.getRule("rfx-001", TENANT));

            assertThat(registered.getId()).isEqualTo("rfx-001");
            assertThat(fetched).isPresent();
            assertThat(fetched.get().getId()).isEqualTo("rfx-001");
        }

        @Test
        @DisplayName("lists all rules for a tenant")
        void listsRulesForTenant() throws Exception {
            runPromise(() -> engine.registerRule(buildAlertRule("rfx-a", TENANT)));
            runPromise(() -> engine.registerRule(buildAlertRule("rfx-b", TENANT)));
            runPromise(() -> engine.registerRule(buildAlertRule("rfx-c", "other-tenant")));

            List<ReflexRule> rules = runPromise(() -> engine.listRules(TENANT));
            assertThat(rules).hasSize(2).extracting(ReflexRule::getId)
                    .containsExactlyInAnyOrder("rfx-a", "rfx-b");
        }

        @Test
        @DisplayName("removes a rule by id")
        void removesRule() throws Exception {
            runPromise(() -> engine.registerRule(buildAlertRule("to-remove", TENANT)));
            Boolean removed = runPromise(() -> engine.removeRule("to-remove", TENANT));

            Optional<ReflexRule> fetched = runPromise(() -> engine.getRule("to-remove", TENANT));
            assertThat(removed).isTrue();
            assertThat(fetched).isEmpty();
        }

        @Test
        @DisplayName("removing non-existent rule returns false")
        void removingNonExistentRuleReturnsFalse() throws Exception {
            Boolean result = runPromise(() -> engine.removeRule("ghost", TENANT));
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("enables a previously disabled rule")
        void enablesDisabledRule() throws Exception {
            ReflexRule disabled = buildAlertRule("rfx-dis", TENANT).toBuilder().enabled(false).build();
            runPromise(() -> engine.registerRule(disabled));

            ReflexRule enabled = runPromise(() -> engine.enableRule("rfx-dis", TENANT));
            assertThat(enabled.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("disables a previously enabled rule")
        void disablesEnabledRule() throws Exception {
            runPromise(() -> engine.registerRule(buildAlertRule("rfx-en", TENANT)));

            ReflexRule disabled = runPromise(() -> engine.disableRule("rfx-en", TENANT));
            assertThat(disabled.isEnabled()).isFalse();
        }
    }

    // ─── Rule Matching ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rule Matching")
    class RuleMatchingTests {

        @Test
        @DisplayName("finds matching rules for a pattern trigger")
        void findsMatchingRulesForPatternTrigger() throws Exception {
            ReflexRule rule = ReflexRule.builder()
                    .id("rfx-pattern")
                    .name("Pattern Rule")
                    .tenantId(TENANT)
                    .enabled(true)
                    .triggerTypes(List.of(ReflexTrigger.TriggerType.PATTERN))
                    .condition(ReflexRule.Condition.builder()
                            .patternId("high-error-pattern")
                            .build())
                    .action(ReflexRule.Action.alert("critical", "Pattern detected"))
                    .build();
            runPromise(() -> engine.registerRule(rule));

            ReflexTrigger trigger = ReflexTrigger.fromPattern("high-error-pattern", 0.9f, TENANT);
            List<ReflexRule> matches = runPromise(() -> engine.findMatchingRules(trigger));

            assertThat(matches).extracting(ReflexRule::getId).contains("rfx-pattern");
        }

        @Test
        @DisplayName("disabled rules are not matched")
        void disabledRulesAreNotMatched() throws Exception {
            ReflexRule disabled = ReflexRule.builder()
                    .id("rfx-disabled")
                    .name("Disabled Rule")
                    .tenantId(TENANT)
                    .enabled(false)
                    .condition(ReflexRule.Condition.builder()
                            .patternId("any-pattern")
                            .build())
                    .action(ReflexRule.Action.alert("low", "Disabled alert"))
                    .build();
            runPromise(() -> engine.registerRule(disabled));

            ReflexTrigger trigger = ReflexTrigger.fromPattern("any-pattern", 0.9f, TENANT);
            List<ReflexRule> matches = runPromise(() -> engine.findMatchingRules(trigger));

            assertThat(matches).extracting(ReflexRule::getId).doesNotContain("rfx-disabled");
        }

        @Test
        @DisplayName("cross-tenant rules are not matched")
        void crossTenantRulesAreNotMatched() throws Exception {
            ReflexRule rule = buildAlertRule("rfx-other-tenant", "other-tenant");
            runPromise(() -> engine.registerRule(rule));

            ReflexTrigger trigger = ReflexTrigger.fromPattern("any-pattern", 1.0f, TENANT);
            List<ReflexRule> matches = runPromise(() -> engine.findMatchingRules(trigger));

            assertThat(matches).extracting(ReflexRule::getId).doesNotContain("rfx-other-tenant");
        }
    }

    // ─── Trigger Processing ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Trigger Processing")
    class TriggerProcessingTests {

        @Test
        @DisplayName("process returns result when no rules match")
        void processReturnsResultWithNoMatches() throws Exception {
            ReflexTrigger trigger = ReflexTrigger.fromPattern("no-such-pattern", 1.0f, TENANT);
            ReflexEngine.ExecutionResult result = runPromise(() -> engine.process(trigger));

            assertThat(result).isNotNull();
            assertThat(result.getMatchedRules()).isEmpty();
        }

        @Test
        @DisplayName("process returns batch result for list of triggers")
        void processBatchHandlesMultipleTriggers() throws Exception {
            ReflexTrigger t1 = ReflexTrigger.fromPattern("pat-a", 0.8f, TENANT);
            ReflexTrigger t2 = ReflexTrigger.fromPattern("pat-b", 0.9f, TENANT);

            ReflexEngine.BatchResult batchResult = runPromise(() -> engine.processBatch(List.of(t1, t2)));

            assertThat(batchResult).isNotNull();
            assertThat(batchResult.getTotalProcessed()).isEqualTo(2);
        }
    }

    // ─── Stats ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Engine Stats")
    class StatsTests {

        @Test
        @DisplayName("getStats returns stats for tenant")
        void getStatsReturnsTenantStats() throws Exception {
            ReflexEngine.EngineStats stats = runPromise(() -> engine.getStats(TENANT));
            assertThat(stats).isNotNull();
            assertThat(stats.getTotalRules()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("clearAll removes all rules for tenant")
        void clearAllRemovesRulesForTenant() throws Exception {
            runPromise(() -> engine.registerRule(buildAlertRule("rfx-x", TENANT)));
            runPromise(() -> engine.registerRule(buildAlertRule("rfx-y", TENANT)));

            runPromise(() -> engine.clearAll(TENANT));

            List<ReflexRule> remaining = runPromise(() -> engine.listRules(TENANT));
            assertThat(remaining).isEmpty();
        }
    }

    // ─── Action Handler Registration ──────────────────────────────────────────

    @Nested
    @DisplayName("Action Handlers")
    class ActionHandlerTests {

        @Test
        @DisplayName("custom handler is registered and retrievable")
        void customHandlerRegisteredAndRetrievable() {
            ReflexEngine.ActionHandler handler = (action, trigger, rule) -> io.activej.promise.Promise.of(
                    ReflexOutcome.success(
                            rule.getId(),
                            trigger.getTriggerId(),
                            ReflexRule.ActionType.WEBHOOK,
                            java.time.Instant.now(),
                            java.util.Map.of(),
                            trigger.getTenantId()));

            engine.registerActionHandler(ReflexRule.ActionType.WEBHOOK, handler);

            Optional<ReflexEngine.ActionHandler> retrieved =
                    engine.getActionHandler(ReflexRule.ActionType.WEBHOOK);
            assertThat(retrieved).isPresent();
        }

        @Test
        @DisplayName("alert handler is pre-registered by default")
        void alertHandlerIsPreregistered() {
            Optional<ReflexEngine.ActionHandler> handler =
                    engine.getActionHandler(ReflexRule.ActionType.ALERT);
            assertThat(handler).isPresent();
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static ReflexRule buildAlertRule(String id, String tenantId) {
        return ReflexRule.builder()
                .id(id)
                .name("Alert Rule " + id)
                .tenantId(tenantId)
                .enabled(true)
                .condition(ReflexRule.Condition.builder()
                        .patternId("test-pattern")
                        .build())
                .action(ReflexRule.Action.alert("medium", "Test alert"))
                .build();
    }
}
