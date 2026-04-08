/**
 * Tests for audit-log service
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { auditLogService, useAuditLog, type AuditEvent } from '../audit-log';

// Mock fetch
global.fetch = vi.fn();

describe('auditLogService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('logs audit event successfully', async () => {
    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
    });

    const event = {
      userId: 'user-1',
      tenantId: 'tenant-1',
      action: 'hitl_approve' as const,
      resource: 'review-123',
      status: 'success' as const,
    };

    await auditLogService.log(event);

    expect(global.fetch).toHaveBeenCalledWith(
      '/api/v1/audit/log',
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      })
    );
  });

  it('stores event locally when API fails', async () => {
    (global.fetch as any).mockRejectedValueOnce(new Error('Network error'));

    const event = {
      userId: 'user-1',
      tenantId: 'tenant-1',
      action: 'hitl_approve' as const,
      resource: 'review-123',
      status: 'success' as const,
    };

    await auditLogService.log(event);

    const backups = auditLogService.getLocalBackups();
    expect(backups).toHaveLength(1);
    expect(backups[0].action).toBe('hitl_approve');
  });

  it('queries audit logs successfully', async () => {
    const mockEvents: AuditEvent[] = [
      {
        id: '1',
        timestamp: new Date().toISOString(),
        userId: 'user-1',
        tenantId: 'tenant-1',
        action: 'hitl_approve',
        resource: 'review-123',
        status: 'success',
      },
    ];

    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        events: mockEvents,
        total: 1,
        hasMore: false,
      }),
    });

    const result = await auditLogService.query({ userId: 'user-1' });

    expect(result.events).toHaveLength(1);
    expect(result.total).toBe(1);
  });

  it('gets recent audit events', async () => {
    const mockEvents: AuditEvent[] = [
      {
        id: '1',
        timestamp: new Date().toISOString(),
        userId: 'user-1',
        tenantId: 'tenant-1',
        action: 'hitl_approve',
        resource: 'review-123',
        status: 'success',
      },
    ];

    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        events: mockEvents,
        total: 1,
        hasMore: false,
      }),
    });

    const recent = await auditLogService.getRecent(5);

    expect(recent).toHaveLength(1);
  });

  it('stores and retrieves local backups', () => {
    const event: AuditEvent = {
      id: '1',
      timestamp: new Date().toISOString(),
      userId: 'user-1',
      tenantId: 'tenant-1',
      action: 'hitl_approve',
      resource: 'review-123',
      status: 'success',
    };

    auditLogService.storeLocally(event);

    const backups = auditLogService.getLocalBackups();
    expect(backups).toHaveLength(1);
    expect(backups[0].id).toBe('1');
  });

  it('clears local backups', () => {
    const event: AuditEvent = {
      id: '1',
      timestamp: new Date().toISOString(),
      userId: 'user-1',
      tenantId: 'tenant-1',
      action: 'hitl_approve',
      resource: 'review-123',
      status: 'success',
    };

    auditLogService.storeLocally(event);
    expect(auditLogService.getLocalBackups()).toHaveLength(1);

    auditLogService.clearLocalBackups();
    expect(auditLogService.getLocalBackups()).toHaveLength(0);
  });

  it('limits local backups to 100 items', () => {
    // Add 101 events
    for (let i = 0; i < 101; i++) {
      const event: AuditEvent = {
        id: `event-${i}`,
        timestamp: new Date(Date.now() + i).toISOString(),
        userId: 'user-1',
        tenantId: 'tenant-1',
        action: 'hitl_approve',
        resource: `review-${i}`,
        status: 'success',
      };
      auditLogService.storeLocally(event);
    }

    const backups = auditLogService.getLocalBackups();
    expect(backups.length).toBeLessThanOrEqual(100);
  });

  it('syncs local backups to server', async () => {
    const event: AuditEvent = {
      id: '1',
      timestamp: new Date().toISOString(),
      userId: 'user-1',
      tenantId: 'tenant-1',
      action: 'hitl_approve',
      resource: 'review-123',
      status: 'success',
    };

    auditLogService.storeLocally(event);
    (global.fetch as any).mockResolvedValueOnce({ ok: true });

    await auditLogService.syncBackups();

    expect(auditLogService.getLocalBackups()).toHaveLength(0);
    expect(global.fetch).toHaveBeenCalled();
  });
});

describe('useAuditLog hook', () => {
  it('provides log function', () => {
    const { log } = useAuditLog();
    expect(typeof log).toBe('function');
  });

  it('provides query function', () => {
    const { query } = useAuditLog();
    expect(typeof query).toBe('function');
  });

  it('provides getRecent function', () => {
    const { getRecent } = useAuditLog();
    expect(typeof getRecent).toBe('function');
  });

  it('provides syncBackups function', () => {
    const { syncBackups } = useAuditLog();
    expect(typeof syncBackups).toBe('function');
  });
});
