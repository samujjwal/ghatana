/**
 * Runtime Truth surfaces client for Data Cloud.
 *
 * DC-P1-001: Canonical migration from /capabilities to /surfaces.
 * Normalizes backend capability values into the canonical SurfaceStatus
 * taxonomy (LIVE | DEGRADED | DISABLED | PREVIEW | UNAVAILABLE | MISCONFIGURED).
 *
 * @doc.type service
 * @doc.purpose Fetch and normalize runtime surface registry state
 * @doc.layer frontend
 */

import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { SurfaceRegistryEnvelopeSchema } from "../contracts/schemas";
import { apiClient } from "../lib/api/client";

// =============================================================================
// CANONICAL TYPES
// =============================================================================

/** Canonical surface status taxonomy — DC-P1-001. */
export type SurfaceStatus =
  | "LIVE"
  | "DEGRADED"
  | "DISABLED"
  | "PREVIEW"
  | "UNAVAILABLE"
  | "MISCONFIGURED";

export interface SurfaceSignal {
  readonly key: string;
  readonly label: string;
  readonly status: SurfaceStatus;
  readonly summary: string;
  readonly detail?: string;
  readonly ownerPlane: string;
  readonly requiredDependencies: readonly string[];
  readonly dependencyProbes: readonly Record<string, unknown>[];
  readonly tenantScope: string;
  readonly runtimeProfile: string;
  readonly limitations: string;
  readonly actionsAllowed: readonly string[];
  readonly runtimePosture?: Record<string, unknown>;
  readonly rawValue: unknown;
}

export interface SurfaceRegistrySnapshot {
  readonly generatedAt: string;
  readonly requestId: string;
  readonly tenantId: string;
  readonly surfaces: SurfaceSignal[];
}

export type CapabilityStatus =
  | "active"
  | "degraded"
  | "disabled"
  | "preview"
  | "unavailable"
  | "misconfigured";

export interface CapabilitySignal {
  readonly key: string;
  readonly label: string;
  readonly status: CapabilityStatus;
  readonly summary: string;
  readonly detail?: string;
  readonly rawValue: unknown;
}

export interface CapabilityRegistrySnapshot {
  readonly generatedAt: string;
  readonly requestId: string;
  readonly tenantId: string;
  readonly capabilities: CapabilitySignal[];
}

// =============================================================================
// NORMALIZATION
// =============================================================================

