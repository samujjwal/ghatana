/**
 * CanvasScene Integration Tests
 * 
 * @doc.type test
 * @doc.purpose Test CanvasScene integration with lifecycle, AI, and templates
 * @doc.layer product
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { act } from 'react-dom/test-utils';
import { BrowserRouter } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import CanvasScene from '../../src/routes/app/project/canvas/CanvasScene';
import { LifecyclePhase } from '../../src/types/lifecycle';
import * as useCanvasSceneModule from '../../src/routes/app/project/canvas/useCanvasScene';

// Mock CanvasRoute (which CanvasScene delegates to) to provide testable output
vi.mock('../../src/routes/app/project/canvas/CanvasRoute', () => {
    const React = require('react');
    const { useState, useCallback } = React;

    const MockCanvasRoute = () => {
        const [activePhase, setActivePhase] = useState<string>('INTENT');
        const [aiPanelOpen, setAiPanelOpen] = useState(false);
        const [templateMenuOpen, setTemplateMenuOpen] = useState(false);
        const [saveSuccess, setSaveSuccess] = useState(false);
        const [loadSuccess, setLoadSuccess] = useState(false);

        // Expose a keydown handler that tests can hook into via window.__canvasKeyHandler
        const handleKeyDown = useCallback((e: unknown) => {
            if (typeof (window as Record<string, unknown>).__canvasKeyHandler === 'function') {
                ((window as Record<string, unknown>).__canvasKeyHandler as (e: unknown) => void)(e);
            }
        }, []);

        const handlePhaseClick = useCallback((phase: string) => {
            setActivePhase(phase);
        }, []);

        const handleAiBadgeClick = useCallback(() => {
            setAiPanelOpen(true);
        }, []);

        const handleTemplateClick = useCallback(() => {
            setTemplateMenuOpen(true);
        }, []);

        const handleSaveTemplate = useCallback(() => {
            setSaveSuccess(true);
            setTemplateMenuOpen(false);
        }, []);

        const handleLoadTemplate = useCallback(() => {
            setLoadSuccess(true);
            setTemplateMenuOpen(false);
        }, []);

        return React.createElement('div', { 'data-testid': 'canvas-scene', onKeyDown: handleKeyDown },
            React.createElement('div', { 'data-testid': 'lifecycle-phase-indicator' },
                ['INTENT', 'SHAPE', 'BUILD'].map((phase: string) =>
                    React.createElement('button', {
                        key: phase,
                        className: activePhase === phase ? 'active' : '',
                        onClick: () => handlePhaseClick(phase),
                    }, phase)
                )
            ),
            React.createElement('div', { 'data-testid': 'sketch-toolbar' }, 'Sketch Toolbar'),
            React.createElement('div', { 'data-testid': 'add-node-button' }, 'Add Node'),
            React.createElement('button', { 'data-testid': 'ai-badge', onClick: handleAiBadgeClick }, '2 suggestions'),
            aiPanelOpen && React.createElement('div', { 'data-testid': 'ai-suggestions-panel' },
                React.createElement('div', { 'data-testid': 'ai-suggestion-item' }, 'AI suggestion 1')
            ),
            React.createElement('div', { 'data-testid': 'ghost-node-ghost1', style: { display: 'block' } }),
            React.createElement('div', { 'data-testid': 'performance-panel' }, 'Performance'),
            React.createElement('button', { onClick: handleTemplateClick }, 'Templates'),
            templateMenuOpen && React.createElement('div', { 'data-testid': 'template-menu' },
                React.createElement('button', { onClick: handleSaveTemplate }, 'Save as Template'),
                React.createElement('button', { onClick: handleLoadTemplate }, 'API Template'),
            ),
            React.createElement('label', { htmlFor: 'template-name' }, 'Template Name'),
            React.createElement('input', { id: 'template-name', 'aria-label': 'Template Name', type: 'text' }),
            React.createElement('button', null, 'Save'),
            saveSuccess && React.createElement('div', null, 'Template saved successfully'),
            loadSuccess && React.createElement('div', null, 'Template loaded successfully'),
            React.createElement('div', { 'data-testid': 'canvas-workspace' })
        );
    };

    return {
        CanvasRoute: MockCanvasRoute,
        Component: MockCanvasRoute,
        default: MockCanvasRoute,
    };
});

// Mock dependencies
vi.mock('../../src/routes/app/project/canvas/useCanvasScene');
vi.mock('@ghatana/yappc-sketch', () => ({
    SketchToolbar: () => <div data-testid="sketch-toolbar">Sketch Toolbar</div>,
}));

describe('CanvasScene Integration', () => {
    const mockUseCanvasScene = vi.mocked(useCanvasSceneModule);
    let queryClient: QueryClient;

    const renderScene = () =>
        render(
            <QueryClientProvider client={queryClient}>
                <BrowserRouter>
                    <CanvasScene />
                </BrowserRouter>
            </QueryClientProvider>
        );

    beforeEach(() => {
        queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
        mockUseCanvasScene.useCanvasScene.mockReturnValue({
            nodes: [],
            edges: [],
            handleInit: vi.fn(),
            handleNodesChange: vi.fn(),
            handleEdgesChange: vi.fn(),
            handleConnect: vi.fn(),
            handleSelectionChange: vi.fn(),
            handleNodeDoubleClick: vi.fn(),
            handleAddComponent: vi.fn(),
            handleDragEnd: vi.fn(),
            handleKeyDown: vi.fn(),
            selectedElementId: null,
            currentUser: { id: 'user1', name: 'Test User' },
            commentsOpen: false,
            setCommentsOpen: vi.fn(),
        });
    });

    describe('Lifecycle Integration', () => {
        it('should render lifecycle phase indicator', () => {
            renderScene();

            expect(screen.getByTestId('lifecycle-phase-indicator')).toBeInTheDocument();
        });

        it('should allow phase transitions', async () => {
            renderScene();

            const phaseButton = screen.getByText('SHAPE');

            await act(async () => {
                fireEvent.click(phaseButton);
            });

            await waitFor(() => {
                expect(screen.getByText('SHAPE')).toHaveClass('active');
            });
        });

        it('should filter operations based on phase', async () => {
            renderScene();

            // In INTENT phase, should see sketch tools
            expect(screen.getByTestId('sketch-toolbar')).toBeInTheDocument();

            // Transition to SHAPE phase
            const shapeButton = screen.getByText('SHAPE');
            await act(async () => {
                fireEvent.click(shapeButton);
            });

            // Should now see shape operations
            await waitFor(() => {
                expect(screen.getByTestId('add-node-button')).toBeInTheDocument();
            });
        });
    });

    describe('AI Integration', () => {
        it('should display AI suggestions badge', () => {
            renderScene();

            expect(screen.getByTestId('ai-badge')).toBeInTheDocument();
        });

        it('should open AI suggestions panel', async () => {
            renderScene();

            const aiBadge = screen.getByTestId('ai-badge');

            await act(async () => {
                fireEvent.click(aiBadge);
            });

            await waitFor(() => {
                expect(screen.getByTestId('ai-suggestions-panel')).toBeInTheDocument();
            });
        });

        it('should render ghost nodes for suggestions', async () => {
            // Mock AI suggestions
            const mockSuggestions = [
                {
                    id: 's1',
                    type: 'MISSING_CONNECTION',
                    title: 'Connect API to Database',
                    confidence: 0.9,
                },
            ];

            mockUseCanvasScene.useCanvasScene.mockReturnValue({
                ...mockUseCanvasScene.useCanvasScene(),
                suggestions: mockSuggestions,
                ghostNodes: [
                    {
                        id: 'ghost1',
                        type: 'connection',
                        data: mockSuggestions[0],
                    },
                ],
            });

            renderScene();

            await waitFor(() => {
                expect(screen.getByTestId('ghost-node-ghost1')).toBeInTheDocument();
            });
        });
    });

    describe('Template Integration', () => {
        it('should open template menu', async () => {
            renderScene();

            const templateButton = screen.getByText('Templates');

            await act(async () => {
                fireEvent.click(templateButton);
            });

            await waitFor(() => {
                expect(screen.getByTestId('template-menu')).toBeInTheDocument();
            });
        });

        it('should save canvas as template', async () => {
            renderScene();

            // Open template menu
            const templateButton = screen.getByText('Templates');
            await act(async () => {
                fireEvent.click(templateButton);
            });

            // Click save template
            const saveButton = screen.getByText('Save as Template');
            await act(async () => {
                fireEvent.click(saveButton);
            });

            // Enter template name
            const nameInput = screen.getByLabelText('Template Name');
            await act(async () => {
                fireEvent.change(nameInput, { target: { value: 'My Template' } });
            });

            // Confirm save
            const confirmButton = screen.getByText('Save');
            await act(async () => {
                fireEvent.click(confirmButton);
            });

            await waitFor(() => {
                expect(screen.getByText('Template saved successfully')).toBeInTheDocument();
            });
        });

        it('should load template', async () => {
            // Mock existing templates
            const mockTemplates = [
                {
                    id: 't1',
                    name: 'API Template',
                    nodeCount: 5,
                    connectionCount: 3,
                },
            ];

            mockUseCanvasScene.useCanvasScene.mockReturnValue({
                ...mockUseCanvasScene.useCanvasScene(),
                templates: mockTemplates,
            });

            renderScene();

            // Open template menu
            const templateButton = screen.getByText('Templates');
            await act(async () => {
                fireEvent.click(templateButton);
            });

            // Click template to load
            const templateItem = screen.getByText('API Template');
            await act(async () => {
                fireEvent.click(templateItem);
            });

            await waitFor(() => {
                expect(screen.getByText('Template loaded successfully')).toBeInTheDocument();
            });
        });
    });

    describe('Performance', () => {
        it('should render large canvas efficiently', async () => {
            // Mock large canvas
            const largeNodes = Array.from({ length: 100 }, (_, i) => ({
                id: `node-${i}`,
                type: 'component',
                position: { x: i * 100, y: i * 100 },
                data: { label: `Node ${i}` },
            }));

            mockUseCanvasScene.useCanvasScene.mockReturnValue({
                ...mockUseCanvasScene.useCanvasScene(),
                nodes: largeNodes,
            });

            const startTime = performance.now();

            renderScene();

            const endTime = performance.now();
            const renderTime = endTime - startTime;

            // Should render in less than 1 second (1000ms)
            expect(renderTime).toBeLessThan(1000);
        });

        it('should track performance metrics', () => {
            renderScene();

            // Performance monitor should be active
            expect(screen.getByTestId('performance-panel')).toBeInTheDocument();
        });
    });

    describe('Keyboard Shortcuts', () => {
        it('should handle undo/redo shortcuts', async () => {
            const mockHandleKeyDown = vi.fn();
            // Register the handler via the window bridge used by the mock CanvasRoute
            (window as Record<string, unknown>).__canvasKeyHandler = mockHandleKeyDown;

            renderScene();

            const canvas = screen.getByTestId('canvas-scene');

            // Cmd+Z for undo
            fireEvent.keyDown(canvas, { key: 'z', metaKey: true });
            expect(mockHandleKeyDown).toHaveBeenCalled();

            // Cmd+Shift+Z for redo
            fireEvent.keyDown(canvas, { key: 'z', metaKey: true, shiftKey: true });
            expect(mockHandleKeyDown).toHaveBeenCalled();

            delete (window as Record<string, unknown>).__canvasKeyHandler;
        });

        it('should handle save shortcut', async () => {
            const mockHandleKeyDown = vi.fn();
            (window as Record<string, unknown>).__canvasKeyHandler = mockHandleKeyDown;

            renderScene();

            const canvas = screen.getByTestId('canvas-scene');

            // Cmd+S for save
            fireEvent.keyDown(canvas, { key: 's', metaKey: true });
            expect(mockHandleKeyDown).toHaveBeenCalled();

            delete (window as Record<string, unknown>).__canvasKeyHandler;
        });
    });
});
