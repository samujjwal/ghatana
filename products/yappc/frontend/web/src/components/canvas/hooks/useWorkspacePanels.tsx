/**
 * useWorkspacePanels
 *
 * Builds the WorkspacePanelConfig array for the floating panel dock.
 * Extracted from CanvasWorkspace so that zoom/persona/history state changes
 * don't force the entire workspace to re-render.
 *
 * @doc.type hook
 * @doc.purpose Workspace panel configuration factory
 * @doc.layer product
 * @doc.pattern Extracted Hook
 */

import { useMemo } from 'react';
import { Box, Button, Typography, Divider } from '@ghatana/design-system';
import { Map as MapIcon, User as PersonIcon, History as HistoryIcon, Gauge as SpeedIcon } from 'lucide-react';
import {
    PresenceIndicator,
    PersonaFilterToolbar,
    ViewModeSelector,
    type PersonaFilterData,
} from '../workspace';
import { AbstractionLevelNavigator } from '../AbstractionLevelNavigator';
import { type WorkspacePanelConfig } from '../panels/types';
import { PerformanceMetricsPanel } from '../panels/PerformanceMetricsPanel';
import { type AbstractionLevel, type AbstractionBreadcrumb, getNextLevel } from '../../../types/abstractionLevel';

// ── Types ─────────────────────────────────────────────────────────────────

export interface UseWorkspacePanelsProps {
    zoom: {
        currentAbstractionLevel: AbstractionLevel;
        breadcrumbs: AbstractionBreadcrumb[];
        canDrillDown: boolean;
        canZoomOut: boolean;
        handleLevelChange: (level: AbstractionLevel) => void;
        handleBreadcrumbClick: (index: number) => void;
        handleZoomOut: () => void;
    };
    personaFilterData: PersonaFilterData[];
    activePersona: string | null;
    setActivePersona: (persona: string | null) => void;
    nodeCount: number;
    canUndo: boolean;
    canRedo: boolean;
    undo: () => void;
    redo: () => void;
}

// ── Hook ──────────────────────────────────────────────────────────────────

export function useWorkspacePanels({
    zoom,
    personaFilterData,
    activePersona,
    setActivePersona,
    nodeCount,
    canUndo,
    canRedo,
    undo,
    redo,
}: UseWorkspacePanelsProps): WorkspacePanelConfig[] {
    return useMemo<WorkspacePanelConfig[]>(() => [
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
                        onReset={() => zoom.handleLevelChange('system' as AbstractionLevel)}
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
                    <Typography as="p" className="text-xs text-fg-muted">
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
            content: <PerformanceMetricsPanel nodeCount={nodeCount} />,
        },
    ], [
        zoom.currentAbstractionLevel, zoom.breadcrumbs, zoom.canDrillDown, zoom.canZoomOut,
        zoom.handleLevelChange, zoom.handleBreadcrumbClick, zoom.handleZoomOut,
        personaFilterData, activePersona, setActivePersona,
        nodeCount,
        canUndo, canRedo, undo, redo,
    ]);
}
