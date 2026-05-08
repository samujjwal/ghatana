/**
 * Generation Request Mappers
 *
 * Shared mapper functions for generation requests and jobs.
 * Consolidates duplicate mapper logic from planner-service and execution-service.
 *
 * @doc.type module
 * @doc.purpose Shared mapper functions for generation requests and jobs
 * @doc.layer product
 * @doc.pattern Module
 */

import type { RiskLevel } from "@tutorputor/contracts/v1/types";
import {
  type GenerationCostEstimate,
  type GenerationJob,
  type GenerationJobStatus,
  type GenerationJobType,
  type GenerationRequest,
  type GenerationRequestStatus,
  type PlannedAssetDescriptor,
  type ReviewPath,
} from "../types.js";

/**
 * Type-safe helper to convert unknown to string or null.
 */
export function asString(value: unknown): string | null {
  return typeof value === "string" ? value : null;
}

/**
 * Type-safe helper to convert unknown to number or null.
 */
export function asNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

/**
 * Type-safe helper to convert unknown to record or null.
 */
export function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

/**
 * Type-safe helper to convert unknown to string array.
 */
export function asStringArray(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((entry): entry is string => typeof entry === "string")
    : [];
}

/**
 * Convert unknown to ISO date string or null.
 */
export function toIso(value: unknown): string | null {
  if (value instanceof Date) {
    return value.toISOString();
  }
  if (typeof value === "string") {
    return value;
  }
  return null;
}

/**
 * Convert status string to uppercase for database storage.
 */
export function statusToDb(value: string): string {
  return value.toUpperCase();
}

/**
 * Convert job type string to uppercase for database storage.
 */
export function jobTypeToDb(value: string): string {
  return value.toUpperCase();
}

/**
 * Convert risk level string to uppercase for database storage.
 */
export function riskLevelToDb(value: string): string {
  return value.toUpperCase();
}

/**
 * Normalize domain string to lowercase.
 */
export function normalizeDomain(value: string | undefined | null): string {
  return value ? value.toLowerCase() : "";
}

/**
 * Normalize grade string to lowercase.
 */
export function normalizeGrade(value: string): string {
  return value.toLowerCase();
}

/**
 * Convert review path string to enum value.
 */
export function enumToReviewPath(value: string | null): ReviewPath {
  if (!value) return "human_review";
  const normalized = value.toLowerCase();
  switch (normalized) {
    case "expert_review":
      return "expert_review";
    case "human_review":
      return "human_review";
    case "auto_publish":
      return "auto_publish";
    default:
      return "human_review";
  }
}

/**
 * Convert status string to enum value.
 */
export function enumToRequestStatus(value: string): GenerationRequestStatus {
  const normalized = value.toLowerCase();
  switch (normalized) {
    case "draft":
      return "draft";
    case "planning":
      return "planning";
    case "planned":
      return "planned";
    case "failed_planning":
      return "failed_planning";
    case "executing":
    case "in_progress":
      return "executing";
    case "completed":
      return "completed";
    case "failed":
      return "failed";
    case "cancelled":
      return "cancelled";
    default:
      return "draft";
  }
}

/**
 * Convert risk level string to enum value.
 */
export function enumToRiskLevel(value: string): RiskLevel {
  const normalized = value.toLowerCase();
  switch (normalized) {
    case "critical":
      return "critical";
    case "high":
      return "high";
    case "medium":
      return "medium";
    case "low":
      return "low";
    default:
      return "low";
  }
}

/**
 * Convert job status string to enum value.
 */
export function enumToJobStatus(value: string): GenerationJobStatus {
  const normalized = value.toLowerCase();
  switch (normalized) {
    case "pending":
    case "queued":
      return "pending";
    case "running":
      return "running";
    case "completed":
      return "completed";
    case "failed":
      return "failed";
    case "cancelled":
      return "cancelled";
    default:
      return "pending";
  }
}

/**
 * Map a database row to a GenerationRequest.
 */
