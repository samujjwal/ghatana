/**
 * Unified operations/job API service.
 *
 * @doc.type service
 * @doc.purpose Fetch product-wide Data Cloud operation lifecycle records
 * @doc.layer product
 * @doc.pattern Repository
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../lib/api/client";

export type OperationStatus =
  | "ACCEPTED"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELLED"
  | "BLOCKED";

export type OperationKind =
  | "CONNECTOR_SYNC"
  | "CONNECTOR_TEST"
  | "CONNECTOR_SCHEMA"
  | "CONNECTOR_HEALTH"
  | "CONNECTOR_CREDENTIAL_ROTATION"
  | "MEDIA_PROCESSING"
  | "MEDIA_RETENTION"
  | "MEDIA_DELETE"
  | "PIPELINE_EXECUTION"
  | "PIPELINE_CANCEL"
  | "PIPELINE_RETRY"
  | "PIPELINE_ROLLBACK"
  | "PIPELINE_CHECKPOINT"
  | "PIPELINE_RESTORE"
  | "AGENT_RUN"
  | "AEP_PATTERN_RUN"
  | "BACKGROUND_TASK";

export interface OperationJob {
  operationId: string;
  tenantId: string;
  kind: OperationKind;
  status: OperationStatus;
  resourceType: string;
  resourceId: string;
  action: string;
  summary: string;
  detail: string;
  actorId: string;
  correlationId: string;
  createdAt: string;
  updatedAt: string;
  completedAt: string;
  cancellable: boolean;
  metadata: Record<string, unknown>;
}

export interface OperationTimelineResponse {
  tenantId: string;
  items: OperationJob[];
  count: number;
  storageMode: "volatile" | "durable";
  generatedAt: string;
}

function normalizeOperationJob(value: unknown): OperationJob | null {
  if (!value || typeof value !== "object") return null;
  const record = value as Record<string, unknown>;
  const operationId = String(record.operationId ?? "");
  const action = String(record.action ?? "");
  const status = String(record.status ?? "");
  const kind = String(record.kind ?? "");
  if (!operationId || !action || !status || !kind) return null;

  return {
    operationId,
    tenantId: String(record.tenantId ?? ""),
    kind: kind as OperationKind,
    status: status as OperationStatus,
    resourceType: String(record.resourceType ?? ""),
    resourceId: String(record.resourceId ?? ""),
    action,
    summary: String(record.summary ?? ""),
    detail: String(record.detail ?? ""),
    actorId: String(record.actorId ?? ""),
    correlationId: String(record.correlationId ?? ""),
    createdAt: String(record.createdAt ?? new Date(0).toISOString()),
    updatedAt: String(record.updatedAt ?? record.createdAt ?? new Date(0).toISOString()),
    completedAt: String(record.completedAt ?? ""),
    cancellable: Boolean(record.cancellable),
    metadata:
      record.metadata && typeof record.metadata === "object"
        ? (record.metadata as Record<string, unknown>)
        : {},
  };
}

function normalizeTimeline(value: unknown): OperationTimelineResponse {
  const record =
    value && typeof value === "object" ? (value as Record<string, unknown>) : {};
  const rawItems = Array.isArray(record.items) ? record.items : [];
  const items = rawItems
    .map(normalizeOperationJob)
    .filter((item): item is OperationJob => item !== null);
  return {
    tenantId: String(record.tenantId ?? ""),
    items,
    count:
      typeof record.count === "number" && Number.isFinite(record.count)
        ? record.count
        : items.length,
    storageMode: record.storageMode === "durable" ? "durable" : "volatile",
    generatedAt: String(record.generatedAt ?? new Date().toISOString()),
  };
}

export async function fetchOperationTimeline(
  limit = 100,
): Promise<OperationTimelineResponse> {
  const response = await apiClient.get<unknown>(
    `/api/v1/operations/jobs?limit=${limit}`,
    { skipCache: true },
  );
  return normalizeTimeline(response);
}

export function useOperationTimeline(limit = 100) {
  return useQuery({
    queryKey: ["operations", "jobs", limit],
    queryFn: () => fetchOperationTimeline(limit),
    refetchInterval: 10000,
  });
}
