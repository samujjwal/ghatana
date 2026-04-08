/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.learning.signal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A structured learning signal emitted from an agent or turn lifecycle hook.
 *
 * <p>Signals are consumed asynchronously by the learning plane. The {@code signalType}
 * is a free-form string that consuming systems use to route signals to the correct
 * learning update pipeline (e.g. {@code "skill.success"}, {@code "tool.failure"}).
 *
 * @param signalId     unique identifier for this signal
 * @param signalType   event type for routing (e.g. "skill.success", "tool.failure")
 * @param sourceAgentId the agent that emitted the signal
 * @param tenantId     tenant scope
 * @param correlationId the turn or request correlation ID
 * @param payload      additional key-value metadata (immutable)
 * @param emittedAt    when the signal was created
 *
 * @doc.type record
 * @doc.purpose Learning event contract for the agent learning plane SPI
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record LearningSignal(
        @NotNull String signalId,
        @NotNull String signalType,
        @NotNull String sourceAgentId,
        @NotNull String tenantId,
        @NotNull String correlationId,
        @NotNull Map<String, String> payload,
        @NotNull Instant emittedAt) {

    public LearningSignal {
        Objects.requireNonNull(signalId, "signalId");
        Objects.requireNonNull(signalType, "signalType");
        Objects.requireNonNull(sourceAgentId, "sourceAgentId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(emittedAt, "emittedAt");
        if (signalType.isBlank()) {
            throw new IllegalArgumentException("signalType must not be blank");
        }
        payload = Map.copyOf(payload);
    }

    /**
     * Factory that generates a signal ID and sets the emission timestamp to now.
     */
    @NotNull
    public static LearningSignal of(
            @NotNull String signalType,
            @NotNull String sourceAgentId,
            @NotNull String tenantId,
            @NotNull String correlationId,
            @Nullable Map<String, String> payload) {
        return new LearningSignal(
                UUID.randomUUID().toString(),
                signalType,
                sourceAgentId,
                tenantId,
                correlationId,
                payload != null ? payload : Map.of(),
                Instant.now());
    }
}
