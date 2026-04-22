/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.reflex;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DefaultReflexEngine}.
 *
 * @doc.type class
 * @doc.purpose Test coverage for DefaultReflexEngine rule management, matching, and actions
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("DefaultReflexEngine [GH-90000]")
class DefaultReflexEngineTest extends EventloopTestBase {

    private DefaultReflexEngine engine;
    private static final String TENANT = "tenant-test";

    @BeforeEach
    void setUp() { // GH-90000
        engine = new DefaultReflexEngine(); // GH-90000
    }

    // ─── Rule Registration ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rule Registration [GH-90000]")
    class RuleRegistrationTests {

        @Test
        @DisplayName("registers a rule and retrieves it [GH-90000]")
        void registersAndRetrievesRule() throws Exception { // GH-90000
            ReflexRule rule = buildAlertRule("rfx-001", TENANT); // GH-90000

            ReflexRule registered = runPromise(() -> engine.registerRule(rule)); // GH-90000
            Optional<ReflexRule> fetched = runPromise(() -> engine.getRule("rfx-001", TENANT)); // GH-90000

            assertThat(registered.getId()).isEqualTo("rfx-001 [GH-90000]");
            assertThat(fetched).isPresent(); // GH-90000
            assertThat(fetched.get().getId()).isEqualTo("rfx-001 [GH-90000]");
        }

        @Test
        @DisplayName("lists all rules for a tenant [GH-90000]")
        void listsRulesForTenant() throws Exception { // GH-90000
            runPromise(() -> engine.registerRule(buildAlertRule("rfx-a", TENANT))); // GH-90000
            runPromise(() -> engine.registerRule(buildAlertRule("rfx-b", TENANT))); // GH-90000
            runPromise(() -> engine.registerRule(buildAlertRule("rfx-c", "other-tenant"))); // GH-90000

            List<ReflexRule> rules = runPromise(() -> engine.listRules(TENANT)); // GH-90000
            assertThat(rules).hasSize(2).extracting(ReflexRule::getId) // GH-90000
                    .containsExactlyInAnyOrder("rfx-a", "rfx-b"); // GH-90000
        }

