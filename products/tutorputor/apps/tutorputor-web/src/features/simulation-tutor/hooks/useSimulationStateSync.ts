/**
 * useSimulationStateSync Hook
 *
 * Synchronizes simulation state between the player and AI tutor.
 * Enables real-time context awareness for tutoring.
 *
 * @doc.type hook
 * @doc.purpose Sync simulation state for AI tutor context
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useRef, useCallback, useEffect, useState } from "react";

// =============================================================================
// Types
// =============================================================================

export interface SimulationState {
  stepIndex: number;
  timeInStep: number;
  totalTime: number;
  isPlaying: boolean;
  playbackSpeed: number;
  entities: EntityState[];
  parameters: ParameterState[];
  userInteractions: UserInteraction[];
}

export interface EntityState {
  id: string;
  type: string;
  label: string;
  position?: { x: number; y: number; z?: number };
  velocity?: { x: number; y: number; z?: number };
  properties: Record<string, unknown>;
  lastUpdated: number;
}

export interface ParameterState {
  name: string;
  value: unknown;
  previousValue?: unknown;
  changedAt?: number;
  changedBy?: "user" | "simulation" | "system";
}

export interface UserInteraction {
  timestamp: number;
  type: InteractionType;
  details: Record<string, unknown>;
}

export type InteractionType =
  | "parameter_change"
  | "entity_select"
  | "entity_drag"
  | "step_navigation"
  | "playback_control"
  | "zoom_pan"
  | "measurement"
  | "annotation";

export interface StateSyncConfig {
  /** Sync interval in milliseconds */
  syncInterval: number;
  /** Maximum interactions to keep in history */
  maxInteractionHistory: number;
  /** Whether to track entity positions */
  trackPositions: boolean;
  /** Whether to track velocity changes */
  trackVelocity: boolean;
  /** Debounce delay for frequent updates */
  debounceMs: number;
}

export interface UseSimulationStateSyncOptions {
  /** Simulation ID */
  simulationId: string;
  /** Whether sync is enabled */
  enabled?: boolean;
  /** Custom configuration */
  config?: Partial<StateSyncConfig>;
  /** Callback when state changes significantly */
  onSignificantChange?: (state: SimulationState) => void;
}

export interface UseSimulationStateSyncReturn {
  /** Current synchronized state */
  state: SimulationState;
  /** Update step information */
  updateStep: (stepIndex: number, timeInStep: number) => void;
  /** Update entity state */
  updateEntity: (entity: EntityState) => void;
  /** Update parameter value */
  updateParameter: (name: string, value: unknown, changedBy?: "user" | "simulation") => void;
  /** Record user interaction */
  recordInteraction: (type: InteractionType, details: Record<string, unknown>) => void;
  /** Update playback state */
  updatePlayback: (isPlaying: boolean, speed?: number) => void;
  /** Get state snapshot for tutor context */
  getSnapshot: () => SimulationStateSnapshot;
  /** Reset state */
  reset: () => void;
}

export interface SimulationStateSnapshot {
  timestamp: number;
  state: SimulationState;
  summary: string;
  significantEvents: string[];
}

// =============================================================================
// Default Configuration
// =============================================================================

const DEFAULT_CONFIG: StateSyncConfig = {
  syncInterval: 100,
  maxInteractionHistory: 50,
  trackPositions: true,
  trackVelocity: true,
  debounceMs: 50,
};

// =============================================================================
// Hook Implementation
// =============================================================================

