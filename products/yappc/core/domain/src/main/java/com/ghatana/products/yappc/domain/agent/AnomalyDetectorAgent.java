package com.ghatana.products.yappc.domain.agent;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anomaly Detector Agent for real-time anomaly detection.
 * <p>
 * Uses ensemble anomaly detection algorithms to identify unusual patterns
 * in metrics, behaviors, and system states.
 * <p>
 * <b>Responsibilities:</b>
 * <ul>
 *   <li>Real-time anomaly detection in metrics streams</li>
 *   <li>Pattern break identification</li>
 *   <li>Root cause analysis</li>
 *   <li>Alert generation and severity classification</li>
 * </ul>
 * <p>
 * <b>Models:</b> Isolation Forest, LSTM
 * <p>
 * <b>Latency SLA:</b> 200ms
 *
 * @doc.type class
 * @doc.purpose Real-time anomaly detection agent
 * @doc.layer product
 * @doc.pattern AIAgent
 */
public class AnomalyDetectorAgent extends AbstractAIAgent<AnomalyInput, AnomalyOutput> {

    private static final Logger LOG = LoggerFactory.getLogger(AnomalyDetectorAgent.class);

    private static final String VERSION = "2.0.0";
    private static final String DESCRIPTION = "Real-time anomaly detection in metrics and patterns";
    private static final List<String> CAPABILITIES = List.of(
            "anomaly-detection",
            "pattern-analysis",
            "root-cause-analysis",
            "severity-classification",
            "real-time-monitoring"
    );
    private static final List<String> SUPPORTED_MODELS = List.of(
            "isolation-forest",
            "lstm",
            "statistical"
    );

    private final AnomalyMLService mlService;
    private final AlertService alertService;
    private final MetricStore metricStore;

    /**
     * Creates a new AnomalyDetectorAgent.
     *
     * @param mlService        The ML service for anomaly detection
     * @param alertService     The alert service for notifications
     * @param metricStore      The metric store for baseline data
     * @param metricsCollector The metrics collector
     */
    public AnomalyDetectorAgent(
            @NotNull AnomalyMLService mlService,
            @NotNull AlertService alertService,
            @NotNull MetricStore metricStore,
            @NotNull MetricsCollector metricsCollector
    ) {
        super(
                AgentName.ANOMALY_DETECTOR_AGENT,
                VERSION,
                DESCRIPTION,
                CAPABILITIES,
                SUPPORTED_MODELS,
                metricsCollector
        );
        this.mlService = mlService;
        this.alertService = alertService;
        this.metricStore = metricStore;
    }

    @Override
    public void validateInput(@NotNull AnomalyInput input) {
        if (input.metricType() == null) {
            throw new IllegalArgumentException("Metric type is required");
        }
        if (input.currentMetrics() == null || input.currentMetrics().isEmpty()) {
            throw new IllegalArgumentException("Current metrics cannot be empty");
        }
        if (input.sensitivity() < 0 || input.sensitivity() > 1) {
            throw new IllegalArgumentException("Sensitivity must be between 0 and 1");
        }
    }

    @Override
    protected Promise<ProcessResult<AnomalyOutput>> processRequest(
            @NotNull AnomalyInput input,
            @NotNull AIAgentContext context
    ) {
        LOG.debug("Detecting anomalies for metric type: {}", input.metricType());

        // Load baseline metrics
        return metricStore.getBaseline(context.workspaceId(), input.metricType(), "7d")
                .then(baseline -> {
                    // Run anomaly detection with ensemble
                    return runEnsembleDetection(input, baseline, context);
                })
                .then(result -> {
                    // Classify severity and identify root causes
                    List<AnomalyOutput.DetectedAnomaly> classifiedAnomalies =
                            classifyAndEnrichAnomalies(result.anomalies(), result.baseline(), input);

                    // Trigger alerts for high-severity anomalies
                    return triggerAlerts(classifiedAnomalies, context)
                            .map(v -> {
                                // Generate mitigation recommendations
                                List<AnomalyOutput.MitigationStep> recommendations =
                                        generateMitigationSteps(classifiedAnomalies);

                                AnomalyOutput output = AnomalyOutput.builder()
                                        .anomaliesDetected(classifiedAnomalies.size())
                                        .anomalies(classifiedAnomalies)
                                        .baseline(result.baseline())
                                        .recommendations(recommendations)
                                        .build();

                                double avgConfidence = classifiedAnomalies.stream()
                                        .mapToDouble(AnomalyOutput.DetectedAnomaly::confidence)
                                        .average()
                                        .orElse(0.8);

                                return ProcessResult.of(output, avgConfidence);
                            });
                });
    }

