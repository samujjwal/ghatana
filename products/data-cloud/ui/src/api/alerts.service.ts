/**
 * Alerts Service
 *
 * REST + SSE client for Data-Cloud operational alerts.
 *
 * @doc.type service
 * @doc.purpose Typed client for alerts management API
 * @doc.layer frontend
 * @doc.pattern Service
 */

import { z } from "zod";
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../lib/api/client";
import SessionBootstrap from "../lib/auth/session";
import type { AlertRule } from "../components/alerts/AlertRuleForm";
import {
  ALERTS_UNSUPPORTED_MESSAGE,
  createRuntimeBoundaryError,
} from "@/lib/runtime-boundaries";

export { ALERTS_UNSUPPORTED_MESSAGE } from "@/lib/runtime-boundaries";

export type AlertSeverity = "critical" | "warning" | "info";
export type AlertStatus = "active" | "acknowledged" | "resolved";

export interface Alert {
  id: string;
  title: string;
  description: string;
  severity: AlertSeverity;
  status: AlertStatus;
  source: string;
  createdAt: string;
  acknowledgedAt?: string;
  resolvedAt?: string;
}

export interface AlertGroup {
  id: string;
  title: string;
  rootCause: string;
  alertIds: string[];
  aiConfidence: number;
  suggestedAction: string;
  suggestedActionType: "auto" | "manual";
}

export interface ResolutionSuggestion {
  id: string;
  alertId: string;
  suggestion: string;
  confidence: number;
  canAutoResolve: boolean;
  steps?: string[];
}

export interface AlertQueryParams {
  severity?: AlertSeverity;
  status?: AlertStatus;
  tenantId?: string;
  limit?: number;
  offset?: number;
}

const AlertSchema = z.object({
  id: z.string(),
  title: z.string(),
  description: z.string(),
  severity: z.enum(["critical", "warning", "info"]),
  status: z.enum(["active", "acknowledged", "resolved"]),
  source: z.string(),
  createdAt: z.string(),
  acknowledgedAt: z.string().optional(),
  resolvedAt: z.string().optional(),
});

const AlertGroupSchema = z.object({
  id: z.string(),
  title: z.string(),
  rootCause: z.string(),
  alertIds: z.array(z.string()),
  aiConfidence: z.number(),
  suggestedAction: z.string(),
  suggestedActionType: z.enum(["auto", "manual"]),
});

const ResolutionSuggestionSchema = z.object({
  id: z.string(),
  alertId: z.string(),
  suggestion: z.string(),
  confidence: z.number(),
  canAutoResolve: z.boolean(),
  steps: z.array(z.string()).optional(),
});

const AlertRuleSchema = z.object({
  id: z.string().optional(),
  name: z.string(),
  description: z.string().optional(),
  enabled: z.boolean(),
  severity: z.enum(["critical", "warning", "info"]),
  conditionType: z.enum(["threshold", "anomaly", "pattern", "absence"]),
  metric: z.string(),
  operator: z.enum(["gt", "lt", "eq", "gte", "lte"]),
  threshold: z.number(),
  duration: z.number(),
  channels: z.array(z.enum(["email", "slack", "webhook", "pagerduty"])),
  recipients: z.array(z.string()).optional(),
  webhookUrl: z.string().optional(),
});

const AlertListEnvelopeSchema = z.object({
  tenantId: z.string(),
  alerts: z.array(AlertSchema),
  count: z.number(),
  timestamp: z.string(),
});

const AlertGroupListEnvelopeSchema = z.object({
  tenantId: z.string(),
  groups: z.array(AlertGroupSchema),
  count: z.number(),
  timestamp: z.string(),
});

const ResolutionSuggestionEnvelopeSchema = z.object({
  tenantId: z.string(),
  suggestions: z.array(ResolutionSuggestionSchema),
  count: z.number(),
  timestamp: z.string(),
});

const AlertRuleListEnvelopeSchema = z.object({
  tenantId: z.string(),
  rules: z.array(AlertRuleSchema),
  count: z.number(),
  timestamp: z.string(),
});

const ALERT_EVENT_TYPES = [
  "alert.acknowledged",
  "alert.resolved",
  "alert.group.resolved",
  "alert.suggestion.applied",
  "alert.rule.created",
  "alert.rule.updated",
  "alert.rule.deleted",
] as const;

function getTenantId(explicitTenantId?: string): string {
  return explicitTenantId ?? SessionBootstrap.requireTenantId();
}

function normaliseApiError(error: unknown): never {
  if (typeof error === "object" && error !== null && "status" in error) {
    const status = Number((error as { status?: unknown }).status);
    if (status === 404 || status === 405 || status === 501) {
      throw createRuntimeBoundaryError(ALERTS_UNSUPPORTED_MESSAGE);
    }
  }
  throw error instanceof Error ? error : new Error("Unknown alerts API error");
}

export class AlertsService {
  /** List active and historical alerts */
  async getAlerts(params: AlertQueryParams = {}): Promise<Alert[]> {
    const tenantId = getTenantId(params.tenantId);
    try {
      const response = await apiClient.get("/alerts", {
        params: {
          ...params,
          tenantId,
        },
        headers: { "X-Tenant-ID": tenantId },
      });
      return AlertListEnvelopeSchema.parse(response).alerts;
    } catch (error) {
      return normaliseApiError(error);
    }
  }

  /** Acknowledge an alert */
  async acknowledgeAlert(alertId: string): Promise<Alert> {
    const tenantId = getTenantId();
    try {
      const response = await apiClient.post(
        `/alerts/${alertId}/acknowledge`,
        {},
        {
          params: { tenantId },
          headers: { "X-Tenant-ID": tenantId },
        },
      );
      return AlertSchema.parse(response);
    } catch (error) {
      return normaliseApiError(error);
    }
  }

