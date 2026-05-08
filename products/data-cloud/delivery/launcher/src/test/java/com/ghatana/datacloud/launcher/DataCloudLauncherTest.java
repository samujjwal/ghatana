package com.ghatana.datacloud.launcher;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.datacloud.DataCloud;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @doc.type class
 * @doc.purpose Validates launcher warnings for non-durable profile selections
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudLauncher - profile warning behavior")
class DataCloudLauncherTest {

    @Test
    @DisplayName("local profile emits explicit non-durable warning")
    void localProfileEmitsNonDurableWarning() {
        String warning = DataCloudLauncher
            .nonDurableProfileWarning(DataCloud.DataCloudConfig.DataCloudProfile.LOCAL)
            .orElseThrow();

        assertThat(warning)
            .contains("[DC-P2-004] IN-MEMORY profile active")
            .contains("non-durable")
            .contains("will be lost on restart");
    }

    @Test
    @DisplayName("sovereign profile emits explicit durability caveat")
    void sovereignProfileEmitsDurabilityCaveat() {
        String warning = DataCloudLauncher
            .nonDurableProfileWarning(DataCloud.DataCloudConfig.DataCloudProfile.SOVEREIGN)
            .orElseThrow();

        assertThat(warning)
            .contains("[DC-P2-004] SOVEREIGN/file-backed profile active")
            .contains("non-durable")
            .contains("persistent storage");
    }

    @Test
    @DisplayName("staging and production profiles do not emit non-durable warnings")
    void strictProfilesDoNotEmitNonDurableWarnings() {
        assertThat(DataCloudLauncher.nonDurableProfileWarning(DataCloud.DataCloudConfig.DataCloudProfile.STAGING)).isEmpty();
        assertThat(DataCloudLauncher.nonDurableProfileWarning(DataCloud.DataCloudConfig.DataCloudProfile.PRODUCTION)).isEmpty();
    }
}