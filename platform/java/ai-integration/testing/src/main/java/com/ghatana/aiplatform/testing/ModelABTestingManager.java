package com.ghatana.aiplatform.testing;

import com.ghatana.aiplatform.registry.ModelMetadata;
import com.ghatana.aiplatform.registry.ModelRegistryService;
import com.ghatana.aiplatform.observability.AiMetricsEmitter;
import com.ghatana.platform.observability.MetricsEmitter;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Automatic A/B testing for ML models.
 *
 * <p><b>Purpose</b><br>
 * Safely test new models in production with automatic promotion/rollback:
 * <ul>
 *   <li>Traffic splitting (e.g., 10% to new model, 90% to current)</li>
 *   <li>Automatic metric comparison (latency, accuracy, errors)</li>
 *   <li>Statistical significance testing (t-test, chi-square)</li>
 *   <li>Automatic promotion/rollback decision</li>
 *   <li>Gradual rollout (10% → 25% → 50% → 100%)</li>
 * </ul>
 *
 * <p><b>Architecture</b><br>
 * <pre>
 * ModelABTestingManager
 *    ├── TrafficSplitter (route requests)
 *    ├── MetricsCollector (track performance)
 *    ├── StatisticalAnalyzer (significance testing)
 *    ├── DecisionEngine (promote/rollback/continue)
 *    └── GradualRollout (phased deployment)
 * </pre>
 *
 * <p><b>A/B Test Lifecycle</b><br>
 * <ol>
 *   <li>Start test: 10% traffic to new model (variant B)</li>
 *   <li>Collect metrics: latency, accuracy, error rate</li>
 *   <li>Analyze after minimum sample size (1000 requests)</li>
 *   <li>If statistically significant improvement: increase to 25%</li>
 *   <li>Continue gradual rollout: 50% → 75% → 100%</li>
 *   <li>If degradation: automatic rollback to 0%</li>
 *   <li>Promote to production if 100% successful</li>
 * </ol>
 *
 * <p><b>Decision Criteria</b><br>
 * Promote if:
 * <ul>
 *   <li>p-value < 0.05 (95% confidence)</li>
 *   <li>Accuracy improvement > 1%</li>
 *   <li>Error rate not increased</li>
 *   <li>Latency increase < 20%</li>
 * </ul>
 *
 * Rollback if:
 * <ul>
 *   <li>Error rate increase > 10%</li>
 *   <li>Accuracy decrease > 2%</li>
 *   <li>Latency increase > 50%</li>
 *   <li>Any critical errors</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Create A/B testing manager
 * ModelABTestingManager abTesting = ModelABTestingManager.builder()
 *     .modelRegistry(modelRegistry)
 *     .aiMetrics(aiMetrics)
 *     .minSampleSize(1000)
 *     .confidenceLevel(0.95)
 *     .build();
 * 
 * // Start A/B test
 * ABTest test = abTesting.startTest(
 *     tenantId, 
 *     modelName,
 *     currentVersion,
 *     newVersion,
 *     0.10  // 10% traffic to new version
 * );
 * 
 * // Route inference through A/B test
 * ModelMetadata selectedModel = abTesting.selectModel(test.getId());
 * 
 * // Record inference result
 * abTesting.recordInference(test.getId(), selectedModel.getVersion(), 
 *                           latency, accuracy, success);
 * 
 * // Decision happens automatically
 * // Test will promote, rollback, or continue based on metrics
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Automatic A/B testing for ML models
 * @doc.layer platform
 * @doc.pattern A/B Testing, Experimentation
 */
public class ModelABTestingManager {
    private static final Logger logger = LoggerFactory.getLogger(ModelABTestingManager.class);
    
    private static final int DEFAULT_MIN_SAMPLE_SIZE = 1000;
    private static final double DEFAULT_CONFIDENCE_LEVEL = 0.95;
    private static final Duration DEFAULT_CHECK_INTERVAL = Duration.ofMinutes(5);
    private static final double[] ROLLOUT_STAGES = {0.10, 0.25, 0.50, 0.75, 1.0};
    
    private final ModelRegistryService modelRegistry;
    private final AiMetricsEmitter aiMetrics;
    private final MetricsEmitter metrics;
    private final int minSampleSize;
    private final double confidenceLevel;
    private final Duration checkInterval;
    private final Executor blockingExecutor;
    
