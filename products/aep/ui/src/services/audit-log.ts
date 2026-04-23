/**
 * AuditLog — Audit logging for privacy-sensitive operations.
 *
 * Designed for cross-product reuse. Can be extracted to @ghatana/audit-ui
 * after validation in AEP and Data Cloud.
 *
 * @doc.type service
 * @doc.purpose Log all privacy-sensitive operations for compliance
 * @doc.layer frontend
 */

import { z } from "zod";
import { apiClient } from "@/lib/http-client";

/**
 * Audit event schema for runtime validation
 */
const AuditEvent = z
  .object({
    id: z.string(),
    timestamp: z.string().datetime(),
    userId: z.string(),
    tenantId: z.string(),
    action: z.enum([
      "voice_command",
      "hitl_approve",
      "hitl_reject",
      "pipeline_run",
      "pipeline_cancel",
      "policy_approve",
      "policy_reject",
      "data_access",
      "consent_change",
      "ai_suggestion_apply",
      "bulk_delete",
      "bulk_approve",
      "bulk_reject",
    ]),
    resource: z.string(),
    resourceType: z
      .enum([
        "pipeline",
        "run",
        "agent",
        "policy",
        "review_item",
        "entity",
        "collection",
        "workflow",
        "user",
      ])
      .optional(),
    status: z.enum(["success", "failure", "denied"]),
    metadata: z.record(z.string(), z.unknown()).optional(),
    ipAddress: z
      .string()
      .regex(
        /^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$|^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$/,
      )
      .optional(),
    userAgent: z.string().optional(),
  })
  .strict();

export type AuditEvent = z.infer<typeof AuditEvent>;

/**
 * Audit log query filters
 */
export interface AuditLogFilters {
  userId?: string;
  action?: AuditEvent["action"];
  resource?: string;
  resourceType?: AuditEvent["resourceType"];
  status?: AuditEvent["status"];
  from?: string;
  to?: string;
  limit?: number;
  offset?: number;
}

/**
 * Audit log query response
 */
export interface AuditLogQueryResponse {
  events: AuditEvent[];
  total: number;
  hasMore: boolean;
}

function getStorageKeys(storage: Storage): string[] {
  const keys: string[] = [];

  for (let index = 0; index < storage.length; index += 1) {
    const key = storage.key(index);
    if (key !== null) {
      keys.push(key);
    }
  }

  return keys;
}

/**
 * Audit log service
 *
 * Provides methods to log audit events and query audit logs.
 * All logs are sent to the backend for storage in an append-only
 * audit table with tenant isolation.
 */
