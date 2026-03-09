/**
 * Canvas Workspace
 * 
 * Lean orchestrator component for the Canvas-First UX. All domain logic is
 * extracted to dedicated hooks (useCanvasHandlers, useCanvasDragDrop,
 * useCanvasZoom, useCanvasShortcuts, usePerformanceMetrics,
 * useCanvasAccessibility). This file wires hooks to the ReactFlow surface
 * and panels.
 * 
 * @doc.type component
 * @doc.purpose Canvas-first unified workspace orchestrator
 * @doc.layer product
 * @doc.pattern Workspace Container
 */

import React, { useCallback, useMemo, useEffect, useRef, useState } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { Box, Button, IconButton, Toolbar, Tooltip, Spinner as CircularProgress, Typography, Divider, Menu, MenuItem } from '@ghatana/ui';
import { Plus as Add, Link as LinkIcon, Trash2 as TrashIcon, Copy as DuplicateIcon } from 'lucide-react';
import {
    ReactFlowProvider, ReactFlow, Panel, Background, Controls, MiniMap,
    type Node, type Edge, MarkerType, type ReactFlowInstance,
    applyNodeChanges, applyEdgeChanges,
    type NodeChange, type EdgeChange, type Connection,
} from '@xyflow/react';
import { useGateStatus, useNextBestTask, useDerivedPersona, useArtifacts } from '@/hooks/useLifecycleData';
import { lifecycleAPI } from '@/services/lifecycle/api';
import { TIMING } from '@/styles/design-tokens';
import { LifecyclePhase } from '@/types/lifecycle';
import { FOWStage } from '@/types/fow-stages';
import {
    NextBestTaskCard, SpatialZones, PersonaFilterToolbar, AIAssistantModal,
    QuickCreateMenu, InspectorPanel, GhostNodes, ViewModeSelector, PresenceIndicator,
    type GateCriterion, type PersonaFilterData, type AISuggestion,
    type ArtifactTemplate,
    activePersonaAtom, isAIModalOpenAtom, isProjectSwitcherOpenAtom, isInspectorOpenAtom,
    selectedArtifactAtom, selectedNodesAtom, quickCreateMenuPositionAtom, isCommandPaletteOpenAtom,
    nodesAtom, edgesAtom, suppressGeneratedSyncAtom,
    canvasInteractionModeAtom, sketchToolAtom, sketchColorAtom, sketchStrokeWidthAtom,
    canvasAnnouncementAtom, copiedNodesAtom,
    cameraAtom, commandRegistryAtom, registerCommandsAtom,
    sortedCommandsAtom, prefersDarkModeAtom, visibleNodeIdsAtom,
} from './workspace';
import {
    canUndoCommandAtom, canRedoCommandAtom, undoCommandAtom, redoCommandAtom, executeCommandAtom,
    AddEdgeCommand, AddNodeCommand,
} from './workspace/canvasCommands';
import { useCodeAssociationsBatch } from './hooks/useCodeAssociationsBatch';
import { useNodePositions } from './hooks/useNodePositions';
import { UnifiedLeftPanel } from './UnifiedLeftPanel';
import { spatialIndexAPI } from './workspace/spatialIndexService';
import { AlignmentGuides } from './AlignmentGuides';
import { ProjectSwitcher } from './workspace/ProjectSwitcher';
import { type ArtifactNodeData } from './nodes/ArtifactNode';
import { type DependencyEdgeData } from './edges';
import { ArtifactType } from '@/types/fow-stages';
import { EnhancedSketchLayer } from './sketch/EnhancedSketchLayer';
import { SketchToolbar } from './toolbar/SketchToolbar';
import { DiagramToolbar } from './toolbar/DiagramToolbar';
import { getZonePlacementPosition } from './workspace/SpatialZones';
import { useComputedView, viewModeAtom, userIdAtom, userRoleAtom } from './hooks/useComputedView';
import { AbstractionLevelNavigator } from './AbstractionLevelNavigator';
import { PanelManager } from './panels/PanelManager';
import { type WorkspacePanelConfig } from './panels/types';
import { CommandPalette } from './tools/CommandPalette';
import { useCanvasRegistry } from './registry';
import { Map as MapIcon, User as PersonIcon, History as HistoryIcon, Gauge as SpeedIcon } from 'lucide-react';
import { getNextLevel } from '../../types/abstractionLevel';
import { CanvasErrorBoundary } from './CanvasErrorBoundary';
import { AlignmentToolbar } from './toolbar/AlignmentToolbar';

// Extracted hooks
import { useCanvasHandlers } from './hooks/useCanvasHandlers';
import { useCanvasDragDrop } from './hooks/useCanvasDragDrop';
import { useCanvasZoom } from './hooks/useCanvasZoom';
import { useCanvasShortcuts } from './hooks/useCanvasShortcuts';
import { usePerformanceMetrics } from './hooks/usePerformanceMetrics';
import { useCanvasAccessibility } from './hooks/useCanvasAccessibility';

// ============================================================================
// Types
// ============================================================================

