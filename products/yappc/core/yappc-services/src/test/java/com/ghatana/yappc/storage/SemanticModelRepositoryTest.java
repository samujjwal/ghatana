package com.ghatana.yappc.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.artifact.SemanticModelDto;
import com.ghatana.yappc.domain.artifact.SourceLocationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type test
 * @doc.purpose Verifies semantic model persistence contract validation and binding behavior
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticModelRepository Tests")
class SemanticModelRepositoryTest extends EventloopTestBase {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement schemaStatement;

    @Mock
    private PreparedStatement insertStatement;

    @Mock
    private ResultSet schemaResultSet;

    private SemanticModelRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new SemanticModelRepository(dataSource, new ObjectMapper(), Runnable::run);

        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("information_schema.columns")) {
                return schemaStatement;
            }
            return insertStatement;
        });
        lenient().when(schemaStatement.executeQuery()).thenReturn(schemaResultSet);
        lenient().when(insertStatement.executeUpdate()).thenReturn(1);
    }

    @Test
    @DisplayName("saves semantic model when schema contract is satisfied")
    void savesSemanticModelWhenSchemaContractSatisfied() throws Exception {
        stubSchemaColumns(REQUIRED_COLUMNS);

        SemanticModelDto model = createModel();

        SemanticModelDto saved = runPromise(() -> repository.saveModel(model));

        assertThat(saved.id()).isEqualTo(model.id());
        verify(insertStatement).executeUpdate();
    }

    @Test
    @DisplayName("fails fast when semantic_models schema is missing required columns")
    void failsFastWhenSchemaMissingRequiredColumns() throws Exception {
        stubSchemaColumns(List.of("id", "element_id", "element_type", "name"));

        assertThatThrownBy(() -> runPromise(() -> repository.saveModel(createModel())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("semantic_models table schema mismatch")
            .hasMessageContaining("Missing required columns");
    }

    private void stubSchemaColumns(List<String> columns) throws Exception {
        org.mockito.stubbing.OngoingStubbing<Boolean> nextStubbing = when(schemaResultSet.next());
        for (int i = 0; i < columns.size(); i++) {
            nextStubbing = nextStubbing.thenReturn(true);
        }
        nextStubbing.thenReturn(false);
        when(schemaResultSet.getString("column_name")).thenReturn(columns.get(0), columns.subList(1, columns.size()).toArray(new String[0]));
    }

    private SemanticModelDto createModel() {
        return SemanticModelDto.builder()
            .id("model-1")
            .elementId("element-1")
            .elementType("component")
            .name("App")
            .qualifiedName("App")
            .filePath("src/App.tsx")
            .sourceLocation(new SourceLocationDto("src/App.tsx", 1, 0, 10, 1))
            .properties(Map.of("framework", "react"))
            .dependencies(List.of("dep-1"))
            .dependents(List.of())
            .confidence(0.9)
            .reviewRequired(false)
            .reviewReason(null)
            .securityFlags(List.of())
            .privacyFlags(List.of())
            .graphNodeIds(List.of("node-1"))
            .residualIslandIds(List.of())
            .sourceRef("src/App.tsx#component:App")
            .symbolRef("src/App.tsx#component:App")
            .extractorId("ts-extractor")
            .extractorVersion("1.0.0")
            .modelVersionId("v1")
            .provenance(SemanticModelDto.Provenance.EXACT)
            .extractedAt(Instant.parse("2026-05-17T00:00:00Z"))
            .snapshotId("snap-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .projectId("project-1")
            .build();
    }

    private static final List<String> REQUIRED_COLUMNS = List.of(
        "id", "element_id", "element_type", "name", "qualified_name", "file_path",
        "source_location_json", "properties_json", "dependencies_json", "dependents_json",
        "confidence", "review_required", "review_reason", "security_flags", "privacy_flags",
        "graph_node_ids", "residual_island_ids", "source_ref", "symbol_ref",
        "extractor_id", "extractor_version", "model_version_id", "synthetic_reason",
        "provenance", "extracted_at", "snapshot_id", "tenant_id", "workspace_id", "project_id"
    );
}
