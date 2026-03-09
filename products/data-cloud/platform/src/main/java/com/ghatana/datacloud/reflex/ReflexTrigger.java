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

package com.ghatana.datacloud.reflex;

import com.ghatana.datacloud.DataRecord;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;

/**
 * Represents an event that can trigger a reflex activation.
 *
 * <p>
 * Triggers encapsulate the information needed to evaluate whether
 * a reflex rule should fire. They are lightweight and optimized for
 * fast evaluation.
 *
 * <h2>Trigger Types</h2>
 * <ul>
 * <li><b>RECORD</b>: Triggered by a data record</li>
 * <li><b>PATTERN</b>: Triggered by pattern detection</li>
 * <li><b>THRESHOLD</b>: Triggered by metric threshold breach</li>
 * <li><b>TEMPORAL</b>: Triggered by time-based conditions</li>
 * <li><b>COMPOSITE</b>: Triggered by combination of conditions</li>
 * </ul>
 *
 * @doc.type record
 * @doc.purpose Reflex trigger event representation
 * @doc.layer core
 * @doc.pattern Value Object, Event
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
@Value
@Builder(toBuilder = true)
public class ReflexTrigger {

    /**
     * Unique identifier for this trigger instance.
     */
    String triggerId;

    /**
     * The type of trigger.
     */
    TriggerType type;

    /**
     * The record that caused the trigger (if applicable).
     */
    DataRecord record;

    /**
     * The pattern ID that was detected (if applicable).
     */
    String patternId;

    /**
     * Extracted features from the trigger source.
     */
    @Builder.Default
    Map<String, Object> features = Map.of();

    /**
     * Match confidence (0.0 to 1.0).
     */
    @Builder.Default
    float confidence = 1.0f;

    /**
     * When the trigger was created.
     */
    @Builder.Default
    Instant timestamp = Instant.now();

    /**
     * The source that created this trigger.
     */
    String source;

    /**
     * The tenant ID.
     */
    String tenantId;

    /**
     * Priority for processing order.
     */
    @Builder.Default
    int priority = 5;

    /**
     * Whether this trigger should bypass normal processing.
     */
    @Builder.Default
    boolean urgent = false;

    /**
     * Additional context.
     */
    @Builder.Default
    Map<String, Object> context = Map.of();

    /**
     * Types of triggers.
     */
    public enum TriggerType {
        /**
         * Triggered by incoming data record.
         */
        RECORD,

        /**
         * Triggered by pattern detection.
         */
        PATTERN,

        /**
         * Triggered by threshold breach.
         */
        THRESHOLD,

        /**
         * Triggered by time condition.
         */
        TEMPORAL,

        /**
         * Triggered by combination of conditions.
         */
        COMPOSITE,

        /**
         * Triggered by anomaly detection.
         */
        ANOMALY,

        /**
         * Triggered by external signal.
         */
        EXTERNAL,

        /**
         * Triggered manually for testing.
         */
        MANUAL
    }

    /**
     * Creates a trigger from a data record.
     *
     * @param record   the triggering record
     * @param tenantId the tenant ID
     * @return a new trigger
     */
    public static ReflexTrigger fromRecord(DataRecord record, String tenantId) {
        return ReflexTrigger.builder()
                .triggerId("trg-" + record.getId())
                .type(TriggerType.RECORD)
                .record(record)
                .tenantId(tenantId)
                .source("record-processor")
                .build();
    }

    /**
     * Creates a trigger from pattern detection.
     *
     * @param patternId  the detected pattern ID
     * @param confidence detection confidence
     * @param tenantId   the tenant ID
     * @return a new trigger
     */
    public static ReflexTrigger fromPattern(String patternId, float confidence, String tenantId) {
        return ReflexTrigger.builder()
                .triggerId("trg-pat-" + System.nanoTime())
                .type(TriggerType.PATTERN)
                .patternId(patternId)
                .confidence(confidence)
                .tenantId(tenantId)
                .source("pattern-matcher")
                .build();
    }

    /**
     * Creates an urgent trigger.
     *
     * @param type     the trigger type
     * @param features extracted features
     * @param tenantId the tenant ID
     * @return an urgent trigger
     */
    public static ReflexTrigger urgent(TriggerType type, Map<String, Object> features, String tenantId) {
        return ReflexTrigger.builder()
                .triggerId("trg-urgent-" + System.nanoTime())
                .type(type)
                .features(features)
                .tenantId(tenantId)
                .urgent(true)
                .priority(1)
                .source("urgent-handler")
                .build();
    }

    /**
     * Gets a feature value.
     *
     * @param key the feature key
     * @param <T> the expected type
     * @return the feature value or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getFeature(String key) {
        return (T) features.get(key);
    }

    /**
     * Gets a feature with default value.
     *
     * @param key          the feature key
     * @param defaultValue the default value
     * @param <T>          the expected type
     * @return the feature value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getFeature(String key, T defaultValue) {
        Object value = features.get(key);
        return value != null ? (T) value : defaultValue;
    }
}
