package com.ghatana.products.finance.domains.risk.service;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.postgresql.util.PGobject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Generates novel AI stress scenarios beyond the static historical set using a
 *              Variational Autoencoder model via inner port (D06-021, K-09 advisory).
 *              All generated scenarios are advisory; risk officer must approve via HITL before
 *              use in regulatory stress reports.
 * @doc.layer   Domain — risk analytics (AI advisory)
 * @doc.pattern K-09 AI Governance — generative model governed by AI registry; HITL required for regulatory use
 */
public class AiStressScenarioService {

    /** Inner port: wraps the VAE generative model (deployed via K-09 AI registry). */
    public interface VaeModelPort {
        /**
         * Generate novei stress scenario vectors from VAE latent space.
         * Each scenario is a map of shock factors (return shocks, vol spikes, correlation breaks).
         * @param count number of scenarios to generate
         * @param seed  fixed seed for reproducibility (null = random)
         * @return list of generated stress scenario feature maps
         */
        List<Map<String, Double>> generateScenarios(int count, Long seed);
    }

    public enum ScenarioStatus { PENDING_REVIEW, APPROVED, REJECTED }

    public record StressScenario(
        String scenarioId,
        String generationRunId,
        Map<String, Double> shockFactors,  // e.g., equity_shock, vol_spike, correlation_break
        double maxShockSigma,              // plausibility guard: must be <= 10σ
        ScenarioStatus status,
        boolean isAdvisory,
        String reviewerId,
        Instant generatedAt
    ) {}

    public record ScenarioGenerationRun(
        String runId,
        int requestedCount,
        int generatedCount,
        int cappedCount,        // beyond 10σ — capped or discarded
        Instant startedAt,
        String modelId
    ) {}

    private static final int DEFAULT_GENERATE_COUNT = 50;
    private static final double MAX_SIGMA = 10.0;

    private final VaeModelPort vaeModel;
    private final DataSource dataSource;
    private final Executor executor;
    private final Counter generatedCounter;
    private final Counter approvedCounter;

    public AiStressScenarioService(VaeModelPort vaeModel, DataSource dataSource,
                                    Executor executor, MeterRegistry registry) {
        this.vaeModel = vaeModel;
        this.dataSource = dataSource;
        this.executor = executor;
        this.generatedCounter = Counter.builder("risk.stress.ai_scenarios_generated_total").register(registry);
        this.approvedCounter  = Counter.builder("risk.stress.ai_scenarios_approved_total").register(registry);
    }

    /**
     * Generate AI stress scenarios for risk officer review (advisory).
     * Scenarios capped at MAX_SIGMA to satisfy K-09 bias check.
     */
    public Promise<ScenarioGenerationRun> generateScenarios() {
        return Promise.ofBlocking(executor, () -> {
            String runId = UUID.randomUUID().toString();
            List<Map<String, Double>> rawScenarios = vaeModel.generateScenarios(DEFAULT_GENERATE_COUNT, null);

            List<StressScenario> valid = new ArrayList<>();
            int cappedCount = 0;
            for (Map<String, Double> shocks : rawScenarios) {
                double maxSigma = shocks.values().stream()
                    .mapToDouble(Math::abs).max().orElse(0.0);
                if (maxSigma > MAX_SIGMA) {
                    cappedCount++;
                    continue;  // discard implausible scenarios
                }
                valid.add(new StressScenario(UUID.randomUUID().toString(), runId, shocks,
                    maxSigma, ScenarioStatus.PENDING_REVIEW, true, null, Instant.now()));
            }

            persistScenarios(runId, valid);
            generatedCounter.increment(valid.size());

            return new ScenarioGenerationRun(runId, DEFAULT_GENERATE_COUNT, valid.size(),
                cappedCount, Instant.now(), "vae-stress-v1");
        });
    }

    /**
     * HITL: risk officer approves a scenario for use in regulatory stress reports.
     * Maker-checker: second approver required for regulatory submissions.
     */
    public Promise<Void> approveScenario(String scenarioId, String reviewerId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE ai_stress_scenarios SET status = 'APPROVED', reviewer_id = ?, reviewed_at = NOW() " +
                     "WHERE id = ? AND status = 'PENDING_REVIEW'")) {
                ps.setObject(1, UUID.fromString(reviewerId));
                ps.setObject(2, UUID.fromString(scenarioId));
                int updated = ps.executeUpdate();
                if (updated == 0) throw new IllegalStateException("Scenario not pending: " + scenarioId);
            }
            approvedCounter.increment();
            return null;
        });
    }

    /** Get all scenarios pending review for the HITL portal. */
    public Promise<List<StressScenario>> getPendingReview(String runId) {
        return Promise.ofBlocking(executor, () -> {
            List<StressScenario> list = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT id, shock_factors, max_shock_sigma, generated_at " +
                     "FROM ai_stress_scenarios WHERE run_id = ? AND status = 'PENDING_REVIEW'")) {
                ps.setObject(1, UUID.fromString(runId));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Double> shocks = deserializeShockFactors(rs.getObject("shock_factors"));
                        list.add(new StressScenario(rs.getString("id"), runId, shocks,
                            rs.getDouble("max_shock_sigma"), ScenarioStatus.PENDING_REVIEW,
                            true, null, rs.getTimestamp("generated_at").toInstant()));
                    }
                }
            }
            return list;
        });
    }

    /**
     * Type-safe deserialization of shock factors from JSONB.
     */
    private Map<String, Double> deserializeShockFactors(Object shockFactorsObj) throws SQLException {
        if (shockFactorsObj == null) {
            return Map.of();
        }
        
        if (shockFactorsObj instanceof PGobject pgObject) {
            String jsonValue = pgObject.getValue();
            return parseShockFactorsJson(jsonValue);
        }
        
        if (shockFactorsObj instanceof Map) {
            // Already deserialized by JDBC driver
            @SuppressWarnings("unchecked")
            Map<String, Object> rawMap = (Map<String, Object>) shockFactorsObj;
            Map<String, Double> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                if (entry.getValue() instanceof Number) {
                    result.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                }
            }
            return result;
        }
        
        return Map.of();
    }

    /**
     * Parse shock factors from JSON string.
     */
    private Map<String, Double> parseShockFactorsJson(String json) {
        Map<String, Double> result = new HashMap<>();
        // Simple JSON parsing for key-value pairs
        // Format: {"key1":value1,"key2":value2}
        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            String content = trimmed.substring(1, trimmed.length() - 1);
            String[] pairs = content.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    String key = kv[0].trim().replace("\"", "");
                    String valueStr = kv[1].trim();
                    try {
                        double value = Double.parseDouble(valueStr);
                        result.put(key, value);
                    } catch (NumberFormatException e) {
                        // Skip invalid values
                    }
                }
            }
        }
        return result;
    }

    private void persistScenarios(String runId, List<StressScenario> scenarios) throws Exception {
        String sql = "INSERT INTO ai_stress_scenarios(id, run_id, shock_factors, max_shock_sigma, " +
                     "status, generated_at) VALUES(?,?,?::jsonb,?,'PENDING_REVIEW',NOW())";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (StressScenario s : scenarios) {
                ps.setObject(1, UUID.fromString(s.scenarioId()));
                ps.setObject(2, UUID.fromString(runId));
                ps.setString(3, toJsonb(s.shockFactors()));
                ps.setDouble(4, s.maxShockSigma());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private String toJsonb(Map<String, Double> map) {
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> sb.append("\"").append(k).append("\":").append(v).append(","));
        if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }
}
