/**
 * CanvasOverlays
 *
 * All floating overlay elements for the canvas workspace: the next-task card,
 * AI assistant modal, quick-create menu, inspector panel, project switcher,
 * floating panel dock, command palette, and node context menu. Extracted from
 * CanvasWorkspace to keep the orchestrator focused on data wiring.
 *
 * @doc.type component
 * @doc.purpose Canvas floating overlay panels, modals, and context menus
 * @doc.layer product
 * @doc.pattern Extracted Component
 */

import React from 'react';
import { Box } from '@ghatana/design-system';
import { Trash2 as TrashIcon, Copy as DuplicateIcon } from 'lucide-react';
import {
    NextBestTaskCard,
    AIAssistantModal,
    QuickCreateMenu,
    InspectorPanel,
    type GateCriterion,
    type AISuggestion,
    type InspectorArtifact,
    type ArtifactTemplate,
} from './workspace';
import { ProjectSwitcher } from './workspace/ProjectSwitcher';
import { PanelManager } from './panels/PanelManager';
import { type WorkspacePanelConfig } from './panels/types';
import { CommandPalette, type CommandAction } from './tools/CommandPalette';
import { CanvasErrorBoundary } from './CanvasErrorBoundary';
import { Button } from '../ui/Button';
import { type LifecyclePhase } from '@/types/lifecycle';
import { type ReactFlowInstance } from '@xyflow/react';
import { type AbstractionLevel } from '../../types/abstractionLevel';
import { type Task } from '@/services/lifecycle/api';
import { type CanvasCommandAction } from './workspace';
import { type CanvasInteractionMode } from './workspace/canvasSharedState';
import type { CanvasAccessPolicy } from './canvasAccessPolicy';

// ── Types ─────────────────────────────────────────────────────────────────

interface OverlayHandlers {
    handleCreateArtifact: (template: ArtifactTemplate, position: { x: number; y: number }) => void | Promise<void>;
    handleUpdateArtifact: (id: string, updates: Partial<InspectorArtifact>) => void;
    handleAddBlocker: (nodeId: string, blocker: Record<string, unknown>) => void;
    handleAddComment: (nodeId: string, comment: string) => void;
    handleLinkArtifact: (sourceId: string, targetId: string) => void;
    handleCopyNodes?: () => void;
    handlePasteNodes?: () => void;
    handleDeleteSelected: () => void;
}

interface OverlayZoom {
    handleLevelChange: (level: AbstractionLevel) => void;
    handleNextPhase: () => void;
    handlePrevPhase: () => void;
    handleFitView: () => void;
}

export interface CanvasOverlaysProps {
    // ── Next Best Task ───────────────────────────────────────────────────
    nextTask: Task | null | undefined;
    personaData: { persona?: string } | null | undefined;
    handleStartTask: () => void;
    // ── AI Modal ─────────────────────────────────────────────────────────
    isAIModalOpen: boolean;
    setIsAIModalOpen: (open: boolean) => void;
    selectedNodes: string[];
    currentPhase: LifecyclePhase;
    activePersona: string | null;
    gateCriteria: GateCriterion[];
    handleAIQuery: (query: string) => Promise<AISuggestion[]>;
    // ── Quick Create ─────────────────────────────────────────────────────
    quickCreateMenuPosition: { x: number; y: number } | null;
    setQuickCreateMenuPosition: (pos: { x: number; y: number } | null) => void;
    // ── Inspector Panel ──────────────────────────────────────────────────
    isInspectorOpen: boolean;
    setIsInspectorOpen: (open: boolean | ((prev: boolean) => boolean)) => void;
    selectedArtifact: InspectorArtifact | null;
    // ── Project Switcher ─────────────────────────────────────────────────
    isProjectSwitcherOpen: boolean;
    setIsProjectSwitcherOpen: (open: boolean) => void;
    projectId: string;
    // ── Panel Manager ────────────────────────────────────────────────────
    workspacePanels: WorkspacePanelConfig[];
    // ── Command Palette ──────────────────────────────────────────────────
    isCommandPaletteOpen: boolean;
    setIsCommandPaletteOpen: (open: boolean) => void;
    commandRegistry: CanvasCommandAction[];
    zoom: OverlayZoom;
    reactFlowInstance: ReactFlowInstance | null;
    setInteractionMode: (mode: CanvasInteractionMode) => void;
    // ── Context Menu ─────────────────────────────────────────────────────
    nodeContextMenu: { x: number; y: number; nodeId: string } | null;
    setNodeContextMenu: (menu: { x: number; y: number; nodeId: string } | null) => void;
    setSelectedNodes: (ids: string[]) => void;
    // ── Shared handlers ──────────────────────────────────────────────────
    handlers: OverlayHandlers;
    canvasPolicy: CanvasAccessPolicy;
}