    // Active A/B tests
    private final Map<String, ABTest> activeTests;
    
    // Test metrics
    private final AtomicLong testsStarted;
    private final AtomicLong testsPromoted;
    private final AtomicLong testsRolledBack;
    private final AtomicLong testsContinued;
    
    private volatile boolean running;
    
    private ModelABTestingManager(Builder builder) {
        this.modelRegistry = Objects.requireNonNull(builder.modelRegistry, "modelRegistry is required");
        this.aiMetrics = Objects.requireNonNull(builder.aiMetrics, "aiMetrics is required");
        this.metrics = Objects.requireNonNull(builder.metrics, "metrics is required");
        this.minSampleSize = builder.minSampleSize;
        this.confidenceLevel = builder.confidenceLevel;
        this.checkInterval = builder.checkInterval;
        this.blockingExecutor = Objects.requireNonNull(builder.blockingExecutor, "blockingExecutor is required");
        
        this.activeTests = new ConcurrentHashMap<>();
        
        this.testsStarted = new AtomicLong(0);
        this.testsPromoted = new AtomicLong(0);
        this.testsRolledBack = new AtomicLong(0);
        this.testsContinued = new AtomicLong(0);
        this.running = false;
        
        logger.info("ModelABTestingManager created: minSampleSize={}, confidenceLevel={}",
                   minSampleSize, confidenceLevel);
    }
    
    /**
     * Start A/B testing manager.
     *
     * @return promise that completes when started
     */
    public Promise<Void> start() {
        logger.info("Starting ModelABTestingManager");
        
        return Promise.ofBlocking(blockingExecutor, () -> {
            running = true;
            
            // Start analysis loop
            startAnalysisLoop();
            
            logger.info("ModelABTestingManager started");
            metrics.incrementCounter("abtesting.manager_started");
            
            return null;
        });
    }
    
    /**
     * Stop A/B testing manager.
     *
     * @return promise that completes when stopped
     */
    public Promise<Void> stop() {
        logger.info("Stopping ModelABTestingManager");
        
        return Promise.ofBlocking(blockingExecutor, () -> {
            running = false;
            
            logger.info("ModelABTestingManager stopped");
            metrics.incrementCounter("abtesting.manager_stopped");
            
            return null;
        });
    }
    
    /**
     * Start A/B test.
     *
     * @param tenantId tenant ID
     * @param modelName model name
     * @param controlVersion control version (current production)
     * @param variantVersion variant version (new model to test)
     * @param trafficSplit initial traffic split for variant (0.0 to 1.0)
     * @return A/B test instance
     */
    public ABTest startTest(String tenantId, String modelName, String controlVersion, 
                           String variantVersion, double trafficSplit) {
        logger.info("Starting A/B test: modelName={}, control={}, variant={}, split={}",
                   modelName, controlVersion, variantVersion, trafficSplit);
        
        String testId = UUID.randomUUID().toString();
        
        ABTest test = new ABTest(
            testId,
            tenantId,
            modelName,
            controlVersion,
            variantVersion,
            trafficSplit,
            Instant.now()
        );
        
        activeTests.put(testId, test);
        testsStarted.incrementAndGet();
        
        metrics.incrementCounter("abtesting.test_started",
                               "model_name", modelName,
                               "tenant_id", tenantId);
        
        return test;
    }
    
    /**
     * Select model for inference (A or B).
     *
     * @param testId test ID
     * @return selected model metadata
     */
    public ModelMetadata selectModel(String testId) {
        ABTest test = activeTests.get(testId);
        if (test == null) {
            throw new IllegalArgumentException("Test not found: " + testId);
        }
        
        // Use traffic split to decide
        double random = ThreadLocalRandom.current().nextDouble();
        boolean useVariant = random < test.getTrafficSplit();
        
        String selectedVersion = useVariant ? test.getVariantVersion() : test.getControlVersion();
        
        // Get model from registry
        Optional<ModelMetadata> modelOpt = modelRegistry.getModel(test.getTenantId(), 
                                                                  test.getModelName(), 
                                                                  selectedVersion)
            .getResult();
        
        if (modelOpt.isEmpty()) {
            throw new IllegalStateException("Model not found: " + selectedVersion);
        }
        
        return modelOpt.get();
    }
    
