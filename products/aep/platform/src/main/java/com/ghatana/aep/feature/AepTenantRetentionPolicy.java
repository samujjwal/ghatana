/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.feature;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-tenant data retention policy for AEP event logs and memory plane.
 *
 * <p>Retention is enforced per-{@code eventType} bucket within a tenant so that
 * high-frequency operational events can have a shorter TTL than compliance-
 * relevant audit events. If no type-specific policy exists the {@code DEFAULT}
 * bucket is used as fallback.
 *
 * <p>The {@code gdprErasure} flag indicates that this policy was created
 * as a result of a GDPR Art.17 / CCPA §1798.105 right-to-erasure request.
 * Erasure-triggered policies have an effective {@code maxAge} of {@link Duration#ZERO}
 * and bypass the normal scheduling interval — they are enforced immediately.
 *
 * @param id            surrogate primary key (database-assigned)
 * @param tenantId      tenant this policy belongs to; never {@code null}
 * @param eventType     logical event type bucket; {@code "DEFAULT"} is the
 *                      catch-all bucket
 * @param maxAge        maximum time an event may be retained; zero or negative
 *                      means "purge immediately"
 * @param maxBytes      soft cap (bytes) for on-disk storage — 0 disables
 * @param gdprErasure   {@code true} when triggered by a data-subject erasure
 *                      request; such policies are enforced once immediately and
 *                      then deleted
 * @param createdAt     time this policy was created
 * @param updatedAt     time this policy was last modified
 *
 * @doc.type record
 * @doc.purpose Per-tenant, per-event-type event retention policy for AEP
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AepTenantRetentionPolicy(
        UUID id,
        String tenantId,
        String eventType,
        Duration maxAge,
        long maxBytes,
        boolean gdprErasure,
        Instant createdAt,
        Instant updatedAt
) {
    /** Bucket name used when no type-specific policy is configured. */
    public static final String DEFAULT_BUCKET = "DEFAULT";

    public AepTenantRetentionPolicy {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (maxAge == null || maxAge.isNegative()) {
            maxAge = Duration.ZERO;
        }
        if (maxBytes < 0) {
            maxBytes = 0L;
        }
    }

    /**
     * Factory for a standard (non-GDPR) per-tenant retention policy.
     *
     * @param tenantId  tenant identifier
     * @param eventType event type bucket
     * @param maxAge    maximum event age before purge
     * @param maxBytes  storage cap (0 to disable)
     * @return new policy with generated id and current timestamps
     */
    public static AepTenantRetentionPolicy of(
            String tenantId, String eventType, Duration maxAge, long maxBytes) {
        Instant now = Instant.now();
        return new AepTenantRetentionPolicy(
                UUID.randomUUID(), tenantId, eventType,
                maxAge, maxBytes, false, now, now);
    }

    /**
     * Factory for a GDPR/CCPA erasure-triggered policy applying to all event
     * types for the given tenant.
     *
     * <p>The returned policy has {@code maxAge = ZERO} so all events for the
     * tenant are eligible for immediate purge when the policy is enforced.
     *
     * @param tenantId tenant whose events must be erased
     * @return new erasure policy valid for the DEFAULT bucket
     */
    public static AepTenantRetentionPolicy erasure(String tenantId) {
        Instant now = Instant.now();
        return new AepTenantRetentionPolicy(
                UUID.randomUUID(), tenantId, DEFAULT_BUCKET,
                Duration.ZERO, 0L, true, now, now);
    }
}
