/**
 * Feature flag configuration for AEP.
 *
 * @doc.type config
 * @doc.purpose Control feature visibility via flags
 * @doc.layer frontend
 */

/**
 * Feature flag configuration
 * 
 * All flags default to false for safety. Features must be explicitly
 * enabled via environment variables or configuration.
 */
export const featureFlags = {
  // Run detail tabs
  EVENT_LINEAGE: import.meta.env.VITE_FEATURE_EVENT_LINEAGE === 'true',
  AGENT_DECISIONS: import.meta.env.VITE_FEATURE_AGENT_DECISIONS === 'true',
  POLICY_REFERENCES: import.meta.env.VITE_FEATURE_POLICY_REFERENCES === 'true',
  
  // Governance sections
  COMPLIANCE_REPORTS: import.meta.env.VITE_FEATURE_COMPLIANCE_REPORTS === 'true',
  TENANT_MANAGEMENT: import.meta.env.VITE_FEATURE_TENANT_MANAGEMENT === 'true',
  AUDIT_LOG: import.meta.env.VITE_FEATURE_AUDIT_LOG === 'true',
  
  // Voice/NLP features
  VOICE_COMMANDS: import.meta.env.VITE_FEATURE_VOICE_COMMANDS === 'true',
  NLQ: import.meta.env.VITE_FEATURE_NLQ === 'true',
  
  // AI features
  AI_SUGGESTIONS: import.meta.env.VITE_FEATURE_AI_SUGGESTIONS === 'true',
  SMART_PRIORITIZATION: import.meta.env.VITE_FEATURE_SMART_PRIORITIZATION === 'true',
  ANOMALY_DETECTION: import.meta.env.VITE_FEATURE_ANOMALY_DETECTION === 'true',
  
  // Bulk operations
  BULK_OPERATIONS: import.meta.env.VITE_FEATURE_BULK_OPERATIONS === 'true',
  
  // Advanced features
  COMMAND_PALETTE: import.meta.env.VITE_FEATURE_COMMAND_PALETTE === 'true',
  BREADCRUMBS: import.meta.env.VITE_FEATURE_BREADCRUMBS === 'true',
} as const;

/**
 * Feature flag type
 */
export type FeatureFlag = keyof typeof featureFlags;

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
export function useFeatureFlags(flags: FeatureFlag[]): Record<FeatureFlag, boolean> {
  return flags.reduce((acc, flag) => {
    acc[flag] = featureFlags[flag];
    return acc;
  }, {} as Record<FeatureFlag, boolean>);
}
