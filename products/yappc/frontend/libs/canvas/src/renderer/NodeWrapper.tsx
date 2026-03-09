/**
 * Node Wrapper
 *
 * Wraps individual nodes with selection, resize, and interaction UI.
 * Handles node transforms and interaction state.
 *
 * @doc.type component
 * @doc.purpose Node container with interaction UI
 * @doc.layer core
 * @doc.pattern Decorator Component
 */

import React, {
    useMemo,
    useCallback,
    useState,
    useRef,
    type ReactNode,
    type MouseEvent,
    type CSSProperties,
} from 'react';
import type { UniversalNode, ArtifactContract } from '../model/contracts';
import type { NodeRenderState } from './types';

// ============================================================================
// Node Wrapper Types
// ============================================================================

/**
 * Resize handle positions
 */
export type ResizeHandle =
    | 'n'
    | 'ne'
    | 'e'
    | 'se'
    | 's'
    | 'sw'
    | 'w'
    | 'nw';

/**
 * Node wrapper props
 */
export interface NodeWrapperProps {
    /** The node to render */
    node: UniversalNode;
    /** Artifact contract for the node */
    contract?: ArtifactContract;
    /** Node render state */
    state: NodeRenderState;
    /** Current zoom level */
    zoom: number;
    /** Children to render inside the node */
    children?: ReactNode;
    /** Mouse down handler */
    onMouseDown?: (e: MouseEvent) => void;
    /** Click handler */
    onClick?: (e: MouseEvent) => void;
    /** Double-click handler */
    onDoubleClick?: (e: MouseEvent) => void;
    /** Context menu handler */
    onContextMenu?: (e: MouseEvent) => void;
    /** Mouse enter handler */
    onMouseEnter?: () => void;
    /** Mouse leave handler */
    onMouseLeave?: () => void;
    /** Resize start handler */
    onResizeStart?: (handle: ResizeHandle, e: MouseEvent) => void;
    /** Rotation start handler */
    onRotateStart?: (e: MouseEvent) => void;
}

// ============================================================================
// Node Wrapper Component
// ============================================================================

/**
 * Node Wrapper Component
 *
 * Provides the visual container and interaction handles for a node.
 */
