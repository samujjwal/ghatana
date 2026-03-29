/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.tracing;

import com.ghatana.aep.AepEngine;
import com.ghatana.platform.observability.CorrelationContext;
import io.activej.promise.Promise;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Correlation and tracing utilities for the AEP processing pipeline (AEP-020).
 *
 * <p>The engine uses the platform {@link CorrelationContext} as the canonical
 * request context. This wrapper ensures every processed event carries a correlation
 * ID and that MDC / tracing context are correctly initialised for the duration of
 * async event processing.
 *
 * @doc.type class
 * @doc.purpose Propagate event correlation IDs and distributed tracing context
 * @doc.layer product
 * @doc.pattern Context, Utility
 */
public final class AepTraceContext {

    private AepTraceContext() {
    }

    /**
     * Ensures the event carries a correlation ID.
     *
     * @param event event to normalise
     * @return original event or a copy with a generated correlation ID
     */
    public static AepEngine.Event ensureCorrelationId(AepEngine.Event event) {
        Objects.requireNonNull(event, "event must not be null");
        return event.correlationId() != null ? event : event.withCorrelationId(UUID.randomUUID().toString());
    }

    /**
     * Executes an async operation with correlation context initialised from the event.
     *
     * @param tenantId tenant identifier
     * @param event    event supplying the correlation ID and user identity
     * @param task     task to execute under that context
     * @param <T>      result type
     * @return promise that clears the context after completion
     */
    public static <T> Promise<T> withEventContext(String tenantId,
                                                  AepEngine.Event event,
                                                  Supplier<Promise<T>> task) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(task, "task must not be null");

        AepEngine.Event correlatedEvent = ensureCorrelationId(event);
        CorrelationContext.initialize(
            correlatedEvent.correlationId(),
            correlatedEvent.identityContext().userId().orElse(null),
            tenantId,
            null
        );

        try {
            return task.get().whenComplete((result, error) -> CorrelationContext.clear());
        } catch (Exception exception) {
            CorrelationContext.clear();
            return Promise.ofException(exception);
        }
    }
}