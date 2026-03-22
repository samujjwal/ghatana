// All tests skipped - incomplete feature
import { render, waitFor } from '@testing-library/react';
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

// We'll mock jotai's useAtom to provide a shared canvas state and a setter we can control.
let mockCanvasState: any = null;
const mockSetCanvasState = vi.fn();

vi.mock('jotai', async () => {
    const actual = await vi.importActual('jotai');
    return {
        ...actual,
        useAtom: (_: unknown) => [mockCanvasState, mockSetCanvasState],
    };
});

import { useCanvasScene } from '../../routes/app/project/canvas/useCanvasScene';

function HookHarness() {
    const hook = useCanvasScene({ projectId: 'p', canvasId: 'c' });

    React.useEffect(() => {
        // mount-time: nothing to do here
    }, []);

    return (
        <div>
            <button data-testid="call-change" onClick={() => hook.handleNodesChange([{ id: 'n1', type: 'position', position: { x: 10, y: 20 } }])}>
                change
            </button>
        </div>
    );
}

describe.skip('useCanvasScene integration - no ping-pong', () => {
    beforeEach(() => {
        mockSetCanvasState.mockReset();
        mockCanvasState = {
            elements: [
                { id: 'n1', kind: 'node', type: 'component', position: { x: 10, y: 20 }, data: {} },
            ],
            connections: [],
        };

        // Make setter behave like real setState: execute updater and only count when state changes
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
            (mockSetCanvasState as unknown)._updateCount = updateCount;
            return mockCanvasState;
        });
    });

    it('does not produce ping-pong when external update sets identical normalized nodes', async () => {
        const { getByTestId } = render(<HookHarness />);

        // Simulate external component updating the atom to an identical normalized shape
        mockSetCanvasState((prev: unknown) => ({ ...prev }));

        // Record actual updates after external update
        const callsAfterExternal = (mockSetCanvasState as unknown)._updateCount || 0;

        // Now call handleNodesChange with an identical position (no-op)
        getByTestId('call-change').click();

        await waitFor(() => {
            // The hook should not make additional real changes (no-op)
            const after = (mockSetCanvasState as unknown)._updateCount || 0;
            expect(after).toBe(callsAfterExternal);
        });
    });
});