export const NodeWrapper: React.FC<NodeWrapperProps> = ({
    node,
    contract,
    state,
    zoom,
    children,
    onMouseDown,
    onClick,
    onDoubleClick,
    onContextMenu,
    onMouseEnter,
    onMouseLeave,
    onResizeStart,
    onRotateStart,
}) => {
    const [isHoveringHandle, setIsHoveringHandle] = useState(false);
    const wrapperRef = useRef<HTMLDivElement>(null);

    // Calculate styles
    const wrapperStyle = useMemo((): CSSProperties => {
        const { x, y, width, height, rotation, zIndex } = node.transform;

        return {
            position: 'absolute',
            left: x,
            top: y,
            width,
            height,
            transform: rotation ? `rotate(${rotation}deg)` : undefined,
            zIndex: zIndex ?? 0,
            cursor: node.locked ? 'not-allowed' : 'move',
            userSelect: 'none',
            // Apply node styles
            ...Object.fromEntries(
                Object.entries(node.style).filter(([, v]) => v !== undefined)
            ),
        };
    }, [node.transform, node.style, node.locked]);

    // Selection outline style
    const outlineStyle = useMemo((): CSSProperties | null => {
        if (!state.selected && !state.hovered) return null;

        return {
            position: 'absolute',
            top: -2,
            left: -2,
            right: -2,
            bottom: -2,
            border: state.selected
                ? `2px solid ${state.primary ? '#6366f1' : '#a5b4fc'}`
                : '2px solid rgba(99, 102, 241, 0.5)',
            borderRadius: 2,
            pointerEvents: 'none',
        };
    }, [state.selected, state.hovered, state.primary]);

    // Whether to show resize handles
    const showResizeHandles = useMemo(() => {
        return (
            state.selected &&
            !node.locked &&
            (contract?.capabilities.resizable ?? true)
        );
    }, [state.selected, node.locked, contract]);

    // Handle resize start
    const handleResizeMouseDown = useCallback(
        (handle: ResizeHandle) => (e: MouseEvent) => {
            e.stopPropagation();
            onResizeStart?.(handle, e);
        },
        [onResizeStart]
    );

    // Handle rotation start
    const handleRotateMouseDown = useCallback(
        (e: MouseEvent) => {
            e.stopPropagation();
            onRotateStart?.(e);
        },
        [onRotateStart]
    );

    // Resize handle size (scaled by zoom)
    const handleSize = Math.max(6, 8 / zoom);

    // Render resize handles
    const renderResizeHandles = useCallback(() => {
        const handles: Array<{ position: ResizeHandle; style: CSSProperties }> = [
            { position: 'nw', style: { top: -handleSize / 2, left: -handleSize / 2, cursor: 'nwse-resize' } },
            { position: 'n', style: { top: -handleSize / 2, left: '50%', marginLeft: -handleSize / 2, cursor: 'ns-resize' } },
            { position: 'ne', style: { top: -handleSize / 2, right: -handleSize / 2, cursor: 'nesw-resize' } },
            { position: 'e', style: { top: '50%', marginTop: -handleSize / 2, right: -handleSize / 2, cursor: 'ew-resize' } },
            { position: 'se', style: { bottom: -handleSize / 2, right: -handleSize / 2, cursor: 'nwse-resize' } },
            { position: 's', style: { bottom: -handleSize / 2, left: '50%', marginLeft: -handleSize / 2, cursor: 'ns-resize' } },
            { position: 'sw', style: { bottom: -handleSize / 2, left: -handleSize / 2, cursor: 'nesw-resize' } },
            { position: 'w', style: { top: '50%', marginTop: -handleSize / 2, left: -handleSize / 2, cursor: 'ew-resize' } },
        ];

        return handles.map(({ position, style }) => (
            <div
                key={position}
                style={{
                    position: 'absolute',
                    width: handleSize,
                    height: handleSize,
                    backgroundColor: '#ffffff',
                    border: '1px solid #6366f1',
                    borderRadius: 1,
                    ...style,
                }}
                onMouseDown={handleResizeMouseDown(position)}
                onMouseEnter={() => setIsHoveringHandle(true)}
                onMouseLeave={() => setIsHoveringHandle(false)}
            />
        ));
    }, [handleSize, handleResizeMouseDown]);

    // Render rotation handle
    const renderRotationHandle = useCallback(() => {
        return (
            <div
                style={{
                    position: 'absolute',
                    top: -24,
                    left: '50%',
                    marginLeft: -4,
                    width: 8,
                    height: 8,
                    backgroundColor: '#ffffff',
                    border: '1px solid #6366f1',
                    borderRadius: '50%',
                    cursor: 'grab',
                }}
                onMouseDown={handleRotateMouseDown}
                onMouseEnter={() => setIsHoveringHandle(true)}
                onMouseLeave={() => setIsHoveringHandle(false)}
            />
        );
    }, [handleRotateMouseDown]);

    // Lock indicator
    const renderLockIndicator = useCallback(() => {
        if (!node.locked) return null;

        return (
            <div
                style={{
                    position: 'absolute',
                    top: -20,
                    right: -20,
                    width: 16,
                    height: 16,
                    backgroundColor: '#fef3c7',
                    border: '1px solid #f59e0b',
                    borderRadius: '50%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 10,
                }}
            >
                🔒
            </div>
        );
    }, [node.locked]);

    // Annotation indicator
    const renderAnnotationIndicator = useCallback(() => {
        if (!node.annotations || node.annotations.length === 0) return null;

        const unresolved = node.annotations.filter((a) => !a.resolved).length;

        return (
            <div
                style={{
                    position: 'absolute',
                    top: -8,
                    right: -8,
                    minWidth: 16,
                    height: 16,
                    backgroundColor: unresolved > 0 ? '#ef4444' : '#22c55e',
                    color: '#ffffff',
                    borderRadius: 8,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 10,
                    fontWeight: 'bold',
                    padding: '0 4px',
                }}
            >
                {node.annotations.length}
            </div>
        );
    }, [node.annotations]);

    return (
        <div
            ref={wrapperRef}
            style={wrapperStyle}
            onMouseDown={onMouseDown}
            onClick={onClick}
            onDoubleClick={onDoubleClick}
            onContextMenu={onContextMenu}
            onMouseEnter={onMouseEnter}
            onMouseLeave={onMouseLeave}
            data-node-id={node.id}
            data-node-kind={node.kind}
        >
            {/* Selection/hover outline */}
            {outlineStyle && <div style={outlineStyle} />}

            {/* Node content */}
            <div
                style={{
                    width: '100%',
                    height: '100%',
                    overflow: 'hidden',
                    pointerEvents: state.editing ? 'auto' : 'none',
                }}
            >
                {children || (
                    <DefaultNodeContent node={node} contract={contract} state={state} />
                )}
            </div>

            {/* Resize handles */}
            {showResizeHandles && renderResizeHandles()}

            {/* Rotation handle */}
            {showResizeHandles && node.transform.rotation !== undefined && renderRotationHandle()}

            {/* Lock indicator */}
            {renderLockIndicator()}

            {/* Annotation indicator */}
            {renderAnnotationIndicator()}

            {/* Node name label (on hover or select) */}
            {(state.hovered || state.selected) && !state.editing && (
                <div
                    style={{
                        position: 'absolute',
                        bottom: '100%',
                        left: 0,
                        marginBottom: 4,
                        padding: '2px 6px',
                        backgroundColor: 'rgba(0, 0, 0, 0.7)',
                        color: '#ffffff',
                        fontSize: 10,
                        borderRadius: 2,
                        whiteSpace: 'nowrap',
                        pointerEvents: 'none',
                    }}
                >
                    {node.name}
                </div>
            )}
        </div>
    );
};

