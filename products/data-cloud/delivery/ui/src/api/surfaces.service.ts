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

/** P5-03: Readiness class for surface availability */
export type ReadinessClass =
  | "user-ready"
  | "operator-preview"
  | "internal-preview"
  | "target-only";

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
  // P5-03: Preview audience for controlled access
  readonly audience?: "internal" | "operator" | "admin";
  // P5-03: Readiness class for surface categorization
  readonly readinessClass?: ReadinessClass;
  // P5-03: Target-only flag
  readonly targetOnly?: boolean;
  // WS1: UI-specific fields from enriched SurfaceRecord
  readonly path?: string;
  readonly labelKey?: string;
  readonly description?: string;
  readonly descriptionKey?: string;
  readonly iconName?: string;
  readonly minimumShellRole?: string;
  readonly discoverable?: boolean;
  readonly lifecycle?: string;
  readonly previewAudience?: string;
  readonly routeGroup?: string;
  readonly sortOrder?: number;
  readonly primaryNavigation?: boolean;
  readonly contextualNavigation?: boolean;
  readonly fallbackReason?: string;
  readonly recommendedAction?: string;
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
        "USER-READY",
      ].includes(s)
    ) {
      return "LIVE";
    }
    // DEGRADED aliases
    if (["DEGRADED", "PARTIAL", "WARNING", "LIMITED"].includes(s)) {
      return "DEGRADED";
    }
    // PREVIEW aliases - P5-03: Now includes operator-preview and internal-preview
    if (["PREVIEW", "DEMO", "BETA", "EXPERIMENTAL", "OPERATOR-PREVIEW", "INTERNAL-PREVIEW"].includes(s)) {
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
    // P5-03: TARGET-ONLY surfaces are considered unavailable for general use
    if (["TARGET-ONLY"].includes(s)) {
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
    // P5-03: Check for targetOnly flag
    if (record["targetOnly"] === true || record["targetOnly"] === "true") {
      return "UNAVAILABLE";
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

function normalizeReadinessClass(
  record: Record<string, unknown>,
  status: SurfaceStatus
): ReadinessClass | undefined {
  // P5-03: Extract readiness class from record
  const readiness = record["readinessClass"] ?? record["readiness"];
  if (typeof readiness === "string") {
    if (["user-ready", "operator-preview", "internal-preview", "target-only"].includes(readiness)) {
      return readiness as ReadinessClass;
    }
  }
  // Infer from status if not explicitly set
  if (status === "LIVE") return "user-ready";
  if (record["targetOnly"] === true) return "target-only";
  return undefined;
}

function normalizeAudience(
  record: Record<string, unknown>
): "internal" | "operator" | "admin" | undefined {
  // P5-03: Extract audience from record
  const audience = record["audience"] ?? record["previewAudience"];
  if (typeof audience === "string") {
    if (["internal", "operator", "admin"].includes(audience)) {
      return audience as "internal" | "operator" | "admin";
    }
  }
  return undefined;
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

  // P5-03: Normalize targetOnly, audience, and readinessClass
  const targetOnly = record["targetOnly"] === true || record["targetOnly"] === "true";
  const audience = normalizeAudience(record);
  const readinessClass = normalizeReadinessClass(record, status);

  // P5-03: Combine limitations from multiple sources
  const limitations = [
    typeof record.limitations === "string" ? record.limitations : "",
    targetOnly ? "Target-only surface - not yet generally available" : "",
  ].filter(Boolean).join("; ");

  // WS1: Extract UI-specific fields from enriched SurfaceRecord
  const path = typeof record.path === "string" ? record.path : undefined;
  const labelKey = typeof record.labelKey === "string" ? record.labelKey : undefined;
  const description = typeof record.description === "string" ? record.description : undefined;
  const descriptionKey = typeof record.descriptionKey === "string" ? record.descriptionKey : undefined;
  const iconName = typeof record.iconName === "string" ? record.iconName : undefined;
  const minimumShellRole = typeof record.minimumShellRole === "string" ? record.minimumShellRole : "viewer";
  const discoverable = typeof record.discoverable === "boolean" ? record.discoverable : true;
  const lifecycle = typeof record.lifecycle === "string" ? record.lifecycle : "stable";
  const previewAudience = typeof record.previewAudience === "string" ? record.previewAudience : undefined;
  const routeGroup = typeof record.routeGroup === "string" ? record.routeGroup : undefined;
  const sortOrder = typeof record.sortOrder === "number" ? record.sortOrder : 0;
  const primaryNavigation = typeof record.primaryNavigation === "boolean" ? record.primaryNavigation : false;
  const contextualNavigation = typeof record.contextualNavigation === "boolean" ? record.contextualNavigation : false;
  const fallbackReason = typeof record.fallbackReason === "string" ? record.fallbackReason : undefined;
  const recommendedAction = typeof record.recommendedAction === "string" ? record.recommendedAction : undefined;

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
    limitations,
    actionsAllowed,
    runtimePosture,
    rawValue,
    // P5-03: New fields
    audience,
    readinessClass,
    targetOnly,
    // WS1: UI-specific fields
    path,
    labelKey,
    description,
    descriptionKey,
    iconName,
    minimumShellRole,
    discoverable,
    lifecycle,
    previewAudience,
    routeGroup,
    sortOrder,
    primaryNavigation,
    contextualNavigation,
    fallbackReason,
    recommendedAction,
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

/**
 * P5-03: Return true if the surface is considered usable.
 * PREVIEW is NOT globally available - requires explicit preview audience match.
 */
export function isSurfaceAvailable(
  signal: SurfaceSignal | undefined,
  options?: { allowPreview?: boolean; previewAudience?: "internal" | "operator" | "admin" }
): boolean {
  if (!signal) return false;

  // LIVE and DEGRADED are always available
  if (signal.status === "LIVE" || signal.status === "DEGRADED") {
    return true;
  }

  // P5-03: PREVIEW requires explicit opt-in and audience match
  if (signal.status === "PREVIEW") {
    if (!options?.allowPreview) return false;
    // If preview audience specified, check match
    if (options?.previewAudience && signal.audience) {
      return signal.audience === options.previewAudience ||
        // Admin can access any preview
        options.previewAudience === "admin";
    }
    return true;
  }

  // P5-03: TARGET-ONLY surfaces are never generally available
  if (signal.targetOnly || signal.readinessClass === "target-only") {
    return false;
  }

  return false;
}
