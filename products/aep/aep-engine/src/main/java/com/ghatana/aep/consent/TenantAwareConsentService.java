/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.consent;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.consent.strategy.ConsentEvaluationStrategy;
import io.activej.promise.Promise;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ConsentService decorator that dispatches evaluation to per-tenant
 * {@link ConsentEvaluationStrategy} instances, falling back to a default
 * {@link ConsentService} for tenants that have no registered strategy.
 *
 * <p>This satisfies AEP-004: tenants that require custom consent rules (e.g.,
 * stricter GDPR enforcement, field-level redaction, or environment-specific
 * allow-lists) register a dedicated {@link ConsentEvaluationStrategy} without
 * touching the shared {@link DefaultConsentService}.
 *
 * <p><b>Construction</b> — use the fluent {@link Builder}:
 * <pre>{@code
 * ConsentService service = TenantAwareConsentService.builder()
 *     .withStrategy("tenant-eu", new GdprStrictConsentStrategy())
 *     .withStrategy("tenant-test", (id, e) -> Promise.of(ConsentDecision.allow()))
 *     .build(defaultConsentService);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Composite ConsentService with per-tenant strategy dispatch
 * @doc.layer product
 * @doc.pattern Decorator
 * @since 1.1.0
 */
public final class TenantAwareConsentService implements ConsentService {

    private final Map<String, ConsentEvaluationStrategy> strategies;
    private final ConsentService fallback;

    private TenantAwareConsentService(Map<String, ConsentEvaluationStrategy> strategies,
                                      ConsentService fallback) {
        this.strategies = Collections.unmodifiableMap(new HashMap<>(strategies));
        this.fallback = Objects.requireNonNull(fallback, "fallback ConsentService must not be null");
    }

    /**
     * Returns a new {@link Builder}.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Promise<ConsentDecision> evaluateConsent(String tenantId, AepEngine.Event event) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(event, "event must not be null");

        ConsentEvaluationStrategy strategy = strategies.get(tenantId);
        if (strategy != null) {
            return strategy.evaluate(tenantId, event);
        }
        return fallback.evaluateConsent(tenantId, event);
    }

    @Override
    public Promise<List<String>> getAllowedPurposes(String tenantId, String userId, String purpose) {
        return fallback.getAllowedPurposes(tenantId, userId, purpose);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fluent builder for {@link TenantAwareConsentService}.
     */
    public static final class Builder {

        private final Map<String, ConsentEvaluationStrategy> strategies = new HashMap<>();

        private Builder() {}

        /**
         * Register a consent evaluation strategy for the given tenant.
         *
         * @param tenantId tenant identifier; must not be {@code null} or blank
         * @param strategy strategy to use for that tenant; must not be {@code null}
         * @return this builder
         */
        public Builder withStrategy(String tenantId, ConsentEvaluationStrategy strategy) {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            if (tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId must not be blank");
            }
            Objects.requireNonNull(strategy, "strategy must not be null for tenantId=" + tenantId);
            strategies.put(tenantId, strategy);
            return this;
        }

        /**
         * Build the service with the supplied fallback for tenants with no registered strategy.
         *
         * @param fallback default ConsentService; must not be {@code null}
         * @return fully constructed {@link TenantAwareConsentService}
         */
        public TenantAwareConsentService build(ConsentService fallback) {
            return new TenantAwareConsentService(strategies, fallback);
        }
    }
}
