/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.inmemory;

import com.ghatana.yappc.api.domain.Trace;
import com.ghatana.yappc.api.domain.Trace.*;
import com.ghatana.yappc.api.repository.TraceRepository;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of TraceRepository.
 *
 * @doc.type class
 * @doc.purpose In-memory storage for traces
 * @doc.layer repository
 * @doc.pattern Repository
 */
public class InMemoryTraceRepository implements TraceRepository {

    private final Map<String, Map<UUID, Trace>> tenantTraces = new ConcurrentHashMap<>();

    private Map<UUID, Trace> getTraceMap(String tenantId) {
        return tenantTraces.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
    }

    @Override
    public Promise<Trace> save(Trace trace) {
        if (trace.getId() == null) {
            trace.setId(UUID.randomUUID());
        }
        getTraceMap(trace.getTenantId()).put(trace.getId(), trace);
        return Promise.of(trace);
    }

    @Override
    public Promise<Trace> findById(String tenantId, UUID id) {
        return Promise.of(getTraceMap(tenantId).get(id));
    }

    @Override
    public Promise<Trace> findByTraceId(String tenantId, String traceId) {
        return Promise.of(
            getTraceMap(tenantId).values().stream()
                .filter(t -> traceId.equals(t.getTraceId()))
                .findFirst()
                .orElse(null)
        );
    }

    @Override
    public Promise<List<Trace>> findByProject(String tenantId, String projectId, int limit) {
        return Promise.of(
            getTraceMap(tenantId).values().stream()
                .filter(t -> projectId.equals(t.getProjectId()))
                .sorted(Comparator.comparing(Trace::getStartTime).reversed())
                .limit(limit)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<Trace>> findByService(String tenantId, String service, int limit) {
        return Promise.of(
            getTraceMap(tenantId).values().stream()
                .filter(t -> service.equals(t.getService()))
                .sorted(Comparator.comparing(Trace::getStartTime).reversed())
                .limit(limit)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<Trace>> findByOperation(String tenantId, String service, String operation, int limit) {
        return Promise.of(
            getTraceMap(tenantId).values().stream()
                .filter(t -> service.equals(t.getService()) && operation.equals(t.getOperation()))
                .sorted(Comparator.comparing(Trace::getStartTime).reversed())
                .limit(limit)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<Trace>> findByStatus(String tenantId, TraceStatus status, int limit) {
        return Promise.of(
            getTraceMap(tenantId).values().stream()
                .filter(t -> status == t.getStatus())
                .sorted(Comparator.comparing(Trace::getStartTime).reversed())
                .limit(limit)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<Trace>> findErrors(String tenantId, String projectId, int limit) {
        return Promise.of(
            getTraceMap(tenantId).values().stream()
                .filter(t -> projectId.equals(t.getProjectId()) && TraceStatus.ERROR == t.getStatus())
                .sorted(Comparator.comparing(Trace::getStartTime).reversed())
                .limit(limit)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<Trace>> findByUser(String tenantId, String userId, int limit) {
        return Promise.of(
            getTraceMap(tenantId).values().stream()
                .filter(t -> userId.equals(t.getUserId()))
                .sorted(Comparator.comparing(Trace::getStartTime).reversed())
                .limit(limit)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<Trace>> findByRequestId(String tenantId, String requestId) {
        return Promise.of(
            getTraceMap(tenantId).values().stream()
                .filter(t -> requestId.equals(t.getRequestId()))
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<Trace>> findSlowTraces(String tenantId, String projectId, long minDurationMs, int limit) {
        return Promise.of(
            getTraceMap(tenantId).values().stream()
                .filter(t -> projectId.equals(t.getProjectId()) && t.getDurationMs() >= minDurationMs)
                .sorted(Comparator.comparingLong(Trace::getDurationMs).reversed())
                .limit(limit)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<Trace>> findByTimeRange(String tenantId, String projectId, Instant start, Instant end, int limit) {
        return Promise.of(
            getTraceMap(tenantId).values().stream()
                .filter(t -> projectId.equals(t.getProjectId()))
                .filter(t -> !t.getStartTime().isBefore(start) && !t.getStartTime().isAfter(end))
                .sorted(Comparator.comparing(Trace::getStartTime).reversed())
                .limit(limit)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<Trace>> findByTag(String tenantId, String tagKey, String tagValue, int limit) {
        return Promise.of(
            getTraceMap(tenantId).values().stream()
                .filter(t -> t.getTags() != null && tagValue.equals(t.getTags().get(tagKey)))
                .sorted(Comparator.comparing(Trace::getStartTime).reversed())
                .limit(limit)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<Long> countByStatus(String tenantId, String projectId, TraceStatus status) {
        long count = getTraceMap(tenantId).values().stream()
            .filter(t -> projectId.equals(t.getProjectId()) && status == t.getStatus())
            .count();
        return Promise.of(count);
    }

    @Override
    public Promise<Double> getAverageDuration(String tenantId, String projectId, String service) {
        double avg = getTraceMap(tenantId).values().stream()
            .filter(t -> projectId.equals(t.getProjectId()) && service.equals(t.getService()))
            .mapToLong(Trace::getDurationMs)
            .average()
            .orElse(0.0);
        return Promise.of(avg);
    }

    @Override
    public Promise<Integer> deleteBefore(String tenantId, Instant before) {
        Map<UUID, Trace> map = getTraceMap(tenantId);
        List<UUID> toDelete = map.values().stream()
            .filter(t -> t.getStartTime().isBefore(before))
            .map(Trace::getId)
            .collect(Collectors.toList());
        toDelete.forEach(map::remove);
        return Promise.of(toDelete.size());
    }

    @Override
    public Promise<Void> delete(String tenantId, UUID id) {
        getTraceMap(tenantId).remove(id);
        return Promise.complete();
    }
}
