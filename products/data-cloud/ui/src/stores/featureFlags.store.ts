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

/**
 * Feature flag definitions
 */
export interface FeatureFlags {
  /** Enable the new Intelligent Hub (unified home page) */
  enableIntelligentHub: boolean;
  /** Enable the Command Bar (NL command input) */
  enableCommandBar: boolean;
  /** Enable the Ambient Intelligence Bar (bottom notifications) */
  enableAmbientIntelligence: boolean;
  /** Enable the Context Sidebar (always-visible assistance panel) */
  enableContextSidebar: boolean;
  /** Enable unified Data Explorer (merged pages) */
  enableUnifiedDataExplorer: boolean;
  /** Enable Smart Workflow Builder (intent-based) */
  enableSmartWorkflowBuilder: boolean;
  /** Keep legacy pages accessible for power users */
  legacyPagesEnabled: boolean;
  /** Enable simplified navigation (5 items vs 12+) */
  enableSimplifiedNav: boolean;
}

/**
 * Default feature flags (these are user-overridable defaults)
 * The actual availability is determined by the capability schema
 */
const defaultFlags: FeatureFlags = {
  enableIntelligentHub: true,
  enableCommandBar: true,
  enableAmbientIntelligence: true,
  enableContextSidebar: true,
  enableUnifiedDataExplorer: true,
  enableSmartWorkflowBuilder: true,
  legacyPagesEnabled: true, // Keep legacy for backwards compatibility
  enableSimplifiedNav: true,
};

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
    console.error("Failed to load capability schema:", error);
    // Continue with defaults if schema fails to load
    set(capabilitySchemaLoadedAtom, false);
  }
});
