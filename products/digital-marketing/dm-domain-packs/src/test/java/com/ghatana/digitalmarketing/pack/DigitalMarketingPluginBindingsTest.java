package com.ghatana.digitalmarketing.pack;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.compliance.CompliancePlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link DigitalMarketingPluginBindings}.
 *
 * <p>Uses an in-memory fake compliance plugin to verify registration behavior without mocks.</p>
 */
@DisplayName("DigitalMarketingPluginBindings")
class DigitalMarketingPluginBindingsTest extends EventloopTestBase {

    private RecordingCompliancePlugin compliancePlugin;
    private DigitalMarketingPluginBindings bindings;

    @BeforeEach
    void setUp() {
        compliancePlugin = new RecordingCompliancePlugin();
        bindings = new DigitalMarketingPluginBindings(compliancePlugin);
    }

    @Test
    @DisplayName("registerAll() completes without error when all registrations succeed")
    void shouldCompleteWhenAllRegistrationsSucceed() {
        assertThatNoException()
            .isThrownBy(() -> runPromise(() -> bindings.registerAll()));
    }

    @Test
    @DisplayName("constructor rejects null compliancePlugin")
    void shouldRejectNullCompliancePlugin() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DigitalMarketingPluginBindings(null))
            .withMessageContaining("compliancePlugin");
    }

    @Test
    @DisplayName("registerAll() handles all rule sets with valid rules")
    void shouldRegisterAllRuleSets() {
        runPromise(() -> bindings.registerAll());

        assertThat(compliancePlugin.registeredRuleSets()).hasSize(7);
        assertThat(compliancePlugin.registeredRuleSets())
            .containsKeys(
                DmComplianceRuleSetIds.DM_MARKETING_INTEGRITY,
                DmComplianceRuleSetIds.DM_CONSENT_LIFECYCLE,
                DmComplianceRuleSetIds.DM_AUDIT_TRACEABILITY,
                DmComplianceRuleSetIds.DM_CAMPAIGN_PREFLIGHT,
                DmComplianceRuleSetIds.DM_CLAIMS_DISCLOSURES,
                DmComplianceRuleSetIds.DM_EMAIL_COMPLIANCE,
                DmComplianceRuleSetIds.DM_CONNECTOR_EXECUTION_SAFETY
            );

        compliancePlugin.registeredRuleSets().forEach((ruleSetId, rules) -> {
            assertThat(rules)
                .as("rules for %s should not be empty", ruleSetId)
                .isNotEmpty();
        });
    }

    @Test
    @DisplayName("registerAll() throws when rule set is empty")
    void shouldThrowWhenRuleSetIsEmpty() {
        compliancePlugin.setNextRuleSetEmpty(true);

        assertThatThrownBy(() -> runPromise(() -> bindings.registerAll()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("is empty");
    }

    @Test
    @DisplayName("registerAll() throws when rule ID is blank")
    void shouldThrowWhenRuleIdIsBlank() {
        compliancePlugin.setNextRuleIdBlank(true);

        assertThatThrownBy(() -> runPromise(() -> bindings.registerAll()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("blank ID");
    }

    @Test
    @DisplayName("registerAll() throws when rule ID has invalid prefix")
    void shouldThrowWhenRuleIdHasInvalidPrefix() {
        compliancePlugin.setNextRuleIdInvalidPrefix(true);

        assertThatThrownBy(() -> runPromise(() -> bindings.registerAll()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("invalid prefix");
    }

    @Test
    @DisplayName("registerAll() throws when rule ID is duplicate")
    void shouldThrowWhenRuleIdIsDuplicate() {
        compliancePlugin.setNextRuleIdDuplicate(true);

        assertThatThrownBy(() -> runPromise(() -> bindings.registerAll()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate rule ID");
    }

    private static final class RecordingCompliancePlugin implements CompliancePlugin {
        private final Map<String, List<ComplianceRule>> registeredRuleSets = new LinkedHashMap<>();
        private boolean nextRuleSetEmpty = false;
        private boolean nextRuleIdBlank = false;
        private boolean nextRuleIdInvalidPrefix = false;
        private boolean nextRuleIdDuplicate = false;

        Map<String, List<ComplianceRule>> registeredRuleSets() {
            return registeredRuleSets;
        }

        void setNextRuleSetEmpty(boolean value) {
            this.nextRuleSetEmpty = value;
        }

        void setNextRuleIdBlank(boolean value) {
            this.nextRuleIdBlank = value;
        }

        void setNextRuleIdInvalidPrefix(boolean value) {
            this.nextRuleIdInvalidPrefix = value;
        }

        void setNextRuleIdDuplicate(boolean value) {
            this.nextRuleIdDuplicate = value;
        }

        @Override
        public Promise<ComplianceResult> evaluate(String ruleSetId, ComplianceContext context) {
            return Promise.of(new ComplianceResult(true, List.of(), ruleSetId, Instant.now()));
        }

        @Override
        public Promise<Void> registerRuleSet(String ruleSetId, List<ComplianceRule> rules) {
            if (nextRuleSetEmpty) {
                rules = List.of();
            }
            if (nextRuleIdBlank && !rules.isEmpty()) {
                rules = List.of(createTestRule(""));
            }
            if (nextRuleIdInvalidPrefix && !rules.isEmpty()) {
                rules = List.of(createTestRule("INVALID-001"));
            }
            if (nextRuleIdDuplicate && !rules.isEmpty()) {
                rules = List.of(createTestRule("DM-001"), createTestRule("DM-001"));
            }
            registeredRuleSets.put(ruleSetId, List.copyOf(rules));
            return Promise.of(null);
        }

        private ComplianceRule createTestRule(String ruleId) {
            return new ComplianceRule(
                ruleId,
                "test-type",
                "Test rule",
                ComplianceRule.Severity.HIGH,
                "true"
            );
        }

        @Override
        public Promise<Void> addRule(String ruleSetId, ComplianceRule rule) {
            registeredRuleSets.computeIfAbsent(ruleSetId, ignored -> new java.util.ArrayList<>()).add(rule);
            return Promise.of(null);
        }

        @Override
        public Promise<List<AuditEntry>> getAuditTrail(String entityId) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<ComplianceViolation>> getActiveViolations(String ruleSetId) {
            return Promise.of(List.of());
        }

        @Override
        public PluginMetadata metadata() {
            return PluginMetadata.builder()
                .id("dm-recording-compliance-plugin")
                .name("DM Recording Compliance Plugin")
                .type(PluginType.CUSTOM)
                .build();
        }

        @Override
        public PluginState getState() {
            return PluginState.RUNNING;
        }

        @Override
        public Promise<Void> initialize(PluginContext context) {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> start() {
            return Promise.of(null);
        }

        @Override
        public Promise<Void> stop() {
            return Promise.of(null);
        }
    }
}
