package com.ghatana.core.integration;

import com.ghatana.core.workflow.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects workflow anomalies using pattern operators.
 *
 * <p><b>Purpose</b><br>
 * Applies CEP patterns to detect anomalous workflow behaviors:
 * - Multiple rejections (rejection loop)
 * - Escalations without resolution
 * - SLA breaches across org unit
 * - Unusual approval chains
 * - Deadlock patterns (no progress)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowAnomalyDetector detector = new WorkflowAnomalyDetector();
 *
 * // Define anomaly patterns
 * detector.registerAnomalyPattern("rejection-loop",
 *     3,  // 3+ rejections
 *     600000,  // 10 minutes
 *     "task");
 *
 * // Monitor tasks
 * detector.recordTaskEvent(task);
 *
 * // Check for anomalies
 * List<Anomaly> anomalies = detector.detectAnomalies(task);
 * }</pre>
 *
 * <p><b>Anomaly Types</b><br>
 * - REJECTION_LOOP: Task rejected multiple times
 * - ESCALATION_CHAIN: Multiple escalations
 * - SLA_BREACH_PATTERN: Consistent SLA violations
 * - DEADLOCK: No state change for extended period
 * - UNUSUAL_APPROVERS: Approval from unexpected people
 *
 * @doc.type class
 * @doc.purpose Workflow anomaly detection using patterns
 * @doc.layer core
 * @doc.pattern Observer, Strategy
 */
