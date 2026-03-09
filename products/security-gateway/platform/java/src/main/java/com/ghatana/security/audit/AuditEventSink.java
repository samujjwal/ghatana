/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 *
 * This source code and the accompanying materials are the confidential
 * and proprietary information of Ghatana Inc. ("Confidential Information").
 * You shall not disclose such Confidential Information and shall use it
 * only in accordance with the terms of the license agreement you entered
 * into with Ghatana Inc.
 *
 * Unauthorized copying of this file, via any medium, is strictly prohibited.
 * Proprietary and confidential.
 */
package com.ghatana.security.audit;

import com.ghatana.platform.audit.AuditEvent;

/**
 * Interface for audit event sinks that handle the storage/forwarding of audit events.
 * Implementations might write to files, databases, message queues, or external audit systems.
 
 *
 * @doc.type interface
 * @doc.purpose Audit event sink
 * @doc.layer core
 * @doc.pattern Interface
*/
public interface AuditEventSink {
    
    /**
     * Emits an audit event to the configured sink.
     *
     * @param event the audit event to emit
     * @throws AuditException if the event cannot be emitted
     */
    void emit(AuditEvent event) throws AuditException;
    
    /**
     * Flushes any buffered audit events to ensure they are persisted.
     *
     * @throws AuditException if the flush operation fails
     */
    void flush() throws AuditException;
    
    /**
     * Closes the audit event sink and releases any resources.
     *
     * @throws AuditException if the close operation fails
     */
    void close() throws AuditException;
}