package com.ghatana.digitalmarketing.pack;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.compliance.CompliancePlugin;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.ghatana.digitalmarketing.pack.DmComplianceRuleSetIds.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for {@link DigitalMarketingPluginBindings}.
 *
 * <p>Verifies that {@code registerAll()} invokes {@link CompliancePlugin#registerRuleSet}
 * for each of the seven DMOS rule set IDs with non-empty rule lists.</p>
 */
@DisplayName("DigitalMarketingPluginBindings")
@ExtendWith(MockitoExtension.class)
class DigitalMarketingPluginBindingsTest extends EventloopTestBase {

    @Mock
    private CompliancePlugin compliancePlugin;

    private DigitalMarketingPluginBindings bindings;

    @BeforeEach
    void setUp() {
        bindings = new DigitalMarketingPluginBindings(compliancePlugin);
        lenient().when(compliancePlugin.registerRuleSet(any(), any()))
            .thenReturn(Promise.of(null));
    }

    @Test
    @DisplayName("registerAll() completes without error when all registrations succeed")
    void shouldCompleteWhenAllRegistrationsSucceed() {
        assertThatNoException()
            .isThrownBy(() -> runPromise(() -> bindings.registerAll()));
    }

    @Test
    @DisplayName("registerAll() registers DM_MARKETING_INTEGRITY rule set")
    void shouldRegisterMarketingIntegrityRuleSet() {
        runPromise(() -> bindings.registerAll());
        verify(compliancePlugin).registerRuleSet(eq(DM_MARKETING_INTEGRITY), any());
    }

    @Test
    @DisplayName("registerAll() registers DM_CONSENT_LIFECYCLE rule set")
    void shouldRegisterConsentLifecycleRuleSet() {
        runPromise(() -> bindings.registerAll());
        verify(compliancePlugin).registerRuleSet(eq(DM_CONSENT_LIFECYCLE), any());
    }

    @Test
    @DisplayName("registerAll() registers DM_AUDIT_TRACEABILITY rule set")
    void shouldRegisterAuditTraceabilityRuleSet() {
        runPromise(() -> bindings.registerAll());
        verify(compliancePlugin).registerRuleSet(eq(DM_AUDIT_TRACEABILITY), any());
    }

    @Test
    @DisplayName("registerAll() registers DM_CAMPAIGN_PREFLIGHT rule set")
    void shouldRegisterCampaignPreflightRuleSet() {
        runPromise(() -> bindings.registerAll());
        verify(compliancePlugin).registerRuleSet(eq(DM_CAMPAIGN_PREFLIGHT), any());
    }

    @Test
    @DisplayName("registerAll() registers DM_CLAIMS_DISCLOSURES rule set")
    void shouldRegisterClaimsDisclosuresRuleSet() {
        runPromise(() -> bindings.registerAll());
        verify(compliancePlugin).registerRuleSet(eq(DM_CLAIMS_DISCLOSURES), any());
    }

    @Test
    @DisplayName("registerAll() registers DM_EMAIL_COMPLIANCE rule set")
    void shouldRegisterEmailComplianceRuleSet() {
        runPromise(() -> bindings.registerAll());
        verify(compliancePlugin).registerRuleSet(eq(DM_EMAIL_COMPLIANCE), any());
    }

    @Test
    @DisplayName("registerAll() registers DM_CONNECTOR_EXECUTION_SAFETY rule set")
    void shouldRegisterConnectorExecutionSafetyRuleSet() {
        runPromise(() -> bindings.registerAll());
        verify(compliancePlugin).registerRuleSet(eq(DM_CONNECTOR_EXECUTION_SAFETY), any());
    }

    @Test
    @DisplayName("constructor rejects null compliancePlugin")
    void shouldRejectNullCompliancePlugin() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DigitalMarketingPluginBindings(null))
            .withMessageContaining("compliancePlugin");
    }
}
