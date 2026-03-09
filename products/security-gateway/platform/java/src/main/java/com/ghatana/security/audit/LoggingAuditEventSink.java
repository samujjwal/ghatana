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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple logging-based audit event sink for development and testing.
 * In production, this would be replaced with a more robust implementation
 * that writes to persistent storage or forwards to an external audit system.
 
 *
 * @doc.type class
 * @doc.purpose Logging audit event sink
 * @doc.layer core
 * @doc.pattern Component
*/
public class LoggingAuditEventSink implements AuditEventSink {
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    
    @Override
    public void emit(AuditEvent event) throws AuditException {
        try {
            String action = event.getDetails() != null ? (String) event.getDetails().get("action") : "UNKNOWN";
            String status = Boolean.TRUE.equals(event.getSuccess()) ? "SUCCESS" : "FAILURE";
            
            auditLogger.info("AUDIT: {} | Principal: {} | Resource: {} | Action: {} | Status: {} | Details: {}", 
                           event.getEventType(),
                           event.getPrincipal(),
                           event.getResourceId(),
                           action,
                           status,
                           event.getDetails());
        } catch (Exception e) {
            throw new AuditException("Failed to emit audit event to log", e);
        }
    }
    
    @Override
    public void flush() throws AuditException {
        // No buffering in this implementation, so flush is a no-op
    }
    
    @Override
    public void close() throws AuditException {
        // No resources to close in this implementation
    }
}