/**
 * Canvas URL State Hook
 *
 * Manages Canvas state synchronization with URL parameters.
 * Provides automatic parsing, validation, and state updates based on URL changes.
 *
 * @doc.type hook
 * @doc.purpose Canvas URL state management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useEffect, useState, useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  parseCanvasURL,
  updateCanvasURL,
  validateCanvasURLState,
  mergeCanvasURLState,
  type CanvasURLState,
} from '../utils/canvasDeepLinking';

/**
 * Hook return type
 */
export interface UseCanvasURLStateReturn {
  /** Current canvas state from URL */
  urlState: CanvasURLState;
  /** Whether URL state is valid */
  isValid: boolean;
  /** Validation errors if any */
  errors: string[];
  /** Update URL state (merges with current) */
  updateURLState: (updates: Partial<CanvasURLState>) => void;
  /** Replace entire URL state */
  setURLState: (state: CanvasURLState) => void;
  /** Clear specific URL parameter */
  clearURLParam: (param: keyof CanvasURLState) => void;
  /** Clear all URL parameters */
  clearAllURLParams: () => void;
}

/**
 * Hook options
 */
export interface UseCanvasURLStateOptions {
  /** Whether to sync state changes to URL automatically */
  syncToURL?: boolean;
  /** Whether to replace history entry instead of pushing */
  replaceHistory?: boolean;
  /** Callback when URL state changes */
  onStateChange?: (state: CanvasURLState) => void;
  /** Callback when validation fails */
  onValidationError?: (errors: string[]) => void;
}

/**
 * Custom hook for managing Canvas URL state
 *
 * @doc.param options - Hook configuration options
 * @doc.returns Canvas URL state management interface
 *
 * @example
 * ```tsx
 * const { urlState, updateURLState } = useCanvasURLState({
 *   syncToURL: true,
 *   onStateChange: (state) => {
 *     console.log('Canvas state changed:', state);
 *   }
 * });
 *
 * // Update task in URL
 * updateURLState({ taskId: '101' });
 * ```
 */
export function useCanvasURLState(
  options: UseCanvasURLStateOptions = {}
): UseCanvasURLStateReturn {
  const {
    syncToURL = true,
    replaceHistory = false,
    onStateChange,
    onValidationError,
  } = options;

  const [searchParams] = useSearchParams();
  const [urlState, setUrlState] = useState<CanvasURLState>(() =>
    parseCanvasURL(searchParams)
  );

  // Validate current state
  const validation = useMemo(() => {
    return validateCanvasURLState(urlState);
  }, [urlState]);

  // Parse URL params whenever they change
  useEffect(() => {
    const newState = parseCanvasURL(searchParams);
    setUrlState(newState);

    // Notify state change
    if (onStateChange) {
      onStateChange(newState);
    }

    // Validate and notify errors
    const validation = validateCanvasURLState(newState);
    if (!validation.valid && onValidationError) {
      onValidationError(validation.errors);
    }
  }, [searchParams, onStateChange, onValidationError]);

  /**
   * Update URL state with partial updates
   */
  const updateURLState = useCallback(
    (updates: Partial<CanvasURLState>) => {
      const newState = mergeCanvasURLState(urlState, updates);
      setUrlState(newState);

      if (syncToURL) {
        updateCanvasURL(newState, replaceHistory);
      }
    },
    [urlState, syncToURL, replaceHistory]
  );

  /**
   * Replace entire URL state
   */
  const setURLStateCallback = useCallback(
    (state: CanvasURLState) => {
      setUrlState(state);

      if (syncToURL) {
        updateCanvasURL(state, replaceHistory);
      }
    },
    [syncToURL, replaceHistory]
  );

  /**
   * Clear specific URL parameter
   */
  const clearURLParam = useCallback(
    (param: keyof CanvasURLState) => {
      const newState = { ...urlState };
      delete newState[param];
      setUrlState(newState);

      if (syncToURL) {
        updateCanvasURL(newState, replaceHistory);
      }
    },
    [urlState, syncToURL, replaceHistory]
  );

  /**
   * Clear all URL parameters
   */
  const clearAllURLParams = useCallback(() => {
    setUrlState({});

    if (syncToURL) {
      updateCanvasURL({}, replaceHistory);
    }
  }, [syncToURL, replaceHistory]);

  return {
    urlState,
    isValid: validation.valid,
    errors: validation.errors,
    updateURLState,
    setURLState: setURLStateCallback,
    clearURLParam,
    clearAllURLParams,
  };
}

/**
 * Hook for task-specific URL state management
 *
 * @doc.returns Task-focused URL state interface
 */
export function useTaskURLState() {
  const { urlState, updateURLState, clearURLParam } = useCanvasURLState();

  const setTaskId = useCallback(
    (taskId: string | undefined) => {
      if (taskId) {
        updateURLState({ taskId });
      } else {
        clearURLParam('taskId');
      }
    },
    [updateURLState, clearURLParam]
  );

  return {
    taskId: urlState.taskId,
    setTaskId,
    hasTask: !!urlState.taskId,
  };
}

/**
 * Hook for persona-specific URL state management
 *
 * @doc.returns Persona-focused URL state interface
 */
export function usePersonaURLState() {
  const { urlState, updateURLState, clearURLParam } = useCanvasURLState();

  const setPersona = useCallback(
    (persona: CanvasURLState['persona']) => {
      if (persona) {
        updateURLState({ persona });
      } else {
        clearURLParam('persona');
      }
    },
    [updateURLState, clearURLParam]
  );

  return {
    persona: urlState.persona,
    setPersona,
    hasPersona: !!urlState.persona,
  };
}

/**
 * Hook for viewport-specific URL state management
 *
 * @doc.returns Viewport-focused URL state interface
 */
export function useViewportURLState() {
  const { urlState, updateURLState } = useCanvasURLState();

  const setViewport = useCallback(
    (viewport: { zoom?: number; panX?: number; panY?: number }) => {
      updateURLState(viewport);
    },
    [updateURLState]
  );

  return {
    zoom: urlState.zoom,
    panX: urlState.panX,
    panY: urlState.panY,
    setViewport,
    hasViewport: !!(urlState.zoom || urlState.panX || urlState.panY),
  };
}
