/*
 * Copyright (c) 2026 Ghatana Technologies
 * Integration Tests — Cross-Service Workflow
 */
package com.ghatana.integration.crossservice;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.operator.OperatorResult;
import com.ghatana.yappc.domain.Identifiable;
import com.ghatana.yappc.infrastructure.datacloud.adapter.YappcDataCloudRepository;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import com.ghatana.yappc.services.lifecycle.YappcAepPipelineBootstrapper;
import com.ghatana.yappc.services.lifecycle.dlq.DlqPublisher;
import com.ghatana.yappc.services.lifecycle.operators.AgentDispatchOperator;
import com.ghatana.yappc.services.lifecycle.operators.GateOrchestratorOperator;
import com.ghatana.yappc.services.lifecycle.operators.LifecycleStatePublisherOperator;
import com.ghatana.yappc.services.lifecycle.operators.PhaseTransitionValidatorOperator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validates tenant isolation at cross-product interaction boundaries.
 *
 * <p>Specifically covers:
 * <ul>
 *   <li>YAPPC → Data Cloud: entities saved under the correct tenant scope</li>
 *   <li>YAPPC → AEP pipeline: DLQ publish carries the originating tenant ID</li>
 *   <li>Cross-tenant contamination: data saved for tenant A is not accessible to tenant B</li>
 *   <li>TenantContext cleanup: contexts do not leak between test interactions</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Tenant isolation contract tests across YAPPC, AEP, and Data Cloud boundaries
 * @doc.layer integration
 * @doc.pattern Test, ContractTest
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Cross-product tenant isolation contract")
class CrossProductTenantIsolationTest extends EventloopTestBase {

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ── YAPPC → Data Cloud tenant isolation ───────────────────────────────────

    @Nested
    @DisplayName("YAPPC → Data Cloud: tenant-scoped persistence")
    class YappcDataCloudTenantIsolation {

        @Test
        @DisplayName("save() routes entity to the tenant declared in TenantContext")
        void save_routesToDeclaredTenant() {
            DataCloudClient client = mock(DataCloudClient.class);
            SampleEntity entity = new SampleEntity(UUID.randomUUID(), "Project Alpha", "ACTIVE");

            when(client.save(anyString(), anyString(), any()))
                    .thenAnswer(inv -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = inv.getArgument(2, Map.class);
                        return Promise.of(new DataCloudClient.Entity(
                                entity.id().toString(), "projects", payload,
                                Instant.now(), Instant.now(), 1L));
                    });

            YappcDataCloudRepository<SampleEntity> repository = new YappcDataCloudRepository<>(
                    client, new YappcEntityMapper(new ObjectMapper()), "projects", SampleEntity.class);

            runPromise(() -> {
                TenantContext.setCurrentTenantId("tenant-alpha");
                return repository.save(entity);
            });

            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
            verify(client).save(tenantCaptor.capture(), anyString(), any());
            assertThat(tenantCaptor.getValue()).isEqualTo("tenant-alpha");
        }

        @Test
        @DisplayName("entities saved for tenant A are not visible when queried as tenant B")
        void entities_forTenantA_notVisibleAsTenantB() {
            DataCloudClient client = mock(DataCloudClient.class);
            UUID idA = UUID.randomUUID();
            SampleEntity entityA = new SampleEntity(idA, "Alpha project", "ACTIVE");

            when(client.save(anyString(), anyString(), any()))
                    .thenAnswer(inv -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = inv.getArgument(2, Map.class);
                        return Promise.of(new DataCloudClient.Entity(
                                idA.toString(), "projects", payload,
                                Instant.now(), Instant.now(), 1L));
                    });

            // Save is tenant-scoped by the repository — when queried as tenant-beta the
            // mock returns no result, simulating the server-side tenant filter.
            when(client.findById("tenant-beta", "projects", idA.toString()))
                    .thenReturn(Promise.of(java.util.Optional.empty()));

            YappcDataCloudRepository<SampleEntity> repository = new YappcDataCloudRepository<>(
                    client, new YappcEntityMapper(new ObjectMapper()), "projects", SampleEntity.class);

            // Act: save as tenant-alpha
            runPromise(() -> {
                TenantContext.setCurrentTenantId("tenant-alpha");
                return repository.save(entityA);
            });

            // Act: query as tenant-beta
            java.util.Optional<SampleEntity> found = runPromise(() -> {
                TenantContext.setCurrentTenantId("tenant-beta");
                return repository.findById(idA);
            });

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("TenantContext is cleared after each interaction so tenants do not leak")
        void tenantContext_doesNotLeakBetweenInteractions() {
            DataCloudClient client = mock(DataCloudClient.class);
            SampleEntity entity1 = new SampleEntity(UUID.randomUUID(), "First project", "ACTIVE");
            SampleEntity entity2 = new SampleEntity(UUID.randomUUID(), "Second project", "ACTIVE");

            when(client.save(anyString(), anyString(), any()))
                    .thenAnswer(inv -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = inv.getArgument(2, Map.class);
                        return Promise.of(new DataCloudClient.Entity(
                                UUID.randomUUID().toString(),
                                "projects", payload,
                                Instant.now(), Instant.now(), 1L));
                    });

            YappcDataCloudRepository<SampleEntity> repository = new YappcDataCloudRepository<>(
                    client, new YappcEntityMapper(new ObjectMapper()), "projects", SampleEntity.class);

            // First interaction: tenant-one
            runPromise(() -> {
                TenantContext.setCurrentTenantId("tenant-one");
                return repository.save(entity1);
            });
            TenantContext.clear();

