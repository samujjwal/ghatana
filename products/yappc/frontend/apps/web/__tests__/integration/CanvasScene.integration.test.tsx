/**
 * CanvasScene Integration Tests
 * 
 * @doc.type test
 * @doc.purpose Test CanvasScene integration with lifecycle, AI, and templates
 * @doc.layer product
 */

import { describe, it, expect, beforeEach } from '@jest/globals';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { act } from 'react-dom/test-utils';
import { BrowserRouter } from 'react-router-dom';
import CanvasScene from '../../../src/routes/app/project/canvas/CanvasScene';
import { LifecyclePhase } from '../../../src/types/lifecycle';

// Mock dependencies
jest.mock('../../../src/routes/app/project/canvas/useCanvasScene');
jest.mock('@ghatana/yappc-sketch', () => ({
    SketchToolbar: () => <div data-testid="sketch-toolbar">Sketch Toolbar</div>,
}));

describe('CanvasScene Integration', () => {
    const mockUseCanvasScene = require('../../../src/routes/app/project/canvas/useCanvasScene');

    beforeEach(() => {
        mockUseCanvasScene.useCanvasScene.mockReturnValue({
            nodes: [],
            edges: [],
            handleInit: jest.fn(),
            handleNodesChange: jest.fn(),
            handleEdgesChange: jest.fn(),
            handleConnect: jest.fn(),
            handleSelectionChange: jest.fn(),
            handleNodeDoubleClick: jest.fn(),
            handleAddComponent: jest.fn(),
            handleDragEnd: jest.fn(),
            handleKeyDown: jest.fn(),
            selectedElementId: null,
            currentUser: { id: 'user1', name: 'Test User' },
            commentsOpen: false,
            setCommentsOpen: jest.fn(),
        });
    });

    describe('Lifecycle Integration', () => {
        it('should render lifecycle phase indicator', () => {
            render(
                <BrowserRouter>
                    <CanvasScene />
                </BrowserRouter>
            );

            expect(screen.getByTestId('lifecycle-phase-indicator')).toBeInTheDocument();
        });

        it('should allow phase transitions', async () => {
            render(
                <BrowserRouter>
                    <CanvasScene />
                </BrowserRouter>
            );

            const phaseButton = screen.getByText('SHAPE');

            await act(async () => {
                fireEvent.click(phaseButton);
            });

            await waitFor(() => {
                expect(screen.getByText('SHAPE')).toHaveClass('active');
            });
        });

        it('should filter operations based on phase', async () => {
            render(
                <BrowserRouter>
                    <CanvasScene />
                </BrowserRouter>
            );

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
            render(
                <BrowserRouter>
                    <CanvasScene />
                </BrowserRouter>
            );

            expect(screen.getByTestId('ai-badge')).toBeInTheDocument();
        });

        it('should open AI suggestions panel', async () => {
            render(
                <BrowserRouter>
                    <CanvasScene />
                </BrowserRouter>
            );

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

            render(
                <BrowserRouter>
                    <CanvasScene />
                </BrowserRouter>
            );

            await waitFor(() => {
                expect(screen.getByTestId('ghost-node-ghost1')).toBeInTheDocument();
            });
        });
    });

    describe('Template Integration', () => {
        it('should open template menu', async () => {
            render(
                <BrowserRouter>
                    <CanvasScene />
                </BrowserRouter>
            );

            const templateButton = screen.getByText('Templates');

            await act(async () => {
                fireEvent.click(templateButton);
            });

            await waitFor(() => {
                expect(screen.getByTestId('template-menu')).toBeInTheDocument();
            });
        });

        it('should save canvas as template', async () => {
            render(
                <BrowserRouter>
                    <CanvasScene />
                </BrowserRouter>
            );

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

            render(
                <BrowserRouter>
                    <CanvasScene />
                </BrowserRouter>
            );

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

            render(
                <BrowserRouter>
                    <CanvasScene />
                </BrowserRouter>
            );

            const endTime = performance.now();
            const renderTime = endTime - startTime;

            // Should render in less than 1 second (1000ms)
            expect(renderTime).toBeLessThan(1000);
        });

        it('should track performance metrics', () => {
            render(
                <BrowserRouter>
                    <CanvasScene />
                </BrowserRouter>
            );

            // Performance monitor should be active
            expect(screen.getByTestId('performance-panel')).toBeInTheDocument();
        });
    });

    describe('Keyboard Shortcuts', () => {
        it('should handle undo/redo shortcuts', async () => {
            const mockHandleKeyDown = jest.fn();
            mockUseCanvasScene.useCanvasScene.mockReturnValue({
                ...mockUseCanvasScene.useCanvasScene(),
                handleKeyDown: mockHandleKeyDown,
            });

            render(
                <BrowserRouter>
                    <CanvasScene />
                </BrowserRouter>
            );

            const canvas = screen.getByTestId('canvas-scene');

            // Cmd+Z for undo
            fireEvent.keyDown(canvas, { key: 'z', metaKey: true });
            expect(mockHandleKeyDown).toHaveBeenCalled();

            // Cmd+Shift+Z for redo
            fireEvent.keyDown(canvas, { key: 'z', metaKey: true, shiftKey: true });
            expect(mockHandleKeyDown).toHaveBeenCalled();
        });

        it('should handle save shortcut', async () => {
            const mockHandleKeyDown = jest.fn();
            mockUseCanvasScene.useCanvasScene.mockReturnValue({
                ...mockUseCanvasScene.useCanvasScene(),
                handleKeyDown: mockHandleKeyDown,
            });

            render(
                <BrowserRouter>
                    <CanvasScene />
                </BrowserRouter>
            );

            const canvas = screen.getByTestId('canvas-scene');

            // Cmd+S for save
            fireEvent.keyDown(canvas, { key: 's', metaKey: true });
            expect(mockHandleKeyDown).toHaveBeenCalled();
        });
    });
});
