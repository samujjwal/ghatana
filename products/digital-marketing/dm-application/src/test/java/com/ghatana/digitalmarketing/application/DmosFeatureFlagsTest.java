package com.ghatana.digitalmarketing.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies canonical key constants on {@link DmosFeatureFlags} have the expected string values.
 *
 * <p>These constants are used as keys passed to {@code DigitalMarketingKernelAdapter.isFeatureEnabled()}.
 * A mismatch would silently break runtime feature flag evaluation (e.g. the platform plugin would
 * not recognise the flag and fall back to its default). This test catches accidental rename or
 * copy-paste drift.</p>
 */
@DisplayName("DmosFeatureFlags — canonical key constants")
class DmosFeatureFlagsTest {

    @Test
    @DisplayName("AI_ENABLED key is 'dmos.ai.enabled'")
    void aiEnabledKey() {
        assertThat(DmosFeatureFlags.AI_ENABLED).isEqualTo("dmos.ai.enabled");
    }

    @Test
    @DisplayName("GOOGLE_ADS_CONNECTOR_ENABLED key is 'dmos.google_ads_connector.enabled'")
    void googleAdsConnectorEnabledKey() {
        assertThat(DmosFeatureFlags.GOOGLE_ADS_CONNECTOR_ENABLED)
            .isEqualTo("dmos.google_ads_connector.enabled");
    }

    @Test
    @DisplayName("KILL_SWITCH_ENABLED key is 'dmos.kill_switch.enabled'")
    void killSwitchEnabledKey() {
        assertThat(DmosFeatureFlags.KILL_SWITCH_ENABLED).isEqualTo("dmos.kill_switch.enabled");
    }

    @Test
    @DisplayName("ROLLBACK_ENABLED key is 'dmos.rollback.enabled'")
    void rollbackEnabledKey() {
        assertThat(DmosFeatureFlags.ROLLBACK_ENABLED).isEqualTo("dmos.rollback.enabled");
    }

    @Test
    @DisplayName("OBSERVABILITY_ENABLED key is 'dmos.observability.enabled'")
    void observabilityEnabledKey() {
        assertThat(DmosFeatureFlags.OBSERVABILITY_ENABLED).isEqualTo("dmos.observability.enabled");
    }

    @Test
    @DisplayName("no two flags share the same key")
    void keysAreUnique() {
        assertThat(java.util.Set.of(
            DmosFeatureFlags.AI_ENABLED,
            DmosFeatureFlags.GOOGLE_ADS_CONNECTOR_ENABLED,
            DmosFeatureFlags.KILL_SWITCH_ENABLED,
            DmosFeatureFlags.ROLLBACK_ENABLED,
            DmosFeatureFlags.OBSERVABILITY_ENABLED
        )).hasSize(5);
    }
}
