package com.ghatana.yappc.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.services.artifact.compileback.PatchSetService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * @doc.type class
 * @doc.purpose JDBC persistence for patch sets, change plans, and review bundles
 * @doc.layer infrastructure
 * @doc.pattern Repository
 *
 * P1: Provides durable storage for compile-back patch lifecycle including
 * change plans, patch sets, file patches, validation results, review bundles,
 * and rollback metadata with full tenant/workspace/project isolation.
 */
public final class PatchSetRepository {

    private static final Logger log = LoggerFactory.getLogger(PatchSetRepository.class);
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final TypeReference<List<Map<String, Object>>> OBJECT_LIST = new TypeReference<>() { };

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public PatchSetRepository(DataSource dataSource, ObjectMapper objectMapper, Executor executor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    // ============================================================================
    // Change Plan Operations
    // ============================================================================

    /**
     * P1: Persist a change plan.
     */
    public Promise<com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ChangePlan> saveChangePlan(
        com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ChangePlan plan
    ) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO change_plans (
                    id, plan_id, tenant_id, workspace_id, project_id, base_model_id, target_model_id,
                    operation_count, auto_applicable_count, review_required_count,
                    impact_assessment_json, validation_result_json, created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (plan_id) DO UPDATE SET
                    impact_assessment_json = EXCLUDED.impact_assessment_json,
                    validation_result_json = EXCLUDED.validation_result_json
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, plan.planId());
                statement.setString(2, plan.planId());
                statement.setString(3, plan.tenantId());
                statement.setString(4, plan.workspaceId());
                statement.setString(5, plan.projectId());
                statement.setString(6, plan.baseModelId());
                statement.setString(7, plan.targetModelId());
                statement.setInt(8, plan.getOperationCount());
                statement.setInt(9, plan.getAutoApplicableCount());
                statement.setInt(10, plan.getReviewRequiredCount());
                statement.setString(11, writeJson(plan.impact()));
                statement.setString(12, null);
                statement.setTimestamp(13, Timestamp.from(plan.createdAt()));
                statement.setString(14, plan.createdBy());
                statement.executeUpdate();
            }
            return plan;
        });
    }

    /**
     * P1: Find a change plan by ID.
     */
    public Promise<Optional<com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ChangePlan>> findChangePlanById(String planId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT plan_id, tenant_id, workspace_id, project_id, base_model_id, target_model_id,
                       operation_count, auto_applicable_count, review_required_count,
                       impact_assessment_json, validation_result_json, created_at, created_by
                FROM change_plans
                WHERE plan_id = ?
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, planId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapChangePlan(rs));
                    }
                    return Optional.<com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ChangePlan>empty();
                }
            }
        });
    }

    /**
     * P1: List change plans by scope.
     */
    public Promise<List<com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ChangePlan>> listChangePlansByScope(
        String tenantId, String workspaceId, String projectId, int limit
    ) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT plan_id, tenant_id, workspace_id, project_id, base_model_id, target_model_id,
                       operation_count, auto_applicable_count, review_required_count,
                       impact_assessment_json, validation_result_json, created_at, created_by
                FROM change_plans
                WHERE tenant_id = ? AND workspace_id = ? AND project_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;

            List<com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ChangePlan> plans = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, workspaceId);
                statement.setString(3, projectId);
                statement.setInt(4, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        plans.add(mapChangePlan(rs));
                    }
                }
            }
            return plans;
        });
    }

    // ============================================================================
    // Patch Set Operations
    // ============================================================================

    /**
     * Persist a complete patch set with all file patches.
     */
    public Promise<PatchSetService.PatchSet> savePatchSet(PatchSetService.PatchSet patchSet) {
        return Promise.ofBlocking(executor, () -> {
            String patchSetSql = """
                INSERT INTO patch_sets (
                    id, patch_set_id, tenant_id, workspace_id, project_id, change_plan_id, plan_id, snapshot_id,
                    status, preserved_residuals_json, review_required_patches_json,
                    stats_json, created_at, created_by, applied_at, applied_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (patch_set_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    preserved_residuals_json = EXCLUDED.preserved_residuals_json,
                    review_required_patches_json = EXCLUDED.review_required_patches_json,
                    stats_json = EXCLUDED.stats_json,
                    applied_at = EXCLUDED.applied_at,
                    applied_by = EXCLUDED.applied_by
                """;

            String patchSql = """
                INSERT INTO patch_set_patches (
                    patch_id, patch_set_id, relative_path, diff, ranges_json, is_atomic,
                    source_change_op_id, emitter_id, base_checksum, target_checksum, validation_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (patch_id) DO UPDATE SET
                    diff = EXCLUDED.diff,
                    ranges_json = EXCLUDED.ranges_json,
                    validation_status = EXCLUDED.validation_status
                """;

            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    // Insert/update patch set
                    try (PreparedStatement statement = connection.prepareStatement(patchSetSql)) {
                        statement.setString(1, patchSet.patchSetId());
                        statement.setString(2, patchSet.patchSetId());
                        statement.setString(3, patchSet.tenantId());
                        statement.setString(4, patchSet.workspaceId());
                        statement.setString(5, patchSet.projectId());
                        statement.setString(6, patchSet.planId());
                        statement.setString(7, patchSet.planId());
                        statement.setString(8, patchSet.snapshotId());
                        statement.setString(9, patchSet.status().name());
                        statement.setString(10, writeJson(patchSet.preservedResiduals()));
                        statement.setString(11, writeJson(patchSet.reviewRequiredPatches()));
                        statement.setString(12, writeStats(patchSet.stats()));
                        statement.setTimestamp(13, Timestamp.from(patchSet.createdAt()));
                        statement.setString(14, patchSet.createdBy());
                        statement.setTimestamp(15, patchSet.appliedAt() != null ? Timestamp.from(patchSet.appliedAt()) : null);
                        statement.setString(16, patchSet.appliedBy());
                        statement.executeUpdate();
                    }

                    // Insert/update patches
                    if (patchSet.patches() != null && !patchSet.patches().isEmpty()) {
                        try (PreparedStatement statement = connection.prepareStatement(patchSql)) {
                            for (PatchSetService.TextPatch patch : patchSet.patches()) {
                                statement.setString(1, patch.patchId());
                                statement.setString(2, patchSet.patchSetId());
                                statement.setString(3, patch.relativePath());
                                statement.setString(4, patch.diff());
                                statement.setString(5, writeRanges(patch.ranges()));
                                statement.setBoolean(6, patch.isAtomic());
                                statement.setString(7, patch.sourceChangeOpId());
                                statement.setString(8, patch.emitterId());
                                statement.setString(9, patch.baseChecksum());
                                statement.setString(10, patch.targetChecksum());
                                statement.setString(11, patch.validationStatus().name());
                                statement.addBatch();
                            }
                            statement.executeBatch();
                        }
                    }

                    connection.commit();
                    log.info("Persisted patch set {} for project {} with {} patches",
                        patchSet.patchSetId(), patchSet.projectId(),
                        patchSet.patches() != null ? patchSet.patches().size() : 0);
                    return patchSet;
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        });
    }

    /**
     * Find a patch set by ID.
     */
    public Promise<Optional<PatchSetService.PatchSet>> findPatchSetById(String patchSetId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT patch_set_id, tenant_id, workspace_id, project_id, plan_id, snapshot_id,
                       status, preserved_residuals_json, review_required_patches_json,
                       stats_json, created_at, created_by, applied_at, applied_by
                FROM patch_sets
                WHERE patch_set_id = ?
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, patchSetId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        List<PatchSetService.TextPatch> patches = loadPatches(connection, patchSetId);
                        return Optional.of(mapPatchSet(rs, patches));
                    }
                    return Optional.<PatchSetService.PatchSet>empty();
                }
            }
        });
    }

    /**
     * List patch sets by scope.
     */
    public Promise<List<PatchSetService.PatchSet>> listPatchSetsByScope(String tenantId, String workspaceId, String projectId, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");

        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT patch_set_id, tenant_id, workspace_id, project_id, plan_id, snapshot_id,
                       status, preserved_residuals_json, review_required_patches_json,
                       stats_json, created_at, created_by, applied_at, applied_by
                FROM patch_sets
                WHERE tenant_id = ? AND workspace_id = ? AND project_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;

            List<PatchSetService.PatchSet> patchSets = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, workspaceId);
                statement.setString(3, projectId);
                statement.setInt(4, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("patch_set_id");
                        List<PatchSetService.TextPatch> patches = loadPatches(connection, id);
                        patchSets.add(mapPatchSet(rs, patches));
                    }
                }
            }
            return patchSets;
        });
    }

    /**
     * Update patch set status.
     */
    public Promise<Void> updatePatchSetStatus(String patchSetId, PatchSetService.PatchSetStatus status) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                UPDATE patch_sets
                SET status = ?
                WHERE patch_set_id = ?
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, status.name());
                statement.setString(2, patchSetId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Mark patch set as applied.
     */
    public Promise<Void> markPatchSetApplied(String patchSetId, String appliedBy) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                UPDATE patch_sets
                SET status = 'APPLIED', applied_at = ?, applied_by = ?
                WHERE patch_set_id = ?
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.from(Instant.now()));
                statement.setString(2, appliedBy);
                statement.setString(3, patchSetId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    // ============================================================================
    // Review Bundle Operations
    // ============================================================================

    /**
     * Persist a review bundle.
     */
    public Promise<Void> saveReviewBundle(com.ghatana.yappc.services.patch.PatchReviewService.ReviewBundle bundle) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO patch_review_bundles (
                    bundle_id, tenant_id, project_id, snapshot_id, version_id, patch_set_id,
                    status, reviewed_by, reviewed_at, created_at, metadata_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (bundle_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    reviewed_by = EXCLUDED.reviewed_by,
                    reviewed_at = EXCLUDED.reviewed_at,
                    metadata_json = EXCLUDED.metadata_json
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, bundle.id());
                statement.setString(2, bundle.tenantId());
                statement.setString(3, bundle.projectId());
                statement.setString(4, bundle.snapshotId());
                statement.setString(5, bundle.versionId());
                statement.setString(6, bundle.patchSetId());
                statement.setString(7, bundle.status());
                statement.setString(8, bundle.reviewedBy());
                statement.setTimestamp(9, bundle.reviewedAt() != null ? Timestamp.from(bundle.reviewedAt()) : null);
                statement.setTimestamp(10, Timestamp.from(bundle.createdAt()));
                statement.setString(11, writeJson(bundle.metadata()));
                statement.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Find review bundle by ID.
     */
    public Promise<Optional<com.ghatana.yappc.services.patch.PatchReviewService.ReviewBundle>> findReviewBundleById(String bundleId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT bundle_id, tenant_id, project_id, snapshot_id, version_id, patch_set_id,
                       status, reviewed_by, reviewed_at, created_at, metadata_json
                FROM patch_review_bundles
                WHERE bundle_id = ?
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, bundleId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapReviewBundle(rs));
                    }
                    return Optional.<com.ghatana.yappc.services.patch.PatchReviewService.ReviewBundle>empty();
                }
            }
        });
    }

    /**
     * List review bundles by project.
     */
    public Promise<List<com.ghatana.yappc.services.patch.PatchReviewService.ReviewBundle>> listReviewBundlesByProject(String tenantId, String projectId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT bundle_id, tenant_id, project_id, snapshot_id, version_id, patch_set_id,
                       status, reviewed_by, reviewed_at, created_at, metadata_json
                FROM patch_review_bundles
                WHERE tenant_id = ? AND project_id = ?
                ORDER BY created_at DESC
                """;

            List<com.ghatana.yappc.services.patch.PatchReviewService.ReviewBundle> bundles = new ArrayList<>();
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setString(2, projectId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        bundles.add(mapReviewBundle(rs));
                    }
                }
            }
            return bundles;
        });
    }

    // ============================================================================
    // Rollback Metadata Operations
    // ============================================================================

    /**
     * Save rollback metadata.
     */
    public Promise<Void> saveRollbackMetadata(PatchSetService.RollbackResult rollback) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO patch_rollback_metadata (
                    rollback_id, patch_set_id, original_patch_set_id, rollback_patch_set_id,
                    rolled_back_by, rolled_back_at, reason, success, error
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (rollback_id) DO UPDATE SET
                    success = EXCLUDED.success,
                    error = EXCLUDED.error
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, rollback.patchSetId() + "_rollback");
                statement.setString(2, rollback.patchSetId());
                statement.setString(3, rollback.originalPatchSetId());
                statement.setString(4, rollback.rollbackPatchSetId());
                statement.setString(5, rollback.rolledBackBy());
                statement.setTimestamp(6, Timestamp.from(rollback.rolledBackAt()));
                statement.setString(7, rollback.reason());
                statement.setBoolean(8, rollback.success());
                statement.setString(9, rollback.error());
                statement.executeUpdate();
            }
            return null;
        });
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private List<PatchSetService.TextPatch> loadPatches(Connection connection, String patchSetId) throws SQLException {
        String sql = """
            SELECT patch_id, relative_path, diff, ranges_json, is_atomic,
                   source_change_op_id, emitter_id, base_checksum, target_checksum, validation_status
            FROM patch_set_patches
            WHERE patch_set_id = ?
            ORDER BY relative_path
            """;

        List<PatchSetService.TextPatch> patches = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, patchSetId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    patches.add(mapPatch(rs));
                }
            }
        }
        return patches;
    }

    private PatchSetService.PatchSet mapPatchSet(ResultSet rs, List<PatchSetService.TextPatch> patches) throws SQLException {
        return new PatchSetService.PatchSet(
            rs.getString("patch_set_id"),
            rs.getString("tenant_id"),
            rs.getString("workspace_id"),
            rs.getString("project_id"),
            rs.getString("plan_id"),
            rs.getString("snapshot_id"),
            PatchSetService.PatchSetStatus.valueOf(rs.getString("status")),
            patches,
            readStringList(rs.getString("preserved_residuals_json")),
            readStringList(rs.getString("review_required_patches_json")),
            readStats(rs.getString("stats_json")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getString("created_by"),
            rs.getTimestamp("applied_at") != null ? rs.getTimestamp("applied_at").toInstant() : null,
            rs.getString("applied_by")
        );
    }

    private PatchSetService.TextPatch mapPatch(ResultSet rs) throws SQLException {
        return new PatchSetService.TextPatch(
            rs.getString("patch_id"),
            rs.getString("relative_path"),
            rs.getString("diff"),
            readRanges(rs.getString("ranges_json")),
            rs.getBoolean("is_atomic"),
            rs.getString("source_change_op_id"),
            rs.getString("emitter_id"),
            rs.getString("base_checksum"),
            rs.getString("target_checksum"),
            PatchSetService.PatchValidationStatus.valueOf(rs.getString("validation_status"))
        );
    }

    private com.ghatana.yappc.services.patch.PatchReviewService.ReviewBundle mapReviewBundle(ResultSet rs) throws SQLException {
        return new com.ghatana.yappc.services.patch.PatchReviewService.ReviewBundle(
            rs.getString("bundle_id"),
            rs.getString("tenant_id"),
            rs.getString("project_id"),
            rs.getString("snapshot_id"),
            rs.getString("version_id"),
            rs.getString("patch_set_id"),
            rs.getString("status"),
            rs.getString("reviewed_by"),
            rs.getTimestamp("reviewed_at") != null ? rs.getTimestamp("reviewed_at").toInstant() : null,
            rs.getTimestamp("created_at").toInstant(),
            readObjectMap(rs.getString("metadata_json"))
        );
    }

    private String writeJson(Object value) throws JsonProcessingException {
        if (value == null) {
            return "null";
        }
        return objectMapper.writeValueAsString(value);
    }

    private String writeStats(PatchSetService.PatchStats stats) throws JsonProcessingException {
        if (stats == null) {
            return "null";
        }
        return objectMapper.writeValueAsString(stats);
    }

    private String writeRanges(List<PatchSetService.PatchRange> ranges) throws JsonProcessingException {
        if (ranges == null || ranges.isEmpty()) {
            return "[]";
        }
        return objectMapper.writeValueAsString(ranges);
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse string list JSON", e);
            return List.of();
        }
    }

    private Map<String, Object> readObjectMap(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, OBJECT_MAP);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse object map JSON", e);
            return Map.of();
        }
    }

    private PatchSetService.PatchStats readStats(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return new PatchSetService.PatchStats(0, 0, 0, 0, 0);
        }
        try {
            return objectMapper.readValue(json, PatchSetService.PatchStats.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse stats JSON", e);
            return new PatchSetService.PatchStats(0, 0, 0, 0, 0);
        }
    }

    private List<PatchSetService.PatchRange> readRanges(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse ranges JSON", e);
            return List.of();
        }
    }

    private com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ChangePlan mapChangePlan(ResultSet rs) throws SQLException {
        int operationCount = rs.getInt("operation_count");
        int autoApplicableCount = rs.getInt("auto_applicable_count");
        int reviewRequiredCount = rs.getInt("review_required_count");
        return new com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ChangePlan(
            rs.getString("plan_id"),
            rs.getString("tenant_id"),
            rs.getString("workspace_id"),
            rs.getString("project_id"),
            rs.getString("base_model_id"),
            rs.getString("target_model_id"),
            syntheticOperations(operationCount, autoApplicableCount, reviewRequiredCount),
            rs.getTimestamp("created_at").toInstant(),
            rs.getString("created_by"),
            "Loaded change plan " + rs.getString("plan_id"),
            readImpactAssessment(rs.getString("impact_assessment_json"))
        );
    }

    private com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ImpactAssessment readImpactAssessment(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return new com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ImpactAssessment(0, 0, 0, 0, List.of(), List.of());
        }
        try {
            return objectMapper.readValue(json, com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ImpactAssessment.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse impact assessment JSON", e);
            return new com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ImpactAssessment(0, 0, 0, 0, List.of(), List.of());
        }
    }

    private com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ValidationResult readValidationResult(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return new com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ValidationResult("", true, List.of(), List.of(), Instant.now(), "");
        }
        try {
            return objectMapper.readValue(json, com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ValidationResult.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse validation result JSON", e);
            return new com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ValidationResult("", false, List.of(), List.of(), Instant.now(), "");
        }
    }

    private List<com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ChangeOp> syntheticOperations(
        int operationCount,
        int autoApplicableCount,
        int reviewRequiredCount
    ) {
        List<com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ChangeOp> operations = new ArrayList<>();
        for (int index = 0; index < operationCount; index++) {
            boolean reviewRequired = index >= autoApplicableCount && index < autoApplicableCount + reviewRequiredCount;
            operations.add(new com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ChangeOp(
                "op-" + index,
                com.ghatana.yappc.services.artifact.compileback.ChangePlanService.ChangeOpKind.MANUAL_REVIEW,
                "synthetic-target-" + index,
                "Reconstructed operation from persisted counts",
                null,
                null,
                reviewRequired ? 0.5 : 0.95,
                reviewRequired
            ));
        }
        return operations;
    }
}
