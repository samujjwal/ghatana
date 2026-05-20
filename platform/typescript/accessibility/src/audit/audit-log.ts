/**
 * Audit Log Service - Generic audit logging service
 *
 * @doc.type service
 * @doc.purpose Provide audit logging for privacy-sensitive operations
 * @doc.layer platform
 * @doc.pattern Audit Service
 */

import { z } from 'zod';

/**
 * Audit event schema
 */
export const AuditEventSchema = z.object({
  id: z.string().optional(),
  timestamp: z.string().datetime(),
  userId: z.string(),
  tenantId: z.string(),
  action: z.enum(['create', 'read', 'update', 'delete', 'bulk_delete', 'voice_command', 'permission_check']),
  resource: z.string(),
  resourceType: z.string().optional(),
  status: z.enum(['success', 'failure', 'error']),
  metadata: z.record(z.string(), z.unknown()).optional(),
});

export type AuditEvent = z.infer<typeof AuditEventSchema>;

/**
 * Audit log query response
 */
export interface AuditLogQueryResponse {
  events: AuditEvent[];
  total: number;
  hasMore: boolean;
  cursor?: string;
}

/**
 * Audit log query options
 */
export interface AuditLogQueryOptions {
  userId?: string;
  tenantId?: string;
  action?: string;
  resource?: string;
  resourceType?: string;
  status?: string;
  from?: string;
  to?: string;
  limit?: number;
  cursor?: string;
}

/**
 * Audit log service
 *
 * @doc.type class
 * @doc.purpose HTTP-backed audit log service with local storage fallback
 * @doc.layer platform
 * @doc.pattern Service
 */
export class AuditLogService {
  private readonly baseUrl: string;

  constructor(baseUrl: string = '/api/v1/audit') {
    this.baseUrl = baseUrl;
  }

  /**
   * Log an audit event, persisting locally if the remote call fails.
   */
  async log(event: Omit<AuditEvent, 'id' | 'timestamp'>): Promise<AuditEvent> {
    const auditEvent: AuditEvent = {
      ...event,
      id: `audit_${Date.now()}_${Math.random().toString(36).substring(2, 11)}`,
      timestamp: new Date().toISOString(),
    };

    try {
      const response = await fetch(`${this.baseUrl}/log`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(auditEvent),
      });

      if (!response.ok) {
        throw new Error(`Audit log request failed: ${response.status}`);
      }

      this.storeLocally(auditEvent);
      return auditEvent;
    } catch (error) {
      this.storeLocally(auditEvent);
      throw error;
    }
  }

  /**
   * Query audit logs with optional filters.
   */
  async query(options: AuditLogQueryOptions = {}): Promise<AuditLogQueryResponse> {
    const params = new URLSearchParams();

    for (const [key, value] of Object.entries(options)) {
      if (value !== undefined) {
        params.append(key, String(value));
      }
    }

    const response = await fetch(`${this.baseUrl}/query?${params.toString()}`);

    if (!response.ok) {
      throw new Error(`Audit log query failed: ${response.status}`);
    }

    const data: unknown = await response.json();
    return data as AuditLogQueryResponse;
  }

  /**
   * Return the most recent audit events.
   */
  async getRecent(limit: number = 10): Promise<AuditEvent[]> {
    const result = await this.query({ limit });
    return result.events;
  }

  /**
   * Persist an event in localStorage as a backup.
   */
  storeLocally(event: AuditEvent): void {
    const backups = this.getLocalBackups();
    backups.push(event);

    if (backups.length > 100) {
      backups.splice(0, backups.length - 100);
    }

    localStorage.setItem('audit_backups', JSON.stringify(backups));
  }

  /**
   * Return locally-stored backup events.
   */
  getLocalBackups(): AuditEvent[] {
    try {
      const stored = localStorage.getItem('audit_backups');
      return stored ? (JSON.parse(stored) as AuditEvent[]) : [];
    } catch {
      return Array.from<AuditEvent>([]);
    }
  }

  /**
   * Remove all locally-stored backup events.
   */
  clearLocalBackups(): void {
    localStorage.removeItem('audit_backups');
  }

  /**
   * Attempt to sync locally-stored backups to the server.
   */
  async syncBackups(): Promise<void> {
    const backups = this.getLocalBackups();

    for (const event of backups) {
      try {
        await this.log(event);
      } catch {
        // Continue syncing remaining events even if one fails
      }
    }

    this.clearLocalBackups();
  }
}

/**
 * Default singleton audit log service instance.
 */
export const auditLogService = new AuditLogService();

/**
 * React hook providing a stable interface to AuditLogService.
 */
export function useAuditLog(service: AuditLogService = auditLogService): {
  log: (event: Omit<AuditEvent, 'id' | 'timestamp'>) => Promise<AuditEvent>;
  query: (options: AuditLogQueryOptions) => Promise<AuditLogQueryResponse>;
  getRecent: (limit?: number) => Promise<AuditEvent[]>;
  getLocalBackups: () => AuditEvent[];
  clearLocalBackups: () => void;
  syncBackups: () => Promise<void>;
} {
  return {
    log: (event) => service.log(event),
    query: (options) => service.query(options),
    getRecent: (limit) => service.getRecent(limit),
    getLocalBackups: () => service.getLocalBackups(),
    clearLocalBackups: () => service.clearLocalBackups(),
    syncBackups: () => service.syncBackups(),
  };
}
