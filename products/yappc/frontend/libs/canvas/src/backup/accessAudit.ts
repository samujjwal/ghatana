/**
 * Access & Audit Manager for Restore Operations
 *
 * Provides dual-control approval workflow, audit logging, and alert notifications
 * for restore operations to meet compliance and security requirements.
 *
 * Features:
 * - Dual-control approval: Restore requires approver sign-off
 * - Audit log: Each restore recorded with actor/time/reason
 * - Alerting: Restore events trigger alert channel
 * - Approval workflow: Request → Grant/Deny with role verification
 * - Alert priorities: info, warning, critical with configurable channels
 *
 * @module libs/canvas/src/backup/accessAudit
 */

/**
 * Approval status for restore requests
 */
export type ApprovalStatus = 'pending' | 'granted' | 'denied' | 'expired';

/**
 * User role for approval workflow
 */
export type UserRole = 'requester' | 'approver' | 'admin';

/**
 * Alert priority level
 */
export type AlertPriority = 'info' | 'warning' | 'critical';

/**
 * Alert channel type
 */
export type AlertChannel = 'email' | 'slack' | 'pagerduty' | 'webhook';

/**
 * Audit log action type
 */
export type AuditAction =
  | 'restore_request'
  | 'restore_approve'
  | 'restore_deny'
  | 'restore_start'
  | 'restore_complete'
  | 'restore_fail'
  | 'alert_sent';

/**
 * Approval request for restore operation
 */
export interface ApprovalRequest {
  /** Unique request ID */
  id: string;
  /** Snapshot to restore */
  snapshotId: string;
  /** Target environment */
  environment: 'staging' | 'production';
  /** User requesting restore */
  requestedBy: string;
  /** Request timestamp */
  requestedAt: number;
  /** Current approval status */
  status: ApprovalStatus;
  /** Reason for restore */
  reason: string;
  /** User who approved/denied */
  reviewedBy?: string;
  /** Review timestamp */
  reviewedAt?: number;
  /** Review comment */
  reviewComment?: string;
  /** Request expiration time */
  expiresAt?: number;
  /** Additional metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Audit log entry
 */
export interface AuditLogEntry {
  /** Unique log entry ID */
  id: string;
  /** Action performed */
  action: AuditAction;
  /** User performing action */
  actor: string;
  /** User role at time of action */
  actorRole: UserRole;
  /** Timestamp of action */
  timestamp: number;
  /** Snapshot ID if applicable */
  snapshotId?: string;
  /** Environment if applicable */
  environment?: 'staging' | 'production';
  /** Related approval request ID */
  approvalRequestId?: string;
  /** Action reason/description */
  reason?: string;
  /** Action outcome (success/failure) */
  success?: boolean;
  /** Error message if failed */
  error?: string;
  /** Additional metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Alert notification
 */
export interface Alert {
  /** Unique alert ID */
  id: string;
  /** Alert priority */
  priority: AlertPriority;
  /** Alert title */
  title: string;
  /** Alert message */
  message: string;
  /** Related action */
  action: AuditAction;
  /** Actor who triggered alert */
  actor: string;
  /** Alert timestamp */
  timestamp: number;
  /** Target channels */
  channels: AlertChannel[];
  /** Delivery status per channel */
  deliveryStatus: Record<AlertChannel, 'pending' | 'sent' | 'failed'>;
  /** Related snapshot ID */
  snapshotId?: string;
  /** Related environment */
  environment?: 'staging' | 'production';
  /** Additional metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Alert configuration
 */
export interface AlertConfig {
  /** Enable alerts */
  enabled: boolean;
  /** Default channels */
  defaultChannels: AlertChannel[];
  /** Priority-specific channel overrides */
  channelsByPriority?: Record<AlertPriority, AlertChannel[]>;
  /** Alert delivery handler */
  deliveryHandler?: (alert: Alert, channel: AlertChannel) => Promise<boolean>;
}

/**
 * Access & Audit configuration
 */
export interface AccessAuditConfig {
  /** Require dual-control approval */
  requireApproval: boolean;
  /** Approval expiration time (ms) */
  approvalExpirationMs: number;
  /** Allowed approver roles */
  approverRoles: UserRole[];
  /** Enable audit logging */
  enableAuditLog: boolean;
  /** Maximum audit log entries */
  maxAuditLogEntries: number;
  /** Alert configuration */
  alertConfig: AlertConfig;
}

/**
 * Access & Audit Manager state
 */
interface AccessAuditState {
  /** Approval requests by ID */
  approvalRequests: Map<string, ApprovalRequest>;
  /** Audit log entries */
  auditLog: AuditLogEntry[];
  /** Alerts by ID */
  alerts: Map<string, Alert>;
  /** Configuration */
  config: AccessAuditConfig;
}

/**
 * Default configuration
 */
const DEFAULT_CONFIG: AccessAuditConfig = {
  requireApproval: true,
  approvalExpirationMs: 24 * 60 * 60 * 1000, // 24 hours
  approverRoles: ['approver', 'admin'],
  enableAuditLog: true,
  maxAuditLogEntries: 10000,
  alertConfig: {
    enabled: true,
    defaultChannels: ['email'],
    channelsByPriority: {
      info: ['email'],
      warning: ['email', 'slack'],
      critical: ['email', 'slack', 'pagerduty'],
    },
  },
};

/**
 * Access & Audit Manager for Restore Operations
 *
 * Manages dual-control approval workflow, audit logging, and alert notifications
 * for restore operations.
 */
export class AccessAuditManager {
  private state: AccessAuditState;

