/**
 * Data Cloud Audit Log Service — Product-local instance of the shared platform audit service.
 *
 * Re-exports platform primitives from @ghatana/audit and wires them to the
 * Data Cloud API transport so every privacy-sensitive operation is consistently logged.
 *
 * @doc.type service
 * @doc.purpose Log all privacy-sensitive operations for compliance via shared platform audit
 * @doc.layer frontend
 */

import {
  type AuditEvent,
  type AuditLogFilters,
  type AuditLogQueryResponse,
  type AuditHttpTransport,
  createAuditLogService,
  createUseAuditLog,
} from "@ghatana/audit";
import { apiClient } from "../lib/api/client";

export type { AuditEvent, AuditLogFilters, AuditLogQueryResponse };

const dcTransport: AuditHttpTransport = {
  async post(url: string, body: unknown) {
    return apiClient.post(url, body);
  },
  async get(
    url: string,
    params?: Record<string, string | number | boolean | null | undefined>
  ) {
    return apiClient.get(url, { params });
  },
};

/**
 * Data Cloud audit-log service instance backed by the shared platform audit package.
 */
export const auditLogService = createAuditLogService({
  transport: dcTransport,
  logEndpoint: "/api/v1/audit/log",
  queryEndpoint: "/api/v1/audit/query",
});

/**
 * Hook for using the Data Cloud audit-log service in React components.
 */
export const useAuditLog = createUseAuditLog(auditLogService);
