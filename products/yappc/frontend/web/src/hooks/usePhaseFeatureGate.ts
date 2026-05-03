/**
 * usePhaseFeatureGate Hook — YAPPC Web.
 *
 * Determines whether a lifecycle phase is available for use.
 * Phases are gated when their supporting features are incomplete or not yet live.
 * This prevents users from navigating to phases that lack implementation.
 *
 * ## Rationale
 * Some lifecycle phases (OBSERVE, LEARN) have UI navigation but lack
 * backend implementation or phase actions. Gating them avoids showing
 * broken UI or incomplete workflows.
 *
 * ## Phase Registry
 * Each phase maps to a `PhaseConfig` that declares:
 * - `enabled`: whether the phase is ready for use. Starts `false` for
 *   incomplete phases. Set to `true` when implementation is complete.
 *
 * ## Usage
 * ```tsx
 * const { isPhaseEnabled } = usePhaseFeatureGate();
 * const tabs = BASE_PROJECT_TABS.filter(tab => isPhaseEnabled(tab.key));
 * ```
 *
 * @doc.type hook
 * @doc.purpose Phase availability gate for lifecycle navigation
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useMemo } from 'react';

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

/** Lifecycle phase keys matching BASE_PROJECT_TABS in _shell.tsx */
export type PhaseKey = 'intent' | 'shape' | 'validate' | 'generate' | 'run' | 'observe' | 'learn' | 'evolve';

/** Return value of {@link usePhaseFeatureGate}. */
export interface PhaseFeatureGateResult {
  /** Check whether a specific phase is enabled. */
  isPhaseEnabled: (phase: PhaseKey) => boolean;
  /** Get all enabled phases. */
  getEnabledPhases: () => PhaseKey[];
}

// ─────────────────────────────────────────────────────────────────────────────
// Phase registry
// ─────────────────────────────────────────────────────────────────────────────

interface PhaseConfig {
  /**
   * Whether the phase is ready for use.
   * Set to `false` (default) for phases lacking implementation.
   * Flip to `true` when the phase ships — no code changes needed in consumers.
   */
  enabled: boolean;
}

/**
 * Central registry of lifecycle phase availability.
 *
 * Core phases (intent, shape, validate, generate, run, evolve) are enabled.
 * Observability and learning phases are gated until backend integration is complete.
 */
const PHASE_REGISTRY: Readonly<Record<PhaseKey, PhaseConfig>> = {
  // Core phases — fully implemented with actions and backend support
  intent: { enabled: true },
  shape: { enabled: true },
  validate: { enabled: true },
  generate: { enabled: true },
  run: { enabled: true },
  evolve: { enabled: true }, // Maps to IMPROVE in phase-actions.ts

  // Observability phase — gated until metrics/incidents backend is live
  observe: { enabled: false },

  // Learning phase — gated until analytics/insights backend is live
  learn: { enabled: false },
};

// ─────────────────────────────────────────────────────────────────────────────
// Hook
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Provides phase availability checks for navigation filtering.
 *
 * @returns `{ isPhaseEnabled, getEnabledPhases }` for filtering phase tabs.
 */
export function usePhaseFeatureGate(): PhaseFeatureGateResult {
  return useMemo(() => ({
    isPhaseEnabled: (phase: PhaseKey): boolean => {
      return PHASE_REGISTRY[phase]?.enabled ?? false;
    },
    getEnabledPhases: (): PhaseKey[] => {
      return (Object.keys(PHASE_REGISTRY) as PhaseKey[]).filter(
        (phase) => PHASE_REGISTRY[phase].enabled
      );
    },
  }), []);
}
