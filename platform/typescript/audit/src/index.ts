/**
 * @ghatana/audit — Shared platform audit-logging primitives
 *
 * Provides typed audit event schemas, a configurable audit-log service,
 * and React hook factories for cross-product compliance logging.
 *
 * Products inject their own HTTP transport so backend URLs and auth
 * stay product-local.
 *
 * @doc.type module
 * @doc.purpose Cross-product audit logging types, schemas, and service factory
 * @doc.layer platform
 * @doc.pattern Service Factory
 */

import { z } from "zod";

// ---------------------------------------------------------------------------
// Schemas
// ---------------------------------------------------------------------------

/**
 * Audit event schema for runtime validation.
 *
 * This is the platform-wide canonical audit event shape.
 * Products may extend metadata but must not remove required fields.
 */
export const AuditEventSchema = z
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
      "suggestion_apply",
      "bulk_delete",
      "bulk_approve",
      "bulk_reject",
      "redaction",
      "purge",
      "retention_classify",
      "connector_change",
      "plugin_activate",
      "automation_assisted_mutation",
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
        "connector",
        "plugin",
        "dataset",
      ])
      .optional(),
    status: z.enum(["success", "failure", "denied"]),
    metadata: z.record(z.string(), z.unknown()).optional(),
    ipAddress: z
      .string()
      .regex(
        /^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$|^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$/
      )
      .optional(),
    userAgent: z.string().optional(),
  })
  .strict();

export type AuditEvent = z.infer<typeof AuditEventSchema>;

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

// ---------------------------------------------------------------------------
// Minimal HTTP transport interface (product-agnostic)
// ---------------------------------------------------------------------------

export interface AuditHttpTransport {
  post(url: string, body: unknown): Promise<unknown>;
  get(url: string, params?: Record<string, string | number | boolean | null | undefined>): Promise<unknown>;
}

function emitAuditDiagnostic(
  level: "error",
  message: string,
  context?: Record<string, unknown>
): void {
  if (typeof globalThis.dispatchEvent === "function" && typeof CustomEvent !== "undefined") {
    globalThis.dispatchEvent(
      new CustomEvent("ghatana:audit-diagnostic", {
        detail: {
          level,
          message,
          context,
          timestamp: new Date().toISOString(),
        },
      })
    );
  }
}

// ---------------------------------------------------------------------------
// Local backup helpers
// ---------------------------------------------------------------------------

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
 * High-risk actions for which local sessionStorage backup is prohibited.
 *
 * These actions involve sensitive PII, policy bodies, privacy data,
 * or irreversible mutations that must never be stored in browser storage —
 * even in minimized form — to satisfy data-minimization and breach-risk requirements.
 *
 * Enforcement: `storeLocally` is a no-op for events with these action types
 * regardless of the `enableLocalBackup` config flag.
 */
export const HIGH_RISK_AUDIT_ACTIONS = new Set<AuditEvent["action"]>([
  "redaction",
  "purge",
  "retention_classify",
  "consent_change",
  "policy_approve",
  "policy_reject",
  "bulk_delete",
]);

/**
 * Returns true when the action's payload must never be cached locally,
 * regardless of the service-level `enableLocalBackup` setting.
 */
export function isHighRiskAuditAction(action: AuditEvent["action"]): boolean {
  return HIGH_RISK_AUDIT_ACTIONS.has(action);
}

// ---------------------------------------------------------------------------
// Service factory
// ---------------------------------------------------------------------------

export interface AuditLogServiceConfig {
  transport: AuditHttpTransport;
  logEndpoint?: string;
  queryEndpoint?: string;
  /**
   * Whether to enable local sessionStorage backup when the network fails.
   * Defaults to true. Products handling high-risk privacy events should
   * set this to false and rely on server-side persistence only.
   */
  enableLocalBackup?: boolean;
}

/**
 * Create an audit-log service bound to the provided HTTP transport.
 *
 * @example
 * const audit = createAuditLogService({ transport: apiClient });
 */