// ============================================================================
// Default Node Content
// ============================================================================

/**
 * Default content renderer for nodes without custom renderers
 */
const DefaultNodeContent: React.FC<{
    node: UniversalNode;
    contract?: ArtifactContract;
    state: NodeRenderState;
}> = ({ node, contract, state }) => {
    const style: CSSProperties = {
        width: '100%',
        height: '100%',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#f1f5f9',
        border: '1px solid #e2e8f0',
        borderRadius: 4,
        fontSize: 12,
        color: '#64748b',
        textAlign: 'center',
        padding: 8,
    };

    // Apply some basic props as content
    const label =
        (node.props.label as string) ||
        (node.props.title as string) ||
        (node.props.text as string) ||
        node.name;

    return (
        <div style={style}>
            <div>
                <div style={{ marginBottom: 4, fontSize: 10, opacity: 0.6 }}>
                    {contract?.identity.name || node.kind}
                </div>
                <div style={{ fontWeight: 500 }}>{label}</div>
            </div>
        </div>
    );
};

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Calculate resize delta based on handle and mouse movement
 */
export function calculateResizeDelta(
    handle: ResizeHandle,
    deltaX: number,
    deltaY: number,
    aspectRatio?: number
): { dx: number; dy: number; dw: number; dh: number } {
    let dx = 0;
    let dy = 0;
    let dw = 0;
    let dh = 0;

    // Handle horizontal resizing
    if (handle.includes('w')) {
        dx = deltaX;
        dw = -deltaX;
    } else if (handle.includes('e')) {
        dw = deltaX;
    }

    // Handle vertical resizing
    if (handle.includes('n')) {
        dy = deltaY;
        dh = -deltaY;
    } else if (handle.includes('s')) {
        dh = deltaY;
    }

    // Apply aspect ratio constraint
    if (aspectRatio && dw !== 0 && dh !== 0) {
        const newHeight = dw / aspectRatio;
        if (Math.abs(newHeight) > Math.abs(dh)) {
            dh = newHeight;
            if (handle.includes('n')) {
                dy = -dh;
            }
        } else {
            dw = dh * aspectRatio;
            if (handle.includes('w')) {
                dx = -dw;
            }
        }
    }

    return { dx, dy, dw, dh };
}

export default NodeWrapper;
