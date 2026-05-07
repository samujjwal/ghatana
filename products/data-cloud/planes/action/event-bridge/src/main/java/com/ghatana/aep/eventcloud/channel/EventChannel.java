/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud.channel;

import java.util.Objects;

/**
 * A named event channel representing a logical event stream in the Event-Cloud.
 *
 * <p>Channels are the primary routing abstraction between AEP components.
 * Events flow through channels from sources (intake) to processors (pipelines)
 * to sinks (outputs). Each channel maps to an event type prefix in the
 * underlying {@link com.ghatana.datacloud.spi.EventLogStore}.
 *
 * <h3>Standard channels</h3>
 * <ul>
 *   <li>{@link #EVENTS_INTAKE} - Raw event ingestion from external sources</li>
 *   <li>{@link #PIPELINE_RUNS} - Pipeline execution lifecycle events</li>
 *   <li>{@link #AGENT_DECISIONS} - Agent decision records with confidence and lineage</li>
 *   <li>{@link #LEARNING_EPISODES} - Learning episode records for policy synthesis</li>
 *   <li>{@link #POLICY_PROMOTIONS} - Policy promotion and rollout events</li>
 * </ul>
 *
 * @param name        unique channel name (used as event type prefix)
 * @param description human-readable description
 * @param retention   retention policy hint (e.g., "7d", "30d", "indefinite")
 *
 * @doc.type record
 * @doc.purpose Named event channel abstraction
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record EventChannel(
    String name,
    String description,
    String retention
) {
    /** Standard channel: raw event ingestion from external sources. */
    public static final EventChannel EVENTS_INTAKE = new EventChannel(
        "aep.events.intake",
        "Raw event ingestion from external sources",
        "30d");

    /** Standard channel: pipeline execution lifecycle events. */
    public static final EventChannel PIPELINE_RUNS = new EventChannel(
        "aep.pipeline.runs",
        "Pipeline execution lifecycle events (start, step, complete, fail)",
        "90d");

    /** Standard channel: agent decision records. */
    public static final EventChannel AGENT_DECISIONS = new EventChannel(
        "aep.agent.decisions",
        "Agent decision records with confidence, explanation, and lineage",
        "indefinite");

    /** Standard channel: learning episode records. */
    public static final EventChannel LEARNING_EPISODES = new EventChannel(
        "aep.learning.episodes",
        "Learning episodes for policy synthesis and evaluation",
        "indefinite");

    /** Standard channel: policy promotion events. */
    public static final EventChannel POLICY_PROMOTIONS = new EventChannel(
        "aep.policy.promotions",
        "Policy promotion, rollout, and rollback events",
        "indefinite");

    public EventChannel {
        Objects.requireNonNull(name, "name required");
        if (name.isBlank()) {
            throw new IllegalArgumentException("channel name cannot be blank");
        }
        description = description != null ? description : "";
        retention = retention != null ? retention : "30d";
    }

    /**
     * Creates a custom channel with default retention.
     *
     * @param name channel name
     * @param description channel description
     */
    public static EventChannel of(String name, String description) {
        return new EventChannel(name, description, "30d");
    }

    /**
     * Returns the event type prefix used in EventLogStore.
     * Events on this channel have types like "{channelName}.{subtype}".
     */
    public String eventTypePrefix() {
        return name;
    }
}
