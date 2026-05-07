package com.ghatana.yappc.domain.pageartifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DbPageArtifactRepository Integration Tests")
class DbPageArtifactRepositoryIT extends EventloopTestBase {

    private DbPageArtifactRepository repository;
    private DataSource dataSource;

    @Override
    protected boolean breakOnFatalError() {
        return false;
    }

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:page_artifacts_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;
        createSchema(ds);
        repository = new DbPageArtifactRepository(ds, new ObjectMapper().findAndRegisterModules(), Runnable::run);
    }

    @Test
    @DisplayName("save load update and delete preserve version history and typed governance records")
    void saveLoadUpdateDelete_roundTripsAndArchivesVersions() throws Exception {
        PageArtifactDocument created = sampleDocument("tenant-a", "artifact-1", "doc-1", "generated");

        PageArtifactDocument inserted = runPromise(() ->
                repository.save("tenant-a", "ws-1", "proj-1", created));

        assertThat(inserted.documentId()).isEqualTo("doc-1");

        PageArtifactDocument loaded = runPromise(() ->
                repository.load("tenant-a", "ws-1", "proj-1", "artifact-1"));

        assertThat(loaded).isNotNull();
        assertThat(loaded.aiChangeRecords()).hasSize(1);
        assertThat(loaded.aiChangeRecords().get(0).lineage().reason()).isEqualTo("Imported from generator");

        PageArtifactDocument updateRequest = new PageArtifactDocument(
                loaded.artifactId(),
                loaded.documentId(),
                "Landing Page v2",
                loaded.createdBy(),
                loaded.createdAt(),
                loaded.updatedAt(),
                loaded.syncStatus(),
                loaded.trustLevel(),
                loaded.dataClassification(),
                loaded.builderDocument(),
                loaded.validationSummary(),
                loaded.aiChangeRecords(),
                loaded.source(),
                loaded.residualIslandCount(),
                loaded.roundTripFidelity()
        );

        PageArtifactDocument updated = runPromise(() ->
                repository.save("tenant-a", "ws-1", "proj-1", updateRequest));

        assertThat(updated.documentId()).isEqualTo("doc-1@rev-2");

        PageArtifactDocument reloaded = runPromise(() ->
                repository.load("tenant-a", "ws-1", "proj-1", "artifact-1"));
        assertThat(reloaded.documentId()).isEqualTo("doc-1@rev-2");

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT COUNT(*) FROM page_artifact_versions")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }

        runPromise(() -> repository.delete("tenant-a", "ws-1", "proj-1", "artifact-1"));
        assertThat(runPromise(() -> repository.load("tenant-a", "ws-1", "proj-1", "artifact-1"))).isNull();
    }

    @Test
    @DisplayName("stale document ids conflict without overwriting the latest persisted version")
    void save_conflictPreservesLatestVersion() throws Exception {
        PageArtifactDocument created = sampleDocument("tenant-a", "artifact-2", "doc-7", "manual");
        runPromise(() -> repository.save("tenant-a", "ws-1", "proj-1", created));

        PageArtifactDocument firstUpdate = runPromise(() -> repository.save(
                "tenant-a",
                "ws-1",
                "proj-1",
                created
        ));

        assertThatThrownBy(() -> runPromise(() -> repository.save(
                "tenant-a",
                "ws-1",
                "proj-1",
                created
        )))
                .isInstanceOf(PageArtifactConflictException.class)
                .hasMessageContaining(firstUpdate.documentId());

        PageArtifactDocument latest = runPromise(() ->
                repository.load("tenant-a", "ws-1", "proj-1", "artifact-2"));
        assertThat(latest.documentId()).isEqualTo(firstUpdate.documentId());
    }

    @Test
    @DisplayName("tenant workspace and project scope isolate artifacts with the same artifact id")
    void load_isolatedByTenantWorkspaceAndProject() throws Exception {
        PageArtifactDocument tenantA = sampleDocument("tenant-a", "artifact-shared", "doc-a", "manual");
        PageArtifactDocument tenantB = sampleDocument("tenant-b", "artifact-shared", "doc-b", "manual");

        runPromise(() -> repository.save("tenant-a", "ws-1", "proj-1", tenantA));
        runPromise(() -> repository.save("tenant-b", "ws-1", "proj-1", tenantB));

        assertThat(runPromise(() ->
                repository.load("tenant-a", "ws-1", "proj-1", "artifact-shared")).documentId())
                .isEqualTo("doc-a");
        assertThat(runPromise(() ->
                repository.load("tenant-b", "ws-1", "proj-1", "artifact-shared")).documentId())
                .isEqualTo("doc-b");
    }

    private static PageArtifactDocument sampleDocument(String tenantId, String artifactId, String documentId, String source) {
        return new PageArtifactDocument(
                artifactId,
                documentId,
                "Landing Page",
                tenantId + "-user",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                "SYNCED",
                "TRUSTED",
                "INTERNAL",
                Map.of(
                        "rootNodes", List.of("root"),
                        "nodes", Map.of(
                                "root", Map.of(
                                        "id", "root",
                                        "contractName", "Box",
                                        "props", Map.of("padding", "md"),
                                        "slots", Map.of("default", List.of())
                                )
                        )
                ),
                new PageArtifactDocument.ValidationSummary(true, 0, 0),
                List.of(new PageArtifactDocument.GovernanceRecord(
                        artifactId,
                        documentId,
                        new PageArtifactDocument.GovernanceLineage(
                                "action-1",
                                "import",
                                "Imported from generator",
                                0.9,
                                true,
                                "approved",
                                List.of("root"),
                                Instant.parse("2026-01-01T00:00:00Z").toString(),
                                List.of("source:" + source)
                        )
                )),
                source,
                0,
                0.95
        );
    }

    private static void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE page_artifacts (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        tenant_id VARCHAR(255) NOT NULL,
                        workspace_id VARCHAR(255) NOT NULL,
                        project_id VARCHAR(255) NOT NULL,
                        artifact_id VARCHAR(255) NOT NULL,
                        document_id VARCHAR(255) NOT NULL,
                        name VARCHAR(500) NOT NULL,
                        created_by VARCHAR(255) NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        sync_status VARCHAR(50) NOT NULL,
                        trust_level VARCHAR(50) NOT NULL,
                        data_classification VARCHAR(50) NOT NULL,
                        source VARCHAR(255),
                        builder_document CLOB NOT NULL,
                        validation_summary CLOB,
                        ai_change_records CLOB,
                        operation_log CLOB,
                        residual_island_count INTEGER NOT NULL,
                        round_trip_fidelity DOUBLE PRECISION NOT NULL,
                        CONSTRAINT uq_page_artifact UNIQUE (tenant_id, workspace_id, project_id, artifact_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE page_artifact_versions (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        page_artifact_id BIGINT NOT NULL,
                        tenant_id VARCHAR(255) NOT NULL,
                        workspace_id VARCHAR(255) NOT NULL,
                        project_id VARCHAR(255) NOT NULL,
                        artifact_id VARCHAR(255) NOT NULL,
                        document_id VARCHAR(255) NOT NULL,
                        name VARCHAR(500) NOT NULL,
                        created_by VARCHAR(255) NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        sync_status VARCHAR(50) NOT NULL,
                        trust_level VARCHAR(50) NOT NULL,
                        data_classification VARCHAR(50) NOT NULL,
                        source VARCHAR(255),
                        builder_document CLOB NOT NULL,
                        validation_summary CLOB,
                        ai_change_records CLOB,
                        operation_log CLOB,
                        residual_island_count INTEGER NOT NULL,
                        round_trip_fidelity DOUBLE PRECISION NOT NULL,
                        version_created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        version_reason VARCHAR(500)
                    )
                    """);
        }
    }
}
