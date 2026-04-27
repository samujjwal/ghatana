/**
 * useLifecyclePhase Hook Tests
 */
// @ts-nocheck
import React from 'react';
import { MemoryRouter } from 'react-router';
import { describe, it, expect, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useLifecyclePhase } from '../useLifecyclePhase';
import { LifecyclePhase } from '../../types/lifecycle';

// Mock useWorkspaceContext
const mockUseWorkspaceContext = vi.hoisted(() =>
  vi.fn(() => ({
    ownedProjects: [],
    includedProjects: [],
    isLoading: false,
  }))
);
vi.mock('../useWorkspaceData', () => ({
  useWorkspaceContext: mockUseWorkspaceContext,
}));

function createWrapper(
  initialPath = '/'
): React.ComponentType<{ children: React.ReactNode }> {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
    );
  };
}

describe('useLifecyclePhase', () => {
  it('returns correct shape of values', () => {
    const { result } = renderHook(() => useLifecyclePhase(), {
      wrapper: createWrapper('/'),
    });

    expect(result.current).toHaveProperty('currentPhase');
    expect(result.current).toHaveProperty('projectPhase');
    expect(result.current).toHaveProperty('navigateToPhase');
    expect(result.current).toHaveProperty('canTransitionTo');
    expect(result.current).toHaveProperty('currentLabel');
    expect(result.current).toHaveProperty('currentDescription');
    expect(result.current).toHaveProperty('isPhase');
    expect(result.current).toHaveProperty('isLoading');
  });

  it('exposes navigateToPhase, canTransitionTo, isPhase as functions', () => {
    const { result } = renderHook(() => useLifecyclePhase(), {
      wrapper: createWrapper('/'),
    });

    expect(typeof result.current.navigateToPhase).toBe('function');
    expect(typeof result.current.canTransitionTo).toBe('function');
    expect(typeof result.current.isPhase).toBe('function');
  });

  it('isLoading reflects workspace context loading state', () => {
    mockUseWorkspaceContext.mockReturnValueOnce({
      ownedProjects: [],
      includedProjects: [],
      isLoading: true,
    });

    const { result } = renderHook(() => useLifecyclePhase(), {
      wrapper: createWrapper('/'),
    });

    expect(result.current.isLoading).toBe(true);
  });

  it('currentPhase is null on non-phase route', () => {
    const { result } = renderHook(() => useLifecyclePhase(), {
      wrapper: createWrapper('/dashboard'),
    });

    expect(result.current.currentPhase).toBeNull();
  });

  it('currentLabel is "Unknown" when no phase is active', () => {
    const { result } = renderHook(() => useLifecyclePhase(), {
      wrapper: createWrapper('/dashboard'),
    });

    expect(result.current.currentLabel).toBe('Unknown');
  });

  it('projectPhase is null when no matching project found', () => {
    const { result } = renderHook(() => useLifecyclePhase(), {
      wrapper: createWrapper('/projects/unknown-id/intent'),
    });

    expect(result.current.projectPhase).toBeNull();
  });

  it('canTransitionTo returns true when currentPhase is null', () => {
    const { result } = renderHook(() => useLifecyclePhase(), {
      wrapper: createWrapper('/'),
    });

    // From unknown state, any transition allowed
    expect(result.current.canTransitionTo(LifecyclePhase.INTENT)).toBe(true);
  });

  it('isPhase returns false when current phase does not match', () => {
    const { result } = renderHook(() => useLifecyclePhase(), {
      wrapper: createWrapper('/dashboard'),
    });

    expect(result.current.isPhase(LifecyclePhase.INTENT)).toBe(false);
  });
});
