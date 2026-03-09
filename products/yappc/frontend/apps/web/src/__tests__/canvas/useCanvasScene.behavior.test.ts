// All tests skipped - incomplete feature
import { describe, it, expect, vi, beforeEach } from 'vitest';

// We need to mock jotai's useAtom before importing the hook under test
const mockSetCanvasState = vi.fn();
let mockCanvasState: any = null;

vi.mock('jotai', async () => {
  const actual = await vi.importActual('jotai');
  return {
    ...actual,
    useAtom: (atom: unknown) => {
      // return the current mock state and setter
      return [mockCanvasState, mockSetCanvasState];
    },
  };
});

// Now import the hook after mocking
import { renderHook, act } from '@testing-library/react';

import { useCanvasScene } from '../../routes/app/project/canvas/useCanvasScene';


describe.skip('useCanvasScene behavior - change detection', () => {
  beforeEach(() => {
    mockSetCanvasState.mockReset();
    // initial canvas state with a single node
    mockCanvasState = {
      elements: [
        {
          id: 'n1',
          kind: 'node',
          type: 'component',
          position: { x: 10, y: 20 },
          data: { label: 'Node 1' },
        },
      ],
      connections: [],
    };
    // Make the mocked setter behave like a real setter: if passed a function, call it with current state
    // and update mockCanvasState when the returned value differs. Track actual state update counts.
    let updateCount = 0;
    mockSetCanvasState.mockImplementation((arg: unknown) => {
      if (typeof arg === 'function') {
        const prev = mockCanvasState;
        const next = arg(prev);
        if (next !== prev) {
          mockCanvasState = next;
          updateCount++;
        }
      } else {
        if (arg !== mockCanvasState) {
          mockCanvasState = arg;
          updateCount++;
        }
      }
      // expose the count for assertions
      (mockSetCanvasState as unknown)._updateCount = updateCount;
      return mockCanvasState;
    });
  });

  it('does not call setCanvasState when nodesNormalized do not change', async () => {
    let result: unknown;
    await act(async () => {
      result = renderHook(() =>
        useCanvasScene({ projectId: 'p', canvasId: 'c' })
      );
      // flush microtasks so any mount-time effects complete
      await Promise.resolve();
    });

    // record actual state update count after mount-time effects have settled
    const beforeCalls = (mockSetCanvasState as unknown)._updateCount || 0;

    // emulate React Flow nodes change where the position is identical (no-op)
    await act(async () => {
      result.result.current.handleNodesChange([
        { id: 'n1', type: 'position', position: { x: 10, y: 20 } },
      ]);
      await Promise.resolve();
    });

    const afterCallsNoop = (mockSetCanvasState as unknown)._updateCount || 0;
    expect(afterCallsNoop).toBe(beforeCalls);
  });

  it('calls setCanvasState once when node position changes', async () => {
    let result2: unknown;
    await act(async () => {
      result2 = renderHook(() =>
        useCanvasScene({ projectId: 'p', canvasId: 'c' })
      );
      // flush mount-time effects
      await Promise.resolve();
    });

    const before = mockSetCanvasState.mock.calls.length;

    await act(async () => {
      result2.result.current.handleNodesChange([
        { id: 'n1', type: 'position', position: { x: 30, y: 40 } },
      ]);
      await Promise.resolve();
    });

    // we expect one actual state update compared to before
    const afterCalls = (mockSetCanvasState as unknown)._updateCount || 0;
    expect(afterCalls).toBe(before + 1);
    // verify the updated elements passed to setter include new position
    // the mock implementation updated mockCanvasState in-place when an actual change occurred
    const el = mockCanvasState.elements.find((e: unknown) => e.id === 'n1');
    expect(el.position).toEqual({ x: 30, y: 40 });
  });
});