  /** Resolve an alert */
  async resolveAlert(alertId: string): Promise<Alert> {
    const tenantId = getTenantId();
    try {
      const response = await apiClient.post(
        `/alerts/${alertId}/resolve`,
        {},
        {
          params: { tenantId },
          headers: { "X-Tenant-ID": tenantId },
        },
      );
      return AlertSchema.parse(response);
    } catch (error) {
      return normaliseApiError(error);
    }
  }

  /** Get AI-detected correlated alert groups */
  async getAlertGroups(): Promise<AlertGroup[]> {
    const tenantId = getTenantId();
    try {
      const response = await apiClient.get("/alerts/groups", {
        params: { tenantId },
        headers: { "X-Tenant-ID": tenantId },
      });
      return AlertGroupListEnvelopeSchema.parse(response).groups;
    } catch (error) {
      return normaliseApiError(error);
    }
  }

  /** Auto-resolve a correlated alert group */
  async resolveGroup(groupId: string): Promise<void> {
    const tenantId = getTenantId();
    try {
      await apiClient.post(
        `/alerts/groups/${groupId}/resolve`,
        {},
        {
          params: { tenantId },
          headers: { "X-Tenant-ID": tenantId },
        },
      );
    } catch (error) {
      return normaliseApiError(error);
    }
  }

  /** Get AI resolution suggestions for active alerts */
  async getResolutionSuggestions(): Promise<ResolutionSuggestion[]> {
    const tenantId = getTenantId();
    try {
      const response = await apiClient.get("/alerts/suggestions", {
        params: { tenantId },
        headers: { "X-Tenant-ID": tenantId },
      });
      return ResolutionSuggestionEnvelopeSchema.parse(response).suggestions;
    } catch (error) {
      return normaliseApiError(error);
    }
  }

  /** Apply an AI resolution suggestion */
  async applySuggestion(suggestionId: string): Promise<void> {
    const tenantId = getTenantId();
    try {
      await apiClient.post(
        `/alerts/suggestions/${suggestionId}/apply`,
        {},
        {
          params: { tenantId },
          headers: { "X-Tenant-ID": tenantId },
        },
      );
    } catch (error) {
      return normaliseApiError(error);
    }
  }

  /** Open an SSE stream for live alert events */
  openStream(): EventSource {
    const tenantId = getTenantId();
    const query = new URLSearchParams({
      tenantId,
      types: ALERT_EVENT_TYPES.join(","),
    });
    const baseUrl = import.meta.env.VITE_API_URL ?? "/api/v1";
    return new EventSource(`${baseUrl}/alerts/stream?${query.toString()}`);
  }

  // ==================== Alert Rules (B5) ====================

  /** List all configured alert rules */
  async listAlertRules(): Promise<AlertRule[]> {
    const tenantId = getTenantId();
    try {
      const response = await apiClient.get("/alerts/rules", {
        params: { tenantId },
        headers: { "X-Tenant-ID": tenantId },
      });
      return AlertRuleListEnvelopeSchema.parse(response).rules;
    } catch (error) {
      return normaliseApiError(error);
    }
  }

  /** Create a new alert rule */
  async createAlertRule(rule: Omit<AlertRule, "id">): Promise<AlertRule> {
    const tenantId = getTenantId();
    try {
      const response = await apiClient.post("/alerts/rules", rule, {
        params: { tenantId },
        headers: { "X-Tenant-ID": tenantId },
      });
      return AlertRuleSchema.parse(response);
    } catch (error) {
      return normaliseApiError(error);
    }
  }

  /** Update an existing alert rule */
  async updateAlertRule(
    ruleId: string,
    rule: Partial<AlertRule>,
  ): Promise<AlertRule> {
    const tenantId = getTenantId();
    try {
      const response = await apiClient.put(`/alerts/rules/${ruleId}`, rule, {
        params: { tenantId },
        headers: { "X-Tenant-ID": tenantId },
      });
      return AlertRuleSchema.parse(response);
    } catch (error) {
      return normaliseApiError(error);
    }
  }

  /** Delete an alert rule */
  async deleteAlertRule(ruleId: string): Promise<void> {
    const tenantId = getTenantId();
    try {
      await apiClient.delete(`/alerts/rules/${ruleId}`, {
        params: { tenantId },
        headers: { "X-Tenant-ID": tenantId },
      });
    } catch (error) {
      return normaliseApiError(error);
    }
  }
}

export const alertsService = new AlertsService();

// =============================================================================
// REACT QUERY HOOKS
// =============================================================================

/** Aggregate counts of active alerts by severity for console dashboards. */
export interface AlertSummaryResult {
  total: number;
  critical: number;
  warning: number;
  info: number;
}

function summarizeAlerts(alerts: Alert[]): AlertSummaryResult {
  return alerts.reduce(
    (acc, alert) => {
      acc.total++;
      if (alert.severity === "critical") acc.critical++;
      if (alert.severity === "warning") acc.warning++;
      if (alert.severity === "info") acc.info++;
      return acc;
    },
    { total: 0, critical: 0, warning: 0, info: 0 },
  );
}

/**
 * React Query hook: fetch active alerts and return a severity summary.
 *
 * @returns `{ data: AlertSummaryResult, isLoading, error }`
 */
export function useAlertsSummary() {
  return useQuery<AlertSummaryResult>({
    queryKey: [
      "alerts-summary",
      SessionBootstrap.getTenantId() ?? "missing-tenant",
    ],
    queryFn: async () => {
      const alerts = await alertsService.getAlerts({ status: "active" });
      return summarizeAlerts(alerts);
    },
    staleTime: 30_000,
    refetchInterval: 60_000,
    refetchOnWindowFocus: false,
  });
}

export default alertsService;
