/**
 * Audit event types and schemas for role/permission tracking
 * 
 * This module defines the core data structures for audit logging
 * across the persona management system.
 */

/**
 * Types of actions that can be audited
 */
export enum AuditAction {
    // Role actions
    ROLE_CREATED = 'role.created',
    ROLE_UPDATED = 'role.updated',
    ROLE_DELETED = 'role.deleted',
    ROLE_CLONED = 'role.cloned',

    // Permission actions
    PERMISSION_ADDED = 'permission.added',
    PERMISSION_REMOVED = 'permission.removed',
    PERMISSION_MODIFIED = 'permission.modified',

    // Inheritance actions
    INHERITANCE_ADDED = 'inheritance.added',
    INHERITANCE_REMOVED = 'inheritance.removed',

    // Bulk actions
    BULK_PERMISSION_ASSIGN = 'bulk.permission.assign',
    BULK_PERMISSION_REVOKE = 'bulk.permission.revoke',
    BULK_ROLE_UPDATE = 'bulk.role.update',

    // Persona actions
    PERSONA_CREATED = 'persona.created',
    PERSONA_UPDATED = 'persona.updated',
    PERSONA_DELETED = 'persona.deleted',

    // Plugin actions
    PLUGIN_ENABLED = 'plugin.enabled',
    PLUGIN_DISABLED = 'plugin.disabled',
    PLUGIN_CONFIGURED = 'plugin.configured',
}

/**
 * Resource types that can be audited
 */
export enum AuditResourceType {
    ROLE = 'role',
    PERMISSION = 'permission',
    PERSONA = 'persona',
    PLUGIN = 'plugin',
    INHERITANCE = 'inheritance',
}

/**
 * Severity levels for audit events
 */
export enum AuditSeverity {
    INFO = 'info',
    WARNING = 'warning',
    ERROR = 'error',
    CRITICAL = 'critical',
}

/**
 * Change details for before/after comparison
 */
export interface AuditChange {
    field: string;
    oldValue: unknown;
    newValue: unknown;
}

/**
 * Metadata attached to audit events
 */
export interface AuditMetadata {
    // User context
    userId: string;
    userName?: string;
    userEmail?: string;

    // Session context
    sessionId?: string;
    ipAddress?: string;
    userAgent?: string;

    // Request context
    requestId?: string;
    apiEndpoint?: string;

    // Additional context
    reason?: string;
    tags?: string[];
    [key: string]: unknown;
}

/**
 * Core audit event structure
 */
export interface AuditEvent {
    // Unique event identifier
    eventId: string;

    // Timestamp (ISO 8601)
    timestamp: string;

    // Action performed
    action: AuditAction;

    // Resource information
    resourceType: AuditResourceType;
    resourceId: string;
    resourceName?: string;

    // Change tracking
    changes: AuditChange[];

    // Event classification
    severity: AuditSeverity;

    // Context
    metadata: AuditMetadata;

    // Optional fields
    parentEventId?: string; // For related events
    success: boolean;
    errorMessage?: string;
}

/**
 * Audit query filters
 */
export interface AuditFilter {
    // Time range
    startTime?: string;
    endTime?: string;

    // Actions
    actions?: AuditAction[];

    // Resources
    resourceTypes?: AuditResourceType[];
    resourceIds?: string[];

    // Users
    userIds?: string[];

    // Severity
    severities?: AuditSeverity[];

    // Search
    searchQuery?: string;

    // Pagination
    limit?: number;
    offset?: number;
}

/**
 * Audit query result
 */
export interface AuditQueryResult {
    events: AuditEvent[];
    total: number;
    hasMore: boolean;
}

/**
 * Audit statistics
 */
export interface AuditStats {
    totalEvents: number;
    eventsByAction: Record<AuditAction, number>;
    eventsByResourceType: Record<AuditResourceType, number>;
    eventsBySeverity: Record<AuditSeverity, number>;
    topUsers: Array<{ userId: string; userName?: string; count: number }>;
    recentActivity: AuditEvent[];
}

/**
 * Time series data point for audit analytics
 */
export interface AuditTimeSeriesPoint {
    timestamp: string;
    count: number;
    breakdown?: Record<string, number>;
}

/**
 * Audit analytics data
 */
export interface AuditAnalytics {
    timeSeries: AuditTimeSeriesPoint[];
    stats: AuditStats;
    period: {
        start: string;
        end: string;
    };
}
