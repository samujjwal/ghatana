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

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a feedback event capturing outcome information for learning.
 *
 * <p>Feedback events are the primary input to the learning loop, providing
 * signals about the quality and correctness of system outputs. They link
 * predictions or actions to their actual outcomes.
 *
 * <h2>Feedback Sources</h2>
 * <ul>
 *   <li><b>User</b>: Explicit feedback from human users</li>
 *   <li><b>System</b>: Automated observation of outcomes</li>
 *   <li><b>External</b>: Third-party validation or ground truth</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * FeedbackEvent feedback = FeedbackEvent.builder()
 *     .referenceId("prediction-123")
 *     .referenceType(ReferenceType.PREDICTION)
 *     .feedbackType(FeedbackType.OUTCOME)
 *     .sentiment(Sentiment.POSITIVE)
 *     .score(0.95)
 *     .outcome("The predicted anomaly was confirmed")
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Capture feedback for learning
 * @doc.layer core
 * @doc.pattern Value Object, Event
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
@Value
@Builder(toBuilder = true)
public class FeedbackEvent {

    /**
     * Unique identifier for this feedback event.
     */
    @Builder.Default
    String id = UUID.randomUUID().toString();

    /**
     * Timestamp when the feedback was captured.
     */
    @Builder.Default
    Instant timestamp = Instant.now();

    /**
     * ID of the item this feedback relates to.
     */
    String referenceId;

    /**
     * Type of the referenced item.
     */
    @Builder.Default
    ReferenceType referenceType = ReferenceType.PREDICTION;

    /**
     * The tenant this feedback belongs to.
     */
    String tenantId;

    /**
     * Type of feedback being provided.
     */
    @Builder.Default
    FeedbackType feedbackType = FeedbackType.IMPLICIT;

    /**
     * Source of the feedback.
     */
    @Builder.Default
    FeedbackSource source = FeedbackSource.SYSTEM;

    /**
     * Overall sentiment of the feedback.
     */
    @Builder.Default
    Sentiment sentiment = Sentiment.NEUTRAL;

    /**
     * Numeric score representing feedback quality/correctness.
     *
     * <p>Range depends on feedback type:
     * <ul>
     *   <li>EXPLICIT: typically -1.0 to 1.0 (negative to positive)</li>
     *   <li>OUTCOME: 0.0 to 1.0 (accuracy/correctness)</li>
     *   <li>OPERATIONAL: unbounded (e.g., latency in ms)</li>
     * </ul>
     */
    @Builder.Default
    double score = 0.0;

    /**
     * Confidence in the feedback accuracy.
     */
    @Builder.Default
    double confidence = 1.0;

    /**
     * The actual outcome or result observed.
     */
    String outcome;

    /**
     * The expected/predicted outcome, if applicable.
     */
    String expectedOutcome;

    /**
     * Category or domain of the feedback.
     */
    String category;

    /**
     * User-provided textual feedback.
     */
    String comment;

    /**
     * Structured corrections or suggestions.
     */
    Map<String, Object> corrections;

    /**
     * Additional contextual metadata.
     */
    @Builder.Default
    Map<String, Object> metadata = Map.of();

    /**
     * Tags for filtering and categorization.
     */
    @Builder.Default
    java.util.List<String> tags = java.util.List.of();

    /**
     * Delay between the action and this feedback.
     */
    java.time.Duration feedbackDelay;

    /**
     * Whether this feedback has been processed.
     */
    @Builder.Default
    boolean processed = false;

    /**
     * Types of referenced items.
     */
    public enum ReferenceType {
        /** A prediction made by the system */
        PREDICTION,
        /** A recommendation provided */
        RECOMMENDATION,
        /** An action taken by the system */
        ACTION,
        /** An alert generated */
        ALERT,
        /** A pattern detected */
        PATTERN,
        /** An anomaly detected */
        ANOMALY,
        /** A classification result */
        CLASSIFICATION,
        /** A search or query result */
        QUERY_RESULT,
        /** A general data record */
        RECORD,
        /** Other/custom reference */
        OTHER
    }

    /**
     * Types of feedback.
     */
    public enum FeedbackType {
        /**
         * Explicit user feedback (ratings, corrections).
         */
        EXPLICIT,

        /**
         * Implicit behavioral feedback (clicks, dwell time).
         */
        IMPLICIT,

        /**
         * Outcome-based feedback (prediction accuracy).
         */
        OUTCOME,

        /**
         * Operational feedback (performance, errors).
         */
        OPERATIONAL,

        /**
         * Comparative feedback (A/B test results).
         */
        COMPARATIVE,

        /**
         * Expert/ground truth feedback.
         */
        EXPERT
    }