        @Test
        @DisplayName("removes a rule by id [GH-90000]")
        void removesRule() throws Exception { // GH-90000
            runPromise(() -> engine.registerRule(buildAlertRule("to-remove", TENANT))); // GH-90000
            Boolean removed = runPromise(() -> engine.removeRule("to-remove", TENANT)); // GH-90000

            Optional<ReflexRule> fetched = runPromise(() -> engine.getRule("to-remove", TENANT)); // GH-90000
            assertThat(removed).isTrue(); // GH-90000
            assertThat(fetched).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("removing non-existent rule returns false [GH-90000]")
        void removingNonExistentRuleReturnsFalse() throws Exception { // GH-90000
            Boolean result = runPromise(() -> engine.removeRule("ghost", TENANT)); // GH-90000
            assertThat(result).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("enables a previously disabled rule [GH-90000]")
        void enablesDisabledRule() throws Exception { // GH-90000
            ReflexRule disabled = buildAlertRule("rfx-dis", TENANT).toBuilder().enabled(false).build(); // GH-90000
            runPromise(() -> engine.registerRule(disabled)); // GH-90000

            ReflexRule enabled = runPromise(() -> engine.enableRule("rfx-dis", TENANT)); // GH-90000
            assertThat(enabled.isEnabled()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("disables a previously enabled rule [GH-90000]")
        void disablesEnabledRule() throws Exception { // GH-90000
            runPromise(() -> engine.registerRule(buildAlertRule("rfx-en", TENANT))); // GH-90000

            ReflexRule disabled = runPromise(() -> engine.disableRule("rfx-en", TENANT)); // GH-90000
            assertThat(disabled.isEnabled()).isFalse(); // GH-90000
        }
    }

    // ─── Rule Matching ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rule Matching [GH-90000]")
    class RuleMatchingTests {

        @Test
        @DisplayName("finds matching rules for a pattern trigger [GH-90000]")
        void findsMatchingRulesForPatternTrigger() throws Exception { // GH-90000
            ReflexRule rule = ReflexRule.builder() // GH-90000
                    .id("rfx-pattern [GH-90000]")
                    .name("Pattern Rule [GH-90000]")
                    .tenantId(TENANT) // GH-90000
                    .enabled(true) // GH-90000
                    .triggerTypes(List.of(ReflexTrigger.TriggerType.PATTERN)) // GH-90000
                    .condition(ReflexRule.Condition.builder() // GH-90000
                            .patternId("high-error-pattern [GH-90000]")
                            .build()) // GH-90000
                    .action(ReflexRule.Action.alert("critical", "Pattern detected")) // GH-90000
                    .build(); // GH-90000
            runPromise(() -> engine.registerRule(rule)); // GH-90000

            ReflexTrigger trigger = ReflexTrigger.fromPattern("high-error-pattern", 0.9f, TENANT); // GH-90000
            List<ReflexRule> matches = runPromise(() -> engine.findMatchingRules(trigger)); // GH-90000

            assertThat(matches).extracting(ReflexRule::getId).contains("rfx-pattern [GH-90000]");
        }

        @Test
        @DisplayName("disabled rules are not matched [GH-90000]")
        void disabledRulesAreNotMatched() throws Exception { // GH-90000
            ReflexRule disabled = ReflexRule.builder() // GH-90000
                    .id("rfx-disabled [GH-90000]")
                    .name("Disabled Rule [GH-90000]")
                    .tenantId(TENANT) // GH-90000
                    .enabled(false) // GH-90000
                    .condition(ReflexRule.Condition.builder() // GH-90000
                            .patternId("any-pattern [GH-90000]")
                            .build()) // GH-90000
                    .action(ReflexRule.Action.alert("low", "Disabled alert")) // GH-90000
                    .build(); // GH-90000
            runPromise(() -> engine.registerRule(disabled)); // GH-90000

            ReflexTrigger trigger = ReflexTrigger.fromPattern("any-pattern", 0.9f, TENANT); // GH-90000
            List<ReflexRule> matches = runPromise(() -> engine.findMatchingRules(trigger)); // GH-90000

            assertThat(matches).extracting(ReflexRule::getId).doesNotContain("rfx-disabled [GH-90000]");
        }

        @Test
        @DisplayName("cross-tenant rules are not matched [GH-90000]")
        void crossTenantRulesAreNotMatched() throws Exception { // GH-90000
            ReflexRule rule = buildAlertRule("rfx-other-tenant", "other-tenant"); // GH-90000
            runPromise(() -> engine.registerRule(rule)); // GH-90000

            ReflexTrigger trigger = ReflexTrigger.fromPattern("any-pattern", 1.0f, TENANT); // GH-90000
            List<ReflexRule> matches = runPromise(() -> engine.findMatchingRules(trigger)); // GH-90000

            assertThat(matches).extracting(ReflexRule::getId).doesNotContain("rfx-other-tenant [GH-90000]");
        }
    }

    // ─── Trigger Processing ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Trigger Processing [GH-90000]")
    class TriggerProcessingTests {

        @Test
        @DisplayName("process returns result when no rules match [GH-90000]")
        void processReturnsResultWithNoMatches() throws Exception { // GH-90000
            ReflexTrigger trigger = ReflexTrigger.fromPattern("no-such-pattern", 1.0f, TENANT); // GH-90000
            ReflexEngine.ExecutionResult result = runPromise(() -> engine.process(trigger)); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.getMatchedRules()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("process returns batch result for list of triggers [GH-90000]")
        void processBatchHandlesMultipleTriggers() throws Exception { // GH-90000
            ReflexTrigger t1 = ReflexTrigger.fromPattern("pat-a", 0.8f, TENANT); // GH-90000
            ReflexTrigger t2 = ReflexTrigger.fromPattern("pat-b", 0.9f, TENANT); // GH-90000

            ReflexEngine.BatchResult batchResult = runPromise(() -> engine.processBatch(List.of(t1, t2))); // GH-90000

            assertThat(batchResult).isNotNull(); // GH-90000
            assertThat(batchResult.getTotalProcessed()).isEqualTo(2); // GH-90000
        }
    }

    // ─── Stats ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Engine Stats [GH-90000]")
    class StatsTests {

        @Test
        @DisplayName("getStats returns stats for tenant [GH-90000]")
        void getStatsReturnsTenantStats() throws Exception { // GH-90000
            ReflexEngine.EngineStats stats = runPromise(() -> engine.getStats(TENANT)); // GH-90000
            assertThat(stats).isNotNull(); // GH-90000
            assertThat(stats.getTotalRules()).isGreaterThanOrEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("clearAll removes all rules for tenant [GH-90000]")
        void clearAllRemovesRulesForTenant() throws Exception { // GH-90000
            runPromise(() -> engine.registerRule(buildAlertRule("rfx-x", TENANT))); // GH-90000
            runPromise(() -> engine.registerRule(buildAlertRule("rfx-y", TENANT))); // GH-90000

            runPromise(() -> engine.clearAll(TENANT)); // GH-90000

            List<ReflexRule> remaining = runPromise(() -> engine.listRules(TENANT)); // GH-90000
            assertThat(remaining).isEmpty(); // GH-90000
        }
    }

    // ─── Action Handler Registration ──────────────────────────────────────────

    @Nested
    @DisplayName("Action Handlers [GH-90000]")
    class ActionHandlerTests {

        @Test
        @DisplayName("custom handler is registered and retrievable [GH-90000]")
        void customHandlerRegisteredAndRetrievable() { // GH-90000
            ReflexEngine.ActionHandler handler = (action, trigger, rule) -> io.activej.promise.Promise.of( // GH-90000
                    ReflexOutcome.success( // GH-90000
                            rule.getId(), // GH-90000
                            trigger.getTriggerId(), // GH-90000
                            ReflexRule.ActionType.WEBHOOK,
                            java.time.Instant.now(), // GH-90000
                            java.util.Map.of(), // GH-90000
                            trigger.getTenantId())); // GH-90000

            engine.registerActionHandler(ReflexRule.ActionType.WEBHOOK, handler); // GH-90000

            Optional<ReflexEngine.ActionHandler> retrieved =
                    engine.getActionHandler(ReflexRule.ActionType.WEBHOOK); // GH-90000
            assertThat(retrieved).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("alert handler is pre-registered by default [GH-90000]")
        void alertHandlerIsPreregistered() { // GH-90000
            Optional<ReflexEngine.ActionHandler> handler =
                    engine.getActionHandler(ReflexRule.ActionType.ALERT); // GH-90000
            assertThat(handler).isPresent(); // GH-90000
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static ReflexRule buildAlertRule(String id, String tenantId) { // GH-90000
        return ReflexRule.builder() // GH-90000
                .id(id) // GH-90000
                .name("Alert Rule " + id) // GH-90000
                .tenantId(tenantId) // GH-90000
                .enabled(true) // GH-90000
                .condition(ReflexRule.Condition.builder() // GH-90000
                        .patternId("test-pattern [GH-90000]")
                        .build()) // GH-90000
                .action(ReflexRule.Action.alert("medium", "Test alert")) // GH-90000
                .build(); // GH-90000
    }
}
