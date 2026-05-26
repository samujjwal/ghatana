package com.ghatana.yappc.services.intent;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.intent.IntentSpec;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies Data Cloud-backed versioned intent persistence
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataCloudIntentRepository")
class DataCloudIntentRepositoryTest extends EventloopTestBase {

    @Mock private DataCloudClient dataCloudClient;

    @Test
    @DisplayName("saveVersion stores next version with tenant workspace project scope")
    void saveVersion_persistsNextVersion() {
        IntentSpec spec = spec("intent-1", "tenant-1");
        Map<String, Object> previous = document("project-1:intent-1:v1", spec, 1);
        when(dataCloudClient.query(eq("tenant-1"), eq(DataCloudIntentRepository.COLLECTION), any()))
                .thenReturn(Promise.of(List.of(DataCloudClient.Entity.of(
                        "project-1:intent-1:v1",
                        DataCloudIntentRepository.COLLECTION,
                        previous))));
        when(dataCloudClient.save(eq("tenant-1"), eq(DataCloudIntentRepository.COLLECTION), any()))
                .thenReturn(Promise.of(DataCloudClient.Entity.of(
                        "project-1:intent-1:v2",
                        DataCloudIntentRepository.COLLECTION,
                        Map.of())));

        DataCloudIntentRepository repository = new DataCloudIntentRepository(dataCloudClient);
        IntentVersionRecord record = runPromise(() -> repository.saveVersion(
                spec,
                new IntentPersistenceContext(
                        "tenant-1",
                        "workspace-1",
                        "project-1",
                        "user-1",
                        "audit-1",
                        List.of("evidence-1"),
                        Map.of("source", "intent.capture"))));

        assertThat(record.version()).isEqualTo(2);
        assertThat(record.workspaceId()).isEqualTo("workspace-1");
        assertThat(record.projectId()).isEqualTo("project-1");

        ArgumentCaptor<Map<String, Object>> documentCaptor = dataCaptor();
        verify(dataCloudClient).save(eq("tenant-1"), eq(DataCloudIntentRepository.COLLECTION), documentCaptor.capture());
        Map<String, Object> saved = documentCaptor.getValue();
        assertThat(saved)
                .containsEntry("id", "project-1:intent-1:v2")
                .containsEntry("intentId", "intent-1")
                .containsEntry("version", 2)
                .containsEntry("createdBy", "user-1")
                .containsEntry("auditEventId", "audit-1");
        assertThat(saved.get("evidenceIds")).isEqualTo(List.of("evidence-1"));
        assertThat(saved.get("spec")).isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("findLatest returns newest Data Cloud intent version")
    void findLatest_returnsNewestVersion() {
        IntentSpec spec = spec("intent-1", "tenant-1");
        when(dataCloudClient.query(eq("tenant-1"), eq(DataCloudIntentRepository.COLLECTION), any()))
                .thenReturn(Promise.of(List.of(
                        DataCloudClient.Entity.of(
                                "project-1:intent-1:v1",
                                DataCloudIntentRepository.COLLECTION,
                                document("project-1:intent-1:v1", spec, 1)),
                        DataCloudClient.Entity.of(
                                "project-1:intent-1:v3",
                                DataCloudIntentRepository.COLLECTION,
                                document("project-1:intent-1:v3", spec, 3)))));

        DataCloudIntentRepository repository = new DataCloudIntentRepository(dataCloudClient);

        assertThat(runPromise(() -> repository.findLatest(
                "tenant-1",
                "workspace-1",
                "project-1",
                "intent-1")))
                .isPresent()
                .get()
                .extracting(IntentVersionRecord::version)
                .isEqualTo(3);
    }

    @Test
    @DisplayName("saveVersion rejects default tenant before writing")
    void saveVersion_rejectsDefaultTenant() {
        DataCloudIntentRepository repository = new DataCloudIntentRepository(dataCloudClient);

        assertThatThrownBy(() -> runPromise(() -> repository.saveVersion(
                spec("intent-1", "default-tenant"),
                new IntentPersistenceContext(
                        "default-tenant",
                        "workspace-1",
                        "project-1",
                        "user-1",
                        "audit-1",
                        List.of(),
                        Map.of()))))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("default-tenant");
    }

    private static IntentSpec spec(String intentId, String tenantId) {
        return IntentSpec.builder()
                .id(intentId)
                .productName("Task Manager")
                .description("Team collaboration tool")
                .tenantId(tenantId)
                .createdAt(Instant.parse("2026-05-26T12:00:00Z"))
                .build();
    }

    private static Map<String, Object> document(String recordId, IntentSpec spec, int version) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", recordId);
        document.put("recordId", recordId);
        document.put("tenantId", spec.tenantId());
        document.put("workspaceId", "workspace-1");
        document.put("projectId", "project-1");
        document.put("intentId", spec.id());
        document.put("version", version);
        document.put("productName", spec.productName());
        document.put("createdBy", "user-1");
        document.put("createdAt", "2026-05-26T12:00:00Z");
        document.put("auditEventId", "audit-1");
        document.put("evidenceIds", List.of());
        document.put("metadata", Map.of());
        document.put("spec", Map.of(
                        "id", spec.id(),
                        "productName", spec.productName(),
                        "description", spec.description(),
                        "goals", List.of(),
                        "personas", List.of(),
                        "constraints", List.of(),
                        "metadata", Map.of(),
                        "createdAt", "2026-05-26T12:00:00Z",
                        "tenantId", spec.tenantId()));
        return document;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Map<String, Object>> dataCaptor() {
        return ArgumentCaptor.forClass((Class) Map.class);
    }
}
