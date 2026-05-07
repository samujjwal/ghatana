/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.consent.strategy;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.consent.ConsentService.ConsentDecision;
import io.activej.promise.Promise;

/**
 * Strategy for evaluating consent on a per-tenant basis.
 *
 * <p>Implementations supply the tenant-specific consent logic used by
 * {@link com.ghatana.aep.consent.TenantAwareConsentService}. A strategy is
 * registered for a specific tenant ID and takes precedence over the system-wide
 * {@link com.ghatana.aep.consent.DefaultConsentService} for that tenant.
 *
 * <p><b>Usage example — registering a custom strategy for a tenant:</b>
 * <pre>{@code
 * ConsentEvaluationStrategy strictStrategy = (tenantId, event) ->
 *     Promise.of(ConsentDecision.deny("All events denied by strict policy"));
 *
 * TenantAwareConsentService service = TenantAwareConsentService.builder()
 *     .withStrategy("tenant-strict", strictStrategy)
 *     .build(defaultConsentService);
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Per-tenant pluggable consent evaluation strategy
 * @doc.layer product
 * @doc.pattern Strategy
 * @since 1.1.0
 */
@FunctionalInterface
public interface ConsentEvaluationStrategy {

    /**
     * Evaluate consent for the given tenant and event.
     *
     * @param tenantId tenant identifier; never {@code null}
     * @param event    event being processed; never {@code null}
     * @return promise of consent decision; must never resolve to {@code null}
     */
    Promise<ConsentDecision> evaluate(String tenantId, AepEngine.Event event);
}
