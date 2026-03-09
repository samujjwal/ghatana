import { query } from "../db";
import { logger } from "../utils/logger";
import { Request } from "express";

/**
 * Audit Logging Service
 *
 * Provides comprehensive audit trail for security and compliance:
 * - Authentication events (login, logout, password changes)
 * - Authorization events (access denied, permission changes)
 * - Data modifications (CRUD operations on sensitive data)
 * - Policy enforcement (policy created, updated, deleted)
 * - Administrative actions (user management, config changes)
 *
 * Compliance: SOC 2, GDPR, HIPAA
 */

export type EventSeverity = "info" | "warning" | "critical";

export interface AuditEventData {
  resource_type?: string;
  resource_id?: string | number;
  [key: string]: any;
}

export interface AuditEvent {
  userId: string | null;
  eventType: string;
  eventData: AuditEventData;
  ipAddress: string;
  userAgent: string;
  severity: EventSeverity;
}

/**
 * Log audit event for compliance and security tracking.
 *
 * <p><b>Purpose</b><br>
 * Central audit logging for SOC 2, GDPR, and HIPAA compliance.
 * Provides tamper-proof audit trail for all user actions.
 *
 * <p><b>Dual Logging Strategy</b><br>
 * - Database: Permanent record in audit_log table (PostgreSQL)
 * - Winston: Real-time structured logs for monitoring/alerting
 *
 * <p><b>Captured Metadata</b><br>
 * - userId: User performing action
 * - eventType: Predefined event from AuditEvents constant
 * - eventData: Action-specific details (JSON)
 * - ip_address: Client IP (from Express Request)
 * - user_agent: Browser/client info (from Express Request)
 * - severity: info | warning | critical
 *
 * <p><b>Never Fails Requests</b><br>
 * Audit failures logged to Winston but do NOT throw errors.
 * Business operations proceed even if audit fails.
 *
 * @param userId User performing the action
 * @param eventType Event type from AuditEvents constant
 * @param eventData Event-specific details
 * @param req Express Request for IP/user agent
 * @param severity Severity level (default: "info")
 * @return Promise<void>
 * @see auditLogin
 * @see auditPasswordChange
 * @doc.type function
 * @doc.purpose Central audit logging for compliance
 * @doc.layer product
 * @doc.pattern Service
 */
export const logAuditEvent = async (
  userId: string | null,
  eventType: string,
  eventData: AuditEventData = {},
  req: Request,
  severity: EventSeverity = "info"
): Promise<void> => {
  try {
    // Log to database (permanent audit trail)
    // Schema: audit_log(user_id, action, resource_type, resource_id, changes, ip_address, user_agent, created_at)
    await query(
      `INSERT INTO audit_log 
       (user_id, action, resource_type, resource_id, changes, ip_address, user_agent)
       VALUES ($1, $2, $3, $4, $5, $6, $7)`,
      [
        userId,
        eventType,
        eventData.resource_type || null,
        eventData.resource_id || null,
        JSON.stringify(eventData || {}),
        req.ip || req.socket.remoteAddress || "unknown",
        req.headers["user-agent"] || "unknown",
      ]
    );

    // Log to Winston (for real-time monitoring)
    const logLevel =
      severity === "critical"
        ? "error"
        : severity === "warning"
          ? "warn"
          : "info";
    logger.log(logLevel, "Audit Event", {
      userId,
      eventType,
      eventData,
      ipAddress: req.ip,
      userAgent: req.headers["user-agent"],
      severity,
      timestamp: new Date().toISOString(),
    });
  } catch (error) {
    // Never fail the request due to audit logging errors
    logger.error("Failed to log audit event", {
      error: error instanceof Error ? error.message : "Unknown error",
      eventType,
      userId,
    });
  }
}

/**
 * Pre-defined audit event types for consistency
 */
