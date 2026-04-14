/**
 * Tests for AuditLog service
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { auditLogService, useAuditLog, type AuditEvent } from '../audit-log';

// Mock fetch
describe('auditLogService', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it('logs audit event successfully', async () => {
    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({}),
    });

    const event = {
      userId: 'user-1',
      tenantId: 'tenant-1',
      action: 'voice_command' as const,
      resource: 'test-resource',
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
      action: 'voice_command' as const,
      resource: 'test-resource',
      status: 'success' as const,
    };

    await auditLogService.log(event);

    const backups = auditLogService.getLocalBackups();
    expect(backups.length).toBe(1);
    expect(backups[0].action).toBe('voice_command');
  });

  it('queries audit logs successfully', async () => {
    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        events: [
          {
            id: 'event-1',
            timestamp: '2024-01-01T00:00:00Z',
            userId: 'user-1',
            tenantId: 'tenant-1',
            action: 'voice_command',
            resource: 'test',
            status: 'success',
          },
        ],
        total: 1,
        hasMore: false,
      }),
    });

    const result = await auditLogService.query({ userId: 'user-1' });

    expect(result.events.length).toBe(1);
    expect(result.total).toBe(1);
    expect(result.hasMore).toBe(false);
  });

  it('gets recent audit events', async () => {
    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        events: [
          {
            id: 'event-1',
            timestamp: '2024-01-01T00:00:00Z',
            userId: 'user-1',
            tenantId: 'tenant-1',
            action: 'voice_command',
            resource: 'test',
            status: 'success',
          },
        ],
        total: 1,
        hasMore: false,
      }),
    });

    const events = await auditLogService.getRecent(10);

    expect(events.length).toBe(1);
  });

  it('stores backup locally', () => {
    const event: AuditEvent = {
      id: 'event-1',
      timestamp: '2024-01-01T00:00:00Z',
      userId: 'user-1',
      tenantId: 'tenant-1',
      action: 'voice_command',
      resource: 'test',
      status: 'success',
    };

    auditLogService.storeLocally(event);

    const backups = auditLogService.getLocalBackups();
    expect(backups.length).toBe(1);
    expect(backups[0].id).toBe('event-1');
  });

  it('clears local backups', () => {
    const event: AuditEvent = {
      id: 'event-1',
      timestamp: '2024-01-01T00:00:00Z',
      userId: 'user-1',
      tenantId: 'tenant-1',
      action: 'voice_command',
      resource: 'test',
      status: 'success',
    };

    auditLogService.storeLocally(event);
    auditLogService.clearLocalBackups();

    const backups = auditLogService.getLocalBackups();
    expect(backups.length).toBe(0);
  });

  it('syncs local backups to server', async () => {
    const event: AuditEvent = {
      id: 'event-1',
      timestamp: '2024-01-01T00:00:00Z',
      userId: 'user-1',
      tenantId: 'tenant-1',
      action: 'voice_command',
      resource: 'test',
      status: 'success',
    };

    auditLogService.storeLocally(event);

    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({}),
    });

    await auditLogService.syncBackups();

    const backups = auditLogService.getLocalBackups();
    expect(backups.length).toBe(0);
  });
});

describe('useAuditLog hook', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it('logs event using hook', async () => {
    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({}),
    });

    const { log } = useAuditLog();

    const event = {
      userId: 'user-1',
      tenantId: 'tenant-1',
      action: 'voice_command' as const,
      resource: 'test',
      status: 'success' as const,
    };

    await log(event);

    expect(global.fetch).toHaveBeenCalled();
  });

  it('queries logs using hook', async () => {
    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        events: [],
        total: 0,
        hasMore: false,
      }),
    });

    const { query } = useAuditLog();

    const result = await query({ userId: 'user-1' });

    expect(result.total).toBe(0);
  });

  it('gets recent logs using hook', async () => {
    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        events: [],
        total: 0,
        hasMore: false,
      }),
    });

    const { getRecent } = useAuditLog();

    const events = await getRecent(10);

    expect(events).toEqual([]);
  });
});
