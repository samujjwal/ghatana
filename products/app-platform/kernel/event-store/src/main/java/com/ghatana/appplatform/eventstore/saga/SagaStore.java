package com.ghatana.appplatform.eventstore.saga;

import java.util.List;
import java.util.Optional;

/**
 * Port for persisting saga definitions and instances.
 *
 * @doc.type interface
 * @doc.purpose Saga persistence port (STORY-K05-020)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface SagaStore {

    // ── Definitions ──────────────────────────────────────────────────────────

    void saveDefinition(SagaDefinition definition);

    Optional<SagaDefinition> getDefinition(String sagaType, int version);

    /** Returns the latest version of a saga type. */
    Optional<SagaDefinition> getLatestDefinition(String sagaType);

    // ── Instances ─────────────────────────────────────────────────────────────

    void saveInstance(SagaInstance instance);

    void updateInstance(SagaInstance instance);

    Optional<SagaInstance> findInstance(String sagaId);

    /** Find active sagas for a correlation ID (e.g. all open sagas for an orderId). */
    List<SagaInstance> findActiveByCorrelation(String tenantId, String correlationId);
}
