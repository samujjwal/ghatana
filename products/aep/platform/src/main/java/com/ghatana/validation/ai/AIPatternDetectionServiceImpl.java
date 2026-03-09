package com.ghatana.validation.ai;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.observability.Metrics;
import com.ghatana.validation.ai.AIPatternDetectionService.ValidationAnomalyDetectionConfig;
import com.ghatana.validation.ai.AIPatternDetectionService.ValidationAnomalyDetectionResult;
import com.ghatana.validation.ai.AIPatternDetectionService.DetectedAnomaly;
import com.ghatana.validation.ai.AIPatternDetectionService.DetectedPattern;
import com.ghatana.validation.ai.AIPatternDetectionService.EventPattern;
import com.ghatana.validation.ai.AIPatternDetectionService.ExplanationFactor;
import com.ghatana.validation.ai.AIPatternDetectionService.PatternAnalysisConfig;
import com.ghatana.validation.ai.AIPatternDetectionService.PatternExplanation;
import com.ghatana.validation.ai.AIPatternDetectionService.PatternSuggestion;
import com.ghatana.validation.ai.AIPatternDetectionService.PatternType;
import com.ghatana.validation.ai.AIPatternDetectionService.PatternValidationResult;
import com.ghatana.validation.ai.anomaly.AnomalyDetector;
import com.ghatana.validation.ai.anomaly.DefaultAnomalyDetector;
import com.ghatana.validation.ai.detectors.CorrelationPatternDetector;
import com.ghatana.validation.ai.detectors.FrequencyPatternDetector;
import com.ghatana.validation.ai.detectors.PatternDetector;
import com.ghatana.validation.ai.detectors.SequencePatternDetector;
import com.ghatana.validation.ai.detectors.TemporalPatternDetector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Default implementation of AI pattern detection service composed from detector modules.
 
 *
 * @doc.type class
 * @doc.purpose Aipattern detection service implementation
 * @doc.layer core
 * @doc.pattern Service
