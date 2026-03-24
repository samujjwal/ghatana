/**
 * Canvas Surface
 *
 * Renders the node tree within the infinite viewport.
 * Handles node rendering, selection, and layout.
 *
 * @doc.type component
 * @doc.purpose Node tree rendering surface
 * @doc.layer core
 * @doc.pattern Container Component
 */

import React, {
    useMemo,
    useCallback,
    type ReactNode,
    type MouseEvent,
} from 'react';
import type { UniversalNode, UniqueId, ArtifactContract } from '../model/contracts';
import { NodeWrapper } from './NodeWrapper';
import type { SelectionState, NodeRenderState, CanvasSurfaceProps } from './types';

// Re-export types for backward compatibility
export type { SelectionState, NodeRenderState, CanvasSurfaceProps };

// ============================================================================
// Canvas Surface Types
// ============================================================================

// ============================================================================
// Canvas Surface Component
// ============================================================================

/**
 * Canvas Surface Component
 *
 * Renders the node tree for display in the viewport.
 */
export const CanvasSurface: React.FC<CanvasSurfaceProps> = ({
    nodes,
    rootIds,
    getContract,
    selection,
    zoom,
    onNodeClick,
    onNodeDoubleClick,
    onNodeContextMenu,
    onNodeHover,
    onNodeDragStart,
    onBackgroundClick,
    renderNode,
    showGuides = true,
    gridSize = 8,
    showSelectionBounds = true,
}) => {
    // Calculate selection bounds
    const selectionBounds = useMemo(() => {
        if (selection.selectedIds.size === 0) return null;

        let minX = Infinity;
        let minY = Infinity;
        let maxX = -Infinity;
        let maxY = -Infinity;

        for (const id of selection.selectedIds) {
            const node = nodes.get(id);
            if (!node) continue;

            const { x, y, width, height } = node.transform;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + width);
            maxY = Math.max(maxY, y + height);
        }

        if (minX === Infinity) return null;

        return {
            x: minX,
            y: minY,
            width: maxX - minX,
            height: maxY - minY,
        };
    }, [selection.selectedIds, nodes]);

    // Handle background click
    const handleBackgroundClick = useCallback(
        (e: MouseEvent<HTMLDivElement>) => {
            if (e.target === e.currentTarget) {
                onBackgroundClick?.(e);
            }
        },
        [onBackgroundClick]
    );

    // Render a node and its children recursively
    const renderNodeTree = useCallback(
        (nodeId: UniqueId): ReactNode => {
            const node = nodes.get(nodeId);
            if (!node || !node.visible) return null;

            const contract = getContract(node.kind);
            const renderState: NodeRenderState = {
                selected: selection.selectedIds.has(nodeId),
                hovered: selection.hoveredId === nodeId,
                editing: selection.editingId === nodeId,
                primary: selection.primaryId === nodeId,
            };

            // Render children
            const children = node.children
                .map((childId) => renderNodeTree(childId))
                .filter(Boolean);

            return (
                <NodeWrapper
                    key={nodeId}
                    node={node}
                    contract={contract}
                    state={renderState}
                    zoom={zoom}
                    onMouseDown={(e) => {
                        if (e.button === 0) {
                            onNodeDragStart?.(nodeId, e);
                        }
                    }}
                    onClick={(e) => {
                        e.stopPropagation();
                        onNodeClick?.(nodeId, e);
                    }}
                    onDoubleClick={(e) => {
                        e.stopPropagation();
                        onNodeDoubleClick?.(nodeId, e);
                    }}
                    onContextMenu={(e) => {
                        e.stopPropagation();
                        e.preventDefault();
                        onNodeContextMenu?.(nodeId, e);
                    }}
                    onMouseEnter={() => onNodeHover?.(nodeId)}
                    onMouseLeave={() => onNodeHover?.(null)}
                >
                    {/* Custom renderer or children */}
                    {renderNode
                        ? renderNode(node, contract, renderState)
                        : children.length > 0
                            ? children
                            : null}
                </NodeWrapper>
            );
        },
        [
            nodes,
            getContract,
            selection,
            zoom,
            onNodeClick,
            onNodeDoubleClick,
            onNodeContextMenu,
            onNodeHover,
            onNodeDragStart,
            renderNode,
        ]
    );

    return (
        <div
            style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                height: '100%',
                minWidth: 5000,
                minHeight: 5000,
            }}
            onClick={handleBackgroundClick}
        >
            {/* Render root nodes */}
            {rootIds.map((id) => renderNodeTree(id))}

            {/* Selection bounds */}
            {showSelectionBounds && selectionBounds && selection.selectedIds.size > 1 && (
                <div
                    style={{
                        position: 'absolute',
                        left: selectionBounds.x - 2,
                        top: selectionBounds.y - 2,
                        width: selectionBounds.width + 4,
                        height: selectionBounds.height + 4,
                        border: '1px dashed #6366f1',
                        borderRadius: 2,
                        pointerEvents: 'none',
                    }}
                />
            )}

            {/* Alignment guides would be rendered here */}
            {showGuides && (
                <svg
                    style={{
                        position: 'absolute',
                        top: 0,
                        left: 0,
                        width: '100%',
                        height: '100%',
                        pointerEvents: 'none',
                        overflow: 'visible',
                    }}
                >
                    {/* Guides would be dynamically generated during drag */}
                </svg>
            )}
        </div>
    );
};

// ============================================================================
// Helper Components
// ============================================================================

/**
 * Empty state component
 */
export const EmptyCanvasState: React.FC<{
    onAddNode?: () => void;
}> = ({ onAddNode }) => (
    <div
        style={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
            textAlign: 'center',
            color: '#94a3b8',
        }}
    >
        <div style={{ fontSize: 48, marginBottom: 16 }}>🎨</div>
        <div style={{ fontSize: 18, marginBottom: 8 }}>Empty Canvas</div>
        <div style={{ fontSize: 14, marginBottom: 16 }}>
            Drag components from the palette or click below to get started
        </div>
        {onAddNode && (
            <button
                onClick={onAddNode}
                style={{
                    padding: '8px 16px',
                    backgroundColor: '#6366f1',
                    color: 'white',
                    border: 'none',
                    borderRadius: 6,
                    cursor: 'pointer',
                    fontSize: 14,
                }}
            >
                Add Component
            </button>
        )}
    </div>
);

/**
 * Loading state component
 */
export const LoadingCanvasState: React.FC = () => (
    <div
        style={{
            position: 'absolute',
            top: '50%',
            left: '50%',
            transform: 'translate(-50%, -50%)',
            textAlign: 'center',
            color: '#64748b',
        }}
    >
        <div
            style={{
                width: 40,
                height: 40,
                border: '3px solid #e2e8f0',
                borderTopColor: '#6366f1',
                borderRadius: '50%',
                animation: 'spin 1s linear infinite',
                margin: '0 auto 16px',
            }}
        />
        <div style={{ fontSize: 14 }}>Loading canvas...</div>
        <style>
            {`
        @keyframes spin {
          to { transform: rotate(360deg); }
        }
      `}
        </style>
    </div>
);

export default CanvasSurface;
