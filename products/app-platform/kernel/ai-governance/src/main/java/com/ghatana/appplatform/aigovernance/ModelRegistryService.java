package com.ghatana.appplatform.aigovernance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Centralized AI model registry: register, version, and track all ML models.
 *              ModelRecord: model_id, name, version, type, framework, status lifecycle
 *              (DRAFT→VALIDATED→DEPLOYED→DEPRECATED→RETIRED), owner_team. REST API surface.
 *              Publishes ModelRegistered event. Satisfies STORY-K09-001.
 * @doc.layer   Kernel
 * @doc.pattern Model registry CRUD; status lifecycle state machine; K-07 audit via EventPort;
 *              ModelRegistered event; Counter + Gauge.
 */
public class ModelRegistryService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final EventPort        eventPort;
    private final Counter          modelsRegisteredCounter;
    private final AtomicLong       deployedModelsGauge = new AtomicLong(0);

    public ModelRegistryService(HikariDataSource dataSource, Executor executor,
                                 EventPort eventPort, MeterRegistry registry) {
        this.dataSource             = dataSource;
        this.executor               = executor;
        this.eventPort              = eventPort;
        this.modelsRegisteredCounter = Counter.builder("ai.registry.models_registered_total").register(registry);
        Gauge.builder("ai.registry.deployed_models", deployedModelsGauge, AtomicLong::get)
                .register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    public interface EventPort {
        void publish(String topic, Object event);
    }

    // ─── Enums & Records ─────────────────────────────────────────────────────

    public enum ModelType      { CLASSIFICATION, REGRESSION, ANOMALY, NLP, CLUSTERING }
    public enum ModelFramework { SCIKIT, PYTORCH, TENSORFLOW, XGBOOST, ONNX }
    public enum ModelStatus    { DRAFT, VALIDATED, DEPLOYED, DEPRECATED, RETIRED }

    public record ModelRecord(String modelId, String name, int version, ModelType type,
                               ModelFramework framework, ModelStatus status, String ownerTeam,
                               String trainingDataRef, String description,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {}

    // ─── Allowed status transitions ─────────────────────────────────────────

    private static final java.util.Map<ModelStatus, List<ModelStatus>> TRANSITIONS = java.util.Map.of(
            ModelStatus.DRAFT,       List.of(ModelStatus.VALIDATED),
            ModelStatus.VALIDATED,   List.of(ModelStatus.DEPLOYED, ModelStatus.DEPRECATED),
            ModelStatus.DEPLOYED,    List.of(ModelStatus.DEPRECATED),
            ModelStatus.DEPRECATED,  List.of(ModelStatus.RETIRED),
            ModelStatus.RETIRED,     List.of()
    );

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<ModelRecord> register(String name, ModelType type, ModelFramework framework,
                                          String ownerTeam, String trainingDataRef,
                                          String description) {
        return Promise.ofBlocking(executor, () -> {
            int nextVersion = nextVersionForName(name);
            String modelId = UUID.randomUUID().toString();
            ModelRecord model = insertModel(modelId, name, nextVersion, type, framework,
                    ownerTeam, trainingDataRef, description);
            modelsRegisteredCounter.increment();
            eventPort.publish("ai.registry.model_registered", model);
            return model;
        });
    }

    public Promise<ModelRecord> transition(String modelId, ModelStatus targetStatus) {
        return Promise.ofBlocking(executor, () -> {
            ModelRecord current = loadModel(modelId)
                    .orElseThrow(() -> new IllegalArgumentException("Model not found: " + modelId));
            List<ModelStatus> allowed = TRANSITIONS.get(current.status());
            if (!allowed.contains(targetStatus)) {
                throw new IllegalStateException("Invalid transition: " + current.status()
                        + " → " + targetStatus + " for model " + modelId);
            }
            ModelRecord updated = updateStatus(modelId, targetStatus);
            deployedModelsGauge.set(countByStatus(ModelStatus.DEPLOYED));
            eventPort.publish("ai.registry.model_status_changed",
                    new ModelStatusChangedEvent(modelId, current.status(), targetStatus));
            return updated;
        });
    }

    public Promise<Optional<ModelRecord>> findById(String modelId) {
        return Promise.ofBlocking(executor, () -> loadModel(modelId));
    }

    public Promise<List<ModelRecord>> search(ModelType type, ModelStatus status, String ownerTeam) {
        return Promise.ofBlocking(executor, () -> queryModels(type, status, ownerTeam));
    }

    public Promise<List<ModelRecord>> getVersionHistory(String name) {
        return Promise.ofBlocking(executor, () -> {
            List<ModelRecord> versions = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT * FROM model_registry WHERE name=? ORDER BY version DESC")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) versions.add(mapRow(rs));
                }
            }
            return versions;
        });
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private ModelRecord insertModel(String modelId, String name, int version, ModelType type,
                                     ModelFramework framework, String ownerTeam,
                                     String trainingDataRef, String description) throws SQLException {
        String sql = """
                INSERT INTO model_registry
                    (model_id, name, version, model_type, framework, status, owner_team,
                     training_data_ref, description, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, NOW(), NOW())
                ON CONFLICT (name, version) DO NOTHING
                RETURNING *
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, modelId); ps.setString(2, name); ps.setInt(3, version);
            ps.setString(4, type.name()); ps.setString(5, framework.name());
            ps.setString(6, ownerTeam); ps.setString(7, trainingDataRef);
            ps.setString(8, description);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return loadByNameVersion(name, version);
                return mapRow(rs);
            }
        }
    }

    private ModelRecord updateStatus(String modelId, ModelStatus status) throws SQLException {
        String sql = """
                UPDATE model_registry SET status=?, updated_at=NOW()
                WHERE model_id=? RETURNING *
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name()); ps.setString(2, modelId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next(); return mapRow(rs);
            }
        }
    }

    private Optional<ModelRecord> loadModel(String modelId) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM model_registry WHERE model_id=?")) {
            ps.setString(1, modelId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    private ModelRecord loadByNameVersion(String name, int version) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM model_registry WHERE name=? AND version=?")) {
            ps.setString(1, name); ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next(); return mapRow(rs);
            }
        }
    }

    private List<ModelRecord> queryModels(ModelType type, ModelStatus status,
                                           String ownerTeam) throws SQLException {
        StringBuilder sb = new StringBuilder("SELECT * FROM model_registry WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (type != null)      { sb.append(" AND model_type=?"); params.add(type.name()); }
        if (status != null)    { sb.append(" AND status=?");     params.add(status.name()); }
        if (ownerTeam != null) { sb.append(" AND owner_team=?"); params.add(ownerTeam); }
        sb.append(" ORDER BY created_at DESC LIMIT 100");

        List<ModelRecord> models = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) models.add(mapRow(rs));
            }
        }
        return models;
    }

    private int nextVersionForName(String name) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COALESCE(MAX(version), 0) + 1 FROM model_registry WHERE name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        }
    }

    private long countByStatus(ModelStatus status) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM model_registry WHERE status=?")) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private ModelRecord mapRow(ResultSet rs) throws SQLException {
        return new ModelRecord(rs.getString("model_id"), rs.getString("name"),
                rs.getInt("version"), ModelType.valueOf(rs.getString("model_type")),
                ModelFramework.valueOf(rs.getString("framework")),
                ModelStatus.valueOf(rs.getString("status")), rs.getString("owner_team"),
                rs.getString("training_data_ref"), rs.getString("description"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class));
    }

    record ModelStatusChangedEvent(String modelId, ModelStatus previousStatus,
                                    ModelStatus newStatus) {}
}