export const auditLogService = {
  /**
   * Log an audit event
   *
   * @param event - The audit event to log (without id and timestamp)
   * @returns Promise that resolves when the event is logged
   */
  async log(event: Omit<AuditEvent, "id" | "timestamp">): Promise<void> {
    const auditEvent: AuditEvent = {
      id: crypto.randomUUID(),
      timestamp: new Date().toISOString(),
      ...event,
    };

    // Validate the event before sending
    const validated = AuditEvent.parse(auditEvent);

    try {
      await apiClient.post("/api/v1/audit/log", validated);
    } catch (error) {
      console.error("Error logging audit event:", error);
      // Store in localStorage as backup
      this.storeLocally(validated);
    }
  },

  /**
   * Query audit logs
   *
   * @param filters - Query filters
   * @returns Promise that resolves with the audit log query response
   */
  async query(filters: AuditLogFilters = {}): Promise<AuditLogQueryResponse> {
    const params = new URLSearchParams();

    if (filters.userId) params.set("userId", filters.userId);
    if (filters.action) params.set("action", filters.action);
    if (filters.resource) params.set("resource", filters.resource);
    if (filters.resourceType) params.set("resourceType", filters.resourceType);
    if (filters.status) params.set("status", filters.status);
    if (filters.from) params.set("from", filters.from);
    if (filters.to) params.set("to", filters.to);
    if (filters.limit) params.set("limit", filters.limit.toString());
    if (filters.offset) params.set("offset", filters.offset.toString());

    try {
      const { data } = await apiClient.get<unknown>(`/api/v1/audit/query`, {
        params: Object.fromEntries(params.entries()),
      });

      // Validate response
      const validated = z
        .object({
          events: z.array(AuditEvent),
          total: z.number(),
          hasMore: z.boolean(),
        })
        .parse(data);

      return validated;
    } catch (error) {
      console.error("Error querying audit logs:", error);
      throw error;
    }
  },

  /**
   * Get recent audit events for the current user
   *
   * @param limit - Maximum number of events to return
   * @returns Promise that resolves with recent audit events
   */
  async getRecent(limit = 10): Promise<AuditEvent[]> {
    const result = await this.query({ limit });
    return result.events;
  },

  /**
   * Store audit event locally as backup
   *
   * @param event - The audit event to store
   */
  storeLocally(event: AuditEvent): void {
    try {
      const key = `audit_backup_${event.id}`;
      localStorage.setItem(key, JSON.stringify(event));

      // Clean up old backups (keep last 100)
      const keys = getStorageKeys(localStorage)
        .filter((k) => k.startsWith("audit_backup_"))
        .sort((a, b) => {
          const aTime =
            JSON.parse(localStorage.getItem(a) || "{}").timestamp || "";
          const bTime =
            JSON.parse(localStorage.getItem(b) || "{}").timestamp || "";
          return bTime.localeCompare(aTime);
        });

      keys.slice(100).forEach((k) => localStorage.removeItem(k));
    } catch (error) {
      console.error("Failed to store audit event locally:", error);
    }
  },

  /**
   * Get locally stored backup audit events
   *
   * @returns Array of locally stored audit events
   */
  getLocalBackups(): AuditEvent[] {
    try {
      const keys = getStorageKeys(localStorage).filter((k) =>
        k.startsWith("audit_backup_"),
      );
      return keys
        .map((k) => {
          const data = localStorage.getItem(k);
          if (!data) return null;
          try {
            return AuditEvent.parse(JSON.parse(data));
          } catch {
            localStorage.removeItem(k);
            return null;
          }
        })
        .filter((e): e is AuditEvent => e !== null)
        .sort((a, b) => b.timestamp.localeCompare(a.timestamp));
    } catch (error) {
      console.error("Failed to get local audit backups:", error);
      return [];
    }
  },

  /**
   * Clear locally stored backup audit events
   */
  clearLocalBackups(): void {
    try {
      const keys = getStorageKeys(localStorage).filter((k) =>
        k.startsWith("audit_backup_"),
      );
      keys.forEach((k) => localStorage.removeItem(k));
    } catch (error) {
      console.error("Failed to clear local audit backups:", error);
    }
  },

  /**
   * Sync local backups to server
   *
   * @returns Promise that resolves when sync is complete
   */
  async syncBackups(): Promise<void> {
    const backups = this.getLocalBackups();

    for (const event of backups) {
      try {
        await this.log(event);
        // Remove successful sync
        localStorage.removeItem(`audit_backup_${event.id}`);
      } catch (error) {
        console.error(`Failed to sync audit event ${event.id}:`, error);
      }
    }
  },
};

/**
 * Hook for using audit log service in React components
 */
export function useAuditLog() {
  const log = async (event: Omit<AuditEvent, "id" | "timestamp">) => {
    await auditLogService.log(event);
  };

  const query = async (filters?: AuditLogFilters) => {
    return await auditLogService.query(filters);
  };

  const getRecent = async (limit?: number) => {
    return await auditLogService.getRecent(limit);
  };

  const syncBackups = async () => {
    await auditLogService.syncBackups();
  };

  return {
    log,
    query,
    getRecent,
    syncBackups,
    getLocalBackups: auditLogService.getLocalBackups.bind(auditLogService),
    clearLocalBackups: auditLogService.clearLocalBackups.bind(auditLogService),
  };
}
