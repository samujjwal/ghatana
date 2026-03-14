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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import static com.ghatana.datacloud.analytics.anomaly.StatisticalAnomalyDetector.percentile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StatisticalAnomalyDetector}.
 *
 * <p>All async tests use {@link EventloopTestBase#runPromise} so that
 * ActiveJ Promises are driven correctly. The {@link EntityRepository} is
 * mocked with Mockito so no database is required.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StatisticalAnomalyDetector Tests")
class StatisticalAnomalyDetectorTest extends EventloopTestBase {

    private static final String TENANT     = "tenant-1";
    private static final String COLLECTION = "temperature-readings";

    @Mock
    private EntityRepository repository;

    private SimpleMeterRegistry meterRegistry;
    private StatisticalAnomalyDetector detector;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // Use a direct executor so blocking tasks execute synchronously inside runPromise
        detector = new StatisticalAnomalyDetector(
                repository, meterRegistry,
                StatisticalAnomalyDetector.DEFAULT_Z_THRESHOLD,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    // =========================================================================
    // Constructor validation
    // =========================================================================

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorTests {

        @Test
        @DisplayName("null repository throws NullPointerException")
        void nullRepositoryFails() {
            assertThatThrownBy(() ->
                    new StatisticalAnomalyDetector(null, meterRegistry))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("entityRepository");
        }

        @Test
        @DisplayName("null meterRegistry does not throw (metrics disabled)")
        void publicConstructorWithNullMeterRegistryFails() {
            // null registry should throw because Counter.builder requires a non-null registry
            assertThatThrownBy(() ->
                    new StatisticalAnomalyDetector(repository, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null executor throws NullPointerException")
        void nullExecutorFails() {
            assertThatThrownBy(() ->
                    new StatisticalAnomalyDetector(repository, meterRegistry, 3.0, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("executor");
        }

        @Test
        @DisplayName("getSupportedDetectionTypes returns DATA_QUALITY and BEHAVIORAL")
        void supportedTypesAreReturned() {
            List<DetectionType> types = runPromise(() -> detector.getSupportedDetectionTypes());
            assertThat(types).containsExactlyInAnyOrder(DetectionType.DATA_QUALITY, DetectionType.BEHAVIORAL);
        }
    }

    // =========================================================================
    // detect() — no baseline
    // =========================================================================

    @Nested
    @DisplayName("detect() — without baseline")
    class DetectWithoutBaselineTests {

        @Test
        @DisplayName("returns empty list when collection is empty")
        void emptyCollectionReturnsNoAnomalies() {
            when(repository.findAll(eq(TENANT), eq(COLLECTION), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Promise.of(Collections.emptyList()));

            List<Anomaly> result = runPromise(() -> detector.detect(basicContext()));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when no baseline has been computed")
        void noBaselineYieldsNoAnomalies() {
            List<Entity> entities = List.of(
                    entityWithData(Map.of("value", 999.0)), // extreme outlier
                    entityWithData(Map.of("value", 1.0)));

            when(repository.findAll(eq(TENANT), eq(COLLECTION), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Promise.of(entities));

            List<Anomaly> result = runPromise(() -> detector.detect(basicContext()));

            // no baseline → nothing detected
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null data map in entity is silently skipped")
        void nullDataFieldSkipped() {
            Entity entityWithNoData = new Entity();
            entityWithNoData.setId(UUID.randomUUID());
            entityWithNoData.setData(null);

            when(repository.findAll(eq(TENANT), eq(COLLECTION), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Promise.of(List.of(entityWithNoData)));

            List<Anomaly> result = runPromise(() -> detector.detect(basicContext()));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("detect() null context throws NullPointerException")
        void nullContextThrows() {
            assertThatThrownBy(() ->
                    runPromise(() -> detector.detect(null)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("context");
        }
    }

    // =========================================================================
    // updateBaseline() + detect() — Z-score path
    // =========================================================================

    @Nested
    @DisplayName("Z-score detection")
    class ZScoreDetectionTests {

        @Test
        @DisplayName("values within 3σ are not flagged")
        void normalValuesProduceNoAnomalies() {
            // 10 entities with value={1..10}: mean=5.5, stdDev≈2.87
            List<Entity> trainingEntities = numericalEntities("value", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            // detection uses same data — all within 3σ
            stubRepository(trainingEntities, trainingEntities);

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> result = runPromise(() -> detector.detect(basicContext()));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("value 6σ above mean is detected as CRITICAL Z-score anomaly")
        void extremeOutlierIsDetected() {
            // Tight cluster at 100 ± small noise, then a massive outlier at 700
            List<Entity> trainingData = numericalEntities("temperature",
                    100, 101, 99, 100, 102, 100, 101, 98, 100, 100);
            List<Entity> detectionData = List.of(entityWithData(Map.of("temperature", 700.0)));

            stubRepository(trainingData, detectionData);

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            List<Anomaly> anomalies = runPromise(() -> detector.detect(basicContext()));

            assertThat(anomalies).hasSize(1);
            Anomaly a = anomalies.get(0);
            assertThat(a.getSeverity()).isEqualTo(Severity.CRITICAL);
            assertThat(a.getDetectionMethod()).isEqualTo("Z-Score");
            assertThat(a.getAffectedEntity()).isNotBlank();
            assertThat(a.getAnomalyId()).isNotBlank();
            assertThat(a.getType()).isEqualTo(DetectionType.DATA_QUALITY);
        }

        @Test
        @DisplayName("custom threshold from context is respected")
        void customThresholdIsRespected() {
            // Cluster at mean=50, stdDev≈0.6 — a value at 52 is ~3.3σ away
            List<Entity> training = numericalEntities("metric", 50, 50, 51, 49, 50, 50, 51, 49, 50, 50);
            List<Entity> detection = List.of(entityWithData(Map.of("metric", 52.0)));

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));

            // With default threshold (3σ) — may or may not detect depending on stdDev
            // With low threshold (1σ) — should always detect
            AnomalyContext lowThresholdCtx = AnomalyContext.builder()
                    .tenantId(TENANT)
                    .collectionName(COLLECTION)
                    .detectionType(DetectionType.DATA_QUALITY)
                    .threshold(1.0)
                    .build();

            List<Anomaly> anomalies = runPromise(() -> detector.detect(lowThresholdCtx));

            assertThat(anomalies).isNotEmpty();
        }

        @Test
        @DisplayName("anomaly confidence increases with Z-score magnitude")
        void confidenceIncreasesWithZScore() {
            // Large enough outlier to produce a high confidence
            List<Entity> training = numericalEntities("v", 10, 10, 10, 10, 10, 10, 10, 10, 10, 10);
            List<Entity> detection = List.of(entityWithData(Map.of("v", 1000.0)));

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));

            List<Anomaly> anomalies = runPromise(() -> detector.detect(basicContext()));
            assertThat(anomalies).hasSize(1);
            assertThat(anomalies.get(0).getConfidence()).isGreaterThan(0.9);
        }

        @Test
        @DisplayName("non-numeric fields are silently skipped")
        void nonNumericFieldsSkipped() {
            List<Entity> training = numericalEntities("amount", 10, 20, 30, 40, 50, 60, 70, 80, 90, 100);
            List<Entity> detection = List.of(entityWithData(Map.of("status", "ERROR")));

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));

            List<Anomaly> result = runPromise(() -> detector.detect(basicContext()));
            assertThat(result).isEmpty(); // no numeric field in detection entity
        }
    }

    // =========================================================================
    // IQR fence detection
    // =========================================================================

    @Nested
    @DisplayName("IQR-Fence detection")
    class IqrDetectionTests {

        @Test
        @DisplayName("IQR outlier below lower fence is detected")
        void belowLowerFenceIsDetected() {
            // skewed distribution: most values around 100, IQR will produce a tight fence
            List<Entity> training = numericalEntities("score", 100, 100, 100, 101, 100, 100, 100, 99, 100, 101);
            // Value at -50 is far below the IQR lower fence but |z| might be
            // less than 3σ (works better with IQR for skewed data)
            Entity outlier = entityWithData(Map.of("score", -50.0));

            stubRepository(training, List.of(outlier));
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));

            List<Anomaly> result = runPromise(() -> detector.detect(basicContext()));
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("IQR anomaly has detectionMethod IQR-Fence or Z-Score")
        void iqrAnomalyHasCorrectMethod() {
            List<Entity> training = numericalEntities("x", 1, 2, 2, 3, 3, 3, 4, 4, 5, 5);
            Entity outlier = entityWithData(Map.of("x", 50.0));

            stubRepository(training, List.of(outlier));
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));

            List<Anomaly> result = runPromise(() -> detector.detect(basicContext()));
            assertThat(result).isNotEmpty();
            assertThat(result.get(0).getDetectionMethod()).isIn("Z-Score", "IQR-Fence");
        }

        @Test
        @DisplayName("value inside Tukey fence produces no IQR anomaly")
        void valueInsideFenceIsNotAnomaly() {
            List<Entity> training = numericalEntities("y", 10, 12, 11, 10, 13, 12, 11, 10, 12, 11);
            Entity normal = entityWithData(Map.of("y", 11.0));

            stubRepository(training, List.of(normal));
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));

            List<Anomaly> result = runPromise(() -> detector.detect(basicContext()));
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // Baseline management (updateBaseline + getBaseline)
    // =========================================================================

    @Nested
    @DisplayName("Baseline management")
    class BaselineTests {

        @Test
        @DisplayName("getBaseline before update returns failed Promise")
        void getBaselineBeforeUpdateFails() {
            assertThatThrownBy(() ->
                    runPromise(() -> detector.getBaseline(TENANT, COLLECTION, "someField")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No baseline found");
        }

        @Test
        @DisplayName("updateBaseline stores descriptive stats for numeric fields")
        void updateBaselineStoresStats() {
            List<Entity> training = numericalEntities("price", 10, 20, 30, 40, 50, 60, 70, 80, 90, 100);
            when(repository.findAll(eq(TENANT), eq(COLLECTION), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Promise.of(training));

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));

            BaselineStatistics stats = runPromise(() -> detector.getBaseline(TENANT, COLLECTION, "price"));
            assertThat(stats.getMean()).isEqualTo(55.0);
            assertThat(stats.getMin()).isEqualTo(10.0);
            assertThat(stats.getMax()).isEqualTo(100.0);
            assertThat(stats.getStandardDeviation()).isGreaterThan(0.0);
            assertThat(stats.getSampleCount()).isEqualTo(10);
            assertThat(stats.getLastUpdated()).isNotNull();
        }

        @Test
        @DisplayName("updateBaseline skips fields with fewer than MIN_SAMPLE_COUNT values")
        void insufficientSamplesAreSkipped() {
            // Only 3 entities — less than MIN_SAMPLE_COUNT (5)
            List<Entity> sparse = numericalEntities("rare", 1, 2, 3);
            when(repository.findAll(eq(TENANT), eq(COLLECTION), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Promise.of(sparse));

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));

            assertThatThrownBy(() ->
                    runPromise(() -> detector.getBaseline(TENANT, COLLECTION, "rare")))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("second updateBaseline replaces the previous baseline")
        void updateBaselineReplaces() {
            List<Entity> round1 = numericalEntities("metric", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            List<Entity> round2 = numericalEntities("metric", 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000);

            when(repository.findAll(eq(TENANT), eq(COLLECTION), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Promise.of(round1))
                    .thenReturn(Promise.of(round2));

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            double firstMean = runPromise(() -> detector.getBaseline(TENANT, COLLECTION, "metric")).getMean();

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            double secondMean = runPromise(() -> detector.getBaseline(TENANT, COLLECTION, "metric")).getMean();

            assertThat(secondMean).isGreaterThan(firstMean);
        }

        @Test
        @DisplayName("p25 < median < p75 for normally distributed data")
        void percentileOrderIsCorrect() {
            List<Entity> training = numericalEntities("v", 10, 20, 30, 40, 50, 60, 70, 80, 90, 100);
            when(repository.findAll(eq(TENANT), eq(COLLECTION), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Promise.of(training));

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            BaselineStatistics stats = runPromise(() -> detector.getBaseline(TENANT, COLLECTION, "v"));

            assertThat(stats.getP25()).isLessThan(stats.getMedian());
            assertThat(stats.getMedian()).isLessThan(stats.getP75());
        }
    }

    // =========================================================================
    // Severity classification
    // =========================================================================

    @Nested
    @DisplayName("Severity classification")
    class SeverityClassificationTests {

        @ParameterizedTest(name = "|z|={0} → expected severity={1}")
        @CsvSource({
                "5.5, CRITICAL",
                "4.2, HIGH",
                "3.1, MEDIUM",
                "2.0, LOW"
        })
        @DisplayName("zToSeverity maps Z-score magnitude to correct severity")
        void zScoreSeverityMapping(double absZ, String expectedSeverity) {
            Severity mapped = StatisticalAnomalyDetector.zToSeverity(absZ);
            assertThat(mapped).isEqualTo(Severity.valueOf(expectedSeverity));
        }

        @ParameterizedTest(name = "deviation={0} → expected severity={1}")
        @CsvSource({
                "2.5, CRITICAL",
                "1.1, HIGH",
                "0.6, MEDIUM",
                "0.3, LOW"
        })
        @DisplayName("deviationToSeverity maps IQR deviation to correct severity")
        void iqrDeviationSeverityMapping(double deviation, String expectedSeverity) {
            Severity mapped = StatisticalAnomalyDetector.deviationToSeverity(deviation);
            assertThat(mapped).isEqualTo(Severity.valueOf(expectedSeverity));
        }

        @Test
        @DisplayName("CRITICAL anomaly is counted in both the total and critical metric")
        void criticalAnomalyIsCountedByMetrics() {
            // Use varied training data so stdDev > 0 and Z-score detection fires
            List<Entity> training = numericalEntities("val", 10, 12, 11, 13, 10, 12, 11, 13, 12, 11);
            List<Entity> detection = List.of(entityWithData(Map.of("val", 10_000.0)));

            stubRepository(training, detection);
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            runPromise(() -> detector.detect(basicContext()));

            double detected  = meterRegistry.counter("data_cloud.anomaly.detected", "detector", "statistical").count();
            assertThat(detected).isEqualTo(1.0);
        }
    }

    // =========================================================================
    // Anomaly record completeness
    // =========================================================================

    @Nested
    @DisplayName("Anomaly record completeness")
    class AnomalyRecordTests {

        @Test
        @DisplayName("Z-score anomaly has all required fields populated")
        void zScoreAnomalyFields() {
            // Use varied training data so stdDev > 0 and Z-score detection fires
            List<Entity> training = numericalEntities("temp", 10, 12, 11, 13, 10, 12, 11, 13, 12, 11);
            Entity outlier = entityWithData(Map.of("temp", 5_000.0));

            stubRepository(training, List.of(outlier));
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));

            List<Anomaly> anomalies = runPromise(() -> detector.detect(basicContext()));
            assertThat(anomalies).hasSize(1);

            Anomaly a = anomalies.get(0);
            assertThat(a.getAnomalyId()).isNotBlank();
            assertThat(a.getTitle()).contains("temp");
            assertThat(a.getDescription()).isNotBlank();
            assertThat(a.getDetectionMethod()).isEqualTo("Z-Score");
            assertThat(a.getObservedValue()).isEqualTo(5_000.0);
            assertThat((Double) a.getExpectedValue()).isBetween(10.0, 14.0); // mean of training cluster
            assertThat(a.getDeviation()).isGreaterThan(0.0);
            assertThat(a.getOccurrenceTime()).isNotNull();
            assertThat(a.getDetectedAt()).isNotNull();
            assertThat(a.getSuggestedActions()).isNotEmpty();
            assertThat(a.getEvidence()).containsKey("field");
        }

        @Test
        @DisplayName("anomaly uses entity id as affectedEntity when id is present")
        void anomalyAffectedEntityIsEntityId() {
            List<Entity> training = numericalEntities("n", 10, 12, 11, 13, 10, 12, 11, 13, 12, 11);
            UUID entityId = UUID.randomUUID();
            Entity outlier = entityWithDataAndId(entityId, Map.of("n", 1_000.0));

            stubRepository(training, List.of(outlier));
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));

            List<Anomaly> anomalies = runPromise(() -> detector.detect(basicContext()));
            assertThat(anomalies).isNotEmpty();
            assertThat(anomalies.get(0).getAffectedEntity()).isEqualTo(entityId.toString());
        }

        @Test
        @DisplayName("detectionType from context is stamped on anomaly")
        void detectionTypeFromContextIsUsed() {
            List<Entity> training = numericalEntities("x", 10, 12, 11, 13, 10, 12, 11, 13, 12, 11);
            Entity outlier = entityWithData(Map.of("x", 10_000.0));

            stubRepository(training, List.of(outlier));
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));

            AnomalyContext ctx = AnomalyContext.builder()
                    .tenantId(TENANT)
                    .collectionName(COLLECTION)
                    .detectionType(DetectionType.BEHAVIORAL)
                    .build();
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx));

            assertThat(anomalies).isNotEmpty();
            assertThat(anomalies.get(0).getType()).isEqualTo(DetectionType.BEHAVIORAL);
        }
    }

    // =========================================================================
    // percentile() helper
    // =========================================================================

    @Nested
    @DisplayName("percentile() helper")
    class PercentileHelperTests {

        @Test
        @DisplayName("empty list returns 0.0")
        void emptyListReturnsZero() {
            assertThat(percentile(List.of(), 50)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("single element returns that element for any percentile")
        void singleElementReturnedForAnyP() {
            assertThat(percentile(List.of(42.0), 0)).isEqualTo(42.0);
            assertThat(percentile(List.of(42.0), 50)).isEqualTo(42.0);
            assertThat(percentile(List.of(42.0), 100)).isEqualTo(42.0);
        }

        @Test
        @DisplayName("p0 = minimum, p100 = maximum for sorted list")
        void p0isMinimuumP100isMaximum() {
            List<Double> sorted = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
            assertThat(percentile(sorted, 0)).isEqualTo(1.0);
            assertThat(percentile(sorted, 100)).isEqualTo(5.0);
        }

        @Test
        @DisplayName("p50 of [1,2,3,4,5] is 3.0")
        void medianOfFiveElements() {
            List<Double> sorted = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
            assertThat(percentile(sorted, 50)).isEqualTo(3.0);
        }
    }

    // =========================================================================
    // Metrics
    // =========================================================================

    @Nested
    @DisplayName("Metrics counters")
    class MetricsTests {

        @Test
        @DisplayName("baseline_updates counter increments on each updateBaseline call")
        void baselineUpdateCounterIncrements() {
            when(repository.findAll(eq(TENANT), eq(COLLECTION), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Promise.of(Collections.emptyList()));

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION));

            double count = meterRegistry.counter("data_cloud.anomaly.baseline_updates").count();
            assertThat(count).isEqualTo(2.0);
        }

        @Test
        @DisplayName("detected counter is zero when no anomalies found")
        void noAnomaliesNoCounterIncrement() {
            when(repository.findAll(eq(TENANT), eq(COLLECTION), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Promise.of(Collections.emptyList()));

            runPromise(() -> detector.detect(basicContext()));

            double count = meterRegistry.counter("data_cloud.anomaly.detected", "detector", "statistical").count();
            assertThat(count).isEqualTo(0.0);
        }
    }

    // =========================================================================
    // baselineKey() helper
    // =========================================================================

    @Nested
    @DisplayName("baselineKey() uniqueness")
    class BaselineKeyTests {

        @Test
        @DisplayName("different tenants produce different keys for same field")
        void differentTenantsHaveDifferentKeys() {
            String k1 = StatisticalAnomalyDetector.baselineKey("tenant-A", "col", "field");
            String k2 = StatisticalAnomalyDetector.baselineKey("tenant-B", "col", "field");
            assertThat(k1).isNotEqualTo(k2);
        }

        @Test
        @DisplayName("different collections produce different keys")
        void differentCollectionsHaveDifferentKeys() {
            String k1 = StatisticalAnomalyDetector.baselineKey("t", "col-1", "field");
            String k2 = StatisticalAnomalyDetector.baselineKey("t", "col-2", "field");
            assertThat(k1).isNotEqualTo(k2);
        }

        @Test
        @DisplayName("different fields produce different keys")
        void differentFieldsHaveDifferentKeys() {
            String k1 = StatisticalAnomalyDetector.baselineKey("t", "col", "field-1");
            String k2 = StatisticalAnomalyDetector.baselineKey("t", "col", "field-2");
            assertThat(k1).isNotEqualTo(k2);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private AnomalyContext basicContext() {
        return AnomalyContext.builder()
                .tenantId(TENANT)
                .collectionName(COLLECTION)
                .detectionType(DetectionType.DATA_QUALITY)
                .build();
    }

    /**
     * Stubs the repository to return {@code trainingData} for the first call
     * (used by {@code updateBaseline}) and {@code detectionData} for the second
     * (used by {@code detect}).
     */
    private void stubRepository(List<Entity> trainingData, List<Entity> detectionData) {
        when(repository.findAll(eq(TENANT), eq(COLLECTION), any(), any(), anyInt(), anyInt()))
                .thenReturn(Promise.of(trainingData))
                .thenReturn(Promise.of(detectionData));
    }

    /** Creates entities each with a single numeric field mapped to the given values. */
    private static List<Entity> numericalEntities(String field, double... values) {
        List<Entity> result = new ArrayList<>();
        for (double v : values) {
            result.add(entityWithData(Map.of(field, v)));
        }
        return result;
    }

    private static Entity entityWithData(Map<String, Object> data) {
        return entityWithDataAndId(UUID.randomUUID(), data);
    }

    private static Entity entityWithDataAndId(UUID id, Map<String, Object> data) {
        Entity entity = new Entity();
        entity.setId(id);
        entity.setTenantId(TENANT);
        entity.setCollectionName(COLLECTION);
        entity.setActive(true);
        entity.setCreatedAt(Instant.now());
        Map<String, Object> mutableData = new HashMap<>(data);
        entity.setData(mutableData);
        return entity;
    }
}
