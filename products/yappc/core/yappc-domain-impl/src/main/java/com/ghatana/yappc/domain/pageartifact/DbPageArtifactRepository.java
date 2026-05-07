/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.domain.pageartifact;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.core.database.jdbc.JdbcTemplate;
import com.ghatana.platform.observability.util.BlockingExecutors;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Database-backed implementation of PageArtifactRepository.
 * <p>
 * Uses JDBC for durable persistence with tenant/workspace/project scoping,
 * optimistic concurrency control via documentId, and historical version tracking.
 *
 * @doc.type class
 * @doc.purpose Database-backed implementation of page artifact repository
 * @doc.layer product
 * @doc.pattern Repository Implementation
 */
public final class DbPageArtifactRepository implements PageArtifactRepository, PageArtifactAtomicMutationRepository {

    private static final Logger LOG = LoggerFactory.getLogger(DbPageArtifactRepository.class);
    private static final Pattern DOCUMENT_REVISION_PATTERN =
            Pattern.compile("^(.*?)(?:@rev-(\\d+))?$");

    private static final String INSERT_ARTIFACT = """
            INSERT INTO page_artifacts (
                tenant_id, workspace_id, project_id, artifact_id, document_id,
                name, created_by, created_at, updated_at,
                sync_status, trust_level, data_classification, source,
                builder_document, validation_summary, ai_change_records, operation_log,
                residual_island_count, round_trip_fidelity
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_ARTIFACT = """
            UPDATE page_artifacts
            SET document_id = ?, name = ?, updated_at = ?,
                sync_status = ?, trust_level = ?, data_classification = ?, source = ?,
                builder_document = ?, validation_summary = ?, ai_change_records = ?, operation_log = ?,
                residual_island_count = ?, round_trip_fidelity = ?
            WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND artifact_id = ?
              AND document_id = ?
            """;

    private static final String SELECT_ARTIFACT = """
            SELECT artifact_id, document_id, name, created_by, created_at, updated_at,
                   sync_status, trust_level, data_classification, source,
                   builder_document, validation_summary, ai_change_records, operation_log,
                   residual_island_count, round_trip_fidelity
            FROM page_artifacts
            WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND artifact_id = ?
            """;

    private static final String DELETE_ARTIFACT = """
            DELETE FROM page_artifacts
            WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND artifact_id = ?
            """;

    private static final String INSERT_VERSION = """
            INSERT INTO page_artifact_versions (
                page_artifact_id, tenant_id, workspace_id, project_id, artifact_id, document_id,
                name, created_by, created_at, updated_at,
                sync_status, trust_level, data_classification, source,
                builder_document, validation_summary, ai_change_records, operation_log,
                residual_island_count, round_trip_fidelity, version_created_at, version_reason
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_AUDIT_EVENT = """
            INSERT INTO page_artifact_audit_events (
                action,
                tenant_id,
                workspace_id,
                project_id,
                artifact_id,
                actor,
                summary,
                occurred_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Executor blockingExecutor;

    /**
     * Creates a new DbPageArtifactRepository.
     *
     * @param dataSource The data source
     * @param objectMapper JSON object mapper for JSONB serialization
     */
    public DbPageArtifactRepository(
            @NotNull DataSource dataSource,
            @NotNull ObjectMapper objectMapper
    ) {
        this(dataSource, objectMapper, BlockingExecutors.blockingExecutor());
    }

    public DbPageArtifactRepository(
            @NotNull DataSource dataSource,
            @NotNull ObjectMapper objectMapper,
            @NotNull Executor blockingExecutor
    ) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
        this.blockingExecutor = blockingExecutor;
        LOG.debug("DbPageArtifactRepository created");
    }

    @Override
    public Promise<PageArtifactDocument> save(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull PageArtifactDocument document
    ) {
        LOG.debug("Saving page artifact: tenant={}, workspace={}, project={}, artifactId={}, documentId={}",
                tenantId, workspaceId, projectId, document.artifactId(), document.documentId());

        return Promise.ofBlocking(blockingExecutor, () -> jdbc.inTransaction(
            (com.ghatana.core.database.transaction.TransactionCallback<JdbcTemplate, PageArtifactDocument>)
                tx -> saveInTransaction(tx, tenantId, workspaceId, projectId, document)
        ));
        }

        @Override
        public Promise<PageArtifactDocument> saveWithAudit(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull PageArtifactDocument document,
            @NotNull String action,
            @NotNull String actor,
            @NotNull String summary
        ) {
        return Promise.ofBlocking(blockingExecutor, () -> jdbc.inTransaction(
            (com.ghatana.core.database.transaction.TransactionCallback<JdbcTemplate, PageArtifactDocument>) tx -> {
                PageArtifactDocument persistedDocument = saveInTransaction(tx, tenantId, workspaceId, projectId, document);
                tx.update(
                    INSERT_AUDIT_EVENT,
                    action,
                    tenantId,
                    workspaceId,
                    projectId,
                    document.artifactId(),
                    actor,
                    summary,
                    Instant.now()
                );
                return persistedDocument;
            }
        ));
    }

    @Override
    public Promise<PageArtifactDocument> load(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String artifactId
    ) {
        LOG.debug("Loading page artifact: tenant={}, workspace={}, project={}, artifactId={}",
                tenantId, workspaceId, projectId, artifactId);

        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<PageArtifactDocument> document = jdbc.queryForObject(
                    SELECT_ARTIFACT,
                    this::mapRow,
                    tenantId,
                    workspaceId,
                    projectId,
                    artifactId
            );

            if (document.isEmpty()) {
                LOG.debug("Page artifact not found: {}", artifactId);
                return null;
            }

            LOG.debug("Successfully loaded page artifact: {}", artifactId);
            return document.get();
        });
    }

    @Override
    public Promise<Void> delete(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String artifactId
    ) {
        LOG.debug("Deleting page artifact: tenant={}, workspace={}, project={}, artifactId={}",
                tenantId, workspaceId, projectId, artifactId);

        return Promise.ofBlocking(blockingExecutor, () -> {
            jdbc.update(
                    DELETE_ARTIFACT,
                    tenantId,
                    workspaceId,
                    projectId,
                    artifactId
            );
            LOG.debug("Successfully deleted page artifact: {}", artifactId);
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private PageArtifactDocument mapRow(ResultSet rs) throws SQLException {
        try {
            return new PageArtifactDocument(
                    rs.getString("artifact_id"),
                    rs.getString("document_id"),
                    rs.getString("name"),
                    rs.getString("created_by"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(),
                    rs.getString("sync_status"),
                    rs.getString("trust_level"),
                    rs.getString("data_classification"),
                    fromJson(rs.getString("builder_document")),
                    fromJsonValidationSummary(rs.getString("validation_summary")),
                    fromJsonGovernanceRecords(rs.getString("ai_change_records")),
                    rs.getString("source"),
                    rs.getInt("residual_island_count"),
                    rs.getDouble("round_trip_fidelity"),
                    fromJsonOperationRecords(rs.getString("operation_log"))
            );
        } catch (Exception e) {
            LOG.error("Failed to map ResultSet to PageArtifactDocument", e);
            throw new SQLException("Failed to map ResultSet to PageArtifactDocument", e);
        }
    }

    private long getArtifactId(JdbcTemplate tx, String tenantId, String workspaceId, String projectId, String artifactId) {
        return tx.queryForObject(
                "SELECT id FROM page_artifacts WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND artifact_id = ?",
                rs -> rs.getLong("id"),
                tenantId,
                workspaceId,
                projectId,
                artifactId
        ).orElseThrow(() -> new IllegalStateException("Artifact not found: " + artifactId));
    }

        private PageArtifactDocument saveInTransaction(
            @NotNull JdbcTemplate tx,
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull PageArtifactDocument document
        ) {
        Optional<PageArtifactDocument> existing = tx.queryForObject(
            SELECT_ARTIFACT,
            this::mapRow,
            tenantId,
            workspaceId,
            projectId,
            document.artifactId()
        );

        if (existing.isEmpty()) {
            tx.update(
                INSERT_ARTIFACT,
                tenantId,
                workspaceId,
                projectId,
                document.artifactId(),
                document.documentId(),
                document.name(),
                document.createdBy(),
                document.createdAt(),
                document.updatedAt(),
                document.syncStatus(),
                document.trustLevel(),
                document.dataClassification(),
                document.source(),
                toJson(document.builderDocument()),
                toJson(document.validationSummary()),
                toJson(document.aiChangeRecords()),
                toJson(document.operationLog()),
                document.residualIslandCount(),
                document.roundTripFidelity()
            );
            LOG.debug("Inserted new page artifact: {}", document.artifactId());
            return document;
        }

        PageArtifactDocument existingDoc = existing.get();
        if (!existingDoc.documentId().equals(document.documentId())) {
            LOG.warn("Conflict detected for artifact {}: expected documentId={}, got documentId={}",
                document.artifactId(), existingDoc.documentId(), document.documentId());
            throw new PageArtifactConflictException(
                document.artifactId(),
                existingDoc.documentId()
            );
        }

        PageArtifactDocument persistedDocument = new PageArtifactDocument(
            document.artifactId(),
            nextDocumentId(existingDoc.documentId()),
            document.name(),
            existingDoc.createdBy(),
            existingDoc.createdAt(),
            Instant.now(),
            document.syncStatus(),
            document.trustLevel(),
            document.dataClassification(),
            document.builderDocument(),
            document.validationSummary(),
            document.aiChangeRecords(),
            document.source(),
            document.residualIslandCount(),
            document.roundTripFidelity(),
            document.operationLog()
        );

        long artifactId = getArtifactId(tx, tenantId, workspaceId, projectId, document.artifactId());
        archiveVersion(
            tx,
            artifactId,
            tenantId,
            workspaceId,
            projectId,
            existingDoc,
            "Version before update to " + persistedDocument.documentId()
        );

        int updated = tx.update(
            UPDATE_ARTIFACT,
            persistedDocument.documentId(),
            persistedDocument.name(),
            persistedDocument.updatedAt(),
            persistedDocument.syncStatus(),
            persistedDocument.trustLevel(),
            persistedDocument.dataClassification(),
            persistedDocument.source(),
            toJson(persistedDocument.builderDocument()),
            toJson(persistedDocument.validationSummary()),
            toJson(persistedDocument.aiChangeRecords()),
            toJson(persistedDocument.operationLog()),
            persistedDocument.residualIslandCount(),
            persistedDocument.roundTripFidelity(),
            tenantId,
            workspaceId,
            projectId,
            persistedDocument.artifactId(),
            existingDoc.documentId()
        );

        if (updated == 0) {
            LOG.warn("No rows updated for artifact {}, possible concurrent modification",
                document.artifactId());
            throw new PageArtifactConflictException(
                document.artifactId(),
                existingDoc.documentId()
            );
        }

        LOG.debug("Updated page artifact: {}", document.artifactId());
        return persistedDocument;
        }

    private void archiveVersion(
            JdbcTemplate tx,
            long artifactId,
            String tenantId,
            String workspaceId,
            String projectId,
            PageArtifactDocument document,
            String reason
    ) {
        tx.update(
                INSERT_VERSION,
                artifactId,
                tenantId,
                workspaceId,
                projectId,
                document.artifactId(),
                document.documentId(),
                document.name(),
                document.createdBy(),
                document.createdAt(),
                document.updatedAt(),
                document.syncStatus(),
                document.trustLevel(),
                document.dataClassification(),
                document.source(),
                toJson(document.builderDocument()),
                toJson(document.validationSummary()),
                toJson(document.aiChangeRecords()),
                toJson(document.operationLog()),
                document.residualIslandCount(),
                document.roundTripFidelity(),
                Instant.now(),
                reason
        );
        LOG.debug("Archived version for artifact {}: documentId={}", document.artifactId(), document.documentId());
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> fromJson(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return java.util.Map.of();
        }
        return objectMapper.readValue(json, new TypeReference<java.util.Map<String, Object>>() { });
    }

    private String toJson(Object obj) {
        try {
            if (obj == null) {
                return null;
            }
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            LOG.error("Failed to serialize object to JSON", e);
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
    }

    private PageArtifactDocument.ValidationSummary fromJsonValidationSummary(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return null;
        }
        return objectMapper.readValue(json, PageArtifactDocument.ValidationSummary.class);
    }

    @SuppressWarnings("unchecked")
    private java.util.List<PageArtifactDocument.GovernanceRecord> fromJsonGovernanceRecords(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return java.util.List.of();
        }
        return objectMapper.readValue(json, new TypeReference<java.util.List<PageArtifactDocument.GovernanceRecord>>() { });
    }

    private java.util.List<PageArtifactDocument.OperationRecord> fromJsonOperationRecords(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return java.util.List.of();
        }
        return objectMapper.readValue(json, new TypeReference<java.util.List<PageArtifactDocument.OperationRecord>>() { });
    }

    private String nextDocumentId(String currentDocumentId) {
        Matcher matcher = DOCUMENT_REVISION_PATTERN.matcher(currentDocumentId);
        if (!matcher.matches()) {
            return currentDocumentId + "@rev-2";
        }

        String base = matcher.group(1);
        String revision = matcher.group(2);
        long nextRevision = revision == null ? 2L : Long.parseLong(revision) + 1L;
        return base + "@rev-" + nextRevision;
    }
}
