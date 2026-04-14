/**
 * @ghatana/audit-ui — Audit UI platform stub.
 *
 * Stub implementation until the full platform package is built.
 *
 * @doc.type module
 * @doc.purpose Audit log service, hook, and type definitions
 * @doc.layer platform
 * @doc.pattern Library
 */

// ── Types ──────────────────────────────────────────────────────────────────────

export type AuditAction =
  | 'voice_command'
  | 'data_access'
  | 'data_export'
  | 'consent_change'
  | 'login'
  | 'logout'
  | 'settings_change'
  | 'admin_action';

export type AuditStatus = 'success' | 'failure' | 'pending';

export interface AuditEvent {
  id?: string;
  timestamp?: string;
  userId: string;
  tenantId: string;
  action: AuditAction;
  resource: string;
  status: AuditStatus;
  metadata?: Record<string, unknown>;
}

export interface AuditLogQueryOptions {
  userId?: string;
  tenantId?: string;
  action?: AuditAction;
  resource?: string;
  status?: AuditStatus;
  startTime?: string;
  endTime?: string;
  limit?: number;
  offset?: number;
}

export interface AuditLogQueryResponse {
  events: AuditEvent[];
  total: number;
  hasMore: boolean;
}

// ── Service ────────────────────────────────────────────────────────────────────

const LOCAL_STORAGE_KEY = 'ghatana_audit_log_backups';
const API_ENDPOINT = '/api/v1/audit/log';
const QUERY_ENDPOINT = '/api/v1/audit/events';

export class AuditLogService {
  async log(event: Omit<AuditEvent, 'id' | 'timestamp'>): Promise<void> {
    const enriched: AuditEvent = {
      ...event,
      id: crypto.randomUUID(),
      timestamp: new Date().toISOString(),
    };
    try {
      const res = await fetch(API_ENDPOINT, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(enriched),
      });
      if (!res.ok) {
        this.storeLocally(enriched);
      }
    } catch {
      this.storeLocally(enriched);
    }
  }

  async query(options: AuditLogQueryOptions = {}): Promise<AuditLogQueryResponse> {
    const params = new URLSearchParams();
    (Object.entries(options) as [string, unknown][]).forEach(([k, v]) => {
      if (v !== undefined) params.set(k, String(v));
    });
    const res = await fetch(`${QUERY_ENDPOINT}?${params.toString()}`);
    if (!res.ok) throw new Error(`Audit query failed: ${res.status}`);
    return (res.json() as Promise<AuditLogQueryResponse>);
  }

  async getRecent(limit = 50): Promise<AuditEvent[]> {
    const result = await this.query({ limit });
    return result.events;
  }

  storeLocally(event: AuditEvent): void {
    try {
      const existing = this.getLocalBackups();
      existing.push(event);
      localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(existing));
    } catch {
      // Ignore storage errors
    }
  }

  getLocalBackups(): AuditEvent[] {
    try {
      const raw = localStorage.getItem(LOCAL_STORAGE_KEY);
      return raw ? (JSON.parse(raw) as AuditEvent[]) : [];
    } catch {
      return [];
    }
  }

  clearLocalBackups(): void {
    localStorage.removeItem(LOCAL_STORAGE_KEY);
  }

  async syncBackups(): Promise<void> {
    const backups = this.getLocalBackups();
    if (backups.length === 0) return;
    const results = await Promise.allSettled(
      backups.map((e) =>
        fetch(API_ENDPOINT, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(e),
        })
      )
    );
    const failed = backups.filter((_, i) => results[i].status === 'rejected');
    localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(failed));
  }
}

export const auditLogService = new AuditLogService();

// ── Hook ───────────────────────────────────────────────────────────────────────

export interface UseAuditLogReturn {
  log: (event: Omit<AuditEvent, 'id' | 'timestamp'>) => Promise<void>;
  query: (options?: AuditLogQueryOptions) => Promise<AuditLogQueryResponse>;
  getRecent: (limit?: number) => Promise<AuditEvent[]>;
}

export function useAuditLog(): UseAuditLogReturn {
  return {
    log: (event) => auditLogService.log(event),
    query: (options) => auditLogService.query(options),
    getRecent: (limit) => auditLogService.getRecent(limit),
  };
}
