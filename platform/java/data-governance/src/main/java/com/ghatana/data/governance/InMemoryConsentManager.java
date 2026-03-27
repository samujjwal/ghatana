/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.data.governance;

import io.activej.promise.Promise;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ConsentManager} implementation for testing and single-node deployments.
 *
 * <p>Consent is stored as a {@code Map<tenantId:subjectId, Set<purpose>>}.
 * Thread-safe via {@link ConcurrentHashMap}.
 *
 * @doc.type class
 * @doc.purpose In-memory consent store supporting record, withdraw, check and enforce
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class InMemoryConsentManager implements ConsentManager {

    /** key = "tenantId:subjectId", value = set of consented purposes */
    private final Map<String, Set<String>> consents = new ConcurrentHashMap<>();

    @Override
    public Promise<Void> recordConsent(String tenantId, String subjectId, String purpose) {
        consents.computeIfAbsent(key(tenantId, subjectId),
            k -> ConcurrentHashMap.newKeySet()).add(purpose);
        return Promise.complete();
    }

    @Override
    public Promise<Void> withdrawConsent(String tenantId, String subjectId, String purpose) {
        Set<String> purposes = consents.get(key(tenantId, subjectId));
        if (purposes != null) {
            purposes.remove(purpose);
        }
        return Promise.complete();
    }

    @Override
    public Promise<Boolean> hasConsent(String tenantId, String subjectId, String purpose) {
        Set<String> purposes = consents.get(key(tenantId, subjectId));
        return Promise.of(purposes != null && purposes.contains(purpose));
    }

    @Override
    public Promise<Void> enforceConsent(String tenantId, String subjectId, String purpose) {
        Set<String> purposes = consents.get(key(tenantId, subjectId));
        if (purposes == null || !purposes.contains(purpose)) {
            return Promise.ofException(new ConsentRequiredException(tenantId, subjectId, purpose));
        }
        return Promise.complete();
    }

    /** Total number of consented (tenant+subject, purpose) pairs stored. */
    public int totalConsentedPairs() {
        return consents.values().stream().mapToInt(Set::size).sum();
    }

    private static String key(String tenantId, String subjectId) {
        return tenantId + ':' + subjectId;
    }
}
