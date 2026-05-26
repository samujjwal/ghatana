package com.ghatana.yappc.services.shape;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.shape.ShapeSpec;
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
 * @doc.purpose Verifies Data Cloud-backed shape artifact persistence
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataCloudShapeRepository")
class DataCloudShapeRepositoryTest extends EventloopTestBase {

    @Mock private DataCloudClient dataCloudClient;

    @Test
    @DisplayName("saveShape stores next version with intent evidence lineage")
    void saveShape_storesLineage() {
        ShapeSpec shape = shape();
        when(dataCloudClient.query(eq("tenant-1"), eq(DataCloudShapeRepository.COLLECTION), any()))
                .thenReturn(Promise.of(List.of(DataCloudClient.Entity.of(
                        "project-1:shape-1:v1",
                        DataCloudShapeRepository.COLLECTION,
                        document(shape, 1)))));
        when(dataCloudClient.save(eq("tenant-1"), eq(DataCloudShapeRepository.COLLECTION), any()))
                .thenReturn(Promise.of(DataCloudClient.Entity.of(
                        "project-1:shape-1:v2",
                        DataCloudShapeRepository.COLLECTION,
                        Map.of())));

        DataCloudShapeRepository repository = new DataCloudShapeRepository(dataCloudClient);
        ShapeVersionRecord record = runPromise(() -> repository.saveShape(shape, context()));

        assertThat(record.version()).isEqualTo(2);
        assertThat(record.intentEvidenceIds()).isEqualTo(List.of("evidence-intent-1"));

        ArgumentCaptor<Map<String, Object>> documentCaptor = dataCaptor();
        verify(dataCloudClient).save(eq("tenant-1"), eq(DataCloudShapeRepository.COLLECTION), documentCaptor.capture());
        Map<String, Object> saved = documentCaptor.getValue();
        assertThat(saved)
                .containsEntry("id", "project-1:shape-1:v2")
                .containsEntry("shapeId", "shape-1")
                .containsEntry("sourceIntentId", "intent-1");
        assertThat(saved.get("intentEvidenceIds")).isEqualTo(List.of("evidence-intent-1"));
        assertThat(saved.get("shape")).isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("saveShape rejects default tenant before writing")
    void saveShape_rejectsDefaultTenant() {
        DataCloudShapeRepository repository = new DataCloudShapeRepository(dataCloudClient);

        assertThatThrownBy(() -> runPromise(() -> repository.saveShape(
                shape(),
                new ShapePersistenceContext(
                        "default-tenant",
                        "workspace-1",
                        "project-1",
                        "user-1",
                        "intent-1",
                        List.of(),
                        Map.of()))))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("default-tenant");
    }

    private static ShapePersistenceContext context() {
        return new ShapePersistenceContext(
                "tenant-1",
                "workspace-1",
                "project-1",
                "user-1",
                "intent-1",
                List.of("evidence-intent-1"),
                Map.of("source", "shape.derive"));
    }

    private static ShapeSpec shape() {
        return ShapeSpec.builder()
                .id("shape-1")
                .intentRef("intent-1")
                .tenantId("tenant-1")
                .metadata(Map.of(
                        "workspaceId", "workspace-1",
                        "projectId", "project-1",
                        "intentEvidenceIds", "evidence-intent-1"))
                .createdAt(Instant.parse("2026-05-26T12:00:00Z"))
                .build();
    }

    private static Map<String, Object> document(ShapeSpec shape, int version) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", "project-1:" + shape.id() + ":v" + version);
        document.put("recordId", "project-1:" + shape.id() + ":v" + version);
        document.put("tenantId", shape.tenantId());
        document.put("workspaceId", "workspace-1");
        document.put("projectId", "project-1");
        document.put("shapeId", shape.id());
        document.put("version", version);
        document.put("sourceIntentId", "intent-1");
        document.put("intentEvidenceIds", List.of("evidence-intent-1"));
        document.put("createdBy", "user-1");
        document.put("createdAt", "2026-05-26T12:00:00Z");
        document.put("metadata", Map.of());
        document.put("shape", Map.of(
                "id", shape.id(),
                "intentRef", shape.intentRef(),
                "workflows", List.of(),
                "integrations", List.of(),
                "metadata", shape.metadata(),
                "createdAt", "2026-05-26T12:00:00Z",
                "tenantId", shape.tenantId()));
        return document;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Map<String, Object>> dataCaptor() {
        return ArgumentCaptor.forClass((Class) Map.class);
    }
}