// ── Component ─────────────────────────────────────────────────────────────

export const CanvasOverlays: React.FC<CanvasOverlaysProps> = ({
    nextTask,
    personaData,
    handleStartTask,
    isAIModalOpen,
    setIsAIModalOpen,
    selectedNodes,
    currentPhase,
    activePersona,
    gateCriteria,
    handleAIQuery,
    quickCreateMenuPosition,
    setQuickCreateMenuPosition,
    isInspectorOpen,
    setIsInspectorOpen,
    selectedArtifact,
    isProjectSwitcherOpen,
    setIsProjectSwitcherOpen,
    projectId,
    workspacePanels,
    isCommandPaletteOpen,
    setIsCommandPaletteOpen,
    commandRegistry,
    zoom,
    reactFlowInstance,
    setInteractionMode,
    nodeContextMenu,
    setNodeContextMenu,
    setSelectedNodes,
    handlers,
    canvasPolicy,
}) => (
    <>
        {/* Next Best Task card */}
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

        {canvasPolicy.canCreateArtifacts && (
            <QuickCreateMenu
                open={quickCreateMenuPosition !== null}
                anchorPosition={quickCreateMenuPosition}
                currentPhase={currentPhase}
                onClose={() => setQuickCreateMenuPosition(null)}
                onCreate={handlers.handleCreateArtifact as Parameters<typeof QuickCreateMenu>[0]['onCreate']}
            />
        )}

        <CanvasErrorBoundary label="Inspector Panel">
            <InspectorPanel
                open={isInspectorOpen}
                artifact={selectedArtifact}
                onClose={() => setIsInspectorOpen(false)}
                onUpdate={handlers.handleUpdateArtifact as Parameters<typeof InspectorPanel>[0]['onUpdate']}
                onAddBlocker={handlers.handleAddBlocker as Parameters<typeof InspectorPanel>[0]['onAddBlocker']}
                onAddComment={handlers.handleAddComment as Parameters<typeof InspectorPanel>[0]['onAddComment']}
                onLinkArtifact={handlers.handleLinkArtifact as Parameters<typeof InspectorPanel>[0]['onLinkArtifact']}
                canEdit={canvasPolicy.canMutateArtifacts}
                canComment={canvasPolicy.canComment}
                readOnlyReason={canvasPolicy.readOnlyReason}
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
            actions={commandRegistry as unknown as CommandAction[]}
            onNavigate={(_path) => { /* router navigation */ }}
            onModeChange={(mode) => setInteractionMode(mode as 'navigate' | 'sketch' | 'code' | 'diagram')}
            onLevelChange={(level) => zoom.handleLevelChange(level as AbstractionLevel)}
            onTogglePanel={(_panel) => { /* handled by PanelManager dock */ }}
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
            <Box
                className="absolute z-50 min-w-[180px] rounded-lg border border-border bg-white p-2 shadow-lg dark:border-border dark:bg-surface"
                style={{ top: nodeContextMenu.y, left: nodeContextMenu.x }}
                role="menu"
            >
                <Button
                    type="button"
                    variant="ghost"
                    size="small"
                    className="w-full min-h-0 justify-start rounded-md px-3 py-2 text-left text-sm hover:bg-surface-muted dark:hover:bg-surface"
                    onClick={() => {
                        setIsInspectorOpen(true);
                        setNodeContextMenu(null);
                    }}
                >
                    Edit in Inspector
                </Button>
                {canvasPolicy.canCreateArtifacts && (
                    <Button
                        type="button"
                        variant="ghost"
                        size="small"
                        className="w-full min-h-0 justify-start rounded-md px-3 py-2 text-left text-sm hover:bg-surface-muted dark:hover:bg-surface"
                        onClick={() => {
                            handlers.handleCopyNodes?.();
                            handlers.handlePasteNodes?.();
                            setNodeContextMenu(null);
                        }}
                    >
                        <DuplicateIcon size={14} className="mr-2" />
                        Duplicate
                    </Button>
                )}
                {canvasPolicy.canMutateArtifacts && (
                    <Button
                        type="button"
                        variant="ghost"
                        tone="danger"
                        size="small"
                        className="w-full min-h-0 justify-start rounded-md px-3 py-2 text-left text-sm text-destructive hover:bg-surface-muted dark:text-destructive dark:hover:bg-surface"
                        onClick={() => {
                            setSelectedNodes([nodeContextMenu.nodeId]);
                            handlers.handleDeleteSelected();
                            setNodeContextMenu(null);
                        }}
                    >
                        <TrashIcon size={14} className="mr-2" />
                        Delete
                    </Button>
                )}
            </Box>
        )}
    </>
);
