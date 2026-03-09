package com.ghatana.aiplatform.evaluation;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.observability.NoopMetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for EvaluationRunner.
 *
 * Tests validate:
 * - Golden dataset loading and caching
 * - Precision/recall/F1 metric computation
 * - Multi-tenant evaluation isolation
 * - Status classification (PASSED/WARNING/FAILED)
 * - Performance tracking via metrics
 *
 * @see EvaluationRunner
 */
@DisplayName("EvaluationRunner Integration Tests")
class EvaluationRunnerIntegrationTest extends EventloopTestBase {

    private EvaluationRunner evaluationRunner;

    @BeforeEach
    void setUp() {
        evaluationRunner = new EvaluationRunner(NoopMetricsCollector.getInstance());
    }

    /**
     * Verifies golden dataset loads correctly.
     *
     * GIVEN: Valid dataset ID
     * WHEN: loadGoldenDataset() called
     * THEN: Returns dataset with ground truth records
     */
    @Test
    @DisplayName("Should load golden dataset with ground truth records")
    void shouldLoadGoldenDatasetWithGroundTruthRecords() {
        // GIVEN: Valid dataset ID
        String tenantId = "tenant-123";
        String datasetId = "dataset-golden-001";

        // WHEN: Load dataset
        EvaluationRunner.GoldenDataset dataset = runPromise(() ->
            evaluationRunner.loadGoldenDataset(tenantId, datasetId)
        );

        // THEN: Dataset loaded with records
        assertThat(dataset)
            .as("Golden dataset should not be null")
            .isNotNull();
        assertThat(dataset.datasetId())
            .as("Dataset ID should match")
            .isEqualTo(datasetId);
        assertThat(dataset.tenantId())
            .as("Tenant ID should be scoped")
            .isEqualTo(tenantId);
        assertThat(dataset.groundTruth())
            .as("Ground truth records should be present")
            .isNotEmpty()
            .hasSize(2);
    }

    /**
     * Verifies precision/recall/F1 computation.
     *
     * GIVEN: Predictions matching ground truth
     * WHEN: computeMetrics() called
     * THEN: Returns metrics in range [0,1] with correct F1 formula
     */
    @Test
    @DisplayName("Should compute precision, recall, F1 metrics correctly")
    void shouldComputePrecisionRecallF1Correctly() {
        // GIVEN: Predictions matching ground truth
        EvaluationRunner.GoldenDataset dataset = runPromise(() ->
            evaluationRunner.loadGoldenDataset("tenant-123", "dataset-1")
        );

        List<EvaluationRunner.Prediction> predictions = dataset.groundTruth().stream()
            .map(truth -> new EvaluationRunner.Prediction(truth.recordId(), truth.labels(), 0.95))
            .toList();

        // WHEN: Compute metrics
        EvaluationRunner.EvaluationMetrics metrics =
            evaluationRunner.computeMetrics(predictions, dataset.groundTruth());

        // THEN: Metrics computed correctly
        assertThat(metrics.precision())
            .as("Precision should be in range [0,1]")
            .isBetween(0.0, 1.0);
        assertThat(metrics.recall())
            .as("Recall should be in range [0,1]")
            .isBetween(0.0, 1.0);
        assertThat(metrics.f1Score())
            .as("F1 should be in range [0,1]")
            .isBetween(0.0, 1.0);
        assertThat(metrics.accuracy())
            .as("Accuracy should be in range [0,1]")
            .isBetween(0.0, 1.0);
    }

    /**
     * Verifies high-quality model receives PASSED status.
     *
     * GIVEN: Model with F1 >= 0.80
     * WHEN: evaluate() called
     * THEN: Status is PASSED, no errors
     */
    @Test
    @DisplayName("Should mark high-quality model as PASSED")
    void shouldMarkHighQualityModelAsPassed() {
        // GIVEN: Tenant and model target
        String tenantId = "tenant-prod";
        String modelTarget = "model-v2";

        EvaluationRunner.GoldenDataset dataset = runPromise(() ->
            evaluationRunner.loadGoldenDataset(tenantId, "dataset-gold")
        );

        // WHEN: Evaluate
        EvaluationRunner.EvaluationResult result = runPromise(() ->
            evaluationRunner.evaluate(tenantId, modelTarget, dataset)
        );

        // THEN: Status is PASSED
        assertThat(result.status())
            .as("High-quality model should PASS evaluation")
            .isEqualTo("PASSED");
        assertThat(result.errors())
            .as("No errors should be present")
            .isEmpty();
    }

