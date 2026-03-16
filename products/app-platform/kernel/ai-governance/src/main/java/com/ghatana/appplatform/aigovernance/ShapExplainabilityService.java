package com.ghatana.appplatform.aigovernance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Computes and persists SHAP (SHapley Additive exPlanations) values for
 *              ML model predictions. Supports TreeSHAP (tree-based models), KernelSHAP
 *              (model-agnostic), and DeepSHAP (neural networks) via an inner ShapEnginePort.
 *              Stores per-prediction local explanation (waterfall data) and aggregated
 *              global feature importance. Explanations are retrievable for K-07 audit.
 *              Satisfies STORY-K09-004.
 * @doc.layer   Kernel
 * @doc.pattern ShapEnginePort strategy; per-prediction caching; audit-ready explanation
 *              store; ON CONFLICT DO NOTHING; Timer instrumentation.
 */
public class ShapExplainabilityService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ShapEnginePort   shapEngine;
    private final Counter          explanationsCounter;
    private final Timer            computeTimer;

    public ShapExplainabilityService(HikariDataSource dataSource, Executor executor,
                                      ShapEnginePort shapEngine, MeterRegistry registry) {
        this.dataSource          = dataSource;
        this.executor            = executor;
        this.shapEngine          = shapEngine;
        this.explanationsCounter = Counter.builder("ai.shap.explanations_total").register(registry);
        this.computeTimer        = Timer.builder("ai.shap.compute_duration").register(registry);
    }

    // ─── Inner ports / records ────────────────────────────────────────────────

    public interface ShapEnginePort {
        /** Compute per-feature SHAP values for a single prediction. */
        Map<String, Double> computeLocal(String modelId, Map<String, Object> inputFeatures,
                                          ShapMethod method);
        /** Compute aggregate feature importance from a batch of samples. */
        Map<String, Double> computeGlobal(String modelId, List<Map<String, Object>> samples,
                                           ShapMethod method);
    }

    public enum ShapMethod { TREE_SHAP, KERNEL_SHAP, DEEP_SHAP }

    public record LocalExplanation(String explanationId, String predictionId, String modelId,
                                    Map<String, Double> shapValues, double baseValue,
                                    ShapMethod method, LocalDateTime computedAt) {}

    public record GlobalFeatureImportance(String importanceId, String modelId,
                                           Map<String, Double> meanAbsShap, int sampleSize,
                                           ShapMethod method, LocalDateTime computedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<LocalExplanation> explainPrediction(String predictionId, String modelId,
                                                        Map<String, Object> inputFeatures,
                                                        ShapMethod method) {
        return Promise.ofBlocking(executor, () -> computeTimer.recordCallable(() -> {
            Map<String, Double> shapValues = shapEngine.computeLocal(modelId, inputFeatures, method);
            double baseValue = shapValues.getOrDefault("_base_value", 0.0);
            shapValues.remove("_base_value");

            String explanationId = UUID.randomUUID().toString();
            persistLocalExplanation(explanationId, predictionId, modelId, shapValues, baseValue, method);
            explanationsCounter.increment();

            return new LocalExplanation(explanationId, predictionId, modelId,
                    shapValues, baseValue, method, LocalDateTime.now());
        }));
    }

    public Promise<GlobalFeatureImportance> computeGlobalImportance(String modelId,
                                                                      List<Map<String, Object>> samples,
                                                                      ShapMethod method) {
        return Promise.ofBlocking(executor, () -> {
            Map<String, Double> meanAbsShap = shapEngine.computeGlobal(modelId, samples, method);
            String importanceId = UUID.randomUUID().toString();
            persistGlobalImportance(importanceId, modelId, meanAbsShap, samples.size(), method);
            return new GlobalFeatureImportance(importanceId, modelId, meanAbsShap,
                    samples.size(), method, LocalDateTime.now());
        });
    }

    public Promise<LocalExplanation> getExplanationForPrediction(String predictionId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT * FROM prediction_explanations WHERE prediction_id=?")) {
                ps.setString(1, predictionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    return mapLocalRow(rs);
                }
            }
        });
    }

    public Promise<GlobalFeatureImportance> getLatestGlobalImportance(String modelId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT * FROM global_feature_importances WHERE model_id=? " +
                         "ORDER BY computed_at DESC LIMIT 1")) {
                ps.setString(1, modelId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    return mapGlobalRow(rs);
                }
            }
        });
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private void persistLocalExplanation(String explanationId, String predictionId,
                                          String modelId, Map<String, Double> shapValues,
                                          double baseValue, ShapMethod method) throws SQLException {
        String sql = """
                INSERT INTO prediction_explanations
                    (explanation_id, prediction_id, model_id, shap_values, base_value,
                     shap_method, computed_at)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, NOW())
                ON CONFLICT (prediction_id) DO NOTHING
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, explanationId); ps.setString(2, predictionId);
            ps.setString(3, modelId); ps.setString(4, toJson(shapValues));
            ps.setDouble(5, baseValue); ps.setString(6, method.name());
            ps.executeUpdate();
        }
    }

    private void persistGlobalImportance(String importanceId, String modelId,
                                          Map<String, Double> meanAbsShap, int sampleSize,
                                          ShapMethod method) throws SQLException {
        String sql = """
                INSERT INTO global_feature_importances
                    (importance_id, model_id, mean_abs_shap, sample_size, shap_method, computed_at)
                VALUES (?, ?, ?::jsonb, ?, ?, NOW())
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, importanceId); ps.setString(2, modelId);
            ps.setString(3, toJson(meanAbsShap)); ps.setInt(4, sampleSize);
            ps.setString(5, method.name());
            ps.executeUpdate();
        }
    }

    @SuppressWarnings("unchecked")
    private LocalExplanation mapLocalRow(ResultSet rs) throws SQLException {
        return new LocalExplanation(rs.getString("explanation_id"),
                rs.getString("prediction_id"), rs.getString("model_id"),
                parseJsonDoubleMap(rs.getString("shap_values")),
                rs.getDouble("base_value"),
                ShapMethod.valueOf(rs.getString("shap_method")),
                rs.getObject("computed_at", LocalDateTime.class));
    }

    private GlobalFeatureImportance mapGlobalRow(ResultSet rs) throws SQLException {
        return new GlobalFeatureImportance(rs.getString("importance_id"),
                rs.getString("model_id"),
                parseJsonDoubleMap(rs.getString("mean_abs_shap")),
                rs.getInt("sample_size"),
                ShapMethod.valueOf(rs.getString("shap_method")),
                rs.getObject("computed_at", LocalDateTime.class));
    }

    /** Minimal JSON serialiser for Map<String,Double> — no external dep required. */
    private String toJson(Map<String, Double> map) {
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> sb.append('"').append(k).append('"').append(':').append(v).append(','));
        if (sb.length() > 1) sb.setLength(sb.length() - 1);
        sb.append('}');
        return sb.toString();
    }

    /** Very small JSON Object → Map<String,Double> parser (keys/values only, no nesting). */
    private Map<String, Double> parseJsonDoubleMap(String json) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return result;
        String inner = json.strip().replaceAll("^\\{|\\}$", "");
        for (String pair : inner.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].strip().replaceAll("\"", "");
                result.put(key, Double.parseDouble(kv[1].strip()));
            }
        }
        return result;
    }
}
