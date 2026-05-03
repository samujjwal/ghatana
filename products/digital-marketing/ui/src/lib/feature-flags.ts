/**
 * Feature flag configuration for DMOS.
 *
 * @doc.type config
 * @doc.purpose Control feature visibility via flags
 * @doc.layer frontend
 */

type FlagClass = 'GA' | 'EXPERIMENTAL';

export type FeatureFlag =
  | 'APPROVAL_COMMENTS'
  | 'APPROVAL_SNAPSHOT_DIFF'
  | 'DASHBOARD_GROWTH_METRICS'
  | 'DASHBOARD_RISK_COMPLIANCE'
  | 'AI_ACTION_LOG';

const FLAG_CLASSES: Record<FeatureFlag, FlagClass> = {
  APPROVAL_COMMENTS: 'GA',
  APPROVAL_SNAPSHOT_DIFF: 'GA',
  DASHBOARD_GROWTH_METRICS: 'EXPERIMENTAL',
  DASHBOARD_RISK_COMPLIANCE: 'GA',
  AI_ACTION_LOG: 'EXPERIMENTAL',
};

export const featureFlags: Record<FeatureFlag, boolean> = Object.fromEntries(
  Object.entries(FLAG_CLASSES).map(([flag, cls]) => {
    const envKey = `VITE_FEATURE_${flag}`;
    const raw = import.meta.env[envKey] as string | undefined;
    if (cls === 'GA') {
      return [flag, raw !== 'false'];
    }
    return [flag, raw === 'true'];
  }),
) as Record<FeatureFlag, boolean>;

export function isFeatureEnabled(flag: FeatureFlag): boolean {
  return featureFlags[flag];
}
