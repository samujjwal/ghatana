/**
 * Learning Engine Hook
 *
 * React hook wrapping LearningService for behaviour tracking and
 * adaptive preference derivation.
 *
 * @doc.type hook
 * @doc.purpose User behaviour learning and adaptive preferences
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useEffect } from 'react';
import {
  recordAction,
  getState,
  getPreferences,
  getPatterns,
  resetState,
  type LearningState,
  type UserPreferences,
  type UsagePattern,
  type ActionCategory,
} from '../services/ai/LearningService';

// ============================================================================
// Types
// ============================================================================

export interface UseLearningEngineResult {
  preferences: UserPreferences;
  patterns: UsagePattern[];
  record: (category: ActionCategory, action: string, context: string) => void;
  reset: () => void;
  actionCount: number;
}

// ============================================================================
// Hook
// ============================================================================

export function useLearningEngine(): UseLearningEngineResult {
  const [state, setState] = useState<LearningState>(getState);

  // Re-sync from storage on mount
  useEffect(() => {
    setState(getState());
  }, []);

  const record = useCallback(
    (category: ActionCategory, action: string, context: string) => {
      const next = recordAction({ category, action, context });
      setState(next);
    },
    [],
  );

  const reset = useCallback(() => {
    resetState();
    setState(getState());
  }, []);

  return {
    preferences: state.preferences,
    patterns: state.patterns,
    record,
    reset,
    actionCount: state.actions.length,
  };
}
