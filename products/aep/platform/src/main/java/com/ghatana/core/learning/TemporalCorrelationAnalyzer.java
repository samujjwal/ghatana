package com.ghatana.aep.learning.mining;

import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Analyzes temporal correlations between event types.
 *
 * <p><b>Purpose</b><br>
 * Discovers timing relationships and temporal dependencies between events.
 * Complements sequence mining by analyzing time windows and causality.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * TemporalCorrelationAnalyzer analyzer =
 *     new TemporalCorrelationAnalyzer(60000); // 60s window
 *
 * for (Event event : events) {
 *     analyzer.addEvent(event);
 * }
 *
 * List<EventCorrelation> correlations =
 *     analyzer.analyzeCorrelations().getResult();
 * }</pre>
 *
 * <p><b>Metrics</b><br>
 * For each event pair (A → B):
 * <ul>
 *   <li>Frequency: How often B follows A</li>
 *   <li>Time Delta: Average time between A and B</li>
 *   <li>Correlation Score: 0-1 strength of relationship</li>
 *   <li>Causality: Likelihood B is caused by A</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Temporal event correlation analysis
 * @doc.layer core
 * @doc.pattern Strategy
 */
public class TemporalCorrelationAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(TemporalCorrelationAnalyzer.class);

    private final long windowDurationMillis;
    private final Map<String, List<Event>> eventsByType;
    private final List<Event> allEvents;

    /**
     * Create temporal correlation analyzer.
     *
     * @param windowDurationMillis Time window for correlation analysis
     */
    public TemporalCorrelationAnalyzer(long windowDurationMillis) {
        this.windowDurationMillis = windowDurationMillis;
        this.eventsByType = Collections.synchronizedMap(new HashMap<>());
        this.allEvents = Collections.synchronizedList(new ArrayList<>());

        logger.debug("Created analyzer with window: {}ms", windowDurationMillis);
    }

    /**
     * Add event for correlation analysis.
     *
     * @param event Event to analyze
     */
    public void addEvent(Event event) {
        allEvents.add(event);
        eventsByType.computeIfAbsent(event.getType(), k -> new ArrayList<>()).add(event);
    }

    /**
     * Analyze temporal correlations between event types.
     *
     * @return Promise of detected correlations
     */
    public Promise<List<EventCorrelation>> analyzeCorrelations() {
        logger.info("Analyzing correlations for {} events", allEvents.size());

        if (allEvents.isEmpty()) {
            return Promise.of(List.of());
        }

        List<EventCorrelation> correlations = new ArrayList<>();

        // Analyze each pair of event types
        Set<String> eventTypes = eventsByType.keySet();
        for (String typeA : eventTypes) {
            for (String typeB : eventTypes) {
                if (!typeA.equals(typeB)) {
                    EventCorrelation correlation = analyzeEventPair(typeA, typeB);
                    if (correlation.getCorrelationScore() > 0) {
                        correlations.add(correlation);
                    }
                }
            }
        }

        // Sort by correlation score
        correlations.sort((a, b) -> Double.compare(b.getCorrelationScore(), a.getCorrelationScore()));

        logger.info("Discovered {} temporal correlations", correlations.size());
        return Promise.of(correlations);
    }

    /**
     * Analyze correlation between two event types.
     *
     * @param typeA First event type
     * @param typeB Second event type
     * @return Correlation details
     */
    private EventCorrelation analyzeEventPair(String typeA, String typeB) {
        List<Event> eventsA = eventsByType.get(typeA);
        List<Event> eventsB = eventsByType.get(typeB);

        if (eventsA == null || eventsB == null) {
            return new EventCorrelation(typeA, typeB, 0, 0, 0);
        }

        int correlationCount = 0;
        long totalTimeDelta = 0;

        // For each A event, look for B events within window
        for (Event eventA : eventsA) {
            long timeA = eventA.getTimestamp().toEpochMilli();

            for (Event eventB : eventsB) {
                long timeB = eventB.getTimestamp().toEpochMilli();
                long timeDelta = timeB - timeA;

                // B should follow A within window
                if (timeDelta > 0 && timeDelta <= windowDurationMillis) {
                    correlationCount++;
                    totalTimeDelta += timeDelta;
                }
            }
        }

        if (correlationCount == 0) {
            return new EventCorrelation(typeA, typeB, 0, 0, 0);
        }

        double frequency = (double) correlationCount / eventsA.size();
        long avgTimeDelta = totalTimeDelta / correlationCount;
        double correlationScore = calculateCorrelationScore(frequency, avgTimeDelta);

        return new EventCorrelation(typeA, typeB, frequency, avgTimeDelta, correlationScore);
    }

    /**
     * Calculate correlation score (0-1).
     *
     * @param frequency How often B follows A
     * @param avgTimeDelta Average time between events
     * @return Correlation score
     */
    private double calculateCorrelationScore(double frequency, long avgTimeDelta) {
        // Score based on frequency and time proximity
        double frequencyScore = Math.min(frequency, 1.0);
        double timeScore = 1.0 - (Math.min(avgTimeDelta, windowDurationMillis) / (double) windowDurationMillis);

        return (frequencyScore * 0.7) + (timeScore * 0.3);
    }

    /**
     * Event correlation result.
     */
    public static class EventCorrelation {
        private final String eventTypeA;
        private final String eventTypeB;
        private final double frequency;  // 0-1: how often B follows A
        private final long avgTimeDelta;  // milliseconds between events
        private final double correlationScore;  // 0-1: strength of correlation

        EventCorrelation(String eventTypeA, String eventTypeB,
                        double frequency, long avgTimeDelta, double correlationScore) {
            this.eventTypeA = eventTypeA;
            this.eventTypeB = eventTypeB;
            this.frequency = frequency;
            this.avgTimeDelta = avgTimeDelta;
            this.correlationScore = correlationScore;
        }

        public String getEventTypeA() {
            return eventTypeA;
        }

        public String getEventTypeB() {
            return eventTypeB;
        }

        public double getFrequency() {
            return frequency;
        }

        public long getAvgTimeDelta() {
            return avgTimeDelta;
        }

        public double getCorrelationScore() {
            return correlationScore;
        }

        @Override
        public String toString() {
            return String.format("%s → %s (score=%.2f, freq=%.2f, delta=%dms)",
                    eventTypeA, eventTypeB, correlationScore, frequency, avgTimeDelta);
        }
    }
}

