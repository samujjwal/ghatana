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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.core.database.jdbc.JdbcTemplate;
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
import java.util.concurrent.Executors;

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
public final class DbPageArtifactRepository implements PageArtifactRepository {

    private static final Logger LOG = LoggerFactory.getLogger(DbPageArtifactRepository.class);
    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();

    private static final String INSERT_ARTIFACT = """
            INSERT INTO page_artifacts (
                tenant_id, workspace_id, project_id, artifact_id, document_id,
                name, created_by, created_at, updated_at,
                sync_status, trust_level, data_classification, source,
                builder_document, validation_summary, ai_change_records,
                residual_island_count, round_trip_fidelity
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_ARTIFACT = """
            UPDATE page_artifacts
            SET document_id = ?, name = ?, updated_at = ?,
                sync_status = ?, trust_level = ?, data_classification = ?, source = ?,
                builder_document = ?, validation_summary = ?, ai_change_records = ?,
                residual_island_count = ?, round_trip_fidelity = ?
            WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND artifact_id = ?
              AND document_id = ?
            """;

    private static final String SELECT_ARTIFACT = """
            SELECT artifact_id, document_id, name, created_by, created_at, updated_at,
                   sync_status, trust_level, data_classification, source,
                   builder_document, validation_summary, ai_change_records,
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
                builder_document, validation_summary, ai_change_records,
                residual_island_count, round_trip_fidelity, version_reason
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

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
        this.jdbc = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
        LOG.debug("DbPageArtifactRepository created");
    }

    @Override
    public Promise<Void> save(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull PageArtifactDocument document
    ) {
        LOG.debug("Saving page artifact: tenant={}, workspace={}, project={}, artifactId={}, documentId={}",
                tenantId, workspaceId, projectId, document.artifactId(), document.documentId());

        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            // Check if this is an insert or update
            Optional<PageArtifactDocument> existing = jdbc.queryForObject(
                    SELECT_ARTIFACT,
                    this::mapRow,
                    tenantId,
                    workspaceId,
                    projectId,
                    document.artifactId()
            );

            if (existing.isEmpty()) {
                // Insert new artifact
                jdbc.update(
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
                        document.residualIslandCount(),
                        document.roundTripFidelity()
                );
                LOG.debug("Inserted new page artifact: {}", document.artifactId());
            } else {
                // Update existing artifact with optimistic concurrency
                PageArtifactDocument existingDoc = existing.get();
                if (!existingDoc.documentId().equals(document.documentId())) {
                    LOG.warn("Conflict detected for artifact {}: expected documentId={}, got documentId={}",
                            document.artifactId(), existingDoc.documentId(), document.documentId());
                    throw new PageArtifactConflictException(
                            document.artifactId(),
                            existingDoc.documentId()
                    );
                }

                // Archive current version before updating
                long artifactId = getArtifactId(tenantId, workspaceId, projectId, document.artifactId());
                archiveVersion(artifactId, tenantId, workspaceId, projectId, existingDoc, "Version before update");

                // Update artifact
                int updated = jdbc.update(
                        UPDATE_ARTIFACT,
                        document.documentId(),
                        document.name(),
                        document.updatedAt(),
                        document.syncStatus(),
                        document.trustLevel(),
                        document.dataClassification(),
                        document.source(),
                        toJson(document.builderDocument()),
                        toJson(document.validationSummary()),
                        toJson(document.aiChangeRecords()),
                        document.residualIslandCount(),
                        document.roundTripFidelity(),
                        tenantId,
                        workspaceId,
                        projectId,
                        document.artifactId(),
                        existingDoc.documentId()  // WHERE clause checks old documentId
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
            }

            return null;
        });
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

        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
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

        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
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
                    rs.getDouble("round_trip_fidelity")
            );
        } catch (Exception e) {
            LOG.error("Failed to map ResultSet to PageArtifactDocument", e);
            throw new SQLException("Failed to map ResultSet to PageArtifactDocument", e);
        }
    }

    private long getArtifactId(String tenantId, String workspaceId, String projectId, String artifactId) {
        return jdbc.queryForObject(
                "SELECT id FROM page_artifacts WHERE tenant_id = ? AND workspace_id = ? AND project_id = ? AND artifact_id = ?",
                rs -> rs.getLong("id"),
                tenantId,
                workspaceId,
                projectId,
                artifactId
        ).orElseThrow(() -> new IllegalStateException("Artifact not found: " + artifactId));
    }

    private void archiveVersion(
            long artifactId,
            String tenantId,
            String workspaceId,
            String projectId,
            PageArtifactDocument document,
            String reason
    ) {
        jdbc.update(
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
                document.residualIslandCount(),
                document.roundTripFidelity(),
                reason
        );
        LOG.debug("Archived version for artifact {}: documentId={}", document.artifactId(), document.documentId());
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> fromJson(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return java.util.Map.of();
        }
        return objectMapper.readValue(json, java.util.Map.class);
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
        return objectMapper.readValue(json, java.util.List.class);
    }
}