export function useSimulationStateSync({
  simulationId,
  enabled = true,
  config: userConfig,
  onSignificantChange,
}: UseSimulationStateSyncOptions): UseSimulationStateSyncReturn {
  const config = { ...DEFAULT_CONFIG, ...userConfig };

  // State
  const [state, setState] = useState<SimulationState>({
    stepIndex: 0,
    timeInStep: 0,
    totalTime: 0,
    isPlaying: false,
    playbackSpeed: 1,
    entities: [],
    parameters: [],
    userInteractions: [],
  });

  // Track whether sync is active based on enabled flag and simulationId
  const isActive = enabled && !!simulationId;

  // Refs for tracking
  const lastSignificantChangeRef = useRef<number>(0);
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Debounced state update
  const debouncedUpdate = useCallback(
    (updater: (prev: SimulationState) => SimulationState) => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }

      debounceTimerRef.current = setTimeout(() => {
        setState(updater);
      }, config.debounceMs);
    },
    [config.debounceMs]
  );

  // Check for significant changes
  const checkSignificantChange = useCallback(
    (newState: SimulationState, prevState: SimulationState) => {
      const now = Date.now();
      const timeSinceLastChange = now - lastSignificantChangeRef.current;

      // Rate limit significant change callbacks
      if (timeSinceLastChange < 1000) return;

      const isSignificant =
        // Step changed
        newState.stepIndex !== prevState.stepIndex ||
        // Parameter changed by user
        newState.parameters.some(
          (p) =>
            p.changedBy === "user" &&
            p.changedAt &&
            p.changedAt > lastSignificantChangeRef.current
        ) ||
        // Many interactions in short time
        newState.userInteractions.filter((i) => i.timestamp > now - 5000).length > 5;

      if (isSignificant && onSignificantChange) {
        lastSignificantChangeRef.current = now;
        onSignificantChange(newState);
      }
    },
    [onSignificantChange]
  );

  // Update step information
  const updateStep = useCallback(
    (stepIndex: number, timeInStep: number) => {
      if (!isActive) return;
      setState((prev) => {
        const newState = {
          ...prev,
          stepIndex,
          timeInStep,
          totalTime: prev.totalTime + (timeInStep - prev.timeInStep),
        };
        checkSignificantChange(newState, prev);
        return newState;
      });
    },
    [checkSignificantChange, isActive]
  );

  // Update entity state
  const updateEntity = useCallback(
    (entity: EntityState) => {
      debouncedUpdate((prev) => {
        const entityIndex = prev.entities.findIndex((e) => e.id === entity.id);
        const newEntities = [...prev.entities];

        if (entityIndex >= 0) {
          newEntities[entityIndex] = entity;
        } else {
          newEntities.push(entity);
        }

        return { ...prev, entities: newEntities };
      });
    },
    [debouncedUpdate]
  );

  // Update parameter
  const updateParameter = useCallback(
    (name: string, value: unknown, changedBy: "user" | "simulation" = "simulation") => {
      setState((prev) => {
        const paramIndex = prev.parameters.findIndex((p) => p.name === name);
        const newParams = [...prev.parameters];
        const now = Date.now();

        if (paramIndex >= 0) {
          newParams[paramIndex] = {
            ...newParams[paramIndex],
            previousValue: newParams[paramIndex].value,
            value,
            changedAt: now,
            changedBy,
          };
        } else {
          newParams.push({
            name,
            value,
            changedAt: now,
            changedBy,
          });
        }

        const newState = { ...prev, parameters: newParams };
        checkSignificantChange(newState, prev);
        return newState;
      });
    },
    [checkSignificantChange]
  );

  // Record user interaction
  const recordInteraction = useCallback(
    (type: InteractionType, details: Record<string, unknown>) => {
      setState((prev) => {
        const newInteraction: UserInteraction = {
          timestamp: Date.now(),
          type,
          details,
        };

        // Keep only recent interactions
        const newInteractions = [
          ...prev.userInteractions.slice(-config.maxInteractionHistory + 1),
          newInteraction,
        ];

        return { ...prev, userInteractions: newInteractions };
      });
    },
    [config.maxInteractionHistory]
  );

  // Update playback state
  const updatePlayback = useCallback((isPlaying: boolean, speed?: number) => {
    setState((prev) => ({
      ...prev,
      isPlaying,
      playbackSpeed: speed ?? prev.playbackSpeed,
    }));
  }, []);

  // Get snapshot for tutor context
  const getSnapshot = useCallback((): SimulationStateSnapshot => {
    const now = Date.now();
    const recentInteractions = state.userInteractions.filter(
      (i) => i.timestamp > now - 30000 // Last 30 seconds
    );

    // Generate summary
    const summaryParts: string[] = [];
    summaryParts.push(`Step ${state.stepIndex + 1}`);
    if (state.isPlaying) {
      summaryParts.push(`playing at ${state.playbackSpeed}x`);
    } else {
      summaryParts.push("paused");
    }
    summaryParts.push(`${state.entities.length} entities`);

    // Significant events
    const significantEvents: string[] = [];
    const userChangedParams = state.parameters.filter(
      (p) => p.changedBy === "user" && p.changedAt && p.changedAt > now - 60000
    );
    if (userChangedParams.length > 0) {
      significantEvents.push(
        `User changed: ${userChangedParams.map((p) => p.name).join(", ")}`
      );
    }

    const recentInteractionTypes = [...new Set(recentInteractions.map((i) => i.type))];
    if (recentInteractionTypes.length > 0) {
      significantEvents.push(`Recent actions: ${recentInteractionTypes.join(", ")}`);
    }

    return {
      timestamp: now,
      state,
      summary: summaryParts.join(", "),
      significantEvents,
    };
  }, [state]);

  // Reset state
  const reset = useCallback(() => {
    setState({
      stepIndex: 0,
      timeInStep: 0,
      totalTime: 0,
      isPlaying: false,
      playbackSpeed: 1,
      entities: [],
      parameters: [],
      userInteractions: [],
    });
    lastSignificantChangeRef.current = 0;
  }, []);

  // Cleanup
  useEffect(() => {
    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
  }, []);

  // Reset on simulation change
  useEffect(() => {
    reset();
  }, [simulationId, reset]);

  return {
    state,
    updateStep,
    updateEntity,
    updateParameter,
    recordInteraction,
    updatePlayback,
    getSnapshot,
    reset,
  };
}

export default useSimulationStateSync;
