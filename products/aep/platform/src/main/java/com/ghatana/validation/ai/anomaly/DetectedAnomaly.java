package com.ghatana.validation.ai.anomaly;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.validation.ai.AIPatternDetectionService;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Internal representation of a detected anomaly used by the validation module.
 * The structure mirrors {@link AIPatternDetectionService.DetectedAnomaly} to
 * simplify conversion when returning results through the service interface.
 
 *
 * @doc.type class
 * @doc.purpose Detected anomaly
 * @doc.layer core
 * @doc.pattern Component
*/
public class DetectedAnomaly {
    private final Event event;
    private final AIPatternDetectionService.AnomalyType type;
    private final double severityScore;
    private final String description;
    private final Map<String, Object> anomalyFeatures;
    private final List<String> affectedDimensions;

    public DetectedAnomaly(Event event,
                           AIPatternDetectionService.AnomalyType type,
                           double severityScore,
                           String description,
                           Map<String, Object> anomalyFeatures,
                           List<String> affectedDimensions) {
        this.event = Objects.requireNonNull(event, "Event cannot be null");
        this.type = Objects.requireNonNull(type, "Anomaly type cannot be null");
        this.severityScore = validateSeverity(severityScore);
        this.description = Objects.requireNonNull(description, "Description cannot be null");
        this.anomalyFeatures = Map.copyOf(Objects.requireNonNull(anomalyFeatures, "Anomaly features cannot be null"));
        this.affectedDimensions = List.copyOf(Objects.requireNonNull(affectedDimensions, "Affected dimensions cannot be null"));
    }

    private double validateSeverity(double severity) {
        if (severity < 0.0 || severity > 1.0) {
            throw new IllegalArgumentException("Severity must be between 0.0 and 1.0");
        }
        return severity;
    }

    public Event event() { return event; }
    public AIPatternDetectionService.AnomalyType type() { return type; }
    public double severityScore() { return severityScore; }
    public String description() { return description; }
    public Map<String, Object> anomalyFeatures() { return anomalyFeatures; }
    public List<String> affectedDimensions() { return affectedDimensions; }

    // Alias methods used by existing code
    public Event getEvent() { return event(); }
    public AIPatternDetectionService.AnomalyType getType() { return type(); }
    public double getSeverity() { return severityScore(); }
    public String getDescription() { return description(); }
    public Map<String, Object> getDetails() { return anomalyFeatures(); }
    public List<String> getAffectedDimensions() { return affectedDimensions(); }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Event event;
        private AIPatternDetectionService.AnomalyType type = AIPatternDetectionService.AnomalyType.POINT;
        private double severityScore = 0.5;
        private String description = "";
        private Map<String, Object> anomalyFeatures = Map.of();
        private List<String> affectedDimensions = List.of();

        public Builder event(Event event) {
            this.event = event;
            return this;
        }

        public Builder type(AIPatternDetectionService.AnomalyType type) {
            this.type = type;
            return this;
        }

        public Builder severityScore(double severityScore) {
            this.severityScore = severityScore;
            return this;
        }

        public Builder description(String description) {
            this.description = description != null ? description : "";
            return this;
        }

        public Builder anomalyFeatures(Map<String, Object> features) {
            this.anomalyFeatures = features != null ? Map.copyOf(features) : Map.of();
            return this;
        }

        public Builder affectedDimensions(List<String> dimensions) {
            this.affectedDimensions = dimensions != null ? List.copyOf(dimensions) : List.of();
            return this;
        }

        public DetectedAnomaly build() {
            return new DetectedAnomaly(event, type, severityScore, description, anomalyFeatures, affectedDimensions);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DetectedAnomaly that = (DetectedAnomaly) o;
        return Double.compare(that.severityScore, severityScore) == 0 &&
               Objects.equals(event, that.event) &&
               type == that.type &&
               Objects.equals(description, that.description) &&
               Objects.equals(anomalyFeatures, that.anomalyFeatures) &&
               Objects.equals(affectedDimensions, that.affectedDimensions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(event, type, severityScore, description, anomalyFeatures, affectedDimensions);
    }

    @Override
    public String toString() {
        return "DetectedAnomaly{" +
               "type=" + type +
               ", severityScore=" + severityScore +
               ", description='" + description + '\'' +
               '}';
    }
}
