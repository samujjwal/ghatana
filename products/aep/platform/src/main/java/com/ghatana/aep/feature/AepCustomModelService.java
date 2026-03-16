/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.feature;

import com.ghatana.aiplatform.registry.DeploymentStatus;
import com.ghatana.aiplatform.registry.ModelMetadata;
import com.ghatana.aiplatform.registry.ModelRegistryService;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Production-grade custom model support for AEP — artifact management,
 * validation gates, and canary deployment coordination.
 *
 * <h3>Capabilities beyond {@link AepModelRegistryClient}</h3>
 * <ul>
 *   <li><b>Artifact provenance</b> — stores SHA-256 checksum, artifact URI,
 *       git commit, training dataset hash, and hyperparameters in addition to
 *       the metadata managed by {@link ModelRegistryService}.</li>
 *   <li><b>Validation gates</b> — evaluates configurable metric thresholds
 *       (e.g. f1 ≥ 0.90, auc_roc ≥ 0.95) before a version may be promoted.
 *       Failed validations are recorded and block promotion.</li>
 *   <li><b>Canary deployments</b> — manages live traffic splitting between
 *       the incumbent production model and a candidate version.  Traffic
 *       percentage can be adjusted incrementally (10 → 25 → 50 → 100).
 *       The service routes a given inference request to either version via
 *       {@link #routeRequest(String, String)}.</li>
 *   <li><b>Observability</b> — Micrometer counters and timers for every
 *       artifact registration, validation run, canary operation, and routing
 *       decision.</li>
 * </ul>
 *
 * <h3>Typical workflow</h3>
 * <pre>{@code
 * // 1. After training: register the artifact with provenance
 * service.registerArtifact(
 *     AepCustomModelVersion.of(
 *         tenantId, modelMetadata.getId(), "pattern-recommender", "v3.0.0",
 *         "s3://models/pattern-recommender-v3.onnx", sha256Hex,
 *         Map.of("learning_rate", "0.001", "epochs", "50"),
 *         Map.of("f1_score", 0.90, "auc_roc", 0.95)))
 *   .then(__ -> service.validate(tenantId, "pattern-recommender", "v3.0.0",
 *                               Map.of("f1_score", 0.93, "auc_roc", 0.97)))
 *   .then(passed -> passed
 *       ? service.startCanary(tenantId, "pattern-recommender", "v2.1.0", "v3.0.0", 10)
 *       : Promise.of(false))   // block promotion if validation fails
 *   .whenResult(started -> log.info("Canary started: {}", started));
 *
 * // 2. During inference: route the request
 * String selectedVersion = service.routeRequest(tenantId, "pattern-recommender").getResult();
 *
 * // 3. Gradually increase traffic
 * service.adjustCanaryTraffic(tenantId, "pattern-recommender", 25)
 *   .then(__ -> service.adjustCanaryTraffic(tenantId, "pattern-recommender", 50))
 *   .then(__ -> service.promoteCanary(tenantId, "pattern-recommender"));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Custom model artifact registration, validation gates, and canary deployment
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AepCustomModelService {

    private static final Logger log = LoggerFactory.getLogger(AepCustomModelService.class);

    private final DataSource dataSource;
    private final ModelRegistryService modelRegistry;
    private final Executor blockingExecutor;

    // Observability
    private final Counter artifactsRegisteredCounter;
    private final Counter validationPassedCounter;
    private final Counter validationFailedCounter;
    private final Counter canaryStartedCounter;
    private final Counter canaryPromotedCounter;
    private final Counter canaryRolledBackCounter;
    private final Timer validationTimer;

    /**
     * Creates a new service instance.
     *
     * @param dataSource        JDBC data source for the custom model tables
     * @param modelRegistry     platform model registry service
     * @param meterRegistry     Micrometer registry
     * @param blockingExecutor  executor for blocking JDBC calls — must not be
     *                          the ActiveJ event-loop thread
     */
    public AepCustomModelService(
            DataSource dataSource,
            ModelRegistryService modelRegistry,
            MeterRegistry meterRegistry,
            Executor blockingExecutor) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry");
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor");
        Objects.requireNonNull(meterRegistry, "meterRegistry");

        this.artifactsRegisteredCounter = Counter.builder("aep.custom_model.artifacts.registered")
                .description("Custom model artifacts registered")
                .register(meterRegistry);
        this.validationPassedCounter = Counter.builder("aep.custom_model.validation.passed")
                .description("Model validation gates passed")
                .register(meterRegistry);
        this.validationFailedCounter = Counter.builder("aep.custom_model.validation.failed")
                .description("Model validation gates failed")
                .register(meterRegistry);
        this.canaryStartedCounter = Counter.builder("aep.custom_model.canary.started")
                .description("Canary deployments initiated")
                .register(meterRegistry);
        this.canaryPromotedCounter = Counter.builder("aep.custom_model.canary.promoted")
                .description("Canary deployments promoted to production")
                .register(meterRegistry);
        this.canaryRolledBackCounter = Counter.builder("aep.custom_model.canary.rolled_back")
                .description("Canary deployments rolled back")
                .register(meterRegistry);
        this.validationTimer = Timer.builder("aep.custom_model.validation.duration")
                .description("Model validation gate evaluation duration")
                .register(meterRegistry);
    }

    // =========================================================================
    // Artifact provenance
    // =========================================================================

    /**
     * Registers an extended artifact version record in addition to the base
     * metadata already managed by {@link ModelRegistryService}.
     *
     * <p>The artifact checksum is validated for format correctness (64-character
     * hex string) before any write occurs.
     *
     * @param version version descriptor including checksum and URI
     * @return promise completing when the artifact record is persisted
     * @throws IllegalArgumentException if the SHA-256 is malformed
     */
    public Promise<Void> registerArtifact(AepCustomModelVersion version) {
        Objects.requireNonNull(version, "version");
        validateSha256(version.artifactSha256());
        return Promise.ofBlocking(blockingExecutor, () -> {
            String sql = """
                INSERT INTO aep_custom_model_versions
                    (id, tenant_id, model_id, model_name, version,
                     artifact_uri, artifact_sha256, git_commit_sha,
                     training_dataset_hash, hyperparameters,
                     validation_thresholds, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                ON CONFLICT (tenant_id, model_name, version) DO UPDATE SET
                    artifact_uri          = EXCLUDED.artifact_uri,
                    artifact_sha256       = EXCLUDED.artifact_sha256,
                    git_commit_sha        = EXCLUDED.git_commit_sha,
                    training_dataset_hash = EXCLUDED.training_dataset_hash,
                    hyperparameters       = EXCLUDED.hyperparameters,
                    validation_thresholds = EXCLUDED.validation_thresholds
                """;
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, version.id());
                ps.setString(2, version.tenantId());
                ps.setObject(3, version.modelId());
                ps.setString(4, version.modelName());
                ps.setString(5, version.version());
                ps.setString(6, version.artifactUri());
                ps.setString(7, version.artifactSha256());
                ps.setString(8, version.gitCommitSha());
                ps.setString(9, version.trainingDatasetHash());
                ps.setString(10, toJson(version.hyperparameters()));
                ps.setString(11, toJson(version.validationThresholds()));
                ps.setTimestamp(12, Timestamp.from(version.createdAt()));
                ps.executeUpdate();
            }
            artifactsRegisteredCounter.increment();
            log.info("[CustomModel] Registered artifact tenant='{}' model='{}' version='{}'  sha='{}'",
                    version.tenantId(), version.modelName(),
                    version.version(), version.artifactSha256().substring(0, 8) + "…");
            return null;
        });
    }

    /**
     * Retrieves all artifact versions for a model, newest first.
     *
     * @param tenantId  tenant identifier
     * @param modelName logical model name
     * @return promise of the version list
     */
    public Promise<List<AepCustomModelVersion>> listArtifactVersions(
            String tenantId, String modelName) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelName, "modelName");
        return Promise.ofBlocking(blockingExecutor, () -> {
            String sql = """
                SELECT id, tenant_id, model_id, model_name, version,
                       artifact_uri, artifact_sha256, git_commit_sha,
                       training_dataset_hash, hyperparameters,
                       validation_thresholds, created_at
                FROM aep_custom_model_versions
                WHERE tenant_id = ? AND model_name = ?
                ORDER BY created_at DESC
                """;
            List<AepCustomModelVersion> versions = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, modelName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        versions.add(mapVersionRow(rs));
                    }
                }
            }
            return versions;
        });
    }

    // =========================================================================
    // Validation gates
    // =========================================================================

    /**
     * Evaluates whether the supplied live metrics satisfy the configured
     * validation thresholds for the given model version.
     *
     * <p>All threshold keys present in the stored version descriptor must be
     * met.  Extra metrics not present in the thresholds are ignored.
     * The result is persisted to {@code aep_model_validation_runs} for audit.
     *
     * @param tenantId    tenant identifier
     * @param modelName   logical model name
     * @param version     version to validate
     * @param liveMetrics evaluated metrics from the training/eval run
     * @return promise of {@code true} when all thresholds pass
     */
    public Promise<Boolean> validate(
            String tenantId, String modelName, String version,
            Map<String, Double> liveMetrics) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelName, "modelName");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(liveMetrics, "liveMetrics");
        return Promise.ofBlocking(blockingExecutor, () ->
                validationTimer.recordCallable(() -> {
                    Optional<AepCustomModelVersion> verOpt = findVersion(tenantId, modelName, version);
                    if (verOpt.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Version not found: " + tenantId + "/" + modelName + "/" + version);
                    }
                    AepCustomModelVersion ver = verOpt.get();
                    Map<String, Double> thresholds = ver.validationThresholds();

                    List<String> failures = new ArrayList<>();
                    for (Map.Entry<String, Double> threshold : thresholds.entrySet()) {
                        double actual = liveMetrics.getOrDefault(threshold.getKey(), Double.NaN);
                        if (Double.isNaN(actual) || actual < threshold.getValue()) {
                            failures.add(String.format("%s: required=%.4f actual=%.4f",
                                    threshold.getKey(), threshold.getValue(), actual));
                        }
                    }

                    boolean passed = failures.isEmpty();
                    writeValidationRun(tenantId, modelName, version,
                            passed, liveMetrics, thresholds, failures);

                    if (passed) {
                        validationPassedCounter.increment();
                        log.info("[CustomModel] Validation PASSED tenant='{}' model='{}' version='{}'",
                                tenantId, modelName, version);
                    } else {
                        validationFailedCounter.increment();
                        log.warn("[CustomModel] Validation FAILED tenant='{}' model='{}' version='{}' failures={}",
                                tenantId, modelName, version, failures);
                    }
                    return passed;
                }));
    }

    // =========================================================================
    // Canary deployment management
    // =========================================================================

    /**
     * Initiates a canary deployment, routing {@code initialTrafficPct}% of
     * inference requests to {@code canaryVersion} and the rest to
     * {@code productionVersion}.
     *
     * <p>Only one ACTIVE or PAUSED canary per (tenantId, modelName) is
     * permitted at a time — the database unique index enforces this.
     *
     * @param tenantId          owning tenant
     * @param modelName         logical model name
     * @param productionVersion incumbent production version (control)
     * @param canaryVersion     version under evaluation (treatment)
     * @param initialTrafficPct starting traffic fraction [1, 99]
     * @return promise of the created canary descriptor
     */
    public Promise<AepCanaryDeployment> startCanary(
            String tenantId, String modelName,
            String productionVersion, String canaryVersion,
            int initialTrafficPct) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelName, "modelName");
        Objects.requireNonNull(productionVersion, "productionVersion");
        Objects.requireNonNull(canaryVersion, "canaryVersion");
        return Promise.ofBlocking(blockingExecutor, () -> {
            AepCanaryDeployment canary = AepCanaryDeployment.start(
                    tenantId, modelName, productionVersion, canaryVersion, initialTrafficPct);
            persistCanary(canary);
            canaryStartedCounter.increment();
            log.info("[CustomModel] Canary started tenant='{}' model='{}' {}->{} pct={}%",
                    tenantId, modelName, productionVersion, canaryVersion, initialTrafficPct);
            return canary;
        });
    }

    /**
     * Adjusts the traffic percentage for the active canary on (tenantId, modelName).
     *
     * @param tenantId  owning tenant
     * @param modelName logical model name
     * @param newPct    new traffic percentage [0, 100]
     * @return promise of the updated canary descriptor
     */
    public Promise<AepCanaryDeployment> adjustCanaryTraffic(
            String tenantId, String modelName, int newPct) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelName, "modelName");
        return Promise.ofBlocking(blockingExecutor, () -> {
            AepCanaryDeployment current = requireActiveCanary(tenantId, modelName);
            AepCanaryDeployment updated = current.withTrafficPct(newPct);
            updateCanary(updated);
            log.info("[CustomModel] Canary traffic adjusted tenant='{}' model='{}' → {}%",
                    tenantId, modelName, newPct);
            return updated;
        });
    }

    /**
     * Promotes the canary to production: updates the model registry deployment
     * status to {@link DeploymentStatus#PRODUCTION} and marks the canary
     * {@link AepCanaryDeployment.Status#PROMOTED}.
     *
     * @param tenantId  owning tenant
     * @param modelName logical model name
     * @return promise of the concluded canary descriptor
     */
    public Promise<AepCanaryDeployment> promoteCanary(String tenantId, String modelName) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelName, "modelName");
        return Promise.ofBlocking(blockingExecutor, () -> {
            AepCanaryDeployment current = requireActiveCanary(tenantId, modelName);

            // Find the canary model and promote it
            Optional<ModelMetadata> modelOpt = modelRegistry
                    .findByName(tenantId, modelName, current.canaryVersion());
            if (modelOpt.isEmpty()) {
                throw new IllegalStateException(
                        "Canary model not found in registry: " + current.canaryVersion());
            }
            modelRegistry.updateStatus(tenantId, modelOpt.get().getId(), DeploymentStatus.PRODUCTION);

            AepCanaryDeployment promoted = current.withStatus(AepCanaryDeployment.Status.PROMOTED);
            updateCanary(promoted);
            canaryPromotedCounter.increment();
            log.info("[CustomModel] Canary PROMOTED tenant='{}' model='{}' version='{}'",
                    tenantId, modelName, current.canaryVersion());
            return promoted;
        });
    }

    /**
     * Rolls back the active canary: sets traffic to 0% and marks the canary
     * {@link AepCanaryDeployment.Status#ROLLED_BACK}.
     *
     * @param tenantId  owning tenant
     * @param modelName logical model name
     * @return promise of the concluded canary descriptor
     */
    public Promise<AepCanaryDeployment> rollbackCanary(String tenantId, String modelName) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelName, "modelName");
        return Promise.ofBlocking(blockingExecutor, () -> {
            AepCanaryDeployment current = requireActiveCanary(tenantId, modelName);
            AepCanaryDeployment rolledBack = current
                    .withTrafficPct(0)
                    .withStatus(AepCanaryDeployment.Status.ROLLED_BACK);
            updateCanary(rolledBack);
            canaryRolledBackCounter.increment();
            log.warn("[CustomModel] Canary ROLLED_BACK tenant='{}' model='{}' version='{}'",
                    tenantId, modelName, current.canaryVersion());
            return rolledBack;
        });
    }

    /**
     * Routes a single inference request to either the canary or production
     * version based on the active canary's traffic split.
     *
     * <p>If no active canary exists the production version is returned. The
     * routing decision is deterministic within a request but random across
     * requests — suitable for slice/dice A/B evaluation.
     *
     * @param tenantId  owning tenant
     * @param modelName logical model name
     * @return promise of the version string to use for this request
     */
    public Promise<String> routeRequest(String tenantId, String modelName) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelName, "modelName");
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<AepCanaryDeployment> canaryOpt = findActiveCanary(tenantId, modelName);
            if (canaryOpt.isEmpty()) {
                // No canary — always return active production version
                return modelRegistry.findByStatus(tenantId, DeploymentStatus.PRODUCTION)
                        .stream()
                        .filter(m -> m.getName().equals(modelName))
                        .findFirst()
                        .map(ModelMetadata::getVersion)
                        .orElseThrow(() -> new IllegalStateException(
                                "No PRODUCTION model found for " + tenantId + "/" + modelName));
            }
            AepCanaryDeployment canary = canaryOpt.get();
            double random = ThreadLocalRandom.current().nextDouble(100.0);
            return (random < canary.canaryTrafficPct())
                    ? canary.canaryVersion() : canary.productionVersion();
        });
    }

    /**
     * Returns the currently active canary for the given (tenantId, modelName),
     * or empty if none is running.
     *
     * @param tenantId  tenant identifier
     * @param modelName logical model name
     * @return promise of an optional canary descriptor
     */
    public Promise<Optional<AepCanaryDeployment>> getActiveCanary(
            String tenantId, String modelName) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelName, "modelName");
        return Promise.ofBlocking(blockingExecutor, () ->
                findActiveCanary(tenantId, modelName));
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private Optional<AepCustomModelVersion> findVersion(
            String tenantId, String modelName, String version) throws SQLException {
        String sql = """
            SELECT id, tenant_id, model_id, model_name, version,
                   artifact_uri, artifact_sha256, git_commit_sha,
                   training_dataset_hash, hyperparameters,
                   validation_thresholds, created_at
            FROM aep_custom_model_versions
            WHERE tenant_id = ? AND model_name = ? AND version = ?
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, modelName);
            ps.setString(3, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapVersionRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    private AepCustomModelVersion mapVersionRow(ResultSet rs) throws SQLException {
        return new AepCustomModelVersion(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_id"),
                rs.getObject("model_id", UUID.class),
                rs.getString("model_name"),
                rs.getString("version"),
                rs.getString("artifact_uri"),
                rs.getString("artifact_sha256"),
                rs.getString("git_commit_sha"),
                rs.getString("training_dataset_hash"),
                fromJson(rs.getString("hyperparameters")),
                fromDoubleJson(rs.getString("validation_thresholds")),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private void persistCanary(AepCanaryDeployment canary) throws SQLException {
        String sql = """
            INSERT INTO aep_canary_deployments
                (id, tenant_id, model_name, production_version, canary_version,
                 canary_traffic_pct, status, started_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, canary.id());
            ps.setString(2, canary.tenantId());
            ps.setString(3, canary.modelName());
            ps.setString(4, canary.productionVersion());
            ps.setString(5, canary.canaryVersion());
            ps.setInt(6, canary.canaryTrafficPct());
            ps.setString(7, canary.status().name());
            ps.setTimestamp(8, Timestamp.from(canary.startedAt()));
            ps.executeUpdate();
        }
    }

    private void updateCanary(AepCanaryDeployment canary) throws SQLException {
        String sql = """
            UPDATE aep_canary_deployments
            SET canary_traffic_pct = ?,
                status             = ?,
                concluded_at       = ?
            WHERE id = ?
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, canary.canaryTrafficPct());
            ps.setString(2, canary.status().name());
            ps.setTimestamp(3, canary.concludedAt() != null
                    ? Timestamp.from(canary.concludedAt()) : null);
            ps.setObject(4, canary.id());
            ps.executeUpdate();
        }
    }

    private AepCanaryDeployment requireActiveCanary(
            String tenantId, String modelName) throws SQLException {
        return findActiveCanary(tenantId, modelName).orElseThrow(() ->
                new IllegalStateException(
                        "No active canary for " + tenantId + "/" + modelName));
    }

    private Optional<AepCanaryDeployment> findActiveCanary(
            String tenantId, String modelName) throws SQLException {
        String sql = """
            SELECT id, tenant_id, model_name, production_version, canary_version,
                   canary_traffic_pct, status, started_at, concluded_at
            FROM aep_canary_deployments
            WHERE tenant_id = ? AND model_name = ?
              AND status IN ('ACTIVE', 'PAUSED')
            LIMIT 1
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, modelName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapCanaryRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    private AepCanaryDeployment mapCanaryRow(ResultSet rs) throws SQLException {
        Timestamp concludedTs = rs.getTimestamp("concluded_at");
        return new AepCanaryDeployment(
                rs.getObject("id", UUID.class),
                rs.getString("tenant_id"),
                rs.getString("model_name"),
                rs.getString("production_version"),
                rs.getString("canary_version"),
                rs.getInt("canary_traffic_pct"),
                AepCanaryDeployment.Status.valueOf(rs.getString("status")),
                rs.getTimestamp("started_at").toInstant(),
                concludedTs != null ? concludedTs.toInstant() : null
        );
    }

    private void writeValidationRun(
            String tenantId, String modelName, String version,
            boolean passed, Map<String, Double> metrics,
            Map<String, Double> thresholds, List<String> failures) throws SQLException {
        String sql = """
            INSERT INTO aep_model_validation_runs
                (tenant_id, model_name, version, passed, metrics, thresholds, failure_reasons)
            VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, modelName);
            ps.setString(3, version);
            ps.setBoolean(4, passed);
            ps.setString(5, toJson(metrics));
            ps.setString(6, toJson(thresholds));
            ps.setArray(7, c.createArrayOf("TEXT", failures.toArray(new String[0])));
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.warn("[CustomModel] Could not write validation audit record", ex);
        }
    }

    private static void validateSha256(String sha256) {
        if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException(
                    "artifactSha256 must be a 64-character hex string; got: "
                            + (sha256 == null ? "null" : "'" + sha256 + "'"));
        }
    }

    /** Minimal JSON serialiser — avoids an extra Jackson dependency in this class. */
    private static String toJson(Map<?, ?> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) sb.append(',');
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof Number) {
                sb.append(v);
            } else {
                sb.append('"').append(v).append('"');
            }
            first = false;
        }
        return sb.append('}').toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> fromJson(String json) {
        // Simple parser — in production leverage Jackson already in the classpath
        if (json == null || json.equals("{}") || json.isBlank()) return Map.of();
        try {
            com.fasterxml.jackson.databind.ObjectMapper om =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return om.readValue(json, Map.class);
        } catch (Exception ex) {
            log.warn("[CustomModel] Could not deserialise hyperparameters JSON: {}", json, ex);
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Double> fromDoubleJson(String json) {
        if (json == null || json.equals("{}") || json.isBlank()) return Map.of();
        try {
            com.fasterxml.jackson.databind.ObjectMapper om =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return om.readValue(json, Map.class);
        } catch (Exception ex) {
            log.warn("[CustomModel] Could not deserialise thresholds JSON: {}", json, ex);
            return Map.of();
        }
    }
}
