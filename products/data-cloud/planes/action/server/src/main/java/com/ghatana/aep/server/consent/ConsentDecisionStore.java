/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.consent;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * T-23: Server-side consent decision store.
 *
 * <p>Persists and retrieves consent decisions so that the server is the
 * authoritative source of truth rather than the browser's localStorage.
 *
 * <p>Each decision is tenant-scoped and user-scoped. Decisions include:
 * the consent status ({@code granted}, {@code denied}, {@code withdrawn}),
 * the set of purposes the user consented to, and the timestamp of the decision.
 *
 * @doc.type interface
 * @doc.purpose Server-side consent decision persistence
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ConsentDecisionStore {

    /**
     * Record a consent decision for a user.
     *
     * @param tenantId   tenant this user belongs to
     * @param userId     user identifier
     * @param status     consent status: {@code granted}, {@code denied}, or {@code withdrawn}
     * @param purposes   list of purposes the user consented to (empty = no restriction)
     * @param decidedAt  when the decision was recorded
     * @return promise of the persisted record
     */
    Promise<ConsentRecord> recordDecision(
            String tenantId,
            String userId,
            String status,
            List<String> purposes,
            Instant decidedAt);

    /**
     * Retrieve the most recent consent decision for a user.
     *
     * @param tenantId tenant identifier
     * @param userId   user identifier
     * @return promise of the decision, or empty if none exists
     */
    Promise<Optional<ConsentRecord>> getDecision(String tenantId, String userId);

    /**
     * List all consent decisions for a tenant, most recent first.
     *
     * @param tenantId tenant identifier
     * @param limit    maximum number of records to return
     * @param offset   pagination offset
     * @return promise of matching records
     */
    Promise<List<ConsentRecord>> listDecisions(String tenantId, int limit, int offset);

    /**
     * A persisted consent decision record.
     *
     * @param consentId unique record identifier
     * @param tenantId  tenant
     * @param userId    user
     * @param status    consent status string
     * @param purposes  purposes consented to
     * @param decidedAt when the decision was made
     */
    record ConsentRecord(
            String consentId,
            String tenantId,
            String userId,
            String status,
            List<String> purposes,
            Instant decidedAt
    ) {
        public ConsentRecord {
            purposes = purposes != null ? List.copyOf(purposes) : List.of();
        }
    }
}
