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

/**
 * Interface for recording audit events.
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
}
