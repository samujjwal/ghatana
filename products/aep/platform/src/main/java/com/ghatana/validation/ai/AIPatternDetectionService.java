package com.ghatana.validation.ai;

import com.ghatana.platform.domain.domain.Severity;
import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * AI-powered pattern detection and suggestion service consolidated from event-core.
 * This service provides intelligent pattern recognition, anomaly detection, and
 * automated pattern suggestions for event streams.
 
 *
 * @doc.type interface
 * @doc.purpose Aipattern detection service
 * @doc.layer core
 * @doc.pattern Service
*/
public interface AIPatternDetectionService {
    
    /**
     * Analyzes events to detect patterns automatically.
     * 
     * @param events List of events to analyze
     * @param analysisConfig Configuration for pattern analysis
     * @return Promise completing with detected patterns
     */
    Promise<List<DetectedPattern>> detectPatterns(List<Event> events, PatternAnalysisConfig analysisConfig);
    
    /**
     * Suggests patterns based on event characteristics and historical data.
     * 
     * @param eventType The event type to suggest patterns for
     * @param context Additional context for pattern suggestions
     * @return Promise completing with pattern suggestions
     */
    Promise<List<PatternSuggestion>> suggestPatterns(String eventType, Map<String, Object> context);
    
    /**
     * Validates a pattern against historical events to assess effectiveness.
     * 
     * @param pattern The pattern to validate
     * @param validationEvents Historical events for validation
     * @return Promise completing with validation results
     */
    Promise<PatternValidationResult> validatePattern(EventPattern pattern, List<Event> validationEvents);
    
    /**
     * Detects anomalies in event streams using AI models.
     * 
     * @param events Recent events to analyze for anomalies
     * @param baselineConfig Configuration for baseline comparison
     * @return Promise completing with anomaly detection results
     */
    Promise<ValidationAnomalyDetectionResult> detectAnomalies(List<Event> events, ValidationAnomalyDetectionConfig baselineConfig);
    
    /**
     * Provides explainability for pattern detection decisions.
     * 
     * @param pattern The pattern to explain
     * @param events The events that triggered the pattern
     * @return Promise completing with explanation
     */
    Promise<PatternExplanation> explainPattern(DetectedPattern pattern, List<Event> events);
    
    // ==================== DATA CLASSES ====================
    
    /**
     * Configuration for pattern analysis
     */
    record PatternAnalysisConfig(
        int minEventCount,
        double confidenceThreshold,
        long timeWindowMs,
        Map<String, Object> algorithmParameters
    ) {}
    
    /**
     * A detected pattern in event data
     */
    record DetectedPattern(
        String id,
        String name,
        String description,
        double confidence,
        PatternType type,
        Map<String, Object> parameters,
        List<Event> matchingEvents,
        long detectionTime
    ) {}
    
    /**
     * A suggested pattern for consideration
     */
    record PatternSuggestion(
        String suggestedName,
        String description,
        PatternType type,
        double relevanceScore,
        Map<String, Object> suggestedParameters,
        String rationale,
        List<String> basedOnEventTypes
    ) {}
    
    /**
     * Result of pattern validation
     */
    record PatternValidationResult(
        String patternId,
        boolean isValid,
        double accuracy,
        double precision,
        double recall,
        int truePositives,
        int falsePositives,
        int falseNegatives,
        List<ValidationIssue> issues,
        Map<String, Object> detailedMetrics
    ) {}
    
    /**
     * Result of anomaly detection
     */
    record ValidationAnomalyDetectionResult(
        List<DetectedAnomaly> anomalies,
        double overallAnomalyScore,
        ValidationAnomalyDetectionConfig config,
        long analysisTime,
        Map<String, Object> modelMetrics
    ) {}
    
    /**
     * A detected anomaly
     */
    record DetectedAnomaly(
        Event event,
        AnomalyType type,
        double severityScore,
        String description,
        Map<String, Object> anomalyFeatures,
        List<String> affectedDimensions
    ) {}
    
    /**
     * Explanation of a pattern detection
     */
    record PatternExplanation(
        String patternId,
        String explanation,
        List<ExplanationFactor> keyFactors,
        Map<String, Double> featureImportance,
        List<Event> exemplarEvents,
        String visualizationData
    ) {}
    
    /**
     * A factor contributing to pattern detection
     */
    record ExplanationFactor(
        String factorName,
        String description,
        double importance,
        Object value
    ) {}
    
    /**
     * Validation issue found during pattern validation
     */
    record ValidationIssue(
        String issueType,
        String description,
        Severity severity,
        List<Event> affectedEvents
    ) {}
    
    /**
     * Event pattern definition
     */
    record EventPattern(
        String id,
        String name,
        String description,
        PatternType type,
        Map<String, Object> matchingCriteria,
        Map<String, Object> configuration
    ) {}
    
    /**
     * Anomaly detection configuration
     */
    record ValidationAnomalyDetectionConfig(
        String algorithm,
        double sensitivityThreshold,
        long baselineWindowMs,
        Map<String, Object> modelParameters,
        List<String> monitoredFeatures
    ) {}
    
    // ==================== ENUMS ====================
    
    enum PatternType {
        SEQUENCE,       // Sequential pattern in events
        FREQUENCY,      // Frequency-based pattern
        CORRELATION,    // Correlation between event attributes
        TEMPORAL,       // Time-based pattern
        SPATIAL,        // Geographic/spatial pattern
        BEHAVIORAL,     // User/entity behavior pattern
        ANOMALY         // Anomalous pattern
    }
    
    enum AnomalyType {
        STATISTICAL,    // Statistical anomaly
        CONTEXTUAL,     // Context-dependent anomaly
        COLLECTIVE,     // Group of events forming anomaly
        POINT,          // Single event anomaly
        TREND,          // Trend-based anomaly
        SEASONAL        // Seasonal deviation anomaly
    }
    
}
