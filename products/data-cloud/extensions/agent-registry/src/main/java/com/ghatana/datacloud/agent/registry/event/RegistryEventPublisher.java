/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.datacloud.agent.registry.event;

import com.ghatana.datacloud.EntityRecord;
import com.ghatana.datacloud.client.DataCloudClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Publishes agent registry lifecycle events to Data-Cloud event streams.
 *
 * <p>Emits events such as {@code agent.registered}, {@code agent.deregistered},
 * {@code agent.capability.updated} to the {@code agent-registry-events} stream
 * for monitoring, audit, and downstream consumption.
 *
 * @doc.type class
 * @doc.purpose Agent registry event publishing to Data-Cloud
 * @doc.layer registry
 * @doc.pattern Observer, Event Publisher
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
public class RegistryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RegistryEventPublisher.class);
    private static final String STREAM = "agent-registry-events";

    private final DataCloudClient dataCloud;
    private final String tenantId;

    public RegistryEventPublisher(@NotNull DataCloudClient dataCloud,
                                   @NotNull String tenantId) {
        this.dataCloud = Objects.requireNonNull(dataCloud, "dataCloud");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
    }

    /**
     * Publishes an {@code agent.registered} event.
     */
    @NotNull
    public Promise<Long> publishAgentRegistered(@NotNull String agentId,
                                                 @NotNull String agentName,
                                                 @NotNull String version) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "agent.registered");
        payload.put("agentId", agentId);
        payload.put("agentName", agentName);
        payload.put("version", version);
        payload.put("timestamp", Instant.now().toString());

        return publish(payload);
    }

    /**
     * Publishes an {@code agent.deregistered} event.
     */
    @NotNull
    public Promise<Long> publishAgentDeregistered(@NotNull String agentId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "agent.deregistered");
        payload.put("agentId", agentId);
        payload.put("timestamp", Instant.now().toString());

        return publish(payload);
    }

    /**
     * Publishes an {@code agent.capability.updated} event.
     */
    @NotNull
    public Promise<Long> publishCapabilityUpdated(@NotNull String agentId,
                                                    @NotNull String capability,
                                                    @NotNull String action) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "agent.capability.updated");
        payload.put("agentId", agentId);
        payload.put("capability", capability);
        payload.put("action", action);
        payload.put("timestamp", Instant.now().toString());

        return publish(payload);
    }

    private Promise<Long> publish(Map<String, Object> payload) {
        EntityRecord event = EntityRecord.builder()
                .tenantId(tenantId)
                .collectionName(STREAM)
                .data(payload)
                .build();

        return dataCloud.appendEvent(tenantId, STREAM, event)
                .whenResult(offset -> log.debug("Published registry event: {} at offset {}",
                        payload.get("eventType"), offset))
                .whenException(e -> log.error("Failed to publish registry event: {}",
                        e.getMessage()));
    }
}
