/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.data.governance;

import io.activej.promise.Promise;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link PurposeLimitationEnforcer} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Suitable for single-node deployments and tests. Production deployments should replace
 * this with a distributed implementation backed by Redis or the policy engine.
 *
 * @doc.type class
 * @doc.purpose In-memory implementation of PurposeLimitationEnforcer for dev/test
 * @doc.layer platform
 * @doc.pattern Repository
 */
public final class DefaultPurposeLimitationEnforcer implements PurposeLimitationEnforcer {

    /** key = "tenantId:dataId", value = immutable snapshot of allowed purposes. */
    private final Map<String, Set<String>> bindings = new ConcurrentHashMap<>();

    @Override
    public Promise<Void> bindPurpose(String tenantId, String dataId, Set<String> allowedPurposes) {
        if (allowedPurposes == null || allowedPurposes.isEmpty()) {
            return Promise.ofException(
                new IllegalArgumentException("allowedPurposes must not be null or empty"));
        }
        bindings.put(key(tenantId, dataId), Collections.unmodifiableSet(new HashSet<>(allowedPurposes)));
        return Promise.complete();
    }

    @Override
    public Promise<Void> enforceForPurpose(String tenantId, String dataId, String requestedPurpose) {
        Set<String> allowed = bindings.getOrDefault(key(tenantId, dataId), Set.of());
        if (!allowed.contains(requestedPurpose)) {
            return Promise.ofException(new PurposeViolationException(tenantId, dataId, requestedPurpose, allowed));
        }
        return Promise.complete();
    }

    @Override
    public Promise<Set<String>> getAllowedPurposes(String tenantId, String dataId) {
        Set<String> result = bindings.getOrDefault(key(tenantId, dataId), Set.of());
        return Promise.of(result);
    }

    private static String key(String tenantId, String dataId) {
        return tenantId + ":" + dataId;
    }
}
