/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.feature;

import com.ghatana.aiplatform.registry.DeploymentStatus;
import com.ghatana.aiplatform.registry.ModelMetadata;
import com.ghatana.aiplatform.registry.ModelRegistryService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AepModelRegistryClient}.
 *
 * <p>Verifies the async façade over {@link ModelRegistryService}:
 * all lifecycle operations must be dispatched off the ActiveJ event-loop
 * thread and delegate correctly to the underlying synchronous service.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AepModelRegistryClient")
class AepModelRegistryClientTest extends EventloopTestBase {

    @Mock
    private ModelRegistryService modelRegistryService;

    private AepModelRegistryClient client;

    @BeforeEach
    void setUp() {
        client = new AepModelRegistryClient(
                modelRegistryService,
                Executors.newSingleThreadExecutor());
    }

    // =========================================================================
    // Construction
    // =========================================================================
    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("null delegate throws NullPointerException")
        void nullDelegate_throwsNpe() {
            assertThatThrownBy(() ->
                    new AepModelRegistryClient(null, Executors.newSingleThreadExecutor()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("delegate");
        }

        @Test
        @DisplayName("null executor throws NullPointerException")
        void nullExecutor_throwsNpe() {
            assertThatThrownBy(() ->
                    new AepModelRegistryClient(modelRegistryService, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("blockingExecutor");
        }
    }

    // =========================================================================
    // Registration
    // =========================================================================
    @Nested
    @DisplayName("Registration")
    class RegistrationTests {

        @Test
        @DisplayName("register(tenantId, model) delegates to the underlying service")
        void register_delegatesToService() {
            ModelMetadata model = sampleModel(DeploymentStatus.DEVELOPMENT);

            runPromise(() -> client.register("tenant-1", model));

            verify(modelRegistryService).register(model);
        }

        @Test
        @DisplayName("registerStaged builds a STAGED model and delegates to service")
        void registerStaged_buildsStagedModelAndDelegates() {
            Map<String, Double> metrics = Map.of("f1_score", 0.91, "auc_roc", 0.96);

            runPromise(() -> client.registerStaged(
                    "t1", "pattern-recommender", "v3.0.0", "sklearn", metrics));

            verify(modelRegistryService).register(any(ModelMetadata.class));
        }
    }

    // =========================================================================
    // Lookup
    // =========================================================================
    @Nested
    @DisplayName("Lookup")
    class LookupTests {

        @Test
        @DisplayName("findByName delegates to service and returns result")
        void findByName_delegatesAndReturnsResult() {
            ModelMetadata model = sampleModel(DeploymentStatus.PRODUCTION);
            when(modelRegistryService.findByName("t1", "event-classifier", "v2.0.0"))
                    .thenReturn(Optional.of(model));

            Optional<ModelMetadata> result = runPromise(() ->
                    client.findByName("t1", "event-classifier", "v2.0.0"));

            assertThat(result).contains(model);
        }

        @Test
        @DisplayName("findByName returns empty when model is not found")
        void findByName_notFound_returnsEmpty() {
            when(modelRegistryService.findByName(eq("t1"), eq("missing-model"), any()))
                    .thenReturn(Optional.empty());

            Optional<ModelMetadata> result = runPromise(() ->
                    client.findByName("t1", "missing-model", null));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("findActiveModel returns ACTIVE-status model when found")
        void findActiveModel_activeModelFound_returnsIt() {
            ModelMetadata activeModel = sampleModel(DeploymentStatus.ACTIVE);
            when(modelRegistryService.findByStatus("t1", DeploymentStatus.ACTIVE))
                    .thenReturn(List.of(activeModel));

            Optional<ModelMetadata> result = runPromise(() ->
                    client.findActiveModel("t1", activeModel.getName()));

            assertThat(result).contains(activeModel);
        }

        @Test
        @DisplayName("findActiveModel falls back to PRODUCTION status when no ACTIVE model exists")
        void findActiveModel_noActive_fallsBackToProduction() {
            ModelMetadata prodModel = sampleModel(DeploymentStatus.PRODUCTION);
            when(modelRegistryService.findByStatus("t1", DeploymentStatus.ACTIVE))
                    .thenReturn(List.of());
            when(modelRegistryService.findByStatus("t1", DeploymentStatus.PRODUCTION))
                    .thenReturn(List.of(prodModel));

            Optional<ModelMetadata> result = runPromise(() ->
                    client.findActiveModel("t1", prodModel.getName()));

            assertThat(result).contains(prodModel);
        }

        @Test
        @DisplayName("findActiveModel returns empty when neither ACTIVE nor PRODUCTION exists")
        void findActiveModel_noneFound_returnsEmpty() {
            when(modelRegistryService.findByStatus("t1", DeploymentStatus.ACTIVE))
                    .thenReturn(List.of());
            when(modelRegistryService.findByStatus("t1", DeploymentStatus.PRODUCTION))
                    .thenReturn(List.of());

            Optional<ModelMetadata> result = runPromise(() ->
                    client.findActiveModel("t1", "no-such-model"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("listVersions delegates to the service and returns all versions")
        void listVersions_delegatesAndReturnsVersions() {
            List<ModelMetadata> versions = List.of(
                    sampleModelVersion("event-classifier", "v1.0.0", DeploymentStatus.DEPRECATED),
                    sampleModelVersion("event-classifier", "v2.0.0", DeploymentStatus.PRODUCTION));
            when(modelRegistryService.listVersions("t1", "event-classifier")).thenReturn(versions);

            List<ModelMetadata> result = runPromise(() ->
                    client.listVersions("t1", "event-classifier"));

            assertThat(result).hasSize(2);
            assertThat(result.get(1).getVersion()).isEqualTo("v2.0.0");
        }

        @Test
        @DisplayName("listVersions returns empty list when no versions exist")
        void listVersions_none_returnsEmpty() {
            when(modelRegistryService.listVersions("t1", "unknown")).thenReturn(List.of());

            List<ModelMetadata> result = runPromise(() ->
                    client.listVersions("t1", "unknown"));

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // Lifecycle transitions
    // =========================================================================
    @Nested
    @DisplayName("Lifecycle transitions")
    class LifecycleTests {

        @Test
        @DisplayName("promoteToProduction calls updateStatus with PRODUCTION")
        void promoteToProduction_updatesStatusToProduction() {
            UUID modelId = UUID.randomUUID();

            runPromise(() -> client.promoteToProduction("t1", modelId));

            verify(modelRegistryService).updateStatus("t1", modelId, DeploymentStatus.PRODUCTION);
        }

        @Test
        @DisplayName("deprecate calls updateStatus with DEPRECATED")
        void deprecate_updatesStatusToDeprecated() {
            UUID modelId = UUID.randomUUID();

            runPromise(() -> client.deprecate("t1", modelId));

            verify(modelRegistryService).updateStatus("t1", modelId, DeploymentStatus.DEPRECATED);
        }

        @Test
        @DisplayName("promoteToCanary calls updateStatus with CANARY")
        void promoteToCanary_updatesStatusToCanary() {
            UUID modelId = UUID.randomUUID();

            runPromise(() -> client.promoteToCanary("t1", modelId));

            verify(modelRegistryService).updateStatus("t1", modelId, DeploymentStatus.CANARY);
        }
    }

    // =========================================================================
    // Null-safety
    // =========================================================================
    @Nested
    @DisplayName("Null-safety")
    class NullSafetyTests {

        @Test
        @DisplayName("register with null tenantId throws NullPointerException")
        void register_nullTenantId_throwsNpe() {
            ModelMetadata model = sampleModel(DeploymentStatus.STAGED);
            assertThatThrownBy(() -> runPromise(() -> client.register(null, model)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("register with null model throws NullPointerException")
        void register_nullModel_throwsNpe() {
            assertThatThrownBy(() -> runPromise(() -> client.register("t1", null)))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("findByName with null tenantId throws NullPointerException")
        void findByName_nullTenantId_throwsNpe() {
            assertThatThrownBy(() -> runPromise(() -> client.findByName(null, "model", "v1")))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("findByName with null name throws NullPointerException")
        void findByName_nullName_throwsNpe() {
            assertThatThrownBy(() -> runPromise(() -> client.findByName("t1", null, "v1")))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ModelMetadata sampleModel(DeploymentStatus status) {
        return sampleModelVersion("event-classifier", "v2.0.0", status);
    }

    private ModelMetadata sampleModelVersion(String name, String version, DeploymentStatus status) {
        Instant now = Instant.now();
        return ModelMetadata.builder()
                .id(UUID.randomUUID())
                .tenantId("t1")
                .name(name)
                .version(version)
                .framework("sklearn")
                .deploymentStatus(status)
                .trainingMetrics(Map.of("f1_score", 0.91))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
