/**
 * Audit domain types — mirrors the Java K-07 domain model.
 *
 * These types are the TypeScript representation of the data model defined in
 * `com.ghatana.appplatform.audit.domain.AuditEntry`.
 */

export type AuditOutcome = "SUCCESS" | "FAILURE" | "PARTIAL";

export interface AuditActor {
  userId: string;
  role: string;
  ipAddress?: string;
  sessionId?: string;
}

export interface AuditResource {
  type: string;
  id: string;
  parentId?: string;
}

export interface AuditPayload {
  action: string;
  actor: AuditActor;
  resource: AuditResource;
  outcome: AuditOutcome;
  tenantId: string;
  details?: Record<string, unknown>;
  traceId?: string;
  /** ISO-8601 timestamp. Defaults to now if not provided. */
  timestampGregorian?: string;
  /** Bikram Sambat calendar date (YYYY-MM-DD). Empty string when K-15 unavailable. */
  timestampBs?: string;
}

export interface AuditReceipt {
  auditId: string;
  sequenceNumber: number;
  previousHash: string;
  currentHash: string;
  timestamp: string;
}

export interface AuditStoreClient {
  log(payload: AuditPayload): Promise<AuditReceipt>;
}
