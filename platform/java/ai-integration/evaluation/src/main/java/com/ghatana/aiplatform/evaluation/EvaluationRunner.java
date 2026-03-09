package com.ghatana.aiplatform.evaluation;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runner for evaluating ML models against golden datasets.
 *
 * <p><b>Purpose</b><br>
 * Validates model performance using ground truth datasets; computes precision, recall, F1,
 * and other quality metrics for deployment decisions.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EvaluationRunner runner = new EvaluationRunner(metricsCollector);
 * GoldenDataset dataset = await(runner.loadGoldenDataset("dataset-123"));
 * EvaluationResult result = await(runner.evaluate(modelTarget, dataset));
 * if (result.metrics.f1Score >= 0.85) {
 *     // Model meets quality threshold
 * }
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe via ConcurrentHashMap for dataset cache and AtomicLong for metrics.
 *
 * <p><b>Tenant Isolation</b><br>
 * All datasets and evaluations scoped to tenant via composite keys: {@code tenant:{id}:golden:{name}}
 *
 * @doc.type class
 * @doc.purpose Model evaluation against golden datasets
 * @doc.layer product
 * @doc.pattern Service
 */
public class EvaluationRunner {

    private final MetricsCollector metricsCollector;
    private final ConcurrentHashMap<String, GoldenDataset> datasetCache;
    private final AtomicLong evaluationCount;

    /**
     * Constructs evaluation runner with metrics collection.
     *
     * @param metricsCollector metrics collector for tracking evaluations
     */
    public EvaluationRunner(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
        this.datasetCache = new ConcurrentHashMap<>();
        this.evaluationCount = new AtomicLong(0L);
    }

    /**
     * Loads golden dataset from storage.
     *
     * GIVEN: Dataset ID and tenant context
     * WHEN: loadGoldenDataset() called
     * THEN: Returns dataset with all ground truth records
     *
     * @param tenantId tenant identifier
     * @param datasetId dataset identifier
     * @return promise of loaded golden dataset
     */
    public Promise<GoldenDataset> loadGoldenDataset(String tenantId, String datasetId) {
        String cacheKey = tenantId + ":golden:" + datasetId;
        return Promise.of(datasetCache.computeIfAbsent(cacheKey, key -> {
            // Load from storage (mock implementation)
            return new GoldenDataset(
                datasetId,
                tenantId,
                List.of(
                    new GroundTruth("record-1", List.of("label-1"), Instant.now()),
                    new GroundTruth("record-2", List.of("label-2"), Instant.now())
                ),
                Instant.now()
            );
        }));
    }

    /**
     * Evaluates model against golden dataset.
     *
     * GIVEN: Model target and golden dataset
     * WHEN: evaluate() called
     * THEN: Returns evaluation result with precision/recall/F1 metrics
     *
     * @param tenantId tenant identifier
     * @param modelTarget target model for evaluation
     * @param dataset golden dataset with ground truth
     * @return promise of evaluation result with metrics
     */
    public Promise<EvaluationResult> evaluate(String tenantId, String modelTarget, GoldenDataset dataset) {
        long startTime = System.currentTimeMillis();
        evaluationCount.incrementAndGet();

        try {
            // Generate predictions (mock)
            List<Prediction> predictions = generatePredictions(modelTarget, dataset);

            // Compute metrics
            EvaluationMetrics metrics = computeMetrics(predictions, dataset.groundTruth());

            // Determine status
            String status = metrics.f1Score() >= 0.80 ? "PASSED" : "WARNING";
            List<String> errors = metrics.f1Score() < 0.75 ? List.of("F1 below threshold") : List.of();

            EvaluationResult result = new EvaluationResult(
                "eval-" + evaluationCount.get(),
                modelTarget,
                metrics,
                status,
                errors
            );

            long duration = System.currentTimeMillis() - startTime;
            metricsCollector.incrementCounter(
                "ai.evaluation.total",
                "tenant", tenantId,
                "status", status
            );
            metricsCollector.recordTimer(
                "ai.evaluation.duration",
                duration,
                "tenant", tenantId,
                "model", modelTarget
            );

            return Promise.of(result);
        } catch (Exception e) {
            metricsCollector.incrementCounter(
                "ai.evaluation.errors",
                "tenant", tenantId
            );
            return Promise.ofException(e);
        }
    }

    /**
     * Computes precision, recall, F1 and other metrics.
     *
     * Formula:
     * - Precision = TP / (TP + FP)
     * - Recall = TP / (TP + FN)
     * - F1 = 2 * (Precision * Recall) / (Precision + Recall)
     *
     * @param predictions model predictions
     * @param groundTruth actual labels
     * @return computed metrics
     */
    public EvaluationMetrics computeMetrics(List<Prediction> predictions, List<GroundTruth> groundTruth) {
        long truePositives = 0L;
        long falsePositives = 0L;
        long falseNegatives = 0L;

        for (int i = 0; i < predictions.size(); i++) {
            Prediction pred = predictions.get(i);
            GroundTruth truth = groundTruth.get(i);

            if (pred.labels().stream().anyMatch(label -> truth.labels().contains(label))) {
                truePositives++;
            } else {
                falsePositives++;
            }

            if (truth.labels().stream().noneMatch(label -> pred.labels().contains(label))) {
                falseNegatives++;
            }
        }

        double precision = truePositives > 0 ? (double) truePositives / (truePositives + falsePositives) : 0.0;
        double recall = truePositives > 0 ? (double) truePositives / (truePositives + falseNegatives) : 0.0;
        double f1 = (precision + recall > 0) ? 2 * (precision * recall) / (precision + recall) : 0.0;
        double accuracy = (double) truePositives / predictions.size();

        return new EvaluationMetrics(
            precision,
            recall,
            f1,
            accuracy,
            new ConfusionMatrix(truePositives, falsePositives, falseNegatives, predictions.size() - truePositives - falsePositives - falseNegatives),
            System.currentTimeMillis()
        );
    }

    private List<Prediction> generatePredictions(String modelTarget, GoldenDataset dataset) {
        return dataset.groundTruth().stream()
            .map(truth -> new Prediction(truth.recordId(), truth.labels(), 0.95))
            .toList();
    }

    // Inner Classes

    /**
     * Golden dataset containing ground truth labels.
     */
    public record GoldenDataset(
        String datasetId,
        String tenantId,
        List<GroundTruth> groundTruth,
        Instant loadedAt
    ) {
    }

    /**
     * Ground truth record with actual labels.
     */
    public record GroundTruth(
        String recordId,
        List<String> labels,
        Instant timestamp
    ) {
    }

    /**
     * Model prediction for a record.
     */
    public record Prediction(
        String recordId,
        List<String> labels,
        double confidence
    ) {
    }

    /**
     * Evaluation result with computed metrics.
     */
    public record EvaluationResult(
        String evaluationId,
        String modelTarget,
        EvaluationMetrics metrics,
        String status,  // PASSED, FAILED, WARNING
        List<String> errors
    ) {
    }

    /**
     * Computed evaluation metrics.
     */
    public record EvaluationMetrics(
        double precision,
        double recall,
        double f1Score,
        double accuracy,
        ConfusionMatrix matrix,
        long computedAt
    ) {
    }

    /**
     * Confusion matrix (TP, FP, FN, TN).
     */
    public record ConfusionMatrix(
        long truePositives,
        long falsePositives,
        long falseNegatives,
        long trueNegatives
    ) {
    }
}