public class WorkflowAnomalyDetector {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowAnomalyDetector.class);

    public enum AnomalyType {
        REJECTION_LOOP,
        ESCALATION_CHAIN,
        SLA_BREACH_PATTERN,
        DEADLOCK,
        UNUSUAL_APPROVERS,
        APPROVAL_TIMEOUT
    }

    private final Map<String, AnomalyPattern> patterns;
    private final Map<String, TaskEventHistory> taskHistory;
    private final List<AnomalyListener> listeners;

    /**
     * Create workflow anomaly detector.
     */
    public WorkflowAnomalyDetector() {
        this.patterns = new ConcurrentHashMap<>();
        this.taskHistory = new ConcurrentHashMap<>();
        this.listeners = Collections.synchronizedList(new ArrayList<>());

        registerDefaultPatterns();
        logger.debug("Created WorkflowAnomalyDetector");
    }

    /**
     * Register default anomaly patterns.
     */
    private void registerDefaultPatterns() {
        // Rejection loop: 3+ rejections in 10 minutes
        registerAnomalyPattern("rejection-loop",
            AnomalyType.REJECTION_LOOP,
            3,
            600000);

        // Escalation chain: 2+ escalations in 30 minutes
        registerAnomalyPattern("escalation-chain",
            AnomalyType.ESCALATION_CHAIN,
            2,
            1800000);

        // Deadlock: No progress for 4 hours
        registerAnomalyPattern("deadlock",
            AnomalyType.DEADLOCK,
            1,
            14400000);

        // SLA breach: 2+ breaches in 1 hour
        registerAnomalyPattern("sla-breach-pattern",
            AnomalyType.SLA_BREACH_PATTERN,
            2,
            3600000);
    }

    /**
     * Register anomaly pattern.
     *
     * @param patternName Pattern name
     * @param anomalyType Type of anomaly
     * @param threshold Occurrence threshold
     * @param windowMillis Time window
     */
    public void registerAnomalyPattern(String patternName, AnomalyType anomalyType,
                                       int threshold, long windowMillis) {
        patterns.put(patternName, new AnomalyPattern(patternName, anomalyType, threshold, windowMillis));
        logger.info("Registered anomaly pattern: {} ({} threshold: {} in {}ms)",
                   patternName, anomalyType, threshold, windowMillis);
    }

    /**
     * Record task event for anomaly detection.
     *
     * @param task Task to monitor
     * @param eventType Type of event
     */
    public void recordTaskEvent(WorkflowTask task, String eventType) {
        TaskEventHistory history = taskHistory.computeIfAbsent(
            task.getTaskId(),
            k -> new TaskEventHistory(task.getTaskId())
        );

        String normalizedEventType = normalizeEventType(eventType);
        history.recordEvent(normalizedEventType, System.currentTimeMillis());
    }

    /**
     * Detect anomalies for task.
     *
     * @param task Task to analyze
     * @return List of detected anomalies
     */
    public List<Anomaly> detectAnomalies(WorkflowTask task) {
        List<Anomaly> anomalies = new ArrayList<>();
        TaskEventHistory history = taskHistory.get(task.getTaskId());

        if (history == null) {
            return anomalies;
        }

        long now = System.currentTimeMillis();

        // Check each pattern
        for (AnomalyPattern pattern : patterns.values()) {
            int eventCount = history.countEventsInWindow(pattern.anomalyType.name(), now, pattern.windowMillis);

            if (eventCount >= pattern.threshold) {
                Anomaly anomaly = new Anomaly(
                    task.getTaskId(),
                    pattern.anomalyType,
                    eventCount,
                    pattern.threshold,
                    now
                );
                anomalies.add(anomaly);

                logger.warn("Detected anomaly: {} - {} occurrences (threshold: {})",
                           pattern.anomalyType, eventCount, pattern.threshold);

                notifyListeners(anomaly);
            }
        }

        return anomalies;
    }

    private String normalizeEventType(String eventType) {
        if (eventType == null) {
            return "";
        }

        String normalized = eventType.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "REJECTION":
            case "REJECTED":
                return AnomalyType.REJECTION_LOOP.name();
            case "ESCALATION":
            case "ESCALATED":
                return AnomalyType.ESCALATION_CHAIN.name();
            case "SLA_BREACH":
            case "SLA_VIOLATION":
                return AnomalyType.SLA_BREACH_PATTERN.name();
            case "DEADLOCK":
            case "NO_PROGRESS":
                return AnomalyType.DEADLOCK.name();
            case "APPROVAL_TIMEOUT":
            case "TIMEOUT":
                return AnomalyType.APPROVAL_TIMEOUT.name();
            default:
                return normalized;
        }
    }

    /**
     * Register anomaly listener.
     *
     * @param listener Listener to register
     */
    public void registerListener(AnomalyListener listener) {
        listeners.add(listener);
    }

    /**
     * Notify listeners of anomaly.
     *
     * @param anomaly Detected anomaly
     */
    private void notifyListeners(Anomaly anomaly) {
        for (AnomalyListener listener : listeners) {
            try {
                listener.onAnomalyDetected(anomaly);
            } catch (Exception e) {
                logger.error("Error notifying anomaly listener", e);
            }
        }
    }

    /**
     * Get task history.
     *
     * @param taskId Task identifier
     * @return Optional task history
     */
    public Optional<TaskEventHistory> getTaskHistory(String taskId) {
        return Optional.ofNullable(taskHistory.get(taskId));
    }

    /**
     * Anomaly pattern definition.
     */
    private static class AnomalyPattern {
        private final String patternName;
        private final AnomalyType anomalyType;
        private final int threshold;
        private final long windowMillis;

        AnomalyPattern(String patternName, AnomalyType anomalyType, int threshold, long windowMillis) {
            this.patternName = patternName;
            this.anomalyType = anomalyType;
            this.threshold = threshold;
            this.windowMillis = windowMillis;
        }
    }

    /**
     * Task event history.
     */
    public static class TaskEventHistory {
        private final String taskId;
        private final List<WorkflowEvent> events;

        public TaskEventHistory(String taskId) {
            this.taskId = taskId;
            this.events = Collections.synchronizedList(new ArrayList<>());
        }

        public void recordEvent(String eventType, long timestamp) {
            events.add(new WorkflowEvent(eventType, timestamp));
        }

        public int countEventsInWindow(String eventType, long now, long windowMillis) {
            long startTime = now - windowMillis;
            return (int) events.stream()
                .filter(e -> e.eventType.equals(eventType) && e.timestamp >= startTime)
                .count();
        }

        public List<WorkflowEvent> getEvents() {
            return Collections.unmodifiableList(events);
        }

        public static class WorkflowEvent {
            private final String eventType;
            private final long timestamp;

            WorkflowEvent(String eventType, long timestamp) {
                this.eventType = eventType;
                this.timestamp = timestamp;
            }

            public String getEventType() {
                return eventType;
            }

            public long getTimestamp() {
                return timestamp;
            }
        }
    }

    /**
     * Detected anomaly.
     */
    public static class Anomaly {
        private final String taskId;
        private final AnomalyType type;
        private final int occurrenceCount;
        private final int threshold;
        private final long detectedAt;

        public Anomaly(String taskId, AnomalyType type, int occurrenceCount, int threshold, long detectedAt) {
            this.taskId = taskId;
            this.type = type;
            this.occurrenceCount = occurrenceCount;
            this.threshold = threshold;
            this.detectedAt = detectedAt;
        }

        public String getTaskId() {
            return taskId;
        }

        public AnomalyType getType() {
            return type;
        }

        public int getOccurrenceCount() {
            return occurrenceCount;
        }

        public int getThreshold() {
            return threshold;
        }

        public long getDetectedAt() {
            return detectedAt;
        }

        @Override
        public String toString() {
            return String.format("Anomaly{task=%s, type=%s, count=%d (threshold=%d)}",
                    taskId, type, occurrenceCount, threshold);
        }
    }

    /**
     * Anomaly listener interface.
     */
    @FunctionalInterface
    public interface AnomalyListener {
        void onAnomalyDetected(Anomaly anomaly);
    }
}

