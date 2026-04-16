package com.ghatana.aep.server;

import io.activej.inject.Injector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for launcher production bootstrap behavior.
 *
 * @doc.type class
 * @doc.purpose Verify launcher bootstrap fails fast on production governance misconfiguration
 * @doc.layer launcher
 * @doc.pattern Test
 */
@DisplayName("AepLauncher")
class AepLauncherTest {

    @Test
    @DisplayName("production bootstrap fails when database configuration is missing")
    void productionBootstrapFailsWithoutDatabase() {
        assertThatThrownBy(() -> AepLauncher.createGovernanceInjector(Map.of(
            "AEP_PROFILE", "production",
            "AEP_JWT_SECRET", "test-secret")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AEP_DB_URL");
    }

    @Test
    @DisplayName("non-production bootstrap allows missing database configuration")
    void nonProductionBootstrapAllowsMissingDatabase() {
        Injector injector = AepLauncher.createGovernanceInjector(Map.of(
            "AEP_PROFILE", "development"));

        assertThat(injector).isNotNull();
    }
}