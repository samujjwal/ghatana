/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.launcher.settings.SettingsStore;
import com.ghatana.platform.observability.idempotency.IdempotencyStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("DataCloudHttpServer production dependency validation (P0.5)")
class DataCloudHttpServerProductionDependencyTest {

    private void validateProductionDependencies(Logger logger,
                                                boolean authConfigured,
                                                boolean auditAvailable,
                                                boolean policyAvailable,
                                                boolean idempotencyStoreAvailable,
                                                boolean eventStoreAvailable,
                                                boolean entityStoreDurable,
                                                boolean coreEventStoreDurable,
                                                boolean metricsConfigured,
                                                boolean traceExportAvailable,
                                                boolean tenantResolverAvailable,
                                                boolean strictTenantResolution,
                                                String deploymentMode) {
        IdempotencyStore platformIdempotencyStore = idempotencyStoreAvailable ? mock(IdempotencyStore.class) : null;
        DataCloudHttpServer.validateProductionDependenciesWithProfileValidator(
            strictTenantResolution,
            deploymentMode,
            authConfigured,
            auditAvailable,
            policyAvailable,
            idempotencyStoreAvailable,
            eventStoreAvailable,
            entityStoreDurable,
            coreEventStoreDurable,
            metricsConfigured,
            traceExportAvailable,
            tenantResolverAvailable,
            true,
            platformIdempotencyStore,
            logger
        );
    }

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

        assertThatThrownBy(() -> validateProductionDependencies(
            logger, true, false, true, true, true, true, true, true, true, true, true, "production"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Audit service is required");
    }

    @Test
    @DisplayName("blocks startup when policy engine is missing in production profile")
    void blocksStartupWhenPolicyMissingInProduction() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> validateProductionDependencies(
            logger, true, true, false, true, true, true, true, true, true, true, true, "production"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Policy engine is required");
    }

    @Test
    @DisplayName("blocks startup when tenant resolver is missing in strict tenant mode")
    void blocksStartupWhenTenantResolverMissingInStrictMode() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> validateProductionDependencies(
            logger, true, true, true, true, true, true, true, true, true, false, true, "production"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Authentication (API key or JWT) is required");
    }

    @Test
    @DisplayName("blocks startup when idempotency store is missing in production profile")
    void blocksStartupWhenIdempotencyStoreMissingInProduction() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> validateProductionDependencies(
            logger, true, true, true, false, true, true, true, true, true, true, true, "production"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Durable idempotency store is required");
    }

    @Test
    @DisplayName("blocks startup when authentication is missing in production profile")
    void blocksStartupWhenAuthMissingInProduction() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> validateProductionDependencies(
            logger, false, true, true, true, true, true, true, true, true, true, true, "production"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Authentication (API key or JWT) is required");
    }

    @Test
    @DisplayName("blocks startup when event store is missing in production profile")
    void blocksStartupWhenEventStoreMissingInProduction() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> validateProductionDependencies(
            logger, true, true, true, true, false, true, true, true, true, true, true, "production"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Durable event store is required");
    }

    @Test
    @DisplayName("blocks startup when metrics are not explicitly configured in production profile")
    void blocksStartupWhenMetricsNotConfiguredInProduction() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> validateProductionDependencies(
            logger, true, true, true, true, true, true, true, false, true, true, true, "production"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Metrics collector is required");
    }

    @Test
    @DisplayName("blocks startup when trace export is missing in production profile")
    void blocksStartupWhenTraceExportMissingInProduction() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> validateProductionDependencies(
            logger, true, true, true, true, true, true, true, true, false, true, true, "production"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Trace export service is required");

    }

    @Test
    @DisplayName("blocks startup when entity store backing is non-durable in production profile")
    void blocksStartupWhenEntityStoreBackingNonDurableInProduction() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> validateProductionDependencies(
            logger, true, true, true, true, true, false, true, true, true, true, true, "production"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Durable entity store is required");
    }

    @Test
    @DisplayName("blocks startup when core event store backing is non-durable in production profile")
    void blocksStartupWhenCoreEventStoreBackingNonDurableInProduction() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> validateProductionDependencies(
            logger, true, true, true, true, true, true, false, true, true, true, true, "production"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Durable event store is required");
    }

    @Test
    @DisplayName("allows startup in local profile even with missing dependencies")
    void allowsStartupInLocalProfile() {
        Logger logger = mock(Logger.class);

        assertThatCode(() -> validateProductionDependencies(
            logger, false, false, false, false, false, false, false, false, false, false, false, "local"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("allows startup when all production dependencies are present")
    void allowsStartupWhenAllDependenciesPresent() {
        Logger logger = mock(Logger.class);

        assertThatCode(() -> validateProductionDependencies(
            logger, true, true, true, true, true, true, true, true, true, true, true, "production"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("blocks startup when generic idempotency store is missing in production profile")
    void blocksStartupWhenGenericIdempotencyMissingInProduction() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> DataCloudHttpServer.validateCriticalRuntimeDependencies(
            "production", false, true, true, logger))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Durable generic idempotency store is required");
    }

    @Test
    @DisplayName("blocks startup when transaction manager is missing in production profile")
    void blocksStartupWhenTransactionManagerMissingInProduction() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> DataCloudHttpServer.validateCriticalRuntimeDependencies(
            "production", true, false, true, logger))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Transaction manager is required");
    }

    @Test
    @DisplayName("blocks startup when completion service is missing in production profile")
    void blocksStartupWhenCompletionServiceMissingInProduction() {
        Logger logger = mock(Logger.class);

        assertThatThrownBy(() -> DataCloudHttpServer.validateCriticalRuntimeDependencies(
            "production", true, true, false, logger))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("AI completion service is required");
    }

    @Test
    @DisplayName("allows missing runtime dependencies in local profile")
    void allowsMissingRuntimeDependenciesInLocalProfile() {
        Logger logger = mock(Logger.class);

        assertThatCode(() -> DataCloudHttpServer.validateCriticalRuntimeDependencies(
            "local", false, false, false, logger))
            .doesNotThrowAnyException();
    }
}
