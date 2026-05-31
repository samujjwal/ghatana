/**
 * Runtime feature gates with production-safe defaults.
 *
 * Uses runtime-truth-derived capability service for dynamic capability resolution.
 * Allows environment fallback only for local/development profiles when runtime
 * truth is unavailable.
 *
 * @doc.type module
 * @doc.purpose Centralized runtime feature-gate policy
 * @doc.layer frontend
 * @doc.pattern Policy
 */

import { runtimeCapabilityService } from "./capabilities/RuntimeCapabilityService";

const TRUE_VALUES = new Set(["1", "true", "yes", "on"]);
const FALSE_VALUES = new Set(["0", "false", "no", "off"]);

function readBooleanEnv(key: string): boolean | undefined {
  const raw = (import.meta.env as Record<string, unknown>)[key];
  if (typeof raw !== "string") {
    return undefined;
  }

  const normalized = raw.trim().toLowerCase();
  if (TRUE_VALUES.has(normalized)) {
    return true;
  }
  if (FALSE_VALUES.has(normalized)) {
    return false;
  }
  return undefined;
}

function resolveRuntimeProfile(): string {
  const explicit = import.meta.env.VITE_DATACLOUD_PROFILE;
  if (typeof explicit === "string" && explicit.trim().length > 0) {
    return explicit.trim().toLowerCase();
  }
  return import.meta.env.MODE.trim().toLowerCase();
}

function isStrictProfile(profile: string): boolean {
  return (
    profile === "production" || profile === "staging" || profile === "sovereign"
  );
}

function resolveGate(key: string, defaultInNonStrict: boolean): boolean {
  const explicit = readBooleanEnv(key);
  if (typeof explicit === "boolean") {
    return explicit;
  }

  return isStrictProfile(resolveRuntimeProfile()) ? false : defaultInNonStrict;
}

function resolveRuntimeSurfaceGate(
  surfaceAlias: string,
  envKey: string,
  defaultInNonStrict: boolean,
): boolean {
  const state = runtimeCapabilityService.getState();
  if (state === "ready") {
    return runtimeCapabilityService.isCapabilityEnabled(surfaceAlias);
  }
  if (isStrictProfile(resolveRuntimeProfile())) {
    return false;
  }
  return resolveGate(envKey, defaultInNonStrict);
}

/**
 * Runtime-truth-derived capability checks.
 * These query the backend for dynamic capability flags.
 */
export function isAlertsSurfaceEnabled(): boolean {
  return resolveRuntimeSurfaceGate("alerts", "VITE_FEATURE_ALERTS", true);
}

export function isFabricSurfaceEnabled(): boolean {
  return resolveRuntimeSurfaceGate("fabric", "VITE_FEATURE_FABRIC", false);
}

export function isMemorySurfaceEnabled(): boolean {
  return resolveRuntimeSurfaceGate("memory", "VITE_FEATURE_MEMORY", true);
}

export function isEntityBrowserSurfaceEnabled(): boolean {
  return resolveRuntimeSurfaceGate(
    "entity-browser",
    "VITE_FEATURE_ENTITY_BROWSER",
    true,
  );
}

export function isContextSurfaceEnabled(): boolean {
  return resolveRuntimeSurfaceGate(
    "context-explorer",
    "VITE_FEATURE_CONTEXT_EXPLORER",
    true,
  );
}

export function isAgentCatalogSurfaceEnabled(): boolean {
  return resolveRuntimeSurfaceGate(
    "agent-catalog",
    "VITE_FEATURE_AGENT_CATALOG",
    true,
  );
}

export function isSettingsSurfaceEnabled(): boolean {
  return resolveRuntimeSurfaceGate("settings", "VITE_FEATURE_SETTINGS", true);
}

export function isMediaSurfaceEnabled(): boolean {
  return resolveRuntimeSurfaceGate("media", "VITE_FEATURE_MEDIA", false);
}

/**
 * Legacy environment-based gates (kept for backward compatibility).
 * These will be deprecated in favor of runtime-truth-derived capabilities.
 */
export function isAiOperationsEnabled(): boolean {
  return resolveGate("VITE_FEATURE_AI_OPERATIONS", true);
}

export function isAiAlertGroupingFallbackEnabled(): boolean {
  return resolveGate("VITE_FEATURE_AI_ALERT_GROUPING_FALLBACK", true);
}

export function isAnalyticsAiEnabled(): boolean {
  return resolveGate("VITE_FEATURE_ANALYTICS_AI", true);
}

export function isBrainAutonomyEnabled(): boolean {
  return resolveGate("VITE_FEATURE_BRAIN_AUTONOMY", true);
}
