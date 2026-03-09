/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.client.feedback;

import com.ghatana.datacloud.client.LearningSignal;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import lombok.Builder;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrates the continuous learning cycle from feedback to model updates.
 *
 * <p>
 * The LearningLoop is the central coordinator of the feedback-driven learning
 * system. It processes collected feedback, transforms it into learning signals,
 * and coordinates updates to patterns, models, and system behavior.
 *
 * <h2>Learning Cycle Phases</h2>
 * <ol>
 * <li><b>Collect</b>: Gather pending feedback from the collector</li>
 * <li><b>Transform</b>: Convert feedback events to learning signals</li>
 * <li><b>Aggregate</b>: Combine signals for the same reference</li>
 * <li><b>Apply</b>: Update models, patterns, and configurations</li>
 * <li><b>Verify</b>: Validate changes and rollback if necessary</li>
 * </ol>
 *
 * <h2>Learning Strategies</h2>
 * <ul>
 * <li><b>IMMEDIATE</b>: Apply updates as soon as thresholds are met</li>
 * <li><b>BATCHED</b>: Accumulate updates and apply periodically</li>
 * <li><b>GRADUAL</b>: Apply updates incrementally with confidence
 * weighting</li>
 * <li><b>SUPERVISED</b>: Queue updates for human review before applying</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * LearningLoop loop = LearningLoop.builder()
 *         .feedbackCollector(collector)
 *         .learningSignalStore(signalStore)
 *         .cycleInterval(Duration.ofSeconds(30))
 *         .strategy(LearningStrategy.GRADUAL)
 *         .build();
 * 
 * loop.addLearner(patternLearner);
 * loop.addLearner(modelLearner);
 * loop.start();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Orchestrate continuous learning from feedback
 * @doc.layer core
 * @doc.pattern Mediator, Observer, Strategy
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 * @see FeedbackCollector
 * @see LearningSignal
 */
@Builder
public class LearningLoop {

    private static final Logger LOG = LoggerFactory.getLogger(LearningLoop.class);

    /**
     * The feedback collector providing input events.
     */
    private final FeedbackCollector feedbackCollector;

    /**
     * Interval between learning cycles.
     */
    @Builder.Default
    private final Duration cycleInterval = Duration.ofSeconds(30);

    /**
     * Maximum feedback events to process per cycle.
     */
    @Builder.Default
    private final int maxEventsPerCycle = 500;

    /**
     * Minimum events required before triggering learning.
     */
    @Builder.Default
    private final int minEventsToLearn = 5;

    /**
     * The learning strategy to use.
     */
    @Builder.Default
    private final LearningStrategy strategy = LearningStrategy.GRADUAL;

    /**
     * Confidence threshold for applying updates.
     */
    @Builder.Default
    private final double confidenceThreshold = 0.7;

    /**
     * Whether to enable automatic rollback on errors.
     */
    @Builder.Default
    private final boolean autoRollback = true;

    /**
     * Maximum consecutive failures before pausing.
     */
    @Builder.Default
    private final int maxConsecutiveFailures = 3;

    // Registered learners
    @Builder.Default
    private final List<Learner> learners = new ArrayList<>();

    // Registered listeners
    @Builder.Default
    private final List<LearningListener> listeners = new ArrayList<>();

    // Runtime state
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicLong cycleCount = new AtomicLong(0);
    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    private final AtomicReference<Instant> lastCycleTime = new AtomicReference<>();
    private final AtomicReference<CycleReport> lastReport = new AtomicReference<>();
    private final ConcurrentHashMap<String, LearningState> learningStates = new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Starts the learning loop.
     *
     * @return Promise completing when started
     */
    public Promise<Void> start() {
        if (running.compareAndSet(false, true)) {
            LOG.info("Starting learning loop with strategy {} and interval {}",
                    strategy, cycleInterval);
            consecutiveFailures.set(0);
            scheduleNextCycle();
            return Promise.complete();
        }
        return Promise.ofException(new IllegalStateException("Learning loop already running"));
    }

