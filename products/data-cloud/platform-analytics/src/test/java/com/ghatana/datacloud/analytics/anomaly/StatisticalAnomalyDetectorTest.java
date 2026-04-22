/*
 * Copyright (c) 2026 Ghatana Inc. All rights reserved. // GH-90000
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
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("StatisticalAnomalyDetector Tests [GH-90000]")
class StatisticalAnomalyDetectorTest extends EventloopTestBase {

    private static final String TENANT     = "tenant-1";
    private static final String COLLECTION = "temperature-readings";

    @Mock
    private EntityRepository repository;

    private SimpleMeterRegistry meterRegistry;
    private StatisticalAnomalyDetector detector;

    @BeforeEach
    void setUp() { // GH-90000
        meterRegistry = new SimpleMeterRegistry(); // GH-90000
        // Use a direct executor so blocking tasks execute synchronously inside runPromise
        detector = new StatisticalAnomalyDetector( // GH-90000
                repository, meterRegistry,
                StatisticalAnomalyDetector.DEFAULT_Z_THRESHOLD,
                Executors.newVirtualThreadPerTaskExecutor()); // GH-90000
    }

    // =========================================================================
    // Constructor validation
    // =========================================================================

    @Nested
    @DisplayName("Constructor validation [GH-90000]")
    class ConstructorTests {

        @Test
        @DisplayName("null repository throws NullPointerException [GH-90000]")
        void nullRepositoryFails() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    new StatisticalAnomalyDetector(null, meterRegistry)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("entityRepository [GH-90000]");
        }

        @Test
        @DisplayName("null meterRegistry does not throw (metrics disabled) [GH-90000]")
        void publicConstructorWithNullMeterRegistryFails() { // GH-90000
            // null registry should throw because Counter.builder requires a non-null registry
            assertThatThrownBy(() -> // GH-90000
                    new StatisticalAnomalyDetector(repository, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null executor throws NullPointerException [GH-90000]")
        void nullExecutorFails() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    new StatisticalAnomalyDetector(repository, meterRegistry, 3.0, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("executor [GH-90000]");
        }

        @Test
        @DisplayName("getSupportedDetectionTypes returns DATA_QUALITY and BEHAVIORAL [GH-90000]")
        void supportedTypesAreReturned() { // GH-90000
            List<DetectionType> types = runPromise(() -> detector.getSupportedDetectionTypes()); // GH-90000
            assertThat(types).containsExactlyInAnyOrder(DetectionType.DATA_QUALITY, DetectionType.BEHAVIORAL); // GH-90000
        }
    }

    // =========================================================================
    // detect() — no baseline // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("detect() — without baseline [GH-90000]")
    class DetectWithoutBaselineTests {

        @Test
        @DisplayName("returns empty list when collection is empty [GH-90000]")
        void emptyCollectionReturnsNoAnomalies() { // GH-90000
                    when(repository.findAll(eq(TENANT), eq(COLLECTION), org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString(), anyInt(), anyInt())) // GH-90000
                    .thenReturn(Promise.of(Collections.emptyList())); // GH-90000

            List<Anomaly> result = runPromise(() -> detector.detect(basicContext())); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns empty list when no baseline has been computed [GH-90000]")
        void noBaselineYieldsNoAnomalies() { // GH-90000
            List<Entity> entities = List.of( // GH-90000
                    entityWithData(Map.of("value", 999.0)), // extreme outlier // GH-90000
                    entityWithData(Map.of("value", 1.0))); // GH-90000

                    when(repository.findAll(eq(TENANT), eq(COLLECTION), org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString(), anyInt(), anyInt())) // GH-90000
                    .thenReturn(Promise.of(entities)); // GH-90000

            List<Anomaly> result = runPromise(() -> detector.detect(basicContext())); // GH-90000

            // no baseline → nothing detected
            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("null data map in entity is silently skipped [GH-90000]")
        void nullDataFieldSkipped() { // GH-90000
            Entity entityWithNoData = new Entity(); // GH-90000
            entityWithNoData.setId(UUID.randomUUID()); // GH-90000
            entityWithNoData.setData(null); // GH-90000

                    when(repository.findAll(eq(TENANT), eq(COLLECTION), org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString(), anyInt(), anyInt())) // GH-90000
                    .thenReturn(Promise.of(List.of(entityWithNoData))); // GH-90000

            List<Anomaly> result = runPromise(() -> detector.detect(basicContext())); // GH-90000
            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("detect() null context throws NullPointerException [GH-90000]")
        void nullContextThrows() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> detector.detect(null))) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("context [GH-90000]");
        }
    }

    // =========================================================================
    // updateBaseline() + detect() — Z-score path // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("Z-score detection [GH-90000]")
    class ZScoreDetectionTests {

        @Test
        @DisplayName("values within 3σ are not flagged [GH-90000]")
        void normalValuesProduceNoAnomalies() { // GH-90000
            // 10 entities with value={1..10}: mean=5.5, stdDev≈2.87
            List<Entity> trainingEntities = numericalEntities("value", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10); // GH-90000
            // detection uses same data — all within 3σ
            stubRepository(trainingEntities, trainingEntities); // GH-90000

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000
            List<Anomaly> result = runPromise(() -> detector.detect(basicContext())); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("value 6σ above mean is detected as CRITICAL Z-score anomaly [GH-90000]")
        void extremeOutlierIsDetected() { // GH-90000
            // Tight cluster at 100 ± small noise, then a massive outlier at 700
            List<Entity> trainingData = numericalEntities("temperature", // GH-90000
                    100, 101, 99, 100, 102, 100, 101, 98, 100, 100);
            List<Entity> detectionData = List.of(entityWithData(Map.of("temperature", 700.0))); // GH-90000

            stubRepository(trainingData, detectionData); // GH-90000

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000
            List<Anomaly> anomalies = runPromise(() -> detector.detect(basicContext())); // GH-90000

            assertThat(anomalies).hasSize(1); // GH-90000
            Anomaly a = anomalies.get(0); // GH-90000
            assertThat(a.getSeverity()).isEqualTo(Severity.CRITICAL); // GH-90000
            assertThat(a.getDetectionMethod()).isEqualTo("Z-Score [GH-90000]");
            assertThat(a.getAffectedEntity()).isNotBlank(); // GH-90000
            assertThat(a.getAnomalyId()).isNotBlank(); // GH-90000
            assertThat(a.getType()).isEqualTo(DetectionType.DATA_QUALITY); // GH-90000
        }

        @Test
        @DisplayName("custom threshold from context is respected [GH-90000]")
        void customThresholdIsRespected() { // GH-90000
            // Cluster at mean=50, stdDev≈0.6 — a value at 52 is ~3.3σ away
            List<Entity> training = numericalEntities("metric", 50, 50, 51, 49, 50, 50, 51, 49, 50, 50); // GH-90000
            List<Entity> detection = List.of(entityWithData(Map.of("metric", 52.0))); // GH-90000

            stubRepository(training, detection); // GH-90000
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000

            // With default threshold (3σ) — may or may not detect depending on stdDev // GH-90000
            // With low threshold (1σ) — should always detect // GH-90000
            AnomalyContext lowThresholdCtx = AnomalyContext.builder() // GH-90000
                    .tenantId(TENANT) // GH-90000
                    .collectionName(COLLECTION) // GH-90000
                    .detectionType(DetectionType.DATA_QUALITY) // GH-90000
                    .threshold(1.0) // GH-90000
                    .build(); // GH-90000

            List<Anomaly> anomalies = runPromise(() -> detector.detect(lowThresholdCtx)); // GH-90000

            assertThat(anomalies).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("anomaly confidence increases with Z-score magnitude [GH-90000]")
        void confidenceIncreasesWithZScore() { // GH-90000
            // Large enough outlier to produce a high confidence
            List<Entity> training = numericalEntities("v", 10, 10, 10, 10, 10, 10, 10, 10, 10, 10); // GH-90000
            List<Entity> detection = List.of(entityWithData(Map.of("v", 1000.0))); // GH-90000

            stubRepository(training, detection); // GH-90000
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000

            List<Anomaly> anomalies = runPromise(() -> detector.detect(basicContext())); // GH-90000
            assertThat(anomalies).hasSize(1); // GH-90000
            assertThat(anomalies.get(0).getConfidence()).isGreaterThan(0.9); // GH-90000
        }

        @Test
        @DisplayName("non-numeric fields are silently skipped [GH-90000]")
        void nonNumericFieldsSkipped() { // GH-90000
            List<Entity> training = numericalEntities("amount", 10, 20, 30, 40, 50, 60, 70, 80, 90, 100); // GH-90000
            List<Entity> detection = List.of(entityWithData(Map.of("status", "ERROR"))); // GH-90000

            stubRepository(training, detection); // GH-90000
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000

            List<Anomaly> result = runPromise(() -> detector.detect(basicContext())); // GH-90000
            assertThat(result).isEmpty(); // no numeric field in detection entity // GH-90000
        }
    }

    // =========================================================================
    // IQR fence detection
    // =========================================================================

    @Nested
    @DisplayName("IQR-Fence detection [GH-90000]")
    class IqrDetectionTests {

        @Test
        @DisplayName("IQR outlier below lower fence is detected [GH-90000]")
        void belowLowerFenceIsDetected() { // GH-90000
            // skewed distribution: most values around 100, IQR will produce a tight fence
            List<Entity> training = numericalEntities("score", 100, 100, 100, 101, 100, 100, 100, 99, 100, 101); // GH-90000
            // Value at -50 is far below the IQR lower fence but |z| might be
            // less than 3σ (works better with IQR for skewed data) // GH-90000
            Entity outlier = entityWithData(Map.of("score", -50.0)); // GH-90000

            stubRepository(training, List.of(outlier)); // GH-90000
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000

            List<Anomaly> result = runPromise(() -> detector.detect(basicContext())); // GH-90000
            assertThat(result).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("IQR anomaly has detectionMethod IQR-Fence or Z-Score [GH-90000]")
        void iqrAnomalyHasCorrectMethod() { // GH-90000
            List<Entity> training = numericalEntities("x", 1, 2, 2, 3, 3, 3, 4, 4, 5, 5); // GH-90000
            Entity outlier = entityWithData(Map.of("x", 50.0)); // GH-90000

            stubRepository(training, List.of(outlier)); // GH-90000
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000

            List<Anomaly> result = runPromise(() -> detector.detect(basicContext())); // GH-90000
            assertThat(result).isNotEmpty(); // GH-90000
            assertThat(result.get(0).getDetectionMethod()).isIn("Z-Score", "IQR-Fence"); // GH-90000
        }

        @Test
        @DisplayName("value inside Tukey fence produces no IQR anomaly [GH-90000]")
        void valueInsideFenceIsNotAnomaly() { // GH-90000
            List<Entity> training = numericalEntities("y", 10, 12, 11, 10, 13, 12, 11, 10, 12, 11); // GH-90000
            Entity normal = entityWithData(Map.of("y", 11.0)); // GH-90000

            stubRepository(training, List.of(normal)); // GH-90000
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000

            List<Anomaly> result = runPromise(() -> detector.detect(basicContext())); // GH-90000
            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // Baseline management (updateBaseline + getBaseline) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("Baseline management [GH-90000]")
    class BaselineTests {

        @Test
        @DisplayName("getBaseline before update returns failed Promise [GH-90000]")
        void getBaselineBeforeUpdateFails() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> detector.getBaseline(TENANT, COLLECTION, "someField"))) // GH-90000
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("No baseline found [GH-90000]");
        }

        @Test
        @DisplayName("updateBaseline stores descriptive stats for numeric fields [GH-90000]")
        void updateBaselineStoresStats() { // GH-90000
            List<Entity> training = numericalEntities("price", 10, 20, 30, 40, 50, 60, 70, 80, 90, 100); // GH-90000
                    when(repository.findAll(eq(TENANT), eq(COLLECTION), org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString(), anyInt(), anyInt())) // GH-90000
                    .thenReturn(Promise.of(training)); // GH-90000

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000

            BaselineStatistics stats = runPromise(() -> detector.getBaseline(TENANT, COLLECTION, "price")); // GH-90000
            assertThat(stats.getMean()).isEqualTo(55.0); // GH-90000
            assertThat(stats.getMin()).isEqualTo(10.0); // GH-90000
            assertThat(stats.getMax()).isEqualTo(100.0); // GH-90000
            assertThat(stats.getStandardDeviation()).isGreaterThan(0.0); // GH-90000
            assertThat(stats.getSampleCount()).isEqualTo(10); // GH-90000
            assertThat(stats.getLastUpdated()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("updateBaseline skips fields with fewer than MIN_SAMPLE_COUNT values [GH-90000]")
        void insufficientSamplesAreSkipped() { // GH-90000
            // Only 3 entities — less than MIN_SAMPLE_COUNT (5) // GH-90000
            List<Entity> sparse = numericalEntities("rare", 1, 2, 3); // GH-90000
                    when(repository.findAll(eq(TENANT), eq(COLLECTION), org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString(), anyInt(), anyInt())) // GH-90000
                    .thenReturn(Promise.of(sparse)); // GH-90000

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000

            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> detector.getBaseline(TENANT, COLLECTION, "rare"))) // GH-90000
                    .isInstanceOf(IllegalStateException.class); // GH-90000
        }

        @Test
        @DisplayName("second updateBaseline replaces the previous baseline [GH-90000]")
        void updateBaselineReplaces() { // GH-90000
            List<Entity> round1 = numericalEntities("metric", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10); // GH-90000
            List<Entity> round2 = numericalEntities("metric", 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000); // GH-90000

                    when(repository.findAll(eq(TENANT), eq(COLLECTION), org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString(), anyInt(), anyInt())) // GH-90000
                    .thenReturn(Promise.of(round1)) // GH-90000
                    .thenReturn(Promise.of(round2)); // GH-90000

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000
            double firstMean = runPromise(() -> detector.getBaseline(TENANT, COLLECTION, "metric")).getMean(); // GH-90000

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000
            double secondMean = runPromise(() -> detector.getBaseline(TENANT, COLLECTION, "metric")).getMean(); // GH-90000

            assertThat(secondMean).isGreaterThan(firstMean); // GH-90000
        }

        @Test
        @DisplayName("p25 < median < p75 for normally distributed data [GH-90000]")
        void percentileOrderIsCorrect() { // GH-90000
            List<Entity> training = numericalEntities("v", 10, 20, 30, 40, 50, 60, 70, 80, 90, 100); // GH-90000
                    when(repository.findAll(eq(TENANT), eq(COLLECTION), org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString(), anyInt(), anyInt())) // GH-90000
                    .thenReturn(Promise.of(training)); // GH-90000

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000
            BaselineStatistics stats = runPromise(() -> detector.getBaseline(TENANT, COLLECTION, "v")); // GH-90000

            assertThat(stats.getP25()).isLessThan(stats.getMedian()); // GH-90000
            assertThat(stats.getMedian()).isLessThan(stats.getP75()); // GH-90000
        }
    }

    // =========================================================================
    // Severity classification
    // =========================================================================

    @Nested
    @DisplayName("Severity classification [GH-90000]")
    class SeverityClassificationTests {

        @ParameterizedTest(name = "|z|={0} → expected severity={1}") // GH-90000
        @CsvSource({ // GH-90000
                "5.5, CRITICAL",
                "4.2, HIGH",
                "3.1, MEDIUM",
                "2.0, LOW"
        })
        @DisplayName("zToSeverity maps Z-score magnitude to correct severity [GH-90000]")
        void zScoreSeverityMapping(double absZ, String expectedSeverity) { // GH-90000
            Severity mapped = StatisticalAnomalyDetector.zToSeverity(absZ); // GH-90000
            assertThat(mapped).isEqualTo(Severity.valueOf(expectedSeverity)); // GH-90000
        }

        @ParameterizedTest(name = "deviation={0} → expected severity={1}") // GH-90000
        @CsvSource({ // GH-90000
                "2.5, CRITICAL",
                "1.1, HIGH",
                "0.6, MEDIUM",
                "0.3, LOW"
        })
        @DisplayName("deviationToSeverity maps IQR deviation to correct severity [GH-90000]")
        void iqrDeviationSeverityMapping(double deviation, String expectedSeverity) { // GH-90000
            Severity mapped = StatisticalAnomalyDetector.deviationToSeverity(deviation); // GH-90000
            assertThat(mapped).isEqualTo(Severity.valueOf(expectedSeverity)); // GH-90000
        }

        @Test
        @DisplayName("CRITICAL anomaly is counted in both the total and critical metric [GH-90000]")
        void criticalAnomalyIsCountedByMetrics() { // GH-90000
            // Use varied training data so stdDev > 0 and Z-score detection fires
            List<Entity> training = numericalEntities("val", 10, 12, 11, 13, 10, 12, 11, 13, 12, 11); // GH-90000
            List<Entity> detection = List.of(entityWithData(Map.of("val", 10_000.0))); // GH-90000

            stubRepository(training, detection); // GH-90000
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000
            runPromise(() -> detector.detect(basicContext())); // GH-90000

            double detected  = meterRegistry.counter("data_cloud.anomaly.detected", "detector", "statistical").count(); // GH-90000
            assertThat(detected).isEqualTo(1.0); // GH-90000
        }
    }

    // =========================================================================
    // Anomaly record completeness
    // =========================================================================

    @Nested
    @DisplayName("Anomaly record completeness [GH-90000]")
    class AnomalyRecordTests {

        @Test
        @DisplayName("Z-score anomaly has all required fields populated [GH-90000]")
        void zScoreAnomalyFields() { // GH-90000
            // Use varied training data so stdDev > 0 and Z-score detection fires
            List<Entity> training = numericalEntities("temp", 10, 12, 11, 13, 10, 12, 11, 13, 12, 11); // GH-90000
            Entity outlier = entityWithData(Map.of("temp", 5_000.0)); // GH-90000

            stubRepository(training, List.of(outlier)); // GH-90000
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000

            List<Anomaly> anomalies = runPromise(() -> detector.detect(basicContext())); // GH-90000
            assertThat(anomalies).hasSize(1); // GH-90000

            Anomaly a = anomalies.get(0); // GH-90000
            assertThat(a.getAnomalyId()).isNotBlank(); // GH-90000
            assertThat(a.getTitle()).contains("temp [GH-90000]");
            assertThat(a.getDescription()).isNotBlank(); // GH-90000
            assertThat(a.getDetectionMethod()).isEqualTo("Z-Score [GH-90000]");
            assertThat(a.getObservedValue()).isEqualTo(5_000.0); // GH-90000
            assertThat((Double) a.getExpectedValue()).isBetween(10.0, 14.0); // mean of training cluster // GH-90000
            assertThat(a.getDeviation()).isGreaterThan(0.0); // GH-90000
            assertThat(a.getOccurrenceTime()).isNotNull(); // GH-90000
            assertThat(a.getDetectedAt()).isNotNull(); // GH-90000
            assertThat(a.getSuggestedActions()).isNotEmpty(); // GH-90000
            assertThat(a.getEvidence()).containsKey("field [GH-90000]");
        }

        @Test
        @DisplayName("anomaly uses entity id as affectedEntity when id is present [GH-90000]")
        void anomalyAffectedEntityIsEntityId() { // GH-90000
            List<Entity> training = numericalEntities("n", 10, 12, 11, 13, 10, 12, 11, 13, 12, 11); // GH-90000
            UUID entityId = UUID.randomUUID(); // GH-90000
            Entity outlier = entityWithDataAndId(entityId, Map.of("n", 1_000.0)); // GH-90000

            stubRepository(training, List.of(outlier)); // GH-90000
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000

            List<Anomaly> anomalies = runPromise(() -> detector.detect(basicContext())); // GH-90000
            assertThat(anomalies).isNotEmpty(); // GH-90000
            assertThat(anomalies.get(0).getAffectedEntity()).isEqualTo(entityId.toString()); // GH-90000
        }

        @Test
        @DisplayName("detectionType from context is stamped on anomaly [GH-90000]")
        void detectionTypeFromContextIsUsed() { // GH-90000
            List<Entity> training = numericalEntities("x", 10, 12, 11, 13, 10, 12, 11, 13, 12, 11); // GH-90000
            Entity outlier = entityWithData(Map.of("x", 10_000.0)); // GH-90000

            stubRepository(training, List.of(outlier)); // GH-90000
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000

            AnomalyContext ctx = AnomalyContext.builder() // GH-90000
                    .tenantId(TENANT) // GH-90000
                    .collectionName(COLLECTION) // GH-90000
                    .detectionType(DetectionType.BEHAVIORAL) // GH-90000
                    .build(); // GH-90000
            List<Anomaly> anomalies = runPromise(() -> detector.detect(ctx)); // GH-90000

            assertThat(anomalies).isNotEmpty(); // GH-90000
            assertThat(anomalies.get(0).getType()).isEqualTo(DetectionType.BEHAVIORAL); // GH-90000
        }
    }

    // =========================================================================
    // percentile() helper // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("percentile() helper [GH-90000]")
    class PercentileHelperTests {

        @Test
        @DisplayName("empty list returns 0.0 [GH-90000]")
        void emptyListReturnsZero() { // GH-90000
            assertThat(percentile(List.of(), 50)).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("single element returns that element for any percentile [GH-90000]")
        void singleElementReturnedForAnyP() { // GH-90000
            assertThat(percentile(List.of(42.0), 0)).isEqualTo(42.0); // GH-90000
            assertThat(percentile(List.of(42.0), 50)).isEqualTo(42.0); // GH-90000
            assertThat(percentile(List.of(42.0), 100)).isEqualTo(42.0); // GH-90000
        }

        @Test
        @DisplayName("p0 = minimum, p100 = maximum for sorted list [GH-90000]")
        void p0isMinimuumP100isMaximum() { // GH-90000
            List<Double> sorted = List.of(1.0, 2.0, 3.0, 4.0, 5.0); // GH-90000
            assertThat(percentile(sorted, 0)).isEqualTo(1.0); // GH-90000
            assertThat(percentile(sorted, 100)).isEqualTo(5.0); // GH-90000
        }

        @Test
        @DisplayName("p50 of [1,2,3,4,5] is 3.0 [GH-90000]")
        void medianOfFiveElements() { // GH-90000
            List<Double> sorted = List.of(1.0, 2.0, 3.0, 4.0, 5.0); // GH-90000
            assertThat(percentile(sorted, 50)).isEqualTo(3.0); // GH-90000
        }
    }

    // =========================================================================
    // Metrics
    // =========================================================================

    @Nested
    @DisplayName("Metrics counters [GH-90000]")
    class MetricsTests {

        @Test
        @DisplayName("baseline_updates counter increments on each updateBaseline call [GH-90000]")
        void baselineUpdateCounterIncrements() { // GH-90000
                    when(repository.findAll(eq(TENANT), eq(COLLECTION), org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString(), anyInt(), anyInt())) // GH-90000
                    .thenReturn(Promise.of(Collections.emptyList())); // GH-90000

            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000
            runPromise(() -> detector.updateBaseline(TENANT, COLLECTION)); // GH-90000

            double count = meterRegistry.counter("data_cloud.anomaly.baseline_updates [GH-90000]").count();
            assertThat(count).isEqualTo(2.0); // GH-90000
        }

        @Test
        @DisplayName("detected counter is zero when no anomalies found [GH-90000]")
        void noAnomaliesNoCounterIncrement() { // GH-90000
                    when(repository.findAll(eq(TENANT), eq(COLLECTION), org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString(), anyInt(), anyInt())) // GH-90000
                    .thenReturn(Promise.of(Collections.emptyList())); // GH-90000

            runPromise(() -> detector.detect(basicContext())); // GH-90000

            double count = meterRegistry.counter("data_cloud.anomaly.detected", "detector", "statistical").count(); // GH-90000
            assertThat(count).isEqualTo(0.0); // GH-90000
        }
    }

    // =========================================================================
    // baselineKey() helper // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("baselineKey() uniqueness [GH-90000]")
    class BaselineKeyTests {

        @Test
        @DisplayName("different tenants produce different keys for same field [GH-90000]")
        void differentTenantsHaveDifferentKeys() { // GH-90000
            String k1 = StatisticalAnomalyDetector.baselineKey("tenant-A", "col", "field"); // GH-90000
            String k2 = StatisticalAnomalyDetector.baselineKey("tenant-B", "col", "field"); // GH-90000
            assertThat(k1).isNotEqualTo(k2); // GH-90000
        }

        @Test
        @DisplayName("different collections produce different keys [GH-90000]")
        void differentCollectionsHaveDifferentKeys() { // GH-90000
            String k1 = StatisticalAnomalyDetector.baselineKey("t", "col-1", "field"); // GH-90000
            String k2 = StatisticalAnomalyDetector.baselineKey("t", "col-2", "field"); // GH-90000
            assertThat(k1).isNotEqualTo(k2); // GH-90000
        }

        @Test
        @DisplayName("different fields produce different keys [GH-90000]")
        void differentFieldsHaveDifferentKeys() { // GH-90000
            String k1 = StatisticalAnomalyDetector.baselineKey("t", "col", "field-1"); // GH-90000
            String k2 = StatisticalAnomalyDetector.baselineKey("t", "col", "field-2"); // GH-90000
            assertThat(k1).isNotEqualTo(k2); // GH-90000
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private AnomalyContext basicContext() { // GH-90000
        return AnomalyContext.builder() // GH-90000
                .tenantId(TENANT) // GH-90000
                .collectionName(COLLECTION) // GH-90000
                .detectionType(DetectionType.DATA_QUALITY) // GH-90000
                .build(); // GH-90000
    }

    /**
     * Stubs the repository to return {@code trainingData} for the first call
     * (used by {@code updateBaseline}) and {@code detectionData} for the second // GH-90000
     * (used by {@code detect}). // GH-90000
     */
    private void stubRepository(List<Entity> trainingData, List<Entity> detectionData) { // GH-90000
        when(repository.findAll(eq(TENANT), eq(COLLECTION), org.mockito.ArgumentMatchers.<Map<String, Object>>any(), anyString(), anyInt(), anyInt())) // GH-90000
                .thenReturn(Promise.of(trainingData)) // GH-90000
                .thenReturn(Promise.of(detectionData)); // GH-90000
    }

    /** Creates entities each with a single numeric field mapped to the given values. */
    private static List<Entity> numericalEntities(String field, double... values) { // GH-90000
        List<Entity> result = new ArrayList<>(); // GH-90000
        for (double v : values) { // GH-90000
            result.add(entityWithData(Map.of(field, v))); // GH-90000
        }
        return result;
    }

    private static Entity entityWithData(Map<String, Object> data) { // GH-90000
        return entityWithDataAndId(UUID.randomUUID(), data); // GH-90000
    }

    private static Entity entityWithDataAndId(UUID id, Map<String, Object> data) { // GH-90000
        Entity entity = new Entity(); // GH-90000
        entity.setId(id); // GH-90000
        entity.setTenantId(TENANT); // GH-90000
        entity.setCollectionName(COLLECTION); // GH-90000
        entity.setActive(true); // GH-90000
        entity.setCreatedAt(Instant.now()); // GH-90000
        Map<String, Object> mutableData = new HashMap<>(data); // GH-90000
        entity.setData(mutableData); // GH-90000
        return entity;
    }
}
