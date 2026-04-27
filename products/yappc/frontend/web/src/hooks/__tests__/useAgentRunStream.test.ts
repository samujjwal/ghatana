/**
 * useAgentRunStream Hook Tests
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useAgentRunStream } from '../useAgentRunStream';
import type { LifecycleStateUpdate } from '../../services/LifecycleWebSocketService';

// ---------------------------------------------------------------------------
// Mock LifecycleWebSocketService
// ---------------------------------------------------------------------------

type UpdateHandler = (update: LifecycleStateUpdate) => void;
type ConnectionHandler = (connected: boolean) => void;

const mockUpdateHandlers: UpdateHandler[] = [];
const mockConnectionHandlers: ConnectionHandler[] = [];

const mockService = {
  connect: vi.fn(),
  disconnect: vi.fn(),
  onUpdate: vi.fn((handler: UpdateHandler) => {
    mockUpdateHandlers.push(handler);
    return () => {
      const idx = mockUpdateHandlers.indexOf(handler);
      if (idx >= 0) mockUpdateHandlers.splice(idx, 1);
    };
  }),
  onConnectionChange: vi.fn((handler: ConnectionHandler) => {
    mockConnectionHandlers.push(handler);
    return () => {
      const idx = mockConnectionHandlers.indexOf(handler);
      if (idx >= 0) mockConnectionHandlers.splice(idx, 1);
    };
  }),
};

vi.mock('../../services/LifecycleWebSocketService', () => ({
  LifecycleWebSocketService: vi.fn(() => mockService),
}));

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function fireUpdate(update: LifecycleStateUpdate) {
  mockUpdateHandlers.forEach((h) => h(update));
}

function fireConnection(connected: boolean) {
  mockConnectionHandlers.forEach((h) => h(connected));
}

const SEEDED_RUNS = [
  {
    id: 'run-seed-1',
    agentName: 'LifecyclePlannerAgent',
    status: 'RUNNING' as const,
    stage: 'GENERATE',
    retryCount: 0,
    createdAt: '2026-01-01T00:00:00.000Z',
  },
];

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('useAgentRunStream', () => {
  beforeEach(() => {
    mockUpdateHandlers.length = 0;
    mockConnectionHandlers.length = 0;
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('starts with seeded runs', () => {
    const { result } = renderHook(() =>
      useAgentRunStream('proj-1', { seededRuns: SEEDED_RUNS }),
    );

    expect(result.current.runs).toHaveLength(1);
    expect(result.current.runs[0].id).toBe('run-seed-1');
  });

  it('connects to the WebSocket service on mount', () => {
    renderHook(() =>
      useAgentRunStream('proj-1', { seededRuns: SEEDED_RUNS }),
    );

    expect(mockService.connect).toHaveBeenCalledWith('proj-1');
  });

  it('disconnects from the WebSocket service on unmount', () => {
    const { unmount } = renderHook(() =>
      useAgentRunStream('proj-1', { seededRuns: SEEDED_RUNS }),
    );

    unmount();

    expect(mockService.disconnect).toHaveBeenCalled();
  });

  it('reflects connection state changes', () => {
    const { result } = renderHook(() =>
      useAgentRunStream('proj-1', { seededRuns: SEEDED_RUNS }),
    );

    expect(result.current.isConnected).toBe(false);

    act(() => {
      fireConnection(true);
    });

    expect(result.current.isConnected).toBe(true);
  });

  it('merges incoming agent_result updates into the run list', () => {
    const { result } = renderHook(() =>
      useAgentRunStream('proj-1', { seededRuns: SEEDED_RUNS }),
    );

    act(() => {
      fireUpdate({
        type: 'agent_result',
        projectId: 'proj-1',
        timestamp: '2026-01-01T00:01:00.000Z',
        data: {
          id: 'run-live-1',
          agentName: 'ComplianceGuardAgent',
          status: 'RUNNING',
          stage: 'VALIDATE',
          retryCount: 0,
          createdAt: '2026-01-01T00:01:00.000Z',
        },
      });
    });

    expect(result.current.runs).toHaveLength(2);
    expect(result.current.runs[1].id).toBe('run-live-1');
  });

  it('updates an existing run when the same id arrives', () => {
    const { result } = renderHook(() =>
      useAgentRunStream('proj-1', { seededRuns: SEEDED_RUNS }),
    );

    act(() => {
      fireUpdate({
        type: 'agent_result',
        projectId: 'proj-1',
        timestamp: '2026-01-01T00:01:00.000Z',
        data: {
          id: 'run-seed-1',
          status: 'SUCCEEDED',
          completedAt: '2026-01-01T00:01:00.000Z',
        },
      });
    });

    expect(result.current.runs).toHaveLength(1);
    expect(result.current.runs[0].status).toBe('SUCCEEDED');
  });

  it('ignores updates with unrecognised type', () => {
    const { result } = renderHook(() =>
      useAgentRunStream('proj-1', { seededRuns: SEEDED_RUNS }),
    );

    act(() => {
      fireUpdate({
        type: 'phase_transition',
        projectId: 'proj-1',
        timestamp: '2026-01-01T00:01:00.000Z',
        data: { id: 'should-not-appear' },
      });
    });

    expect(result.current.runs).toHaveLength(1);
  });

  it('exposes setRuns for local state overrides (e.g. retry)', () => {
    const { result } = renderHook(() =>
      useAgentRunStream('proj-1', { seededRuns: SEEDED_RUNS }),
    );

    act(() => {
      result.current.setRuns((prev) =>
        prev.map((r) =>
          r.id === 'run-seed-1' ? { ...r, status: 'FAILED' as const } : r,
        ),
      );
    });

    expect(result.current.runs[0].status).toBe('FAILED');
  });
});
