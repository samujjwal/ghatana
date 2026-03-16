package com.ghatana.appplatform.aigovernance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Generates and stores Model Cards following the Google model card framework.
 *              A card covers: model details (name, version, type, architecture), intended use,
 *              factors (demographic, instrumentation, environmental), metrics (performance by
 *              group), evaluation data, training data, ethical considerations, and caveats.
 *              Cards are auto-updated on model version changes. Exported as structured JSON
 *              (HTML/PDF rendering is the UI layer's responsibility). Satisfies STORY-K09-005.
 * @doc.layer   Kernel
 * @doc.pattern Google model card schema; auto-update on version change; stored as JSONB;
 *              cardsGenerated Counter; ModelRegistryPort for metadata pull.
 */
public class ModelCardGeneratorService {

    private final HikariDataSource   dataSource;
    private final Executor           executor;
    private final ModelRegistryPort  registryPort;
    private final Counter            cardsGeneratedCounter;

    public ModelCardGeneratorService(HikariDataSource dataSource, Executor executor,
                                      ModelRegistryPort registryPort,
                                      MeterRegistry registry) {
        this.dataSource           = dataSource;
        this.executor             = executor;
        this.registryPort         = registryPort;
        this.cardsGeneratedCounter = Counter.builder("aigovernance.modelcard.generated_total").register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    /** Read-only model metadata from ModelRegistryService. */
    public interface ModelRegistryPort {
        Map<String, Object> getModelMetadata(String modelId, String version);
        Map<String, Object> getValidationResults(String modelId, String version);
        Map<String, Object> getTrainingDataSummary(String modelId, String version);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public record ModelCard(
        String cardId, String modelId, String modelVersion,
        Map<String, Object> modelDetails,
        Map<String, Object> intendedUse,
        Map<String, Object> factors,
        Map<String, Object> metrics,
        Map<String, Object> evaluationData,
        Map<String, Object> trainingData,
        Map<String, Object> ethicalConsiderations,
        Map<String, Object> caveats,
        Instant generatedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Generate (or regenerate) a model card for the given model version.
     * Pulls live metadata, validation results, and training summary from the registry.
     */
    public Promise<ModelCard> generateCard(String modelId, String version) {
        return Promise.ofBlocking(executor, () -> {
            String cardId = UUID.randomUUID().toString();
            Instant now   = Instant.now();

            Map<String, Object> meta        = registryPort.getModelMetadata(modelId, version);
            Map<String, Object> validation  = registryPort.getValidationResults(modelId, version);
            Map<String, Object> trainingSummary = registryPort.getTrainingDataSummary(modelId, version);

            Map<String, Object> modelDetails = new LinkedHashMap<>();
            modelDetails.put("modelId",       modelId);
            modelDetails.put("version",       version);
            modelDetails.put("name",          meta.getOrDefault("name", ""));
            modelDetails.put("type",          meta.getOrDefault("model_type", ""));
            modelDetails.put("architecture",  meta.getOrDefault("architecture", ""));
            modelDetails.put("framework",     meta.getOrDefault("framework", ""));
            modelDetails.put("license",       meta.getOrDefault("license", ""));

            Map<String, Object> intendedUse = new LinkedHashMap<>();
            intendedUse.put("primaryUses",     meta.getOrDefault("primary_uses", List.of()));
            intendedUse.put("primaryUsers",    meta.getOrDefault("primary_users", List.of()));
            intendedUse.put("outOfScopeUses",  meta.getOrDefault("out_of_scope_uses", List.of()));

            Map<String, Object> factors = new LinkedHashMap<>();
            factors.put("relevantFactors",     meta.getOrDefault("relevant_factors", List.of()));
            factors.put("evaluationFactors",   meta.getOrDefault("evaluation_factors", List.of()));

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("performanceMetrics",  validation.getOrDefault("metrics", Map.of()));
            metrics.put("decisionThresholds",  validation.getOrDefault("thresholds", Map.of()));
            metrics.put("variationApproaches", validation.getOrDefault("variation", ""));

            Map<String, Object> evalData = new LinkedHashMap<>();
            evalData.put("datasets",  validation.getOrDefault("eval_datasets", List.of()));
            evalData.put("motivation", validation.getOrDefault("motivation", ""));

            Map<String, Object> trainData = new LinkedHashMap<>();
            trainData.put("datasets",   trainingSummary.getOrDefault("datasets", List.of()));
            trainData.put("motivation", trainingSummary.getOrDefault("motivation", ""));
            trainData.put("preprocessing", trainingSummary.getOrDefault("preprocessing", ""));

            Map<String, Object> ethical = new LinkedHashMap<>();
            ethical.put("considerations", meta.getOrDefault("ethical_considerations", List.of()));

            Map<String, Object> caveatsList = new LinkedHashMap<>();
            caveatsList.put("caveats", meta.getOrDefault("caveats", List.of()));

            ModelCard card = new ModelCard(cardId, modelId, version,
                modelDetails, intendedUse, factors, metrics, evalData,
                trainData, ethical, caveatsList, now);

            persistCard(card);
            cardsGeneratedCounter.increment();
            return card;
        });
    }

    /**
     * Retrieve the latest model card for a given model and version.
     */
    public Promise<Optional<Map<String, Object>>> getCard(String modelId, String version) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT card_content FROM model_cards " +
                     "WHERE model_id = ? AND model_version = ? " +
                     "ORDER BY generated_at DESC LIMIT 1")) {
                ps.setString(1, modelId);
                ps.setString(2, version);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    // Return card as opaque map; actual parsing done by caller
                    Map<String, Object> raw = new LinkedHashMap<>();
                    raw.put("content", rs.getString("card_content"));
                    return Optional.of(raw);
                }
            }
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void persistCard(ModelCard card) throws SQLException {
        String json = buildJson(card);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO model_cards (card_id, model_id, model_version, card_content, generated_at) " +
                 "VALUES (?, ?, ?, ?::jsonb, NOW()) " +
                 "ON CONFLICT (model_id, model_version) DO UPDATE SET " +
                 "card_content = EXCLUDED.card_content, generated_at = NOW()")) {
            ps.setString(1, card.cardId());
            ps.setString(2, card.modelId());
            ps.setString(3, card.modelVersion());
            ps.setString(4, json);
            ps.executeUpdate();
        }
    }

    private String buildJson(ModelCard card) {
        // Minimal JSON serialization without external libraries
        return String.format(
            "{\"cardId\":\"%s\",\"modelId\":\"%s\",\"version\":\"%s\",\"generatedAt\":\"%s\"," +
            "\"modelDetails\":%s,\"intendedUse\":%s,\"factors\":%s,\"metrics\":%s," +
            "\"evaluationData\":%s,\"trainingData\":%s,\"ethicalConsiderations\":%s,\"caveats\":%s}",
            card.cardId(), card.modelId(), card.modelVersion(), card.generatedAt(),
            mapToJson(card.modelDetails()), mapToJson(card.intendedUse()),
            mapToJson(card.factors()), mapToJson(card.metrics()),
            mapToJson(card.evaluationData()), mapToJson(card.trainingData()),
            mapToJson(card.ethicalConsiderations()), mapToJson(card.caveats())
        );
    }

    @SuppressWarnings("unchecked")
    private String mapToJson(Map<String, Object> map) {
        if (map == null) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof String s) sb.append("\"").append(s.replace("\"", "\\\"")).append("\"");
            else if (v instanceof Map)  sb.append(mapToJson((Map<String, Object>) v));
            else if (v instanceof List) sb.append("[]");
            else                        sb.append(v);
        }
        sb.append("}");
        return sb.toString();
    }
}