    /**
     * Stops the learning loop.
     *
     * @return Promise completing when stopped
     */
    public Promise<Void> stop() {
        if (running.compareAndSet(true, false)) {
            LOG.info("Stopping learning loop after {} cycles", cycleCount.get());
            return Promise.complete();
        }
        return Promise.complete();
    }

    /**
     * Pauses the learning loop without stopping.
     */
    public void pause() {
        paused.set(true);
        LOG.info("Learning loop paused");
    }

    /**
     * Resumes a paused learning loop.
     */
    public void resume() {
        if (paused.compareAndSet(true, false)) {
            LOG.info("Learning loop resumed");
            consecutiveFailures.set(0);
        }
    }

    /**
     * Checks if the loop is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Checks if the loop is paused.
     *
     * @return true if paused
     */
    public boolean isPaused() {
        return paused.get();
    }

    /**
     * Triggers an immediate learning cycle.
     *
     * @return Promise containing the cycle report
     */
    public Promise<CycleReport> learnNow() {
        return runLearningCycle();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Learner Management
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Registers a learner.
     *
     * @param learner the learner to register
     */
    public void addLearner(Learner learner) {
        learners.add(learner);
        LOG.debug("Added learner: {}", learner.getName());
    }

    /**
     * Removes a learner.
     *
     * @param learner the learner to remove
     */
    public void removeLearner(Learner learner) {
        learners.remove(learner);
    }

    /**
     * Adds a learning listener.
     *
     * @param listener the listener to add
     */
    public void addListener(LearningListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a learning listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(LearningListener listener) {
        listeners.remove(listener);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Learning Cycle Execution
    // ═══════════════════════════════════════════════════════════════════════════

    private void scheduleNextCycle() {
        if (!running.get())
            return;
        LOG.debug("Scheduling next learning cycle in {}", cycleInterval);
        // In production, would use proper scheduler
    }

    private Promise<CycleReport> runLearningCycle() {
        if (paused.get()) {
            return Promise.of(CycleReport.skipped("Loop is paused"));
        }

        Instant cycleStart = Instant.now();
        long cycleNum = cycleCount.incrementAndGet();
        LOG.debug("Starting learning cycle #{}", cycleNum);

        CycleReport.CycleReportBuilder reportBuilder = CycleReport.builder()
                .cycleNumber(cycleNum)
                .startTime(cycleStart)
                .strategy(strategy);

        // Phase 1: Collect pending feedback
        return feedbackCollector.getPending(maxEventsPerCycle)
                .then(events -> {
                    reportBuilder.eventsCollected(events.size());

                    if (events.size() < minEventsToLearn) {
                        LOG.debug("Insufficient events ({}) for learning, skipping cycle",
                                events.size());
                        return Promise.of(CycleReport.skipped(
                                "Insufficient events: " + events.size()));
                    }

                    // Phase 2: Transform to learning signals
                    List<LearningSignal> signals = transformToSignals(events);
                    reportBuilder.signalsGenerated(signals.size());

                    // Phase 3: Aggregate by reference
                    Map<String, AggregatedSignals> aggregated = aggregateSignals(signals);
                    reportBuilder.referencesAffected(aggregated.size());

                    // Phase 4: Apply learning based on strategy
                    return applyLearning(aggregated)
                            .then(applyResult -> {
                                reportBuilder.updatesApplied(applyResult.applied);
                                reportBuilder.updatesFailed(applyResult.failed);
                                reportBuilder.rollbacks(applyResult.rollbacks);

                                // Phase 5: Mark events as processed
                                List<String> processedIds = events.stream()
                                        .map(FeedbackEvent::getId)
                                        .toList();

                                return feedbackCollector.markProcessed(processedIds);
                            })
                            .map(markedCount -> {
                                CycleReport report = reportBuilder
                                        .eventsProcessed(markedCount)
                                        .endTime(Instant.now())
                                        .success(true)
                                        .build();

                                lastCycleTime.set(cycleStart);
                                lastReport.set(report);
                                consecutiveFailures.set(0);

                                notifyListeners(report);

                                LOG.info("Learning cycle #{} complete: {} events, {} signals, " +
                                        "{} updates applied",
                                        cycleNum, report.getEventsCollected(),
                                        report.getSignalsGenerated(), report.getUpdatesApplied());

                                if (running.get()) {
                                    scheduleNextCycle();
                                }

                                return report;
                            });
                })
                .then(report -> Promise.of(report),
                        error -> {
                            LOG.error("Learning cycle #{} failed", cycleNum, error);

                            long failures = consecutiveFailures.incrementAndGet();
                            if (failures >= maxConsecutiveFailures) {
                                LOG.warn("Max consecutive failures reached, pausing loop");
                                pause();
                            }

                            CycleReport failedReport = CycleReport.failed(error.getMessage());
                            lastReport.set(failedReport);

                            if (running.get() && !paused.get()) {
                                scheduleNextCycle();
                            }

                            return Promise.of(failedReport);
                        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Signal Transformation
    // ═══════════════════════════════════════════════════════════════════════════

    private List<LearningSignal> transformToSignals(List<FeedbackEvent> events) {
        List<LearningSignal> signals = new ArrayList<>();

        for (FeedbackEvent event : events) {
            LearningSignal.SignalType signalType = mapFeedbackToSignalType(event);
            double strength = calculateSignalStrength(event);

            Map<String, Object> features = new HashMap<>();
            features.put("feedbackType", event.getFeedbackType() != null ? event.getFeedbackType().name() : null);
            features.put("referenceType", event.getReferenceType() != null ? event.getReferenceType().name() : null);
            features.put("category", event.getCategory());
            features.put("sentiment", event.getSentiment() != null ? event.getSentiment().name() : null);

            Map<String, Object> metrics = new HashMap<>();
            metrics.put("strength", strength);
            metrics.put("confidence", event.getConfidence());
            metrics.put("score", event.getScore());
            metrics.put("sentimentNumeric",
                    event.getSentiment() != null ? event.getSentiment().getNumericValue() : null);

            Map<String, Object> context = new HashMap<>();
            context.put("referenceId", event.getReferenceId());
            context.put("outcome", event.getOutcome());
            context.put("expectedOutcome", event.getExpectedOutcome());
            context.put("comment", event.getComment());
            context.put("corrections", event.getCorrections());
            context.put("metadata", event.getMetadata());
            context.put("tags", event.getTags());

            LearningSignal signal = LearningSignal.builder()
                    .signalType(signalType)
                    .tenantId(event.getTenantId())
                    .correlationId(event.getReferenceId())
                    .source(LearningSignal.SignalSource.builder()
                            .plugin("feedback")
                            .collection(event.getReferenceType() != null ? event.getReferenceType().name() : null)
                            .operation(event.getFeedbackType() != null ? event.getFeedbackType().name() : null)
                            .actor(event.getSource() != null ? event.getSource().name() : null)
                            .metadata(Map.of())
                            .build())
                    .features(features)
                    .metrics(metrics)
                    .context(context)
                    .build();

            signals.add(signal);
        }

        return signals;
    }

    private LearningSignal.SignalType mapFeedbackToSignalType(FeedbackEvent event) {
        return switch (event.getFeedbackType()) {
            case OPERATIONAL -> LearningSignal.SignalType.OPERATIONAL;
            case OUTCOME -> LearningSignal.SignalType.PREDICTION_OUTCOME;
            case EXPLICIT, IMPLICIT, COMPARATIVE, EXPERT -> LearningSignal.SignalType.FEEDBACK;
        };
    }

    private double calculateSignalStrength(FeedbackEvent event) {
        double baseStrength = Math.abs(event.getScore());

        // Boost strength for explicit feedback
        if (event.getFeedbackType() == FeedbackEvent.FeedbackType.EXPLICIT) {
            baseStrength *= 1.5;
        }

        // Boost for expert/ground truth sources
        if (event.getSource() == FeedbackEvent.FeedbackSource.EXPERT
                || event.getSource() == FeedbackEvent.FeedbackSource.GROUND_TRUTH) {
            baseStrength *= 2.0;
        }

        // Weight by confidence
        baseStrength *= event.getConfidence();

        return Math.min(1.0, baseStrength);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Signal Aggregation
    // ═══════════════════════════════════════════════════════════════════════════

    private Map<String, AggregatedSignals> aggregateSignals(List<LearningSignal> signals) {
        Map<String, AggregatedSignals> aggregated = new HashMap<>();

        for (LearningSignal signal : signals) {
            String key = getReferenceId(signal);
            aggregated.computeIfAbsent(key, k -> new AggregatedSignals(k))
                    .add(signal);
        }

        return aggregated;
    }

    private static class AggregatedSignals {
        final String referenceId;
        final List<LearningSignal> signals = new ArrayList<>();
        double totalStrength = 0;
        double netSentiment = 0;
        int reinforcements = 0;
        int corrections = 0;

        AggregatedSignals(String referenceId) {
            this.referenceId = referenceId;
        }

        void add(LearningSignal signal) {
            signals.add(signal);
            double strength = getDoubleMetric(signal, "strength", 0.0);
            double sentiment = getDoubleMetric(signal, "sentimentNumeric", 0.0);

            totalStrength += strength;
            netSentiment += sentiment;

            if (sentiment >= 0.0) {
                reinforcements++;
            } else {
                corrections++;
            }
        }

        double getAverageStrength() {
            return signals.isEmpty() ? 0 : totalStrength / signals.size();
        }

        double getConfidence() {
            if (signals.isEmpty())
                return 0;
            return signals.stream()
                    .mapToDouble(signal -> getDoubleMetric(signal, "confidence", 0.5))
                    .average()
                    .orElse(0.5);
        }
    }

    private static String getReferenceId(LearningSignal signal) {
        if (signal.getCorrelationId() != null && !signal.getCorrelationId().isBlank()) {
            return signal.getCorrelationId();
        }
        Map<String, Object> context = signal.getContext();
        Object ref = context != null ? context.get("referenceId") : null;
        return ref != null ? ref.toString() : "unknown";
    }

    private static double getDoubleMetric(LearningSignal signal, String key, double defaultValue) {
        Map<String, Object> metrics = signal.getMetrics();
        if (metrics == null) {
            return defaultValue;
        }
        Object value = metrics.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return defaultValue;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Learning Application
    // ═══════════════════════════════════════════════════════════════════════════

    private Promise<ApplyResult> applyLearning(Map<String, AggregatedSignals> aggregated) {
        List<Promise<LearnerResult>> learnerPromises = new ArrayList<>();

        for (Learner learner : learners) {
            for (AggregatedSignals signals : aggregated.values()) {
                if (shouldApply(signals)) {
                    learnerPromises.add(applyToLearner(learner, signals));
                }
            }
        }

        return Promises.toList(learnerPromises)
                .map(results -> {
                    int applied = 0;
                    int failed = 0;
                    int rollbacks = 0;

                    for (LearnerResult result : results) {
                        if (result.success) {
                            applied++;
                        } else {
                            failed++;
                            if (result.rolledBack) {
                                rollbacks++;
                            }
                        }
                    }

                    return new ApplyResult(applied, failed, rollbacks);
                });
    }

    private boolean shouldApply(AggregatedSignals signals) {
        return switch (strategy) {
            case IMMEDIATE -> signals.getAverageStrength() >= 0.3;
            case BATCHED -> signals.signals.size() >= 3;
            case GRADUAL -> signals.getConfidence() >= confidenceThreshold;
            case SUPERVISED -> false; // Requires manual approval
        };
    }

    private Promise<LearnerResult> applyToLearner(Learner learner, AggregatedSignals signals) {
        return learner.learn(signals.signals)
                .then(success -> {
                    if (success) {
                        return Promise.of(new LearnerResult(true, false));
                    }

                    // Learning failed
                    if (autoRollback) {
                        return learner.rollback(signals.referenceId)
                                .map(rolledBack -> new LearnerResult(false, rolledBack));
                    }

                    return Promise.of(new LearnerResult(false, false));
                })
                .then(result -> Promise.of(result),
                        error -> {
                            LOG.warn("Learner {} failed for {}: {}",
                                    learner.getName(), signals.referenceId, error.getMessage());

                            if (autoRollback) {
                                return learner.rollback(signals.referenceId)
                                        .map(rolledBack -> new LearnerResult(false, rolledBack));
                            }

                            return Promise.of(new LearnerResult(false, false));
                        });
    }

    private void notifyListeners(CycleReport report) {
        for (LearningListener listener : listeners) {
            try {
                listener.onCycleComplete(report);
            } catch (Exception e) {
                LOG.warn("Listener threw exception", e);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // State Access
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets the last cycle report.
     *
     * @return the most recent report, or null
     */
    public CycleReport getLastReport() {
        return lastReport.get();
    }

    /**
     * Gets the total cycle count.
     *
     * @return number of cycles executed
     */
    public long getCycleCount() {
        return cycleCount.get();
    }

    /**
     * Gets learning state for a reference.
     *
     * @param referenceId the reference ID
     * @return the learning state, or null
     */
    public LearningState getLearningState(String referenceId) {
        return learningStates.get(referenceId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Types
    // ═══════════════════════════════════════════════════════════════════════════

    private record ApplyResult(int applied, int failed, int rollbacks) {
    }

    private record LearnerResult(boolean success, boolean rolledBack) {
    }

    /**
     * Learning strategies.
     */
    public enum LearningStrategy {
        /** Apply updates immediately when thresholds are met */
        IMMEDIATE,
        /** Accumulate updates and apply in batches */
        BATCHED,
        /** Apply updates incrementally with confidence weighting */
        GRADUAL,
        /** Queue updates for human review before applying */
        SUPERVISED
    }

    /**
     * Report of a learning cycle.
     */
    @Value
    @Builder
    public static class CycleReport {
        long cycleNumber;
        Instant startTime;
        Instant endTime;
        LearningStrategy strategy;
        boolean success;
        boolean skipped;
        String skipReason;
        String errorMessage;
        int eventsCollected;
        int eventsProcessed;
        int signalsGenerated;
        int referencesAffected;
        int updatesApplied;
        int updatesFailed;
        int rollbacks;

        public Duration getDuration() {
            return startTime != null && endTime != null
                    ? Duration.between(startTime, endTime)
                    : Duration.ZERO;
        }

        public static CycleReport skipped(String reason) {
            return CycleReport.builder()
                    .startTime(Instant.now())
                    .endTime(Instant.now())
                    .skipped(true)
                    .skipReason(reason)
                    .success(true)
                    .build();
        }

        public static CycleReport failed(String error) {
            return CycleReport.builder()
                    .startTime(Instant.now())
                    .endTime(Instant.now())
                    .success(false)
                    .errorMessage(error)
                    .build();
        }
    }

    /**
     * State of learning for a specific reference.
     */
    @Value
    @Builder
    public static class LearningState {
        String referenceId;
        int totalSignals;
        int reinforcements;
        int corrections;
        double accumulatedStrength;
        double currentConfidence;
        int updateAttempts;
        int successfulUpdates;
        Instant lastUpdateTime;
        Map<String, Object> learnedValues;
    }

    /**
     * Interface for components that can learn from signals.
     */
    public interface Learner {
        /**
         * Gets the learner's name.
         *
         * @return the name
         */
        String getName();

        /**
         * Applies learning from signals.
         *
         * @param signals the learning signals
         * @return Promise indicating success
         */
        Promise<Boolean> learn(List<LearningSignal> signals);

        /**
         * Rolls back learning for a reference.
         *
         * @param referenceId the reference to rollback
         * @return Promise indicating if rollback succeeded
         */
        Promise<Boolean> rollback(String referenceId);
    }

    /**
     * Listener for learning cycle events.
     */
    @FunctionalInterface
    public interface LearningListener {
        /**
         * Called when a learning cycle completes.
         *
         * @param report the cycle report
         */
        void onCycleComplete(CycleReport report);
    }
}
