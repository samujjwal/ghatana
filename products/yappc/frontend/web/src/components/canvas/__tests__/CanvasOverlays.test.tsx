import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { CanvasOverlays, type CanvasOverlaysProps } from '../CanvasOverlays';
import { LifecyclePhase } from '../../../types/lifecycle';
import type { PhrCompletenessOverlayModel } from '../../../lib/phr/phrCompletenessOverlay';

vi.mock('../workspace', () => ({
    NextBestTaskCard: () => null,
    AIAssistantModal: () => null,
    QuickCreateMenu: () => null,
    InspectorPanel: () => null,
}));

vi.mock('../workspace/ProjectSwitcher', () => ({
    ProjectSwitcher: () => null,
}));

vi.mock('../panels/PanelManager', () => ({
    PanelManager: () => null,
}));

vi.mock('../tools/CommandPalette', () => ({
    CommandPalette: () => null,
}));

function buildProps(overrides: Partial<CanvasOverlaysProps> = {}): CanvasOverlaysProps {
    const handlers: CanvasOverlaysProps['handlers'] = {
        handleCreateArtifact: vi.fn(),
        handleUpdateArtifact: vi.fn(),
        handleAddBlocker: vi.fn(),
        handleAddComment: vi.fn(),
        handleLinkArtifact: vi.fn(),
        handleCopyNodes: vi.fn(),
        handlePasteNodes: vi.fn(),
        handleDeleteSelected: vi.fn(),
    };

    return {
        nextTask: null,
        personaData: null,
        handleStartTask: vi.fn(),
        isAIModalOpen: false,
        setIsAIModalOpen: vi.fn(),
        selectedNodes: [],
        currentPhase: LifecyclePhase.SHAPE,
        activePersona: null,
        gateCriteria: [],
        handleAIQuery: vi.fn(async () => []),
        quickCreateMenuPosition: null,
        setQuickCreateMenuPosition: vi.fn(),
        isInspectorOpen: false,
        setIsInspectorOpen: vi.fn(),
        selectedArtifact: null,
        isProjectSwitcherOpen: false,
        setIsProjectSwitcherOpen: vi.fn(),
        projectId: 'project-1',
        workspacePanels: [],
        isCommandPaletteOpen: false,
        setIsCommandPaletteOpen: vi.fn(),
        commandRegistry: [],
        zoom: {
            handleLevelChange: vi.fn(),
            handleNextPhase: vi.fn(),
            handlePrevPhase: vi.fn(),
            handleFitView: vi.fn(),
        },
        reactFlowInstance: null,
        setInteractionMode: vi.fn(),
        nodeContextMenu: { x: 24, y: 32, nodeId: 'node-1' },
        setNodeContextMenu: vi.fn(),
        setSelectedNodes: vi.fn(),
        handlers,
        canvasPolicy: {
            canCreateArtifacts: true,
            canMutateArtifacts: true,
            canComment: true,
            readOnlyReason: null,
        },
        ...overrides,
    };
}

describe('CanvasOverlays', () => {
    it('renders the PHR route completeness overlay when a model is provided', () => {
        const phrCompletenessOverlay: PhrCompletenessOverlayModel = {
            product: 'phr',
            generatedAt: '2026-05-28T00:00:00.000Z',
            totals: {
                routes: 2,
                stableRoutes: 1,
                hiddenRoutes: 1,
                blockedRoutes: 0,
                previewRoutes: 0,
                stableCoveragePercent: 75,
                gapCount: 1,
            },
            routes: [
                {
                    path: '/records',
                    label: 'Records',
                    group: 'care',
                    lifecycle: 'stable',
                    useCaseIds: ['uc-patient-records'],
                    webCovered: true,
                    mobileCovered: false,
                    backendCovered: true,
                    testCovered: true,
                    directLinkAllowed: true,
                    score: 75,
                },
                {
                    path: '/provider',
                    label: 'Provider',
                    group: 'provider',
                    lifecycle: 'hidden',
                    useCaseIds: ['uc-provider-dashboard'],
                    webCovered: true,
                    mobileCovered: false,
                    backendCovered: true,
                    testCovered: false,
                    directLinkAllowed: false,
                    score: 100,
                },
            ],
            gaps: [
                {
                    routePath: '/records',
                    category: 'mobile',
                    message: 'stable route has no mobile use-case coverage',
                },
            ],
        };

        render(<CanvasOverlays {...buildProps({ phrCompletenessOverlay })} />);

        expect(screen.getByRole('complementary', { name: /phr route completeness/i })).toBeInTheDocument();
        expect(screen.getByText('PHR route completeness')).toBeInTheDocument();
        expect(screen.getByText('75%')).toBeInTheDocument();
    });

    it('renders context-menu actions as shared UI buttons and handles edit', () => {
        const setIsInspectorOpen = vi.fn();
        const setNodeContextMenu = vi.fn();

        render(<CanvasOverlays {...buildProps({ setIsInspectorOpen, setNodeContextMenu })} />);

        const edit = screen.getByRole('button', { name: /edit in inspector/i });

        expect(edit).toHaveClass('inline-flex');

        fireEvent.click(edit);

        expect(setIsInspectorOpen).toHaveBeenCalledWith(true);
        expect(setNodeContextMenu).toHaveBeenCalledWith(null);
    });

    it('duplicates and deletes through policy-enabled context-menu actions', () => {
        const props = buildProps();

        render(<CanvasOverlays {...props} />);

        fireEvent.click(screen.getByRole('button', { name: /duplicate/i }));
        fireEvent.click(screen.getByRole('button', { name: /delete/i }));

        expect(props.handlers.handleCopyNodes).toHaveBeenCalled();
        expect(props.handlers.handlePasteNodes).toHaveBeenCalled();
        expect(props.setSelectedNodes).toHaveBeenCalledWith(['node-1']);
        expect(props.handlers.handleDeleteSelected).toHaveBeenCalled();
    });

    it('hides mutation actions when canvas policy is read-only', () => {
        render(
            <CanvasOverlays {...buildProps({
                canvasPolicy: {
                    canCreateArtifacts: false,
                    canMutateArtifacts: false,
                    canComment: false,
                    readOnlyReason: 'Read-only workspace',
                },
            })} />
        );

        expect(screen.getByRole('button', { name: /edit in inspector/i })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /duplicate/i })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /delete/i })).not.toBeInTheDocument();
    });
});
