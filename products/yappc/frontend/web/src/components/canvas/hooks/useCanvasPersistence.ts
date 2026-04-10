/**
 * useCanvasPersistence Hook
 *
 * Debounced auto-save hook for canvas state. Prevents redundant saves when the
 * serialized state has not changed since the last successful save.
 *
 * @doc.type hook
 * @doc.purpose Auto-save canvas state with debounce and dedup
 * @doc.layer product
 * @doc.pattern Hook
 */
import { useRef, useCallback } from 'react';

import { CanvasPersistenceService } from '../../../services/persistence';

interface UseCanvasPersistenceOptions {
  projectId: string;
  canvasId: string;
  autoSaveDelay?: number;
}

interface UseCanvasPersistenceResult {
  triggerAutoSave: (state: unknown) => void;
}

/**
 * useCanvasPersistence schedules a debounced save of canvas state.
 * If the serialized state has not changed since the last save, the call is skipped.
 */
export function useCanvasPersistence({
  projectId,
  canvasId,
  autoSaveDelay = 2000,
}: UseCanvasPersistenceOptions): UseCanvasPersistenceResult {
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastSavedJsonRef = useRef<string | null>(null);

  const triggerAutoSave = useCallback(
    (state: unknown) => {
      // Clear any pending save
      if (timerRef.current !== null) {
        clearTimeout(timerRef.current);
      }

      timerRef.current = setTimeout(() => {
        const json = JSON.stringify(state);
        if (json === lastSavedJsonRef.current) {
          // State unchanged — skip save
          return;
        }
        lastSavedJsonRef.current = json;
        void CanvasPersistenceService.saveCanvas(
          projectId,
          canvasId,
          state as Parameters<typeof CanvasPersistenceService.saveCanvas>[2]
        );
      }, autoSaveDelay);
    },
    [projectId, canvasId, autoSaveDelay]
  );

  return { triggerAutoSave };
}
