package com.ghatana.yappc.ai.cost;

import io.activej.promise.Promise;
import java.time.Instant;

/**
 * Repository for persisting AI cost audit events.
 *
 * <p>Append-only. No updates or deletes are supported.
 *
 * @doc.type interface
 * @doc.purpose Persistence contract for AI cost audit events
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface CostRepository {

    /**
     * Persists a single cost event. The record is immutable after insertion.
     *
     * @param event the cost event to save
     * @return Promise that resolves when the event has been durably stored
     */
    Promise<Void> save(CostEvent event);

    /**
     * Immutable record representing a single LLM call's cost.
     *
     * @param id          unique event identifier (UUID)
     * @param callId      correlation ID for the LLM call
     * @param tenantId    tenant scope
     * @param userId      optional user who triggered the call
     * @param model       model identifier (e.g. "gpt-4", "claude-3-sonnet")
     * @param provider    LLM provider (e.g. "openai", "anthropic", "ollama")
     * @param featureId   optional feature that triggered the call
     * @param tokensInput number of input/prompt tokens consumed
     * @param tokensOutput number of output/completion tokens generated
     * @param costUsd     estimated cost in USD
     * @param occurredAt  wall-clock time the call completed
     */
    record CostEvent(
        String id,
        String callId,
        String tenantId,
        String userId,
        String model,
        String provider,
        String featureId,
        int tokensInput,
        int tokensOutput,
        double costUsd,
        Instant occurredAt
    ) {}
}
