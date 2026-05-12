/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 * MIGRATED: 2026-02-04
 * DEPENDS_ON: platform:java:core
 */
package com.ghatana.platform.audit;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;

/**
 * Interface for recording and querying audit events.
 *
 * @doc.type interface
 * @doc.purpose Service for recording and querying audit trails
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface AuditService {

    /**
     * Record an audit event.
     *
     * @param event The audit event to record
     * @return Promise completing when the event is persisted
     */
    Promise<Void> record(AuditEvent event);

    /**
     * Query audit events with custom criteria.
     *
     * @param query The audit query criteria
     * @return Promise of list of matching audit events
     */
    Promise<List<AuditEvent>> query(AuditQuery query);

    /**
     * Query audit events for a specific project within a time range.
     *
     * @param projectId The project identifier
     * @param startDate Start of time range (inclusive)
     * @param endDate End of time range (inclusive)
     * @return Promise of list of audit events for the project
     */
    Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate);

    /**
     * Query audit events for a specific phase within a project and time range.
     *
     * @param projectId The project identifier
     * @param phase The phase identifier
     * @param startDate Start of time range (inclusive)
     * @param endDate End of time range (inclusive)
     * @return Promise of list of audit events for the phase
     */
    Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate);
}
