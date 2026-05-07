/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.launcher.settings.SettingsStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("DataCloudHttpServer production dependency validation (P0.5)")
class DataCloudHttpServerProductionDependencyTest {

    @Test
    @DisplayName("blocks startup when settings store is missing in production profile")
    void blocksStartupWhenSettingsStoreMissingInProduction() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> DataCloudHttpServer.validateSettingsStorageConfiguration(
            true, "production", null, logger))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("P0.1: Persistent settings storage is required");
    }

    @Test
    @DisplayName("blocks startup when settings store is in-memory in production profile")
    void blocksStartupWhenSettingsStoreIsInMemoryInProduction() {
        Logger logger = mock(Logger.class);
        SettingsStore store = mock(SettingsStore.class);
        org.mockito.Mockito.when(store.getStorageMode()).thenReturn("in-memory");

        assertThatThrownBy(() -> DataCloudHttpServer.validateSettingsStorageConfiguration(
            true, "production", store, logger))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("P0.1: In-memory settings storage is not allowed");
    }

    @Test
    @DisplayName("allows startup when durable settings store is configured in production profile")
    void allowsStartupWhenDurableSettingsStoreConfiguredInProduction() {
        Logger logger = mock(Logger.class);
        SettingsStore store = mock(SettingsStore.class);
        org.mockito.Mockito.when(store.getStorageMode()).thenReturn("jdbc");

        assertThatCode(() -> DataCloudHttpServer.validateSettingsStorageConfiguration(
            true, "production", store, logger))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("blocks startup when audit service is missing in production profile")
    void blocksStartupWhenAuditMissingInProduction() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> DataCloudHttpServer.validateProductionDependencies(
            true, "production", false, true, true, logger))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("P0.5: Audit service is required");
    }

    @Test
    @DisplayName("blocks startup when policy engine is missing in production profile")
    void blocksStartupWhenPolicyMissingInProduction() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> DataCloudHttpServer.validateProductionDependencies(
            true, "production", true, false, true, logger))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("P0.5: Policy engine is required");
    }

    @Test
    @DisplayName("blocks startup when tenant resolver is missing in strict tenant mode")
    void blocksStartupWhenTenantResolverMissingInStrictMode() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> DataCloudHttpServer.validateProductionDependencies(
            true, "production", true, true, false, logger))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("P0.5: Tenant resolver is required");
    }

    @Test
    @DisplayName("allows startup in local profile even with missing dependencies")
    void allowsStartupInLocalProfile() {
        Logger logger = mock(Logger.class);

        assertThatCode(() -> DataCloudHttpServer.validateProductionDependencies(
            false, "local", false, false, false, logger))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("allows startup when all production dependencies are present")
    void allowsStartupWhenAllDependenciesPresent() {
        Logger logger = mock(Logger.class);

        assertThatCode(() -> DataCloudHttpServer.validateProductionDependencies(
            true, "production", true, true, true, logger))
            .doesNotThrowAnyException();
    }
}