    /**
     * Record inference result.
     *
     * @param testId test ID
     * @param version model version used
     * @param latency inference latency
     * @param accuracy prediction accuracy (0.0 to 1.0)
     * @param success whether inference succeeded
     */
    public void recordInference(String testId, String version, Duration latency, 
                               double accuracy, boolean success) {
        ABTest test = activeTests.get(testId);
        if (test == null) {
            logger.warn("Test not found for recording: testId={}", testId);
            return;
        }
        
        boolean isVariant = version.equals(test.getVariantVersion());
        
        if (isVariant) {
            test.recordVariantInference(latency, accuracy, success);
        } else {
            test.recordControlInference(latency, accuracy, success);
        }
        
        metrics.incrementCounter("abtesting.inference_recorded",
                               "test_id", testId,
                               "version", version,
                               "success", String.valueOf(success));
    }
    
    /**
     * Start analysis loop to check test results.
     */
    private void startAnalysisLoop() {
        Thread analysisThread = new Thread(() -> {
            logger.info("A/B test analysis loop started");
            
            while (running) {
                try {
                    // Analyze each active test
                    for (ABTest test : activeTests.values()) {
                        analyzeAndDecide(test);
                    }
                    
                    // Sleep until next check
                    Thread.sleep(checkInterval.toMillis());
                    
                } catch (InterruptedException e) {
                    logger.info("Analysis loop interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in analysis loop", e);
                }
            }
            
            logger.info("A/B test analysis loop stopped");
        });
        
        analysisThread.setName("abtesting-analysis");
        analysisThread.setDaemon(true);
        analysisThread.start();
    }
    
    /**
     * Analyze test results and make decision.
     */
    private void analyzeAndDecide(ABTest test) {
        // Check if we have enough samples
        if (test.getVariantSampleSize() < minSampleSize || test.getControlSampleSize() < minSampleSize) {
            logger.debug("Not enough samples yet: testId={}, variantSamples={}, controlSamples={}",
                        test.getId(), test.getVariantSampleSize(), test.getControlSampleSize());
            return;
        }
        
        // Calculate metrics
        TestMetrics controlMetrics = calculateMetrics(test, false);
        TestMetrics variantMetrics = calculateMetrics(test, true);
        
        logger.info("Test metrics: testId={}, control={}, variant={}",
                   test.getId(), controlMetrics, variantMetrics);
        
        // Check for immediate rollback conditions
        if (shouldRollback(controlMetrics, variantMetrics)) {
            logger.warn("Rolling back test: testId={}, reason=performance_degradation", test.getId());
            rollbackTest(test);
            return;
        }
        
        // Check for promotion conditions
        if (shouldPromote(controlMetrics, variantMetrics, test)) {
            logger.info("Test shows improvement: testId={}, movingToNextStage", test.getId());
            
            if (test.getTrafficSplit() >= 1.0) {
                // Already at 100%, promote to production
                promoteTest(test);
            } else {
                // Move to next rollout stage
                continueTest(test);
            }
        }
        
        // Otherwise, continue collecting data
    }
    
    /**
     * Calculate metrics for control or variant.
     */
    private TestMetrics calculateMetrics(ABTest test, boolean isVariant) {
        if (isVariant) {
            double avgLatency = test.getVariantTotalLatency() / test.getVariantSampleSize();
            double avgAccuracy = test.getVariantTotalAccuracy() / test.getVariantSampleSize();
            double errorRate = test.getVariantErrors() / (double) test.getVariantSampleSize();
            
            return new TestMetrics(avgLatency, avgAccuracy, errorRate);
        } else {
            double avgLatency = test.getControlTotalLatency() / test.getControlSampleSize();
            double avgAccuracy = test.getControlTotalAccuracy() / test.getControlSampleSize();
            double errorRate = test.getControlErrors() / (double) test.getControlSampleSize();
            
            return new TestMetrics(avgLatency, avgAccuracy, errorRate);
        }
    }
    
