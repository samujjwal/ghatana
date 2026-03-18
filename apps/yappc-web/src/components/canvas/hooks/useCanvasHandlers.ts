/**
 * Canvas Handlers Hook
 * 
 * Extracts all CRUD/mutation callbacks from CanvasWorkspace into a single hook.
 * Handles artifact creation, update, blocker, comment, link, and type-change operations.
 * 
 * @doc.type hook
 * @doc.purpose Canvas CRUD callbacks
 * @doc.layer product
 * @doc.pattern Extracted Hook
 */

import { useCallback } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { type Node, type Edge, MarkerType, type ReactFlowInstance } from '@xyflow/react';
import { useCreateArtifact, useUpdateArtifact, useTransitionStage } from '@/hooks/useLifecycleData';
import { LifecyclePhase } from '@/types/lifecycle';
import { ArtifactType, type FOWStage } from '@/types/fow-stages';
import {
    nodesAtom, edgesAtom, selectedNodesAtom, isInspectorOpenAtom, selectedArtifactAtom,
    copiedNodesAtom, ghostNodesAtom, canvasAnnouncementAtom,
} from '../workspace';
import {
    executeCommandAtom,
    executeBatchAtom,
    AddNodeCommand,
    RemoveNodesCommand,
    PasteNodesCommand,
    UpdateNodeDataCommand,
    AddEdgeCommand,
    MoveNodesCommand,
} from '../workspace/canvasCommands';
import { type ArtifactNodeData } from '../nodes/ArtifactNode';
import { type DependencyEdgeData } from '../edges';
import { type ArtifactTemplate, type InspectorArtifact } from '../workspace';
import { migrateData } from '@/lib/canvas/migrationRules';
import { getZonePlacementPosition } from '../workspace/SpatialZones';
import type { GhostNodeTemplate } from '../workspace';

export interface UseCanvasHandlersConfig {
    projectId: string;
    currentPhase: LifecyclePhase;
    fowStage: FOWStage;
    personaName: string | undefined;
    artifacts: Array<{ id: string; type: string; title: string; description?: string; status: string; createdBy?: string; phase: LifecyclePhase; linkedArtifacts?: string[]; data?: Record<string, unknown> }> | undefined;
    userId: string | null;
    reactFlowInstance: ReactFlowInstance | null;
}

