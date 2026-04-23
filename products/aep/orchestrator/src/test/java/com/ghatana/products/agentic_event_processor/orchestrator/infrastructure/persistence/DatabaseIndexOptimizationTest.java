package com.ghatana.products.agentic_event_processor.orchestrator.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.orchestrator.store.PipelineCheckpointEntity;
import com.ghatana.orchestrator.store.StepCheckpointEntity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    void shouldDeclareAllPipelineCheckpointIndexes() { // GH-90000
        Index[] indexes = getIndexes(PipelineCheckpointEntity.class); // GH-90000

        assertThat(indexes).isNotNull(); // GH-90000
        assertThat(indexes.length).isGreaterThanOrEqualTo(7); // GH-90000

        Map<String, Index> byName = Arrays.stream(indexes).collect(Collectors.toMap(Index::name, idx -> idx)); // GH-90000

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
     * The (tenant_id, idempotency_key) and (tenant_id, pipeline_id) indexes // GH-90000
     * ensure multi-tenant isolation without table scans.
     */
    @Test
    @DisplayName("Tenant-scoped composite indexes are declared correctly")
    void shouldDeclareTenantScopedCompositeIndexes() { // GH-90000
        Index[] indexes = getIndexes(PipelineCheckpointEntity.class); // GH-90000
        Map<String, String> columnsByName =
                Arrays.stream(indexes).collect(Collectors.toMap(Index::name, Index::columnList)); // GH-90000

        // tenant + idempotency key — used for duplicate detection
        assertThat(columnsByName.get("idx_pipeline_checkpoints_tenant_idempotency"))
                .isEqualTo("tenant_id, idempotency_key");

        // tenant + pipeline — used for listing executions per pipeline
        assertThat(columnsByName.get("idx_pipeline_checkpoints_tenant_pipeline"))
                .isEqualTo("tenant_id, pipeline_id");
    }

    /**
     * Verifies the unique constraint on (tenant_id, idempotency_key) for // GH-90000
     * exactly-once semantics.
     */
    @Test
    @DisplayName("Idempotency index has unique constraint for exactly-once semantics")
    void shouldHaveUniqueIdempotencyConstraint() { // GH-90000
        Index[] indexes = getIndexes(PipelineCheckpointEntity.class); // GH-90000
        Index idempotencyIdx = Arrays.stream(indexes) // GH-90000
                .filter(idx -> idx.name().equals("idx_pipeline_checkpoints_tenant_idempotency"))
                .findFirst() // GH-90000
                .orElseThrow(() -> new AssertionError("Idempotency index not found"));

        assertThat(idempotencyIdx.unique()).isTrue(); // GH-90000
    }

    /**
     * Verifies that StepCheckpointEntity declares all 4 required indexes.
     */
    @Test
    @DisplayName("StepCheckpointEntity declares all required indexes")
    void shouldDeclareAllStepCheckpointIndexes() { // GH-90000
        Index[] indexes = getIndexes(StepCheckpointEntity.class); // GH-90000

        assertThat(indexes).isNotNull(); // GH-90000
        assertThat(indexes.length).isGreaterThanOrEqualTo(4); // GH-90000

        List<String> indexNames = Arrays.stream(indexes).map(Index::name).toList(); // GH-90000

        assertThat(indexNames) // GH-90000
                .contains( // GH-90000
                        "idx_step_checkpoints_instance_id",
                        "idx_step_checkpoints_step_id",
                        "idx_step_checkpoints_status",
                        "idx_step_checkpoints_started_at");
    }

    /**
     * Verifies that all indexes follow the naming convention:
     * {@code idx_{table_name}_{column_hint}}.
     */
    @Test
    @DisplayName("All indexes follow naming convention idx_{table}_{column}")
    void indexNamingConventionIsConsistent() { // GH-90000
        for (Class<?> entity : List.of(PipelineCheckpointEntity.class, StepCheckpointEntity.class)) { // GH-90000
            Table table = entity.getAnnotation(Table.class); // GH-90000
            assertThat(table).isNotNull(); // GH-90000
            String tableName = table.name(); // GH-90000

            for (Index idx : table.indexes()) { // GH-90000
                assertThat(idx.name()) // GH-90000
                        .as("Index %s on table %s should start with idx_%s_", idx.name(), tableName, tableName) // GH-90000
                        .startsWith("idx_" + tableName + "_"); // GH-90000
            }
        }
    }

    // ---- helper ----
    private static Index[] getIndexes(Class<?> entityClass) { // GH-90000
        Table table = entityClass.getAnnotation(Table.class); // GH-90000
        assertThat(table) // GH-90000
                .as("@Table annotation must be present on " + entityClass.getSimpleName()) // GH-90000
                .isNotNull(); // GH-90000
        return table.indexes(); // GH-90000
    }
}
