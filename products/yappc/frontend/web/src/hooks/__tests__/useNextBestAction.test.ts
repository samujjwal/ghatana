/**
 * useNextBestAction Hook Tests.
 *
 * Verifies that:
 * 1. The hook returns structured NextAction objects from the ranked titles in guidance context.
 * 2. The top action is registered into ActionRegistry as 'next-best-action-top'.
 * 3. The ActionRegistry entry is cleaned up on unmount.
 * 4. When nextActions is empty, primaryAction and secondaryAction are null.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useNextBestAction } from '../useNextBestAction';
import ActionRegistry from '../../services/ActionRegistry';

// ─────────────────────────────────────────────────────────────────────────────
// Mock react-router
// ─────────────────────────────────────────────────────────────────────────────

const mockNavigate = vi.fn();
vi.mock('react-router', () => ({
  useNavigate: () => mockNavigate,
}));

// ─────────────────────────────────────────────────────────────────────────────
// Mock WorkflowContextProvider
// ─────────────────────────────────────────────────────────────────────────────

let mockNextActions: string[] = [];
let mockProjectId: string | null = null;
let mockPhase: string | null = null;

vi.mock('../../context/WorkflowContextProvider', () => ({
  useGuidanceContext: () => ({
    nextActions: mockNextActions,
    currentPhaseSteps: [],
    tips: [],
    completedSteps: [],
    showGuidancePanel: false,
    toggleGuidancePanel: vi.fn(),
    completeStep: vi.fn(),
    dismissTip: vi.fn(),
    resetGuidance: vi.fn(),
  }),
  useWorkflowContext: () => ({
    project: {
      id: mockProjectId,
      name: null,
      phase: mockPhase,
      status: 'active',
      hasUnsavedChanges: false,
    },
    route: {},
    selection: {},
    capabilities: {},
    guidance: {},
    currentPhase: null,
    availablePhases: [],
    canTransitionTo: vi.fn(),
    navigateToPhase: vi.fn(),
    toggleGuidancePanel: vi.fn(),
    completeStep: vi.fn(),
    dismissTip: vi.fn(),
    resetGuidance: vi.fn(),
    setSelection: vi.fn(),
    clearSelection: vi.fn(),
    isLoading: false,
    error: null,
  }),
}));

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

const TOP_ACTION_ID = 'next-best-action-top';

function getRegisteredTopAction() {
  return ActionRegistry.get(TOP_ACTION_ID);
}

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

describe('useNextBestAction', () => {
  beforeEach(() => {
    mockNextActions = [];
    mockProjectId = null;
    mockPhase = null;
    mockNavigate.mockReset();
    // Clean registry before each test
    ActionRegistry.unregister(TOP_ACTION_ID);
  });

  afterEach(() => {
    ActionRegistry.unregister(TOP_ACTION_ID);
  });

  // ── Empty actions ──────────────────────────────────────────────────────────

  it('returns null for primaryAction and secondaryAction when nextActions is empty', () => {
    mockNextActions = [];

    const { result } = renderHook(() => useNextBestAction());

    expect(result.current.primaryAction).toBeNull();
    expect(result.current.secondaryAction).toBeNull();
    expect(result.current.hasActions).toBe(false);
    expect(result.current.rankedTitles).toEqual([]);
  });

  it('does not register an ActionRegistry entry when nextActions is empty', () => {
    mockNextActions = [];

    renderHook(() => useNextBestAction());

    expect(getRegisteredTopAction()).toBeUndefined();
  });

  // ── Ranked actions ─────────────────────────────────────────────────────────

  it('returns primaryAction with the first ranked title', () => {
    mockNextActions = ['Complete validation step', 'Stabilize failing signals'];
    mockProjectId = 'proj-001';
    mockPhase = 'PLAN';

    const { result } = renderHook(() => useNextBestAction());

    expect(result.current.primaryAction).not.toBeNull();
    expect(result.current.primaryAction?.title).toBe('Complete validation step');
    expect(result.current.primaryAction?.priority).toBe('primary');
  });

  it('returns secondaryAction with the second ranked title', () => {
    mockNextActions = ['Complete validation step', 'Stabilize failing signals'];
    mockProjectId = 'proj-001';

    const { result } = renderHook(() => useNextBestAction());

    expect(result.current.secondaryAction).not.toBeNull();
    expect(result.current.secondaryAction?.title).toBe('Stabilize failing signals');
    expect(result.current.secondaryAction?.priority).toBe('secondary');
  });

  it('returns null secondaryAction when only one action is ranked', () => {
    mockNextActions = ['Complete validation step'];

    const { result } = renderHook(() => useNextBestAction());

    expect(result.current.secondaryAction).toBeNull();
  });

  it('sets hasActions to true when at least one action is ranked', () => {
    mockNextActions = ['Save and synchronize pending page artifact changes'];

    const { result } = renderHook(() => useNextBestAction());

    expect(result.current.hasActions).toBe(true);
  });

  // ── ActionRegistry integration ─────────────────────────────────────────────

  it('registers the top action in ActionRegistry under category ai with highest priority', () => {
    mockNextActions = ['Generate code from canvas'];
    mockProjectId = 'proj-001';

    renderHook(() => useNextBestAction());

    const entry = getRegisteredTopAction();
    expect(entry).toBeDefined();
    expect(entry?.id).toBe(TOP_ACTION_ID);
    expect(entry?.label).toBe('Generate code from canvas');
    expect(entry?.category).toBe('ai');
    expect(entry?.priority).toBe(9999);
  });

  it('updates ActionRegistry entry when nextActions changes', () => {
    mockNextActions = ['First action'];
    mockProjectId = 'proj-001';

    const { rerender } = renderHook(() => useNextBestAction());

    let entry = getRegisteredTopAction();
    expect(entry?.label).toBe('First action');

    // Update actions
    act(() => {
      mockNextActions = ['Updated top action'];
    });
    rerender();

    entry = getRegisteredTopAction();
    expect(entry?.label).toBe('Updated top action');
  });

  it('unregisters ActionRegistry entry on unmount', () => {
    mockNextActions = ['Some action'];
    mockProjectId = 'proj-001';

    const { unmount } = renderHook(() => useNextBestAction());

    expect(getRegisteredTopAction()).toBeDefined();

    unmount();

    expect(getRegisteredTopAction()).toBeUndefined();
  });

  // ── Action handler navigation ──────────────────────────────────────────────

  it('action handler navigates to canvas for validation-related actions', () => {
    mockNextActions = ['Review validation findings'];
    mockProjectId = 'proj-abc';

    const { result } = renderHook(() => useNextBestAction());

    result.current.primaryAction?.action();

    expect(mockNavigate).toHaveBeenCalledWith('/p/proj-abc/canvas');
  });

  it('action handler dispatches save event for save-related actions', () => {
    mockNextActions = ['Save and synchronize pending page artifact changes'];
    mockProjectId = 'proj-abc';

    const dispatchedEvents: string[] = [];
    const listener = (e: Event) => { dispatchedEvents.push(e.type); };
    window.addEventListener('yappc:save-requested', listener);

    const { result } = renderHook(() => useNextBestAction());
    result.current.primaryAction?.action();

    window.removeEventListener('yappc:save-requested', listener);

    expect(dispatchedEvents).toContain('yappc:save-requested');
  });

  it('action handler navigates to root when no project is loaded', () => {
    mockNextActions = ['Complete some step'];
    mockProjectId = null;

    const { result } = renderHook(() => useNextBestAction());
    result.current.primaryAction?.action();

    expect(mockNavigate).toHaveBeenCalledWith('/');
  });
});
