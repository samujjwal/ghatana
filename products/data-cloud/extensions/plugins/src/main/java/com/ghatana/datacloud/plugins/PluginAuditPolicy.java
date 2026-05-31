/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugins;

import java.util.Set;

/**
 * Plugin audit policy (P8).
 *
 * <p>Defines the audit requirements for a plugin including what events
 * should be audited and at what level of detail.
 *
 * @doc.type record
 * @doc.purpose Plugin audit policy configuration
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PluginAuditPolicy(
        boolean auditEnabled,
        AuditLevel auditLevel,
        Set<AuditEventType> auditedEvents,
        boolean auditDataAccess,
        boolean auditConfigurationChanges,
        long retentionDays
) {
    public PluginAuditPolicy {
        if (auditedEvents == null) {
            auditedEvents = Set.of();
        }
        if (auditLevel == null) {
            auditLevel = AuditLevel.STANDARD;
        }
        if (retentionDays < 0) {
            retentionDays = 90; // Default 90 days
        }
    }

    /**
     * Returns default audit policy.
     */
    public static PluginAuditPolicy defaultPolicy() {
        return new PluginAuditPolicy(true, AuditLevel.STANDARD, Set.of(
            AuditEventType.INSTALL,
            AuditEventType.UNINSTALL,
            AuditEventType.ENABLE,
            AuditEventType.DISABLE,
            AuditEventType.ERROR
        ), false, true, 90);
    }

    /**
     * Returns true if a specific event type should be audited.
     */
    public boolean shouldAuditEvent(AuditEventType eventType) {
        return auditEnabled && auditedEvents.contains(eventType);
    }

    /**
     * Audit level enumeration.
     */
    public enum AuditLevel {
        /**
         * Minimal audit logging - only critical events.
         */
        MINIMAL,
        
        /**
         * Standard audit logging - important events.
         */
        STANDARD,
        
        /**
         * Detailed audit logging - all events with full context.
         */
        DETAILED,
        
        /**
         * Verbose audit logging - all events with maximum detail.
         */
        VERBOSE
    }

    /**
     * Audit event type enumeration.
     */
    public enum AuditEventType {
        /**
         * Plugin installation event.
         */
        INSTALL,
        
        /**
         * Plugin uninstallation event.
         */
        UNINSTALL,
        
        /**
         * Plugin enable event.
         */
        ENABLE,
        
        /**
         * Plugin disable event.
         */
        DISABLE,
        
        /**
         * Plugin start event.
         */
        START,
        
        /**
         * Plugin stop event.
         */
        STOP,
        
        /**
         * Plugin configuration change event.
         */
        CONFIG_CHANGE,
        
        /**
         * Plugin upgrade event.
         */
        UPGRADE,
        
        /**
         * Plugin data access event.
         */
        DATA_ACCESS,
        
        /**
         * Plugin error event.
         */
        ERROR,
        
        /**
         * Plugin security event.
         */
        SECURITY,
        
        /**
         * Plugin performance event.
         */
        PERFORMANCE
    }
}