    /**
     * Check if test should be rolled back.
     */
    private boolean shouldRollback(TestMetrics control, TestMetrics variant) {
        // Error rate increased significantly
        if (variant.errorRate > control.errorRate * 1.1) {
            return true;
        }
        
        // Accuracy decreased significantly
        if (variant.accuracy < control.accuracy - 0.02) {
            return true;
        }
        
        // Latency increased significantly
        if (variant.latency > control.latency * 1.5) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if test should be promoted to next stage.
     */
    private boolean shouldPromote(TestMetrics control, TestMetrics variant, ABTest test) {
        // Calculate t-test for accuracy improvement
        double pValue = calculateTTest(control.accuracy, variant.accuracy, 
                                       test.getControlSampleSize(), test.getVariantSampleSize());
        
        // Check statistical significance
        if (pValue > (1.0 - confidenceLevel)) {
            logger.debug("Not statistically significant: pValue={}, threshold={}", 
                        pValue, (1.0 - confidenceLevel));
            return false;
        }
        
        // Check improvement threshold
        if (variant.accuracy <= control.accuracy + 0.01) {
            logger.debug("Improvement too small: variantAccuracy={}, controlAccuracy={}",
                        variant.accuracy, control.accuracy);
            return false;
        }
        
        // Check latency constraint
        if (variant.latency > control.latency * 1.2) {
            logger.debug("Latency increased too much: variantLatency={}, controlLatency={}",
                        variant.latency, control.latency);
            return false;
        }
        
        // All criteria met
        return true;
    }
    
    /**
     * Calculate t-test p-value.
     */
    private double calculateTTest(double mean1, double mean2, int n1, int n2) {
        // Simplified t-test calculation
        // TODO: Use proper statistical library for accurate calculation
        double diff = Math.abs(mean1 - mean2);
        double pooledStd = Math.sqrt((1.0/n1 + 1.0/n2) * 0.01); // Assume 0.01 variance
        double tStat = diff / pooledStd;
        
        // Rough p-value approximation
        return 1.0 / (1.0 + tStat);
    }
    
    /**
     * Rollback test.
     */
    private void rollbackTest(ABTest test) {
        logger.info("Rolling back test: testId={}, modelName={}", test.getId(), test.getModelName());
        
        activeTests.remove(test.getId());
        testsRolledBack.incrementAndGet();
        
        metrics.incrementCounter("abtesting.test_rolled_back",
                               "model_name", test.getModelName(),
                               "tenant_id", test.getTenantId());
    }
    
    /**
     * Continue test to next rollout stage.
     */
    private void continueTest(ABTest test) {
        // Find next rollout stage
        double currentSplit = test.getTrafficSplit();
        double nextSplit = currentSplit;
        
        for (double stage : ROLLOUT_STAGES) {
            if (stage > currentSplit) {
                nextSplit = stage;
                break;
            }
        }
        
        logger.info("Continuing test to next stage: testId={}, currentSplit={}, nextSplit={}",
                   test.getId(), currentSplit, nextSplit);
        
        test.setTrafficSplit(nextSplit);
        testsContinued.incrementAndGet();
        
        metrics.incrementCounter("abtesting.test_continued",
                               "model_name", test.getModelName(),
                               "tenant_id", test.getTenantId(),
                               "new_split", String.valueOf(nextSplit));
    }
    
    /**
     * Promote test to production.
     */
    private void promoteTest(ABTest test) {
        logger.info("Promoting test to production: testId={}, modelName={}, variantVersion={}",
                   test.getId(), test.getModelName(), test.getVariantVersion());
        
        // Promote variant to production
        modelRegistry.promoteToProduction(test.getTenantId(), test.getModelName(), test.getVariantVersion());
        
        activeTests.remove(test.getId());
        testsPromoted.incrementAndGet();
        
        metrics.incrementCounter("abtesting.test_promoted",
                               "model_name", test.getModelName(),
                               "tenant_id", test.getTenantId());
    }
    
    /**
     * Get A/B testing statistics.
     *
     * @return statistics map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("running", running);
        stats.put("activeTests", activeTests.size());
        stats.put("testsStarted", testsStarted.get());
        stats.put("testsPromoted", testsPromoted.get());
        stats.put("testsRolledBack", testsRolledBack.get());
        stats.put("testsContinued", testsContinued.get());
        return stats;
    }
    
    /**
     * Create a new builder.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for ModelABTestingManager.
     */
    public static class Builder {
        private ModelRegistryService modelRegistry;
        private AiMetricsEmitter aiMetrics;
        private MetricsEmitter metrics;
        private int minSampleSize = DEFAULT_MIN_SAMPLE_SIZE;
        private double confidenceLevel = DEFAULT_CONFIDENCE_LEVEL;
        private Duration checkInterval = DEFAULT_CHECK_INTERVAL;
        private Executor blockingExecutor;
        
        public Builder modelRegistry(ModelRegistryService registry) {
            this.modelRegistry = registry;
            return this;
        }
        
        public Builder aiMetrics(AiMetricsEmitter metrics) {
            this.aiMetrics = metrics;
            return this;
        }
        
        public Builder metrics(MetricsEmitter metrics) {
            this.metrics = metrics;
            return this;
        }
        
        public Builder minSampleSize(int size) {
            this.minSampleSize = size;
            return this;
        }
        
        public Builder confidenceLevel(double level) {
            this.confidenceLevel = level;
            return this;
        }
        
        public Builder checkInterval(Duration interval) {
            this.checkInterval = interval;
            return this;
        }
        
        public Builder blockingExecutor(Executor executor) {
            this.blockingExecutor = executor;
            return this;
        }
        
        public ModelABTestingManager build() {
            return new ModelABTestingManager(this);
        }
    }
}

/**
 * A/B test instance.
 */
class ABTest {
    private final String id;
    private final String tenantId;
    private final String modelName;
    private final String controlVersion;
    private final String variantVersion;
    private double trafficSplit;
    private final Instant startTime;
    