export function createAuditLogService(config: AuditLogServiceConfig) {
  const logEndpoint = config.logEndpoint ?? "/api/v1/audit/log";
  const queryEndpoint = config.queryEndpoint ?? "/api/v1/audit/query";
  const enableLocalBackup = config.enableLocalBackup ?? true;

  return {
    /**
     * Log an audit event
     */
    async log(event: Omit<AuditEvent, "id" | "timestamp">): Promise<void> {
      const auditEvent: AuditEvent = {
        id: crypto.randomUUID(),
        timestamp: new Date().toISOString(),
        ...event,
      };

      const validated = AuditEventSchema.parse(auditEvent);

      try {
        await config.transport.post(logEndpoint, validated);
      } catch (error) {
        emitAuditDiagnostic("error", "Error logging audit event", { error });
        if (enableLocalBackup) {
          this.storeLocally(validated);
        }
      }
    },

    /**
     * Query audit logs
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

      const data = await config.transport.get(queryEndpoint, Object.fromEntries(params.entries()));

      const validated = z
        .object({
          events: z.array(AuditEventSchema),
          total: z.number(),
          hasMore: z.boolean(),
        })
        .parse(data);

      return validated;
    },

    /**
     * Get recent audit events
     */
    async getRecent(limit = 10): Promise<AuditEvent[]> {
      const result = await this.query({ limit });
      return result.events;
    },

    /**
     * Store audit event locally as backup.
     *
     * NOTE: Base64 obfuscation is not encryption. For high-risk privacy
     * events, set `enableLocalBackup: false` in the service config so
     * that failed events are never persisted in browser storage.
     *
     * High-risk actions (redaction, purge, retention_classify, consent_change,
     * policy_approve, policy_reject, bulk_delete) are ALWAYS blocked from local
     * storage regardless of the `enableLocalBackup` config flag, because their
     * payloads carry sensitive policy bodies, PII references, or irreversible
     * mutation metadata that must not be cached in browser storage.
     */
    storeLocally(event: AuditEvent): void {
      // Enforce high-risk action block unconditionally.
      if (isHighRiskAuditAction(event.action)) {
        return;
      }
      try {
        // Minimize: strip PII fields before local backup
        const minimal = {
          id: event.id,
          timestamp: event.timestamp,
          userId: event.userId,
          tenantId: event.tenantId,
          action: event.action,
          resource: event.resource,
          resourceType: event.resourceType,
          status: event.status,
        };
        const key = `audit_backup_${event.id}`;
        sessionStorage.setItem(key, btoa(JSON.stringify(minimal)));

        // Clean up old backups (keep last 50)
        const keys = getStorageKeys(sessionStorage)
          .filter((k) => k.startsWith("audit_backup_"))
          .sort((a, b) => {
            try {
              const aData = JSON.parse(atob(sessionStorage.getItem(a) || "e30="));
              const bData = JSON.parse(atob(sessionStorage.getItem(b) || "e30="));
              return (bData.timestamp || "").localeCompare(aData.timestamp || "");
            } catch {
              return 0;
            }
          });

        keys.slice(50).forEach((k) => sessionStorage.removeItem(k));
      } catch (error) {
        emitAuditDiagnostic("error", "Failed to store audit event locally", { error });
      }
    },

    /**
     * Get locally stored backup audit events
     */
    getLocalBackups(): AuditEvent[] {
      try {
        const keys = getStorageKeys(sessionStorage).filter((k) =>
          k.startsWith("audit_backup_")
        );
        return keys
          .map((k) => {
            const encoded = sessionStorage.getItem(k);
            if (!encoded) return null;
            try {
              const data = JSON.parse(atob(encoded));
              return AuditEventSchema.parse(data);
            } catch {
              sessionStorage.removeItem(k);
              return null;
            }
          })
          .filter((e): e is AuditEvent => e !== null)
          .sort((a, b) => b.timestamp.localeCompare(a.timestamp));
      } catch (error) {
        emitAuditDiagnostic("error", "Failed to get local audit backups", { error });
        return Array.from<AuditEvent>([]);
      }
    },

    /**
     * Clear locally stored backup audit events
     */
    clearLocalBackups(): void {
      try {
        const keys = getStorageKeys(sessionStorage).filter((k) =>
          k.startsWith("audit_backup_")
        );
        keys.forEach((k) => sessionStorage.removeItem(k));
      } catch (error) {
        emitAuditDiagnostic("error", "Failed to clear local audit backups", { error });
      }
    },

    /**
     * Sync local backups to server
     */
    async syncBackups(): Promise<void> {
      const backups = this.getLocalBackups();

      for (const event of backups) {
        try {
          await this.log(event);
          // Remove successful sync
          sessionStorage.removeItem(`audit_backup_${event.id}`);
        } catch (error) {
          emitAuditDiagnostic("error", "Failed to sync audit event", { error, eventId: event.id });
        }
      }
    },
  };
}

export type AuditLogService = ReturnType<typeof createAuditLogService>;

// ---------------------------------------------------------------------------
// React hook factory
// ---------------------------------------------------------------------------

/**
 * Create a `useAuditLog` hook bound to a specific audit-log service instance.
 */
export function createUseAuditLog(service: AuditLogService) {
  return function useAuditLog() {
    const log = async (event: Omit<AuditEvent, "id" | "timestamp">) => {
      await service.log(event);
    };

    const query = async (filters?: AuditLogFilters) => {
      return await service.query(filters);
    };

    const getRecent = async (limit?: number) => {
      return await service.getRecent(limit);
    };

    const syncBackups = async () => {
      await service.syncBackups();
    };

    return {
      log,
      query,
      getRecent,
      syncBackups,
      getLocalBackups: service.getLocalBackups.bind(service),
      clearLocalBackups: service.clearLocalBackups.bind(service),
    };
  };
}
