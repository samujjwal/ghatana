import React, { useMemo } from 'react';
import { ReactFlow } from '@xyflow/react';
import { nodeTypes as importedNodeTypes } from './nodeTypes';

const SNAP_GRID: [number, number] = [15, 15];

// Memoized ReactFlow wrapper to prevent React Flow warning about nodeTypes changes
interface ReactFlowWrapperProps {
    nodes: unknown[];
    edges: unknown[];
    onInit: unknown;
    onNodesChange: unknown;
    onEdgesChange: unknown;
    onConnect: unknown;
    onSelectionChange: unknown;
    onNodeDoubleClick: unknown;
    children: React.ReactNode;
}

export const ReactFlowWrapper = React.memo(
    ({
        nodes,
        edges,
        onInit,
        onNodesChange,
        onEdgesChange,
        onConnect,
        onSelectionChange,
        onNodeDoubleClick,
        children,
    }: ReactFlowWrapperProps) => {
        // Memoize nodeTypes to ensure stable reference across renders
        const nodeTypes = useMemo(() => importedNodeTypes, []);

        return (
            <ReactFlow
                id="canvas-flow"
                data-testid="canvas-flow"
                nodes={nodes}
                edges={edges}
                nodeTypes={nodeTypes}
                onInit={onInit}
                onNodesChange={onNodesChange}
                onEdgesChange={onEdgesChange}
                onConnect={onConnect}
                onSelectionChange={onSelectionChange}
                onNodeDoubleClick={onNodeDoubleClick}
                nodesDraggable
                nodesConnectable
                elementsSelectable
                selectNodesOnDrag={false}
                panOnScroll
                panOnDrag
                zoomOnScroll
                zoomOnPinch
                zoomOnDoubleClick
                preventScrolling
                snapToGrid
                snapGrid={SNAP_GRID}
            >
                {children}
            </ReactFlow>
        );
    },
    (prevProps, nextProps) => {
        // Custom comparison: only re-render if nodes, edges, or children change
        return (
            prevProps.nodes === nextProps.nodes &&
            prevProps.edges === nextProps.edges &&
            prevProps.children === nextProps.children &&
            prevProps.onInit === nextProps.onInit &&
            prevProps.onNodesChange === nextProps.onNodesChange &&
            prevProps.onEdgesChange === nextProps.onEdgesChange &&
            prevProps.onConnect === nextProps.onConnect &&
            prevProps.onSelectionChange === nextProps.onSelectionChange &&
            prevProps.onNodeDoubleClick === nextProps.onNodeDoubleClick
        );
    }
);

ReactFlowWrapper.displayName = 'ReactFlowWrapper';
