/**
 * Canvas DnD Hook
 *
 * Manages all drag-and-drop operations for the canvas:
 *   - Node drag with real alignment guide SNAPPING (not just display)
 *   - Drag cancelability via Escape key (restores pre-drag positions)
 *   - Custom drag image for left-panel template drops
 *   - Drop positioning from palette to canvas (screen → flow coordinates)
 *   - Position persistence via useNodePositions on drag stop
 *   - Command-pattern integration: MoveNodesCommand pushed to history
 *
 * Architecture:
 *   - Snapping: guide lines AND node positions are corrected on threshold.
 *     When a spatial-index query returns a guide position within SNAP_THRESHOLD,
 *     `reactFlowInstance.setNodes()` is called to move the dragged node to the
 *     exact guide coordinate so the visual guide matches the actual position.
 *   - Cancelability: dragStartPositionsRef captures positions at dragStart;
 *     Escape key listener during drag restores them
 *   - Spatial index is queried asynchronously; last-response-wins via
 *     lastMsgIdRef prevents stale guide flicker on rapid movement
 *
 * @doc.type hook
 * @doc.purpose Canvas drag-and-drop management
 * @doc.layer product
 * @doc.pattern Extracted Hook
 */

import { useCallback, useRef, useState, useEffect } from 'react';
import { useAtom, useSetAtom } from 'jotai';
import { type ReactFlowInstance, type Node } from '@xyflow/react';
import {
    draggedTemplateAtom,
    alignmentGuidesAtom,
    canvasAnnouncementAtom,
} from '../workspace';
import {
    executeCommandAtom,
    MoveNodesCommand,
} from '../workspace/canvasCommands';
import { spatialIndexAPI } from '../workspace/spatialIndexService';
import { useNodePositions } from './useNodePositions';
import type { ArtifactTemplate } from '../workspace';

/** Pixel distance within which a node edge snaps to an alignment guide */
const SNAP_THRESHOLD = 15;

export interface UseCanvasDragDropConfig {
    projectId: string;
    reactFlowInstance: ReactFlowInstance | null;
    interactionMode: string;
    onCreateArtifact: (template: ArtifactTemplate, position: { x: number; y: number }) => void;
}

