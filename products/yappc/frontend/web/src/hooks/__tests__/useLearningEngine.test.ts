/**
 * useLearningEngine Hook Tests
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useLearningEngine } from '../useLearningEngine';
import { resetState } from '../../services/ai/LearningService';

describe('useLearningEngine', () => {
  beforeEach(() => {
    resetState();
  });

  it('should return default preferences on first render', () => {
    const { result } = renderHook(() => useLearningEngine());

    expect(result.current.preferences.preferredLayout).toBe('comfortable');
    expect(result.current.preferences.navigationStyle).toBe('sidebar');
    expect(result.current.actionCount).toBe(0);
  });

  it('should record an action and update state', () => {
    const { result } = renderHook(() => useLearningEngine());

    act(() => {
      result.current.record('navigation', 'open-project', 'project:alpha');
    });

    expect(result.current.actionCount).toBe(1);
    expect(result.current.preferences.frequentActions).toContain('open-project');
  });

  it('should update patterns after recording', () => {
    const { result } = renderHook(() => useLearningEngine());

    act(() => {
      result.current.record('search', 'global-search', 'global');
      result.current.record('search', 'global-search', 'global');
    });

    const searchPattern = result.current.patterns.find((p) => p.category === 'search');
    expect(searchPattern).toBeDefined();
    expect(searchPattern!.frequency).toBe(2);
  });

  it('should reset state', () => {
    const { result } = renderHook(() => useLearningEngine());

    act(() => {
      result.current.record('editing', 'edit', 'ctx');
      result.current.record('editing', 'edit', 'ctx');
    });
    expect(result.current.actionCount).toBe(2);

    act(() => {
      result.current.reset();
    });
    expect(result.current.actionCount).toBe(0);
  });
});