    /**
     * Sources of feedback.
     */
    public enum FeedbackSource {
        /** Direct user input */
        USER,
        /** System-generated observation */
        SYSTEM,
        /** External validation source */
        EXTERNAL,
        /** Automated test/simulation */
        SYNTHETIC,
        /** Expert/human reviewer */
        EXPERT,
        /** Ground truth data */
        GROUND_TRUTH
    }

    /**
     * Sentiment classifications.
     */
    public enum Sentiment {
        /** Very negative feedback */
        VERY_NEGATIVE(-1.0),
        /** Negative feedback */
        NEGATIVE(-0.5),
        /** Neutral or unclear feedback */
        NEUTRAL(0.0),
        /** Positive feedback */
        POSITIVE(0.5),
        /** Very positive feedback */
        VERY_POSITIVE(1.0);

        private final double numericValue;

        Sentiment(double numericValue) {
            this.numericValue = numericValue;
        }

        public double getNumericValue() {
            return numericValue;
        }

        /**
         * Determines sentiment from a numeric score.
         *
         * @param score the score (-1.0 to 1.0)
         * @return the corresponding sentiment
         */
        public static Sentiment fromScore(double score) {
            if (score <= -0.75) return VERY_NEGATIVE;
            if (score <= -0.25) return NEGATIVE;
            if (score < 0.25) return NEUTRAL;
            if (score < 0.75) return POSITIVE;
            return VERY_POSITIVE;
        }
    }

    /**
     * Checks if this is positive feedback.
     *
     * @return true if sentiment is positive or very positive
     */
    public boolean isPositive() {
        return sentiment == Sentiment.POSITIVE || sentiment == Sentiment.VERY_POSITIVE;
    }

    /**
     * Checks if this is negative feedback.
     *
     * @return true if sentiment is negative or very negative
     */
    public boolean isNegative() {
        return sentiment == Sentiment.NEGATIVE || sentiment == Sentiment.VERY_NEGATIVE;
    }

    /**
     * Checks if this feedback indicates an error or failure.
     *
     * @return true if outcome indicates error
     */
    public boolean indicatesError() {
        return (feedbackType == FeedbackType.OUTCOME || feedbackType == FeedbackType.OPERATIONAL)
                && score < 0.5;
    }

    /**
     * Calculates the difference between expected and actual outcome.
     *
     * @return true if there was a mismatch
     */
    public boolean hasOutcomeMismatch() {
        return expectedOutcome != null
                && outcome != null
                && !expectedOutcome.equals(outcome);
    }

    /**
     * Creates a positive feedback event.
     *
     * @param referenceId the referenced item ID
     * @param referenceType the type of referenced item
     * @param comment optional comment
     * @return positive feedback event
     */
    public static FeedbackEvent positive(
            String referenceId,
            ReferenceType referenceType,
            String comment) {
        return FeedbackEvent.builder()
                .referenceId(referenceId)
                .referenceType(referenceType)
                .feedbackType(FeedbackType.EXPLICIT)
                .sentiment(Sentiment.POSITIVE)
                .score(1.0)
                .comment(comment)
                .build();
    }

    /**
     * Creates a negative feedback event.
     *
     * @param referenceId the referenced item ID
     * @param referenceType the type of referenced item
     * @param comment optional comment
     * @return negative feedback event
     */
    public static FeedbackEvent negative(
            String referenceId,
            ReferenceType referenceType,
            String comment) {
        return FeedbackEvent.builder()
                .referenceId(referenceId)
                .referenceType(referenceType)
                .feedbackType(FeedbackType.EXPLICIT)
                .sentiment(Sentiment.NEGATIVE)
                .score(-1.0)
                .comment(comment)
                .build();
    }

    /**
     * Creates an outcome feedback event.
     *
     * @param referenceId the prediction/action ID
     * @param expected the expected outcome
     * @param actual the actual outcome
     * @param correct whether the prediction was correct
     * @return outcome feedback event
     */
    public static FeedbackEvent outcome(
            String referenceId,
            String expected,
            String actual,
            boolean correct) {
        return FeedbackEvent.builder()
                .referenceId(referenceId)
                .referenceType(ReferenceType.PREDICTION)
                .feedbackType(FeedbackType.OUTCOME)
                .source(FeedbackSource.SYSTEM)
                .expectedOutcome(expected)
                .outcome(actual)
                .score(correct ? 1.0 : 0.0)
                .sentiment(correct ? Sentiment.POSITIVE : Sentiment.NEGATIVE)
                .build();
    }

    /**
     * Marks this feedback as processed.
     *
     * @return new instance marked as processed
     */
    public FeedbackEvent markProcessed() {
        return this.toBuilder()
                .processed(true)
                .build();
    }
}
