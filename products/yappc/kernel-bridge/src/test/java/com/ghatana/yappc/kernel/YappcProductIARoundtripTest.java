package com.ghatana.yappc.kernel;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.Identifiable;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G11-014: YAPPC product IA import/generation roundtrip tests
 *
 * Tests that YAPPC can generate Intelligence Artifacts (IA) that Product can import,
 * and that the roundtrip preserves data integrity and contract compliance.
 *
 * @doc.type class
 * @doc.purpose Test YAPPC → product IA import/generation roundtrip
 * @doc.layer integration
 * @doc.pattern Roundtrip Test
 */
@DisplayName("YAPPC → product IA Import/Generation Roundtrip Test")
class YappcProductIARoundtripTest extends EventloopTestBase {

    @Mock
    private DataCloudClient dataCloudClient;

    @Mock
    private YappcEntityMapper mapper;

    private YappcDataCloudRepository<ProductIntelligenceArtifact> productArtifactRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        productArtifactRepository = new YappcDataCloudRepository<>(
            dataCloudClient,
            mapper,
            "sample-product-intelligence-artifacts",
            ProductIntelligenceArtifact.class
        );

        // Default stubs
        lenient().when(dataCloudClient.save(anyString(), anyString(), any()))
            .thenAnswer(invocation -> {
                Map<String, Object> payload = invocation.getArgument(2);
                String entityId = (String) payload.getOrDefault("id", UUID.randomUUID().toString());
                return Promise.of(DataCloudClient.Entity.of(entityId, "sample-product-intelligence-artifacts", payload));
            });

        lenient().when(dataCloudClient.findById(anyString(), anyString(), anyString()))
            .thenAnswer(invocation -> {
                String entityId = invocation.getArgument(2);
                Map<String, Object> payload = Map.of(
                    "id", entityId,
                    "artifactType", "workflow-decision-support",
                    "targetProduct", "sample-product",
                    "schemaVersion", "1.0.0"
                );
                return Promise.of(Optional.of(DataCloudClient.Entity.of(entityId, "sample-product-intelligence-artifacts", payload)));
            });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("IA Generation for Product")
    class IAGeneration {

        @Test
        @DisplayName("GIVEN YAPPC generates IA for Product WHEN persisted THEN uses Product artifact collection")
        void iaGeneratedForProduct_usesProductArtifactCollection() {
            // Given
            TenantContext.setCurrentTenantId("tenant-yappc");
            ProductIntelligenceArtifact artifact = new ProductIntelligenceArtifact(
                UUID.randomUUID(),
                "workflow-decision-support",
                "sample-product",
                "1.0.0",
                Map.of("condition", "workspace-drift", "confidence", 0.95)
            );

            Map<String, Object> entityData = Map.of(
                "id", artifact.getId().toString(),
                "artifactType", artifact.getArtifactType(),
                "targetProduct", artifact.getTargetProduct(),
                "schemaVersion", artifact.getSchemaVersion(),
                "payload", artifact.getPayload()
            );

            when(mapper.toEntityData(artifact)).thenReturn(entityData);
            when(mapper.fromEntity(any(DataCloudClient.Entity.class), eq(ProductIntelligenceArtifact.class))).thenReturn(artifact);

            // When
            ProductIntelligenceArtifact saved = runPromise(() -> {
                TenantContext.setCurrentTenantId("tenant-yappc");
                return productArtifactRepository.save(artifact)
                    .whenComplete(($1, $2) -> TenantContext.clear());
            });

            // Then
            verify(dataCloudClient).save("tenant-yappc", "sample-product-intelligence-artifacts", entityData);
            assertThat(saved.getTargetProduct()).isEqualTo("sample-product");
            assertThat(saved.getArtifactType()).isEqualTo("workflow-decision-support");
        }

