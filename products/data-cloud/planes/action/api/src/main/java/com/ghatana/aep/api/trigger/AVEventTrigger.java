/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.api.trigger;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;

/**
 * Action Plane trigger for AV events.
 * 
 * P8.4: Add Action Plane trigger: AV event → pattern/agent/pipeline.
 * Triggers pattern matching, agent execution, or pipeline runs based on AV events.
 * 
 * @doc.type interface
 * @doc.purpose AV event to Action Plane trigger
 * @doc.layer product
 * @doc.pattern Trigger
 */
public interface AVEventTrigger {

    /**
     * Triggers an action based on an AV event.
     *
     * @param event the AV event
     * @return a Promise that resolves to the trigger result
     */
    Promise<TriggerResult> trigger(AVEvent event);

    /**
     * Registers a trigger rule for AV events.
     *
     * @param rule the trigger rule
     * @return a Promise that resolves to the registered rule
     */
    Promise<TriggerRule> registerRule(TriggerRule rule);

    /**
     * Gets trigger rules for a tenant.
     *
     * @param tenantId the tenant ID
     * @return a Promise that resolves to the list of rules
     */
    Promise<java.util.List<TriggerRule>> getRules(String tenantId);

    /**
     * Deletes a trigger rule.
     *
     * @param ruleId the rule ID
     * @param tenantId the tenant ID
     * @return a Promise that resolves when deletion is complete
     */
    Promise<Void> deleteRule(String ruleId, String tenantId);

    /**
     * AV event that can trigger actions.
     *
     * @param id event ID
     * @param tenantId tenant ID
     * @param assetId the AV asset ID
     * @param eventType the event type
     * @param timestamp when the event occurred
     * @param eventData event-specific data
     * @param metadata event metadata
     */
    record AVEvent(
            String id,
            String tenantId,
            String assetId,
            AVEventType eventType,
            Instant timestamp,
            Map<String, Object> eventData,
            Map<String, String> metadata) {

        public AVEvent(String tenantId, String assetId, AVEventType eventType, Map<String, Object> eventData) {
            this(java.util.UUID.randomUUID().toString(), tenantId, assetId, eventType, Instant.now(), eventData, Map.of());
        }
    }

    /**
     * AV event types that can trigger actions.
     */
    enum AVEventType {
        INGESTION_STARTED,
        INGESTION_COMPLETED,
        INGESTION_FAILED,
        TRANSCRIPT_READY,
        OBJECT_DETECTED,
        SCENE_CHANGE,
        EMOTION_DETECTED,
        SPEAKER_IDENTIFIED,
        KEYWORD_DETECTED,
        ANOMALY_DETECTED
    }

    /**
     * Trigger rule definition.
     *
     * @param id rule ID
     * @param tenantId tenant ID
     * @param name rule name
     * @param description rule description
     * @param eventType the event type to trigger on
     * @param conditions conditions that must be met
     * @param action the action to execute
     * @param enabled whether the rule is enabled
     * @param createdAt when the rule was created
     * @param updatedAt when the rule was last updated
     */
    record TriggerRule(
            String id,
            String tenantId,
            String name,
            String description,
            AVEventType eventType,
            TriggerConditions conditions,
            TriggerAction action,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt) {

        public TriggerRule(
                String id,
                String tenantId,
                String name,
                String description,
                AVEventType eventType,
                TriggerConditions conditions,
                TriggerAction action,
                boolean enabled) {
            this(id, tenantId, name, description, eventType, conditions, action, enabled, Instant.now(), Instant.now());
        }
    }

    /**
     * Conditions that must be met for the trigger to fire.
     *
     * @param confidenceThreshold minimum confidence threshold
     * @param requiredFields required fields in event data
     * @param customConditions custom condition expressions
     */
    record TriggerConditions(
            Double confidenceThreshold,
            java.util.Set<String> requiredFields,
            Map<String, String> customConditions) {

        public TriggerConditions() {
            this(null, java.util.Set.of(), Map.of());
        }
    }

    /**
     * Action to execute when trigger fires.
     *
     * @param type the action type
     * @param target the target (pattern ID, agent ID, or pipeline ID)
     * @param parameters action parameters
     * @param priority action priority
     */
    record TriggerAction(
            ActionType type,
            String target,
            Map<String, Object> parameters,
            Priority priority) {

        public TriggerAction(ActionType type, String target, Map<String, Object> parameters) {
            this(type, target, parameters, Priority.NORMAL);
        }
    }

    /**
     * Action type.
     */
    enum ActionType {
        PATTERN_MATCH,
        AGENT_EXECUTE,
        PIPELINE_RUN,
        WEBHOOK,
        NOTIFICATION
    }

    /**
     * Action priority.
     */
    enum Priority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }

    /**
     * Trigger result.
     *
     * @param success whether the trigger succeeded
     * @param matchedRule the rule that matched
     * @param actionId the action ID that was executed
     * @param result action result
     * @param error error message (if failed)
     */
    record TriggerResult(
            boolean success,
            TriggerRule matchedRule,
            String actionId,
            Map<String, Object> result,
            String error) {

        public static TriggerResult success(TriggerRule matchedRule, String actionId, Map<String, Object> result) {
            return new TriggerResult(true, matchedRule, actionId, result, null);
        }

        public static TriggerResult failed(String error) {
            return new TriggerResult(false, null, null, Map.of(), error);
        }

        public static TriggerResult noMatch() {
            return new TriggerResult(true, null, null, Map.of("matched", false), null);
        }
    }
}
