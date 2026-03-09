import { describe, it, expect, beforeEach, vi } from 'vitest';

import {
  AccessAuditManager,
  type ApprovalRequest,
  type AuditLogEntry,
  type Alert,
  type AlertChannel,
} from '../accessAudit';

describe('AccessAuditManager', () => {
  let manager: AccessAuditManager;

  beforeEach(() => {
    vi.useFakeTimers();
    manager = new AccessAuditManager();
  });

  describe('Initialization', () => {
    it('should initialize with default configuration', () => {
      const config = manager.getConfig();

      expect(config.requireApproval).toBe(true);
      expect(config.approvalExpirationMs).toBe(24 * 60 * 60 * 1000); // 24 hours
      expect(config.approverRoles).toEqual(['approver', 'admin']);
      expect(config.enableAuditLog).toBe(true);
      expect(config.maxAuditLogEntries).toBe(10000);
      expect(config.alertConfig.enabled).toBe(true);
      expect(config.alertConfig.defaultChannels).toEqual(['email']);
    });

    it('should initialize with custom configuration', () => {
      const customManager = new AccessAuditManager({
        requireApproval: false,
        approvalExpirationMs: 1000,
        approverRoles: ['admin'],
        enableAuditLog: false,
        maxAuditLogEntries: 100,
        alertConfig: {
          enabled: false,
          defaultChannels: ['slack'],
        },
      });

      const config = customManager.getConfig();
      expect(config.requireApproval).toBe(false);
      expect(config.approvalExpirationMs).toBe(1000);
      expect(config.approverRoles).toEqual(['admin']);
      expect(config.enableAuditLog).toBe(false);
      expect(config.maxAuditLogEntries).toBe(100);
      expect(config.alertConfig.enabled).toBe(false);
      expect(config.alertConfig.defaultChannels).toEqual(['slack']);
    });

    it('should start with no approval requests', () => {
      const requests = manager.getApprovalRequests();
      expect(requests).toHaveLength(0);
    });

    it('should start with no audit log entries', () => {
      const log = manager.getAuditLog();
      expect(log).toHaveLength(0);
    });

    it('should start with no alerts', () => {
      const alerts = manager.getAlerts();
      expect(alerts).toHaveLength(0);
    });
  });

  describe('Approval Workflow', () => {
    it('should request approval for restore', async () => {
      const now = Date.now();
      vi.setSystemTime(now);

      const request = manager.requestApproval(
        'snapshot-1',
        'production',
        'user-1',
        'Emergency fix'
      );

      expect(request.id).toBeDefined();
      expect(request.snapshotId).toBe('snapshot-1');
      expect(request.environment).toBe('production');
      expect(request.requestedBy).toBe('user-1');
      expect(request.requestedAt).toBe(now);
      expect(request.status).toBe('pending');
      expect(request.reason).toBe('Emergency fix');
      expect(request.expiresAt).toBe(now + 24 * 60 * 60 * 1000);

      // Wait for async alert delivery
      await vi.waitFor(() => manager.getAuditLog().length >= 2);

      // Verify audit log (includes request + alert_sent)
      const log = manager.getAuditLog();
      expect(log.length).toBeGreaterThanOrEqual(2);
      expect(log[0].action).toBe('restore_request');
      expect(log[0].actor).toBe('user-1');

      // Verify alert
      const alerts = manager.getAlerts();
      expect(alerts).toHaveLength(1);
      expect(alerts[0].action).toBe('restore_request');
      expect(alerts[0].priority).toBe('info');
    });

    it('should request approval with metadata', () => {
      const request = manager.requestApproval(
        'snapshot-1',
        'staging',
        'user-1',
        'Testing',
        { ticket: 'JIRA-123' }
      );

      expect(request.metadata).toEqual({ ticket: 'JIRA-123' });
    });

    it('should grant approval with valid approver', () => {
      const request = manager.requestApproval(
        'snapshot-1',
        'production',
        'user-1',
        'Emergency fix'
      );

      const now = Date.now();
      vi.setSystemTime(now);

      const granted = manager.grantApproval(
        request.id,
        'approver-1',
        'approver',
        'Approved for production'
      );

      expect(granted.status).toBe('granted');
      expect(granted.reviewedBy).toBe('approver-1');
      expect(granted.reviewedAt).toBe(now);
      expect(granted.reviewComment).toBe('Approved for production');

      // Verify audit log
      const log = manager.getAuditLogByAction('restore_approve');
      expect(log).toHaveLength(1);
      expect(log[0].actor).toBe('approver-1');

      // Verify alert
      const alerts = manager.getAlertsByPriority('warning');
      expect(alerts).toHaveLength(1);
      expect(alerts[0].action).toBe('restore_approve');
    });

    it('should reject approval from unauthorized role', () => {
      const request = manager.requestApproval(
        'snapshot-1',
        'production',
        'user-1',
        'Test'
      );

      expect(() => {
        manager.grantApproval(request.id, 'user-2', 'requester', 'Comment');
      }).toThrow("User role 'requester' not authorized to approve restores");
    });

    it('should reject approval for non-pending request', () => {
      const request = manager.requestApproval(
        'snapshot-1',
        'production',
        'user-1',
        'Test'
      );

      manager.grantApproval(request.id, 'approver-1', 'approver');

      expect(() => {
        manager.grantApproval(request.id, 'approver-2', 'approver');
      }).toThrow('Cannot grant approval for request with status: granted');
    });

    it('should reject self-approval (dual control)', () => {
      const request = manager.requestApproval(
        'snapshot-1',
        'production',
        'user-1',
        'Test'
      );

      expect(() => {
        manager.grantApproval(request.id, 'user-1', 'admin');
      }).toThrow('Requester cannot approve their own request (dual control)');
    });

    it('should reject approval for expired request', () => {
      const now = Date.now();
      vi.setSystemTime(now);

      const request = manager.requestApproval(
        'snapshot-1',
        'production',
        'user-1',
        'Test'
      );

      // Advance time past expiration
      vi.setSystemTime(now + 25 * 60 * 60 * 1000);

      expect(() => {
        manager.grantApproval(request.id, 'approver-1', 'approver');
      }).toThrow('Approval request has expired');

      const updated = manager.getApprovalRequest(request.id);
      expect(updated?.status).toBe('expired');
    });

    it('should deny approval with valid approver', () => {
      const request = manager.requestApproval(
        'snapshot-1',
        'production',
        'user-1',
        'Test'
      );

      const now = Date.now();
      vi.setSystemTime(now);

      const denied = manager.denyApproval(
        request.id,
        'approver-1',
        'approver',
        'Not authorized'
      );

      expect(denied.status).toBe('denied');
      expect(denied.reviewedBy).toBe('approver-1');
      expect(denied.reviewedAt).toBe(now);
      expect(denied.reviewComment).toBe('Not authorized');

      // Verify audit log
      const log = manager.getAuditLogByAction('restore_deny');
      expect(log).toHaveLength(1);

      // Verify alert
      const alerts = manager.getAlertsByPriority('info');
      expect(alerts.length).toBeGreaterThanOrEqual(1);
      const denyAlert = alerts.find((a) => a.action === 'restore_deny');
      expect(denyAlert).toBeDefined();
    });

    it('should reject denial from unauthorized role', () => {
      const request = manager.requestApproval(
        'snapshot-1',
        'production',
        'user-1',
        'Test'
      );

      expect(() => {
        manager.denyApproval(request.id, 'user-2', 'requester');
      }).toThrow("User role 'requester' not authorized to deny restores");
    });

    it('should reject denial for non-pending request', () => {
      const request = manager.requestApproval(
        'snapshot-1',
        'production',
        'user-1',
        'Test'
      );

      manager.grantApproval(request.id, 'approver-1', 'approver');

      expect(() => {
        manager.denyApproval(request.id, 'approver-2', 'approver');
      }).toThrow('Cannot deny approval for request with status: granted');
    });

    it('should get approval request by ID', () => {
      const request = manager.requestApproval(
        'snapshot-1',
        'production',
        'user-1',
        'Test'
      );

      const retrieved = manager.getApprovalRequest(request.id);
      expect(retrieved).toEqual(request);
    });

    it('should return undefined for non-existent approval request', () => {
      const retrieved = manager.getApprovalRequest('non-existent');
      expect(retrieved).toBeUndefined();
    });

    it('should get approval requests by status', () => {
      manager.requestApproval('snapshot-1', 'production', 'user-1', 'Test 1');
      const req2 = manager.requestApproval(
        'snapshot-2',
        'staging',
        'user-2',
        'Test 2'
      );
      const req3 = manager.requestApproval(
        'snapshot-3',
        'production',
        'user-3',
        'Test 3'
      );

      manager.grantApproval(req2.id, 'approver-1', 'approver');
      manager.denyApproval(req3.id, 'approver-1', 'approver');

      const pending = manager.getApprovalRequestsByStatus('pending');
      expect(pending).toHaveLength(1);
      expect(pending[0].snapshotId).toBe('snapshot-1');

      const granted = manager.getApprovalRequestsByStatus('granted');
      expect(granted).toHaveLength(1);
      expect(granted[0].snapshotId).toBe('snapshot-2');

      const denied = manager.getApprovalRequestsByStatus('denied');
      expect(denied).toHaveLength(1);
      expect(denied[0].snapshotId).toBe('snapshot-3');
    });

    it('should get approval requests by user', () => {
      manager.requestApproval('snapshot-1', 'production', 'user-1', 'Test 1');
      const req2 = manager.requestApproval(
        'snapshot-2',
        'staging',
        'user-2',
        'Test 2'
      );
      manager.requestApproval('snapshot-3', 'production', 'user-1', 'Test 3');

      manager.grantApproval(req2.id, 'approver-1', 'approver');

      const user1Requests = manager.getApprovalRequestsByUser('user-1');
      expect(user1Requests).toHaveLength(2);

      const approverRequests = manager.getApprovalRequestsByUser('approver-1');
      expect(approverRequests).toHaveLength(1);
      expect(approverRequests[0].reviewedBy).toBe('approver-1');
    });

    it('should check if approval is granted', () => {
      const request = manager.requestApproval(
        'snapshot-1',
        'production',
        'user-1',
        'Test'
      );

      expect(manager.isApprovalGranted('snapshot-1', 'production')).toBe(false);

      manager.grantApproval(request.id, 'approver-1', 'approver');

      expect(manager.isApprovalGranted('snapshot-1', 'production')).toBe(true);
    });

    it('should return true if approval not required', () => {
      const noApprovalManager = new AccessAuditManager({
        requireApproval: false,
      });

      expect(noApprovalManager.isApprovalGranted('snapshot-1', 'production')).toBe(
        true
      );
    });

    it('should clear expired approval requests', () => {
      const now = Date.now();
      vi.setSystemTime(now);

      manager.requestApproval('snapshot-1', 'production', 'user-1', 'Test 1');
      manager.requestApproval('snapshot-2', 'staging', 'user-2', 'Test 2');
      const req3 = manager.requestApproval(
        'snapshot-3',
        'production',
        'user-3',
        'Test 3'
      );

      // Grant one to keep it
      manager.grantApproval(req3.id, 'approver-1', 'approver');

      // Advance time past expiration
      vi.setSystemTime(now + 25 * 60 * 60 * 1000);

      const count = manager.clearExpiredApprovals();
      expect(count).toBe(2);

      const requests = manager.getApprovalRequests();
      const expired = requests.filter((r) => r.status === 'expired');
      expect(expired).toHaveLength(2);

      const granted = requests.filter((r) => r.status === 'granted');
      expect(granted).toHaveLength(1);
    });
  });

  describe('Audit Logging', () => {
    it('should log audit entry', () => {
      const now = Date.now();
      vi.setSystemTime(now);

      const entry = manager.logAudit(
        'restore_start',
        'user-1',
        'requester',
        'snapshot-1',
        'production',
        'approval-1',
        'Emergency fix',
        true,
        { ticket: 'JIRA-123' }
      );

      expect(entry.id).toBeDefined();
      expect(entry.action).toBe('restore_start');
      expect(entry.actor).toBe('user-1');
      expect(entry.actorRole).toBe('requester');
      expect(entry.timestamp).toBe(now);
      expect(entry.snapshotId).toBe('snapshot-1');
      expect(entry.environment).toBe('production');
      expect(entry.approvalRequestId).toBe('approval-1');
      expect(entry.reason).toBe('Emergency fix');
      expect(entry.success).toBe(true);
      expect(entry.metadata).toEqual({ ticket: 'JIRA-123' });

      const log = manager.getAuditLog();
      expect(log).toHaveLength(1);
      expect(log[0]).toEqual(entry);
    });

    it('should throw error when audit logging is disabled', () => {
      const noAuditManager = new AccessAuditManager({
        enableAuditLog: false,
      });

      expect(() => {
        noAuditManager.logAudit('restore_start', 'user-1', 'requester');
      }).toThrow('Audit logging is disabled');
    });

    it('should log restore start', () => {
      const entry = manager.logRestoreStart(
        'snapshot-1',
        'production',
        'user-1',
        'requester',
        'approval-1',
        'Emergency fix'
      );

      expect(entry.action).toBe('restore_start');
      expect(entry.success).toBe(true);
    });

    it('should log restore complete with alert', () => {
      const entry = manager.logRestoreComplete(
        'snapshot-1',
        'production',
        'user-1',
        'requester',
        'approval-1'
      );

      expect(entry.action).toBe('restore_complete');
      expect(entry.success).toBe(true);

      // Verify alert
      const alerts = manager.getAlerts();
      const completeAlert = alerts.find((a) => a.action === 'restore_complete');
      expect(completeAlert).toBeDefined();
      expect(completeAlert?.priority).toBe('warning');
    });

    it('should log restore fail with alert', () => {
      const entry = manager.logRestoreFail(
        'snapshot-1',
        'production',
        'user-1',
        'requester',
        'Database connection failed',
        'approval-1'
      );

      expect(entry.action).toBe('restore_fail');
      expect(entry.success).toBe(false);
      expect(entry.error).toBe('Database connection failed');

      // Verify alert
      const alerts = manager.getAlertsByPriority('critical');
      expect(alerts).toHaveLength(1);
      expect(alerts[0].action).toBe('restore_fail');
    });

    it('should get audit log by action', () => {
      manager.logRestoreStart('snapshot-1', 'production', 'user-1', 'requester');
      manager.logRestoreComplete('snapshot-1', 'production', 'user-1', 'requester');
      manager.logRestoreStart('snapshot-2', 'staging', 'user-2', 'requester');

      const starts = manager.getAuditLogByAction('restore_start');
      expect(starts).toHaveLength(2);

      const completes = manager.getAuditLogByAction('restore_complete');
      expect(completes).toHaveLength(1);
    });

    it('should get audit log by actor', () => {
      manager.logRestoreStart('snapshot-1', 'production', 'user-1', 'requester');
      manager.logAudit('restore_complete', 'user-1', 'requester', 'snapshot-1', 'production');
      manager.logRestoreStart('snapshot-2', 'staging', 'user-2', 'requester');

      const user1Log = manager.getAuditLogByActor('user-1');
      expect(user1Log).toHaveLength(2);

      const user2Log = manager.getAuditLogByActor('user-2');
      expect(user2Log).toHaveLength(1);
    });

    it('should get audit log by snapshot', () => {
      manager.logRestoreStart('snapshot-1', 'production', 'user-1', 'requester');
      manager.logAudit('restore_complete', 'user-1', 'requester', 'snapshot-1', 'production');
      manager.logRestoreStart('snapshot-2', 'staging', 'user-2', 'requester');

      const snapshot1Log = manager.getAuditLogBySnapshot('snapshot-1');
      expect(snapshot1Log).toHaveLength(2);

      const snapshot2Log = manager.getAuditLogBySnapshot('snapshot-2');
      expect(snapshot2Log).toHaveLength(1);
    });

    it('should get audit log by time range', () => {
      const now = Date.now();
      vi.setSystemTime(now);

      manager.logRestoreStart('snapshot-1', 'production', 'user-1', 'requester');

      vi.setSystemTime(now + 1000);
      manager.logAudit('restore_complete', 'user-1', 'requester', 'snapshot-1', 'production');

      vi.setSystemTime(now + 2000);
      manager.logRestoreStart('snapshot-2', 'staging', 'user-2', 'requester');

      const rangeLog = manager.getAuditLogByTimeRange(now, now + 1500);
      expect(rangeLog).toHaveLength(2);

      const allLog = manager.getAuditLogByTimeRange(now, now + 3000);
      expect(allLog).toHaveLength(3);
    });

    it('should clear audit log', () => {
      manager.logRestoreStart('snapshot-1', 'production', 'user-1', 'requester');
      manager.logAudit('restore_complete', 'user-1', 'requester', 'snapshot-1', 'production');

      const count = manager.clearAuditLog();
      expect(count).toBe(2);

      const log = manager.getAuditLog();
      expect(log).toHaveLength(0);
    });

    it('should enforce max audit log entries', () => {
      const smallManager = new AccessAuditManager({
        maxAuditLogEntries: 3,
      });

      smallManager.logAudit('restore_start', 'user-1', 'requester', 'snapshot-1', 'production');
      smallManager.logAudit('restore_complete', 'user-1', 'requester', 'snapshot-1', 'production');
      smallManager.logAudit('restore_start', 'user-2', 'requester', 'snapshot-2', 'staging');
      smallManager.logAudit('restore_complete', 'user-2', 'requester', 'snapshot-2', 'staging');

      const log = smallManager.getAuditLog();
      expect(log).toHaveLength(3);

      // First entry should be removed
      expect(log[0].action).toBe('restore_complete');
      expect(log[0].snapshotId).toBe('snapshot-1');
    });
  });

  describe('Alert Management', () => {
    it('should send alert notification', () => {
      const now = Date.now();
      vi.setSystemTime(now);

      const alert = manager.sendAlert(
        'warning',
        'Test Alert',
        'Test message',
        'restore_start',
        'user-1',
        'snapshot-1',
        'production',
        { test: true }
      );

      expect(alert.id).toBeDefined();
      expect(alert.priority).toBe('warning');
      expect(alert.title).toBe('Test Alert');
      expect(alert.message).toBe('Test message');
      expect(alert.action).toBe('restore_start');
      expect(alert.actor).toBe('user-1');
      expect(alert.timestamp).toBe(now);
      expect(alert.snapshotId).toBe('snapshot-1');
      expect(alert.environment).toBe('production');
      expect(alert.metadata).toEqual({ test: true });
      expect(alert.channels).toEqual(['email', 'slack']);

      const alerts = manager.getAlerts();
      expect(alerts).toHaveLength(1);
    });

    it('should throw error when alerts are disabled', () => {
      const noAlertsManager = new AccessAuditManager({
        alertConfig: {
          enabled: false,
          defaultChannels: ['email'],
        },
      });

      expect(() => {
        noAlertsManager.sendAlert(
          'info',
          'Test',
          'Test',
          'restore_start',
          'user-1'
        );
      }).toThrow('Alerts are disabled');
    });

    it('should use priority-specific channels', () => {
      const alert = manager.sendAlert(
        'critical',
        'Critical Alert',
        'Critical message',
        'restore_fail',
        'user-1'
      );

      expect(alert.channels).toEqual(['email', 'slack', 'pagerduty']);
    });

    it('should use default channels when priority channels not configured', () => {
      const customManager = new AccessAuditManager({
        alertConfig: {
          enabled: true,
          defaultChannels: ['slack'],
          channelsByPriority: undefined, // Clear default priority channels
        },
      });

      const alert = customManager.sendAlert(
        'warning',
        'Test',
        'Test',
        'restore_start',
        'user-1'
      );

      expect(alert.channels).toEqual(['slack']);
    });

    it('should handle custom delivery handler', async () => {
      // Use real timers for this test to allow async operations
      vi.useRealTimers();
      
      const deliveryHandler = vi.fn().mockResolvedValue(true);

      const customManager = new AccessAuditManager({
        alertConfig: {
          enabled: true,
          defaultChannels: ['email'],
          deliveryHandler,
        },
      });

      const alert = customManager.sendAlert(
        'info',
        'Test',
        'Test',
        'restore_start',
        'user-1'
      );

      // Wait for async delivery
      await vi.waitFor(() => {
        const updated = customManager.getAlert(alert.id);
        return updated?.deliveryStatus.email === 'sent';
      });

      expect(deliveryHandler).toHaveBeenCalled();
      expect(deliveryHandler).toHaveBeenCalledWith(
        expect.objectContaining({ id: alert.id }),
        'email'
      );

      const updated = customManager.getAlert(alert.id);
      expect(updated?.deliveryStatus.email).toBe('sent');
      
      // Restore fake timers for subsequent tests
      vi.useFakeTimers();
    });

    it('should handle delivery handler failure', async () => {
      const deliveryHandler = vi.fn().mockRejectedValue(new Error('Delivery failed'));

      const customManager = new AccessAuditManager({
        alertConfig: {
          enabled: true,
          defaultChannels: ['email'],
          deliveryHandler,
        },
      });

      const alert = customManager.sendAlert(
        'info',
        'Test',
        'Test',
        'restore_start',
        'user-1'
      );

      // Wait for async delivery
      await vi.waitFor(() => {
        const updated = customManager.getAlert(alert.id);
        return updated?.deliveryStatus.email === 'failed';
      });

      const updated = customManager.getAlert(alert.id);
      expect(updated?.deliveryStatus.email).toBe('failed');
    });

    it('should get alert by ID', () => {
      const alert = manager.sendAlert(
        'info',
        'Test',
        'Test',
        'restore_start',
        'user-1'
      );

      const retrieved = manager.getAlert(alert.id);
      expect(retrieved).toEqual(alert);
    });

    it('should return undefined for non-existent alert', () => {
      const retrieved = manager.getAlert('non-existent');
      expect(retrieved).toBeUndefined();
    });

    it('should get alerts by priority', () => {
      manager.sendAlert('info', 'Info Alert', 'Info', 'restore_start', 'user-1');
      manager.sendAlert(
        'warning',
        'Warning Alert',
        'Warning',
        'restore_approve',
        'user-1'
      );
      manager.sendAlert(
        'critical',
        'Critical Alert',
        'Critical',
        'restore_fail',
        'user-1'
      );

      const infoAlerts = manager.getAlertsByPriority('info');
      expect(infoAlerts).toHaveLength(1);
      expect(infoAlerts[0].title).toBe('Info Alert');

      const warningAlerts = manager.getAlertsByPriority('warning');
      expect(warningAlerts).toHaveLength(1);

      const criticalAlerts = manager.getAlertsByPriority('critical');
      expect(criticalAlerts).toHaveLength(1);
    });

    it('should get alerts by actor', () => {
      manager.sendAlert('info', 'Test 1', 'Test', 'restore_start', 'user-1');
      manager.sendAlert('info', 'Test 2', 'Test', 'restore_start', 'user-2');
      manager.sendAlert('info', 'Test 3', 'Test', 'restore_start', 'user-1');

      const user1Alerts = manager.getAlertsByActor('user-1');
      expect(user1Alerts).toHaveLength(2);

      const user2Alerts = manager.getAlertsByActor('user-2');
      expect(user2Alerts).toHaveLength(1);
    });

    it('should clear all alerts', () => {
      manager.sendAlert('info', 'Test 1', 'Test', 'restore_start', 'user-1');
      manager.sendAlert('warning', 'Test 2', 'Test', 'restore_approve', 'user-2');

      const count = manager.clearAlerts();
      expect(count).toBe(2);

      const alerts = manager.getAlerts();
      expect(alerts).toHaveLength(0);
    });
  });

  describe('Configuration', () => {
    it('should get configuration', () => {
      const config = manager.getConfig();

      expect(config).toHaveProperty('requireApproval');
      expect(config).toHaveProperty('approvalExpirationMs');
      expect(config).toHaveProperty('approverRoles');
      expect(config).toHaveProperty('enableAuditLog');
      expect(config).toHaveProperty('maxAuditLogEntries');
      expect(config).toHaveProperty('alertConfig');
    });

    it('should update configuration', () => {
      manager.updateConfig({
        requireApproval: false,
        approvalExpirationMs: 1000,
      });

      const config = manager.getConfig();
      expect(config.requireApproval).toBe(false);
      expect(config.approvalExpirationMs).toBe(1000);
    });

    it('should merge alert configuration updates', () => {
      manager.updateConfig({
        alertConfig: {
          enabled: false,
          defaultChannels: ['slack'],
        },
      });

      const config = manager.getConfig();
      expect(config.alertConfig.enabled).toBe(false);
      expect(config.alertConfig.defaultChannels).toEqual(['slack']);
    });
  });
});
