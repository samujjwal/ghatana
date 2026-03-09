/**
 * useSimulationTimeline Hook
 *
 * State management and utilities for the simulation timeline editor.
 * Handles playback, step selection, undo/redo, and persistence.
 *
 * @doc.type hook
 * @doc.purpose Timeline state management for simulation authoring
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useRef, useEffect, useMemo } from "react";

// =============================================================================
// Local Types (avoiding external contract dependencies)
// =============================================================================

export type SimStepId = string;
export type SimEntityId = string;

export type SimulationDomain =
  | "CS_DISCRETE"
  | "PHYSICS"
  | "ECONOMICS"
  | "CHEMISTRY"
  | "BIOLOGY"
  | "MEDICINE"
  | "ENGINEERING"
  | "MATHEMATICS";

export interface SimAction {
  action: string;
  type?: string;
  target?: SimEntityId;
  entityId?: SimEntityId;
  params?: Record<string, unknown>;
  duration?: number;
  delay?: number;
  easing?: string;
}

export interface SimulationStep {
  id: SimStepId;
  orderIndex?: number;
  title?: string;
  label?: string;
  description?: string;
  duration: number;
  actions: SimAction[];
  narration?: string;
  checkpoint?: boolean;
  breakpoint?: boolean;
  assessmentHook?: {
    prompt?: string;
    type?: string;
  };
}

export interface SimEntity {
  id: SimEntityId;
  type: string;
  label?: string;
  x?: number;
  y?: number;
  z?: number;
  position?: { x: number; y: number };
  data?: Record<string, unknown>;
  properties?: Record<string, unknown>;
}

export interface CanvasSettings {
  width?: number;
  height?: number;
  backgroundColor?: string;
}

export interface PlaybackSettings {
  defaultSpeed?: number;
  autoPlay?: boolean;
  loop?: boolean;
}

export interface AccessibilitySettings {
  altText?: string;
  screenReaderNarration?: boolean;
  reducedMotion?: boolean;
}

export interface SimulationManifest {
  id: string;
  title: string;
  description?: string;
  domain: SimulationDomain;
  version: string;
  initialEntities: SimEntity[];
  steps: SimulationStep[];
  canvas?: CanvasSettings;
  playback?: PlaybackSettings;
  accessibility?: AccessibilitySettings;
  createdAt?: string;
  updatedAt?: string;
  metadata?: Record<string, unknown>;
}

// =============================================================================
// Types
// =============================================================================

export interface TimelineState {
  steps: SimulationStep[];
  selectedStepId: SimStepId | null;
  currentPlaybackStep: number;
  isPlaying: boolean;
  playbackSpeed: number;
}

export interface TimelineHistoryEntry {
  steps: SimulationStep[];
  timestamp: number;
  description: string;
}

export interface UseSimulationTimelineOptions {
  manifest: SimulationManifest | null;
  onManifestChange: (manifest: SimulationManifest) => void;
  autoSaveInterval?: number;
  maxHistorySize?: number;
}

export interface UseSimulationTimelineReturn {
  // State
  steps: SimulationStep[];
  selectedStepId: SimStepId | null;
  selectedStep: SimulationStep | undefined;
  currentPlaybackStep: number;
  isPlaying: boolean;
  playbackSpeed: number;

  // Step Management
  setSteps: (steps: SimulationStep[]) => void;
  selectStep: (stepId: SimStepId | null) => void;
  addStep: (afterStepId?: SimStepId) => SimStepId;
  deleteStep: (stepId: SimStepId) => void;
  duplicateStep: (stepId: SimStepId) => SimStepId;
  reorderStep: (stepId: SimStepId, newIndex: number) => void;

  // Playback
  play: () => void;
  pause: () => void;
  togglePlayPause: () => void;
  seekTo: (stepIndex: number) => void;
  nextStep: () => void;
  prevStep: () => void;
  setPlaybackSpeed: (speed: number) => void;

  // History
  canUndo: boolean;
  canRedo: boolean;
  undo: () => void;
  redo: () => void;

  // Utilities
  totalDuration: number;
  currentTimestamp: number;
  isDirty: boolean;
  save: () => Promise<void>;
}

// =============================================================================
// Utility Functions
// =============================================================================

function generateStepId(): SimStepId {
  return `step_${Date.now()}_${Math.random().toString(36).slice(2, 8)}` as SimStepId;
}

function calculateTotalDuration(steps: SimulationStep[]): number {
  return steps.reduce((sum, step) => {
    const stepDuration = step.actions.reduce(
      (d: number, a: SimAction) =>
        Math.max(d, (a.duration || 0) + (a.delay || 0)),
      0,
    );
    return sum + (stepDuration || 500);
  }, 0);
}

function calculateTimestamp(
  steps: SimulationStep[],
  stepIndex: number,
): number {
  return steps.slice(0, stepIndex).reduce((sum, step) => {
    const stepDuration = step.actions.reduce(
      (d: number, a: SimAction) =>
        Math.max(d, (a.duration || 0) + (a.delay || 0)),
      0,
    );
    return sum + (stepDuration || 500);
  }, 0);
}

// =============================================================================
// Hook Implementation
// =============================================================================

export function useSimulationTimeline({
  manifest,
  onManifestChange,
  autoSaveInterval = 30000,
  maxHistorySize = 50,
}: UseSimulationTimelineOptions): UseSimulationTimelineReturn {
  // --- Core State ---
  const [steps, setStepsInternal] = useState<SimulationStep[]>(
    manifest?.steps || [],
  );
  const [selectedStepId, setSelectedStepId] = useState<SimStepId | null>(null);
  const [currentPlaybackStep, setCurrentPlaybackStep] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [playbackSpeed, setPlaybackSpeed] = useState(1);
  const [isDirty, setIsDirty] = useState(false);

  // --- History for Undo/Redo ---
  const historyRef = useRef<TimelineHistoryEntry[]>([]);
  const [historyIndex, setHistoryIndex] = useState(-1);

  // --- Refs ---
  const playbackTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const autoSaveTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // --- Sync with manifest prop ---
  useEffect(() => {
    if (manifest?.steps) {
      setStepsInternal(manifest.steps);
    }
  }, [manifest?.id]); // Only reset when manifest ID changes

  // --- Auto-save ---
  useEffect(() => {
    if (autoSaveInterval > 0 && isDirty) {
      autoSaveTimerRef.current = setTimeout(() => {
        handleSave();
      }, autoSaveInterval);

      return () => {
        if (autoSaveTimerRef.current) {
          clearTimeout(autoSaveTimerRef.current);
        }
      };
    }
  }, [isDirty, autoSaveInterval]);

  // --- Playback Timer ---
  useEffect(() => {
    if (isPlaying && steps.length > 0) {
      const currentStep = steps[currentPlaybackStep];
      const duration =
        currentStep?.actions.reduce(
          (d: number, a: SimAction) =>
            Math.max(d, (a.duration || 0) + (a.delay || 0)),
          0,
        ) || 500;

      playbackTimerRef.current = setTimeout(() => {
        if (currentPlaybackStep < steps.length - 1) {
          setCurrentPlaybackStep((prev) => prev + 1);
        } else {
          setIsPlaying(false);
        }
      }, duration / playbackSpeed);

      return () => {
        if (playbackTimerRef.current) {
          clearTimeout(playbackTimerRef.current);
        }
      };
    }
  }, [isPlaying, currentPlaybackStep, steps, playbackSpeed]);

  // --- History Management ---
  // History stores states BEFORE each change - when we undo, we restore this saved state
  const pushToHistory = useCallback(
    (currentSteps: SimulationStep[], description: string) => {
      const entry: TimelineHistoryEntry = {
        steps: JSON.parse(JSON.stringify(currentSteps)),
        timestamp: Date.now(),
        description,
      };

      // Truncate any redo history (when making a new change after undo)
      historyRef.current = historyRef.current.slice(0, historyIndex + 1);
      historyRef.current.push(entry);

      // Limit history size
      if (historyRef.current.length > maxHistorySize) {
        historyRef.current.shift();
      } else {
        setHistoryIndex((prev) => prev + 1);
      }
    },
    [maxHistorySize, historyIndex],
  );

  // --- Step Management ---
  const setSteps = useCallback(
    (newSteps: SimulationStep[]) => {
      pushToHistory(steps, "Update steps");
      setStepsInternal(newSteps);
      setIsDirty(true);
    },
    [steps, pushToHistory],
  );

  const selectStep = useCallback(
    (stepId: SimStepId | null) => {
      setSelectedStepId(stepId);
      if (stepId) {
        const index = steps.findIndex((s) => s.id === stepId);
        if (index !== -1) {
          setCurrentPlaybackStep(index);
        }
      }
    },
    [steps],
  );

  const addStep = useCallback(
    (afterStepId?: SimStepId): SimStepId => {
      const newId = generateStepId();
      let insertIndex = steps.length;

      if (afterStepId) {
        const afterIndex = steps.findIndex((s) => s.id === afterStepId);
        if (afterIndex !== -1) {
          insertIndex = afterIndex + 1;
        }
      }

      const newStep: SimulationStep = {
        id: newId,
        orderIndex: insertIndex,
        title: `Step ${insertIndex + 1}`,
        duration: 1000, // default 1 second
        actions: [],
      };

      const newSteps = [...steps];
      newSteps.splice(insertIndex, 0, newStep);

      // Reindex
      const reindexed = newSteps.map((s, i) => ({ ...s, orderIndex: i }));

      // Push current state to history BEFORE applying the change
      pushToHistory(steps, "Add step");

      setStepsInternal(reindexed);
      setSelectedStepId(newId);
      setIsDirty(true);

      return newId;
    },
    [steps, pushToHistory],
  );

  const deleteStep = useCallback(
    (stepId: SimStepId) => {
      pushToHistory(steps, "Delete step");

      const newSteps = steps
        .filter((s) => s.id !== stepId)
        .map((s, i) => ({ ...s, orderIndex: i }));

      setStepsInternal(newSteps);

      if (selectedStepId === stepId) {
        setSelectedStepId(null);
      }

      if (currentPlaybackStep >= newSteps.length) {
        setCurrentPlaybackStep(Math.max(0, newSteps.length - 1));
      }

      setIsDirty(true);
    },
    [steps, selectedStepId, currentPlaybackStep, pushToHistory],
  );

  const duplicateStep = useCallback(
    (stepId: SimStepId): SimStepId => {
      pushToHistory(steps, "Duplicate step");

      const sourceIndex = steps.findIndex((s) => s.id === stepId);
      if (sourceIndex === -1) return stepId;

      const sourceStep = steps[sourceIndex];
      const newId = generateStepId();

      const duplicatedStep: SimulationStep = {
        ...JSON.parse(JSON.stringify(sourceStep)),
        id: newId,
        orderIndex: sourceIndex + 1,
        title: `${sourceStep.title || "Step"} (copy)`,
      };

      const newSteps = [...steps];
      newSteps.splice(sourceIndex + 1, 0, duplicatedStep);

      // Reindex
      const reindexed = newSteps.map((s, i) => ({ ...s, orderIndex: i }));

      setStepsInternal(reindexed);
      setSelectedStepId(newId);
      setIsDirty(true);

      return newId;
    },
    [steps, pushToHistory],
  );

  const reorderStep = useCallback(
    (stepId: SimStepId, newIndex: number) => {
      pushToHistory(steps, "Reorder step");

      const currentIndex = steps.findIndex((s) => s.id === stepId);
      if (currentIndex === -1 || currentIndex === newIndex) return;

      const newSteps = [...steps];
      const [removed] = newSteps.splice(currentIndex, 1);
      newSteps.splice(newIndex, 0, removed);

      // Reindex
      const reindexed = newSteps.map((s, i) => ({ ...s, orderIndex: i }));

      setStepsInternal(reindexed);
      setIsDirty(true);
    },
    [steps, pushToHistory],
  );

  // --- Playback Controls ---
  const play = useCallback(() => {
    if (currentPlaybackStep >= steps.length - 1) {
      setCurrentPlaybackStep(0);
    }
    setIsPlaying(true);
  }, [currentPlaybackStep, steps.length]);

  const pause = useCallback(() => {
    setIsPlaying(false);
  }, []);

  const togglePlayPause = useCallback(() => {
    if (isPlaying) {
      pause();
    } else {
      play();
    }
  }, [isPlaying, play, pause]);

  const seekTo = useCallback(
    (stepIndex: number) => {
      setCurrentPlaybackStep(
        Math.max(0, Math.min(stepIndex, steps.length - 1)),
      );
    },
    [steps.length],
  );

  const nextStep = useCallback(() => {
    if (currentPlaybackStep < steps.length - 1) {
      setCurrentPlaybackStep((prev) => prev + 1);
    }
  }, [currentPlaybackStep, steps.length]);

  const prevStep = useCallback(() => {
    if (currentPlaybackStep > 0) {
      setCurrentPlaybackStep((prev) => prev - 1);
    }
  }, [currentPlaybackStep]);

  // --- History Controls ---
  // historyIndex points to the last applied entry. -1 means no history applied yet.
  // history entries contain the state BEFORE each action, so we can restore to that state
  const canUndo = historyRef.current.length > 0 && historyIndex >= 0;
  const canRedo = historyIndex < historyRef.current.length - 1;

  const undo = useCallback(() => {
    if (!canUndo) return;

    // Before undoing, save current state for redo if we're at the end
    if (historyIndex === historyRef.current.length - 1) {
      // Save current state at the end of history for redo
      historyRef.current.push({
        steps: JSON.parse(JSON.stringify(steps)),
        timestamp: Date.now(),
        description: "Current state (for redo)",
      });
    }

    // Restore the state at current history index (which is the state BEFORE that action)
    const entry = historyRef.current[historyIndex];
    setStepsInternal(JSON.parse(JSON.stringify(entry.steps)));
    setHistoryIndex((prev) => prev - 1);
    setIsDirty(true);
  }, [canUndo, historyIndex, steps]);

  const redo = useCallback(() => {
    if (!canRedo) return;

    // Move forward and restore that state
    const newIndex = historyIndex + 2;
    if (newIndex < historyRef.current.length) {
      const entry = historyRef.current[newIndex];
      setStepsInternal(JSON.parse(JSON.stringify(entry.steps)));
      setHistoryIndex(newIndex - 1);
    }
    setIsDirty(true);
  }, [canRedo, historyIndex]);

  // --- Save ---
  const handleSave = useCallback(async () => {
    if (!manifest || !isDirty) return;

    const updatedManifest: SimulationManifest = {
      ...manifest,
      steps,
      updatedAt: new Date().toISOString(),
    };

    onManifestChange(updatedManifest);
    setIsDirty(false);
  }, [manifest, steps, isDirty, onManifestChange]);

  // --- Computed Values ---
  const selectedStep = useMemo(
    () => steps.find((s) => s.id === selectedStepId),
    [steps, selectedStepId],
  );

  const totalDuration = useMemo(() => calculateTotalDuration(steps), [steps]);

  const currentTimestamp = useMemo(
    () => calculateTimestamp(steps, currentPlaybackStep),
    [steps, currentPlaybackStep],
  );

  return {
    // State
    steps,
    selectedStepId,
    selectedStep,
    currentPlaybackStep,
    isPlaying,
    playbackSpeed,

    // Step Management
    setSteps,
    selectStep,
    addStep,
    deleteStep,
    duplicateStep,
    reorderStep,

    // Playback
    play,
    pause,
    togglePlayPause,
    seekTo,
    nextStep,
    prevStep,
    setPlaybackSpeed,

    // History
    canUndo,
    canRedo,
    undo,
    redo,

    // Utilities
    totalDuration,
    currentTimestamp,
    isDirty,
    save: handleSave,
  };
}

export default useSimulationTimeline;
