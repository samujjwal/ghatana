package com.ghatana.yappc.services.generate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EnvironmentAiHealthProvider")
class EnvironmentAiHealthProviderTest {

    @Test
    @DisplayName("defaults to healthy when no degradation signal is configured")
    void defaultsToHealthyWhenNoDegradationSignalIsConfigured() {
        String previousValue = System.getProperty("yappc.ai.degraded");
        try {
            System.clearProperty("yappc.ai.degraded");

            EnvironmentAiHealthProvider provider = new EnvironmentAiHealthProvider();

            assertThat(provider.isDegraded()).isFalse();
        } finally {
            restoreSystemProperty(previousValue);
        }
    }

    @Test
    @DisplayName("treats truthy system property values as degraded")
    void treatsTruthySystemPropertyValuesAsDegraded() {
        String previousValue = System.getProperty("yappc.ai.degraded");
        try {
            System.setProperty("yappc.ai.degraded", "yes");

            EnvironmentAiHealthProvider provider = new EnvironmentAiHealthProvider();

            assertThat(provider.isDegraded()).isTrue();
        } finally {
            restoreSystemProperty(previousValue);
        }
    }

    private static void restoreSystemProperty(String previousValue) {
        if (previousValue == null) {
            System.clearProperty("yappc.ai.degraded");
        } else {
            System.setProperty("yappc.ai.degraded", previousValue);
        }
    }
}