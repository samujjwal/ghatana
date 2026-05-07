import { fireEvent, render, screen } from '@testing-library/react';
import type { Node } from '@xyflow/react';
import { describe, expect, it, vi } from 'vitest';

import { NodePropertiesPanel } from '../NodePropertiesPanel';

describe('NodePropertiesPanel', () => {
    it('edits text node properties through shared form primitives', () => {
        const onNodeUpdate = vi.fn();
        const onNodeDelete = vi.fn();
        const onClose = vi.fn();
        const selectedNode: Node<Record<string, unknown>> = {
            id: 'node-12345678',
            type: 'text',
            position: { x: 0, y: 0 },
            data: {
                text: 'Original text',
                fontSize: 16,
                color: '#333333',
            },
        };

        render(
            <NodePropertiesPanel
                selectedNode={selectedNode}
                onNodeUpdate={onNodeUpdate}
                onNodeDelete={onNodeDelete}
                onClose={onClose}
            />,
        );

        fireEvent.change(screen.getByLabelText('Text'), { target: { value: 'Updated text' } });
        fireEvent.change(screen.getByLabelText('Font Size: 16'), { target: { value: '24' } });
        fireEvent.change(screen.getByLabelText('Color'), { target: { value: '#111111' } });
        fireEvent.click(screen.getByRole('button', { name: /save changes/i }));

        expect(onNodeUpdate).toHaveBeenCalledWith('node-12345678', {
            text: 'Updated text',
            fontSize: 24,
            color: '#111111',
        });
    });

    it('edits sticky note text through the shared textarea primitive', () => {
        const onNodeUpdate = vi.fn();
        const selectedNode: Node<Record<string, unknown>> = {
            id: 'sticky-12345678',
            type: 'sticky-note',
            position: { x: 0, y: 0 },
            data: {
                text: 'Remember this',
                color: '#fff9c4',
            },
        };

        render(
            <NodePropertiesPanel
                selectedNode={selectedNode}
                onNodeUpdate={onNodeUpdate}
                onNodeDelete={vi.fn()}
                onClose={vi.fn()}
            />,
        );

        fireEvent.change(screen.getByLabelText('Text'), { target: { value: 'Remember that' } });
        fireEvent.click(screen.getByRole('button', { name: /save changes/i }));

        expect(onNodeUpdate).toHaveBeenCalledWith('sticky-12345678', {
            text: 'Remember that',
            color: '#fff9c4',
        });
    });
});