*/
public class AIPatternDetectionServiceImpl implements AIPatternDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(AIPatternDetectionServiceImpl.class);

    private final Metrics metrics;
    private final Map<String, PatternDetector> detectors;
    private final AnomalyDetector anomalyDetector;
    private final ExecutorService blockingExecutor;

    public AIPatternDetectionServiceImpl(Metrics metrics) {
        this.metrics = metrics;
        this.detectors = initializeDetectors();
        this.anomalyDetector = new DefaultAnomalyDetector();
        this.blockingExecutor = Executors.newFixedThreadPool(4);
    }

    @Override
    public Promise<List<DetectedPattern>> detectPatterns(List<Event> events, PatternAnalysisConfig analysisConfig) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            long startTime = System.currentTimeMillis();

            try {
                List<DetectedPattern> allPatterns = new ArrayList<>();
                for (Map.Entry<String, PatternDetector> entry : detectors.entrySet()) {
                    PatternDetector detector = entry.getValue();
                    logger.debug("Running {} pattern detector on {} events", entry.getKey(), events.size());

                    List<DetectedPattern> detected = detector.detect(events, analysisConfig).stream()
                            .filter(pattern -> pattern.confidence() >= analysisConfig.confidenceThreshold())
                            .collect(Collectors.toList());
                    allPatterns.addAll(detected);
                }

                List<DetectedPattern> merged = mergeSimilarPatterns(allPatterns);

                metrics.timer("ai.pattern.detection.duration")
                        .record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);

                logger.info("Detected {} patterns from {} events", merged.size(), events.size());
                return merged;
            } catch (Exception e) {
                metrics.timer("ai.pattern.detection.duration")
                        .record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
                logger.error("Pattern detection failed", e);
                throw new RuntimeException("Pattern detection failed", e);
            }
        });
    }

    @Override
    public Promise<List<PatternSuggestion>> suggestPatterns(String eventType, Map<String, Object> context) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            long startTime = System.currentTimeMillis();

            try {
                List<PatternSuggestion> suggestions = new ArrayList<>();
                suggestions.addAll(generateEventTypePatterns(eventType));
                suggestions.addAll(generateContextualPatterns(context));
                suggestions.addAll(generateHistoricalPatterns(eventType));

                suggestions.sort((a, b) -> Double.compare(b.relevanceScore(), a.relevanceScore()));

                metrics.timer("ai.pattern.suggestions.duration")
                        .record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
                metrics.timer("ai.pattern.suggestions.generated")
                        .record(1, TimeUnit.MILLISECONDS);

                logger.info("Generated {} pattern suggestions for event type {}", suggestions.size(), eventType);
                return suggestions.stream().limit(10).collect(Collectors.toList());
            } catch (Exception e) {
                metrics.timer("ai.pattern.suggestions.failed")
                        .record(1, TimeUnit.MILLISECONDS);
                logger.error("Pattern suggestion failed", e);
                throw new RuntimeException("Pattern suggestion failed", e);
            }
        });
    }

    @Override
    public Promise<PatternValidationResult> validatePattern(EventPattern pattern, List<Event> validationEvents) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            long startTime = System.currentTimeMillis();

            try {
                PatternMatcher matcher = new PatternMatcher(pattern);
                ValidationMetrics metricsSnapshot = new ValidationMetrics();

                for (Event event : validationEvents) {
                    boolean shouldMatch = matcher.shouldMatch(event);
                    boolean actualMatch = matcher.matches(event);

                    if (shouldMatch && actualMatch) {
                        metricsSnapshot.truePositives++;
                    } else if (!shouldMatch && !actualMatch) {
                        metricsSnapshot.trueNegatives++;
                    } else if (shouldMatch) {
                        metricsSnapshot.falseNegatives++;
                    } else {
                        metricsSnapshot.falsePositives++;
                    }
                }

                PatternValidationResult result = calculateValidationResult(pattern, metricsSnapshot);

                metrics.timer("ai.pattern.validation.duration")
                        .record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
                metrics.timer("ai.pattern.validation.completed")
                        .record(1, TimeUnit.MILLISECONDS);

                logger.info("Validated pattern {} with accuracy {}", pattern.id(), result.accuracy());
                return result;
            } catch (Exception e) {
                metrics.timer("ai.pattern.validation.failed")
                        .record(1, TimeUnit.MILLISECONDS);
                logger.error("Pattern validation failed", e);
                throw new RuntimeException("Pattern validation failed", e);
            }
        });
    }

    @Override
    public Promise<AIPatternDetectionService.ValidationAnomalyDetectionResult> detectAnomalies(
            List<Event> events,
            AIPatternDetectionService.ValidationAnomalyDetectionConfig config) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            long startTime = System.currentTimeMillis();

            try {
                com.ghatana.validation.ai.anomaly.ValidationAnomalyDetectionConfig localConfig = mapToLocalConfig(config);
                com.ghatana.validation.ai.anomaly.ValidationAnomalyDetectionResult localResult = anomalyDetector.detectAnomalies(events, localConfig);

                metrics.timer("ai.anomaly.detection.duration")
                        .record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
                metrics.timer("ai.anomaly.detection.score")
                        .record((long) (localResult.getConfidenceScore() * 1000), TimeUnit.MILLISECONDS);

                logger.info("Detected {} anomalies with overall score {}", localResult.getAnomalies().size(), localResult.getConfidenceScore());

                return mapToServiceResult(localResult, config);
            } catch (Exception e) {
                metrics.timer("ai.anomaly.detection.duration")
                        .record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
                logger.error("Anomaly detection failed", e);
                throw new RuntimeException("Anomaly detection failed", e);
            }
        });
    }

    @Override
    public Promise<PatternExplanation> explainPattern(DetectedPattern pattern, List<Event> events) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            long startTime = System.currentTimeMillis();

            try {
                List<ExplanationFactor> factors = analyzePatternFactors(pattern, events);
                Map<String, Double> featureImportance = calculateFeatureImportance(pattern, events);
                List<Event> exemplars = findExemplarEvents(pattern, events);
                Map<String, Object> visualization = generateVisualizationData(pattern, events);
                String explanationText = generateExplanationText(pattern, factors);

                metrics.timer("ai.pattern.explanation.duration")
                        .record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);

                return new PatternExplanation(
                        pattern.id(),
                        explanationText,
                        factors,
                        featureImportance,
                        exemplars,
                        visualizationToString(visualization)
                );
            } catch (Exception e) {
                metrics.timer("ai.pattern.explanation.duration")
                        .record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
                logger.error("Pattern explanation failed", e);
                throw new RuntimeException("Pattern explanation failed", e);
            }
        });
    }

    // ==================== PRIVATE HELPERS ====================

    private Map<String, PatternDetector> initializeDetectors() {
        Map<String, PatternDetector> registry = new HashMap<>();
        registry.put("sequence", new SequencePatternDetector());
        registry.put("frequency", new FrequencyPatternDetector());
        registry.put("correlation", new CorrelationPatternDetector());
        registry.put("temporal", new TemporalPatternDetector());
        return registry;
    }

    private List<DetectedPattern> mergeSimilarPatterns(List<DetectedPattern> patterns) {
        Map<String, DetectedPattern> unique = new HashMap<>();
        for (DetectedPattern pattern : patterns) {
            String key = pattern.type() + "_" + pattern.name();
            DetectedPattern existing = unique.get(key);
            if (existing == null || pattern.confidence() > existing.confidence()) {
                unique.put(key, pattern);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private List<PatternSuggestion> generateEventTypePatterns(String eventType) {
        List<PatternSuggestion> suggestions = new ArrayList<>();
        suggestions.add(new PatternSuggestion(
                "Frequent Events",
                "Events that occur frequently in the stream",
                PatternType.FREQUENCY,
                0.8,
                Map.of("eventType", eventType, "threshold", 2.0),
                "Frequent occurrences may indicate abnormal load",
                List.of(eventType)
        ));
        suggestions.add(new PatternSuggestion(
                "Temporal Patterns",
                "Identify recurring time-based patterns",
                PatternType.TEMPORAL,
                0.75,
                Map.of("eventType", eventType, "timeWindow", "hourly"),
                "Helps identify periodic spikes or regressions",
                List.of(eventType)
        ));
        suggestions.add(new PatternSuggestion(
                "Event Sequences",
                "Common sequences of events",
                PatternType.SEQUENCE,
                0.7,
                Map.of("eventType", eventType, "window", "5m", "threshold", 10),
                "Sequences often precede incidents",
                List.of(eventType)
        ));
        return suggestions;
    }

    private List<PatternSuggestion> generateContextualPatterns(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return List.of();
        }
        return List.of();
    }

    private List<PatternSuggestion> generateHistoricalPatterns(String eventType) {
        return List.of();
    }

    private PatternValidationResult calculateValidationResult(EventPattern pattern, ValidationMetrics metricsSnapshot) {
        double total = metricsSnapshot.truePositives + metricsSnapshot.trueNegatives
                + metricsSnapshot.falsePositives + metricsSnapshot.falseNegatives;
        double accuracy = total == 0 ? 0.0 : (metricsSnapshot.truePositives + metricsSnapshot.trueNegatives) / total;
        double precision = (metricsSnapshot.truePositives + metricsSnapshot.falsePositives) == 0
                ? 0.0
                : (double) metricsSnapshot.truePositives / (metricsSnapshot.truePositives + metricsSnapshot.falsePositives);
        double recall = (metricsSnapshot.truePositives + metricsSnapshot.falseNegatives) == 0
                ? 0.0
                : (double) metricsSnapshot.truePositives / (metricsSnapshot.truePositives + metricsSnapshot.falseNegatives);

        return new PatternValidationResult(
                pattern.id(),
                accuracy >= 0.8,
                accuracy,
                precision,
                recall,
                metricsSnapshot.truePositives,
                metricsSnapshot.falsePositives,
                metricsSnapshot.falseNegatives,
                List.of(),
                Map.of(
                        "accuracy", accuracy,
                        "precision", precision,
                        "recall", recall,
                        "f1Score", (precision + recall) == 0 ? 0.0 : 2 * (precision * recall) / (precision + recall)
                )
        );
    }

    private List<Event> findExemplarEvents(DetectedPattern pattern, List<Event> events) {
        List<Event> matching = pattern.matchingEvents() != null ? pattern.matchingEvents() : List.of();
        if (!matching.isEmpty()) {
            return matching.stream().limit(3).collect(Collectors.toList());
        }
        return events.stream().limit(3).collect(Collectors.toList());
    }

    private String generateExplanationText(DetectedPattern pattern, List<ExplanationFactor> factors) {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("Pattern '%s' detected with %.2f%% confidence. ", pattern.name(), pattern.confidence() * 100));
        if (!factors.isEmpty()) {
            builder.append("Key contributing factors: ");
            for (int i = 0; i < Math.min(3, factors.size()); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(factors.get(i).description());
            }
        }
        return builder.toString();
    }

    private List<ExplanationFactor> analyzePatternFactors(DetectedPattern pattern, List<Event> events) {
        List<ExplanationFactor> factors = new ArrayList<>();
        List<Event> matching = pattern.matchingEvents() != null ? pattern.matchingEvents() : List.of();
        factors.add(new ExplanationFactor("Event Count", "Events contributing to the pattern", 0.8, matching.size()));
        factors.add(new ExplanationFactor("Confidence", "Detector confidence level", 0.9, pattern.confidence()));
        factors.add(new ExplanationFactor("Pattern Type", "Categorization of the pattern", 0.7, pattern.type()));
        return factors;
    }

    private Map<String, Double> calculateFeatureImportance(DetectedPattern pattern, List<Event> events) {
        Map<String, Double> importance = new HashMap<>();
        importance.put("timestamp", 0.6);
        importance.put("eventType", 0.8);
        importance.put("payload", 0.5);
        return importance;
    }

    private Map<String, Object> generateVisualizationData(DetectedPattern pattern, List<Event> events) {
        Map<String, Object> visualization = new HashMap<>();
        visualization.put("patternId", pattern.id());
        visualization.put("patternName", pattern.name());
        visualization.put("patternType", pattern.type());
        visualization.put("confidence", pattern.confidence());

        List<Event> sourceEvents = pattern.matchingEvents() != null && !pattern.matchingEvents().isEmpty()
                ? pattern.matchingEvents()
                : events;

        List<Map<String, Object>> eventData = sourceEvents.stream()
                .map(event -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("eventId", event.getId().toString());
                    data.put("eventType", event.getType());
                    data.put("timestamp", event.getTime()
                            .getOccurrenceTime()
                            .start()
                            .toInstant()
                            .toString());
                    return data;
                })
                .collect(Collectors.toList());
        visualization.put("events", eventData);

        PatternType patternType = pattern.type();
        if (patternType != null) {
            switch (patternType) {
                case FREQUENCY -> {
                    visualization.put("chartType", "line");
                    visualization.put("xAxis", "time");
                    visualization.put("yAxis", "count");
                }
                case SEQUENCE -> visualization.put("chartType", "sequence");
                case ANOMALY -> visualization.put("chartType", "scatter");
                case CORRELATION -> visualization.put("chartType", "heatmap");
                default -> visualization.put("chartType", "bar");
            }
        }

        return visualization;
    }

    private com.ghatana.validation.ai.anomaly.ValidationAnomalyDetectionConfig mapToLocalConfig(ValidationAnomalyDetectionConfig config) {
        Map<String, Object> parameters = new HashMap<>(config.modelParameters());
        parameters.putIfAbsent("monitoredFeatures", config.monitoredFeatures());
        parameters.putIfAbsent("baselineWindowMs", config.baselineWindowMs());
        return new com.ghatana.validation.ai.anomaly.ValidationAnomalyDetectionConfig(
                config.sensitivityThreshold(),
                "default",
                config.algorithm(),
                parameters
        );
    }

    private AIPatternDetectionService.ValidationAnomalyDetectionResult mapToServiceResult(
            com.ghatana.validation.ai.anomaly.ValidationAnomalyDetectionResult localResult,
            ValidationAnomalyDetectionConfig originalConfig) {
        List<AIPatternDetectionService.DetectedAnomaly> mappedAnomalies = localResult.getAnomalies().stream()
                .map(this::mapToServiceAnomaly)
                .collect(Collectors.toList());

        long analysisTime = 0L;
        Object evaluationTime = localResult.getMetadata().get("evaluationTimeMs");
        if (evaluationTime instanceof Number number) {
            analysisTime = number.longValue();
        }

        return new AIPatternDetectionService.ValidationAnomalyDetectionResult(
                mappedAnomalies,
                localResult.getConfidenceScore(),
                originalConfig,
                analysisTime,
                localResult.getMetadata()
        );
    }

    private AIPatternDetectionService.DetectedAnomaly mapToServiceAnomaly(
            com.ghatana.validation.ai.anomaly.DetectedAnomaly anomaly) {
        return new AIPatternDetectionService.DetectedAnomaly(
                anomaly.event(),
                anomaly.type(),
                anomaly.severityScore(),
                anomaly.description(),
                anomaly.anomalyFeatures(),
                anomaly.affectedDimensions()
        );
    }

    private String visualizationToString(Map<String, Object> visualization) {
        return visualization == null ? "{}" : visualization.toString();
    }

    private static class ValidationMetrics {
        int truePositives;
        int trueNegatives;
        int falsePositives;
        int falseNegatives;
    }

    private static class PatternMatcher {
        private final EventPattern pattern;

        PatternMatcher(EventPattern pattern) {
            this.pattern = pattern;
        }

        boolean matches(Event event) {
            return pattern.type() != null && pattern.type() == PatternType.SEQUENCE
                    ? event.getType().equals(pattern.name())
                    : event.getType().equals(pattern.name());
        }

        boolean shouldMatch(Event event) {
            return matches(event);
        }
    }
}