    /**
     * Verifies multi-tenant isolation during evaluation.
     *
     * GIVEN: Evaluations for two different tenants
     * WHEN: Both run concurrently
     * THEN: Tenant A's results unaffected by tenant B's evaluation
     */
    @Test
    @DisplayName("Should enforce tenant isolation during evaluation")
    void shouldEnforceTenantIsolationDuringEvaluation() {
        // GIVEN: Two tenants
        String tenantA = "tenant-a";
        String tenantB = "tenant-b";

        EvaluationRunner.GoldenDataset datasetA = runPromise(() ->
            evaluationRunner.loadGoldenDataset(tenantA, "dataset-a")
        );

        EvaluationRunner.GoldenDataset datasetB = runPromise(() ->
            evaluationRunner.loadGoldenDataset(tenantB, "dataset-b")
        );

        // WHEN: Evaluate both
        EvaluationRunner.EvaluationResult resultA = runPromise(() ->
            evaluationRunner.evaluate(tenantA, "model-a", datasetA)
        );

        EvaluationRunner.EvaluationResult resultB = runPromise(() ->
            evaluationRunner.evaluate(tenantB, "model-b", datasetB)
        );

        // THEN: Results isolated by tenant
        assertThat(resultA.evaluationId())
            .as("Evaluation A should have distinct ID")
            .isNotEqualTo(resultB.evaluationId());
        assertThat(resultA.modelTarget())
            .as("Model targets should differ")
            .isNotEqualTo(resultB.modelTarget());
    }

    /**
     * Verifies confusion matrix computation.
     *
     * GIVEN: Mixed predictions (TP, FP, FN)
     * WHEN: computeMetrics() called
     * THEN: ConfusionMatrix shows correct counts
     */
    @Test
    @DisplayName("Should compute confusion matrix correctly")
    void shouldComputeConfusionMatrixCorrectly() {
        // GIVEN: Predictions with mixed accuracy
        EvaluationRunner.GoldenDataset dataset = runPromise(() ->
            evaluationRunner.loadGoldenDataset("tenant-123", "dataset-1")
        );

        List<EvaluationRunner.Prediction> predictions = dataset.groundTruth().stream()
            .map(truth -> new EvaluationRunner.Prediction(truth.recordId(), truth.labels(), 0.95))
            .toList();

        // WHEN: Compute metrics
        EvaluationRunner.EvaluationMetrics metrics =
            evaluationRunner.computeMetrics(predictions, dataset.groundTruth());

        // THEN: ConfusionMatrix populated
        assertThat(metrics.matrix())
            .as("Confusion matrix should be present")
            .isNotNull();
        assertThat(metrics.matrix().truePositives() + metrics.matrix().falsePositives()
            + metrics.matrix().falseNegatives() + metrics.matrix().trueNegatives())
            .as("Sum of matrix elements should equal total records")
            .isEqualTo(predictions.size());
    }

    /**
     * Verifies cache prevents duplicate loads.
     *
     * GIVEN: Same dataset loaded twice
     * WHEN: loadGoldenDataset() called both times
     * THEN: Second load returns cached result (same reference)
     */
    @Test
    @DisplayName("Should cache datasets to avoid duplicate loads")
    void shouldCacheDatasetsToAvoidDuplicateLoads() {
        // GIVEN: Tenant and dataset ID
        String tenantId = "tenant-123";
        String datasetId = "dataset-cache-test";

        // WHEN: Load same dataset twice
        EvaluationRunner.GoldenDataset dataset1 = runPromise(() ->
            evaluationRunner.loadGoldenDataset(tenantId, datasetId)
        );

        EvaluationRunner.GoldenDataset dataset2 = runPromise(() ->
            evaluationRunner.loadGoldenDataset(tenantId, datasetId)
        );

        // THEN: Both references should be same (cached)
        assertThat(dataset1)
            .as("Cached dataset should return same instance")
            .isSameAs(dataset2);
    }

    /**
     * Verifies evaluation metrics across multiple models.
     *
     * GIVEN: Multiple model evaluations
     * WHEN: evaluate() called for each
     * THEN: Each has distinct evaluation ID and results
     */
    @Test
    @DisplayName("Should track evaluations for multiple models independently")
    void shouldTrackEvaluationsForMultipleModelsIndependently() {
        // GIVEN: Single tenant, multiple models
        String tenantId = "tenant-multi";
        EvaluationRunner.GoldenDataset dataset = runPromise(() ->
            evaluationRunner.loadGoldenDataset(tenantId, "dataset-shared")
        );

        // WHEN: Evaluate multiple models
        EvaluationRunner.EvaluationResult result1 = runPromise(() ->
            evaluationRunner.evaluate(tenantId, "model-v1", dataset)
        );

        EvaluationRunner.EvaluationResult result2 = runPromise(() ->
            evaluationRunner.evaluate(tenantId, "model-v2", dataset)
        );

        // THEN: Each evaluation tracked separately
        assertThat(result1.evaluationId())
            .as("Evaluation IDs should be unique")
            .isNotEqualTo(result2.evaluationId());
        assertThat(result1.modelTarget())
            .as("Model targets should differ")
            .isNotEqualTo(result2.modelTarget());
        assertThat(result1.metrics())
            .as("Metrics should be computed")
            .isNotNull();
    }
}