function formatSurfaceLabel(key: string): string {
  return key
    .replace(/[_.-]+/g, " ")
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

/** Map a raw capability value to the canonical SurfaceStatus taxonomy. */
function normalizeSurfaceStatus(rawValue: unknown): SurfaceStatus {
  if (typeof rawValue === "boolean") {
    return rawValue ? "LIVE" : "DISABLED";
  }

  if (typeof rawValue === "string") {
    const s = rawValue.trim().toUpperCase();
    // LIVE aliases
    if (
      [
        "LIVE",
        "ACTIVE",
        "ENABLED",
        "READY",
        "AVAILABLE",
        "HEALTHY",
        "OK",
        "PRODUCTION",
      ].includes(s)
    ) {
      return "LIVE";
    }
    // DEGRADED aliases
    if (["DEGRADED", "PARTIAL", "WARNING", "LIMITED"].includes(s)) {
      return "DEGRADED";
    }
    // PREVIEW aliases
    if (["PREVIEW", "DEMO", "BETA", "EXPERIMENTAL"].includes(s)) {
      return "PREVIEW";
    }
    // MISCONFIGURED aliases
    if (
      [
        "MISCONFIGURED",
        "NOT_CONFIGURED",
        "ERROR",
        "FAILED",
        "MISSING",
      ].includes(s)
    ) {
      return "MISCONFIGURED";
    }
    // DISABLED aliases
    if (["DISABLED", "INACTIVE", "OFFLINE"].includes(s)) {
      return "DISABLED";
    }
    // UNAVAILABLE aliases
    if (["UNAVAILABLE", "UNKNOWN"].includes(s)) {
      return "UNAVAILABLE";
    }
  }

  if (typeof rawValue === "object" && rawValue !== null) {
    const record = rawValue as Record<string, unknown>;
    const nestedStatus = record["status"] ?? record["state"] ?? record["mode"];
    if (nestedStatus != null) {
      return normalizeSurfaceStatus(nestedStatus);
    }
    const availability =
      record["available"] ?? record["enabled"] ?? record["healthy"];
    if (typeof availability === "boolean") {
      return availability ? "LIVE" : "DISABLED";
    }
  }

  return "UNAVAILABLE";
}

function summarizeSurface(
  status: SurfaceStatus,
  rawValue: unknown,
): { summary: string; detail?: string } {
  if (typeof rawValue === "string") {
    return { summary: rawValue };
  }
  if (typeof rawValue === "object" && rawValue !== null) {
    const record = rawValue as Record<string, unknown>;
    const reason = record["reason"];
    const message = record["message"];
    const detail =
      typeof reason === "string"
        ? reason
        : typeof message === "string"
          ? message
          : undefined;
    const rawStatus = record["status"];
    return {
      summary: typeof rawStatus === "string" ? rawStatus : status,
      detail,
    };
  }
  return { summary: status };
}

function normalizeSurfaceEntry(key: string, rawValue: unknown): SurfaceSignal {
  const status = normalizeSurfaceStatus(rawValue);
  const { summary, detail } = summarizeSurface(status, rawValue);
  const record =
    typeof rawValue === "object" && rawValue !== null
      ? (rawValue as Record<string, unknown>)
      : {};
  const requiredDependencies = Array.isArray(record.requiredDependencies)
    ? record.requiredDependencies.filter(
        (dependency): dependency is string => typeof dependency === "string",
      )
    : [];
  const dependencyProbes = Array.isArray(record.dependencyProbes)
    ? record.dependencyProbes.filter(
        (probe): probe is Record<string, unknown> =>
          typeof probe === "object" && probe !== null,
      )
    : [];
  const actionsAllowed = Array.isArray(record.actionsAllowed)
    ? record.actionsAllowed.filter(
        (action): action is string => typeof action === "string",
      )
    : [];
  const runtimePosture =
    typeof record.runtimePosture === "object" && record.runtimePosture !== null
      ? (record.runtimePosture as Record<string, unknown>)
      : undefined;

  return {
    key,
    label: formatSurfaceLabel(key),
    status,
    summary,
    detail,
    ownerPlane:
      typeof record.ownerPlane === "string" ? record.ownerPlane : "unknown",
    requiredDependencies,
    dependencyProbes,
    tenantScope:
      typeof record.tenantScope === "string" ? record.tenantScope : "global",
    runtimeProfile:
      typeof record.runtimeProfile === "string"
        ? record.runtimeProfile
        : "unknown",
    limitations:
      typeof record.limitations === "string" ? record.limitations : "",
    actionsAllowed,
    runtimePosture,
    rawValue,
  };
}

// =============================================================================
// API — canonical endpoint: /surfaces only (DC-P1.12: removed /capabilities fallback)
// =============================================================================

export async function fetchSurfaceRegistry(): Promise<SurfaceRegistrySnapshot> {
  // DC-P1.12: Use canonical /surfaces endpoint only; /capabilities compatibility alias removed.
  // Backend returns surfaces as an array of SurfaceRecord objects (P0 fix).
  const rawResponse = await apiClient.get<unknown>("/surfaces");
  const envelope = SurfaceRegistryEnvelopeSchema.parse(rawResponse);
  const surfaces = envelope.data.surfaces
    .map((record) => normalizeSurfaceEntry(record.surfaceId, record))
    .sort((a, b) => a.label.localeCompare(b.label));
  return {
    generatedAt: envelope.data.generatedAt,
    requestId: envelope.meta.requestId,
    tenantId: envelope.meta.tenantId,
    surfaces,
  };
}

function toCapabilityStatus(status: SurfaceStatus): CapabilityStatus {
  switch (status) {
    case "LIVE":
      return "active";
    case "DEGRADED":
      return "degraded";
    case "DISABLED":
      return "disabled";
    case "PREVIEW":
      return "preview";
    case "MISCONFIGURED":
      return "unavailable";
    case "UNAVAILABLE":
      return "unavailable";
  }
}

function toCapabilitySignal(signal: SurfaceSignal): CapabilitySignal {
  return {
    ...signal,
    status: toCapabilityStatus(signal.status),
  };
}

export async function fetchCapabilityRegistry(): Promise<CapabilityRegistrySnapshot> {
  const snapshot = await fetchSurfaceRegistry();
  return {
    generatedAt: snapshot.generatedAt,
    requestId: snapshot.requestId,
    tenantId: snapshot.tenantId,
    capabilities: snapshot.surfaces.map(toCapabilitySignal),
  };
}

export function useSurfaceRegistry(): UseQueryResult<
  SurfaceRegistrySnapshot,
  Error
> {
  return useQuery({
    queryKey: ["surface-registry"],
    queryFn: fetchSurfaceRegistry,
    staleTime: 60_000,
    refetchInterval: 60_000,
    refetchOnWindowFocus: false,
  });
}

/** Find a surface signal by any of its aliases. */
const SURFACE_ALIASES: Readonly<Record<string, readonly string[]>> = {
  alerts: ["alert-triage", "monitoring"],
  "media.audioVideo": ["media", "media-artifacts", "audio-video"],
  "data.connectors": ["data-connectors", "connectors", "external-data-sources"],
  "action.agentRuntime": ["agent-catalog", "agents"],
  "event.store": ["event-stream", "event-explorer", "events", "aep"],
  "context.plane": ["context", "context-explorer"],
  "data.entityStore": ["entity-browser", "entities", "data-explorer"],
  "data.storageProfiles": ["data-fabric", "fabric"],
  "runtime.truth.read": ["runtime-truth"],
  "plugin-management": ["plugins", "extensions"],
};

function normalizeSurfaceLookupKey(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[_\s]+/g, "-");
}

