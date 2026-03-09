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

package com.ghatana.datacloud.client;

import com.ghatana.datacloud.record.Record;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.function.Function;

/**
 * AI aspect for detecting anomalies in data records.
 *
 * <p>This aspect uses statistical or ML-based anomaly detection
 * to identify unusual patterns in incoming data. Can flag records
 * for review or trigger alerts.
 *
 * <h2>Detection Strategies</h2>
 * <ul>
 *   <li><b>Statistical</b> - Z-score, IQR-based detection</li>
 *   <li><b>ML-based</b> - Isolation Forest, One-class SVM</li>
 *   <li><b>Rule-based</b> - Custom threshold rules</li>
 *   <li><b>Pattern-based</b> - Deviation from learned patterns</li>
 * </ul>
 *
 * <h2>Context Attributes</h2>
 * <ul>
 *   <li>{@code anomaly.detected} - Boolean indicating anomaly</li>
 *   <li>{@code anomaly.score} - Anomaly score (0.0-1.0)</li>
 *   <li>{@code anomaly.type} - Type of anomaly detected</li>
 *   <li>{@code anomaly.explanation} - Human-readable explanation</li>
 * </ul>
 *
 * @see AIAspect
 * @see AnomalyDetector
 * @doc.type class
 * @doc.purpose Anomaly detection aspect
 * @doc.layer core
 * @doc.pattern Aspect, Observer
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public final class AnomalyDetectionAspect implements AIAspect<Record, Record> {

    /** Context key for anomaly detected flag */
    public static final String ATTR_DETECTED = "anomaly.detected";
    /** Context key for anomaly score */
    public static final String ATTR_SCORE = "anomaly.score";
    /** Context key for anomaly type */
    public static final String ATTR_TYPE = "anomaly.type";
    /** Context key for explanation */
    public static final String ATTR_EXPLANATION = "anomaly.explanation";

    private final AnomalyDetector detector;
    private final Function<Record, Object> featureExtractor;
    private final double threshold;
    private final int priority;
    private final boolean async;
    private final AnomalyHandler handler;

    private AnomalyDetectionAspect(Builder builder) {
        this.detector = builder.detector;
        this.featureExtractor = builder.featureExtractor;
        this.threshold = builder.threshold;
        this.priority = builder.priority;
        this.async = builder.async;
        this.handler = builder.handler;
    }

    @Override
    public String name() {
        return "anomaly-detection";
    }

    @Override
    public Phase phase() {
        return Phase.POST;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public boolean isAsync() {
        return async;
    }

    @Override
    public boolean isApplicable(OperationType operation, AIAspectContext context) {
        return operation == OperationType.CREATE || operation == OperationType.UPDATE;
    }

    @Override
    public Promise<Record> process(Record input, AIAspectContext context) {
        Object features = featureExtractor.apply(input);

        return detector.detect(features)
                .map(result -> {
                    boolean isAnomaly = result.score() >= threshold;
                    
                    context.setAttribute(ATTR_DETECTED, isAnomaly);
                    context.setAttribute(ATTR_SCORE, result.score());
                    context.setAttribute(ATTR_TYPE, result.type());
                    context.setAttribute(ATTR_EXPLANATION, result.explanation());

                    if (isAnomaly && handler != null) {
                        handler.onAnomaly(input, result, context);
                    }

                    return input;
                });
    }

    /**
     * Creates a new builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for AnomalyDetectionAspect.
     */
    public static final class Builder {
        private AnomalyDetector detector;
        private Function<Record, Object> featureExtractor = Record::data;
        private double threshold = 0.8;
        private int priority = 120;
        private boolean async = true;
        private AnomalyHandler handler;

        private Builder() {
        }

        public Builder detector(AnomalyDetector detector) {
            this.detector = detector;
            return this;
        }

        public Builder featureExtractor(Function<Record, Object> extractor) {
            this.featureExtractor = extractor;
            return this;
        }

        public Builder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder async(boolean async) {
            this.async = async;
            return this;
        }

        public Builder handler(AnomalyHandler handler) {
            this.handler = handler;
            return this;
        }

        public AnomalyDetectionAspect build() {
            if (detector == null) {
                throw new IllegalStateException("AnomalyDetector is required");
            }
            return new AnomalyDetectionAspect(this);
        }
    }

    /**
     * Anomaly detector interface.
     */
    public interface AnomalyDetector {

        /**
         * Detects anomalies in the given features.
         *
         * @param features feature data
         * @return detection result
         */
        Promise<AnomalyResult> detect(Object features);

        /**
         * Trains/updates the detector with new data.
         *
         * @param features training data
         * @return completion promise
         */
        Promise<Void> train(Object features);
    }

    /**
     * Anomaly detection result.
     *
     * @param score anomaly score (0.0 = normal, 1.0 = highly anomalous)
     * @param type type of anomaly
     * @param explanation human-readable explanation
     * @param detectedAt detection timestamp
     */
    public record AnomalyResult(
            double score,
            String type,
            String explanation,
            Instant detectedAt
    ) {
        /**
         * Creates a normal (non-anomalous) result.
         *
         * @return normal result
         */
        public static AnomalyResult normal() {
            return new AnomalyResult(0.0, "NORMAL", "No anomaly detected", Instant.now());
        }

        /**
         * Creates an anomalous result.
         *
         * @param score anomaly score
         * @param type anomaly type
         * @param explanation explanation
         * @return anomaly result
         */
        public static AnomalyResult anomaly(double score, String type, String explanation) {
            return new AnomalyResult(score, type, explanation, Instant.now());
        }
    }

    /**
     * Handler for anomaly events.
     */
    @FunctionalInterface
    public interface AnomalyHandler {

        /**
         * Called when an anomaly is detected.
         *
         * @param record the anomalous record
         * @param result the detection result
         * @param context the aspect context
         */
        void onAnomaly(Record record, AnomalyResult result, AIAspectContext context);
    }
}
