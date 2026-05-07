/**
 * Feature flag configuration for AEP.
 *
 * @doc.type config
 * @doc.purpose Control feature visibility via flags
 * @doc.layer frontend
 */

/**
 * Feature flag safety classification
 *
 * - GA (General Availability): on by default; disable only when explicitly set "false"
 * - EXPERIMENTAL: off by default; enable only when explicitly set "true"
 * - SAFETY: off by default; enable only when explicitly set "true"
 * - ADMIN: off by default; enable only when explicitly set "true"
 */
type FlagClass = 'GA' | 'EXPERIMENTAL' | 'SAFETY' | 'ADMIN';

const FLAG_CLASSES: Record<FeatureFlag, FlagClass> = {
  // Run detail tabs — GA
  EVENT_LINEAGE: 'GA',
  AGENT_DECISIONS: 'GA',
  POLICY_REFERENCES: 'GA',

  // Governance sections — GA (operators need safety controls)
  COMPLIANCE_REPORTS: 'GA',
  TENANT_MANAGEMENT: 'GA',
  AUDIT_LOG: 'GA',

  // Voice/NLP — GA
  NLQ: 'GA',
  // Voice commands — EXPERIMENTAL (not yet fully validated)
  VOICE_COMMANDS: 'EXPERIMENTAL',

  // AI features — GA
  AI_SUGGESTIONS: 'GA',
  ANOMALY_DETECTION: 'GA',
  // Smart prioritization — EXPERIMENTAL
  SMART_PRIORITIZATION: 'EXPERIMENTAL',

  // Bulk operations — SAFETY (can affect many runs)
  BULK_OPERATIONS: 'SAFETY',

  // Advanced features — ADMIN (power-user only)
  COMMAND_PALETTE: 'ADMIN',
  BREADCRUMBS: 'ADMIN',

  // Auth migration — SAFETY (legacy fallback)
  LEGACY_JWT_PASTE: 'SAFETY',
};

function resolveFlag(flag: FeatureFlag): boolean {
  const cls = FLAG_CLASSES[flag];
  const raw = import.meta.env[`VITE_FEATURE_${flag}`];
  const explicitValue = raw === 'true' || raw === 'false' ? raw === 'true' : undefined;

  switch (cls) {
    case 'GA':
      return explicitValue ?? true;
    case 'EXPERIMENTAL':
    case 'SAFETY':
    case 'ADMIN':
      return explicitValue ?? false;
    default:
      return false;
  }
}

/**
 * Feature flag configuration
 *
 * Flags are resolved by safety class. GA defaults on; EXPERIMENTAL/SAFETY/ADMIN
 * default off. All can be overridden via VITE_FEATURE_<FLAG_NAME> env vars.
 */
export const featureFlags: Record<FeatureFlag, boolean> = {
  // Run detail tabs
  EVENT_LINEAGE: resolveFlag('EVENT_LINEAGE'),
  AGENT_DECISIONS: resolveFlag('AGENT_DECISIONS'),
  POLICY_REFERENCES: resolveFlag('POLICY_REFERENCES'),

  // Governance sections
  COMPLIANCE_REPORTS: resolveFlag('COMPLIANCE_REPORTS'),
  TENANT_MANAGEMENT: resolveFlag('TENANT_MANAGEMENT'),
  AUDIT_LOG: resolveFlag('AUDIT_LOG'),

  // Voice/NLP
  VOICE_COMMANDS: resolveFlag('VOICE_COMMANDS'),
  NLQ: resolveFlag('NLQ'),

  // AI features
  AI_SUGGESTIONS: resolveFlag('AI_SUGGESTIONS'),
  SMART_PRIORITIZATION: resolveFlag('SMART_PRIORITIZATION'),
  ANOMALY_DETECTION: resolveFlag('ANOMALY_DETECTION'),

  // Bulk operations — SAFETY class
  BULK_OPERATIONS: resolveFlag('BULK_OPERATIONS'),

  // Advanced features — ADMIN class
  COMMAND_PALETTE: resolveFlag('COMMAND_PALETTE'),
  BREADCRUMBS: resolveFlag('BREADCRUMBS'),

  // Auth migration — SAFETY class
  LEGACY_JWT_PASTE: resolveFlag('LEGACY_JWT_PASTE'),
};

/**
 * Feature flag type
 */
export type FeatureFlag =
  | 'EVENT_LINEAGE'
  | 'AGENT_DECISIONS'
  | 'POLICY_REFERENCES'
  | 'COMPLIANCE_REPORTS'
  | 'TENANT_MANAGEMENT'
  | 'AUDIT_LOG'
  | 'VOICE_COMMANDS'
  | 'NLQ'
  | 'AI_SUGGESTIONS'
  | 'SMART_PRIORITIZATION'
  | 'ANOMALY_DETECTION'
  | 'BULK_OPERATIONS'
  | 'COMMAND_PALETTE'
  | 'BREADCRUMBS'
  | 'LEGACY_JWT_PASTE';

/**
 * Check if a feature flag is enabled
 *
 * @param flag - The feature flag to check
 * @returns true if the feature is enabled, false otherwise
 */
export function isFeatureEnabled(flag: FeatureFlag): boolean {
  return featureFlags[flag];
}

/**
 * Get all enabled feature flags
 *
 * @returns Array of enabled feature flag names
 */
export function getEnabledFeatures(): FeatureFlag[] {
  return Object.entries(featureFlags)
    .filter(([_, enabled]) => enabled)
    .map(([name]) => name as FeatureFlag);
}

/**
 * Get all disabled feature flags
 *
 * @returns Array of disabled feature flag names
 */
export function getDisabledFeatures(): FeatureFlag[] {
  return Object.entries(featureFlags)
    .filter(([_, enabled]) => !enabled)
    .map(([name]) => name as FeatureFlag);
}

/**
 * Feature flag hook for React components
 */
export function useFeatureFlag(flag: FeatureFlag): boolean {
  return featureFlags[flag];
}

/**
 * Hook for multiple feature flags
 */
export function useFeatureFlags(
  flags: FeatureFlag[],
): Record<FeatureFlag, boolean> {
  return flags.reduce(
    (acc, flag) => {
      acc[flag] = featureFlags[flag];
      return acc;
    },
    {} as Record<FeatureFlag, boolean>,
  );
}
