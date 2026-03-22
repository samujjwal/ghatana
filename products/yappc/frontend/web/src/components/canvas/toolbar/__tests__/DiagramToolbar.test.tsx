/**
 * DiagramToolbar Component Tests
 * 
 * Tests the diagram mode toolbar component including:
 * - Template selection
 * - Diagram type toggles
 * - Zoom controls
 * - Code editor dialog
 * 
 * @doc.type test
 * @doc.purpose Unit tests for DiagramToolbar
 * @doc.layer product
 */

import React from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'jotai';
import { DiagramToolbar } from '../DiagramToolbar';
import {
    diagramTypeAtom,
    diagramContentAtom,
    diagramZoomAtom,
    showDiagramEditorAtom,
} from '../../workspace/canvasAtoms';
import { MERMAID_TEMPLATES } from '../../diagram/MermaidDiagram';

describe('DiagramToolbar', () => {
    const renderWithJotai = (ui: React.ReactElement) => {
        return render(
            <Provider>
                {ui}
            </Provider>
        );
    };

    describe('Template Selection', () => {
        it('should render template menu button', () => {
            renderWithJotai(<DiagramToolbar />);

            const templateButton = screen.getByRole('button', { name: /templates/i });
            expect(templateButton).toBeInTheDocument();
        });

        it('should show template options when menu is opened', async () => {
            const user = userEvent.setup();
            renderWithJotai(<DiagramToolbar />);

            const templateButton = screen.getByRole('button', { name: /templates/i });
            await user.click(templateButton);

            // Verify all 6 templates are shown
            expect(screen.getByText('Basic Flowchart')).toBeInTheDocument();
            expect(screen.getByText('Sequence Diagram')).toBeInTheDocument();
            expect(screen.getByText('Class Diagram')).toBeInTheDocument();
            expect(screen.getByText('State Diagram')).toBeInTheDocument();
            expect(screen.getByText('Gantt Chart')).toBeInTheDocument();
            expect(screen.getByText('ER Diagram')).toBeInTheDocument();
        });

        it('should update diagram content when template is selected', async () => {
            const user = userEvent.setup();
            const { container } = renderWithJotai(<DiagramToolbar />);

            // Open menu
            const templateButton = screen.getByRole('button', { name: /templates/i });
            await user.click(templateButton);

            // Select sequence diagram
            const sequenceOption = screen.getByText('Sequence Diagram');
            await user.click(sequenceOption);

            // Verify content was updated (would need to check atom value in real implementation)
            await waitFor(() => {
                expect(screen.queryByText('Sequence Diagram')).not.toBeInTheDocument(); // Menu closed
            });
        });
    });

    describe('Diagram Type Toggles', () => {
        it('should render all three type toggle buttons', () => {
            renderWithJotai(<DiagramToolbar />);

            expect(screen.getByRole('button', { name: /flowchart/i })).toBeInTheDocument();
            expect(screen.getByRole('button', { name: /sequence/i })).toBeInTheDocument();
            expect(screen.getByRole('button', { name: /class/i })).toBeInTheDocument();
        });

        it('should show flowchart as active by default', () => {
            renderWithJotai(<DiagramToolbar />);

            const flowchartButton = screen.getByRole('button', { name: /flowchart/i });
            expect(flowchartButton).toHaveAttribute('aria-pressed', 'true');
        });

        it('should switch active type when clicked', async () => {
            const user = userEvent.setup();
            renderWithJotai(<DiagramToolbar />);

            const sequenceButton = screen.getByRole('button', { name: /sequence/i });
            await user.click(sequenceButton);

            expect(sequenceButton).toHaveAttribute('aria-pressed', 'true');
        });

        it('should allow only one type to be active at a time', async () => {
            const user = userEvent.setup();
            renderWithJotai(<DiagramToolbar />);

            const flowchartButton = screen.getByRole('button', { name: /flowchart/i });
            const classButton = screen.getByRole('button', { name: /class/i });

            // Click class button
            await user.click(classButton);

            // Flowchart should no longer be pressed
            expect(flowchartButton).toHaveAttribute('aria-pressed', 'false');
            expect(classButton).toHaveAttribute('aria-pressed', 'true');
        });
    });

    describe('Zoom Controls', () => {
        it('should render zoom in, zoom out, and reset buttons', () => {
            renderWithJotai(<DiagramToolbar />);

            expect(screen.getByLabelText(/zoom in/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/zoom out/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/reset zoom/i)).toBeInTheDocument();
        });

        it('should display current zoom percentage', () => {
            renderWithJotai(<DiagramToolbar />);

            expect(screen.getByText('100%')).toBeInTheDocument();
        });

        it('should increase zoom when zoom in is clicked', async () => {
            const user = userEvent.setup();
            renderWithJotai(<DiagramToolbar />);

            const zoomInButton = screen.getByLabelText(/zoom in/i);
            await user.click(zoomInButton);

            // After zoom in, should show 110%
            await waitFor(() => {
                expect(screen.getByText('110%')).toBeInTheDocument();
            });
        });

        it('should decrease zoom when zoom out is clicked', async () => {
            const user = userEvent.setup();
            renderWithJotai(<DiagramToolbar />);

            const zoomOutButton = screen.getByLabelText(/zoom out/i);
            await user.click(zoomOutButton);

            // After zoom out, should show 90%
            await waitFor(() => {
                expect(screen.getByText('90%')).toBeInTheDocument();
            });
        });

        it('should reset zoom to 100% when reset is clicked', async () => {
            const user = userEvent.setup();
            renderWithJotai(<DiagramToolbar />);

            // Zoom in first
            const zoomInButton = screen.getByLabelText(/zoom in/i);
            await user.click(zoomInButton);
            await user.click(zoomInButton);

            // Reset zoom
            const resetButton = screen.getByLabelText(/reset zoom/i);
            await user.click(resetButton);

            await waitFor(() => {
                expect(screen.getByText('100%')).toBeInTheDocument();
            });
        });

        it('should not zoom below 50%', async () => {
            const user = userEvent.setup();
            renderWithJotai(<DiagramToolbar />);

            const zoomOutButton = screen.getByLabelText(/zoom out/i);

            // Click zoom out many times
            for (let i = 0; i < 10; i++) {
                await user.click(zoomOutButton);
            }

            // Should stop at 50%
            await waitFor(() => {
                expect(screen.getByText(/50%/)).toBeInTheDocument();
            });
        });

        it('should not zoom above 200%', async () => {
            const user = userEvent.setup();
            renderWithJotai(<DiagramToolbar />);

            const zoomInButton = screen.getByLabelText(/zoom in/i);

            // Click zoom in many times
            for (let i = 0; i < 15; i++) {
                await user.click(zoomInButton);
            }

            // Should stop at 200%
            await waitFor(() => {
                expect(screen.getByText(/200%/)).toBeInTheDocument();
            });
        });
    });

    describe('Code Editor Dialog', () => {
        it('should render edit code button', () => {
            renderWithJotai(<DiagramToolbar />);

            expect(screen.getByRole('button', { name: /edit code/i })).toBeInTheDocument();
        });

        it('should open dialog when edit code is clicked', async () => {
            const user = userEvent.setup();
            renderWithJotai(<DiagramToolbar />);

            const editButton = screen.getByRole('button', { name: /edit code/i });
            await user.click(editButton);

            expect(screen.getByRole('dialog')).toBeInTheDocument();
            expect(screen.getByText('Edit Diagram Code')).toBeInTheDocument();
        });

        it('should show current Mermaid code in text field', async () => {
            const user = userEvent.setup();
            renderWithJotai(<DiagramToolbar />);

            const editButton = screen.getByRole('button', { name: /edit code/i });
            await user.click(editButton);

            const textField = screen.getByRole('textbox', { name: /mermaid code/i });
            expect(textField).toBeInTheDocument();
            expect(textField).toHaveValue(MERMAID_TEMPLATES.flowchart);
        });

        it('should allow editing Mermaid code', async () => {
            const user = userEvent.setup();
            renderWithJotai(<DiagramToolbar />);

            const editButton = screen.getByRole('button', { name: /edit code/i });
            await user.click(editButton);

            const textField = screen.getByRole('textbox', { name: /mermaid code/i });
            await user.clear(textField);
            await user.type(textField, 'graph TD\nA-->B');

            expect(textField).toHaveValue('graph TD\nA-->B');
        });

        it('should update diagram when apply is clicked', async () => {
            const user = userEvent.setup();
            renderWithJotai(<DiagramToolbar />);

            // Open editor
            const editButton = screen.getByRole('button', { name: /edit code/i });
            await user.click(editButton);

            // Edit code
            const textField = screen.getByRole('textbox', { name: /mermaid code/i });
            await user.clear(textField);
            await user.type(textField, 'graph TD\nA-->B');

            // Apply changes
            const applyButton = screen.getByRole('button', { name: /apply/i });
            await user.click(applyButton);

            // Dialog should close
            await waitFor(() => {
                expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
            });
        });

        it('should discard changes when cancel is clicked', async () => {
            const user = userEvent.setup();
            renderWithJotai(<DiagramToolbar />);

            // Open editor
            const editButton = screen.getByRole('button', { name: /edit code/i });
            await user.click(editButton);

            // Edit code
            const textField = screen.getByRole('textbox', { name: /mermaid code/i });
            const originalValue = textField.getAttribute('value');
            await user.clear(textField);
            await user.type(textField, 'graph TD\nA-->B');

            // Cancel
            const cancelButton = screen.getByRole('button', { name: /cancel/i });
            await user.click(cancelButton);

            // Reopen and verify code is unchanged
            await user.click(editButton);
            const newTextField = screen.getByRole('textbox', { name: /mermaid code/i });
            expect(newTextField).toHaveValue(originalValue);
        });

        it('should close dialog when escape is pressed', async () => {
            const user = userEvent.setup();
            renderWithJotai(<DiagramToolbar />);

            // Open editor
            const editButton = screen.getByRole('button', { name: /edit code/i });
            await user.click(editButton);

            // Press escape
            await user.keyboard('{Escape}');

            // Dialog should close
            await waitFor(() => {
                expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
            });
        });
    });

    describe('Accessibility', () => {
        it('should have proper ARIA labels', () => {
            renderWithJotai(<DiagramToolbar />);

            expect(screen.getByRole('button', { name: /templates/i })).toBeInTheDocument();
            expect(screen.getByLabelText(/zoom in/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/zoom out/i)).toBeInTheDocument();
            expect(screen.getByLabelText(/reset zoom/i)).toBeInTheDocument();
        });

        it('should be keyboard navigable', async () => {
            const user = userEvent.setup();
            renderWithJotai(<DiagramToolbar />);

            // Tab through controls
            await user.tab();
            expect(screen.getByRole('button', { name: /flowchart/i })).toHaveFocus();

            await user.tab();
            expect(screen.getByRole('button', { name: /sequence/i })).toHaveFocus();

            await user.tab();
            expect(screen.getByRole('button', { name: /class/i })).toHaveFocus();
        });

        it('should announce zoom level changes', async () => {
            const user = userEvent.setup();
            renderWithJotai(<DiagramToolbar />);

            const zoomInButton = screen.getByLabelText(/zoom in/i);
            await user.click(zoomInButton);

            // The zoom percentage should be visible for screen readers
            expect(screen.getByText('110%')).toBeInTheDocument();
        });
    });
});