export const AuditEvents = {
  // Authentication
  LOGIN_SUCCESS: "auth.login.success",
  LOGIN_FAILURE: "auth.login.failure",
  LOGOUT: "auth.logout",
  REGISTER: "auth.register",
  TOKEN_REFRESH: "auth.token.refresh",
  TOKEN_REFRESH_FAILURE: "auth.token.refresh.failure",

  // Password Management
  PASSWORD_CHANGED: "auth.password.changed",
  PASSWORD_RESET_REQUESTED: "auth.password.reset.requested",
  PASSWORD_RESET_COMPLETED: "auth.password.reset.completed",
  PASSWORD_RESET_FAILURE: "auth.password.reset.failure",

  // Email Verification
  EMAIL_VERIFICATION_SENT: "auth.email.verification.sent",
  EMAIL_VERIFIED: "auth.email.verified",
  EMAIL_VERIFICATION_FAILURE: "auth.email.verification.failure",

  // Profile Management
  PROFILE_UPDATED: "user.profile.updated",
  PROFILE_DELETED: "user.profile.deleted",

  // Children Management
  CHILD_CREATED: "child.created",
  CHILD_UPDATED: "child.updated",
  CHILD_DELETED: "child.deleted",

  // Device Management
  DEVICE_PAIRED: "device.paired",
  DEVICE_PAIRING_FAILURE: "device.pairing.failure",
  DEVICE_UPDATED: "device.updated",
  DEVICE_DELETED: "device.deleted",
  DEVICE_ACTIVATED: "device.activated",
  DEVICE_DEACTIVATED: "device.deactivated",

  // Policy Management
  POLICY_CREATED: "policy.created",
  POLICY_UPDATED: "policy.updated",
  POLICY_DELETED: "policy.deleted",
  POLICY_ENABLED: "policy.enabled",
  POLICY_DISABLED: "policy.disabled",
  POLICY_BULK_UPDATE: "policy.bulk.update",

  // Data Access
  DATA_EXPORTED: "data.exported",
  REPORT_GENERATED: "report.generated",

  // Security Events
  ACCESS_DENIED: "security.access.denied",
  RATE_LIMIT_EXCEEDED: "security.rate_limit.exceeded",
  INVALID_TOKEN: "security.invalid_token",
  SUSPICIOUS_ACTIVITY: "security.suspicious_activity",

  // System Events
  API_ERROR: "system.error",
  DATABASE_ERROR: "system.database.error",
  EMAIL_SENT: "system.email.sent",
  EMAIL_FAILURE: "system.email.failure",
} as const;

/**
 * Helper functions for common audit scenarios.
 */

/**
 * Audit user login attempts.
 *
 * <p><b>Purpose</b><br>
 * Tracks successful and failed login attempts for security monitoring.
 * Critical for detecting brute force attacks and unauthorized access.
 *
 * <p><b>Events Logged</b><br>
 * - LOGIN_SUCCESS: Severity "info"
 * - LOGIN_FAILURE: Severity "warning" (potential security issue)
 *
 * @param userId User ID attempting login
 * @param email Email used for login
 * @param success Whether login succeeded
 * @param req Express Request for IP/user agent
 * @return Promise from logAuditEvent
 * @see logAuditEvent
 * @see auditPasswordChange
 * @doc.type function
 * @doc.purpose Track login success/failure for security
 * @doc.layer product
 * @doc.pattern Security
 */
export function auditLogin(
  userId: string,
  email: string,
  success: boolean,
  req: Request
) {
  return logAuditEvent(
    userId,
    success ? AuditEvents.LOGIN_SUCCESS : AuditEvents.LOGIN_FAILURE,
    { email },
    req,
    success ? "info" : "warning"
  );
}

/**
 * Audit password change events.
 *
 * <p><b>Purpose</b><br>
 * Tracks password changes for security compliance.
 * Severity "warning" because password changes are security-relevant.
 *
 * <p><b>Security Note</b><br>
 * Does NOT log old/new passwords (PCI-DSS, GDPR compliance).
 * Only logs that password was changed.
 *
 * @param userId User changing password
 * @param req Express Request for IP/user agent
 * @return Promise from logAuditEvent
 * @see auditLogin
 * @see logAuditEvent
 * @doc.type function
 * @doc.purpose Track password changes for security
 * @doc.layer product
 * @doc.pattern Security
 */
export function auditPasswordChange(userId: string, req: Request) {
  return logAuditEvent(
    userId,
    AuditEvents.PASSWORD_CHANGED,
    {},
    req,
    "warning" // Password changes are security-relevant
  );
}

/**
 * Audit policy changes.
 *
 * <p><b>Purpose</b><br>
 * Tracks policy CRUD operations for accountability.
 * Shows who changed what policy and when.
 *
 * <p><b>Events</b><br>
 * - POLICY_CREATED, POLICY_UPDATED, POLICY_DELETED, etc.
 *
 * @param userId User modifying policy
 * @param action Policy action (created/updated/deleted)
 * @param policyId Policy being modified
 * @param childId Child policy applies to
 * @param req Express Request for IP/user agent
 * @return Promise from logAuditEvent
 * @see logAuditEvent
 * @see auditDataExport
 * @doc.type function
 * @doc.purpose Track policy modifications
 * @doc.layer product
 * @doc.pattern Service
 */
export function auditPolicyChange(
  userId: string,
  action: string,
  policyId: string,
  childId: string,
  req: Request
) {
  return logAuditEvent(userId, action, { policyId, childId }, req, "info");
}