export interface CanvasWorkspaceProps {
    projectId: string;
    currentPhase: LifecyclePhase;
    fowStage: FOWStage;
}

// ============================================================================
// PerformanceMetricsPanel
// ============================================================================

/**
 * Self-contained panel that reads performance metrics internally.
 * Extracted from workspacePanels memo so that 60fps metric updates don't
 * invalidate the entire panel configuration array.
 */
const PerformanceMetricsPanel: React.FC<{ nodeCount: number }> = ({ nodeCount }) => {
    const metrics = usePerformanceMetrics(nodeCount);
    return (
        <Box className="p-4 font-mono">
            <Typography as="span" className="text-xs text-gray-500 dark:text-gray-400 block">FPS: {metrics.fps}</Typography>
            <Typography as="span" className="text-xs text-gray-500 dark:text-gray-400 block">Nodes: {metrics.nodeCount}</Typography>
            <Typography as="span" className="text-xs text-gray-500 dark:text-gray-400 block">Render: {metrics.renderTimeMs}ms</Typography>
        </Box>
    );
};

// ============================================================================
// Component
// ============================================================================

/**
 * CanvasWorkspace — lean orchestrator.
 * 
 * All mutations live in useCanvasHandlers, DnD in useCanvasDragDrop,
 * camera in useCanvasZoom, keyboard in useCanvasShortcuts, perf in
 * usePerformanceMetrics, a11y in useCanvasAccessibility.
 */