    private Promise<DetectionResult> runEnsembleDetection(
            AnomalyInput input,
            AnomalyOutput.Baseline baseline,
            AIAgentContext context
    ) {
        // Run multiple detection algorithms in parallel
        Promise<List<RawAnomaly>> isolationForest = mlService.detectWithIsolationForest(
                input.currentMetrics(),
                baseline,
                input.sensitivity()
        );

        Promise<List<RawAnomaly>> lstm = input.timeSeriesData() != null
                ? mlService.detectWithLSTM(input.timeSeriesData(), 100)
                : Promise.of(List.of());

        Promise<List<RawAnomaly>> statistical = mlService.detectWithStatistical(
                input.currentMetrics(),
                baseline,
                3.0 // 3 sigma threshold
        );

        // Combine results
        return Promises.toList(isolationForest, lstm, statistical)
                .map(results -> {
                    List<RawAnomaly> allAnomalies = new ArrayList<>();
                    for (List<RawAnomaly> result : results) {
                        allAnomalies.addAll(result);
                    }

                    // Ensemble voting - anomalies detected by multiple algorithms have higher confidence
                    List<RawAnomaly> votedAnomalies = ensembleVote(allAnomalies);

                    return new DetectionResult(votedAnomalies, baseline);
                });
    }

    private List<RawAnomaly> ensembleVote(List<RawAnomaly> allAnomalies) {
        // Group anomalies by metric and timestamp
        Map<String, List<RawAnomaly>> grouped = new java.util.HashMap<>();
        for (RawAnomaly anomaly : allAnomalies) {
            String key = anomaly.metricName() + ":" + anomaly.timestamp();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(anomaly);
        }

        // Keep anomalies detected by at least 2 algorithms, boost confidence for unanimous
        List<RawAnomaly> voted = new ArrayList<>();
        for (Map.Entry<String, List<RawAnomaly>> entry : grouped.entrySet()) {
            List<RawAnomaly> group = entry.getValue();
            if (group.size() >= 2) {
                // Average the confidence, boost if detected by all algorithms
                double avgConfidence = group.stream()
                        .mapToDouble(RawAnomaly::confidence)
                        .average()
                        .orElse(0.5);

                if (group.size() == 3) {
                    avgConfidence = Math.min(avgConfidence * 1.2, 1.0);
                }

                RawAnomaly first = group.get(0);
                voted.add(new RawAnomaly(
                        first.metricName(),
                        first.timestamp(),
                        first.value(),
                        first.baselineValue(),
                        avgConfidence,
                        group.stream()
                                .flatMap(a -> a.algorithms().stream())
                                .distinct()
                                .toList()
                ));
            }
        }

        return voted;
    }

    private List<AnomalyOutput.DetectedAnomaly> classifyAndEnrichAnomalies(
            List<RawAnomaly> rawAnomalies,
            AnomalyOutput.Baseline baseline,
            AnomalyInput input
    ) {
        List<AnomalyOutput.DetectedAnomaly> classified = new ArrayList<>();

        for (RawAnomaly raw : rawAnomalies) {
            double deviationPercent = ((raw.value() - raw.baselineValue()) / raw.baselineValue()) * 100;

            // Determine anomaly type based on metric and deviation
            AnomalyOutput.DetectedAnomaly.AnomalyType type = determineAnomalyType(
                    input.metricType(),
                    deviationPercent
            );

            // Classify severity
            AnomalyOutput.DetectedAnomaly.Severity severity =
                    AnomalyOutput.DetectedAnomaly.Severity.fromScore(
                            Math.abs(deviationPercent) / 100 * raw.confidence()
                    );

            // Attempt root cause analysis
            AnomalyOutput.RootCauseAnalysis rootCause = analyzeRootCause(raw, input);

            classified.add(new AnomalyOutput.DetectedAnomaly(
                    "anomaly-" + UUID.randomUUID().toString().substring(0, 8),
                    type,
                    severity,
                    generateAnomalyTitle(type, raw.metricName()),
                    generateAnomalyDescription(type, raw, deviationPercent),
                    Instant.now().toString(),
                    List.of(), // affected items to be filled
                    raw.baselineValue(),
                    raw.value(),
                    deviationPercent,
                    raw.confidence(),
                    VERSION,
                    rootCause
            ));
        }

        return classified;
    }

