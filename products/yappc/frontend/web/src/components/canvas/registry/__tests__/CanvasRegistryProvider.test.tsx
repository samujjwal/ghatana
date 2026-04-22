/**
 * Canvas Registry Provider Tests
 *
 * @doc.type test
 * @doc.purpose Verify production canvas registry contains page-designer node type
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('../../nodes/ArtifactNode', () => ({
    ArtifactNode: () => <div data-testid="artifact-node" />,
}));

vi.mock('../../unified/SimpleUnifiedNode', () => ({
    SimpleUnifiedNode: () => <div data-testid="simple-unified-node" />,
}));

vi.mock('../../nodes/DiagramNode', () => ({
    DiagramNode: () => <div data-testid="diagram-node" />,
}));

vi.mock('../../nodes/MonacoNode', () => ({
    MonacoNode: () => <div data-testid="monaco-node" />,
}));

vi.mock('../../nodes/GroupNode', () => ({
    GroupNode: () => <div data-testid="group-node" />,
}));

vi.mock('../../nodes/PageDesignerNode', () => ({
    PageDesignerNode: () => <div data-testid="page-designer-node" />,
}));

vi.mock('../../edges', () => ({
    DependencyEdge: () => <div data-testid="dependency-edge" />,
}));

import { CanvasRegistryProvider, useCanvasRegistry } from '../CanvasRegistryProvider';

function RegistryProbe(): JSX.Element {
    const { nodeTypes } = useCanvasRegistry();
    const hasPageDesigner = Object.prototype.hasOwnProperty.call(nodeTypes, 'page-designer');
    return <div data-testid="registry-probe">{String(hasPageDesigner)}</div>;
}

describe('CanvasRegistryProvider', () => {
    it('registers page-designer node type for production ReactFlow canvas', () => {
        render(
            <CanvasRegistryProvider>
                <RegistryProbe />
            </CanvasRegistryProvider>,
        );

        expect(screen.getByTestId('registry-probe').textContent).toBe('true');
    });
});
