/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.compliance;

import io.activej.promise.Promise;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link RetentionPolicyEnforcer}.
 *
 * <p>Retention deadlines are stored as absolute {@link Instant} values.
 * Production deployments should persist deadlines to a durable store.
 *
 * @doc.type class
 * @doc.purpose In-memory retention policy enforcer for dev/test
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class InMemoryRetentionPolicyEnforcer implements RetentionPolicyEnforcer {

    private record RetentionEntry(Instant expiresAt, boolean scheduledForDeletion) {}

    /** key = "tenantId:dataId" */
    private final Map<String, RetentionEntry> entries = new ConcurrentHashMap<>();
    private final Set<String> deletionQueue = ConcurrentHashMap.newKeySet();

    @Override
    public Promise<Void> registerRetention(String tenantId, String dataId, Duration retentionPeriod) {
        entries.put(key(tenantId, dataId),
            new RetentionEntry(Instant.now().plus(retentionPeriod), false));
        return Promise.complete();
    }

    @Override
    public Promise<Void> checkRetention(String tenantId, String dataId) {
        String k = key(tenantId, dataId);
        if (deletionQueue.contains(k)) {
            return Promise.ofException(new RetentionExpiredException(tenantId, dataId));
        }
        RetentionEntry entry = entries.get(k);
        if (entry == null) {
            // No retention record means access is permitted (open policy for unregistered data)
            return Promise.complete();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            return Promise.ofException(new RetentionExpiredException(tenantId, dataId));
        }
        return Promise.complete();
    }

    @Override
    public Promise<Void> scheduleDeletion(String tenantId, String dataId) {
        deletionQueue.add(key(tenantId, dataId));
        return Promise.complete();
    }

    private static String key(String tenantId, String dataId) {
        return tenantId + ":" + dataId;
    }
}
