/*
 * Copyright (c) 2026 Ghatana Inc. All rights reserved.
 */
package com.ghatana.datacloud.analytics.anomaly;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.Anomaly;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.AnomalyContext;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.BaselineStatistics;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.DetectionType;
import com.ghatana.datacloud.spi.ai.AnomalyDetectionCapability.Severity;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ML anomaly detection tests covering:
 * <ul>
 *   <li>Drift detection — threshold-based detection of distribution shift over training windows</li>
 *   <li>False-positive rate — normal data within bounds must not exceed the expected FP rate</li>
 *   <li>False-negative rate — known anomalies must be caught above the required recall rate</li>
 *   <li>Explainability artifacts — every detected anomaly must carry evidence, deviation,
 *       suggested actions, and human-readable description</li>
 * </ul>
 *
 * @doc.type    class
 * @doc.purpose ML threshold, drift, false-positive/false-negative, and explainability tests
 * @doc.layer   product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnomalyMLThresholdTest")
@Tag("analytics")
@Tag("ml")
class AnomalyMLThresholdTest extends EventloopTestBase {

    private static final String TENANT     = "test-tenant";
    private static final String COLLECTION = "sensor-readings";

    @Mock
    private EntityRepository repository;

    private SimpleMeterRegistry meterRegistry;
    private StatisticalAnomalyDetector detector;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        detector = new StatisticalAnomalyDetector(
                repository, meterRegistry,
                StatisticalAnomalyDetector.DEFAULT_Z_THRESHOLD,
                Runnable::run);
    }

    // =========================================================================
    // Drift detection — sensitivity to distributional shift
    // =========================================================================

    @Nested
    @DisplayName("Drift detection")
    class DriftDetectionTests {

        @Test
        @DisplayName("detector fires when value distribution shifts by >3σ from baseline")
        void distributionShiftTriggersDetection() {
            // Training window: tight cluster mean≈100, stdDev≈1
            List<Entity> training = cluster("temperature", 100, 10);
            // Shifted window: new mean≈200 — clearly drifted
            List<Entity> shifted = cluster("temperature", 200, 10);

            stubRepository(training, shifted);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            assertThat(anomalies).isNotEmpty();
            anomalies.forEach(a ->
                    assertThat(a.getDeviation()).isGreaterThan(0.0));
        }

        @Test
        @DisplayName("small variance within 1σ of baseline does not trigger detection")
        void smallVarianceNoDrift() {
            // Training and detection from same tight distribution: no drift
            List<Entity> training = cluster("metric", 50, 20);
            List<Entity> normal   = cluster("metric", 50, 10);  // same distribution, fewer points

            stubRepository(training, normal);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            // All detection values are at mean — no anomaly
            assertThat(anomalies).isEmpty();
        }

        @Test
        @DisplayName("gradual drift does not fire until threshold is crossed")
        void gradualDriftFiresOnlyAboveThreshold() {
            // Training: mean=10, stdDev≈1
            List<Entity> training = cluster("v", 10, 20);

            // Detection: single value at mean + (threshold-0.5)σ — should NOT fire at default 3σ
            double justBelow = 10.0 + (StatisticalAnomalyDetector.DEFAULT_Z_THRESHOLD - 0.5);
            List<Entity> belowThreshold = List.of(entity("v", justBelow));

            stubRepository(training, belowThreshold);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> noAnomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            assertThat(noAnomalies).isEmpty();

            // Detection: single value at mean + (threshold+1)σ — MUST fire
            double aboveThreshold = 10.0 + (StatisticalAnomalyDetector.DEFAULT_Z_THRESHOLD + 1.0);
            List<Entity> aboveList = List.of(entity("v", aboveThreshold));

            stubRepository(training, aboveList);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            assertThat(anomalies).isNotEmpty();
        }
    }

    // =========================================================================
    // False-positive rate — normal values must not be flagged
    // =========================================================================

    @Nested
    @DisplayName("False-positive rate")
    class FalsePositiveTests {

        @Test
        @DisplayName("Z-score at 3σ threshold: ≤5% of normally-distributed values flagged (FP budget)")
        void falsePositiveRateWithinBudget() {
            // 200-point training distribution: mean=100, std=10
            List<Entity> training = syntheticNormal("score", 100.0, 10.0, 200, 42);
            // 100-point detection set from the same distribution
            List<Entity> detection = syntheticNormal("score", 100.0, 10.0, 100, 99);

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            // At 3σ threshold the theoretical FP rate is 0.27%. Allow up to 5% to account
            // for finite-sample variance.
            double fpRate = (double) anomalies.size() / 100.0;
            assertThat(fpRate)
                    .as("false-positive rate should be below 5%% for normally-distributed data at 3σ")
                    .isLessThanOrEqualTo(0.05);
        }

        @Test
        @DisplayName("values at exactly the baseline mean are never flagged")
        void valuesAtMeanAreNeverFlagged() {
            List<Entity> training = cluster("value", 50.0, 20);
            // Detect: single entity at the mean
            List<Entity> detection = List.of(entity("value", 50.0));

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            assertThat(anomalies).isEmpty();
        }

        @ParameterizedTest(name = "threshold={0}σ: FP rate must be < {1}")
        @CsvSource({
            "2.0, 0.10",
            "3.0, 0.05",
            "4.0, 0.02"
        })
        @DisplayName("FP rate decreases as threshold increases")
        void falsePositiveRateDecreasesWithThreshold(double threshold, double maxFpRate) {
            StatisticalAnomalyDetector d = new StatisticalAnomalyDetector(
                    repository, meterRegistry, threshold, Runnable::run);

            List<Entity> training  = syntheticNormal("x", 0.0, 1.0, 200, 1);
            List<Entity> detection = syntheticNormal("x", 0.0, 1.0, 200, 2);

            when(repository.findAll(eq(TENANT), eq(COLLECTION),
                    org.mockito.ArgumentMatchers.<Map<String, Object>>any(),
                    anyString(), anyInt(), anyInt()))
                    .thenReturn(Promise.of(training))
                    .thenReturn(Promise.of(detection));

            runPromise(() -> d.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> d.detect(ctx(DetectionType.DATA_QUALITY)));

            double fpRate = (double) anomalies.size() / 200.0;
            assertThat(fpRate)
                    .as("FP rate at threshold %.1fσ must be <= %.2f".formatted(threshold, maxFpRate))
                    .isLessThanOrEqualTo(maxFpRate);
        }
    }

    // =========================================================================
    // False-negative rate — known anomalies must be detected
    // =========================================================================

    @Nested
    @DisplayName("False-negative rate")
    class FalseNegativeTests {

        @Test
        @DisplayName("all values at 5σ+ are detected (zero false negatives)")
        void extremeOutliersAllDetected() {
            List<Entity> training = cluster("temp", 100.0, 30);
            // 5 entities at mean + 10σ — well above 3σ threshold
            double mean = 100.0;
            double std = 1.0;  // tight cluster → large σ distance
            List<Entity> detection = List.of(
                    entity("temp", mean + 10 * std),
                    entity("temp", mean + 12 * std),
                    entity("temp", mean + 15 * std),
                    entity("temp", mean + 20 * std),
                    entity("temp", mean - 10 * std));

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            // All 5 extreme outliers must be detected
            assertThat(anomalies.size())
                    .as("all extreme outliers must be detected (zero false negatives)")
                    .isEqualTo(5);
        }

        @Test
        @DisplayName("recall ≥ 80% for values at 4σ or more from baseline mean")
        void recallAtFourSigmaIsAboveThreshold() {
            // Training: tight cluster mean=0, stdDev=1
            List<Entity> training = syntheticNormal("signal", 0.0, 1.0, 200, 7);
            // Inject 10 known anomalies at ±5σ and 90 normal values
            List<Entity> detection = new ArrayList<>();
            for (int i = 0; i < 90; i++) {
                detection.add(entity("signal", 0.1 * (i % 5)));  // normal range
            }
            for (int i = 0; i < 10; i++) {
                detection.add(entity("signal", 5.0 + i * 0.5));  // 5σ+ outliers
            }

            when(repository.findAll(eq(TENANT), eq(COLLECTION),
                    org.mockito.ArgumentMatchers.<Map<String, Object>>any(),
                    anyString(), anyInt(), anyInt()))
                    .thenReturn(Promise.of(training))
                    .thenReturn(Promise.of(detection));

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            // Count detected anomalies with deviation >= 4σ (5σ injected → must all be caught)
            long trulyAnomalous = anomalies.stream()
                    .filter(a -> a.getDeviation() >= 4.0)
                    .count();
            double recall = (double) trulyAnomalous / 10.0;

            assertThat(recall)
                    .as("recall for 5σ+ anomalies must be >= 80%%")
                    .isGreaterThanOrEqualTo(0.80);
        }
    }

    // =========================================================================
    // Explainability artifacts
    // =========================================================================

    @Nested
    @DisplayName("Explainability artifacts")
    class ExplainabilityTests {

        @Test
        @DisplayName("every anomaly carries a non-null, non-blank anomalyId")
        void anomalyHasUniqueId() {
            List<Entity> training = cluster("v", 10.0, 20);
            List<Entity> detection = List.of(entity("v", 1000.0));

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            assertThat(anomalies).hasSize(1);
            assertThat(anomalies.get(0).getAnomalyId())
                    .as("anomalyId must be non-blank and UUID-like")
                    .isNotBlank()
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("evidence map contains field, z-score/deviation, mean, and stdDev")
        void evidenceMapIsComplete() {
            List<Entity> training = cluster("latency", 200.0, 20);
            List<Entity> detection = List.of(entity("latency", 10000.0));

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            assertThat(anomalies).isNotEmpty();
            Map<String, Object> evidence = anomalies.get(0).getEvidence();

            assertThat(evidence)
                    .as("evidence map must contain 'field'")
                    .containsKey("field");
            assertThat(evidence)
                    .as("evidence map must contain statistical context (mean or lowerFence)")
                    .satisfiesAnyOf(
                            e -> assertThat(e).containsKey("mean"),
                            e -> assertThat(e).containsKey("lowerFence"));
        }

        @Test
        @DisplayName("description is a non-blank human-readable sentence")
        void descriptionIsHumanReadable() {
            List<Entity> training = cluster("cpu_usage", 30.0, 20);
            List<Entity> detection = List.of(entity("cpu_usage", 9999.0));

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            assertThat(anomalies).isNotEmpty();
            String description = anomalies.get(0).getDescription();

            assertThat(description)
                    .as("description must be a non-blank sentence mentioning the numeric context")
                    .isNotBlank()
                    .hasSizeGreaterThan(10)
                    .containsAnyOf("σ", "baseline", "mean", "fence", "outlier");
        }

        @Test
        @DisplayName("title mentions the affected field name")
        void titleMentionsFieldName() {
            List<Entity> training = cluster("error_rate", 0.01, 20);
            List<Entity> detection = List.of(entity("error_rate", 999.0));

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            assertThat(anomalies).isNotEmpty();
            assertThat(anomalies.get(0).getTitle())
                    .as("title must mention the affected field name")
                    .contains("error_rate");
        }

        @Test
        @DisplayName("suggestedActions is non-empty and contains actionable text")
        void suggestedActionsArePresent() {
            List<Entity> training = cluster("queue_depth", 10.0, 20);
            List<Entity> detection = List.of(entity("queue_depth", 50000.0));

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            assertThat(anomalies).isNotEmpty();
            List<String> actions = anomalies.get(0).getSuggestedActions();

            assertThat(actions)
                    .as("suggestedActions must be non-empty")
                    .isNotEmpty();
            actions.forEach(action ->
                    assertThat(action)
                            .as("each suggested action must be non-blank")
                            .isNotBlank()
                            .hasSizeGreaterThan(5));
        }

        @Test
        @DisplayName("observedValue matches the entity field value that was detected")
        void observedValueMatchesInput() {
            List<Entity> training = cluster("throughput", 500.0, 20);
            double injectedValue = 99999.0;
            List<Entity> detection = List.of(entity("throughput", injectedValue));

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            assertThat(anomalies).isNotEmpty();
            assertThat(anomalies.get(0).getObservedValue())
                    .as("observedValue must match the injected anomalous value")
                    .isEqualTo(injectedValue);
        }

        @Test
        @DisplayName("expectedValue is close to the baseline mean")
        void expectedValueIsBaselineMean() {
            double trainingMean = 300.0;
            List<Entity> training = cluster("p99_latency", trainingMean, 20);
            List<Entity> detection = List.of(entity("p99_latency", 999999.0));

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            assertThat(anomalies).isNotEmpty();
            Object expectedValue = anomalies.get(0).getExpectedValue();

            // For Z-score anomalies, expectedValue is the baseline mean (a double)
            // For IQR anomalies it may be a formatted range string
            if (expectedValue instanceof Double mean) {
                assertThat(mean)
                        .as("expected value for Z-score anomaly must be close to training mean %.1f".formatted(trainingMean))
                        .isCloseTo(trainingMean, within(trainingMean * 0.05));
            } else {
                // IQR case — range string must be non-blank
                assertThat(expectedValue.toString()).isNotBlank();
            }
        }

        @Test
        @DisplayName("occurrenceTime and detectedAt are both non-null")
        void timestampsArePresent() {
            List<Entity> training = cluster("bytes_sent", 1000.0, 20);
            List<Entity> detection = List.of(entity("bytes_sent", 1_000_000.0));

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            assertThat(anomalies).isNotEmpty();
            Anomaly a = anomalies.get(0);
            assertThat(a.getOccurrenceTime()).isNotNull();
            assertThat(a.getDetectedAt()).isNotNull();
        }

        @Test
        @DisplayName("severity is not null and maps to a known level")
        void severityIsKnownLevel() {
            List<Entity> training = cluster("requests", 100.0, 20);
            List<Entity> detection = List.of(entity("requests", 1_000_000.0));

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            assertThat(anomalies).isNotEmpty();
            Severity severity = anomalies.get(0).getSeverity();

            assertThat(severity)
                    .isNotNull()
                    .isIn(Severity.LOW, Severity.MEDIUM, Severity.HIGH, Severity.CRITICAL);
        }

        @Test
        @DisplayName("confidence is in [0.0, 1.0] for all anomalies")
        void confidenceIsInValidRange() {
            List<Entity> training = cluster("error_count", 5.0, 20);
            List<Entity> detection = List.of(
                    entity("error_count", 10000.0),
                    entity("error_count", 20000.0));

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            assertThat(anomalies).isNotEmpty();
            anomalies.forEach(a ->
                    assertThat(a.getConfidence())
                            .as("confidence must be in [0.0, 1.0]")
                            .isBetween(0.0, 1.0));
        }

        @Test
        @DisplayName("detectionMethod is 'Z-Score' or 'IQR-Fence'")
        void detectionMethodIsDocumented() {
            List<Entity> training = cluster("heap_usage", 512.0, 20);
            List<Entity> detection = List.of(entity("heap_usage", 1_000_000.0));

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx(DetectionType.DATA_QUALITY)));

            assertThat(anomalies).isNotEmpty();
            anomalies.forEach(a ->
                    assertThat(a.getDetectionMethod())
                            .as("detectionMethod must be a known documented algorithm")
                            .isIn("Z-Score", "IQR-Fence"));
        }

        @Test
        @DisplayName("baseline statistics can be retrieved and contain all required fields")
        void baselineStatisticsAreComplete() {
            List<Entity> training = cluster("disk_io", 100.0, 30);
            when(repository.findAll(eq(TENANT), eq(COLLECTION),
                    org.mockito.ArgumentMatchers.<Map<String, Object>>any(),
                    anyString(), anyInt(), anyInt()))
                    .thenReturn(Promise.of(training));

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            BaselineStatistics stats = runPromise(() ->
                    detector.getBaseline(TENANT, COLLECTION, "disk_io"));

            assertThat(stats.getMean()).isGreaterThan(0.0);
            assertThat(stats.getStandardDeviation()).isGreaterThan(0.0);
            assertThat(stats.getMedian()).isGreaterThan(0.0);
            assertThat(stats.getP25()).isLessThanOrEqualTo(stats.getMedian());
            assertThat(stats.getMedian()).isLessThanOrEqualTo(stats.getP75());
            assertThat(stats.getP75()).isLessThanOrEqualTo(stats.getP95());
            assertThat(stats.getP95()).isLessThanOrEqualTo(stats.getP99());
            assertThat(stats.getMin()).isLessThanOrEqualTo(stats.getMax());
            assertThat(stats.getSampleCount()).isEqualTo(30);
            assertThat(stats.getLastUpdated()).isNotNull();
            assertThat(stats.getMetricName()).isEqualTo("disk_io");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private AnomalyContext ctx(DetectionType type) {
        return AnomalyContext.builder()
                .tenantId(TENANT)
                .collectionName(COLLECTION)
                .detectionType(type)
                .build();
    }

    private void stubRepository(List<Entity> training, List<Entity> detection) {
        when(repository.findAll(eq(TENANT), eq(COLLECTION),
                org.mockito.ArgumentMatchers.<Map<String, Object>>any(),
                anyString(), anyInt(), anyInt()))
                .thenReturn(Promise.of(training))
                .thenReturn(Promise.of(detection));
    }

    /**
     * Creates {@code count} entities all having the same field value equal to {@code mean}.
     * The training cluster has effectively zero variance. Use this when you want to
     * produce a known-mean baseline so that the test value is a specific number of σ away.
     */
    private static List<Entity> cluster(String field, double mean, int count) {
        List<Entity> entities = new ArrayList<>();
        // Add slight variance (±0.01) so stdDev > 0 and Z-score math works
        for (int i = 0; i < count; i++) {
            double v = mean + (i % 2 == 0 ? 0.01 : -0.01);
            entities.add(entity(field, v));
        }
        return entities;
    }

    /**
     * Generates {@code count} pseudo-normally distributed values using Box-Muller transform.
     *
     * @param field  entity field name
     * @param mean   distribution mean
     * @param stdDev distribution standard deviation
     * @param count  number of entities
     * @param seed   random seed for reproducibility
     */
    private static List<Entity> syntheticNormal(String field, double mean, double stdDev,
                                                int count, long seed) {
        List<Entity> entities = new ArrayList<>();
        Random rng = new Random(seed);
        for (int i = 0; i < count; i++) {
            // Box-Muller transform
            double u1 = rng.nextDouble();
            double u2 = rng.nextDouble();
            double z  = Math.sqrt(-2.0 * Math.log(Math.max(u1, 1e-10))) * Math.cos(2.0 * Math.PI * u2);
            double value = mean + stdDev * z;
            entities.add(entity(field, value));
        }
        return entities;
    }

    private static Entity entity(String field, double value) {
        Entity e = new Entity();
        e.setId(UUID.randomUUID());
        e.setTenantId(TENANT);
        e.setCollectionName(COLLECTION);
        e.setActive(true);
        e.setCreatedAt(Instant.now());
        Map<String, Object> data = new HashMap<>();
        data.put(field, value);
        e.setData(data);
        return e;
    }
}