export function useCanvasHandlers(config: UseCanvasHandlersConfig) {
    const { projectId, currentPhase, fowStage, personaName, artifacts, userId, reactFlowInstance } = config;

    const nodes = useAtomValue(nodesAtom);
    const edges = useAtomValue(edgesAtom);
    const setNodesAtom = useSetAtom(nodesAtom);
    const setEdgesAtom = useSetAtom(edgesAtom);
    const [selectedNodes, setSelectedNodes] = useAtom(selectedNodesAtom);
    const [copiedNodes, setCopiedNodes] = useAtom(copiedNodesAtom);
    const [, setIsInspectorOpen] = useAtom(isInspectorOpenAtom);
    const [, setSelectedArtifact] = useAtom(selectedArtifactAtom);
    const [, setGhostNodes] = useAtom(ghostNodesAtom);
    const executeCommand = useSetAtom(executeCommandAtom);
    const executeBatch = useSetAtom(executeBatchAtom);
    const announce = useSetAtom(canvasAnnouncementAtom);

    const { mutateAsync: createArtifact } = useCreateArtifact();
    const { mutateAsync: updateArtifact } = useUpdateArtifact();
    const { mutateAsync: transitionStage } = useTransitionStage();

    // --- Create Artifact ---
    const handleCreateArtifact = useCallback(
        async (template: ArtifactTemplate, position: { x: number; y: number }) => {
            const newId = `artifact-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

            const newNode: Node<ArtifactNodeData> = {
                id: newId,
                type: 'artifact',
                position,
                data: {
                    id: newId,
                    type: template.type as ArtifactType,
                    title: template.defaultTitle || template.label || 'New Artifact',
                    description: template.description || '',
                    status: 'pending',
                    persona: personaName || 'developer',
                    phase: (template.phase || currentPhase) as LifecyclePhase,
                    linkedCount: 0,
                    onEdit: (id: string) => {
                        setSelectedNodes([id]);
                        setIsInspectorOpen(true);
                    },
                    onLink: (_id: string) => { /* handled by inspector */ },
                },
            };

            // Command pattern: AddNodeCommand pushes to history and executes
            executeCommand(new AddNodeCommand(newNode));
            announce(`Created artifact: ${newNode.data.title}`);

            try {
                await createArtifact({
                    type: template.type,
                    title: newNode.data.title,
                    description: newNode.data.description || '',
                    phase: template.phase || currentPhase,
                    status: 'pending',
                    createdBy: personaName || 'developer',
                    linkedArtifacts: [],
                });
            } catch (err) {
                announce('Failed to save artifact to server');
            }
        },
        [personaName, currentPhase, setNodesAtom, setSelectedNodes, setIsInspectorOpen, createArtifact, announce, executeCommand]
    );

    // --- Copy Nodes ---
    // Note: copying is handled in CanvasWorkspace (reads current nodes inline)
    // and passes copied nodes to setCopiedNodes. This hook provides paste/delete.

    // --- Paste Nodes ---
    const handlePasteNodes = useCallback(() => {
        if (copiedNodes.length === 0) return;
        const newNodes: Node<ArtifactNodeData>[] = copiedNodes.map((node, index) => ({
            ...node,
            id: `artifact-${Date.now()}-${index}`,
            data: { ...node.data, id: `artifact-${Date.now()}-${index}` },
            position: {
                x: node.position.x + 50 + index * 20,
                y: node.position.y + 50 + index * 20,
            },
        }));
        executeCommand(new PasteNodesCommand(newNodes));
        announce(`Pasted ${newNodes.length} nodes`);
    }, [copiedNodes, executeCommand, announce]);

    // --- Delete Selected Nodes ---
    const handleDeleteSelected = useCallback(() => {
        if (selectedNodes.length === 0) return;
        // ✅ Read directly from atom value — no setter-as-getter antipattern.
        // RemoveNodesCommand stores only the deleted nodes (O(deleted)), not
        // the full canvas snapshot (was O(n × MAX_HISTORY), now memory-safe).
        const ids = new Set(selectedNodes);
        const deletedNodes = nodes.filter((n) => ids.has(n.id));
        const deletedEdges = edges.filter(
            (e) => ids.has(e.source) || ids.has(e.target)
        );
        executeCommand(new RemoveNodesCommand(deletedNodes, deletedEdges));
        setSelectedNodes([]);
        announce(`Deleted ${selectedNodes.length} node${selectedNodes.length > 1 ? 's' : ''}`);
    }, [selectedNodes, nodes, edges, executeCommand, setSelectedNodes, announce]);

    // --- Select All ---
    const handleSelectAll = useCallback(() => {
        // ✅ Read directly from atom value — no setter-as-getter antipattern.
        setSelectedNodes(nodes.map((n) => n.id));
    }, [nodes, setSelectedNodes]);

    // --- Update Artifact ---
    const handleUpdateArtifact = useCallback(
        (id: string, updates: Partial<InspectorArtifact>) => {
            // ✅ Read current node data directly — no setter-as-getter antipattern.
            const node = nodes.find((n) => n.id === id);
            const currentData = node ? { ...(node.data as Record<string, unknown>) } : {};
            executeCommand(new UpdateNodeDataCommand(
                id,
                currentData,
                updates as Record<string, unknown>,
                `Update ${updates.title ?? id}`,
            ));
            updateArtifact({ artifactId: id, data: updates as Record<string, unknown> }).catch(
                () => announce('Failed to update artifact')
            );
        },
        [nodes, executeCommand, updateArtifact, announce]
    );

    // --- Add Blocker ---
    const handleAddBlocker = useCallback(
        (artifactId: string, blocker: Record<string, unknown>) => {
            updateArtifact({
                artifactId,
                data: { blockers: [blocker], status: 'blocked' } as Record<string, unknown>,
            }).catch(() => announce('Failed to add blocker'));
        },
        [updateArtifact, announce]
    );

    // --- Add Comment ---
    const handleAddComment = useCallback(
        (artifactId: string, content: string) => {
            updateArtifact({
                artifactId,
                data: {
                    comments: [{ content, createdBy: userId || 'current-user', createdAt: new Date().toISOString() }],
                } as Record<string, unknown>,
            }).catch(() => announce('Failed to add comment'));
        },
        [updateArtifact, userId, announce]
    );

    // --- Link Artifacts ---
    const handleLinkArtifact = useCallback(
        (artifactId: string, targetId: string) => {
            const artifact = artifacts?.find((a) => a.id === artifactId);
            const existingLinks = artifact?.linkedArtifacts || [];
            if (existingLinks.includes(targetId)) return;

            updateArtifact({
                artifactId,
                data: { linkedArtifacts: [...existingLinks, targetId] } as Record<string, unknown>,
            }).catch(() => announce('Failed to link artifact'));

            const newEdge: Edge<DependencyEdgeData> = {
                id: `${artifactId}->${targetId}`,
                source: artifactId, target: targetId,
                type: 'dependency',
                data: { type: 'requires', label: 'Linked' } as DependencyEdgeData,
                markerEnd: { type: MarkerType.ArrowClosed, color: 'var(--color-primary, #1976d2)' },
            };
            executeCommand(new AddEdgeCommand(newEdge));
        },
        [artifacts, updateArtifact, executeCommand, announce]
    );

    // --- Ghost Node Handlers ---
    const handleAcceptGhost = useCallback(async (ghostId: string) => {
        setGhostNodes((prev) => {
            const ghost = prev.find((g) => g.id === ghostId);
            if (!ghost) return prev;
            createArtifact({
                type: ghost.data.type,
                title: ghost.data.title,
                description: ghost.data.description || '',
                phase: currentPhase,
                status: 'pending',
                createdBy: personaName || 'AI Assistant',
                linkedArtifacts: [],
            }).catch(() => announce('Failed to create artifact from suggestion'));
            return prev.filter((g) => g.id !== ghostId);
        });
    }, [createArtifact, currentPhase, personaName, setGhostNodes, announce]);

    const handleRejectGhost = useCallback((ghostId: string) => {
        setGhostNodes((prev) => prev.filter((g) => g.id !== ghostId));
    }, [setGhostNodes]);

    // --- Ghost Node Create ---
    const handleGhostNodeCreate = useCallback((template: GhostNodeTemplate) => {
        const artifactTemplate: ArtifactTemplate = {
            type: template.id.replace('ghost-', '') as unknown as import('../workspace').ArtifactType,
            icon: '',
            label: template.title,
            description: template.description,
            phase: template.phase,
            defaultTitle: template.title,
        };
        const position = getZonePlacementPosition(template.phase, 0);
        handleCreateArtifact(artifactTemplate, position);
    }, [handleCreateArtifact]);

    // --- Type Change ---
    const handleTypeChange = useCallback((artifactId: string, newType: ArtifactType) => {
        const artifact = artifacts?.find((a) => a.id === artifactId);
        if (!artifact) return;
        const migratedData = migrateData(artifact.data || {}, artifact.type, newType);
        updateArtifact({
            artifactId,
            data: { type: newType, data: migratedData } as Record<string, unknown>,
        }).catch(() => announce('Failed to change artifact type'));

        setNodesAtom((prev) => prev.map((node) => {
            if (node.id === artifactId) {
                return { ...node, data: { ...node.data, type: newType, ...(migratedData ? { migratedData } : {}) } };
            }
            return node;
        }));
    }, [artifacts, setNodesAtom, updateArtifact, announce]);

    // --- Phase Transition ---
    const handleProceedToNext = useCallback(() => {
        const phases = [LifecyclePhase.INTENT, LifecyclePhase.SHAPE, LifecyclePhase.VALIDATE, LifecyclePhase.GENERATE, LifecyclePhase.RUN, LifecyclePhase.OBSERVE, LifecyclePhase.IMPROVE];
        const index = phases.indexOf(currentPhase);
        const nextPhase = index >= 0 && index < phases.length - 1 ? phases[index + 1] : undefined;
        if (nextPhase) {
            transitionStage({
                projectId,
                targetStage: nextPhase as unknown as FOWStage,
            }).catch(() => announce('Phase transition failed'));
        }
    }, [currentPhase, projectId, transitionStage, announce]);

    // --- Move Nodes (keyboard accessibility) ---
    const handleMoveSelectedNodes = useCallback((dx: number, dy: number) => {
        if (selectedNodes.length === 0) return;
        const moveFrom: Record<string, { x: number; y: number }> = {};
        const moveTo: Record<string, { x: number; y: number }> = {};
        // Read current positions from the atom value directly — no getter-inside-setter antipattern
        nodes.forEach((n) => {
            if (selectedNodes.includes(n.id)) {
                moveFrom[n.id] = { x: n.position.x, y: n.position.y };
                moveTo[n.id] = { x: n.position.x + dx, y: n.position.y + dy };
            }
        });
        if (Object.keys(moveTo).length === 0) return;
        executeCommand(new MoveNodesCommand(selectedNodes, moveFrom, moveTo));
    }, [selectedNodes, nodes, executeCommand]);
    // Note: setNodesAtom kept because it's used elsewhere in the hook (handleCreateArtifact, etc.)

    return {
        handleCreateArtifact,
        handlePasteNodes,
        handleDeleteSelected,
        handleSelectAll,
        handleUpdateArtifact,
        handleAddBlocker,
        handleAddComment,
        handleLinkArtifact,
        handleAcceptGhost,
        handleRejectGhost,
        handleGhostNodeCreate,
        handleTypeChange,
        handleProceedToNext,
        handleMoveSelectedNodes,
    };
}
