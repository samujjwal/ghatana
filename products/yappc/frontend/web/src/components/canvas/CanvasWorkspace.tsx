/**
 * Canvas Workspace
 *
 * Lean orchestrator component for the Canvas-First UX. All domain logic is
 * extracted to dedicated hooks (useCanvasHandlers, useCanvasDragDrop,
 * useCanvasZoom, useCanvasShortcuts, useCanvasAccessibility). Surface
 * rendering is delegated to CanvasReactFlowSurface; overlays to
 * CanvasOverlays; panel config to useWorkspacePanels.
 *
 * @doc.type component
 * @doc.purpose Canvas-first unified workspace orchestrator
 * @doc.layer product
 * @doc.pattern Workspace Container
 */

import React, { useCallback, useMemo, useEffect, useRef, useState } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { Box, Spinner as CircularProgress, Typography } from '@ghatana/design-system';
import {
    ReactFlowProvider,
    type Node, type Edge, MarkerType, type ReactFlowInstance,
    applyNodeChanges, applyEdgeChanges,
    type NodeChange, type EdgeChange, type Connection,
} from '@xyflow/react';
import { useGateStatus, useNextBestTask, useDerivedPersona, useArtifacts } from '@/hooks/useLifecycleData';
import { lifecycleAPI, type AIRecommendation } from '@/services/lifecycle/api';
import { LifecyclePhase } from '@/types/lifecycle';
import { FOWStage } from '@/types/fow-stages';
import {
    type GateCriterion, type PersonaFilterData, type AISuggestion,
    activePersonaAtom, isAIModalOpenAtom, isProjectSwitcherOpenAtom, isInspectorOpenAtom,
    selectedArtifactAtom, selectedNodesAtom, quickCreateMenuPositionAtom, isCommandPaletteOpenAtom,
    nodesAtom, edgesAtom, suppressGeneratedSyncAtom,
    canvasInteractionModeAtom, sketchToolAtom, sketchColorAtom, sketchStrokeWidthAtom,
    canvasAnnouncementAtom, copiedNodesAtom,
    cameraAtom,
    sortedCommandsAtom, prefersDarkModeAtom, visibleNodeIdsAtom,
    getZonePlacementPosition,
    type ArtifactTemplate,
} from './workspace';
import { type DependencyEdgeDataR } from './workspace/canvasAtoms';
import {
    canUndoCommandAtom, canRedoCommandAtom, undoCommandAtom, redoCommandAtom, executeCommandAtom,
    AddEdgeCommand, AddNodeCommand,
} from './workspace/canvasCommands';
import { useCodeAssociationsBatch } from './hooks/useCodeAssociationsBatch';
import { useNodePositions } from './hooks/useNodePositions';
import { UnifiedLeftPanel } from './UnifiedLeftPanel';
import { spatialIndexAPI } from './workspace/spatialIndexService';

import { type ArtifactNodeData } from './nodes/ArtifactNode';
import { type DependencyEdgeData } from './edges';
import { useComputedView, viewModeAtom, userIdAtom, userRoleAtom } from './hooks/useComputedView';
import { type WorkspacePanelConfig } from './panels/types';
import { useCanvasRegistry } from './registry';
import { CanvasErrorBoundary } from './CanvasErrorBoundary';

// Extracted hooks
import { useCanvasHandlers } from './hooks/useCanvasHandlers';
import { useCanvasDragDrop } from './hooks/useCanvasDragDrop';
import { useCanvasZoom } from './hooks/useCanvasZoom';
import { useCanvasShortcuts } from './hooks/useCanvasShortcuts';
import { useCanvasAccessibility } from './hooks/useCanvasAccessibility';
import { useWorkspacePanels } from './hooks/useWorkspacePanels';
import { CanvasReactFlowSurface } from './CanvasReactFlowSurface';
import { CanvasOverlays } from './CanvasOverlays';

// ============================================================================
// Types
// ============================================================================

export interface CanvasWorkspaceProps {
    projectId: string;
    currentPhase: LifecyclePhase;
    flowStage: FOWStage;
}

const ARTIFACT_TEMPLATE_TYPES: ArtifactTemplate['type'][] = [
    'brief',
    'user-story',
    'requirement',
    'design',
    'mockup',
    'api-spec',
    'code',
    'test',
    'deployment',
    'metric',
];

