package com.ghatana.platform.testing;

import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditQuery;
import com.ghatana.platform.audit.AuditService;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double for AuditService that records all logged events for test assertions.
 *
 * @doc.type class
 * @doc.purpose Test double for AuditService to record events for assertions
 * @doc.layer testing
 * @doc.pattern Test Double
 */
public final class RecordingAuditLogger implements AuditService {

    private final List<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public Promise<Void> record(AuditEvent event) {
        events.add(event);
        return Promise.complete();
    }

    @Override
    public Promise<List<AuditEvent>> query(AuditQuery query) {
        // For test purposes, return empty list - query methods not used in recording scenarios
        return Promise.of(List.of());
    }

    @Override
    public Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate) {
        // For test purposes, return empty list - query methods not used in recording scenarios
        return Promise.of(List.of());
    }

    @Override
    public Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate) {
        // For test purposes, return empty list - query methods not used in recording scenarios
        return Promise.of(List.of());
    }

    public List<AuditEvent> getEvents() {
        return new ArrayList<>(events);
    }

    public void clear() {
        events.clear();
    }

    public int getEventCount() {
        return events.size();
    }
}