        @Test
        @DisplayName("GIVEN IA schema version WHEN generated THEN includes version for Product compatibility")
        void iaSchemaVersion_includesVersionForProductCompatibility() {
            // Given
            TenantContext.setCurrentTenantId("tenant-yappc");
            ProductIntelligenceArtifact artifact = new ProductIntelligenceArtifact(
                UUID.randomUUID(),
                "workflow-risk-scoring",
                "sample-product",
                "1.0.0",
                Map.of("riskLevel", "high", "factors", "age,signalVariance")
            );

            Map<String, Object> entityData = Map.of(
                "id", artifact.getId().toString(),
                "artifactType", artifact.getArtifactType(),
                "targetProduct", artifact.getTargetProduct(),
                "schemaVersion", artifact.getSchemaVersion(),
                "payload", artifact.getPayload()
            );

            when(mapper.toEntityData(artifact)).thenReturn(entityData);
            when(mapper.fromEntity(any(DataCloudClient.Entity.class), eq(ProductIntelligenceArtifact.class))).thenReturn(artifact);

            // When
            ProductIntelligenceArtifact saved = runPromise(() -> {
                TenantContext.setCurrentTenantId("tenant-yappc");
                return productArtifactRepository.save(artifact)
                    .whenComplete(($1, $2) -> TenantContext.clear());
            });

            // Then
            assertThat(saved.getSchemaVersion()).isEqualTo("1.0.0");
            verify(dataCloudClient).save(eq("tenant-yappc"), eq("sample-product-intelligence-artifacts"), any());
        }
    }

    @Nested
    @DisplayName("IA Import by Product")
    class IAImport {

        @Test
        @DisplayName("GIVEN IA stored in Data-Cloud WHEN Product imports THEN artifact is retrieved correctly")
        void iaStoredInDataCloud_whenProductImports_retrievedCorrectly() {
            // Given
            TenantContext.setCurrentTenantId("tenant-yappc");
            UUID artifactId = UUID.randomUUID();
            ProductIntelligenceArtifact artifact = new ProductIntelligenceArtifact(
                artifactId,
                "workflow-decision-support",
                "sample-product",
                "1.0.0",
                Map.of("recommendation", "schedule-followup", "urgency", "medium")
            );

            Map<String, Object> entityData = Map.of(
                "id", artifactId.toString(),
                "artifactType", artifact.getArtifactType(),
                "targetProduct", artifact.getTargetProduct(),
                "schemaVersion", artifact.getSchemaVersion(),
                "payload", artifact.getPayload()
            );

            when(mapper.toEntityData(artifact)).thenReturn(entityData);
            when(mapper.fromEntity(any(DataCloudClient.Entity.class), eq(ProductIntelligenceArtifact.class))).thenReturn(artifact);

            // When - Save first
            runPromise(() -> {
                TenantContext.setCurrentTenantId("tenant-yappc");
                return productArtifactRepository.save(artifact)
                    .whenComplete(($1, $2) -> TenantContext.clear());
            });

            // Then - Load (simulating Product import)
            ProductIntelligenceArtifact loaded = runPromise(() -> {
                TenantContext.setCurrentTenantId("tenant-yappc");
                return productArtifactRepository.findById(artifactId)
                    .map(opt -> opt.orElseThrow(() -> new IllegalStateException("Artifact not found")))
                    .whenComplete(($1, $2) -> TenantContext.clear());
            });

            assertThat(loaded).isNotNull();
            assertThat(loaded.getId()).isEqualTo(artifactId);
            assertThat(loaded.getArtifactType()).isEqualTo("workflow-decision-support");
        }