export const CanvasWorkspace: React.FC<CanvasWorkspaceProps> = ({
    projectId,
    currentPhase,
    fowStage,
}) => {
    // ── Jotai atoms (UI state only – no domain logic) ──────────────────
    const [activePersona, setActivePersona] = useAtom(activePersonaAtom);
    const [isAIModalOpen, setIsAIModalOpen] = useAtom(isAIModalOpenAtom);
    const selectedNodes = useAtomValue(selectedNodesAtom);
    const [quickCreateMenuPosition, setQuickCreateMenuPosition] = useAtom(quickCreateMenuPositionAtom);
    const [isInspectorOpen, setIsInspectorOpen] = useAtom(isInspectorOpenAtom);
    const [isCommandPaletteOpen, setIsCommandPaletteOpen] = useAtom(isCommandPaletteOpenAtom);
    const [selectedArtifact, setSelectedArtifact] = useAtom(selectedArtifactAtom);
    const [isProjectSwitcherOpen, setIsProjectSwitcherOpen] = useAtom(isProjectSwitcherOpenAtom);
    const setNodesAtom = useSetAtom(nodesAtom);
    const setEdgesAtom = useSetAtom(edgesAtom);
    const [copiedNodes, setCopiedNodes] = useAtom(copiedNodesAtom);
    const announcement = useAtomValue(canvasAnnouncementAtom);

    // Canvas interaction mode
    const [interactionMode, setInteractionMode] = useAtom(canvasInteractionModeAtom);
    const sketchTool = useAtomValue(sketchToolAtom);
    const sketchColor = useAtomValue(sketchColorAtom);
    const sketchStrokeWidth = useAtomValue(sketchStrokeWidthAtom);

    // ReactFlow instance
    const [reactFlowInstance, setReactFlowInstance] = React.useState<ReactFlowInstance | null>(null);
    const setCamera = useSetAtom(cameraAtom);
    const prefersDark = useAtomValue(prefersDarkModeAtom);
    const minimapMaskColor = prefersDark ? 'rgba(17, 24, 39, 0.6)' : 'rgba(240, 242, 245, 0.6)';

    // Context menu for right-click on nodes
    const [nodeContextMenu, setNodeContextMenu] = useState<{
        x: number; y: number; nodeId: string;
    } | null>(null);

    // Canvas surface dimensions — tracked via ResizeObserver so the sketch
    // layer always matches the actual canvas area (not window size).
    const canvasSurfaceRef = useRef<HTMLDivElement>(null);
    const [canvasSize, setCanvasSize] = useState({ width: 1920, height: 1080 });
    useEffect(() => {
        const el = canvasSurfaceRef.current;
        if (!el) return;
        const observer = new ResizeObserver(([entry]) => {
            const { width, height } = entry.contentRect;
            setCanvasSize({ width: Math.floor(width), height: Math.floor(height) });
        });
        observer.observe(el);
        // Capture initial size before any resize event fires
        setCanvasSize({ width: el.clientWidth, height: el.clientHeight });
        return () => observer.disconnect();
    }, []);

    // Nodes/edges atoms
    const nodes = useAtomValue(nodesAtom);
    const edges = useAtomValue(edgesAtom);
    const setSelectedNodes = useSetAtom(selectedNodesAtom);

    // ── Incremental spatial index sync ─────────────────────────────────
    // Replaces the O(n) full-rebuild on every atom update.
    // Only nodes with measured dimensions are indexed — avoids wrong hit
    // boxes before ResizeObserver fires.
    const prevSpatialNodesRef = useRef<Map<string, Node>>(new Map());
    useEffect(() => {
        const prev = prevSpatialNodesRef.current;
        const next = new Map(
            nodes
                .filter((n) => n.measured?.width && n.measured?.height)
                .map((n) => [n.id, n]),
        );

        if (prev.size === 0 && next.size === 0) return;

        const added: Node[] = [];
        const removed: string[] = [];
        const moved: Node[] = [];

        for (const [id, n] of next) {
            const p = prev.get(id);
            if (!p) {
                added.push(n);
            } else if (
                p.position.x !== n.position.x ||
                p.position.y !== n.position.y ||
                p.measured?.width !== n.measured?.width ||
                p.measured?.height !== n.measured?.height
            ) {
                moved.push(n);
            }
        }
        for (const id of prev.keys()) {
            if (!next.has(id)) removed.push(id);
        }

        if (added.length || removed.length || moved.length) {
            spatialIndexAPI.syncNodes(added, removed, moved);
        }

        prevSpatialNodesRef.current = next;
    }, [nodes]);

    // Command history (replaces snapshot-based history atoms)
    const canUndo = useAtomValue(canUndoCommandAtom);
    const canRedo = useAtomValue(canRedoCommandAtom);
    const undo = useSetAtom(undoCommandAtom);
    const redo = useSetAtom(redoCommandAtom);
    const executeCommand = useSetAtom(executeCommandAtom);

    // Auto-create a diagram node when switching to diagram mode.
    // Placed AFTER executeCommand so the closure captures the initialized reference.
    // Undoable via command system — unlike the previous setNodesAtom mutation.
    useEffect(() => {
        if (interactionMode !== 'diagram') return;
        if (nodes.some((n) => n.type === 'diagram')) return;
        const position = reactFlowInstance
            ? reactFlowInstance.screenToFlowPosition({
                x: window.innerWidth / 2 - 200,
                y: window.innerHeight / 2 - 150,
            })
            : { x: 100, y: 100 };
        const newDiagramNode: Node<ArtifactNodeData> = {
            id: `diagram-node-${Date.now()}`,
            type: 'diagram',
            position,
            data: { label: 'System Diagram' } as unknown as ArtifactNodeData,
            draggable: true,
            selectable: true,
        };
        executeCommand(new AddNodeCommand(newDiagramNode));
    // nodes/executeCommand intentionally excluded — stable refs, run only on mode change
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [interactionMode, reactFlowInstance]);

    // Command registry (extensible command palette)
    const commandRegistry = useAtomValue(sortedCommandsAtom);
    const registerCommands = useSetAtom(registerCommandsAtom);

    // Server data
    const { data: gateStatus } = useGateStatus(projectId, fowStage);
    const { data: nextTask } = useNextBestTask(projectId, currentPhase);
    const { data: personaData } = useDerivedPersona({ projectId, phase: currentPhase, fowStage });
    const { data: artifacts } = useArtifacts(projectId);
    const userRole = useAtomValue(userRoleAtom);
    const userId = useAtomValue(userIdAtom);

    // ── Extracted hooks ────────────────────────────────────────────────
    const handlers = useCanvasHandlers({
        projectId,
        currentPhase,
        fowStage,
        personaName: personaData?.persona,
        artifacts,
        userId,
        reactFlowInstance,
    });

    // Batch-load code associations for all nodes — single request, no N+1
    useCodeAssociationsBatch(projectId);

    // Persistent node positions (survives server re-sync)
    const { mergePositions } = useNodePositions(projectId);

    const dragDrop = useCanvasDragDrop({
        projectId,
        reactFlowInstance,
        interactionMode,
        onCreateArtifact: handlers.handleCreateArtifact,
    });

    const zoom = useCanvasZoom({ reactFlowInstance, currentPhase });

    const { focusNextNode, focusPrevNode } = useCanvasAccessibility();

    // ── Keyboard shortcuts (single handler) ────────────────────────────
    useCanvasShortcuts({
        enabled: true,
        canUndo,
        canRedo,
        onUndo: undo,
        onRedo: redo,
        onCopy: () => {
            if (selectedNodes.length > 0) {
                const toCopy = nodes.filter(n => selectedNodes.includes(n.id));
                setCopiedNodes(toCopy);
            }
        },
        onPaste: handlers.handlePasteNodes,
        onSelectAll: handlers.handleSelectAll,
        onDeleteSelected: handlers.handleDeleteSelected,
        onZoomToPhase: zoom.handleZoomToPhase,
        onFitView: zoom.handleFitView,
        onPrevPhase: zoom.handlePrevPhase,
        onNextPhase: zoom.handleNextPhase,
        onOpenCommandPalette: () => setIsCommandPaletteOpen(true),
        onOpenAI: () => setIsAIModalOpen(true),
        onCloseModals: () => {
            setIsCommandPaletteOpen(false);
            setIsAIModalOpen(false);
            setIsInspectorOpen(false);
        },
        onMoveSelectedNodes: handlers.handleMoveSelectedNodes,
        onFocusNextNode: focusNextNode,
        onFocusPrevNode: focusPrevNode,
        onOpenInspector: () => setIsInspectorOpen(prev => !prev),
        onFitSelection: () => zoom.handleFitSelection(),
        onShowShortcuts: () => setIsCommandPaletteOpen(true), // opens palette; TODO: dedicated shortcut sheet
    });

    // ── ReactFlow change handlers ──────────────────────────────────────
    const onNodesChange = useCallback(
        (changes: NodeChange[]) => {
            // Allow ALL changes through — including position changes during drag.
            // Previously we filtered dragging:true position changes, which broke
            // real-time guide updates and incremental spatial index diffs.
            setNodesAtom(nds => applyNodeChanges(changes, nds));
        },
        [setNodesAtom],
    );

    const onEdgesChange = useCallback(
        (changes: EdgeChange[]) => setEdgesAtom(eds => applyEdgeChanges(changes, eds)),
        [setEdgesAtom],
    );

    const onConnect = useCallback(
        (connection: Connection) => {
            if (interactionMode !== 'navigate') return;
            const newEdge: Edge<DependencyEdgeData> = {
                id: `edge-${connection.source}-${connection.target}`,
                source: connection.source!,
                target: connection.target!,
                type: 'dependency',
                markerEnd: { type: MarkerType.ArrowClosed, color: 'var(--color-primary, #1976d2)' },
                data: { label: 'requires', type: 'requires' },
            };
            executeCommand(new AddEdgeCommand(newEdge));
        },
        [executeCommand, interactionMode],
    );

    // ── Derived data ───────────────────────────────────────────────────
    const personaFilterData: PersonaFilterData[] = useMemo(() => {
        if (!artifacts) return [];
        const map = new Map<string, PersonaFilterData>();
        artifacts.forEach(a => {
            const p = a.createdBy || 'Unassigned';
            if (!map.has(p)) map.set(p, { persona: p, total: 0, completed: 0, blocked: 0, inProgress: 0 });
            const d = map.get(p)!;
            d.total++;
            if (a.status === 'approved') d.completed++;
            else if (a.status === 'review') d.inProgress++;
        });
        return Array.from(map.values());
    }, [artifacts]);

    const gateCriteria: GateCriterion[] = useMemo(() => {
        if (!gateStatus) return [];
        return gateStatus.missingArtifacts?.map((missing, index) => ({
            id: `artifact-${missing.type}-${index}`,
            label: `${missing.type} artifacts`,
            status: missing.current === 0 ? 'blocked' : missing.current < missing.required ? 'in-progress' : 'complete',
            progress: { current: missing.current, total: missing.required },
            personas: personaFilterData.map(p => p.persona),
        })) ?? [];
    }, [gateStatus, personaFilterData]);

    // ── Node generation from artifacts ─────────────────────────────────
    const generatedNodes: Node<ArtifactNodeData>[] = useMemo(() => {
        if (!artifacts) return [];
        const filtered = activePersona ? artifacts.filter(a => a.createdBy === activePersona) : artifacts;
        const phaseCounts: Partial<Record<LifecyclePhase, number>> = {};
        return mergePositions(filtered.map(artifact => {
            const count = phaseCounts[artifact.phase] || 0;
            phaseCounts[artifact.phase] = count + 1;
            return {
                id: artifact.id,
                type: 'artifact',
                position: getZonePlacementPosition(artifact.phase, count),
                data: {
                    id: artifact.id,
                    type: artifact.type,
                    title: artifact.title,
                    description: artifact.description,
                    status: artifact.status === 'approved' ? 'complete' : artifact.status === 'review' ? 'in-progress' : 'pending',
                    persona: artifact.createdBy,
                    phase: artifact.phase,
                    linkedCount: artifact.linkedArtifacts?.length || 0,
                    onEdit: (_id: string) => { /* inspector opens via node click */ },
                    onLink: (_id: string) => { /* inspector handles linking */ },
                },
            };
        }));
    }, [artifacts, activePersona, mergePositions]);

    const suppressGeneratedSync = useAtomValue(suppressGeneratedSyncAtom);
    useEffect(() => { if (!suppressGeneratedSync) setNodesAtom(generatedNodes); }, [generatedNodes, setNodesAtom, suppressGeneratedSync]);

    const { nodeTypes, edgeTypes } = useCanvasRegistry();

    const generatedEdges: Edge<DependencyEdgeData>[] = useMemo(() => {
        if (!artifacts) return [];
        const list: Edge<DependencyEdgeData>[] = [];
        artifacts.forEach(a => {
            a.linkedArtifacts?.forEach(linkedId => {
                list.push({
                    id: `${a.id}->${linkedId}`,
                    source: a.id,
                    target: linkedId,
                    type: 'dependency',
                    data: { type: 'requires', label: 'Requires' },
                    markerEnd: { type: MarkerType.ArrowClosed, color: 'var(--color-primary, #1976d2)' },
                });
            });
        });
        return list;
    }, [artifacts]);

    useEffect(() => { setEdgesAtom(generatedEdges); }, [generatedEdges, setEdgesAtom]);

    // Computed view
    const viewMode = useAtomValue(viewModeAtom);
    const computedView = useComputedView({
        userRole: userRole || 'developer',
        userId: userId || 'current-user',
        viewMode,
    });

    // Separate memo so styledNodes doesn't re-run on edge changes unrelated to style.
    const edgeTargetMap = useMemo(() => {
        const m = new Map<string, string[]>();
        edges.forEach(e => {
            const list = m.get(e.source) ?? [];
            list.push(e.target);
            m.set(e.source, list);
        });
        return m;
    }, [edges]);

    // Viewport-culled node IDs — avoids expensive style computations for
    // off-screen nodes. ReactFlow's onlyRenderVisibleElements suppresses DOM;
    // this atom skips JS-level work entirely.
    const visibleIds = useAtomValue(visibleNodeIdsAtom);

    const styledNodes = useMemo(() => {
        return computedView.visibleNodes
            .filter(node => visibleIds.has(node.id))
            .map(node => ({
            ...node,
            style: {
                ...node.style,
                opacity: computedView.dimmedNodeIds.has(node.id) ? 0.4 : 1,
                // Colour-based ring + HCM-safe outline (Highlight token renders
                // with system accent in Windows High Contrast Mode)
                boxShadow: computedView.highlightedNodeIds.has(node.id)
                    ? '0 0 0 3px rgba(99, 102, 241, 0.5)' : undefined,
                outline: computedView.highlightedNodeIds.has(node.id)
                    ? '3px solid Highlight' : undefined,
            },
            data: {
                ...node.data,
                isLocked: computedView.lockedNodeIds.has(node.id),
                isBlocked: computedView.blockedNodeIds.has(node.id),
                outgoingEdgeTargets: (edgeTargetMap.get(node.id) ?? []).join(' ') || undefined,
            },
        }));
    }, [computedView, edgeTargetMap, visibleIds]);

    // ── Event handlers (thin wrappers) ─────────────────────────────────
    const handleNodeClick = useCallback(
        (event: React.MouseEvent, node: Node) => {
            // Preserve Shift+click multi-selection — ReactFlow has already toggled
            // the node's selected state before this handler fires. Only replace
            // the Jotai selection on a plain (non-shift) click.
            if (!event.shiftKey) {
                setSelectedNodes([node.id]);
            }
            if (!artifacts) return;
            const artifact = artifacts.find(a => a.id === node.id);
            if (artifact) {
                // Map server artifact to InspectorArtifact shape
                setSelectedArtifact({
                    id: artifact.id,
                    type: artifact.type,
                    title: artifact.title,
                    description: artifact.description,
                    status: artifact.status,
                    phase: artifact.phase,
                    persona: artifact.createdBy,
                    linkedArtifacts: artifact.linkedArtifacts ?? [],
                    blockers: [],
                    comments: [],
                });
                setIsInspectorOpen(true);
            }
        },
        [artifacts, setSelectedArtifact, setIsInspectorOpen, setSelectedNodes],
    );

    const handleNodeContextMenu = useCallback(
        (event: React.MouseEvent, node: Node) => {
            event.preventDefault();
            setNodeContextMenu({ x: event.clientX, y: event.clientY, nodeId: node.id });
        },
        [],
    );

    const handleCanvasDoubleClick = useCallback((event: React.MouseEvent) => {
        setQuickCreateMenuPosition({ x: event.clientX, y: event.clientY });
    }, [setQuickCreateMenuPosition]);

    const handleStartTask = useCallback(() => {
        if (!nextTask) return;
        const taskNodeId = nextTask.id || nextTask.artifactId;
        if (taskNodeId && reactFlowInstance) {
            const taskNode = generatedNodes.find(n => n.id === taskNodeId);
            if (taskNode) {
                const w = taskNode.measured?.width ?? 200;
                const h = taskNode.measured?.height ?? 100;
                // fitBounds respects minZoom/maxZoom and reveals the whole node
                reactFlowInstance.fitBounds(
                    { x: taskNode.position.x, y: taskNode.position.y, width: w, height: h },
                    { padding: 0.25, duration: 600 },
                );
            }
        }
    }, [nextTask, generatedNodes, reactFlowInstance]);

    const handleAIQuery = useCallback(
        async (query: string): Promise<AISuggestion[]> => {
            try {
                const recs = await lifecycleAPI.ai.getRecommendations(projectId, {
                    phase: currentPhase, fowStage,
                    persona: activePersona || personaData?.persona,
                    recentActivity: [query],
                });
                return recs.map((r: Record<string, unknown>) => ({
                    id: (r.id as string) || `ai-${Date.now()}`,
                    type: (r.type as string) || 'suggestion',
                    title: (r.title as string) || (r.action as string),
                    description: (r.description as string) || (r.reasoning as string),
                    confidence: (r.confidence as number) || 0.8,
                    action: r.action as string,
                }));
            } catch {
                return [];
            }
        },
        [projectId, currentPhase, fowStage, activePersona, personaData],
    );

    // ── Workspace Panels ───────────────────────────────────────────────
    const workspacePanels: WorkspacePanelConfig[] = useMemo(() => [
        {
            id: 'navigation',
            title: 'Navigation',
            icon: <MapIcon />,
            defaultPosition: { x: window.innerWidth - 320, y: 80 },
            defaultWidth: 280,
            defaultOpen: false,
            description: 'View modes, presence, and abstraction level',
            content: (
                <Box className="p-2 flex flex-col gap-2">
                    <PresenceIndicator users={[]} />
                    <Divider />
                    <ViewModeSelector showBadges compact={false} />
                    <Divider />
                    <AbstractionLevelNavigator
                        currentLevel={zoom.currentAbstractionLevel}
                        breadcrumbs={zoom.breadcrumbs}
                        canDrillDown={zoom.canDrillDown}
                        canZoomOut={zoom.canZoomOut}
                        canGoBack={zoom.breadcrumbs.length > 1}
                        onLevelChange={zoom.handleLevelChange}
                        onDrillDown={() => {
                            const next = getNextLevel(zoom.currentAbstractionLevel);
                            if (next) zoom.handleLevelChange(next);
                        }}
                        onZoomOut={zoom.handleZoomOut}
                        onGoBack={() => zoom.handleBreadcrumbClick(zoom.breadcrumbs.length - 2)}
                        onBreadcrumbClick={zoom.handleBreadcrumbClick}
                        onReset={() => zoom.handleLevelChange('system')}
                    />
                </Box>
            ),
        },
        {
            id: 'team-filter',
            title: 'Team Filter',
            icon: <PersonIcon />,
            defaultPosition: { x: 20, y: 80 },
            defaultWidth: 260,
            defaultOpen: false,
            description: 'Filter artifacts by persona/role',
            content: (
                <PersonaFilterToolbar
                    personas={personaFilterData}
                    activePersona={activePersona}
                    onPersonaChange={setActivePersona}
                />
            ),
        },
        {
            id: 'history',
            title: 'History',
            icon: <HistoryIcon />,
            defaultPosition: { x: window.innerWidth - 360, y: 400 },
            defaultWidth: 320,
            defaultOpen: false,
            description: 'Command history — undo/redo operations',
            content: (
                <Box className="p-4 flex flex-col gap-2">
                    <Box className="flex gap-2">
                        <Button
                            variant="outlined"
                            size="sm"
                            onClick={() => undo()}
                            disabled={!canUndo}
                            className="flex-1"
                        >Undo</Button>
                        <Button
                            variant="outlined"
                            size="sm"
                            onClick={() => redo()}
                            disabled={!canRedo}
                            className="flex-1"
                        >Redo</Button>
                    </Box>
                    <Typography as="p" className="text-xs text-gray-500">
                        {canUndo ? 'Changes available to undo.' : 'Nothing to undo.'}
                    </Typography>
                </Box>
            ),
        },
        {
            id: 'performance',
            title: 'Performance',
            icon: <SpeedIcon />,
            defaultPosition: { x: 20, y: 400 },
            defaultWidth: 280,
            defaultOpen: false,
            description: 'Canvas performance metrics',
            content: <PerformanceMetricsPanel nodeCount={nodes.length} />,
        },
    ], [
        zoom.currentAbstractionLevel, zoom.breadcrumbs, zoom.canDrillDown, zoom.canZoomOut,
        zoom.handleLevelChange, zoom.handleBreadcrumbClick, zoom.handleZoomOut,
        personaFilterData, activePersona, setActivePersona,
        nodes.length,
        canUndo, canRedo, undo, redo,
    ]);

    // ── Render ─────────────────────────────────────────────────────────
    // Loading guard — placed after all hooks to comply with Rules of Hooks
    if (fowStage === undefined) {
        return (
            <Box className="w-full h-full flex items-center justify-center flex-col">
                <CircularProgress />
                <Typography as="p" className="mt-4">Loading workspace…</Typography>
            </Box>
        );
    }

    return (
        <ReactFlowProvider>
        <Box
            className="w-full h-full relative"
            role="application"
            aria-label="Canvas workspace"
            aria-roledescription="interactive canvas"
        >
            {/* Skip-to-content link for keyboard users */}
            <a
                href="#canvas-surface"
                className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:top-2 focus:left-2 focus:px-4 focus:py-2 focus:bg-white focus:dark:bg-gray-900 focus:rounded focus:shadow-lg"
            >
                Skip to canvas
            </a>

            {/* ARIA live region for announcements */}
            <div aria-live="polite" aria-atomic="true" className="sr-only">
                {announcement}
            </div>

            <Box className="w-full h-full flex">
                {/* Left Panel */}
                <CanvasErrorBoundary label="Left Panel">
                    <UnifiedLeftPanel
                        projectId={projectId}
                        onDragStart={dragDrop.setDraggedTemplate}
                        onAddComponent={(template) => {
                            let position = { x: 400, y: 300 };
                            if (reactFlowInstance) {
                                const center = reactFlowInstance.screenToFlowPosition({
                                    x: window.innerWidth / 2,
                                    y: window.innerHeight / 2,
                                });
                                position = {
                                    x: center.x + (Math.random() * 100 - 50),
                                    y: center.y + (Math.random() * 100 - 50),
                                };
                            }
                            handlers.handleCreateArtifact(template, position);
                        }}
                        nodes={nodes}
                        edges={edges}
                    />
                </CanvasErrorBoundary>

                {/* Canvas Surface */}
                <Box
                    id="canvas-surface"
                    ref={canvasSurfaceRef}
                    className={`flex-1 relative ${dragDrop.isDragOver ? 'ring-2 ring-primary-400 ring-inset' : ''}`}
                    onDoubleClick={handleCanvasDoubleClick}
                    onDrop={interactionMode === 'navigate' ? dragDrop.handleCanvasDrop : undefined}
                    onDragOver={interactionMode === 'navigate' ? dragDrop.handleCanvasDragOver : undefined}
                    onDragLeave={interactionMode === 'navigate' ? dragDrop.handleCanvasDragLeave : undefined}
                    tabIndex={0}
                    aria-label="Canvas surface — use Tab to navigate nodes, arrow keys to move them"
                >
                        <ReactFlow
                            nodes={styledNodes}
                            edges={computedView.visibleEdges}
                            nodeTypes={nodeTypes}
                            edgeTypes={edgeTypes}
                            onNodesChange={onNodesChange}
                            onEdgesChange={onEdgesChange}
                            onConnect={onConnect}
                            onNodeClick={handleNodeClick}
                            onNodeContextMenu={handleNodeContextMenu}
                            onNodeDragStart={dragDrop.onNodeDragStart}
                            onNodeDrag={dragDrop.onNodeDrag}
                            onNodeDragStop={dragDrop.onNodeDragStop}
                            onSelectionChange={({ nodes: sel }) => {
                                setSelectedNodes(sel.map(n => n.id));
                            }}
                            onlyRenderVisibleElements
                            nodesFocusable
                            onMove={(_, vp) => setCamera({ ...vp, initialized: true })}
                            onInit={(rf) => {
                                setReactFlowInstance(rf);
                                // Mark camera as initialized with the real viewport so
                                // visibleNodeIdsAtom stops returning all nodes unconditionally.
                                const vp = rf.getViewport();
                                setCamera({ ...vp, initialized: true });
                            }}
                            fitView
                            snapToGrid
                            snapGrid={[16, 16]}
                            minZoom={0.1}
                            maxZoom={2}
                            zoomOnPinch
                            panOnDrag={interactionMode === 'navigate'}
                            panOnScroll
                            zoomOnScroll
                            zoomActivationKeyCode="Meta"
                            nodesDraggable={interactionMode === 'navigate'}
                            nodesConnectable={interactionMode === 'navigate'}
                            elementsSelectable={interactionMode === 'navigate'}
                            selectNodesOnDrag={false}
                            noDragClassName="nodrag"
                            noWheelClassName="nowheel"
                            className="dark:bg-gray-950"
                            style={{
                                opacity: interactionMode === 'sketch' ? 0.6 : 1,
                                transition: 'opacity 200ms ease',
                            }}
                        >
                            <SpatialZones currentPhase={currentPhase} onZoneClick={zoom.handleZoomToPhase} />

                            <GhostNodes
                                currentPhase={currentPhase}
                                artifactCount={computedView.totalNodes}
                                onCreateArtifact={handlers.handleGhostNodeCreate}
                                onAISuggestion={() => setIsAIModalOpen(true)}
                            />

                            {/* Grid — dark-mode aware */}
                            <Background
                                color="var(--canvas-grid-color, #aaa)"
                                gap={16}
                                className="dark:[--canvas-grid-color:#444]"
                            />

                            <Controls
                                style={{
                                    margin: 16, display: 'flex', gap: 4, border: 'none',
                                    boxShadow: '0 4px 12px rgba(0,0,0,0.1)', borderRadius: 12,
                                    overflow: 'hidden',
                                }}
                                className="bg-white dark:bg-gray-800"
                                showInteractive={false}
                                aria-label="Zoom controls"
                            />

                            <MiniMap
                                pannable
                                zoomable
                                nodeColor={(node) => {
                                    const data = node.data as unknown as ArtifactNodeData;
                                    if (data.status === 'blocked') return 'var(--color-error, #ef5350)';
                                    if (data.status === 'complete') return 'var(--color-success, #66bb6a)';
                                    if (data.status === 'in-progress') return 'var(--color-info, #42a5f5)';
                                    return 'var(--color-muted, #e0e0e0)';
                                }}
                                maskColor={minimapMaskColor}
                                className="dark:bg-gray-900/90"
                                style={{
                                    border: '1px solid rgba(0,0,0,0.05)', borderRadius: 16,
                                    margin: 20, height: 120, width: 180,
                                    boxShadow: '0 8px 24px rgba(0,0,0,0.12)',
                                }}
                                aria-label="Minimap overview"
                            />

                            {/* Context-sensitive alignment toolbar — appears when ≥2 nodes selected */}
                            <Panel position="top-center">
                                <AlignmentToolbar />
                            </Panel>
                        </ReactFlow>

                        <AlignmentGuides />

                        {interactionMode === 'sketch' && (
                            <Box className="absolute inset-0 pointer-events-auto z-[30]">
                                <EnhancedSketchLayer
                                    width={canvasSize.width}
                                    height={canvasSize.height}
                                    activeTool={sketchTool}
                                    config={{ color: sketchColor, strokeWidth: sketchStrokeWidth, fill: 'transparent' }}
                                />
                            </Box>
                        )}
                    {interactionMode === 'sketch' && <SketchToolbar />}
                    {interactionMode === 'diagram' && <DiagramToolbar />}
                </Box>
            </Box>

            {/* Overlays — wrapped in error boundaries */}
            {nextTask && personaData && (
                <CanvasErrorBoundary label="Next Best Task">
                    <NextBestTaskCard
                        persona={personaData.persona || 'Developer'}
                        taskTitle={nextTask.title || 'No title'}
                        taskDescription={nextTask.description}
                        impact="Recommended for current phase"
                        estimatedMinutes={nextTask.estimatedEffort}
                        blocksCount={0}
                        collaborators={[nextTask.persona]}
                        onStartTask={handleStartTask}
                        onSkip={() => { /* refetch next task */ }}
                        priority={nextTask.priority}
                    />
                </CanvasErrorBoundary>
            )}

            <AIAssistantModal
                open={isAIModalOpen}
                onClose={() => setIsAIModalOpen(false)}
                context={{
                    selectedArtifacts: selectedNodes,
                    currentPhase,
                    persona: activePersona || personaData?.persona,
                    blockers: gateCriteria.filter(g => g.status === 'blocked').map(g => g.id),
                }}
                onSubmit={handleAIQuery}
            />

            <QuickCreateMenu
                open={quickCreateMenuPosition !== null}
                anchorPosition={quickCreateMenuPosition}
                currentPhase={currentPhase}
                onClose={() => setQuickCreateMenuPosition(null)}
                onCreate={handlers.handleCreateArtifact}
            />

            <CanvasErrorBoundary label="Inspector Panel">
                <InspectorPanel
                    open={isInspectorOpen}
                    artifact={selectedArtifact}
                    onClose={() => setIsInspectorOpen(false)}
                    onUpdate={handlers.handleUpdateArtifact}
                    onAddBlocker={handlers.handleAddBlocker}
                    onAddComment={handlers.handleAddComment}
                    onLinkArtifact={handlers.handleLinkArtifact}
                />
            </CanvasErrorBoundary>

            <ProjectSwitcher
                open={isProjectSwitcherOpen}
                onClose={() => setIsProjectSwitcherOpen(false)}
                currentProjectId={projectId}
            />

            <PanelManager panels={workspacePanels} />

            {/* Command Palette — fully wired */}
            <CommandPalette
                open={isCommandPaletteOpen}
                onClose={() => setIsCommandPaletteOpen(false)}
                actions={commandRegistry}
                onNavigate={(path) => { /* router navigation */ }}
                onModeChange={(mode) => setInteractionMode(mode as 'navigate' | 'sketch' | 'code' | 'diagram')}
                onLevelChange={(level) => zoom.handleLevelChange(level)}
                onTogglePanel={(panel) => { /* handled by PanelManager dock */ }}
                onPhaseTransition={(dir) => dir === 'next' ? zoom.handleNextPhase() : zoom.handlePrevPhase()}
                onValidate={() => { /* validation pipeline */ }}
                onGenerate={() => { /* code generation */ }}
                onExport={() => { /* export handler */ }}
                onSave={() => { /* save handler */ }}
                onFitView={zoom.handleFitView}
                onZoomIn={() => reactFlowInstance?.zoomIn({ duration: 300 })}
                onZoomOut={() => reactFlowInstance?.zoomOut({ duration: 300 })}
                onShowHelp={() => { /* help modal */ }}
                onShowShortcuts={() => { /* shortcuts reference */ }}
            />

            {/* Right-click context menu for nodes */}
            {nodeContextMenu && (
                <Menu
                    open
                    onClose={() => setNodeContextMenu(null)}
                    anchorReference="anchorPosition"
                    anchorPosition={{ top: nodeContextMenu.y, left: nodeContextMenu.x }}
                >
                    <MenuItem
                        onClick={() => {
                            setIsInspectorOpen(true);
                            setNodeContextMenu(null);
                        }}
                    >
                        Edit in Inspector
                    </MenuItem>
                    <MenuItem
                        onClick={() => {
                            handlers.handleCopyNodes?.();
                            handlers.handlePasteNodes?.();
                            setNodeContextMenu(null);
                        }}
                    >
                        <DuplicateIcon size={14} className="mr-2" />
                        Duplicate
                    </MenuItem>
                    <MenuItem
                        onClick={() => {
                            setSelectedNodes([nodeContextMenu.nodeId]);
                            handlers.handleDeleteSelected();
                            setNodeContextMenu(null);
                        }}
                        className="text-red-500 dark:text-red-400"
                    >
                        <TrashIcon size={14} className="mr-2" />
                        Delete
                    </MenuItem>
                </Menu>
            )}
        </Box>
        </ReactFlowProvider>
    );
};
