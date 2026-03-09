/**
 * Alignment Toolbar
 *
 * Context-sensitive panel that appears when two or more nodes are selected.
 * Provides six alignment actions (left, center-x, right, top, center-y, bottom)
 * each implemented as an `AlignNodesCommand` so alignment participates fully
 * in undo/redo.
 *
 * Rendered inside a ReactFlow `<Panel>` by CanvasWorkspace.
 *
 * @doc.type component
 * @doc.purpose Node alignment toolbar for canvas multi-selection
 * @doc.layer product
 * @doc.pattern Toolbar
 */

import React, { useCallback } from 'react';
import { useAtomValue, useSetAtom } from 'jotai';
import { type Node } from '@xyflow/react';
import {
    AlignLeft,
    AlignCenterVertical,
    AlignRight,
    AlignStartHorizontal,
    AlignCenterHorizontal,
    AlignEndHorizontal,
} from 'lucide-react';
import { IconButton, Tooltip, Divider, Box } from '@ghatana/ui';
import { selectedNodesAtom, nodesAtom } from '../workspace';
import { executeCommandAtom, AlignNodesCommand } from '../workspace/canvasCommands';

// ============================================================================
// Alignment computation helpers
// ============================================================================

type AlignAxis = 'left' | 'center-x' | 'right' | 'top' | 'center-y' | 'bottom';

function computeAlignedPositions(
    nodeIds: string[],
    nodes: Node[],
    axis: AlignAxis,
): Record<string, { x: number; y: number }> {
    const targets = nodes.filter((n) => nodeIds.includes(n.id));
    if (targets.length === 0) return {};

    const w = (n: Node) => n.measured?.width ?? 120;
    const h = (n: Node) => n.measured?.height ?? 40;

    const after: Record<string, { x: number; y: number }> = {};

    switch (axis) {
        case 'left': {
            const minX = Math.min(...targets.map((n) => n.position.x));
            targets.forEach((n) => { after[n.id] = { x: minX, y: n.position.y }; });
            break;
        }
        case 'right': {
            const maxRight = Math.max(...targets.map((n) => n.position.x + w(n)));
            targets.forEach((n) => { after[n.id] = { x: maxRight - w(n), y: n.position.y }; });
            break;
        }
        case 'center-x': {
            // Align horizontal midpoints to the average midpoint
            const avgCenterX = targets.reduce((acc, n) => acc + n.position.x + w(n) / 2, 0) / targets.length;
            targets.forEach((n) => { after[n.id] = { x: avgCenterX - w(n) / 2, y: n.position.y }; });
            break;
        }
        case 'top': {
            const minY = Math.min(...targets.map((n) => n.position.y));
            targets.forEach((n) => { after[n.id] = { x: n.position.x, y: minY }; });
            break;
        }
        case 'bottom': {
            const maxBottom = Math.max(...targets.map((n) => n.position.y + h(n)));
            targets.forEach((n) => { after[n.id] = { x: n.position.x, y: maxBottom - h(n) }; });
            break;
        }
        case 'center-y': {
            // Align vertical midpoints to the average midpoint
            const avgCenterY = targets.reduce((acc, n) => acc + n.position.y + h(n) / 2, 0) / targets.length;
            targets.forEach((n) => { after[n.id] = { x: n.position.x, y: avgCenterY - h(n) / 2 }; });
            break;
        }
    }

    return after;
}

// ============================================================================
// Component
// ============================================================================

/**
 * Alignment toolbar — visible only when ≥2 nodes are selected.
 */
export function AlignmentToolbar() {
    const selectedNodes = useAtomValue(selectedNodesAtom);
    const nodes = useAtomValue(nodesAtom);
    const executeCommand = useSetAtom(executeCommandAtom);

    const handleAlign = useCallback(
        (axis: AlignAxis) => {
            if (selectedNodes.length < 2) return;

            // Capture before-positions for undo
            const before: Record<string, { x: number; y: number }> = {};
            nodes.forEach((n) => {
                if (selectedNodes.includes(n.id)) {
                    before[n.id] = { x: n.position.x, y: n.position.y };
                }
            });

            const after = computeAlignedPositions(selectedNodes, nodes, axis);
            if (Object.keys(after).length === 0) return;

            executeCommand(new AlignNodesCommand(selectedNodes, axis, before, after));
        },
        [selectedNodes, nodes, executeCommand],
    );

    // Hide toolbar when fewer than 2 nodes are selected
    if (selectedNodes.length < 2) return null;

    const ACTIONS: { axis: AlignAxis; label: string; Icon: React.FC<{ size?: number }> }[] = [
        { axis: 'left',     label: 'Align left edges',             Icon: AlignLeft },
        { axis: 'center-x', label: 'Align horizontal centres',     Icon: AlignCenterVertical },
        { axis: 'right',    label: 'Align right edges',            Icon: AlignRight },
        { axis: 'top',      label: 'Align top edges',              Icon: AlignStartHorizontal },
        { axis: 'center-y', label: 'Align vertical centres',       Icon: AlignCenterHorizontal },
        { axis: 'bottom',   label: 'Align bottom edges',           Icon: AlignEndHorizontal },
    ];

    return (
        <Box
            role="toolbar"
            aria-label={`Alignment — ${selectedNodes.length} nodes selected`}
            className="flex items-center gap-0.5 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-md px-1 py-1"
        >
            {ACTIONS.map(({ axis, label, Icon }, i) => (
                <React.Fragment key={axis}>
                    {i === 3 && (
                        <Divider orientation="vertical" className="h-5 mx-1" />
                    )}
                    <Tooltip content={label}>
                        <IconButton
                            size="sm"
                            aria-label={label}
                            onClick={() => handleAlign(axis)}
                        >
                            <Icon size={16} />
                        </IconButton>
                    </Tooltip>
                </React.Fragment>
            ))}
        </Box>
    );
}
