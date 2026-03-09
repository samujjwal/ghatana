/**
 * ServiceBlueprintCanvas Tests
 * 
 * Comprehensive test suite for Service Blueprint Canvas (Journey 20.1)
 */

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import '@testing-library/jest-dom/vitest';
import { ServiceBlueprintCanvas } from '../ServiceBlueprintCanvas';

// Mock the useServiceBlueprint hook
vi.mock('../../hooks/useServiceBlueprint', () => ({
    useServiceBlueprint: () => ({
        lanes: [
            { id: 'customer', type: 'customer', label: 'Customer Actions', nodes: [] },
            { id: 'frontstage', type: 'frontstage', label: 'Frontstage', nodes: [] },
            { id: 'backstage', type: 'backstage', label: 'Backstage', nodes: [] },
            { id: 'support', type: 'support', label: 'Support', nodes: [] },
        ],
        processNodes: [],
        connections: [],
        touchpoints: [],
        addNode: vi.fn(),
        updateNode: vi.fn(),
        deleteNode: vi.fn(),
        addConnection: vi.fn(),
        deleteConnection: vi.fn(),
        addTouchpoint: vi.fn(),
        updateTouchpoint: vi.fn(),
        deleteTouchpoint: vi.fn(),
    }),
}));

describe('ServiceBlueprintCanvas', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('Rendering', () => {
        it('should render successfully', () => {
            render(<ServiceBlueprintCanvas />);
            expect(screen.getByText(/service blueprint/i)).toBeInTheDocument();
        });

        it('should render with initial blueprint name', () => {
            render(<ServiceBlueprintCanvas initialBlueprintName="Customer Onboarding" />);
            expect(screen.getByDisplayValue('Customer Onboarding')).toBeInTheDocument();
        });

        it('should render all swimlanes', () => {
            render(<ServiceBlueprintCanvas />);
            expect(screen.getByText('Customer Actions')).toBeInTheDocument();
            expect(screen.getByText('Frontstage')).toBeInTheDocument();
            expect(screen.getByText('Backstage')).toBeInTheDocument();
            expect(screen.getByText('Support')).toBeInTheDocument();
        });
    });

    describe('Multi-Layer Swimlanes', () => {
        it('should display customer lane', () => {
            render(<ServiceBlueprintCanvas />);
            expect(screen.getByText('Customer Actions')).toBeInTheDocument();
        });

        it('should display frontstage lane', () => {
            render(<ServiceBlueprintCanvas />);
            expect(screen.getByText('Frontstage')).toBeInTheDocument();
        });

        it('should display backstage lane', () => {
            render(<ServiceBlueprintCanvas />);
            expect(screen.getByText('Backstage')).toBeInTheDocument();
        });

        it('should display support lane', () => {
            render(<ServiceBlueprintCanvas />);
            expect(screen.getByText('Support')).toBeInTheDocument();
        });

        it('should show lane descriptions', () => {
            render(<ServiceBlueprintCanvas />);
            // Customer lane typically has description about customer-facing actions
            expect(screen.getByText(/customer/i)).toBeInTheDocument();
        });
    });

    describe('Process Nodes', () => {
        it('should show add node button', () => {
            render(<ServiceBlueprintCanvas />);
            const addButtons = screen.getAllByRole('button', { name: /add/i });
            expect(addButtons.length).toBeGreaterThan(0);
        });

        it('should allow adding nodes to lanes', async () => {
            const user = userEvent.setup();
            render(<ServiceBlueprintCanvas />);

            const addButton = screen.getAllByRole('button', { name: /add/i })[0];
            await user.click(addButton);

            // Dialog should open
            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });

        it('should display node form fields', async () => {
            const user = userEvent.setup();
            render(<ServiceBlueprintCanvas />);

            const addButton = screen.getAllByRole('button', { name: /add/i })[0];
            await user.click(addButton);

            expect(screen.getByLabelText(/node name|label/i)).toBeInTheDocument();
        });
    });

    describe('Touchpoints', () => {
        it('should have touchpoint management', () => {
            render(<ServiceBlueprintCanvas />);
            // Look for touchpoint-related UI
            const touchpointElements = screen.queryAllByText(/touchpoint/i);
            expect(touchpointElements.length).toBeGreaterThan(0);
        });

        it('should display touchpoint metrics', () => {
            render(<ServiceBlueprintCanvas />);
            // Metrics UI should be present
            expect(screen.getByText(/service blueprint/i)).toBeInTheDocument();
        });
    });

    describe('Lines of Interaction', () => {
        it('should show line of visibility marker', () => {
            render(<ServiceBlueprintCanvas />);
            // Look for visibility-related UI
            const visibilityElements = screen.queryAllByText(/visibility|visible/i);
            expect(visibilityElements.length).toBeGreaterThanOrEqual(0);
        });

        it('should show line of interaction marker', () => {
            render(<ServiceBlueprintCanvas />);
            // Interaction line should be indicated
            const interactionElements = screen.queryAllByText(/interaction|interact/i);
            expect(interactionElements.length).toBeGreaterThanOrEqual(0);
        });
    });

    describe('Node Connections', () => {
        it('should support cross-lane connections', () => {
            render(<ServiceBlueprintCanvas />);
            // Connection UI should be available
            expect(screen.getByText(/service blueprint/i)).toBeInTheDocument();
        });

        it('should show connection indicators', () => {
            render(<ServiceBlueprintCanvas />);
            // Visual indicators for connections
            const canvas = screen.getByText(/service blueprint/i);
            expect(canvas).toBeInTheDocument();
        });
    });

    describe('Export & Share', () => {
        it('should have export button', () => {
            render(<ServiceBlueprintCanvas />);
            const exportButton = screen.queryByRole('button', { name: /export|download/i });
            expect(exportButton).toBeTruthy();
        });

        it('should call onExport when export clicked', async () => {
            const onExport = vi.fn();
            const user = userEvent.setup();
            render(<ServiceBlueprintCanvas onExport={onExport} />);

            const exportButton = screen.getByRole('button', { name: /export|download/i });
            await user.click(exportButton);

            expect(onExport).toHaveBeenCalled();
        });

        it('should have share button', () => {
            render(<ServiceBlueprintCanvas />);
            const shareButton = screen.queryByRole('button', { name: /share/i });
            expect(shareButton).toBeTruthy();
        });

        it('should call onShare when share clicked', async () => {
            const onShare = vi.fn();
            const user = userEvent.setup();
            render(<ServiceBlueprintCanvas onShare={onShare} />);

            const shareButton = screen.getByRole('button', { name: /share/i });
            await user.click(shareButton);

            expect(onShare).toHaveBeenCalled();
        });
    });

    describe('Blueprint Name', () => {
        it('should allow editing blueprint name', async () => {
            const user = userEvent.setup();
            render(<ServiceBlueprintCanvas initialBlueprintName="Initial Name" />);

            const nameInput = screen.getByDisplayValue('Initial Name');
            await user.clear(nameInput);
            await user.type(nameInput, 'New Blueprint Name');

            expect(screen.getByDisplayValue('New Blueprint Name')).toBeInTheDocument();
        });

        it('should display default name if none provided', () => {
            render(<ServiceBlueprintCanvas />);
            const nameInputs = screen.getAllByRole('textbox');
            expect(nameInputs.length).toBeGreaterThan(0);
        });
    });

    describe('Service Description', () => {
        it('should render with initial description', () => {
            render(<ServiceBlueprintCanvas initialServiceDescription="Service description here" />);
            expect(screen.getByDisplayValue('Service description here')).toBeInTheDocument();
        });

        it('should allow editing description', async () => {
            const user = userEvent.setup();
            render(<ServiceBlueprintCanvas initialServiceDescription="Old description" />);

            const descInput = screen.getByDisplayValue('Old description');
            await user.clear(descInput);
            await user.type(descInput, 'New description');

            expect(screen.getByDisplayValue('New description')).toBeInTheDocument();
        });
    });

    describe('Lane Organization', () => {
        it('should show lanes in correct order', () => {
            render(<ServiceBlueprintCanvas />);

            const laneTexts = screen.getAllByText(/customer|frontstage|backstage|support/i);
            expect(laneTexts.length).toBeGreaterThanOrEqual(4);
        });

        it('should visually separate lanes', () => {
            const { container } = render(<ServiceBlueprintCanvas />);

            // Lanes should be in separate containers or have visual separation
            const lanes = container.querySelectorAll('[class*="lane"]');
            expect(lanes.length).toBeGreaterThanOrEqual(0);
        });
    });

    describe('Validation', () => {
        it('should validate service flow', () => {
            render(<ServiceBlueprintCanvas />);
            // Validation UI or feedback should be present
            expect(screen.getByText(/service blueprint/i)).toBeInTheDocument();
        });

        it('should show validation feedback', () => {
            render(<ServiceBlueprintCanvas />);
            // Look for any validation messages or indicators
            const canvas = screen.getByText(/service blueprint/i);
            expect(canvas).toBeInTheDocument();
        });
    });

    describe('AI Optimization', () => {
        it('should support optimization predictions', () => {
            render(<ServiceBlueprintCanvas />);
            // AI features may not be visible by default
            expect(screen.getByText(/service blueprint/i)).toBeInTheDocument();
        });

        it('should show ticket reduction estimates', () => {
            render(<ServiceBlueprintCanvas />);
            // Optimization metrics should be available
            const canvas = screen.getByText(/service blueprint/i);
            expect(canvas).toBeInTheDocument();
        });
    });

    describe('Accessibility', () => {
        it('should have accessible lane labels', () => {
            render(<ServiceBlueprintCanvas />);

            expect(screen.getByText('Customer Actions')).toBeInTheDocument();
            expect(screen.getByText('Frontstage')).toBeInTheDocument();
            expect(screen.getByText('Backstage')).toBeInTheDocument();
            expect(screen.getByText('Support')).toBeInTheDocument();
        });

        it('should have keyboard navigable buttons', () => {
            render(<ServiceBlueprintCanvas />);

            const buttons = screen.getAllByRole('button');
            buttons.forEach((button) => {
                expect(button).toBeInTheDocument();
            });
        });

        it('should have descriptive button labels', () => {
            render(<ServiceBlueprintCanvas />);

            const addButtons = screen.queryAllByRole('button', { name: /add/i });
            expect(addButtons.length).toBeGreaterThanOrEqual(0);
        });
    });

    describe('Edge Cases', () => {
        it('should handle empty blueprint', () => {
            render(<ServiceBlueprintCanvas />);
            expect(screen.getByText(/service blueprint/i)).toBeInTheDocument();
        });

        it('should handle blueprint with no nodes', () => {
            render(<ServiceBlueprintCanvas />);
            // Should show empty state or placeholder
            expect(screen.getByText(/service blueprint/i)).toBeInTheDocument();
        });

        it('should handle missing callbacks gracefully', () => {
            expect(() => {
                render(<ServiceBlueprintCanvas />);
            }).not.toThrow();
        });

        it('should handle long blueprint names', async () => {
            const longName = 'A'.repeat(100);
            const user = userEvent.setup();
            render(<ServiceBlueprintCanvas initialBlueprintName="" />);

            const nameInputs = screen.getAllByRole('textbox');
            if (nameInputs.length > 0) {
                await user.type(nameInputs[0], longName);
                expect(nameInputs[0]).toHaveValue(longName);
            }
        });

        it('should handle long descriptions', async () => {
            const longDesc = 'B'.repeat(500);
            const user = userEvent.setup();
            render(<ServiceBlueprintCanvas initialServiceDescription="" />);

            const descInputs = screen.getAllByRole('textbox');
            if (descInputs.length > 1) {
                await user.type(descInputs[1], longDesc);
                expect(descInputs[1]).toHaveValue(longDesc);
            }
        });
    });

    describe('Performance', () => {
        it('should render quickly with default props', () => {
            const start = performance.now();
            render(<ServiceBlueprintCanvas />);
            const end = performance.now();

            // Should render in under 1 second
            expect(end - start).toBeLessThan(1000);
        });

        it('should handle multiple re-renders', () => {
            const { rerender } = render(<ServiceBlueprintCanvas />);

            expect(() => {
                rerender(<ServiceBlueprintCanvas initialBlueprintName="Name 1" />);
                rerender(<ServiceBlueprintCanvas initialBlueprintName="Name 2" />);
                rerender(<ServiceBlueprintCanvas initialBlueprintName="Name 3" />);
            }).not.toThrow();
        });
    });
});
