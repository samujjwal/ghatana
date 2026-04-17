/**
 * Progressive Disclosure Hook
 *
 * Manages progressive disclosure of advanced features based on user experience level and context.
 * Reduces cognitive load by hiding advanced features until needed.
 *
 * @doc.type hook
 * @doc.purpose Progressive disclosure management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useEffect } from 'react';

// ============================================================================
// Types
// ============================================================================

export type ExperienceLevel = 'beginner' | 'intermediate' | 'advanced';

export interface DisclosureConfig {
  featureId: string;
  minExperienceLevel?: ExperienceLevel;
  requireConfirmation?: boolean;
  showHint?: boolean;
  hintDelay?: number; // ms
}

export interface UseProgressiveDisclosureOptions {
  experienceLevel?: ExperienceLevel;
  features: DisclosureConfig[];
  storageKey?: string;
}

export interface UseProgressiveDisclosureResult {
  isFeatureVisible: (featureId: string) => boolean;
  showFeature: (featureId: string) => void;
  dismissFeature: (featureId: string) => void;
  toggleFeature: (featureId: string) => void;
  resetAll: () => void;
  getFeatureHint: (featureId: string) => string | null;
  experienceLevel: ExperienceLevel;
  setExperienceLevel: (level: ExperienceLevel) => void;
}

// ============================================================================
// Experience Level Thresholds
// ============================================================================

const EXPERIENCE_ORDER: Record<ExperienceLevel, number> = {
  beginner: 1,
  intermediate: 2,
  advanced: 3,
};

// ============================================================================
// Hook Implementation
// ============================================================================

export function useProgressiveDisclosure({
  experienceLevel: initialExperienceLevel = 'intermediate',
  features,
  storageKey = 'progressive-disclosure',
}: UseProgressiveDisclosureOptions): UseProgressiveDisclosureResult {
  const [experienceLevel, setExperienceLevel] = useState<ExperienceLevel>(initialExperienceLevel);
  const [visibleFeatures, setVisibleFeatures] = useState<Set<string>>(new Set());
  const [dismissedFeatures, setDismissedFeatures] = useState<Set<string>>(new Set());

  // Load persisted state from localStorage
  useEffect(() => {
    try {
      const stored = localStorage.getItem(storageKey);
      if (stored) {
        const data = JSON.parse(stored);
        setVisibleFeatures(new Set(data.visible || []));
        setDismissedFeatures(new Set(data.dismissed || []));
        if (data.experienceLevel) {
          setExperienceLevel(data.experienceLevel);
        }
      }
    } catch {
      // Ignore storage errors
    }
  }, [storageKey]);

  // Persist state to localStorage
  useEffect(() => {
    try {
      const data = {
        visible: Array.from(visibleFeatures),
        dismissed: Array.from(dismissedFeatures),
        experienceLevel,
      };
      localStorage.setItem(storageKey, JSON.stringify(data));
    } catch {
      // Ignore storage errors
    }
  }, [visibleFeatures, dismissedFeatures, experienceLevel, storageKey]);

  // Check if a feature should be visible based on experience level
  const isFeatureVisible = useCallback(
    (featureId: string): boolean => {
      const config = features.find(f => f.featureId === featureId);
      if (!config) return false;

      // Check if dismissed
      if (dismissedFeatures.has(featureId)) return false;

      // Explicit user reveal should win over default experience gating.
      if (visibleFeatures.has(featureId)) return true;

      // Check experience level requirement
      if (config.minExperienceLevel) {
        const requiredLevel = EXPERIENCE_ORDER[config.minExperienceLevel];
        const currentLevel = EXPERIENCE_ORDER[experienceLevel];
        if (currentLevel < requiredLevel) return false;
      }

      // Default to hidden for features with minExperienceLevel
      return !config.minExperienceLevel;
    },
    [experienceLevel, features, visibleFeatures, dismissedFeatures]
  );

  // Show a feature
  const showFeature = useCallback((featureId: string) => {
    setVisibleFeatures(prev => new Set(prev).add(featureId));
    setDismissedFeatures(prev => {
      const next = new Set(prev);
      next.delete(featureId);
      return next;
    });
  }, []);

  // Dismiss a feature
  const dismissFeature = useCallback((featureId: string) => {
    setDismissedFeatures(prev => new Set(prev).add(featureId));
    setVisibleFeatures(prev => {
      const next = new Set(prev);
      next.delete(featureId);
      return next;
    });
  }, []);

  // Toggle a feature
  const toggleFeature = useCallback((featureId: string) => {
    if (isFeatureVisible(featureId)) {
      dismissFeature(featureId);
    } else {
      showFeature(featureId);
    }
  }, [isFeatureVisible, dismissFeature, showFeature]);

  // Reset all features
  const resetAll = useCallback(() => {
    setVisibleFeatures(new Set());
    setDismissedFeatures(new Set());
  }, []);

  // Get feature hint
  const getFeatureHint = useCallback((featureId: string): string | null => {
    const config = features.find(f => f.featureId === featureId);
    if (!config || !config.showHint) return null;

    if (config.minExperienceLevel && EXPERIENCE_ORDER[experienceLevel] < EXPERIENCE_ORDER[config.minExperienceLevel]) {
      return `This feature requires ${config.minExperienceLevel} experience level`;
    }

    return null;
  }, [experienceLevel, features]);

  return {
    isFeatureVisible,
    showFeature,
    dismissFeature,
    toggleFeature,
    resetAll,
    getFeatureHint,
    experienceLevel,
    setExperienceLevel,
  };
}
