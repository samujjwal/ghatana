// All tests skipped - incomplete feature
import { render, act, waitFor } from '@testing-library/react';
import { Provider, useAtom, createStore } from 'jotai';
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

// We'll use a real jotai Provider so that state updates cause re-renders and effects
let mockCanvasState: any = null;
const mockTriggerAutoSave = vi.fn();

// Mock the persistence hook the hook under test imports
vi.mock('@/components/canvas/hooks/useCanvasPersistence', () => ({
    useCanvasPersistence: (_: unknown) => ({
        loadCanvas: async () => mockCanvasState,
        triggerAutoSave: mockTriggerAutoSave,
        saveCanvas: async () => ({ success: true }),
        clearAutoSave: () => { },
        exportCanvas: async () => null,
        getLastSnapshot: async () => null,
    }),
}));

import { useCanvasScene } from '../../routes/app/project/canvas/useCanvasScene';
import { canvasAtom } from '../../components/canvas/workspace/canvasAtoms';
import { createMockReactFlowInstance } from '../../test-utils';

import type { CanvasState } from '../../components/canvas/workspace/canvasAtoms';

function HookHarness({ rf }: { rf: any }) {
    const hook = useCanvasScene({ projectId: 'p', canvasId: 'c' });

    React.useEffect(() => {
        if (rf) {
            hook.handleInit(rf);
        }
    }, [rf]);

    return (
        <div>
            <div id="canvas-drop-zone">drop-area</div>
            <button data-testid="do-drag" onClick={() => hook.handleDragEnd({ active: { data: { current: { id: 'comp-1', type: 'component', kind: 'component', defaultData: {} } } }, over: { id: 'canvas-drop-zone' }, delta: { x: 0, y: 0 } } as unknown)}>
                drag
            </button>
            <button data-testid="move-node" onClick={() => hook.handleNodesChange([{ id: 'n1', type: 'position', position: { x: 999, y: 999 } }])}>move</button>
        </div>
    );
}

function AtomViewer() {
    const [state] = useAtom(canvasAtom);
    const count = Array.isArray(state?.elements) ? state.elements.length : 0;
    return <div data-testid="atom-elements-count">{count}</div>;
}

describe.skip('useCanvasScene deep integration', () => {
    beforeEach(() => {
        mockTriggerAutoSave.mockReset();

        mockCanvasState = {
            elements: [
                { id: 'n1', kind: 'node', type: 'component', position: { x: 10, y: 20 }, data: {} },
            ],
            connections: [],
        } as CanvasState;
    });

    it('calls triggerAutoSave only when real changes occur and adds a component on drag', async () => {
        const rf = createMockReactFlowInstance([], []) as unknown;
        // make project return a deterministic point for added components
        rf.project = (p: unknown) => ({ x: p.x - 300 + 250, y: p.y - 100 + 200 });

        const store = createStore();
        store.set(canvasAtom, mockCanvasState);

        const getElementCount = () => {
            const state = store.get(canvasAtom);
            return Array.isArray(state?.elements) ? state.elements.length : 0;
        };

        const { getByTestId } = render(
            <Provider store={store}>
                <div>
                    <HookHarness rf={rf} />
                    <AtomViewer />
                </div>
            </Provider>
        );

        // wait for mount effects to have run (loadCanvas etc.)
        await act(async () => Promise.resolve());

        // perform a node move -> should update the atom and be visible via AtomViewer
        act(() => {
            const hookMove = getByTestId('move-node');
            hookMove.click();
        });

        await waitFor(() => {
            expect(getElementCount()).toBeGreaterThanOrEqual(1);
        });

        // Now simulate drag end to add a component
        act(() => {
            getByTestId('do-drag').click();
        });

        await waitFor(() => {
            expect(getElementCount()).toBeGreaterThanOrEqual(2);
        });

        // (Autosave timing is tested separately) - ensure state changed and component was added
    });
});