export function mapRequest(row: Record<string, unknown>): GenerationRequest {
  return {
    id: asString(row.id) ?? "",
    tenantId: asString(row.tenantId) ?? "",
    title: asString(row.title) ?? "",
    ...(asString(row.description) ? { description: row.description as string } : {}),
    domain: asString(row.domain) ?? "",
    ...(asString(row.conceptId) ? { conceptId: row.conceptId as string } : {}),
    ...(Array.isArray(row.targetGrades)
      ? { targetGrades: asStringArray(row.targetGrades) }
      : {}),
    requestedBy: asString(row.requestedBy) ?? "",
    status: enumToRequestStatus(asString(row.status) ?? "draft"),
    riskLevel: enumToRiskLevel(asString(row.riskLevel) ?? "low"),
    reviewPath: enumToReviewPath(asString(row.reviewPath)),
    totalJobs: asNumber(row.totalJobs) ?? 0,
    completedJobs: asNumber(row.completedJobs) ?? 0,
    failedJobs: asNumber(row.failedJobs) ?? 0,
    ...(Array.isArray(row.plannedAssets)
      ? { plannedAssets: row.plannedAssets as PlannedAssetDescriptor[] }
      : {}),
    ...(asRecord(row.artifactNeeds)
      ? { artifactNeeds: asRecord(row.artifactNeeds) as Record<string, number> }
      : {}),
    ...(Array.isArray(row.riskFactors)
      ? { riskFactors: asStringArray(row.riskFactors) }
      : {}),
    ...(asRecord(row.estimatedCost)
      ? { estimatedCost: asRecord(row.estimatedCost) as unknown as GenerationCostEstimate }
      : {}),
    ...(toIso(row.plannedAt) ? { plannedAt: toIso(row.plannedAt) as string } : {}),
    ...(toIso(row.startedAt) ? { startedAt: toIso(row.startedAt) as string } : {}),
    ...(toIso(row.completedAt) ? { completedAt: toIso(row.completedAt) as string } : {}),
    createdAt: toIso(row.createdAt) ?? new Date().toISOString(),
    updatedAt: toIso(row.updatedAt) ?? new Date().toISOString(),
  };
}

/**
 * Map a database row to a GenerationJob.
 */
export function mapJob(row: Record<string, unknown>): GenerationJob {
  return {
    id: asString(row.id) ?? "",
    requestId: asString(row.requestId) ?? "",
    jobType: (asString(row.jobType) ?? "claim") as GenerationJobType,
    status: enumToJobStatus(asString(row.status) ?? "queued"),
    progress: asNumber(row.progress) ?? 0,
    retryCount: asNumber(row.retryCount) ?? 0,
    maxRetries: asNumber(row.maxRetries) ?? 3,
    ...(asString(row.targetRef) ? { targetRef: row.targetRef as string } : {}),
    ...(asString(row.inputPrompt) ? { inputPrompt: row.inputPrompt as string } : {}),
    ...(asRecord(row.parameters) ? { parameters: asRecord(row.parameters) as Record<string, unknown> } : {}),
    ...(asString(row.outputAssetId) ? { outputAssetId: row.outputAssetId as string } : {}),
    ...(asRecord(row.outputData) ? { outputData: asRecord(row.outputData) as Record<string, unknown> } : {}),
    ...(asRecord(row.diagnostics) ? { diagnostics: asRecord(row.diagnostics) as Record<string, unknown> } : {}),
    ...(asString(row.errorMessage) ? { errorMessage: row.errorMessage as string } : {}),
    ...(toIso(row.startedAt) ? { startedAt: toIso(row.startedAt) as string } : {}),
    ...(toIso(row.completedAt) ? { completedAt: toIso(row.completedAt) as string } : {}),
    createdAt: toIso(row.createdAt) ?? new Date().toISOString(),
    updatedAt: toIso(row.updatedAt) ?? new Date().toISOString(),
  };
}
