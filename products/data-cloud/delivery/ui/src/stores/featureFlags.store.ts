/**
 * Feature Flags Store
 *
 * Jotai-based state management for feature flags.
 * Controls progressive rollout of new UI/UX features.
 *
 * This store now integrates with the unified capability schema to ensure
 * feature gates are driven by capability availability.
 *
 * @doc.type store
 * @doc.purpose Feature flags for progressive UI rollout driven by capability schema
 * @doc.layer frontend
 */

import { atom } from "jotai";
import { atomWithStorage } from "jotai/utils";
import { isFeatureGateEnabled, loadCapabilitySchema } from "@/lib/capabilities";
import { emitDataCloudDiagnostic } from "@/diagnostics";
import {
  GENERATED_FEATURE_GATE_CONFIG,
  type GeneratedFeatureGateId,
} from "@/lib/generated/feature-gates.generated";

/**
 * Feature flag definitions
 */
export type FeatureFlags = Record<GeneratedFeatureGateId, boolean>;

/**
 * Default feature flags (these are user-overridable defaults)
 * The actual availability is determined by the capability schema
 */
const defaultFlags: FeatureFlags = GENERATED_FEATURE_GATE_CONFIG.reduce(
  (acc, gate) => {
    acc[gate.id] = gate.defaultValue;
    return acc;
  },
  {} as FeatureFlags,
);

/**
 * Feature flags atom with localStorage persistence
 * These are user preferences that can override capability-driven defaults
 */
export const featureFlagsAtom = atomWithStorage<FeatureFlags>(
  "data-cloud-feature-flags",
  defaultFlags,
);

/**
 * Derived atoms for individual flags
 * These check both the capability schema and user preferences
 */
export const isIntelligentHubEnabledAtom = atom(async (get) => {
  const userFlags = get(featureFlagsAtom);
  const capabilityEnabled = await isFeatureGateEnabled("enableIntelligentHub", "data-cloud", userFlags);
  return capabilityEnabled;
});

export const isCommandBarEnabledAtom = atom(async (get) => {
  const userFlags = get(featureFlagsAtom);
  const capabilityEnabled = await isFeatureGateEnabled("enableCommandBar", "data-cloud", userFlags);
  return capabilityEnabled;
});

export const isAmbientIntelligenceEnabledAtom = atom(async (get) => {
  const userFlags = get(featureFlagsAtom);
  const capabilityEnabled = await isFeatureGateEnabled("enableAmbientIntelligence", "data-cloud", userFlags);
  return capabilityEnabled;
});

export const isContextSidebarEnabledAtom = atom(async (get) => {
  const userFlags = get(featureFlagsAtom);
  const capabilityEnabled = await isFeatureGateEnabled("enableContextSidebar", "data-cloud", userFlags);
  return capabilityEnabled;
});

export const isUnifiedDataExplorerEnabledAtom = atom(async (get) => {
  const userFlags = get(featureFlagsAtom);
  const capabilityEnabled = await isFeatureGateEnabled("enableUnifiedDataExplorer", "data-cloud", userFlags);
  return capabilityEnabled;
});

export const isSmartWorkflowBuilderEnabledAtom = atom(async (get) => {
  const userFlags = get(featureFlagsAtom);
  const capabilityEnabled = await isFeatureGateEnabled("enableSmartWorkflowBuilder", "data-cloud", userFlags);
  return capabilityEnabled;
});

export const isLegacyPagesEnabledAtom = atom(async (get) => {
  const userFlags = get(featureFlagsAtom);
  const capabilityEnabled = await isFeatureGateEnabled("legacyPagesEnabled", "data-cloud", userFlags);
  return capabilityEnabled;
});

export const isSimplifiedNavEnabledAtom = atom(async (get) => {
  const userFlags = get(featureFlagsAtom);
  const capabilityEnabled = await isFeatureGateEnabled("enableSimplifiedNav", "data-cloud", userFlags);
  return capabilityEnabled;
});

/**
 * Atom to update a single flag
 */
export const updateFeatureFlagAtom = atom(
  null,
  (get, set, { flag, value }: { flag: keyof FeatureFlags; value: boolean }) => {
    const current = get(featureFlagsAtom);
    set(featureFlagsAtom, {
      ...current,
      [flag]: value,
    });
  },
);

/**
 * Atom to reset all flags to defaults
 */
export const resetFeatureFlagsAtom = atom(null, (_get, set) => {
  set(featureFlagsAtom, defaultFlags);
});

/**
 * Check if we're in "simple" mode (all new features enabled)
 */
export const isSimpleModeAtom = atom(async (get) => {
  const flags = get(featureFlagsAtom);
  const [
    intelligentHub,
    commandBar,
    ambientIntelligence,
    contextSidebar,
    simplifiedNav,
  ] = await Promise.all([
    isFeatureGateEnabled("enableIntelligentHub", "data-cloud", flags),
    isFeatureGateEnabled("enableCommandBar", "data-cloud", flags),
    isFeatureGateEnabled("enableAmbientIntelligence", "data-cloud", flags),
    isFeatureGateEnabled("enableContextSidebar", "data-cloud", flags),
    isFeatureGateEnabled("enableSimplifiedNav", "data-cloud", flags),
  ]);
  return (
    intelligentHub &&
    commandBar &&
    ambientIntelligence &&
    contextSidebar &&
    simplifiedNav
  );
});

/**
 * Check if we're in "advanced" mode (legacy pages enabled)
 */
export const isAdvancedModeAtom = atom(async (get) => {
  const flags = get(featureFlagsAtom);
  const [legacyPages, simplifiedNav] = await Promise.all([
    isFeatureGateEnabled("legacyPagesEnabled", "data-cloud", flags),
    isFeatureGateEnabled("enableSimplifiedNav", "data-cloud", flags),
  ]);
  return legacyPages && !simplifiedNav;
});

/**
 * Atom to load and cache the capability schema
 */
export const capabilitySchemaLoadedAtom = atom(false);

/**
 * Atom to initialize the capability schema
 */
export const initializeCapabilitySchemaAtom = atom(null, async (_get, set) => {
  try {
    await loadCapabilitySchema();
    set(capabilitySchemaLoadedAtom, true);
  } catch (error) {
    emitDataCloudDiagnostic("FeatureFlagsStore", "error", "Failed to load capability schema", {
      error,
    });
    // Continue with defaults if schema fails to load
    set(capabilitySchemaLoadedAtom, false);
  }
});