  /**
   *
   */
  constructor(config: Partial<AccessAuditConfig> = {}) {
    this.state = {
      approvalRequests: new Map(),
      auditLog: [],
      alerts: new Map(),
      config: {
        ...DEFAULT_CONFIG,
        ...config,
        alertConfig: {
          ...DEFAULT_CONFIG.alertConfig,
          ...config.alertConfig,
        },
      },
    };
  }

  // ==================== Approval Workflow ====================

  /**
   * Request approval for restore operation
   */
  requestApproval(
    snapshotId: string,
    environment: 'staging' | 'production',
    requestedBy: string,
    reason: string,
    metadata?: Record<string, unknown>
  ): ApprovalRequest {
    const id = `approval-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
    const now = Date.now();

    const request: ApprovalRequest = {
      id,
      snapshotId,
      environment,
      requestedBy,
      requestedAt: now,
      status: 'pending',
      reason,
      expiresAt: now + this.state.config.approvalExpirationMs,
      metadata,
    };

    this.state.approvalRequests.set(id, request);

    // Log audit entry
    if (this.state.config.enableAuditLog) {
      this.logAudit(
        'restore_request',
        requestedBy,
        'requester',
        snapshotId,
        environment,
        id,
        reason,
        true,
        metadata
      );
    }

    // Send alert
    if (this.state.config.alertConfig.enabled) {
      this.sendAlert(
        'info',
        'Restore Approval Requested',
        `User ${requestedBy} requested restore approval for snapshot ${snapshotId} in ${environment}. Reason: ${reason}`,
        'restore_request',
        requestedBy,
        snapshotId,
        environment
      );
    }

    return request;
  }

  /**
   * Grant approval for restore request
   */
  grantApproval(
    requestId: string,
    reviewedBy: string,
    reviewerRole: UserRole,
    comment?: string
  ): ApprovalRequest {
    const request = this.state.approvalRequests.get(requestId);
    if (!request) {
      throw new Error(`Approval request not found: ${requestId}`);
    }

    // Check if reviewer has permission
    if (!this.state.config.approverRoles.includes(reviewerRole)) {
      throw new Error(
        `User role '${reviewerRole}' not authorized to approve restores`
      );
    }

    // Check if request is still pending
    if (request.status !== 'pending') {
      throw new Error(
        `Cannot grant approval for request with status: ${request.status}`
      );
    }

    // Check if requester and reviewer are different
    if (request.requestedBy === reviewedBy) {
      throw new Error('Requester cannot approve their own request (dual control)');
    }

    // Check expiration
    const now = Date.now();
    if (request.expiresAt && now > request.expiresAt) {
      request.status = 'expired';
      this.state.approvalRequests.set(requestId, request);
      throw new Error('Approval request has expired');
    }

    // Grant approval
    request.status = 'granted';
    request.reviewedBy = reviewedBy;
    request.reviewedAt = now;
    request.reviewComment = comment;
    this.state.approvalRequests.set(requestId, request);

    // Log audit entry
    if (this.state.config.enableAuditLog) {
      this.logAudit(
        'restore_approve',
        reviewedBy,
        reviewerRole,
        request.snapshotId,
        request.environment,
        requestId,
        comment,
        true
      );
    }

    // Send alert
    if (this.state.config.alertConfig.enabled) {
      this.sendAlert(
        'warning',
        'Restore Approval Granted',
        `User ${reviewedBy} granted restore approval for snapshot ${request.snapshotId} in ${request.environment}. Comment: ${comment || 'None'}`,
        'restore_approve',
        reviewedBy,
        request.snapshotId,
        request.environment
      );
    }

    return request;
  }

  /**
   * Deny approval for restore request
   */
  denyApproval(
    requestId: string,
    reviewedBy: string,
    reviewerRole: UserRole,
    comment?: string
  ): ApprovalRequest {
    const request = this.state.approvalRequests.get(requestId);
    if (!request) {
      throw new Error(`Approval request not found: ${requestId}`);
    }

    // Check if reviewer has permission
    if (!this.state.config.approverRoles.includes(reviewerRole)) {
      throw new Error(
        `User role '${reviewerRole}' not authorized to deny restores`
      );
    }

    // Check if request is still pending
    if (request.status !== 'pending') {
      throw new Error(`Cannot deny approval for request with status: ${request.status}`);
    }

    // Deny approval
    const now = Date.now();
    request.status = 'denied';
    request.reviewedBy = reviewedBy;
    request.reviewedAt = now;
    request.reviewComment = comment;
    this.state.approvalRequests.set(requestId, request);

    // Log audit entry
    if (this.state.config.enableAuditLog) {
      this.logAudit(
        'restore_deny',
        reviewedBy,
        reviewerRole,
        request.snapshotId,
        request.environment,
        requestId,
        comment,
        true
      );
    }

    // Send alert
    if (this.state.config.alertConfig.enabled) {
      this.sendAlert(
        'info',
        'Restore Approval Denied',
        `User ${reviewedBy} denied restore approval for snapshot ${request.snapshotId} in ${request.environment}. Comment: ${comment || 'None'}`,
        'restore_deny',
        reviewedBy,
        request.snapshotId,
        request.environment
      );
    }

    return request;
  }

  /**
   * Get approval request by ID
   */
  getApprovalRequest(requestId: string): ApprovalRequest | undefined {
    return this.state.approvalRequests.get(requestId);
  }

  /**
   * Get all approval requests
   */
  getApprovalRequests(): ApprovalRequest[] {
    return Array.from(this.state.approvalRequests.values());
  }

  /**
   * Get approval requests by status
   */
  getApprovalRequestsByStatus(status: ApprovalStatus): ApprovalRequest[] {
    return Array.from(this.state.approvalRequests.values()).filter(
      (req) => req.status === status
    );
  }

  /**
   * Get approval requests by user
   */
  getApprovalRequestsByUser(userId: string): ApprovalRequest[] {
    return Array.from(this.state.approvalRequests.values()).filter(
      (req) => req.requestedBy === userId || req.reviewedBy === userId
    );
  }

  /**
   * Check if approval is required and granted
   */
  isApprovalGranted(snapshotId: string, environment: string): boolean {
    if (!this.state.config.requireApproval) {
      return true;
    }

    // Find granted approval for this snapshot/environment
    return Array.from(this.state.approvalRequests.values()).some(
      (req) =>
        req.snapshotId === snapshotId &&
        req.environment === environment &&
        req.status === 'granted'
    );
  }

  /**
   * Clear expired approval requests
   */
  clearExpiredApprovals(): number {
    const now = Date.now();
    let count = 0;

    for (const [id, request] of this.state.approvalRequests.entries()) {
      if (
        request.status === 'pending' &&
        request.expiresAt &&
        now > request.expiresAt
      ) {
        request.status = 'expired';
        this.state.approvalRequests.set(id, request);
        count++;
      }
    }

    return count;
  }

  // ==================== Audit Logging ====================

  /**
   * Log audit entry
   */
  logAudit(
    action: AuditAction,
    actor: string,
    actorRole: UserRole,
    snapshotId?: string,
    environment?: 'staging' | 'production',
    approvalRequestId?: string,
    reason?: string,
    success?: boolean,
    metadata?: Record<string, unknown>,
    error?: string
  ): AuditLogEntry {
    if (!this.state.config.enableAuditLog) {
      throw new Error('Audit logging is disabled');
    }

    const id = `audit-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
    const entry: AuditLogEntry = {
      id,
      action,
      actor,
      actorRole,
      timestamp: Date.now(),
      snapshotId,
      environment,
      approvalRequestId,
      reason,
      success,
      error,
      metadata,
    };

    this.state.auditLog.push(entry);

    // Enforce max entries
    if (this.state.auditLog.length > this.state.config.maxAuditLogEntries) {
      this.state.auditLog.shift();
    }

    return entry;
  }

