/**
 * Runtime feature gates with production-safe defaults.
 *
 * In production-like profiles, optional and preview features are disabled
 * unless explicitly enabled via environment flags.
 *
 * @doc.type module
 * @doc.purpose Centralized runtime feature-gate policy
 * @doc.layer frontend
 * @doc.pattern Policy
 */

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
  return profile === "production" || profile === "staging";
}

function resolveGate(key: string, defaultInNonStrict: boolean): boolean {
  const explicit = readBooleanEnv(key);
  if (typeof explicit === "boolean") {
    return explicit;
  }

  return isStrictProfile(resolveRuntimeProfile()) ? false : defaultInNonStrict;
}

export function isAlertsSurfaceEnabled(): boolean {
  return resolveGate("VITE_FEATURE_ALERTS", true);
}

export function isFabricSurfaceEnabled(): boolean {
  // Data Fabric is a preview capability and disabled by default in production
  // Requires explicit VITE_FEATURE_FABRIC=true even in non-production profiles
  const explicit = readBooleanEnv("VITE_FEATURE_FABRIC");
  if (typeof explicit === "boolean") {
    return explicit;
  }
  // Default to disabled in all profiles unless explicitly enabled
  return false;
}

export function isAiOperationsEnabled(): boolean {
  return resolveGate("VITE_FEATURE_AI_OPERATIONS", true);
}

export function isAiAlertGroupingFallbackEnabled(): boolean {
  return resolveGate("VITE_FEATURE_AI_ALERT_GROUPING_FALLBACK", true);
}

export function isMemorySurfaceEnabled(): boolean {
  return resolveGate("VITE_FEATURE_MEMORY", true);
}

export function isEntityBrowserSurfaceEnabled(): boolean {
  return resolveGate("VITE_FEATURE_ENTITY_BROWSER", true);
}

export function isContextSurfaceEnabled(): boolean {
  return resolveGate("VITE_FEATURE_CONTEXT_EXPLORER", true);
}

export function isAgentCatalogSurfaceEnabled(): boolean {
  return resolveGate("VITE_FEATURE_AGENT_CATALOG", true);
}

export function isSettingsSurfaceEnabled(): boolean {
  return resolveGate("VITE_FEATURE_SETTINGS", true);
}

export function isAnalyticsAiEnabled(): boolean {
  return resolveGate("VITE_FEATURE_ANALYTICS_AI", true);
}

export function isBrainAutonomyEnabled(): boolean {
  return resolveGate("VITE_FEATURE_BRAIN_AUTONOMY", true);
}
