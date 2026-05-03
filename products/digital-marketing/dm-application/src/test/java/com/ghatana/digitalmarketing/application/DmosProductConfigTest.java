package com.ghatana.digitalmarketing.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DmosProductConfig")
class DmosProductConfigTest {

    @Test
    @DisplayName("AI_FEATURES_ENABLED defaults to true when env is not set")
    void aiFeaturesFlagDefaultsToTrue() {
        // Environment variable is not set in test context; defaults apply
        assertThat(DmosProductConfig.AI_FEATURES_ENABLED).isTrue();
    }

    @Test
    @DisplayName("GOOGLE_ADS_CONNECTOR_ENABLED defaults to true")
    void googleAdsConnectorDefaultsToTrue() {
        assertThat(DmosProductConfig.GOOGLE_ADS_CONNECTOR_ENABLED).isTrue();
    }

    @Test
    @DisplayName("KILL_SWITCH_ENABLED defaults to true")
    void killSwitchDefaultsToTrue() {
        assertThat(DmosProductConfig.KILL_SWITCH_ENABLED).isTrue();
    }

    @Test
    @DisplayName("ROLLBACK_ENABLED defaults to true")
    void rollbackDefaultsToTrue() {
        assertThat(DmosProductConfig.ROLLBACK_ENABLED).isTrue();
    }

    @Test
    @DisplayName("OBSERVABILITY_ENABLED defaults to true")
    void observabilityDefaultsToTrue() {
        assertThat(DmosProductConfig.OBSERVABILITY_ENABLED).isTrue();
    }

    @Test
    @DisplayName("MAX_INTAKE_FIELDS defaults to 50")
    void maxIntakeFieldsDefaultsTo50() {
        assertThat(DmosProductConfig.MAX_INTAKE_FIELDS).isEqualTo(50);
    }

    @Test
    @DisplayName("MAX_AD_COPY_VARIANTS defaults to 5")
    void maxAdCopyVariantsDefaultsTo5() {
        assertThat(DmosProductConfig.MAX_AD_COPY_VARIANTS).isEqualTo(5);
    }

    @Test
    @DisplayName("CONNECTOR_TOKEN_REFRESH_WINDOW defaults to 5 minutes")
    void connectorTokenRefreshWindowDefaultsTo5Minutes() {
        assertThat(DmosProductConfig.CONNECTOR_TOKEN_REFRESH_WINDOW)
            .isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("WORKFLOW_STEP_TIMEOUT defaults to 30 seconds")
    void workflowStepTimeoutDefaultsTo30Seconds() {
        assertThat(DmosProductConfig.WORKFLOW_STEP_TIMEOUT)
            .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("MAX_BUDGET_CAP defaults to 100000")
    void maxBudgetCapDefaultsTo100000() {
        assertThat(DmosProductConfig.MAX_BUDGET_CAP).isEqualTo(100_000);
    }

    @Test
    @DisplayName("ENVIRONMENT defaults to 'development'")
    void environmentDefaultsToDevelopment() {
        assertThat(DmosProductConfig.ENVIRONMENT).isEqualTo("development");
    }

    @Test
    @DisplayName("VERSION defaults to 'local'")
    void versionDefaultsToLocal() {
        assertThat(DmosProductConfig.VERSION).isEqualTo("local");
    }
}
