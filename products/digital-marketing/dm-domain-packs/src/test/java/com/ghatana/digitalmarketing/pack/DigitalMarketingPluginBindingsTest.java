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
    @DisplayName("registerAll() registers all seven DMOS rule sets")
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
    @DisplayName("constructor rejects null compliancePlugin")
    void shouldRejectNullCompliancePlugin() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DigitalMarketingPluginBindings(null))
            .withMessageContaining("compliancePlugin");
    }

    private static final class RecordingCompliancePlugin implements CompliancePlugin {
        private final Map<String, List<ComplianceRule>> registeredRuleSets = new LinkedHashMap<>();

        Map<String, List<ComplianceRule>> registeredRuleSets() {
            return registeredRuleSets;
        }

        @Override
        public Promise<ComplianceResult> evaluate(String ruleSetId, ComplianceContext context) {
            return Promise.of(new ComplianceResult(true, List.of(), ruleSetId, Instant.now()));
        }

        @Override
        public Promise<Void> registerRuleSet(String ruleSetId, List<ComplianceRule> rules) {
            registeredRuleSets.put(ruleSetId, List.copyOf(rules));
            return Promise.of(null);
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