export function useCanvasDragDrop(config: UseCanvasDragDropConfig) {
    const { projectId, reactFlowInstance, interactionMode, onCreateArtifact } = config;

    const [draggedTemplate, setDraggedTemplate] = useAtom(draggedTemplateAtom);
    const setAlignmentGuides = useSetAtom(alignmentGuidesAtom);
    const announce = useSetAtom(canvasAnnouncementAtom);
    const executeCommand = useSetAtom(executeCommandAtom);

    const { persistPositions } = useNodePositions(projectId);

    /** Whether a template drop target is active over the canvas */
    const [isDragOver, setIsDragOver] = useState(false);

    /**
     * Captures node positions at drag start so Escape can restore them.
     * Key: nodeId, Value: { x, y } before drag began.
     */
    const dragStartPositionsRef = useRef<Record<string, { x: number; y: number }>>({});

    /** True while a node drag is in progress */
    const isDraggingRef = useRef(false);

    /** Last inflight spatial-index query ID (last-wins deduplication) */
    const lastMsgIdRef = useRef(0);

    /** RAF handle — throttles guide queries to one-per-frame */
    const guideDragRafRef = useRef<number | null>(null);

    // ── Drag cancel via Escape ──────────────────────────────────────────
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key !== 'Escape' || !isDraggingRef.current || !reactFlowInstance) return;

            const restoreMap = dragStartPositionsRef.current;
            if (Object.keys(restoreMap).length === 0) return;

            reactFlowInstance.setNodes((nds) =>
                nds.map((n) =>
                    restoreMap[n.id]
                        ? { ...n, position: restoreMap[n.id], dragging: false }
                        : n
                )
            );

            dragStartPositionsRef.current = {};
            isDraggingRef.current = false;
            setAlignmentGuides({ vertical: null, horizontal: null });
            announce('Drag cancelled — nodes returned to original positions');
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [reactFlowInstance, setAlignmentGuides, announce]);

    // ── Node drag start — capture pre-drag positions ────────────────────
    const onNodeDragStart = useCallback(
        (_: React.MouseEvent, node: Node, allNodes: Node[]) => {
            isDraggingRef.current = true;
            dragStartPositionsRef.current = {};

            // Capture positions for the dragged node AND all selected nodes
            allNodes.forEach((n) => {
                if (n.selected || n.id === node.id) {
                    dragStartPositionsRef.current[n.id] = { ...n.position };
                }
            });
        },
        [],
    );

    // ── Node drag — display alignment guides AND snap node positions ────
    // RAF-throttled so the worker queue never backs up at 60 pointer-events/sec.
    // When a guide fires within SNAP_THRESHOLD, we also move the node to the
    // exact guide coordinate so visual guide ≡ actual position.
    const onNodeDrag = useCallback(
        (_: React.MouseEvent, node: Node) => {
            // Drop the pending frame so we only send one query per paint cycle
            if (guideDragRafRef.current !== null) {
                cancelAnimationFrame(guideDragRafRef.current);
            }

            guideDragRafRef.current = requestAnimationFrame(async () => {
                guideDragRafRef.current = null;
                const myMsgId = ++lastMsgIdRef.current;

                try {
                    const collisions = await spatialIndexAPI.findCollisions(node, 100);

                    // Discard stale responses if a newer query fired
                    if (myMsgId !== lastMsgIdRef.current) return;

                    const candidates = collisions.filter((c) => c.id !== node.id);
                    let vSnap: number | null = null;
                    let hSnap: number | null = null;

                    for (const target of candidates) {
                        if (vSnap === null && Math.abs(node.position.x - target.minX) < SNAP_THRESHOLD) {
                            vSnap = target.minX;
                        }
                        if (hSnap === null && Math.abs(node.position.y - target.minY) < SNAP_THRESHOLD) {
                            hSnap = target.minY;
                        }
                        if (vSnap !== null && hSnap !== null) break;
                    }

                    setAlignmentGuides({ vertical: vSnap, horizontal: hSnap });

                    // Apply positional snap: move the dragged node to the exact
                    // guide coordinate so the visual guide and actual position agree.
                    if ((vSnap !== null || hSnap !== null) && reactFlowInstance) {
                        reactFlowInstance.setNodes((nds) =>
                            nds.map((n) => {
                                if (n.id !== node.id) return n;
                                return {
                                    ...n,
                                    position: {
                                        x: vSnap !== null ? vSnap : n.position.x,
                                        y: hSnap !== null ? hSnap : n.position.y,
                                    },
                                };
                            }),
                        );
                    }
                } catch {
                    if (myMsgId !== lastMsgIdRef.current) return;
                    setAlignmentGuides({ vertical: null, horizontal: null });
                }
            });
        },
        [setAlignmentGuides, reactFlowInstance],
    );

    // ── Node drag stop — persist positions, push Command ───────────────
    const onNodeDragStop = useCallback(
        (_: React.MouseEvent, node: Node, allNodes: Node[]) => {
            isDraggingRef.current = false;
            setAlignmentGuides({ vertical: null, horizontal: null });

            const startPositions = dragStartPositionsRef.current;
            dragStartPositionsRef.current = {};

            // Collect nodes that actually moved
            const movedIds = allNodes
                .filter(
                    (n) =>
                        startPositions[n.id] &&
                        (startPositions[n.id].x !== n.position.x ||
                            startPositions[n.id].y !== n.position.y),
                )
                .map((n) => n.id);

            if (movedIds.length > 0) {
                const fromPositions: Record<string, { x: number; y: number }> = {};
                const toPositions: Record<string, { x: number; y: number }> = {};
                // Batch all position updates into a single localStorage write
                const batchPositions: Record<string, { x: number; y: number }> = {};

                allNodes.forEach((n) => {
                    if (movedIds.includes(n.id)) {
                        fromPositions[n.id] = startPositions[n.id];
                        toPositions[n.id] = { ...n.position };
                        batchPositions[n.id] = { x: n.position.x, y: n.position.y };
                    }
                });

                // Single atom update + single localStorage write (was N writes)
                persistPositions(batchPositions);

                // Command-pattern: push MoveNodesCommand (supports merge for rapid drags)
                executeCommand(new MoveNodesCommand(movedIds, fromPositions, toPositions));
            }
        },
        [persistPositions, executeCommand, setAlignmentGuides],
    );

    // ── Canvas drop (from palette) ──────────────────────────────────────
    const handleCanvasDrop = useCallback(
        (event: React.DragEvent) => {
            event.preventDefault();
            setIsDragOver(false);

            if (!draggedTemplate || !reactFlowInstance) return;

            try {
                const position = reactFlowInstance.screenToFlowPosition({
                    x: event.clientX,
                    y: event.clientY,
                });
                onCreateArtifact(draggedTemplate, position);
                setDraggedTemplate(null);
                announce(`Created ${draggedTemplate.label || 'artifact'} on canvas`);
            } catch {
                announce('Failed to place artifact on canvas');
            }
        },
        [draggedTemplate, reactFlowInstance, onCreateArtifact, setDraggedTemplate, announce],
    );

    const handleCanvasDragOver = useCallback((event: React.DragEvent) => {
        event.preventDefault();
        event.dataTransfer.dropEffect = 'copy';
        setIsDragOver(true);
    }, []);

    const handleCanvasDragLeave = useCallback(() => {
        setIsDragOver(false);
    }, []);

    return {
        onNodeDragStart,
        onNodeDrag,
        onNodeDragStop,
        handleCanvasDrop,
        handleCanvasDragOver,
        handleCanvasDragLeave,
        isDragOver,
        setDraggedTemplate,
    };
}
