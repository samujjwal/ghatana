/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.inmemory;

import com.ghatana.yappc.api.domain.LogEntry;
import com.ghatana.yappc.api.domain.LogEntry.*;
import com.ghatana.yappc.api.repository.LogEntryRepository;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of LogEntryRepository.
 *
 * @doc.type class
 * @doc.purpose In-memory storage for log entries
 * @doc.layer repository
 * @doc.pattern Repository
 */
public class InMemoryLogEntryRepository implements LogEntryRepository {

    private final Map<String, Map<UUID, LogEntry>> tenantLogs = new ConcurrentHashMap<>();

    private Map<UUID, LogEntry> getLogMap(String tenantId) {
        return tenantLogs.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
    }

    @Override
    public Promise<LogEntry> save(LogEntry log) {
        if (log.getId() == null) {
            log.setId(UUID.randomUUID());
        }
        getLogMap(log.getTenantId()).put(log.getId(), log);
        return Promise.of(log);
    }

    @Override
    public Promise<List<LogEntry>> saveBatch(List<LogEntry> logs) {
        List<LogEntry> saved = new ArrayList<>();
        for (LogEntry log : logs) {
            if (log.getId() == null) {
                log.setId(UUID.randomUUID());
            }
            getLogMap(log.getTenantId()).put(log.getId(), log);
            saved.add(log);
        }
        return Promise.of(saved);
    }

    @Override
    public Promise<LogEntry> findById(String tenantId, UUID id) {
        return Promise.of(getLogMap(tenantId).get(id));
    }

    @Override
    public Promise<List<LogEntry>> findByProject(String tenantId, String projectId, int limit) {
        return Promise.of(
            getLogMap(tenantId).values().stream()
                .filter(l -> projectId.equals(l.getProjectId()))
                .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<LogEntry>> findByService(String tenantId, String service, int limit) {
        return Promise.of(
            getLogMap(tenantId).values().stream()
                .filter(l -> service.equals(l.getService()))
                .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<LogEntry>> findByLevel(String tenantId, LogLevel level, int limit) {
        return Promise.of(
            getLogMap(tenantId).values().stream()
                .filter(l -> level == l.getLevel())
                .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<LogEntry>> findErrors(String tenantId, String projectId, int limit) {
        return Promise.of(
            getLogMap(tenantId).values().stream()
                .filter(l -> projectId.equals(l.getProjectId()) && l.isError())
                .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<LogEntry>> findByTraceId(String tenantId, String traceId) {
        return Promise.of(
            getLogMap(tenantId).values().stream()
                .filter(l -> traceId.equals(l.getTraceId()))
                .sorted(Comparator.comparing(LogEntry::getTimestamp))
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<LogEntry>> findByRequestId(String tenantId, String requestId) {
        return Promise.of(
            getLogMap(tenantId).values().stream()
                .filter(l -> requestId.equals(l.getRequestId()))
                .sorted(Comparator.comparing(LogEntry::getTimestamp))
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<LogEntry>> findByUser(String tenantId, String userId, int limit) {
        return Promise.of(
            getLogMap(tenantId).values().stream()
                .filter(l -> userId.equals(l.getUserId()))
                .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<LogEntry>> searchByMessage(String tenantId, String query, int limit) {
        String lowerQuery = query.toLowerCase();
        return Promise.of(
            getLogMap(tenantId).values().stream()
                .filter(l -> l.getMessage() != null && l.getMessage().toLowerCase().contains(lowerQuery))
                .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<List<LogEntry>> findByTimeRange(String tenantId, String projectId, Instant start, Instant end, int limit) {
        return Promise.of(
            getLogMap(tenantId).values().stream()
                .filter(l -> projectId.equals(l.getProjectId()))
                .filter(l -> !l.getTimestamp().isBefore(start) && !l.getTimestamp().isAfter(end))
                .sorted(Comparator.comparing(LogEntry::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<Long> countByLevel(String tenantId, String projectId, LogLevel level) {
        long count = getLogMap(tenantId).values().stream()
            .filter(l -> projectId.equals(l.getProjectId()) && level == l.getLevel())
            .count();
        return Promise.of(count);
    }

    @Override
    public Promise<Integer> deleteBefore(String tenantId, Instant before) {
        Map<UUID, LogEntry> map = getLogMap(tenantId);
        List<UUID> toDelete = map.values().stream()
            .filter(l -> l.getTimestamp().isBefore(before))
            .map(LogEntry::getId)
            .collect(Collectors.toList());
        toDelete.forEach(map::remove);
        return Promise.of(toDelete.size());
    }

    @Override
    public Promise<Void> delete(String tenantId, UUID id) {
        getLogMap(tenantId).remove(id);
        return Promise.complete();
    }
}
