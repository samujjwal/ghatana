package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.Identifiable;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contract-level integration coverage proving YAPPC persists release artifacts through
 * canonical Data-Cloud contracts instead of product-local duplicate storage.
 *
 * @doc.type test
 * @doc.purpose Verifies YAPPC to Data-Cloud persistence contracts for release evidence
 * @doc.layer integration
 * @doc.pattern Contract Test
 */
@DisplayName("YAPPC Data-Cloud contract integration")
class YappcDataCloudContractIntegrationTest extends EventloopTestBase {

    private static final String TENANT_A = "tenant-yappc-a";
    private static final String TENANT_B = "tenant-yappc-b";

    @Mock
    private DataCloudClient dataCloudClient;

    private YappcEntityMapper mapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mapper = new YappcEntityMapper(objectMapper);

        when(dataCloudClient.save(anyString(), anyString(), any()))
            .thenAnswer(invocation -> {
                String collection = invocation.getArgument(1);
                Map<String, Object> payload = invocation.getArgument(2);
                return Promise.of(DataCloudClient.Entity.of(String.valueOf(payload.get("id")), collection, payload));
            });
    }

    @AfterEach
    void tearDown() {
        runBlocking(TenantContext::clear);
        TenantContext.clear();
    }

    @Test
    @DisplayName("persists YAPPC artifact metadata, context, generated contracts, evidence, query, and tenants through Data-Cloud")
    void persistsYappcReleaseArtifactsThroughCanonicalDataCloudContracts() {
        YappcDataCloudRepository<ContractRecord> artifacts = repository("yappc-artifact-metadata");
        YappcDataCloudRepository<ContractRecord> projectContext = repository("yappc-project-workspace-context");
        YappcDataCloudRepository<ContractRecord> generatedContracts = repository("yappc-generated-contracts");
        YappcDataCloudRepository<ContractRecord> artifactEvidence = repository("yappc-artifact-evidence");

        ContractRecord artifact = record("artifact-metadata")
            .with("projectId", "project-1")
            .with("workspaceId", "workspace-1")
            .with("artifactType", "generated-contract")
            .with("metadata", Map.of("language", "typescript", "generator", "yappc-scaffold"));

        ContractRecord context = record("project-workspace-context")
            .with("projectId", "project-1")
            .with("workspaceId", "workspace-1")
            .with("intentId", "intent-1")
            .with("contextType", "product-unit-intent");

        ContractRecord contract = record("generated-contract")
            .with("projectId", "project-1")
            .with("workspaceId", "workspace-1")
            .with("contractKind", "openapi")
            .with("contractBody", "openapi: 3.1.0");

        ContractRecord evidence = record("artifact-evidence")
            .with("projectId", "project-1")
            .with("workspaceId", "workspace-1")
            .with("evidenceKind", "roundtrip-validation")
            .with("evidenceRef", ".kernel/evidence/yappc/kernel-artifact-roundtrip.json");

        TenantContext.setCurrentTenantId(TENANT_A);
        runBlocking(() -> TenantContext.setCurrentTenantId(TENANT_A));

        ContractRecord savedArtifact = runPromise(() -> artifacts.save(artifact));
        runPromise(() -> projectContext.save(context));
        runPromise(() -> generatedContracts.save(contract));
        runPromise(() -> artifactEvidence.save(evidence));

        when(dataCloudClient.findById(eq(TENANT_A), eq("yappc-artifact-metadata"), eq(savedArtifact.getId().toString())))
            .thenReturn(Promise.of(Optional.of(DataCloudClient.Entity.of(
                savedArtifact.getId().toString(),
                "yappc-artifact-metadata",
                mapper.toEntityData(savedArtifact)))));

        when(dataCloudClient.query(eq(TENANT_A), eq("yappc-artifact-evidence"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of(DataCloudClient.Entity.of(
                evidence.getId().toString(),
                "yappc-artifact-evidence",
                mapper.toEntityData(evidence)))));

        YappcDataCloudRepository<ContractRecord> artifactReader = repository("yappc-artifact-metadata");
        Optional<ContractRecord> retrieved = runPromise(() -> artifactReader.findById(savedArtifact.getId()));
        List<ContractRecord> evidenceResults = runPromise(() -> artifactEvidence.findByField("projectId", "project-1"));

        TenantContext.setCurrentTenantId(TENANT_B);
        runBlocking(() -> TenantContext.setCurrentTenantId(TENANT_B));
        when(dataCloudClient.query(eq(TENANT_B), eq("yappc-artifact-evidence"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of()));
        List<ContractRecord> tenantBResults = runPromise(() -> artifactEvidence.findByField("projectId", "project-1"));

        assertThat(retrieved).isPresent();
        assertThat(retrieved.orElseThrow().values()).containsEntry("workspaceId", "workspace-1");
        assertThat(evidenceResults).hasSize(1);
        assertThat(tenantBResults).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor =
            (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(dataCloudClient, times(4)).save(eq(TENANT_A), anyString(), payloadCaptor.capture());
        assertThat(payloadCaptor.getAllValues())
            .allSatisfy(payload -> assertThat(payload).containsKeys("id", "kind", "values"))
            .anySatisfy(payload -> assertThat(payload).containsEntry("kind", "artifact-metadata"))
            .anySatisfy(payload -> assertThat(payload).containsEntry("kind", "project-workspace-context"))
            .anySatisfy(payload -> assertThat(payload).containsEntry("kind", "generated-contract"))
            .anySatisfy(payload -> assertThat(payload).containsEntry("kind", "artifact-evidence"));
        verify(dataCloudClient).findById(eq(TENANT_A), eq("yappc-artifact-metadata"), eq(savedArtifact.getId().toString()));
        verify(dataCloudClient).query(eq(TENANT_A), eq("yappc-artifact-evidence"), any(DataCloudClient.Query.class));
        verify(dataCloudClient).query(eq(TENANT_B), eq("yappc-artifact-evidence"), any(DataCloudClient.Query.class));
    }

    private YappcDataCloudRepository<ContractRecord> repository(String collection) {
        return new YappcDataCloudRepository<>(
            dataCloudClient,
            mapper,
            collection,
            ContractRecord.class);
    }

    private static ContractRecord record(String kind) {
        return new ContractRecord(UUID.randomUUID(), kind, Map.of());
    }

    record ContractRecord(UUID id, String kind, Map<String, Object> values) implements Identifiable<UUID> {
        @Override
        public UUID getId() {
            return id;
        }

        ContractRecord with(String key, Object value) {
            java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>(values);
            copy.put(key, value);
            return new ContractRecord(id, kind, Map.copyOf(copy));
        }
    }
}