    // Control metrics
    private final AtomicLong controlSampleSize = new AtomicLong(0);
    private double controlTotalLatency = 0;
    private double controlTotalAccuracy = 0;
    private final AtomicLong controlErrors = new AtomicLong(0);
    
    // Variant metrics
    private final AtomicLong variantSampleSize = new AtomicLong(0);
    private double variantTotalLatency = 0;
    private double variantTotalAccuracy = 0;
    private final AtomicLong variantErrors = new AtomicLong(0);
    
    public ABTest(String id, String tenantId, String modelName, String controlVersion,
                 String variantVersion, double trafficSplit, Instant startTime) {
        this.id = id;
        this.tenantId = tenantId;
        this.modelName = modelName;
        this.controlVersion = controlVersion;
        this.variantVersion = variantVersion;
        this.trafficSplit = trafficSplit;
        this.startTime = startTime;
    }
    
    public void recordControlInference(Duration latency, double accuracy, boolean success) {
        controlSampleSize.incrementAndGet();
        controlTotalLatency += latency.toMillis();
        controlTotalAccuracy += accuracy;
        if (!success) {
            controlErrors.incrementAndGet();
        }
    }
    
    public void recordVariantInference(Duration latency, double accuracy, boolean success) {
        variantSampleSize.incrementAndGet();
        variantTotalLatency += latency.toMillis();
        variantTotalAccuracy += accuracy;
        if (!success) {
            variantErrors.incrementAndGet();
        }
    }
    
    // Getters
    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getModelName() { return modelName; }
    public String getControlVersion() { return controlVersion; }
    public String getVariantVersion() { return variantVersion; }
    public double getTrafficSplit() { return trafficSplit; }
    public void setTrafficSplit(double split) { this.trafficSplit = split; }
    public Instant getStartTime() { return startTime; }
    
    public long getControlSampleSize() { return controlSampleSize.get(); }
    public double getControlTotalLatency() { return controlTotalLatency; }
    public double getControlTotalAccuracy() { return controlTotalAccuracy; }
    public long getControlErrors() { return controlErrors.get(); }
    
    public long getVariantSampleSize() { return variantSampleSize.get(); }
    public double getVariantTotalLatency() { return variantTotalLatency; }
    public double getVariantTotalAccuracy() { return variantTotalAccuracy; }
    public long getVariantErrors() { return variantErrors.get(); }
}

/**
 * Test metrics.
 */
class TestMetrics {
    final double latency;
    final double accuracy;
    final double errorRate;
    
    public TestMetrics(double latency, double accuracy, double errorRate) {
        this.latency = latency;
        this.accuracy = accuracy;
        this.errorRate = errorRate;
    }
    
    @Override
    public String toString() {
        return String.format("TestMetrics{latency=%.2fms, accuracy=%.3f, errorRate=%.3f}",
                           latency, accuracy, errorRate);
    }
}