function isArtifactTemplateType(value: unknown): value is ArtifactTemplate['type'] {
    return typeof value === 'string' && ARTIFACT_TEMPLATE_TYPES.includes(value as ArtifactTemplate['type']);
}

// ============================================================================
// Component
// ============================================================================

/**
 * CanvasWorkspace — lean orchestrator.
 *
 * Data wiring only. Surface rendering is delegated to CanvasReactFlowSurface,
 * overlays to CanvasOverlays, panel config to useWorkspacePanels, and all
 * mutations/DnD/camera/keyboard/a11y to their respective hooks.
 */
export const CanvasWorkspace: React.FC<CanvasWorkspaceProps> = ({
    projectId,
    currentPhase,
    flowStage,
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
    const [reactFlowInstance, setReactFlowInstance] = useState<ReactFlowInstance | null>(null);
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

    // Server data
    const { data: gateStatus } = useGateStatus(projectId, flowStage);
    const { data: nextTask } = useNextBestTask(projectId, currentPhase);
    const { data: personaData } = useDerivedPersona({ projectId, phase: currentPhase, flowStage });
    const { data: artifacts } = useArtifacts(projectId);
    const userRole = useAtomValue(userRoleAtom);
    const userId = useAtomValue(userIdAtom);

    // ── Extracted hooks ────────────────────────────────────────────────
    const handlers = useCanvasHandlers({
        projectId,
        currentPhase,
        flowStage,
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
            setNodesAtom(nds => applyNodeChanges(changes, nds) as typeof nds);
        },
        [setNodesAtom],
    );

    const onEdgesChange = useCallback(
        (changes: EdgeChange[]) => setEdgesAtom(eds => applyEdgeChanges(changes, eds) as typeof eds),
        [setEdgesAtom],
    );

    const onConnect = useCallback(
        (connection: Connection) => {
            if (interactionMode !== 'navigate') return;
            const newEdge: Edge<DependencyEdgeDataR> = {
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

    const generatedEdges: Edge<DependencyEdgeDataR>[] = useMemo(() => {
        if (!artifacts) return [];
        const list: Edge<DependencyEdgeDataR>[] = [];
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
                    type: isArtifactTemplateType(artifact.type) ? artifact.type : 'brief',
                    title: artifact.title,
                    description: artifact.description,
                    status: artifact.status === 'approved' ? 'complete' : artifact.status === 'review' ? 'review' : 'pending',
                    phase: artifact.phase,
                    persona: artifact.createdBy,
                    linkedArtifacts: artifact.linkedArtifacts ?? [],
                    blockers: [],
                    comments: [],
                    createdAt: new Date(),
                    updatedAt: new Date(),
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
        const taskNodeId = nextTask.id;
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
                    phase: currentPhase, flowStage,
                    persona: activePersona || personaData?.persona,
                    recentActivity: [query],
                });
                return recs.map((recommendation: AIRecommendation) => ({
                    id: recommendation.id || `ai-${Date.now()}`,
                    type: recommendation.type === 'task'
                        ? 'next-action'
                        : recommendation.type === 'insight'
                            ? 'insight'
                            : recommendation.type === 'enhancement'
                                ? 'optimization'
                                : 'unblock',
                    title: recommendation.title,
                    description: recommendation.description,
                    priority: recommendation.priority,
                }));
            } catch {
                return [];
            }
        },
        [projectId, currentPhase, flowStage, activePersona, personaData],
    );

    // ── Workspace Panels ───────────────────────────────────────────────
    const workspacePanels: WorkspacePanelConfig[] = useWorkspacePanels({
        zoom,
        personaFilterData,
        activePersona,
        setActivePersona,
        nodeCount: nodes.length,
        canUndo,
        canRedo,
        undo,
        redo,
    });

    // ── Render ─────────────────────────────────────────────────────────
    // Loading guard — placed after all hooks to comply with Rules of Hooks
    if (flowStage === undefined) {
        return (
            <Box className="w-full h-full flex items-center justify-center flex-col">
                <CircularProgress />
                <Typography className="mt-4">Loading workspace…</Typography>
            </Box>
        );
    }

    const normalizeTemplate = (template: object): ArtifactTemplate => {
        const candidate = template as Partial<ArtifactTemplate> & {
            type?: unknown;
            label?: unknown;
            description?: unknown;
            icon?: unknown;
            defaultTitle?: unknown;
            phase?: unknown;
        };

        return {
            type: isArtifactTemplateType(candidate.type) ? candidate.type : 'brief',
            icon: typeof candidate.icon === 'string' ? candidate.icon : '📄',
            label: typeof candidate.label === 'string' ? candidate.label : 'Artifact',
            description: typeof candidate.description === 'string' ? candidate.description : '',
            phase: Object.values(LifecyclePhase).includes(candidate.phase as LifecyclePhase)
                ? (candidate.phase as LifecyclePhase)
                : currentPhase,
            defaultTitle: typeof candidate.defaultTitle === 'string'
                ? candidate.defaultTitle
                : typeof candidate.label === 'string'
                    ? candidate.label
                    : 'New Artifact',
        };
    };

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
                className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:top-2 focus:left-2 focus:px-4 focus:py-2 focus:bg-white focus:dark:bg-surface focus:rounded focus:shadow-lg"
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
                        onDragStart={(template) => dragDrop.setDraggedTemplate(template)}
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
                            handlers.handleCreateArtifact(normalizeTemplate(template), position);
                        }}
                        nodes={nodes}
                        edges={edges}
                    />
                </CanvasErrorBoundary>

                <CanvasReactFlowSurface
                    canvasSurfaceRef={canvasSurfaceRef}
                    canvasSize={canvasSize}
                    interactionMode={interactionMode}
                    currentPhase={currentPhase}
                    minimapMaskColor={minimapMaskColor}
                    sketchTool={sketchTool}
                    sketchColor={sketchColor}
                    sketchStrokeWidth={sketchStrokeWidth}
                    styledNodes={styledNodes}
                    computedView={computedView}
                    nodeTypes={nodeTypes}
                    edgeTypes={edgeTypes}
                    onNodesChange={onNodesChange}
                    onEdgesChange={onEdgesChange}
                    onConnect={onConnect}
                    handleNodeClick={handleNodeClick}
                    handleNodeContextMenu={handleNodeContextMenu}
                    handleCanvasDoubleClick={handleCanvasDoubleClick}
                    setSelectedNodes={setSelectedNodes}
                    setReactFlowInstance={setReactFlowInstance}
                    setCamera={setCamera}
                    dragDrop={dragDrop}
                    handleZoomToPhase={zoom.handleZoomToPhase}
                    handleGhostNodeCreate={handlers.handleGhostNodeCreate}
                    setIsAIModalOpen={setIsAIModalOpen}
                />
            </Box>

            <CanvasOverlays
                nextTask={nextTask}
                personaData={personaData}
                handleStartTask={handleStartTask}
                isAIModalOpen={isAIModalOpen}
                setIsAIModalOpen={setIsAIModalOpen}
                selectedNodes={selectedNodes}
                currentPhase={currentPhase}
                activePersona={activePersona}
                gateCriteria={gateCriteria}
                handleAIQuery={handleAIQuery}
                quickCreateMenuPosition={quickCreateMenuPosition}
                setQuickCreateMenuPosition={setQuickCreateMenuPosition}
                isInspectorOpen={isInspectorOpen}
                setIsInspectorOpen={setIsInspectorOpen}
                selectedArtifact={selectedArtifact}
                isProjectSwitcherOpen={isProjectSwitcherOpen}
                setIsProjectSwitcherOpen={setIsProjectSwitcherOpen}
                projectId={projectId}
                workspacePanels={workspacePanels}
                isCommandPaletteOpen={isCommandPaletteOpen}
                setIsCommandPaletteOpen={setIsCommandPaletteOpen}
                commandRegistry={commandRegistry}
                zoom={zoom}
                reactFlowInstance={reactFlowInstance}
                setInteractionMode={setInteractionMode}
                nodeContextMenu={nodeContextMenu}
                setNodeContextMenu={setNodeContextMenu}
                setSelectedNodes={setSelectedNodes}
                handlers={handlers}
            />
        </Box>
        </ReactFlowProvider>
    );
};
