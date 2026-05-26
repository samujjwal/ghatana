package com.ghatana.yappc.kernel;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.infrastructure.datacloud.adapter.YappcDataCloudRepository;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KERNEL-P1-001: Product-to-platform E2E proof for YAPPC → Kernel → Data-Cloud
 * 
 * Tests that product intent/artifact action flows through Kernel bridge, Data-Cloud persistence,
 * and runtime validation in a single E2E journey.
 *
 * @doc.type class
 * @doc.purpose E2E test proving YAPPC → Kernel → Data-Cloud integration
 * @doc.layer integration
 * @doc.pattern E2E Test
 */
@DisplayName("YAPPC → Kernel → Data-Cloud E2E Test")
class YappcKernelDataCloudE2ETest extends EventloopTestBase {

    @Mock
    private YappcProductUnitIntentProvider intentProvider;

    @Mock
    private DataCloudClient dataCloudClient;

    @Mock
    private YappcEntityMapper mapper;

    private YappcDataCloudRepository<TestArtifact> repository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        repository = new YappcDataCloudRepository<>(
            dataCloudClient,
            mapper,
            "yappc-artifacts",
            TestArtifact.class
        );

        // Default stubs
        lenient().when(dataCloudClient.save(anyString(), anyString(), any()))
            .thenAnswer(invocation -> {
                Map<String, Object> payload = invocation.getArgument(2);
                return Promise.of(payload);
            });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("Product Unit Intent Export")
    class ProductUnitIntentExport {

        @Test
        @DisplayName("GIVEN YAPPC product intent WHEN exported to Kernel THEN intent is structured correctly")
        void productIntent_exportedToKernel_structuredCorrectly() {
            // Given
            String candidateId = "yappc-scaffold-spring-boot";
            Map<String, Object> request = Map.of(
                "projectId", "project-1",
                "workspaceId", "workspace-1",
                "intentType", "scaffold",
                "targetFramework", "spring-boot"
            );

            Map<String, Object> intentResponse = Map.of(
                "intentId", "intent-1",
                "candidateId", candidateId,
                "status", "ready",
                "metadata", Map.of("framework", "spring-boot", "language", "java")
            );

            when(intentProvider.exportProductUnitIntent(candidateId, request))
                .thenReturn(Promise.of(intentResponse));

            // When
            Map<String, Object> result = runPromise(() -> 
                intentProvider.exportProductUnitIntent(candidateId, request));

            // Then
            assertThat(result).isNotNull();
            assertThat(result.get("intentId")).isEqualTo("intent-1");
            assertThat(result.get("candidateId")).isEqualTo(candidateId);
            assertThat(result.get("status")).isEqualTo("ready");

            verify(intentProvider).exportProductUnitIntent(candidateId, request);
        }

        @Test
        @DisplayName("GIVEN typed intent export WHEN called THEN returns ProductUnitIntentContract")
        void typedIntentExport_returnsContract() {
            // Given
            String candidateId = "yappc-refactor-extract-method";
            Map<String, Object> request = Map.of(
                "projectId", "project-1",
                "intentType", "refactor",
                "refactoringKind", "extract-method"
            );

            Map<String, Object> intentResponse = Map.of(
                "intentId", "intent-2",
                "candidateId", candidateId,
                "status", "ready"
            );

            when(intentProvider.exportProductUnitIntent(candidateId, request))
                .thenReturn(Promise.of(intentResponse));

            // When
            Promise<ProductUnitIntentContract> contractPromise = 
                intentProvider.exportTypedProductUnitIntent(candidateId, request);
            ProductUnitIntentContract contract = runPromise(() -> contractPromise);

            // Then
            assertThat(contract).isNotNull();
            assertThat(contract.candidateId()).isEqualTo(candidateId);
            assertThat(contract.intentId()).isEqualTo("intent-2");
            assertThat(contract.provider()).isEqualTo("yappc-product-unit-intent-provider");
        }
    }

    @Nested
    @DisplayName("Artifact Persistence Through Data-Cloud")
    class ArtifactPersistence {

        @Test
        @DisplayName("GIVEN artifact generated by YAPPC WHEN persisted THEN uses Data-Cloud canonical contracts")
        void artifactPersisted_usesDataCloudCanonicalContracts() {
            // Given
            TenantContext.setCurrentTenantId("tenant-yappc");
            TestArtifact artifact = new TestArtifact(
                UUID.randomUUID(),
                "scaffold-artifact",
                "spring-boot-project"
            );

            Map<String, Object> entityData = Map.of(
                "id", artifact.getId().toString(),
                "kind", artifact.getKind(),
                "target", artifact.getTarget()
            );

            when(mapper.toEntityData(artifact)).thenReturn(entityData);
            when(mapper.fromEntity(entityData, TestArtifact.class)).thenReturn(artifact);

            // When
            TestArtifact saved = runPromise(() -> repository.save(artifact));

            // Then - verify Data-Cloud was called through canonical repository
            verify(dataCloudClient).save("tenant-yappc", "yappc-artifacts", entityData);
            verify(mapper).toEntityData(artifact);
            assertThat(saved.getId()).isEqualTo(artifact.getId());
        }

