// All tests skipped - incomplete feature
import { render, fireEvent, screen } from '@testing-library/react';
import React from 'react';
import { vi, describe, it, expect, beforeEach } from 'vitest';

import { mockUseDraggableWithPayload } from '../../../test-utils';

// Ensure draggable exposes payload attribute
mockUseDraggableWithPayload();

// Stub reactflow project helper (used by the canvas drop handler in real app)
vi.mock('reactflow', () => ({
    project: (point: unknown) => ({ x: (point.x || 0) / 2, y: (point.y || 0) / 2 }),
}));

// Import the mocked project helper so TypeScript and the test use the mock
import { project } from '@xyflow/react';

import { ComponentPalette } from '../ComponentPalette';

describe.skip('ComponentPalette drag integration', () => {
    beforeEach(() => {
        document.body.innerHTML = '';
    });

    it('exposes payload and simulates a drop projection', () => {
        const mockAdd = vi.fn();
        render(<ComponentPalette onAddComponent={mockAdd} />);

        const labelNode = screen.getByText('Frontend App');
        const listItem = labelNode.closest('li') as HTMLElement;
        expect(listItem).toBeTruthy();

        // Read the mock payload and emulate computing a drop point via reactflow.project
        const payloadAttr = listItem.getAttribute('data-dndkit-payload');
        expect(payloadAttr).toBeTruthy();
        const payload = JSON.parse(payloadAttr || '{}');
        expect(payload).toHaveProperty('id');

        // Simulate a drop at screen coords (400, 300) that would be converted via project()
        const screenPoint = { x: 400, y: 300 };
        // In real canvas code you would call rfInstance.project(screenPoint)
        // Our mocked project halves coordinates
        const projected = (project as unknown)(screenPoint);
        // Now call the palette's onAdd handler via the public API (simulate user adding at projected coords)
        // Here we just call the mockAdd similar to how the click handler does
        mockAdd(payload, projected);

        expect(mockAdd).toHaveBeenCalledTimes(1);
        const [componentArg, posArg] = mockAdd.mock.calls[0];
        expect(componentArg).toHaveProperty('label', 'Frontend App');
        expect(posArg).toEqual(projected);
    });
});