            // Second interaction: tenant-two
            runPromise(() -> {
                TenantContext.setCurrentTenantId("tenant-two");
                return repository.save(entity2);
            });

            // Verify both calls carried their respective tenant IDs
            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
            verify(client, org.mockito.Mockito.times(2)).save(tenantCaptor.capture(), anyString(), any());

            assertThat(tenantCaptor.getAllValues())
                    .containsExactly("tenant-one", "tenant-two");
        }
    }

    // ── YAPPC → AEP pipeline tenant isolation ─────────────────────────────────

    @Nested
    @DisplayName("YAPPC → AEP pipeline: tenant ID propagation")
    class YappcAepPipelineTenantIsolation {

        @Mock private PhaseTransitionValidatorOperator validatorOperator;
        @Mock private GateOrchestratorOperator         gateOperator;
        @Mock private AgentDispatchOperator            dispatchOperator;
        @Mock private LifecycleStatePublisherOperator  publisherOperator;
        @Mock private DlqPublisher                    dlqPublisher;

        private YappcAepPipelineBootstrapper bootstrapper;

        @BeforeEach
        void setUpBootstrapper() {
            bootstrapper = new YappcAepPipelineBootstrapper(
                    validatorOperator, gateOperator, dispatchOperator, publisherOperator, dlqPublisher);

            lenient().when(validatorOperator.initialize(any())).thenReturn(Promise.complete());
            lenient().when(gateOperator.initialize(any())).thenReturn(Promise.complete());
            lenient().when(dispatchOperator.initialize(any())).thenReturn(Promise.complete());
            lenient().when(publisherOperator.initialize(any())).thenReturn(Promise.complete());
            lenient().when(validatorOperator.process(any())).thenReturn(Promise.of(OperatorResult.empty()));
            lenient().when(gateOperator.process(any())).thenReturn(Promise.of(OperatorResult.empty()));
            lenient().when(dispatchOperator.process(any())).thenReturn(Promise.of(OperatorResult.empty()));
            lenient().when(publisherOperator.process(any())).thenReturn(Promise.of(OperatorResult.empty()));
        }

        @Test
        @DisplayName("DLQ publish carries the tenant ID of the originating routeEvent call")
        void dlqPublish_carriesOriginatingTenantId() {
            RuntimeException failure = new RuntimeException("simulated-operator-failure");
            lenient().when(validatorOperator.process(any()))
                    .thenReturn(Promise.ofException(failure));
            lenient().when(dlqPublisher.publish(anyString(), anyString(), anyString(),
                    anyString(), any(), anyString(), any()))
                    .thenReturn(Promise.complete());

            runPromise(bootstrapper::start);

            Map<String, Object> payload = Map.of("fromStage", "planning", "toStage", "development");

            try {
                runPromise(() -> bootstrapper.routeEvent(
                        "tenant-isolated-123",
                        "lifecycle.phase.transition.requested",
                        payload,
                        "corr-isolation"));
            } catch (Exception ignored) {
                // Expected: routeEvent re-propagates after DLQ publish
            }

            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
            verify(dlqPublisher).publish(
                    tenantCaptor.capture(), anyString(), anyString(),
                    anyString(), any(), anyString(), any());

            assertThat(tenantCaptor.getValue()).isEqualTo("tenant-isolated-123");
        }

        @Test
        @DisplayName("two concurrent routeEvent calls with different tenants do not cross-contaminate DLQ entries")
        void routeEvent_differentTenants_separateDlqEntries() {
            RuntimeException failure = new RuntimeException("pipeline-failure");
            lenient().when(validatorOperator.process(any()))
                    .thenReturn(Promise.ofException(failure));
            lenient().when(dlqPublisher.publish(anyString(), anyString(), anyString(),
                    anyString(), any(), anyString(), any()))
                    .thenReturn(Promise.complete());

            runPromise(bootstrapper::start);

            Map<String, Object> payload = Map.of("transition", "planning→development");

            // First tenant
            try {
                runPromise(() -> bootstrapper.routeEvent(
                        "tenant-A",
                        "lifecycle.phase.transition.requested",
                        payload,
                        null));
            } catch (Exception ignored) { }

            // Second tenant (sequential in the eventloop)
            try {
                runPromise(() -> bootstrapper.routeEvent(
                        "tenant-B",
                        "lifecycle.phase.transition.requested",
                        payload,
                        null));
            } catch (Exception ignored) { }

            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
            verify(dlqPublisher, org.mockito.Mockito.times(2)).publish(
                    tenantCaptor.capture(), anyString(), anyString(),
                    anyString(), any(), anyString(), any());

            assertThat(tenantCaptor.getAllValues())
                    .containsExactlyInAnyOrder("tenant-A", "tenant-B");
        }

        @Test
        @DisplayName("successful routeEvent does not publish DLQ entry for any tenant")
        void routeEvent_success_noDlqForAnyTenant() {
            runPromise(bootstrapper::start);

            Map<String, Object> payload = Map.of("fromStage", "qa", "toStage", "staging");

            runPromise(() -> bootstrapper.routeEvent(
                    "tenant-clean", "lifecycle.phase.transition.requested", payload, null));

            verify(dlqPublisher, never()).publish(
                    anyString(), anyString(), anyString(),
                    anyString(), any(), anyString(), any());
        }
    }

    // ── Shared helper ──────────────────────────────────────────────────────────

    /** Minimal domain entity used as a stable test subject across repository tests. */
    record SampleEntity(UUID id, String name, String status) implements Identifiable<UUID> {
        @Override
        public UUID getId() { return id; }
    }
}
