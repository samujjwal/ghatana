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
  BUDGET_PAGE_ENABLED: 'dmos.budget_page_enabled',
  STRATEGY_PAGE_ENABLED: 'dmos.strategy_page_enabled',
  CAMPAIGNS_PAGE_ENABLED: 'dmos.campaigns_page_enabled',
} as const;

export type FeatureFlag = typeof FEATURE_FLAGS[keyof typeof FEATURE_FLAGS];

// P0-004: Flag values from Vite build-time environment variables (VITE_* prefix required)
// P0-004: Use import.meta.env instead of Node's process.env for browser compatibility
const getFlagValue = (key: string, defaultValue: boolean): boolean => {
  const envKey = `VITE_${key.toUpperCase().replace(/\./g, '_')}`;
  const envValue = import.meta.env[envKey];
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
  [FEATURE_FLAGS.DASHBOARD_GROWTH_METRICS]: getFlagValue('DMOS_DASHBOARD_GROWTH_METRICS_ENABLED', false),
  [FEATURE_FLAGS.BUDGET_PAGE_ENABLED]: getFlagValue('DMOS_BUDGET_PAGE_ENABLED', false),
  [FEATURE_FLAGS.STRATEGY_PAGE_ENABLED]: getFlagValue('DMOS_STRATEGY_PAGE_ENABLED', false),
  [FEATURE_FLAGS.CAMPAIGNS_PAGE_ENABLED]: getFlagValue('DMOS_CAMPAIGNS_PAGE_ENABLED', false),
};

export function isFeatureEnabled(flag: FeatureFlag): boolean {
  return flagValues[flag];
}