    private AnomalyOutput.DetectedAnomaly.AnomalyType determineAnomalyType(
            AnomalyInput.AnomalyMetricType metricType,
            double deviationPercent
    ) {
        return switch (metricType) {
            case VELOCITY -> deviationPercent < 0
                    ? AnomalyOutput.DetectedAnomaly.AnomalyType.VELOCITY_DROP
                    : AnomalyOutput.DetectedAnomaly.AnomalyType.VELOCITY_SPIKE;
            case ERROR_RATE, QUALITY_METRICS -> AnomalyOutput.DetectedAnomaly.AnomalyType.QUALITY_DEGRADATION;
            case SECURITY_EVENTS -> AnomalyOutput.DetectedAnomaly.AnomalyType.SECURITY_THREAT;
            case RESOURCE_USAGE -> AnomalyOutput.DetectedAnomaly.AnomalyType.RESOURCE_EXHAUSTION;
            default -> AnomalyOutput.DetectedAnomaly.AnomalyType.PATTERN_BREAK;
        };
    }

    private String generateAnomalyTitle(
            AnomalyOutput.DetectedAnomaly.AnomalyType type,
            String metricName
    ) {
        return switch (type) {
            case VELOCITY_DROP -> "Velocity Drop Detected";
            case VELOCITY_SPIKE -> "Unusual Velocity Spike";
            case PATTERN_BREAK -> "Pattern Deviation in " + metricName;
            case SECURITY_THREAT -> "Security Anomaly Detected";
            case QUALITY_DEGRADATION -> "Quality Metrics Degradation";
            case RESOURCE_EXHAUSTION -> "Resource Usage Anomaly";
            case UNUSUAL_ACCESS -> "Unusual Access Pattern";
            case CUSTOM -> "Anomaly in " + metricName;
        };
    }

    private String generateAnomalyDescription(
            AnomalyOutput.DetectedAnomaly.AnomalyType type,
            RawAnomaly anomaly,
            double deviationPercent
    ) {
        return String.format(
                "%s detected a %.1f%% deviation from baseline. " +
                        "Current value: %.2f, Baseline: %.2f. Detected by: %s",
                type.name().replace("_", " "),
                deviationPercent,
                anomaly.value(),
                anomaly.baselineValue(),
                String.join(", ", anomaly.algorithms())
        );
    }

    private AnomalyOutput.RootCauseAnalysis analyzeRootCause(RawAnomaly anomaly, AnomalyInput input) {
        // Simplified root cause analysis
        List<String> factors = new ArrayList<>();

        if (input.context() != null) {
            if (input.context().containsKey("recentDeployment")) {
                factors.add("Recent deployment detected");
            }
            if (input.context().containsKey("teamChanges")) {
                factors.add("Team composition changes");
            }
        }

        if (factors.isEmpty()) {
            factors.add("External factors under investigation");
        }

        return new AnomalyOutput.RootCauseAnalysis(
                "automated",
                "Preliminary root cause analysis",
                0.6,
                factors
        );
    }

    private Promise<Void> triggerAlerts(
            List<AnomalyOutput.DetectedAnomaly> anomalies,
            AIAgentContext context
    ) {
        List<AnomalyOutput.DetectedAnomaly> highSeverity = anomalies.stream()
                .filter(a -> a.severity() == AnomalyOutput.DetectedAnomaly.Severity.CRITICAL)
                .toList();

        if (highSeverity.isEmpty()) {
            return Promise.complete();
        }

        return alertService.trigger(new AlertRequest(
                "anomaly-detected",
                highSeverity.stream().mapToDouble(AnomalyOutput.DetectedAnomaly::getSeverityScore).max().orElse(0.7),
                highSeverity,
                context.workspaceId(),
                context.userId()
        ));
    }