function expandAliases(aliases: readonly string[]): Set<string> {
  const normalized = new Set<string>();
  for (const alias of aliases) {
    normalized.add(normalizeSurfaceLookupKey(alias));
    for (const [surfaceId, surfaceAliases] of Object.entries(SURFACE_ALIASES)) {
      const candidates = [surfaceId, ...surfaceAliases];
      if (
        candidates.some(
          (candidate) =>
            normalizeSurfaceLookupKey(candidate) ===
            normalizeSurfaceLookupKey(alias),
        )
      ) {
        for (const candidate of candidates) {
          normalized.add(normalizeSurfaceLookupKey(candidate));
        }
      }
    }
  }
  return normalized;
}

export function getSurfaceSignal(
  surfaces: readonly SurfaceSignal[] | undefined,
  aliases: readonly string[],
): SurfaceSignal | undefined {
  if (!surfaces) return undefined;
  const normalized = expandAliases(aliases);
  return surfaces.find((s) => normalized.has(normalizeSurfaceLookupKey(s.key)));
}

export function getCapabilitySignal(
  capabilities: CapabilitySignal[] | undefined,
  aliases: readonly string[],
): CapabilitySignal | undefined {
  if (!capabilities) return undefined;
  const normalized = aliases.map((a) => a.toLowerCase());
  return capabilities.find((capability) =>
    normalized.includes(capability.key.toLowerCase()),
  );
}

/** Return true if the surface is considered usable (LIVE, DEGRADED, or PREVIEW). */
export function isSurfaceAvailable(signal: SurfaceSignal | undefined): boolean {
  if (!signal) return false;
  return (
    signal.status === "LIVE" ||
    signal.status === "DEGRADED" ||
    signal.status === "PREVIEW"
  );
}
