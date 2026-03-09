/**
 * GroupNode (Frame Node)
 *
 * A transparent frame / container node that visually groups related child nodes.
 * Children are placed inside this node by setting their `parentId` to this
 * node's id, and their `extent` to `'parent'` to constrain them within the frame.
 *
 * Features:
 *   - Dashed border indicating the frame boundary
 *   - Editable label in the top-left corner
 *   - NodeResizer with zoom-aware handles
 *   - Full pointer event pass-through so the canvas is panning/zooming
 *     when the user clicks the empty interior (children are still draggable)
 *   - Dark mode aware via Tailwind's `dark:` variant
 *
 * Usage (creating a grouped layout):
 * ```ts
 * const frame: Node = {
 *   id: 'frame-1',
 *   type: 'group',
 *   position: { x: 100, y: 100 },
 *   style: { width: 400, height: 300 },
 *   data: { label: 'Domain Layer' },
 * };
 * const child: Node = {
 *   id: 'artifact-1',
 *   type: 'artifact',
 *   parentId: 'frame-1',   // ← sets ownership
 *   extent: 'parent',      // ← constrains drag within the frame
 *   position: { x: 20, y: 40 },
 *   data: { ... },
 * };
 * ```
 *
 * @doc.type component
 * @doc.purpose Grouping / frame node for canvas organization
 * @doc.layer product
 * @doc.pattern ContentNode
 */

import React, { useState, useCallback, useRef } from 'react';
import { type NodeProps } from '@xyflow/react';
import { NodeResizer } from '@xyflow/react';
import { useAtomValue } from 'jotai';
import { cameraZoomAtom } from '../workspace';

// ============================================================================
// Types
// ============================================================================

export interface GroupNodeData {
    /** Display label shown in the top-left corner of the frame */
    label?: string;
    /** Callback when the label is changed by the user */
    onLabelChange?: (label: string) => void;
    /** Accent colour for the border (Tailwind border colour CSS value) */
    color?: string;
    [key: string]: unknown;
}

// ============================================================================
// Component
// ============================================================================

/**
 * GroupNode — transparent frame / container node.
 *
 * The node width and height are controlled by the `style` prop that ReactFlow
 * passes from the node definition:
 * ```ts
 * style: { width: 400, height: 300 }
 * ```
 */
export function GroupNode({ data, selected }: NodeProps<GroupNodeData>) {
    const zoom = useAtomValue(cameraZoomAtom);

    const [label, setLabel] = useState(data.label ?? 'Group');
    const [isEditing, setIsEditing] = useState(false);
    const inputRef = useRef<HTMLInputElement>(null);

    /** Resize handle size stays ~8px physical regardless of canvas zoom */
    const handleSize = Math.round(8 / zoom);

    const handleLabelDoubleClick = useCallback(() => {
        setIsEditing(true);
        requestAnimationFrame(() => inputRef.current?.select());
    }, []);

    const handleLabelBlur = useCallback(() => {
        setIsEditing(false);
        data.onLabelChange?.(label);
    }, [label, data]);

    const handleLabelKeyDown = useCallback(
        (e: React.KeyboardEvent<HTMLInputElement>) => {
            if (e.key === 'Enter' || e.key === 'Escape') {
                e.currentTarget.blur();
            }
        },
        [],
    );

    /**
     * Border colour: default indigo accent, overrideable via `data.color`.
     * Uses CSS variables so it works in both light and dark mode without
     * requiring separate class branches.
     */
    const borderColor = data.color ?? 'rgba(99, 102, 241, 0.6)';
    const bgColor = data.color
        ? `${data.color.replace(')', ', 0.05)').replace('rgb', 'rgba')}`
        : 'rgba(99, 102, 241, 0.04)';

    return (
        <>
            <NodeResizer
                isVisible={selected}
                minWidth={120}
                minHeight={80}
                handleStyle={{ width: handleSize, height: handleSize }}
            />

            {/* Frame body — full width/height from ReactFlow node style */}
            <div
                className="w-full h-full rounded-lg pointer-events-none"
                style={{
                    border: `2px dashed ${borderColor}`,
                    background: bgColor,
                    boxShadow: selected
                        ? `0 0 0 2px ${borderColor}`
                        : undefined,
                }}
                aria-label={`Group: ${label}`}
            >
                {/* Label in the top-left — the only pointer-interactive area */}
                <div
                    className="absolute top-2 left-3 pointer-events-auto"
                    onDoubleClick={handleLabelDoubleClick}
                >
                    {isEditing ? (
                        <input
                            ref={inputRef}
                            className="nodrag nopan text-xs font-semibold bg-transparent border-b border-indigo-400 outline-none text-gray-700 dark:text-gray-200 min-w-[60px]"
                            value={label}
                            onChange={(e) => setLabel(e.target.value)}
                            onBlur={handleLabelBlur}
                            onKeyDown={handleLabelKeyDown}
                            aria-label="Group label"
                        />
                    ) : (
                        <span
                            className="text-xs font-semibold text-gray-500 dark:text-gray-400 select-none cursor-text"
                            title="Double-click to rename"
                            tabIndex={0}
                            role="button"
                            aria-label={`Group label: ${label}. Double-click to rename.`}
                            onKeyDown={(e) => {
                                if (e.key === 'Enter' || e.key === ' ') handleLabelDoubleClick();
                            }}
                        >
                            {label}
                        </span>
                    )}
                </div>
            </div>
        </>
    );
}
