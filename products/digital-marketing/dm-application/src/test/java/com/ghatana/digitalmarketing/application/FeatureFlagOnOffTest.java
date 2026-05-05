/**
 * P1-046: Feature-flag off/on backend tests.
 *
 * <p>Tests that verify behavior when feature flags are enabled/disabled.</p>
 *
 * @doc.type class
 * @doc.purpose Feature-flag off/on backend tests (P1-046)
 * @doc.layer test
 */
package com.ghatana.digitalmarketing.application;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.domain.DmosFeatureDisabledException;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("P1-046: Feature-Flag On/Off Backend Tests")
class FeatureFlagOnOffTest {

    @Mock
    private DigitalMarketingKernelAdapter kernelAdapter;

    private DmosFeatureFlags featureFlags;
    private DmOperationContext testCtx;
    private Eventloop eventloop;

    @BeforeEach
    void setUp() {
        featureFlags = new DmosFeatureFlags(kernelAdapter);
        testCtx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("test-tenant"))
            .workspaceId(DmWorkspaceId.of("test-workspace"))
            .actor(ActorRef.user("test-user"))
            .build();
        eventloop = Eventloop.create();
    }

    @Test
    @DisplayName("P1-046: AI_ENABLED off throws exception when generating strategy")
    void aiEnabledOffThrowsException() {
        when(kernelAdapter.isFeatureEnabled(DmosFeatureFlags.AI_ENABLED)).thenReturn(false);

        assertThatThrownBy(() -> featureFlags.requireAiEnabled())
            .isInstanceOf(DmosFeatureDisabledException.class)
            .hasMessageContaining("AI feature is disabled");
    }

    @Test
    @DisplayName("P1-046: AI_ENABLED on allows strategy generation")
    void aiEnabledOnAllowsStrategyGeneration() {
        when(kernelAdapter.isFeatureEnabled(DmosFeatureFlags.AI_ENABLED)).thenReturn(true);

        assertThat(featureFlags.requireAiEnabled()).isTrue();
    }

    @Test
    @DisplayName("P1-046: GOOGLE_ADS_CONNECTOR_ENABLED off blocks connector operations")
    void googleAdsConnectorOffBlocksOperations() {
        when(kernelAdapter.isFeatureEnabled(DmosFeatureFlags.GOOGLE_ADS_CONNECTOR_ENABLED)).thenReturn(false);

        assertThatThrownBy(() -> featureFlags.requireGoogleAdsConnector())
            .isInstanceOf(DmosFeatureDisabledException.class)
            .hasMessageContaining("Google Ads connector is disabled");
    }

    @Test
    @DisplayName("P1-046: GOOGLE_ADS_CONNECTOR_ENABLED on allows connector operations")
    void googleAdsConnectorOnAllowsOperations() {
        when(kernelAdapter.isFeatureEnabled(DmosFeatureFlags.GOOGLE_ADS_CONNECTOR_ENABLED)).thenReturn(true);

        assertThat(featureFlags.requireGoogleAdsConnector()).isTrue();
    }

    @Test
    @DisplayName("P1-046: KILL_SWITCH_ENABLED off blocks all operations")
    void killSwitchOffBlocksOperations() {
        when(kernelAdapter.isFeatureEnabled(DmosFeatureFlags.KILL_SWITCH_ENABLED)).thenReturn(false);

        assertThatThrownBy(() -> featureFlags.requireKillSwitch())
            .isInstanceOf(DmosFeatureDisabledException.class)
            .hasMessageContaining("Kill switch is active");
    }

    @Test
    @DisplayName("P1-046: KILL_SWITCH_ENABLED on allows normal operations")
    void killSwitchOnAllowsOperations() {
        when(kernelAdapter.isFeatureEnabled(DmosFeatureFlags.KILL_SWITCH_ENABLED)).thenReturn(true);

        assertThat(featureFlags.requireKillSwitch()).isTrue();
    }

    @Test
    @DisplayName("P1-046: ROLLBACK_ENABLED off blocks rollback operations")
    void rollbackOffBlocksOperations() {
        when(kernelAdapter.isFeatureEnabled(DmosFeatureFlags.ROLLBACK_ENABLED)).thenReturn(false);

        assertThatThrownBy(() -> featureFlags.requireRollback())
            .isInstanceOf(DmosFeatureDisabledException.class)
            .hasMessageContaining("Rollback is disabled");
    }

    @Test
    @DisplayName("P1-046: ROLLBACK_ENABLED on allows rollback operations")
    void rollbackOnAllowsOperations() {
        when(kernelAdapter.isFeatureEnabled(DmosFeatureFlags.ROLLBACK_ENABLED)).thenReturn(true);

        assertThat(featureFlags.requireRollback()).isTrue();
    }

    @Test
    @DisplayName("P1-046: OBSERVABILITY_ENABLED off disables metrics collection")
    void observabilityOffDisablesMetrics() {
        when(kernelAdapter.isFeatureEnabled(DmosFeatureFlags.OBSERVABILITY_ENABLED)).thenReturn(false);

        assertThatThrownBy(() -> featureFlags.requireObservability())
            .isInstanceOf(DmosFeatureDisabledException.class)
            .hasMessageContaining("Observability is disabled");
    }

    @Test
    @DisplayName("P1-046: OBSERVABILITY_ENABLED on allows metrics collection")
    void observabilityOnAllowsMetrics() {
        when(kernelAdapter.isFeatureEnabled(DmosFeatureFlags.OBSERVABILITY_ENABLED)).thenReturn(true);

        assertThat(featureFlags.requireObservability()).isTrue();
    }

    @Test
    @DisplayName("P1-046: Multiple flags disabled throws appropriate exception")
    void multipleFlagsDisabledThrowException() {
        when(kernelAdapter.isFeatureEnabled(DmosFeatureFlags.AI_ENABLED)).thenReturn(false);
        when(kernelAdapter.isFeatureEnabled(DmosFeatureFlags.GOOGLE_ADS_CONNECTOR_ENABLED)).thenReturn(false);

        assertThatThrownBy(() -> {
            featureFlags.requireAiEnabled();
            featureFlags.requireGoogleAdsConnector();
        }).isInstanceOf(DmosFeatureDisabledException.class);
    }

    @Test
    @DisplayName("P1-046: Feature flag check returns boolean without exception")
    void featureFlagCheckReturnsBoolean() {
        when(kernelAdapter.isFeatureEnabled(DmosFeatureFlags.AI_ENABLED)).thenReturn(true);

        assertThat(featureFlags.isAiEnabled()).isTrue();

        when(kernelAdapter.isFeatureEnabled(DmosFeatureFlags.AI_ENABLED)).thenReturn(false);

        assertThat(featureFlags.isAiEnabled()).isFalse();
    }
}
