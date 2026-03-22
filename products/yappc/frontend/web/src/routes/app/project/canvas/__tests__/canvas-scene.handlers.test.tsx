// All tests skipped - incomplete feature
import { render, act } from '@testing-library/react';
import { Provider } from 'jotai';
import React from 'react';

// We stub ReactFlow to a minimal component that accepts props and exposes handlers via refs

// Use a jest-like fake timer environment via vitest
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';

import CanvasScene from '../CanvasScene';

// Minimal stub for reactflow that forwards calls to supplied props.
vi.mock('@xyflow/react', async () => {
    const actual = (await vi.importActual('@xyflow/react')) as typeof import('@xyflow/react');
    // Provide a lightweight ReactFlow replacement for tests
    return {
        __esModule: true,
        ...(actual || {}),
        ReactFlow: (props: unknown) => {
            const { children } = props;

            // Ensure handlers are exposed synchronously before render returns
            (globalThis as unknown).__TEST_RF_HANDLERS__ = {
                onNodesChange: props.onNodesChange,
                onEdgesChange: props.onEdgesChange,
                onSelectionChange: props.onSelectionChange,
            };

            return React.createElement('div', {}, children);
        },
        Background: (p: unknown) => React.createElement('div', {}, null),
        Controls: (p: unknown) => React.createElement('div', {}, null),
        MiniMap: (p: unknown) => React.createElement('div', {}, null),
        Panel: (p: unknown) => React.createElement('div', {}, null),
    };
});

vi.mock('@reactflow/background', () => ({
    __esModule: true,
    Background: () => React.createElement('div'),
}));

vi.mock('@reactflow/controls', () => ({
    __esModule: true,
    Controls: () => React.createElement('div'),
}));

vi.mock('@reactflow/minimap', () => ({
    __esModule: true,
    MiniMap: () => React.createElement('div'),
}));

describe.skip('CanvasScene handlers stability', () => {
    beforeEach(() => {
        // Clear any global handlers
        (globalThis as unknown).__TEST_RF_HANDLERS__ = undefined;
    });

    it('does not enter an infinite update loop when handlers are invoked repeatedly', async () => {
        // Render the scene
        const utils = render(
            React.createElement(Provider, {}, React.createElement(CanvasScene)),
        );

        // Wait a tick for any mounts
        await act(async () => Promise.resolve());

        const handlers = (globalThis as unknown).__TEST_RF_HANDLERS__;
        expect(handlers).toBeDefined();

        // Prepare a sample node change (position update)
        const sampleNodeChange = [{ id: 'node-1', type: 'position', position: { x: 10, y: 20 } }];

        // Call the handler many times synchronously to simulate a noisy environment
        // The internal change detection should prevent repeated writes after the first effective update.
        for (let i = 0; i < 10; i++) {
            await act(async () => {
                handlers.onNodesChange?.(sampleNodeChange);
            });
        }

        // If we reach here without throwing a React "Maximum update depth exceeded" error the test passes
        expect(true).toBe(true);

        utils.unmount();
    });
});
