/**
 * Audit Log Entry Entity
 *
 * Immutable record of all significant system actions for compliance and security auditing.
 * Used for SOC2, ISO 27001, HIPAA compliance tracking.
 *
 * @see AuditTrailService for logging operations
 * @see AuditLogRepository for data access
 */

export enum AuditAction {
  CREATE = 'CREATE',
  READ = 'READ',
  UPDATE = 'UPDATE',
  DELETE = 'DELETE',
  EXPORT = 'EXPORT',
  APPROVE = 'APPROVE',
  DENY = 'DENY',
  EXECUTE = 'EXECUTE',
  LOGIN = 'LOGIN',
  LOGOUT = 'LOGOUT',
}

export enum ActionStatus {
  SUCCESS = 'SUCCESS',
  FAILURE = 'FAILURE',
  PARTIAL = 'PARTIAL',
}

export interface AuditChange {
  field: string;
  oldValue: unknown;
  newValue: unknown;
}

export interface IAuditLogEntry {
  id: string;
  timestamp: Date;
  actorId: string;
  actorName: string;
  action: AuditAction;
  resource: string;
  resourceId: string;
  changes: AuditChange[];
  status: ActionStatus;
  description?: string;
  ipAddress?: string;
  userAgent?: string;
  errorDetails?: string;
}

/**
 * AuditLogEntry entity class
 *
 * GIVEN: System action (create/update/delete/execute)
 * WHEN: Logged via AuditTrailService
 * THEN: Immutable record stored in PostgreSQL for compliance auditing
 *
 * Database: PostgreSQL table 'audit_logs' (append-only)
 * Indexes: timestamp, actor_id, action, resource
 * Retention: Per compliance requirement (default 7 years)
 * Immutability: No UPDATE/DELETE allowed (only INSERT)
 */
export class AuditLogEntry implements IAuditLogEntry {
  id: string;
  timestamp: Date;
  actorId: string;
  actorName: string;
  action: AuditAction;
  resource: string;
  resourceId: string;
  changes: AuditChange[] = [];
  status: ActionStatus;
  description?: string;
  ipAddress?: string;
  userAgent?: string;
  errorDetails?: string;

  constructor(
    id: string,
    actorId: string,
    actorName: string,
    action: AuditAction,
    resource: string,
    resourceId: string,
    status: ActionStatus = ActionStatus.SUCCESS
  ) {
    this.id = id;
    this.timestamp = new Date();
    this.actorId = actorId;
    this.actorName = actorName;
    this.action = action;
    this.resource = resource;
    this.resourceId = resourceId;
    this.status = status;
  }

  /**
   * Add a change record
   */
  addChange(field: string, oldValue: unknown, newValue: unknown): void {
    this.changes.push({ field, oldValue, newValue });
  }

  /**
   * Record network details
   */
  setNetworkDetails(ipAddress: string, userAgent: string): void {
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
  }

  /**
   * Record failure details
   */
  setFailure(errorDetails: string): void {
    this.status = ActionStatus.FAILURE;
    this.errorDetails = errorDetails;
  }

  /**
   * Validate entry
   */
  validate(): boolean {
    if (!this.id || !this.actorId || !this.action) {
      throw new Error('Invalid audit entry: required fields missing');
    }
    if (this.timestamp > new Date()) {
      throw new Error('Invalid audit entry: timestamp in future');
    }
    return true;
  }

  /**
   * Convert to JSON for storage/transport
   */
  toJSON(): Record<string, unknown> {
    return {
      id: this.id,
      timestamp: this.timestamp.toISOString(),
      actorId: this.actorId,
      actorName: this.actorName,
      action: this.action,
      resource: this.resource,
      resourceId: this.resourceId,
      changes: this.changes,
      status: this.status,
      description: this.description,
      ipAddress: this.ipAddress,
      userAgent: this.userAgent,
      errorDetails: this.errorDetails,
    };
  }

  /**
   * Create from JSON
   */
  static fromJSON(data: Partial<AuditLogEntry>): AuditLogEntry {
    const entry = new AuditLogEntry(
      data.id || '',
      data.actorId || '',
      data.actorName || '',
      data.action || AuditAction.READ,
      data.resource || '',
      data.resourceId || '',
      data.status || ActionStatus.SUCCESS
    );
    if (data.timestamp) entry.timestamp = new Date(data.timestamp);
    if (data.changes) entry.changes = data.changes;
    if (data.description) entry.description = data.description;
    if (data.ipAddress) entry.ipAddress = data.ipAddress;
    if (data.userAgent) entry.userAgent = data.userAgent;
    if (data.errorDetails) entry.errorDetails = data.errorDetails;
    return entry;
  }
}