  /**
   * Log restore start
   */
  logRestoreStart(
    snapshotId: string,
    environment: 'staging' | 'production',
    actor: string,
    actorRole: UserRole,
    approvalRequestId?: string,
    reason?: string
  ): AuditLogEntry {
    return this.logAudit(
      'restore_start',
      actor,
      actorRole,
      snapshotId,
      environment,
      approvalRequestId,
      reason,
      true
    );
  }

  /**
   * Log restore completion
   */
  logRestoreComplete(
    snapshotId: string,
    environment: 'staging' | 'production',
    actor: string,
    actorRole: UserRole,
    approvalRequestId?: string
  ): AuditLogEntry {
    const entry = this.logAudit(
      'restore_complete',
      actor,
      actorRole,
      snapshotId,
      environment,
      approvalRequestId,
      undefined,
      true
    );

    // Send alert
    if (this.state.config.alertConfig.enabled) {
      this.sendAlert(
        'warning',
        'Restore Completed',
        `User ${actor} completed restore of snapshot ${snapshotId} in ${environment}`,
        'restore_complete',
        actor,
        snapshotId,
        environment
      );
    }

    return entry;
  }

  /**
   * Log restore failure
   */
  logRestoreFail(
    snapshotId: string,
    environment: 'staging' | 'production',
    actor: string,
    actorRole: UserRole,
    error: string,
    approvalRequestId?: string
  ): AuditLogEntry {
    const entry = this.logAudit(
      'restore_fail',
      actor,
      actorRole,
      snapshotId,
      environment,
      approvalRequestId,
      undefined,
      false,
      undefined,
      error
    );

    // Send alert
    if (this.state.config.alertConfig.enabled) {
      this.sendAlert(
        'critical',
        'Restore Failed',
        `User ${actor} restore of snapshot ${snapshotId} in ${environment} failed: ${error}`,
        'restore_fail',
        actor,
        snapshotId,
        environment
      );
    }

    return entry;
  }