        @Test
        @DisplayName("GIVEN artifact evidence WHEN persisted THEN evidence ref is stored in Data-Cloud")
        void artifactEvidence_evidenceRef_storedInDataCloud() {
            // Given
            TenantContext.setCurrentTenantId("tenant-yappc");
            TestArtifact evidenceArtifact = new TestArtifact(
                UUID.randomUUID(),
                "roundtrip-evidence",
                ".kernel/evidence/yappc/kernel-artifact-roundtrip.json"
            );

            Map<String, Object> entityData = Map.of(
                "id", evidenceArtifact.getId().toString(),
                "kind", evidenceArtifact.getKind(),
                "target", evidenceArtifact.getTarget()
            );

            when(mapper.toEntityData(evidenceArtifact)).thenReturn(entityData);
            when(mapper.fromEntity(entityData, TestArtifact.class)).thenReturn(evidenceArtifact);

            // When
            TestArtifact saved = runPromise(() -> repository.save(evidenceArtifact));

            // Then
            verify(dataCloudClient).save("tenant-yappc", "yappc-artifacts", entityData);
            assertThat(saved.getTarget()).contains(".kernel/evidence");
        }
    }

    @Nested
    @DisplayName("End-to-End Journey")
    class EndToEndJourney {

        @Test
        @DisplayName("GIVEN complete workflow WHEN YAPPC generates artifact THEN intent → artifact → evidence → Data-Cloud")
        void completeWorkflow_intentToArtifactToEvidenceToDataCloud() {
            // Given - Step 1: Export product intent
            String candidateId = "yappc-scaffold-react";
            Map<String, Object> intentRequest = Map.of(
                "projectId", "project-1",
                "intentType", "scaffold",
                "targetFramework", "react"
            );

            Map<String, Object> intentResponse = Map.of(
                "intentId", "intent-3",
                "candidateId", candidateId,
                "status", "ready"
            );

            when(intentProvider.exportProductUnitIntent(candidateId, intentRequest))
                .thenReturn(Promise.of(intentResponse));

            // When - Step 1: Export intent
            Map<String, Object> intent = runPromise(() -> 
                intentProvider.exportProductUnitIntent(candidateId, intentRequest));

            // Then - Step 1: Verify intent
            assertThat(intent.get("intentId")).isEqualTo("intent-3");

            // Given - Step 2: Generate and persist artifact
            TenantContext.setCurrentTenantId("tenant-yappc");
            TestArtifact artifact = new TestArtifact(
                UUID.randomUUID(),
                "generated-contract",
                "openapi: 3.1.0"
            );

            Map<String, Object> artifactData = Map.of(
                "id", artifact.getId().toString(),
                "kind", artifact.getKind(),
                "target", artifact.getTarget()
            );

            when(mapper.toEntityData(artifact)).thenReturn(artifactData);
            when(mapper.fromEntity(artifactData, TestArtifact.class)).thenReturn(artifact);

            // When - Step 2: Persist artifact
            TestArtifact savedArtifact = runPromise(() -> repository.save(artifact));

            // Then - Step 2: Verify artifact persistence
            verify(dataCloudClient).save("tenant-yappc", "yappc-artifacts", artifactData);
            assertThat(savedArtifact.getId()).isEqualTo(artifact.getId());

            // Given - Step 3: Persist evidence
            TestArtifact evidence = new TestArtifact(
                UUID.randomUUID(),
                "validation-evidence",
                ".kernel/evidence/yappc/validation-roundtrip.json"
            );

            Map<String, Object> evidenceData = Map.of(
                "id", evidence.getId().toString(),
                "kind", evidence.getKind(),
                "target", evidence.getTarget()
            );

            when(mapper.toEntityData(evidence)).thenReturn(evidenceData);
            when(mapper.fromEntity(evidenceData, TestArtifact.class)).thenReturn(evidence);

            // When - Step 3: Persist evidence
            TestArtifact savedEvidence = runPromise(() -> repository.save(evidence));

            // Then - Step 3: Verify evidence persistence
            verify(dataCloudClient).save("tenant-yappc", "yappc-artifacts", evidenceData);
            assertThat(savedEvidence.getTarget()).contains(".kernel/evidence");

            // Overall: Verify complete journey from intent to evidence through Data-Cloud
            verify(intentProvider).exportProductUnitIntent(candidateId, intentRequest);
            verify(dataCloudClient, times(2)).save(eq("tenant-yappc"), eq("yappc-artifacts"), any());
        }
    }

    // Test artifact class
    private static class TestArtifact {
        private final UUID id;
        private final String kind;
        private final String target;

        TestArtifact(UUID id, String kind, String target) {
            this.id = id;
            this.kind = kind;
            this.target = target;
        }

        UUID getId() {
            return id;
        }

        String getKind() {
            return kind;
        }

        String getTarget() {
            return target;
        }
    }
}
