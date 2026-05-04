/**
 * DMOS feature flags configuration (P2-3: Canonical manifest-based).
 *
 * This file is generated from the canonical FEATURE_FLAGS_MANIFEST.json.
 * Do not modify this file directly - update the manifest instead.
 *
 * @doc.type config
 * @doc.purpose UI feature flags from canonical manifest
 * @doc.layer frontend
 */

// P2-3: Canonical flag definitions from FEATURE_FLAGS_MANIFEST.json
export const FEATURE_FLAGS = {
  AI_ENABLED: 'dmos.ai.enabled',
  GOOGLE_ADS_CONNECTOR_ENABLED: 'dmos.google_ads_connector.enabled',
  KILL_SWITCH_ENABLED: 'dmos.kill_switch.enabled',
  ROLLBACK_WORKFLOW_ENABLED: 'dmos.rollback_workflow.enabled',
  DASHBOARD_GROWTH_METRICS: 'dmos.dashboard_growth_metrics',
} as const;

export type FeatureFlag = typeof FEATURE_FLAGS[keyof typeof FEATURE_FLAGS];

// P2-3: Flag values from environment variables (canonical source)
const getFlagValue = (key: string, defaultValue: boolean): boolean => {
  const envValue = process.env[key.toUpperCase().replace(/\./g, '_')];
  if (envValue !== undefined) {
    return envValue === 'true' || envValue === '1';
  }
  return defaultValue;
};

export const flagValues = {
  [FEATURE_FLAGS.AI_ENABLED]: getFlagValue('DMOS_AI_ENABLED', false),
  [FEATURE_FLAGS.GOOGLE_ADS_CONNECTOR_ENABLED]: getFlagValue('DMOS_GOOGLE_ADS_CONNECTOR_ENABLED', false),
  [FEATURE_FLAGS.KILL_SWITCH_ENABLED]: getFlagValue('DMOS_KILL_SWITCH_ENABLED', true),
  [FEATURE_FLAGS.ROLLBACK_WORKFLOW_ENABLED]: getFlagValue('DMOS_ROLLBACK_WORKFLOW_ENABLED', true),
  [FEATURE_FLAGS.DASHBOARD_GROWTH_METRICS]: getFlagValue('DMOS_DASHBOARD_GROWTH_METRICS', false),
};

export function isFeatureEnabled(flag: FeatureFlag): boolean {
  return flagValues[flag];
}
