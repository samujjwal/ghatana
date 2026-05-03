/**
 * Tests for @ghatana/audit
 *
 * Covers:
 * - High-risk action local-backup enforcement (unconditional block)
 * - Normal action local-backup (allowed when enableLocalBackup=true)
 * - isHighRiskAuditAction helper
 * - createAuditLogService storeLocally path
 *
 * @doc.type test
 * @doc.purpose Verify audit service high-risk local-backup policy
 * @doc.layer platform
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  createAuditLogService,
  HIGH_RISK_AUDIT_ACTIONS,
  isHighRiskAuditAction,
  type AuditEvent,
} from '../index';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeEvent(
  action: AuditEvent['action'],
  overrides: Partial<AuditEvent> = {}
): AuditEvent {
  return {
    id: crypto.randomUUID(),
    timestamp: new Date().toISOString(),
    userId: 'user-1',
    tenantId: 'tenant-1',
    action,
    resource: '/data/collection-1',
    status: 'success',
    ...overrides,
  };
}

const mockTransport = {
  post: vi.fn<[string, unknown], Promise<unknown>>().mockResolvedValue({}),
  get: vi.fn<[string, Record<string, unknown> | undefined], Promise<unknown>>().mockResolvedValue({
    events: [],
    total: 0,
    hasMore: false,
  }),
};

// ---------------------------------------------------------------------------
// isHighRiskAuditAction
// ---------------------------------------------------------------------------

describe('isHighRiskAuditAction', () => {
  it('returns true for all HIGH_RISK_AUDIT_ACTIONS members', () => {
    for (const action of HIGH_RISK_AUDIT_ACTIONS) {
      expect(isHighRiskAuditAction(action)).toBe(true);
    }
  });

  it('returns false for non-high-risk actions', () => {
    const safeActions: AuditEvent['action'][] = [
      'pipeline_run',
      'pipeline_cancel',
      'data_access',
      'suggestion_apply',
      'voice_command',
      'hitl_approve',
      'hitl_reject',
      'connector_change',
      'plugin_activate',
      'automation_assisted_mutation',
    ];
    for (const action of safeActions) {
      expect(isHighRiskAuditAction(action)).toBe(false);
    }
  });
});

// ---------------------------------------------------------------------------
// storeLocally — high-risk enforcement
// ---------------------------------------------------------------------------

describe('createAuditLogService.storeLocally', () => {
  let sessionStorageSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    sessionStorageSpy = vi.spyOn(globalThis.sessionStorage, 'setItem');
    sessionStorageSpy.mockClear();
  });

  it('does NOT write to sessionStorage for redaction even when enableLocalBackup=true', () => {
    const service = createAuditLogService({
      transport: mockTransport,
      enableLocalBackup: true,
    });

    service.storeLocally(makeEvent('redaction'));

    expect(sessionStorageSpy).not.toHaveBeenCalled();
  });

  it('does NOT write to sessionStorage for purge', () => {
    const service = createAuditLogService({ transport: mockTransport, enableLocalBackup: true });
    service.storeLocally(makeEvent('purge'));
    expect(sessionStorageSpy).not.toHaveBeenCalled();
  });

  it('does NOT write to sessionStorage for consent_change', () => {
    const service = createAuditLogService({ transport: mockTransport, enableLocalBackup: true });
    service.storeLocally(makeEvent('consent_change'));
    expect(sessionStorageSpy).not.toHaveBeenCalled();
  });

  it('does NOT write to sessionStorage for retention_classify', () => {
    const service = createAuditLogService({ transport: mockTransport, enableLocalBackup: true });
    service.storeLocally(makeEvent('retention_classify'));
    expect(sessionStorageSpy).not.toHaveBeenCalled();
  });

  it('does NOT write to sessionStorage for policy_approve', () => {
    const service = createAuditLogService({ transport: mockTransport, enableLocalBackup: true });
    service.storeLocally(makeEvent('policy_approve'));
    expect(sessionStorageSpy).not.toHaveBeenCalled();
  });

  it('does NOT write to sessionStorage for policy_reject', () => {
    const service = createAuditLogService({ transport: mockTransport, enableLocalBackup: true });
    service.storeLocally(makeEvent('policy_reject'));
    expect(sessionStorageSpy).not.toHaveBeenCalled();
  });

  it('does NOT write to sessionStorage for bulk_delete', () => {
    const service = createAuditLogService({ transport: mockTransport, enableLocalBackup: true });
    service.storeLocally(makeEvent('bulk_delete'));
    expect(sessionStorageSpy).not.toHaveBeenCalled();
  });

  it('writes to sessionStorage for pipeline_run when enableLocalBackup=true', () => {
    const service = createAuditLogService({ transport: mockTransport, enableLocalBackup: true });
    service.storeLocally(makeEvent('pipeline_run'));
    expect(sessionStorageSpy).toHaveBeenCalled();
  });

  it('does NOT write to sessionStorage for pipeline_run when enableLocalBackup=false', () => {
    const service = createAuditLogService({ transport: mockTransport, enableLocalBackup: false });
    service.storeLocally(makeEvent('pipeline_run'));
    expect(sessionStorageSpy).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// log() — fallback path blocks high-risk storage
// ---------------------------------------------------------------------------

describe('createAuditLogService.log — high-risk fallback', () => {
  let sessionStorageSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    sessionStorageSpy = vi.spyOn(globalThis.sessionStorage, 'setItem');
    sessionStorageSpy.mockClear();
  });

  it('does not fall back to sessionStorage for redaction even when transport fails', async () => {
    const failingTransport = {
      post: vi.fn().mockRejectedValue(new Error('network error')),
      get: vi.fn().mockResolvedValue({ events: [], total: 0, hasMore: false }),
    };
    const service = createAuditLogService({ transport: failingTransport, enableLocalBackup: true });

    await service.log({
      userId: 'user-1',
      tenantId: 'tenant-1',
      action: 'redaction',
      resource: '/data/entity-123',
      status: 'success',
    });

    expect(sessionStorageSpy).not.toHaveBeenCalled();
  });

  it('does fall back to sessionStorage for pipeline_run when transport fails', async () => {
    const failingTransport = {
      post: vi.fn().mockRejectedValue(new Error('network error')),
      get: vi.fn().mockResolvedValue({ events: [], total: 0, hasMore: false }),
    };
    const service = createAuditLogService({ transport: failingTransport, enableLocalBackup: true });

    await service.log({
      userId: 'user-1',
      tenantId: 'tenant-1',
      action: 'pipeline_run',
      resource: '/pipelines/p-1',
      status: 'success',
    });

    expect(sessionStorageSpy).toHaveBeenCalled();
  });
});
