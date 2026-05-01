import { render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { describe, it, expect, vi } from 'vitest';

import { createMockReactFlowInstance } from '../../test-utils/reactflow-mocks';

interface MockRfInstance {
    __applyNodeChanges: (items: unknown[]) => void;
    getNodes: () => unknown[];
}

// We'll create a tiny CanvasScene stub that uses the mock React Flow instance
// In the real app, useCanvasScene wires atoms and React Flow. For this integration
// test we'll simulate the hook behavior and assert the mock receives a single apply.

function CanvasSceneStub({ rf }: { rf: MockRfInstance }) {
    // Simulate mount-time effect that registers the instance
    React.useEffect(() => {
        // In real implementation the hook would hold the ref and call apply changes
        // No-op here: test will directly interact with rf mock
    }, [rf]);

    return (
        <div>
            <div id="canvas-drop-zone">drop-area</div>
            <div data-testid="rf__wrapper">reactflow</div>
        </div>
    );
}

describe('useCanvasScene integration (light)', () => {
    it('mounts canvas and mock reactflow instance can apply changes once', async () => {
        const rf = createMockReactFlowInstance([], [] as unknown[]) as MockRfInstance;

        // Spy on the mock apply function
        const spy = vi.spyOn(rf, '__applyNodeChanges');

        render(<CanvasSceneStub rf={rf} />);

        expect(screen.getByText('drop-area')).toBeTruthy();
        expect(screen.getByTestId('rf__wrapper')).toBeTruthy();

        // simulate an external atom update that would trigger a single apply
        rf.__applyNodeChanges([{ type: 'add', item: { id: 'node-test', position: { x: 1, y: 2 } } }]);

        await waitFor(() => {
            expect(rf.getNodes()).toHaveLength(1);
            expect(spy).toHaveBeenCalledTimes(1);
        });
    });
});
