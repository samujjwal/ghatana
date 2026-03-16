/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api.ai.platform;

import com.ghatana.aiplatform.registry.DeploymentStatus;
import com.ghatana.aiplatform.registry.ModelMetadata;
import com.ghatana.aiplatform.registry.ModelRegistryService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link YappcModelRegistryClient}.
 *
 * @doc.type class
 * @doc.purpose Verifies async façade behaviour and delegation to ModelRegistryService
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YappcModelRegistryClient")
class YappcModelRegistryClientTest extends EventloopTestBase {

    private ModelRegistryService delegate;
    private YappcModelRegistryClient client;

    private static final String TENANT = "yappc-tenant-123";
    private static final String MODEL_NAME = "phase-predictor";
    private static final String VERSION = "v2.1.0";

    @BeforeEach
    void setUp() {
        delegate = mock(ModelRegistryService.class);
        client = new YappcModelRegistryClient(
                delegate, Executors.newSingleThreadExecutor());
    }

    // =========================================================================
    // Constructor guard-rails
    // =========================================================================

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("rejects null delegate")
        void rejectsNullDelegate() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new YappcModelRegistryClient(null, Executors.newCachedThreadPool()))
                    .withMessageContaining("delegate");
        }

        @Test
        @DisplayName("rejects null executor")
        void rejectsNullExecutor() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new YappcModelRegistryClient(delegate, null))
                    .withMessageContaining("blockingExecutor");
        }
    }

    // =========================================================================
    // findActiveModel
    // =========================================================================

    @Nested
    @DisplayName("findActiveModel")
    class FindActiveModel {

        @Test
        @DisplayName("returns ACTIVE model when available")
        void returnsActiveModel() {
            ModelMetadata model = buildModel(DeploymentStatus.ACTIVE);
            when(delegate.findByStatus(TENANT, DeploymentStatus.ACTIVE))
                    .thenReturn(List.of(model));

            Optional<ModelMetadata> result = runPromise(() ->
                    client.findActiveModel(TENANT, MODEL_NAME));

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo(MODEL_NAME);
        }

        @Test
        @DisplayName("falls back to PRODUCTION when no ACTIVE model")
        void fallsBackToProduction() {
            ModelMetadata prodModel = buildModel(DeploymentStatus.PRODUCTION);
            when(delegate.findByStatus(TENANT, DeploymentStatus.ACTIVE))
                    .thenReturn(List.of());
            when(delegate.findByStatus(TENANT, DeploymentStatus.PRODUCTION))
                    .thenReturn(List.of(prodModel));

            Optional<ModelMetadata> result = runPromise(() ->
                    client.findActiveModel(TENANT, MODEL_NAME));

            assertThat(result).isPresent();
            assertThat(result.get().getDeploymentStatus()).isEqualTo(DeploymentStatus.PRODUCTION);
        }

        @Test
        @DisplayName("returns empty when no active or production model exists")
        void returnsEmptyWhenAbsent() {
            when(delegate.findByStatus(any(), eq(DeploymentStatus.ACTIVE)))
                    .thenReturn(List.of());
            when(delegate.findByStatus(any(), eq(DeploymentStatus.PRODUCTION)))
                    .thenReturn(List.of());

            Optional<ModelMetadata> result = runPromise(() ->
                    client.findActiveModel(TENANT, MODEL_NAME));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("rejects null tenantId")
        void rejectsNullTenantId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> client.findActiveModel(null, MODEL_NAME))
                    .withMessageContaining("tenantId");
        }

        @Test
        @DisplayName("rejects null modelName")
        void rejectsNullModelName() {
            assertThatNullPointerException()
                    .isThrownBy(() -> client.findActiveModel(TENANT, null))
                    .withMessageContaining("modelName");
        }
    }

    // =========================================================================
    // findByVersion
    // =========================================================================

    @Nested
    @DisplayName("findByVersion")
    class FindByVersion {

        @Test
        @DisplayName("delegates to findByName on the registry service")
        void delegatesToFindByName() {
            ModelMetadata model = buildModel(DeploymentStatus.STAGED);
            when(delegate.findByName(TENANT, MODEL_NAME, VERSION))
                    .thenReturn(Optional.of(model));

            Optional<ModelMetadata> result = runPromise(() ->
                    client.findByVersion(TENANT, MODEL_NAME, VERSION));

            assertThat(result).isPresent();
            verify(delegate).findByName(TENANT, MODEL_NAME, VERSION);
        }

        @Test
        @DisplayName("returns empty when version not found")
        void returnsEmptyWhenNotFound() {
            when(delegate.findByName(TENANT, MODEL_NAME, VERSION))
                    .thenReturn(Optional.empty());

            Optional<ModelMetadata> result = runPromise(() ->
                    client.findByVersion(TENANT, MODEL_NAME, VERSION));

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // listVersions
    // =========================================================================

    @Nested
    @DisplayName("listVersions")
    class ListVersions {

        @Test
        @DisplayName("returns all versions for the model")
        void returnsVersionList() {
            List<ModelMetadata> versions = List.of(
                    buildModel("v2.0.0", DeploymentStatus.DEPRECATED),
                    buildModel("v2.1.0", DeploymentStatus.ACTIVE));
            when(delegate.listVersions(TENANT, MODEL_NAME)).thenReturn(versions);

            List<ModelMetadata> result = runPromise(() ->
                    client.listVersions(TENANT, MODEL_NAME));

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("returns empty list when no versions registered")
        void returnsEmptyList() {
            when(delegate.listVersions(TENANT, MODEL_NAME)).thenReturn(List.of());

            List<ModelMetadata> result = runPromise(() ->
                    client.listVersions(TENANT, MODEL_NAME));

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // listByStatus
    // =========================================================================

    @Nested
    @DisplayName("listByStatus")
    class ListByStatus {

        @Test
        @DisplayName("delegates status filter to the registry service")
        void delegatesFilter() {
            when(delegate.findByStatus(TENANT, DeploymentStatus.CANARY))
                    .thenReturn(List.of(buildModel(DeploymentStatus.CANARY)));

            List<ModelMetadata> result = runPromise(() ->
                    client.listByStatus(TENANT, DeploymentStatus.CANARY));

            assertThat(result).hasSize(1);
            verify(delegate).findByStatus(TENANT, DeploymentStatus.CANARY);
        }
    }

    // =========================================================================
    // registerStaged
    // =========================================================================

    @Nested
    @DisplayName("registerStaged")
    class RegisterStagedTests {

        @Test
        @DisplayName("calls delegate.register with a STAGED ModelMetadata")
        void callsRegisterWithStagedStatus() {
            runPromise(() -> client.registerStaged(
                    TENANT, MODEL_NAME, VERSION, "pytorch",
                    Map.of("accuracy", 0.94)));

            verify(delegate).register(argThat(m ->
                    m.getTenantId().equals(TENANT)
                    && m.getName().equals(MODEL_NAME)
                    && m.getVersion().equals(VERSION)
                    && m.getFramework().equals("pytorch")
                    && m.getDeploymentStatus() == DeploymentStatus.STAGED));
        }
    }

    // =========================================================================
    // Lifecycle management
    // =========================================================================

    @Nested
    @DisplayName("Lifecycle management")
    class LifecycleTests {

        @Test
        @DisplayName("promoteToProduction updates status to PRODUCTION")
        void promoteToProduction() {
            UUID modelId = UUID.randomUUID();
            doNothing().when(delegate).updateStatus(TENANT, modelId, DeploymentStatus.PRODUCTION);

            runPromise(() -> client.promoteToProduction(TENANT, modelId));

            verify(delegate).updateStatus(TENANT, modelId, DeploymentStatus.PRODUCTION);
        }

        @Test
        @DisplayName("markAsCanary updates status to CANARY")
        void markAsCanary() {
            UUID modelId = UUID.randomUUID();
            doNothing().when(delegate).updateStatus(TENANT, modelId, DeploymentStatus.CANARY);

            runPromise(() -> client.markAsCanary(TENANT, modelId));

            verify(delegate).updateStatus(TENANT, modelId, DeploymentStatus.CANARY);
        }

        @Test
        @DisplayName("deprecate updates status to DEPRECATED")
        void deprecateModel() {
            UUID modelId = UUID.randomUUID();
            doNothing().when(delegate).updateStatus(TENANT, modelId, DeploymentStatus.DEPRECATED);

            runPromise(() -> client.deprecate(TENANT, modelId));

            verify(delegate).updateStatus(TENANT, modelId, DeploymentStatus.DEPRECATED);
        }

        @Test
        @DisplayName("rejects null tenantId on promote")
        void rejectsNullTenantId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> client.promoteToProduction(null, UUID.randomUUID()))
                    .withMessageContaining("tenantId");
        }

        @Test
        @DisplayName("rejects null modelId on deprecate")
        void rejectsNullModelId() {
            assertThatNullPointerException()
                    .isThrownBy(() -> client.deprecate(TENANT, null))
                    .withMessageContaining("modelId");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ModelMetadata buildModel(DeploymentStatus status) {
        return buildModel(VERSION, status);
    }

    private ModelMetadata buildModel(String version, DeploymentStatus status) {
        Instant now = Instant.now();
        return ModelMetadata.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .name(MODEL_NAME)
                .version(version)
                .framework("pytorch")
                .deploymentStatus(status)
                .trainingMetrics(Map.of("accuracy", 0.93))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