    private List<AnomalyOutput.MitigationStep> generateMitigationSteps(
            List<AnomalyOutput.DetectedAnomaly> anomalies
    ) {
        List<AnomalyOutput.MitigationStep> steps = new ArrayList<>();

        for (AnomalyOutput.DetectedAnomaly anomaly : anomalies) {
            switch (anomaly.type()) {
                case VELOCITY_DROP -> steps.add(new AnomalyOutput.MitigationStep(
                        "mit-velocity",
                        "Review Team Capacity",
                        "Investigate blockers and resource constraints affecting velocity",
                        AnomalyOutput.MitigationStep.MitigationPriority.HIGH,
                        AnomalyOutput.MitigationStep.MitigationType.IMMEDIATE,
                        Map.of("action", "review-blockers")
                ));
                case SECURITY_THREAT -> steps.add(new AnomalyOutput.MitigationStep(
                        "mit-security",
                        "Security Investigation Required",
                        "Initiate security review and audit recent access patterns",
                        AnomalyOutput.MitigationStep.MitigationPriority.URGENT,
                        AnomalyOutput.MitigationStep.MitigationType.IMMEDIATE,
                        Map.of("action", "security-audit")
                ));
                case QUALITY_DEGRADATION -> steps.add(new AnomalyOutput.MitigationStep(
                        "mit-quality",
                        "Quality Review",
                        "Review recent changes and increase test coverage",
                        AnomalyOutput.MitigationStep.MitigationPriority.MEDIUM,
                        AnomalyOutput.MitigationStep.MitigationType.SHORT_TERM,
                        Map.of("action", "quality-review")
                ));
                default -> steps.add(new AnomalyOutput.MitigationStep(
                        "mit-investigate",
                        "Investigate Anomaly",
                        "Further investigation required to determine root cause",
                        AnomalyOutput.MitigationStep.MitigationPriority.MEDIUM,
                        AnomalyOutput.MitigationStep.MitigationType.SHORT_TERM,
                        Map.of("anomalyId", anomaly.id())
                ));
            }
        }

        return steps;
    }

    @Override
    protected Promise<Map<String, AgentHealth.DependencyStatus>> doHealthCheck() {
        return mlService.healthCheck()
                .map(healthy -> Map.of(
                        "mlService", healthy ? AgentHealth.DependencyStatus.HEALTHY : AgentHealth.DependencyStatus.UNHEALTHY,
                        "alertService", AgentHealth.DependencyStatus.HEALTHY,
                        "metricStore", AgentHealth.DependencyStatus.HEALTHY
                ));
    }

    // Service interfaces and internal types

    private record DetectionResult(List<RawAnomaly> anomalies, AnomalyOutput.Baseline baseline) {}

    private record RawAnomaly(
            String metricName,
            String timestamp,
            double value,
            double baselineValue,
            double confidence,
            List<String> algorithms
    ) {}

    /**
     * ML Service for anomaly detection.
     */
    public interface AnomalyMLService {
        Promise<List<RawAnomaly>> detectWithIsolationForest(
                List<AnomalyInput.MetricDataPoint> metrics,
                AnomalyOutput.Baseline baseline,
                double sensitivity
        );

        Promise<List<RawAnomaly>> detectWithLSTM(
                List<AnomalyInput.MetricDataPoint> timeSeriesData,
                int lookbackWindow
        );

        Promise<List<RawAnomaly>> detectWithStatistical(
                List<AnomalyInput.MetricDataPoint> metrics,
                AnomalyOutput.Baseline baseline,
                double sigmaThreshold
        );

        Promise<Boolean> healthCheck();
    }

    /**
     * Alert service for notifications.
     */
    public interface AlertService {
        Promise<Void> trigger(AlertRequest request);
    }

    /**
     * Metric store for baseline data.
     */
    public interface MetricStore {
        Promise<AnomalyOutput.Baseline> getBaseline(
                String workspaceId,
                AnomalyInput.AnomalyMetricType metricType,
                String timeWindow
        );
    }

    /**
     * Alert request.
     */
    public record AlertRequest(
            String type,
            double severity,
            List<AnomalyOutput.DetectedAnomaly> anomalies,
            String workspaceId,
            String userId
    ) {}
}
