/**
 * Audit Log Service - Re-export from shared platform component
 * 
 * @doc.type service
 * @doc.purpose Provide audit logging for privacy-sensitive operations
 * @doc.layer frontend
 * @doc.pattern Audit Service
 */

export { auditLogService, useAuditLog, AuditLogService } from '@ghatana/audit-ui';
export type { AuditEvent, AuditLogQueryResponse, AuditLogQueryOptions } from '@ghatana/audit-ui';
