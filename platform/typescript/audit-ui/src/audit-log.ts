/**
 * Audit Log Service - Generic audit logging service
 * 
 * @doc.type service
 * @doc.purpose Provide audit logging for privacy-sensitive operations
 * @doc.layer frontend
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
  metadata: z.record(z.unknown()).optional(),
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
 */
export class AuditLogService {
  private baseUrl: string;

  constructor(baseUrl: string = '/api/v1/audit') {
    this.baseUrl = baseUrl;
  }

  /**
   * Log an audit event
   */
  async log(event: Omit<AuditEvent, 'id' | 'timestamp'>): Promise<AuditEvent> {
    const auditEvent: AuditEvent = {
      ...event,
      id: `audit_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      timestamp: new Date().toISOString(),
    };

    try {
      const response = await fetch(`${this.baseUrl}/log`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(auditEvent),
      });

      if (!response.ok) {
        throw new Error('Failed to log audit event');
      }

      // Store locally for backup
      this.storeLocally(auditEvent);

      return auditEvent;
    } catch (error) {
      // Store locally if API fails
      this.storeLocally(auditEvent);
      throw error;
    }
  }

  /**
   * Query audit logs
   */
  async query(options: AuditLogQueryOptions = {}): Promise<AuditLogQueryResponse> {
    const params = new URLSearchParams();
    
    Object.entries(options).forEach(([key, value]) => {
      if (value !== undefined) {
        params.append(key, value.toString());
      }
    });

    const response = await fetch(`${this.baseUrl}/query?${params}`);
    
    if (!response.ok) {
      throw new Error('Failed to query audit logs');
    }

    return response.json();
  }

  /**
   * Get recent audit events
   */
  async getRecent(limit: number = 10): Promise<AuditEvent[]> {
    const result = await this.query({ limit });
    return result.events;
  }

  /**
   * Store event locally for backup
   */
  storeLocally(event: AuditEvent): void {
    const backups = this.getLocalBackups();
    backups.push(event);
    
    // Keep only last 100 events
    if (backups.length > 100) {
      backups.splice(0, backups.length - 100);
    }
    
    localStorage.setItem('audit_backups', JSON.stringify(backups));
  }

  /**
   * Get local backup events
   */
  getLocalBackups(): AuditEvent[] {
    try {
      const stored = localStorage.getItem('audit_backups');
      return stored ? JSON.parse(stored) : [];
    } catch {
      return [];
    }
  }

  /**
   * Clear local backups
   */
  clearLocalBackups(): void {
    localStorage.removeItem('audit_backups');
  }

  /**
   * Sync local backups to server
   */
  async syncBackups(): Promise<void> {
    const backups = this.getLocalBackups();
    
    for (const event of backups) {
      try {
        await this.log(event);
      } catch {
        // Continue with other events
      }
    }
    
    this.clearLocalBackups();
  }
}

/**
 * Global audit log service instance
 */
export const auditLogService = new AuditLogService();

/**
 * Use audit log hook
 */
export function useAuditLog(service: AuditLogService = auditLogService) {
  return {
    log: (event: Omit<AuditEvent, 'id' | 'timestamp'>) => service.log(event),
    query: (options: AuditLogQueryOptions) => service.query(options),
    getRecent: (limit?: number) => service.getRecent(limit),
    getLocalBackups: () => service.getLocalBackups(),
    clearLocalBackups: () => service.clearLocalBackups(),
    syncBackups: () => service.syncBackups(),
  };
}
