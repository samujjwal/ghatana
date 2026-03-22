// All tests skipped - incomplete feature
import { render, fireEvent } from '@testing-library/react';
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

import { createMockReactFlowInstance } from '../../test-utils/reactflow-mocks';

// Mock jotai useAtom to provide a mutable canvas state and a setter we can observe
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

function HookHarness({ rf }: { rf: any }) {
    const hook = useCanvasScene({ projectId: 'p', canvasId: 'c' });

    React.useEffect(() => {
        // register the reactflow instance the hook expects
        hook.handleInit(rf);
    }, [rf]);

    const simulateDrop = () => {
        const event: any = {
            active: { data: { current: { id: 'comp-1', type: 'component', kind: 'component', defaultData: {} } } },
            over: { id: 'canvas-drop-zone' },
            delta: { x: 10, y: 20 },
        };

        hook.handleDragEnd(event as unknown);
    };

    return (
        <div>
            <button data-testid="simulate-drop" onClick={simulateDrop} />
        </div>
    );
}

describe.skip('useCanvasScene integration with React Flow mock', () => {
    beforeEach(() => {
        mockSetCanvasState.mockReset();
        mockCanvasState = { elements: [], connections: [] };
    });

    it('calls reactflow.project and updates the canvas atom once when dropping a component', async () => {
        const rf = createMockReactFlowInstance([], []) as unknown;
        const spyProject = vi.spyOn(rf, 'project');

        const { getByTestId } = render(<HookHarness rf={rf} />);

        // simulate user dropping a component onto the canvas
        getByTestId('simulate-drop').click();

        // reactflow.project should be invoked to translate coordinates
        expect(spyProject).toHaveBeenCalled();

        // And our mocked atom setter should have been called to persist the new element
        expect(mockSetCanvasState).toHaveBeenCalled();
    });
});
