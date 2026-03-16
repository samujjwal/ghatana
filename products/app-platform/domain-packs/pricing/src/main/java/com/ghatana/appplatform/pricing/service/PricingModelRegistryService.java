package com.ghatana.appplatform.pricing.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose T3 pricing model plugin registry: register, version, activate, and deprecate
 *              custom pricing model implementations. Manages IPricingModel plugin interface
 *              metadata. Plugin runs in K-04 T3 sandbox with market data access only.
 *              Supports A/B testing between model versions by routing fraction of requests.
 *              Satisfies STORY-D05-007.
 * @doc.layer   Domain
 * @doc.pattern Plugin registry; K-04 T3 sandbox; A/B routing fraction; versioned models;
 *              Counter for register/activate/deprecate actions.
 */
public class PricingModelRegistryService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final T3SandboxPort    t3SandboxPort;
    private final Counter          registerCounter;
    private final Counter          activateCounter;
    private final Counter          deprecateCounter;

    public PricingModelRegistryService(HikariDataSource dataSource, Executor executor,
                                        T3SandboxPort t3SandboxPort, MeterRegistry registry) {
        this.dataSource       = dataSource;
        this.executor         = executor;
        this.t3SandboxPort    = t3SandboxPort;
        this.registerCounter  = Counter.builder("pricing.model_registry.registered_total").register(registry);
        this.activateCounter  = Counter.builder("pricing.model_registry.activated_total").register(registry);
        this.deprecateCounter = Counter.builder("pricing.model_registry.deprecated_total").register(registry);
    }

    // ─── Inner port ───────────────────────────────────────────────────────────

    /** K-04 T3 sandbox port: validates and loads plugin artifact. */
    public interface T3SandboxPort {
        String validateAndLoadPlugin(String artifactPath, String modelClass);
        void unloadPlugin(String sandboxId);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum ModelStatus { DRAFT, ACTIVE, DEPRECATED, SHADOW }

    public record ModelConfig(String params, double abTestFraction) {}

    public record PricingModelVersion(String modelId, String modelName, int version,
                                      ModelStatus status, String artifactPath, String modelClass,
                                      ModelConfig config, String registeredBy,
                                      LocalDateTime registeredAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<PricingModelVersion> registerModel(String modelName, String artifactPath,
                                                       String modelClass, ModelConfig config,
                                                       String registeredBy) {
        return Promise.ofBlocking(executor, () -> {
            String sandboxId = t3SandboxPort.validateAndLoadPlugin(artifactPath, modelClass);
            t3SandboxPort.unloadPlugin(sandboxId); // only validate, don't keep loaded at registration
            int nextVersion = getNextVersion(modelName);
            String modelId  = UUID.randomUUID().toString();
            persistModel(modelId, modelName, nextVersion, ModelStatus.DRAFT,
                    artifactPath, modelClass, config, registeredBy);
            registerCounter.increment();
            return new PricingModelVersion(modelId, modelName, nextVersion, ModelStatus.DRAFT,
                    artifactPath, modelClass, config, registeredBy, LocalDateTime.now());
        });
    }

    public Promise<Void> activateModel(String modelId, String activatedBy) {
        return Promise.ofBlocking(executor, () -> {
            PricingModelVersion model = loadModel(modelId);
            // Deactivate prior active version for same modelName
            deactivatePrior(model.modelName(), modelId);
            updateStatus(modelId, ModelStatus.ACTIVE);
            logStatusChange(modelId, ModelStatus.ACTIVE, activatedBy);
            activateCounter.increment();
            return null;
        });
    }

    public Promise<Void> deprecateModel(String modelId, String deprecatedBy) {
        return Promise.ofBlocking(executor, () -> {
            updateStatus(modelId, ModelStatus.DEPRECATED);
            logStatusChange(modelId, ModelStatus.DEPRECATED, deprecatedBy);
            deprecateCounter.increment();
            return null;
        });
    }

    public Promise<Void> setShadow(String modelId, double abFraction) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "UPDATE pricing_model_registry SET status = ?, ab_test_fraction = ? WHERE model_id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ModelStatus.SHADOW.name());
                ps.setDouble(2, abFraction);
                ps.setString(3, modelId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public Promise<List<PricingModelVersion>> listModels(String modelName) {
        return Promise.ofBlocking(executor, () -> {
            List<PricingModelVersion> list = new ArrayList<>();
            String sql = """
                    SELECT model_id, model_name, version, status, artifact_path, model_class,
                           params, ab_test_fraction, registered_by, registered_at
                    FROM pricing_model_registry
                    WHERE model_name = ? ORDER BY version DESC
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, modelName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRow(rs));
                }
            }
            return list;
        });
    }

    // ─── Persistence helpers ─────────────────────────────────────────────────

    private int getNextVersion(String modelName) throws SQLException {
        String sql = "SELECT COALESCE(MAX(version), 0) + 1 FROM pricing_model_registry WHERE model_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, modelName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        }
    }

    private void persistModel(String modelId, String modelName, int version, ModelStatus status,
                               String artifactPath, String modelClass, ModelConfig config,
                               String registeredBy) throws SQLException {
        String sql = """
                INSERT INTO pricing_model_registry
                    (model_id, model_name, version, status, artifact_path, model_class,
                     params, ab_test_fraction, registered_by, registered_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, modelId);
            ps.setString(2, modelName);
            ps.setInt(3, version);
            ps.setString(4, status.name());
            ps.setString(5, artifactPath);
            ps.setString(6, modelClass);
            ps.setString(7, config.params());
            ps.setDouble(8, config.abTestFraction());
            ps.setString(9, registeredBy);
            ps.executeUpdate();
        }
    }

    private PricingModelVersion loadModel(String modelId) throws SQLException {
        String sql = """
                SELECT model_id, model_name, version, status, artifact_path, model_class,
                       params, ab_test_fraction, registered_by, registered_at
                FROM pricing_model_registry WHERE model_id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, modelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Model not found: " + modelId);
                return mapRow(rs);
            }
        }
    }

    private void deactivatePrior(String modelName, String exceptModelId) throws SQLException {
        String sql = """
                UPDATE pricing_model_registry SET status = 'DEPRECATED'
                WHERE model_name = ? AND model_id != ? AND status = 'ACTIVE'
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, modelName);
            ps.setString(2, exceptModelId);
            ps.executeUpdate();
        }
    }

    private void updateStatus(String modelId, ModelStatus status) throws SQLException {
        String sql = "UPDATE pricing_model_registry SET status = ? WHERE model_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, modelId);
            ps.executeUpdate();
        }
    }

    private void logStatusChange(String modelId, ModelStatus status, String actor) throws SQLException {
        String sql = "INSERT INTO pricing_model_status_log (model_id, status, actor_id, changed_at) VALUES (?, ?, ?, NOW())";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, modelId);
            ps.setString(2, status.name());
            ps.setString(3, actor);
            ps.executeUpdate();
        }
    }

    private PricingModelVersion mapRow(ResultSet rs) throws SQLException {
        return new PricingModelVersion(rs.getString("model_id"), rs.getString("model_name"),
                rs.getInt("version"), ModelStatus.valueOf(rs.getString("status")),
                rs.getString("artifact_path"), rs.getString("model_class"),
                new ModelConfig(rs.getString("params"), rs.getDouble("ab_test_fraction")),
                rs.getString("registered_by"),
                rs.getTimestamp("registered_at").toLocalDateTime());
    }
}
