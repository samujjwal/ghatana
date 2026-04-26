/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.consent;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * T-23: In-memory {@link ConsentDecisionStore} for development and test environments.
 *
 * <p>Stores decisions in a {@code ConcurrentHashMap} keyed by {@code tenantId:userId}.
 * Not suitable for production — state is lost on restart.
 *
 * @doc.type class
 * @doc.purpose In-memory consent decision store for dev/test
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class InMemoryConsentDecisionStore implements ConsentDecisionStore {

    private final Map<String, List<ConsentRecord>> records = new ConcurrentHashMap<>();

    @Override
    public Promise<ConsentRecord> recordDecision(
            String tenantId,
            String userId,
            String status,
            List<String> purposes,
            Instant decidedAt) {
        ConsentRecord record = new ConsentRecord(
                UUID.randomUUID().toString(),
                tenantId,
                userId,
                status,
                purposes,
                decidedAt);
        records.computeIfAbsent(compositeKey(tenantId, userId), k -> new ArrayList<>()).add(record);
        return Promise.of(record);
    }

    @Override
    public Promise<Optional<ConsentRecord>> getDecision(String tenantId, String userId) {
        List<ConsentRecord> history = records.getOrDefault(compositeKey(tenantId, userId), List.of());
        Optional<ConsentRecord> latest = history.stream()
                .max(Comparator.comparing(ConsentRecord::decidedAt));
        return Promise.of(latest);
    }

    @Override
    public Promise<List<ConsentRecord>> listDecisions(String tenantId, int limit, int offset) {
        List<ConsentRecord> all = records.values().stream()
                .flatMap(List::stream)
                .filter(r -> tenantId.equals(r.tenantId()))
                .sorted(Comparator.comparing(ConsentRecord::decidedAt).reversed())
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
        return Promise.of(List.copyOf(all));
    }

    private static String compositeKey(String tenantId, String userId) {
        return tenantId + "::" + userId;
    }
}
