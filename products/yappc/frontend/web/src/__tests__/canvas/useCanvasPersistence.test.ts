import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

import { useCanvasPersistence } from '../../components/canvas/hooks/useCanvasPersistence';
import * as PersistenceService from '../../services/persistence';

describe('useCanvasPersistence autosave', () => {
  const projectId = 'proj-x';
  const canvasId = 'canvas-y';
  const sampleState = { elements: [], connections: [] } as unknown;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('schedules saveCanvas after autoSaveDelay and avoids duplicate saves for identical state', async () => {
    const saveSpy = vi
      .spyOn(PersistenceService.CanvasPersistenceService, 'saveCanvas')
      .mockResolvedValue({ success: true });

    const { result } = renderHook(() =>
      useCanvasPersistence({ projectId, canvasId, autoSaveDelay: 100 })
    );

    // Trigger auto-save; should schedule a timer
    act(() => {
      result.current.triggerAutoSave(sampleState);
    });

    // advance less than delay: should not have called save yet
    vi.advanceTimersByTime(50);
    expect(saveSpy).not.toHaveBeenCalled();

    // Advance past delay: save should be called once
    await act(async () => {
      vi.advanceTimersByTime(100);
      // allow any microtask resolution
      await Promise.resolve();
    });

    expect(saveSpy).toHaveBeenCalledTimes(1);

    // Trigger again with identical state: should schedule but saveCanvas should skip due to identical lastSaveRef
    act(() => {
      result.current.triggerAutoSave(sampleState);
    });

    await act(async () => {
      vi.advanceTimersByTime(200);
      await Promise.resolve();
    });

    // saveCanvas should not be called again because the hook remembers last saved JSON
    expect(saveSpy).toHaveBeenCalledTimes(1);
  });
});
