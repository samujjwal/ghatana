package com.ghatana.products.agentic_event_processor.orchestrator.infrastructure.persistence;

import com.ghatana.orchestrator.store.PipelineCheckpointEntity;
import com.ghatana.orchestrator.store.StepCheckpointEntity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for database index optimization.
 *
 * <p>Verifies that JPA entity annotations declare the correct strategic indexes
 * for pipeline and step checkpoint tables. Uses reflection to inspect
 * {@link Table @Table} annotations — no database connection required.
 *
 * <p><b>Verified Properties</b>:
 * <ul>
 *   <li>Index existence and naming conventions</li>
 *   <li>Column list correctness for query patterns</li>
 *   <li>Unique constraints for idempotency enforcement</li>
 *   <li>Tenant-scoped composite indexes for multi-tenant isolation</li>
 *   <li>Minimum index count for both entities</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Database index annotation verification tests
 * @doc.layer infrastructure
 * @doc.pattern Unit Test
 */
@DisplayName("Database Index Optimization Tests")
class DatabaseIndexOptimizationTest {

    /**
     * Verifies that PipelineCheckpointEntity declares all 7 strategic indexes.
     */
    @Test
    @DisplayName("PipelineCheckpointEntity declares all strategic indexes")
    void shouldDeclareAllPipelineCheckpointIndexes() {
        Index[] indexes = getIndexes(PipelineCheckpointEntity.class);

        assertThat(indexes).isNotNull();
        assertThat(indexes.length).isGreaterThanOrEqualTo(7);

        Map<String, Index> byName = Arrays.stream(indexes)
                .collect(Collectors.toMap(Index::name, idx -> idx));

        assertThat(byName).containsKey("idx_pipeline_checkpoints_tenant_idempotency");
        assertThat(byName).containsKey("idx_pipeline_checkpoints_idempotency");
        assertThat(byName).containsKey("idx_pipeline_checkpoints_tenant_pipeline");
        assertThat(byName).containsKey("idx_pipeline_checkpoints_pipeline_id");
        assertThat(byName).containsKey("idx_pipeline_checkpoints_status");
        assertThat(byName).containsKey("idx_pipeline_checkpoints_created_at");
        assertThat(byName).containsKey("idx_pipeline_checkpoints_updated_at");
    }

    /**
     * Verifies that tenant-scoped queries use composite indexes.
     * The (tenant_id, idempotency_key) and (tenant_id, pipeline_id) indexes
     * ensure multi-tenant isolation without table scans.
     */
    @Test
    @DisplayName("Tenant-scoped composite indexes are declared correctly")
    void shouldDeclareTenantScopedCompositeIndexes() {
        Index[] indexes = getIndexes(PipelineCheckpointEntity.class);
        Map<String, String> columnsByName = Arrays.stream(indexes)
                .collect(Collectors.toMap(Index::name, Index::columnList));

        // tenant + idempotency key — used for duplicate detection
        assertThat(columnsByName.get("idx_pipeline_checkpoints_tenant_idempotency"))
                .isEqualTo("tenant_id, idempotency_key");

        // tenant + pipeline — used for listing executions per pipeline
        assertThat(columnsByName.get("idx_pipeline_checkpoints_tenant_pipeline"))
                .isEqualTo("tenant_id, pipeline_id");
    }

    /**
     * Verifies the unique constraint on (tenant_id, idempotency_key) for
     * exactly-once semantics.
     */
    @Test
    @DisplayName("Idempotency index has unique constraint for exactly-once semantics")
    void shouldHaveUniqueIdempotencyConstraint() {
        Index[] indexes = getIndexes(PipelineCheckpointEntity.class);
        Index idempotencyIdx = Arrays.stream(indexes)
                .filter(idx -> idx.name().equals("idx_pipeline_checkpoints_tenant_idempotency"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Idempotency index not found"));

        assertThat(idempotencyIdx.unique()).isTrue();
    }

    /**
     * Verifies that StepCheckpointEntity declares all 4 required indexes.
     */
    @Test
    @DisplayName("StepCheckpointEntity declares all required indexes")
    void shouldDeclareAllStepCheckpointIndexes() {
        Index[] indexes = getIndexes(StepCheckpointEntity.class);

        assertThat(indexes).isNotNull();
        assertThat(indexes.length).isGreaterThanOrEqualTo(4);

        List<String> indexNames = Arrays.stream(indexes)
                .map(Index::name)
                .toList();

        assertThat(indexNames).contains(
                "idx_step_checkpoints_instance_id",
                "idx_step_checkpoints_step_id",
                "idx_step_checkpoints_status",
                "idx_step_checkpoints_started_at"
        );
    }

    /**
     * Verifies that all indexes follow the naming convention:
     * {@code idx_{table_name}_{column_hint}}.
     */
    @Test
    @DisplayName("All indexes follow naming convention idx_{table}_{column}")
    void indexNamingConventionIsConsistent() {
        for (Class<?> entity : List.of(PipelineCheckpointEntity.class, StepCheckpointEntity.class)) {
            Table table = entity.getAnnotation(Table.class);
            assertThat(table).isNotNull();
            String tableName = table.name();

            for (Index idx : table.indexes()) {
                assertThat(idx.name())
                        .as("Index %s on table %s should start with idx_%s_", idx.name(), tableName, tableName)
                        .startsWith("idx_" + tableName + "_");
            }
        }
    }

    // ---- helper ----
    private static Index[] getIndexes(Class<?> entityClass) {
        Table table = entityClass.getAnnotation(Table.class);
        assertThat(table)
                .as("@Table annotation must be present on " + entityClass.getSimpleName())
                .isNotNull();
        return table.indexes();
    }
}