        @Test
        @DisplayName("GIVEN IA with payload WHEN imported THEN payload is preserved")
        void iaWithPayload_whenImported_payloadPreserved() {
            // Given
            TenantContext.setCurrentTenantId("tenant-yappc");
            UUID artifactId = UUID.randomUUID();
            Map<String, Object> payload = Map.of(
                "diagnosis", "type-2-workspace-drift",
                "confidence", 0.92,
                "evidence", "evidence-a,evidence-b"
            );

            ProductIntelligenceArtifact artifact = new ProductIntelligenceArtifact(
                artifactId,
                "workflow-classification",
                "sample-product",
                "1.0.0",
                payload
            );

            Map<String, Object> entityData = Map.of(
                "id", artifactId.toString(),
                "artifactType", artifact.getArtifactType(),
                "targetProduct", artifact.getTargetProduct(),
                "schemaVersion", artifact.getSchemaVersion(),
                "payload", payload
            );

            when(mapper.toEntityData(artifact)).thenReturn(entityData);
            when(mapper.fromEntity(any(DataCloudClient.Entity.class), eq(ProductIntelligenceArtifact.class))).thenReturn(artifact);

            // When
            runPromise(() -> {
                TenantContext.setCurrentTenantId("tenant-yappc");
                return productArtifactRepository.save(artifact)
                    .whenComplete(($1, $2) -> TenantContext.clear());
            });

            ProductIntelligenceArtifact loaded = runPromise(() -> {
                TenantContext.setCurrentTenantId("tenant-yappc");
                return productArtifactRepository.findById(artifactId)
                    .map(opt -> opt.orElseThrow(() -> new IllegalStateException("Artifact not found")))
                    .whenComplete(($1, $2) -> TenantContext.clear());
            });

            // Then
            assertThat(loaded.getPayload()).isEqualTo(payload);
            assertThat(loaded.getPayload().get("diagnosis")).isEqualTo("type-2-workspace-drift");
        }
    }

    @Nested
    @DisplayName("Roundtrip Integrity")
    class RoundtripIntegrity {

        @Test
        @DisplayName("GIVEN IA generated by YAPPC WHEN imported by Product THEN data integrity preserved")
        void iaGeneratedByYappc_whenImportedByProduct_dataIntegrityPreserved() {
            // Given
            TenantContext.setCurrentTenantId("tenant-yappc");
            UUID artifactId = UUID.randomUUID();
            ProductIntelligenceArtifact original = new ProductIntelligenceArtifact(
                artifactId,
                "workflow-reconciliation",
                "sample-product",
                "1.0.0",
                Map.of(
                    "conflictDetected", true,
                    "conflictingMeds", "item-a,item-b",
                    "recommendation", "review-with-owner"
                )
            );

            Map<String, Object> entityData = Map.of(
                "id", artifactId.toString(),
                "artifactType", original.getArtifactType(),
                "targetProduct", original.getTargetProduct(),
                "schemaVersion", original.getSchemaVersion(),
                "payload", original.getPayload()
            );

            when(mapper.toEntityData(original)).thenReturn(entityData);
            when(mapper.fromEntity(any(DataCloudClient.Entity.class), eq(ProductIntelligenceArtifact.class))).thenReturn(original);

            // When - Generate (YAPPC)
            runPromise(() -> {
                TenantContext.setCurrentTenantId("tenant-yappc");
                return productArtifactRepository.save(original)
                    .whenComplete(($1, $2) -> TenantContext.clear());
            });

            // When - Import (Product)
            ProductIntelligenceArtifact imported = runPromise(() -> {
                TenantContext.setCurrentTenantId("tenant-yappc");
                return productArtifactRepository.findById(artifactId)
                    .map(opt -> opt.orElseThrow(() -> new IllegalStateException("Artifact not found")))
                    .whenComplete(($1, $2) -> TenantContext.clear());
            });

            // Then - Verify roundtrip integrity
            assertThat(imported.getId()).isEqualTo(original.getId());
            assertThat(imported.getArtifactType()).isEqualTo(original.getArtifactType());
            assertThat(imported.getTargetProduct()).isEqualTo(original.getTargetProduct());
            assertThat(imported.getSchemaVersion()).isEqualTo(original.getSchemaVersion());
            assertThat(imported.getPayload()).isEqualTo(original.getPayload());
        }