  /**
   * Get audit log entries
   */
  getAuditLog(): AuditLogEntry[] {
    return [...this.state.auditLog];
  }

  /**
   * Get audit log entries by action
   */
  getAuditLogByAction(action: AuditAction): AuditLogEntry[] {
    return this.state.auditLog.filter((entry) => entry.action === action);
  }

  /**
   * Get audit log entries by actor
   */
  getAuditLogByActor(actor: string): AuditLogEntry[] {
    return this.state.auditLog.filter((entry) => entry.actor === actor);
  }

  /**
   * Get audit log entries by snapshot
   */
  getAuditLogBySnapshot(snapshotId: string): AuditLogEntry[] {
    return this.state.auditLog.filter((entry) => entry.snapshotId === snapshotId);
  }

  /**
   * Get audit log entries in time range
   */
  getAuditLogByTimeRange(startTime: number, endTime: number): AuditLogEntry[] {
    return this.state.auditLog.filter(
      (entry) => entry.timestamp >= startTime && entry.timestamp <= endTime
    );
  }

  /**
   * Clear audit log
   */
  clearAuditLog(): number {
    const count = this.state.auditLog.length;
    this.state.auditLog = [];
    return count;
  }

  // ==================== Alert Management ====================

  /**
   * Send alert notification
   */
  sendAlert(
    priority: AlertPriority,
    title: string,
    message: string,
    action: AuditAction,
    actor: string,
    snapshotId?: string,
    environment?: 'staging' | 'production',
    metadata?: Record<string, unknown>
  ): Alert {
    if (!this.state.config.alertConfig.enabled) {
      throw new Error('Alerts are disabled');
    }

    const id = `alert-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;

    // Determine channels
    const channels =
      this.state.config.alertConfig.channelsByPriority?.[priority] ||
      this.state.config.alertConfig.defaultChannels ||
      [];

    const alert: Alert = {
      id,
      priority,
      title,
      message,
      action,
      actor,
      timestamp: Date.now(),
      channels,
      deliveryStatus: {} as Record<AlertChannel, 'pending' | 'sent' | 'failed'>,
      snapshotId,
      environment,
      metadata,
    };

    // Initialize delivery status
    for (const channel of channels) {
      alert.deliveryStatus[channel] = 'pending';
    }

    this.state.alerts.set(id, alert);

    // Trigger delivery (async)
    this.deliverAlert(alert);

    return alert;
  }

  /**
   * Deliver alert to channels
   */
  private async deliverAlert(alert: Alert): Promise<void> {
    const handler = this.state.config.alertConfig.deliveryHandler;

    for (const channel of alert.channels) {
      try {
        if (handler) {
          const success = await handler(alert, channel);
          alert.deliveryStatus[channel] = success ? 'sent' : 'failed';
        } else {
          // Default: mark as sent (no actual delivery)
          alert.deliveryStatus[channel] = 'sent';
        }
      } catch (error) {
        alert.deliveryStatus[channel] = 'failed';
      }

      this.state.alerts.set(alert.id, alert);
    }

    // Log alert delivery
    if (this.state.config.enableAuditLog) {
      this.logAudit(
        'alert_sent',
        alert.actor,
        'requester', // Role not tracked for alerts
        alert.snapshotId,
        alert.environment,
        undefined,
        `Alert: ${alert.title}`,
        true,
        {
          alertId: alert.id,
          priority: alert.priority,
          channels: alert.channels,
          deliveryStatus: alert.deliveryStatus,
        }
      );
    }
  }

  /**
   * Get alert by ID
   */
  getAlert(alertId: string): Alert | undefined {
    return this.state.alerts.get(alertId);
  }

  /**
   * Get all alerts
   */
  getAlerts(): Alert[] {
    return Array.from(this.state.alerts.values());
  }

  /**
   * Get alerts by priority
   */
  getAlertsByPriority(priority: AlertPriority): Alert[] {
    return Array.from(this.state.alerts.values()).filter(
      (alert) => alert.priority === priority
    );
  }

  /**
   * Get alerts by actor
   */
  getAlertsByActor(actor: string): Alert[] {
    return Array.from(this.state.alerts.values()).filter(
      (alert) => alert.actor === actor
    );
  }

  /**
   * Clear all alerts
   */
  clearAlerts(): number {
    const count = this.state.alerts.size;
    this.state.alerts.clear();
    return count;
  }

  // ==================== Configuration ====================

  /**
   * Get current configuration
   */
  getConfig(): AccessAuditConfig {
    return { ...this.state.config };
  }

  /**
   * Update configuration
   */
  updateConfig(updates: Partial<AccessAuditConfig>): void {
    this.state.config = {
      ...this.state.config,
      ...updates,
      alertConfig: {
        ...this.state.config.alertConfig,
        ...updates.alertConfig,
      },
    };
  }
}
