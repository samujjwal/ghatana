/**
 * Feature Flags Store
 *
 * Jotai-based state management for feature flags.
 * Controls progressive rollout of new UI/UX features.
 *
 * @doc.type store
 * @doc.purpose Feature flags for progressive UI rollout
 * @doc.layer frontend
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

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
  /** Enable the Brain Sidebar (always-visible AI assistant) */
  enableBrainSidebar: boolean;
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
 * Default feature flags
 */
const defaultFlags: FeatureFlags = {
  enableIntelligentHub: true,
  enableCommandBar: true,
  enableAmbientIntelligence: true,
  enableBrainSidebar: true,
  enableUnifiedDataExplorer: true,
  enableSmartWorkflowBuilder: true,
  legacyPagesEnabled: true, // Keep legacy for backwards compatibility
  enableSimplifiedNav: true,
};

/**
 * Feature flags atom with localStorage persistence
 */
export const featureFlagsAtom = atomWithStorage<FeatureFlags>(
  'data-cloud-feature-flags',
  defaultFlags
);

/**
 * Derived atoms for individual flags
 */
export const isIntelligentHubEnabledAtom = atom(
  (get) => get(featureFlagsAtom).enableIntelligentHub
);

export const isCommandBarEnabledAtom = atom(
  (get) => get(featureFlagsAtom).enableCommandBar
);

export const isAmbientIntelligenceEnabledAtom = atom(
  (get) => get(featureFlagsAtom).enableAmbientIntelligence
);

export const isBrainSidebarEnabledAtom = atom(
  (get) => get(featureFlagsAtom).enableBrainSidebar
);

export const isUnifiedDataExplorerEnabledAtom = atom(
  (get) => get(featureFlagsAtom).enableUnifiedDataExplorer
);

export const isSmartWorkflowBuilderEnabledAtom = atom(
  (get) => get(featureFlagsAtom).enableSmartWorkflowBuilder
);

export const isLegacyPagesEnabledAtom = atom(
  (get) => get(featureFlagsAtom).legacyPagesEnabled
);

export const isSimplifiedNavEnabledAtom = atom(
  (get) => get(featureFlagsAtom).enableSimplifiedNav
);

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
  }
);

/**
 * Atom to reset all flags to defaults
 */
export const resetFeatureFlagsAtom = atom(
  null,
  (_get, set) => {
    set(featureFlagsAtom, defaultFlags);
  }
);

/**
 * Check if we're in "simple" mode (all new features enabled)
 */
export const isSimpleModeAtom = atom((get) => {
  const flags = get(featureFlagsAtom);
  return (
    flags.enableIntelligentHub &&
    flags.enableCommandBar &&
    flags.enableAmbientIntelligence &&
    flags.enableBrainSidebar &&
    flags.enableSimplifiedNav
  );
});

/**
 * Check if we're in "advanced" mode (legacy pages enabled)
 */
export const isAdvancedModeAtom = atom((get) => {
  const flags = get(featureFlagsAtom);
  return flags.legacyPagesEnabled && !flags.enableSimplifiedNav;
});

