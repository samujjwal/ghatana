package com.ghatana.appplatform.aigovernance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Append-only audit log for every model prediction. Stores prediction_id,
 *              model_id, model version, input hash (SHA-256 of raw input to avoid PII
 *              storage), raw output, confidence score, latency, and timestamp. Supports
 *              querying by model, time window, and confidence threshold for compliance
 *              and investigation workflows. Satisfies STORY-K09-006.
 * @doc.layer   Kernel
 * @doc.pattern Append-only audit; SHA-256 input hash for PII safety; K-07 AuditPort for
 *              compliance events; predictionLogged Counter; immutable log rows (no UPDATE).
 */
public class PredictionAuditLogService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final AuditPort        auditPort;
    private final Counter          predictionLoggedCounter;

    public PredictionAuditLogService(HikariDataSource dataSource, Executor executor,
                                      AuditPort auditPort,
                                      MeterRegistry registry) {
        this.dataSource              = dataSource;
        this.executor                = executor;
        this.auditPort               = auditPort;
        this.predictionLoggedCounter = Counter.builder("aigovernance.prediction.logged_total").register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    /** K-07 audit trail for compliance events triggered from audit log queries. */
    public interface AuditPort {
        void log(String action, String resourceType, String resourceId, Map<String, Object> details);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public record PredictionLogEntry(
        String predictionId, String modelId, String modelVersion,
        String inputHash, String output, double confidence,
        long latencyMs, Instant predictedAt, String requestedBy
    ) {}

    public record AuditQuery(
        String modelId, Instant from, Instant to,
        double minConfidence, double maxConfidence, int limit
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Append a new prediction log entry. Input is hashed with SHA-256 to prevent PII storage.
     */
    public Promise<String> logPrediction(String modelId, String modelVersion,
                                          String rawInput, String output,
                                          double confidence, long latencyMs,
                                          String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            String predictionId = UUID.randomUUID().toString();
            String inputHash    = sha256Hex(rawInput);

            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO prediction_audit_log " +
                     "(prediction_id, model_id, model_version, input_hash, output, " +
                     "confidence, latency_ms, predicted_at, requested_by) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), ?)")) {
                ps.setString(1, predictionId);
                ps.setString(2, modelId);
                ps.setString(3, modelVersion);
                ps.setString(4, inputHash);
                ps.setString(5, output);
                ps.setDouble(6, confidence);
                ps.setLong(7, latencyMs);
                ps.setString(8, requestedBy);
                ps.executeUpdate();
            }

            predictionLoggedCounter.increment();
            auditPort.log("PREDICTION_LOGGED", "Model", modelId,
                Map.of("predictionId", predictionId, "version", modelVersion,
                        "confidence", confidence, "requestedBy", requestedBy));

            return predictionId;
        });
    }

    /**
     * Query prediction log entries filtered by model, time window, and confidence bounds.
     */
    public Promise<List<PredictionLogEntry>> queryLog(AuditQuery query) {
        return Promise.ofBlocking(executor, () -> {
            List<PredictionLogEntry> results = new ArrayList<>();

            String sql = "SELECT prediction_id, model_id, model_version, input_hash, output, " +
                         "confidence, latency_ms, predicted_at, requested_by " +
                         "FROM prediction_audit_log " +
                         "WHERE model_id = ? AND predicted_at BETWEEN ? AND ? " +
                         "AND confidence BETWEEN ? AND ? " +
                         "ORDER BY predicted_at DESC LIMIT ?";

            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, query.modelId());
                ps.setTimestamp(2, Timestamp.from(query.from()));
                ps.setTimestamp(3, Timestamp.from(query.to()));
                ps.setDouble(4, query.minConfidence());
                ps.setDouble(5, query.maxConfidence());
                ps.setInt(6, query.limit());

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(new PredictionLogEntry(
                            rs.getString("prediction_id"),
                            rs.getString("model_id"),
                            rs.getString("model_version"),
                            rs.getString("input_hash"),
                            rs.getString("output"),
                            rs.getDouble("confidence"),
                            rs.getLong("latency_ms"),
                            rs.getTimestamp("predicted_at").toInstant(),
                            rs.getString("requested_by")
                        ));
                    }
                }
            }
            return results;
        });
    }

    /**
     * Count predictions for a model in a given time window (for compliance dashboards).
     */
    public Promise<Long> countPredictions(String modelId, Instant from, Instant to) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM prediction_audit_log " +
                     "WHERE model_id = ? AND predicted_at BETWEEN ? AND ?")) {
                ps.setString(1, modelId);
                ps.setTimestamp(2, Timestamp.from(from));
                ps.setTimestamp(3, Timestamp.from(to));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
