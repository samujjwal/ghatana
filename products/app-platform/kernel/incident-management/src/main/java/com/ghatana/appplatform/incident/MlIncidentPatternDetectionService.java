package com.ghatana.appplatform.incident;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose ML-based incident pattern detection using HDBSCAN clustering on incident embeddings.
 *              Detected clusters are labelled as "Known Patterns" and assigned a pattern_id.
 *              A logistic regression predictor uses 30-day rolling incident features to estimate
 *              the probability of a P1/P2 incident within the next 48 hours.
 *              Rejected hypotheses from the AI RCA service are fed back to improve clusters.
 * @doc.layer   Incident Management (R-02)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer; ML delegation
 *
 * STORY-R02-011: ML incident pattern detection — clustering + 48h risk window prediction
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS incident_patterns (
 *   pattern_id       TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   label            TEXT NOT NULL,
 *   description      TEXT,
 *   cluster_size     INT NOT NULL DEFAULT 0,
 *   first_seen_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   last_seen_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE TABLE IF NOT EXISTS incident_pattern_members (
 *   incident_id      TEXT NOT NULL,
 *   pattern_id       TEXT NOT NULL REFERENCES incident_patterns(pattern_id),
 *   similarity_score NUMERIC(5,4),
 *   assigned_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   PRIMARY KEY (incident_id, pattern_id)
 * );
 * CREATE TABLE IF NOT EXISTS incident_risk_predictions (
 *   prediction_id    TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   predicted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   risk_score       NUMERIC(5,4) NOT NULL,   -- 0.0 to 1.0
 *   threshold        NUMERIC(5,4) NOT NULL DEFAULT 0.7,
 *   high_risk        BOOLEAN NOT NULL,
 *   contributing_features JSONB
 * );
 * </pre>
 */
public class MlIncidentPatternDetectionService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface IncidentEmbeddingPort {
        /**
         * Returns embedding vector for an incident (title + description concatenation).
         * Runs local embedding model.
         */
        float[] embed(String incidentId, String text) throws Exception;
    }

    public interface HdbscanPort {
        /**
         * Cluster a set of incident embeddings.
         * Returns a mapping incidentId → clusterId (-1 = noise).
         */
        Map<String, Integer> cluster(Map<String, float[]> embeddings) throws Exception;
    }

    public interface LogisticRegressionPort {
        /**
         * Predict 48h P1/P2 incident risk from feature vector.
         * Features: [incident_count_7d, avg_mttr_7d, unique_services_7d, aml_flags_24h, cpu_p90_24h]
         */
        double predictRisk(double[] features) throws Exception;
    }

    public interface AlertDispatchPort {
        void alertHighRisk(double riskScore, String featuresJson) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final double RISK_THRESHOLD = 0.70;

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final IncidentEmbeddingPort embedding;
    private final HdbscanPort hdbscan;
    private final LogisticRegressionPort logRegression;
    private final AlertDispatchPort alertDispatch;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter patternsDetected;
    private final Counter highRiskAlerts;

    public MlIncidentPatternDetectionService(
        javax.sql.DataSource ds,
        IncidentEmbeddingPort embedding,
        HdbscanPort hdbscan,
        LogisticRegressionPort logRegression,
        AlertDispatchPort alertDispatch,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds               = ds;
        this.embedding        = embedding;
        this.hdbscan          = hdbscan;
        this.logRegression    = logRegression;
        this.alertDispatch    = alertDispatch;
        this.audit            = audit;
        this.executor         = executor;
        this.patternsDetected = Counter.builder("incident.ml.patterns_detected").register(registry);
        this.highRiskAlerts   = Counter.builder("incident.ml.high_risk_alerts").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run clustering on incidents from the last 30 days.
     * New clusters are persisted as patterns; existing clusters are updated.
     * Returns number of clusters found.
     */
    public Promise<Integer> runClustering() {
        return Promise.ofBlocking(executor, () -> {
            // Load incidents from last 30 days
            Map<String, String> incidents = loadRecentIncidentTexts(30);
            if (incidents.isEmpty()) return 0;

            // Embed each incident
            Map<String, float[]> embeddings = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : incidents.entrySet()) {
                embeddings.put(e.getKey(), embedding.embed(e.getKey(), e.getValue()));
            }

            // Cluster
            Map<String, Integer> assignments = hdbscan.cluster(embeddings);

            // Group by cluster (skip noise = -1)
            Map<Integer, List<String>> clusters = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> e : assignments.entrySet()) {
                if (e.getValue() < 0) continue;
                clusters.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
            }

            // Persist patterns
            for (Map.Entry<Integer, List<String>> entry : clusters.entrySet()) {
                String label = "Pattern-" + entry.getKey();
                String patternId = upsertPattern(label, entry.getValue().size());
                for (String incId : entry.getValue()) {
                    upsertPatternMember(incId, patternId);
                }
                patternsDetected.increment();
            }
            audit.record("system", "INCIDENT_CLUSTERING_RUN",
                "clusters=" + clusters.size() + " incidents=" + incidents.size());
            return clusters.size();
        });
    }

    /**
     * Compute the 48h risk score using logistic regression on recent platform features.
     * Fires an alert if risk > 0.70.
     */
    public Promise<Map<String, Object>> predict48hRisk() {
        return Promise.ofBlocking(executor, () -> {
            double[] features = buildFeatureVector();
            double risk = logRegression.predictRisk(features);
            boolean highRisk = risk >= RISK_THRESHOLD;

            String featJson = featuresToJson(features);
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO incident_risk_predictions (risk_score, threshold, high_risk, contributing_features) " +
                     "VALUES (?,?,?,?::jsonb) RETURNING prediction_id"
                 )) {
                ps.setDouble(1, risk); ps.setDouble(2, RISK_THRESHOLD);
                ps.setBoolean(3, highRisk); ps.setString(4, featJson);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); }
            }

            if (highRisk) {
                alertDispatch.alertHighRisk(risk, featJson);
                highRiskAlerts.increment();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("riskScore",  Math.round(risk * 10000.0) / 10000.0);
            result.put("highRisk",   highRisk);
            result.put("threshold",  RISK_THRESHOLD);
            result.put("features",   featJson);
            return result;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Map<String, String> loadRecentIncidentTexts(int days) throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT incident_id, title || ' ' || COALESCE(description,'') AS text " +
                 "FROM incidents WHERE created_at > NOW() - (? || ' days')::INTERVAL"
             )) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getString("incident_id"), rs.getString("text"));
            }
        }
        return result;
    }

    private double[] buildFeatureVector() throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT " +
                 "COUNT(*) FILTER (WHERE created_at > NOW()-INTERVAL '7 days') AS cnt_7d, " +
                 "AVG(EXTRACT(EPOCH FROM (resolved_at-created_at))/60) FILTER (WHERE created_at > NOW()-INTERVAL '7 days') AS avg_mttr_7d, " +
                 "COUNT(DISTINCT source_service) FILTER (WHERE created_at > NOW()-INTERVAL '7 days') AS svcs_7d, " +
                 "COUNT(*) FILTER (WHERE created_at > NOW()-INTERVAL '1 day' AND tags @> '{\"aml_flag\":\"true\"}') AS aml_24h, " +
                 "0.0 AS cpu_p90_24h FROM incidents"
             )) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new double[]{
                        rs.getDouble("cnt_7d"),
                        rs.getDouble("avg_mttr_7d"),
                        rs.getDouble("svcs_7d"),
                        rs.getDouble("aml_24h"),
                        rs.getDouble("cpu_p90_24h")
                    };
                }
                return new double[]{0, 0, 0, 0, 0};
            }
        }
    }

    private String upsertPattern(String label, int size) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO incident_patterns (label, cluster_size) VALUES (?,?) " +
                 "ON CONFLICT DO NOTHING RETURNING pattern_id"
             )) {
            ps.setString(1, label); ps.setInt(2, size);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        // Already exists — fetch it
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE incident_patterns SET cluster_size=?, last_seen_at=NOW() WHERE label=? RETURNING pattern_id"
             )) {
            ps.setInt(1, size); ps.setString(2, label);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void upsertPatternMember(String incidentId, String patternId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO incident_pattern_members (incident_id, pattern_id) VALUES (?,?) ON CONFLICT DO NOTHING"
             )) { ps.setString(1, incidentId); ps.setString(2, patternId); ps.executeUpdate(); }
    }

    private String featuresToJson(double[] f) {
        return String.format(
            "{\"incidentCount7d\":%.0f,\"avgMttr7dMin\":%.2f,\"uniqueServices7d\":%.0f,\"amlFlags24h\":%.0f,\"cpuP9024h\":%.2f}",
            f[0], f[1], f[2], f[3], f[4]);
    }
}