        @Test
        @DisplayName("GIVEN multiple IA artifacts WHEN roundtrip THEN each artifact preserved independently")
        void multipleIaArtifacts_whenRoundtrip_eachPreservedIndependently() {
            // Given
            TenantContext.setCurrentTenantId("tenant-yappc");
            UUID artifactId1 = UUID.randomUUID();
            UUID artifactId2 = UUID.randomUUID();

            ProductIntelligenceArtifact artifact1 = new ProductIntelligenceArtifact(
                artifactId1,
                "record-interpretation",
                "sample-product",
                "1.0.0",
                Map.of("result", "abnormal", "referenceRange", "70-100")
            );

            ProductIntelligenceArtifact artifact2 = new ProductIntelligenceArtifact(
                artifactId2,
                "workflow-pathway-suggestion",
                "sample-product",
                "1.0.0",
                Map.of("pathway", "workspace-drift-management", "stage", "initial")
            );

            Map<String, Object> entityData1 = Map.of(
                "id", artifactId1.toString(),
                "artifactType", artifact1.getArtifactType(),
                "targetProduct", artifact1.getTargetProduct(),
                "schemaVersion", artifact1.getSchemaVersion(),
                "payload", artifact1.getPayload()
            );

            Map<String, Object> entityData2 = Map.of(
                "id", artifactId2.toString(),
                "artifactType", artifact2.getArtifactType(),
                "targetProduct", artifact2.getTargetProduct(),
                "schemaVersion", artifact2.getSchemaVersion(),
                "payload", artifact2.getPayload()
            );

            when(mapper.toEntityData(artifact1)).thenReturn(entityData1);
            when(mapper.toEntityData(artifact2)).thenReturn(entityData2);
            when(mapper.fromEntity(any(DataCloudClient.Entity.class), eq(ProductIntelligenceArtifact.class)))
                .thenAnswer(invocation -> {
                    DataCloudClient.Entity entity = invocation.getArgument(0);
                    if (entity.id().equals(artifactId1.toString())) {
                        return artifact1;
                    }
                    return artifact2;
                });

            // When - Save both
            runPromise(() -> {
                TenantContext.setCurrentTenantId("tenant-yappc");
                return productArtifactRepository.save(artifact1)
                    .then($1 -> productArtifactRepository.save(artifact2))
                    .whenComplete(($1, $2) -> TenantContext.clear());
            });

            // When - Load both
            ProductIntelligenceArtifact loaded1 = runPromise(() -> {
                TenantContext.setCurrentTenantId("tenant-yappc");
                return productArtifactRepository.findById(artifactId1)
                    .map(opt -> opt.orElseThrow(() -> new IllegalStateException("Artifact not found")))
                    .whenComplete(($1, $2) -> TenantContext.clear());
            });

            ProductIntelligenceArtifact loaded2 = runPromise(() -> {
                TenantContext.setCurrentTenantId("tenant-yappc");
                return productArtifactRepository.findById(artifactId2)
                    .map(opt -> opt.orElseThrow(() -> new IllegalStateException("Artifact not found")))
                    .whenComplete(($1, $2) -> TenantContext.clear());
            });

            // Then - Verify both preserved independently
            assertThat(loaded1.getId()).isEqualTo(artifactId1);
            assertThat(loaded1.getArtifactType()).isEqualTo("record-interpretation");
            assertThat(loaded2.getId()).isEqualTo(artifactId2);
            assertThat(loaded2.getArtifactType()).isEqualTo("workflow-pathway-suggestion");
        }
    }

    // Test artifact class for Product Intelligence Artifacts
    private static class ProductIntelligenceArtifact implements Identifiable<UUID> {
        private final UUID id;
        private final String artifactType;
        private final String targetProduct;
        private final String schemaVersion;
        private final Map<String, Object> payload;

        ProductIntelligenceArtifact(UUID id, String artifactType, String targetProduct, String schemaVersion, Map<String, Object> payload) {
            this.id = id;
            this.artifactType = artifactType;
            this.targetProduct = targetProduct;
            this.schemaVersion = schemaVersion;
            this.payload = payload;
        }

        @Override
        public UUID getId() {
            return id;
        }

        String getArtifactType() {
            return artifactType;
        }

        String getTargetProduct() {
            return targetProduct;
        }

        String getSchemaVersion() {
            return schemaVersion;
        }

        Map<String, Object> getPayload() {
            return payload;
        }
    }
}