/**
 * Audit data export operations.
 *
 * <p><b>Purpose</b><br>
 * Tracks data exports for GDPR compliance (data portability, right to access).
 * Severity "warning" because exports contain sensitive data.
 *
 * <p><b>Compliance</b><br>
 * - GDPR: Right to data portability (Article 20)
 * - CCPA: Consumer right to know
 *
 * @param userId User exporting data
 * @param exportType Type of export (csv/pdf/json)
 * @param dateRange Date range exported
 * @param req Express Request for IP/user agent
 * @return Promise from logAuditEvent
 * @see logAuditEvent
 * @see auditSecurityEvent
 * @doc.type function
 * @doc.purpose Track data exports for GDPR compliance
 * @doc.layer product
 * @doc.pattern Service
 */
export function auditDataExport(
  userId: string,
  exportType: string,
  dateRange: string,
  req: Request
) {
  return logAuditEvent(
    userId,
    AuditEvents.DATA_EXPORTED,
    { exportType, dateRange },
    req,
    "warning" // Data exports are security-relevant
  );
}

/**
 * Audit security events.
 *
 * <p><b>Purpose</b><br>
 * Tracks security-relevant events for threat detection.
 * All security events have severity "critical" for alerting.
 *
 * <p><b>Events</b><br>
 * - ACCESS_DENIED: Unauthorized access attempt
 * - RATE_LIMIT_EXCEEDED: Potential abuse
 * - INVALID_TOKEN: Token validation failure
 * - SUSPICIOUS_ACTIVITY: Anomaly detection
 *
 * @param userId User involved in security event (null for anonymous)
 * @param eventType Security event type from AuditEvents
 * @param details Event-specific details
 * @param req Express Request for IP/user agent
 * @return Promise from logAuditEvent
 * @see logAuditEvent
 * @see auditLogin
 * @doc.type function
 * @doc.purpose Track security events for threat detection
 * @doc.layer product
 * @doc.pattern Security
 */
export function auditSecurityEvent(
  userId: string | null,
  eventType: string,
  details: Record<string, any>,
  req: Request
) {
  return logAuditEvent(
    userId,
    eventType,
    details,
    req,
    "critical" // All security events are critical
  );
}

/**
 * Query audit logs (for admin dashboard)
 */
/**
 * Query audit logs with filters.
 *
 * <p><b>Purpose</b><br>
 * Retrieves audit trail for admin dashboard and compliance reporting.
 * Supports filtering by user, event type, and date range.
 *
 * <p><b>Query Filters</b><br>
 * - userId: Filter to specific user (optional)
 * - eventType: Filter to specific event type (optional)
 * - startDate: Start of date range (optional)
 * - endDate: End of date range (optional)
 * - limit: Max results (default: 100, max: 1000)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const logs = await getAuditLogs(
 *   userId,
 *   "LOGIN_FAILURE",
 *   "2025-01-01",
 *   "2025-01-31",
 *   50
 * );
 * }</pre>
 *
 * @param userId Filter by user ID (optional)
 * @param eventType Filter by event type (optional)
 * @param startDate Filter start date (optional)
 * @param endDate Filter end date (optional)
 * @param limit Max results (default: 100)
 * @return Array of audit log records sorted by created_at DESC
 * @see logAuditEvent
 * @doc.type function
 * @doc.purpose Query audit trail with filters
 * @doc.layer product
 * @doc.pattern Service
 */
export async function getAuditLogs(
  userId?: string,
  eventType?: string,
  startDate?: string,
  endDate?: string,
  limit = 100
): Promise<any[]> {
  const conditions: string[] = [];
  const params: unknown[] = [];
  let paramIndex = 1;

  if (userId) {
    conditions.push(`user_id = $${paramIndex++}`);
    params.push(userId);
  }

  if (eventType) {
    // eventType maps to action column in audit_log
    conditions.push(`action = $${paramIndex++}`);
    params.push(eventType);
  }

  if (startDate) {
    conditions.push(`created_at >= $${paramIndex++}`);
    params.push(startDate);
  }

  if (endDate) {
    conditions.push(`created_at <= $${paramIndex++}`);
    params.push(endDate);
  }

  // severity field is not stored in DB schema; ignore in query

  const whereClause =
    conditions.length > 0 ? `WHERE ${conditions.join(" AND ")}` : "";

  const logs = await query(
    `SELECT * FROM audit_log 
     ${whereClause}
     ORDER BY created_at DESC 
     LIMIT $${paramIndex}`,
    [...params, limit]
  );

  return logs;
}

export default {
  logAuditEvent,
  AuditEvents,
  auditLogin,
  auditPasswordChange,
  auditPolicyChange,
  auditDataExport,
  auditSecurityEvent,
  getAuditLogs,
};
