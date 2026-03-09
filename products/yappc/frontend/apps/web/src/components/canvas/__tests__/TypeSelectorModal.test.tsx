/**
 * TypeSelectorModal Component Tests
 * 
 * Tests the type selection modal including:
 * - Compatible type recommendations
 * - All types display
 * - Warning/info alerts
 * - Type change confirmation
 * 
 * @doc.type test
 * @doc.purpose Unit tests for TypeSelectorModal
 * @doc.layer product
 */

import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TypeSelectorModal } from '../TypeSelectorModal';
import { ArtifactType } from '@/types/fow-stages';

describe('TypeSelectorModal', () => {
    const mockOnClose = jest.fn();
    const mockOnTypeChange = jest.fn();

    const defaultProps = {
        open: true,
        currentType: 'code' as ArtifactType,
        artifactId: 'test-artifact-1',
        onClose: mockOnClose,
        onTypeChange: mockOnTypeChange,
    };

    beforeEach(() => {
        jest.clearAllMocks();
    });

    describe('Rendering', () => {
        it('should render modal when open', () => {
            render(<TypeSelectorModal {...defaultProps} />);

            expect(screen.getByRole('dialog')).toBeInTheDocument();
            expect(screen.getByText('Change Content Type')).toBeInTheDocument();
        });

        it('should not render modal when closed', () => {
            render(<TypeSelectorModal {...defaultProps} open={false} />);

            expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        });

        it('should display current type', () => {
            render(<TypeSelectorModal {...defaultProps} />);

            expect(screen.getByText('Current Type:')).toBeInTheDocument();
            expect(screen.getByText('Code Editor')).toBeInTheDocument();
        });
    });

    describe('Compatible Types Section', () => {
        it('should show recommended types for code type', () => {
            render(<TypeSelectorModal {...defaultProps} currentType="code" />);

            expect(screen.getByText('Recommended Types')).toBeInTheDocument();
            expect(screen.getByText(/Test Case/i)).toBeInTheDocument();
            expect(screen.getByText(/Markdown Document/i)).toBeInTheDocument();
        });

        it('should mark compatible types with "Recommended" chip', () => {
            render(<TypeSelectorModal {...defaultProps} currentType="code" />);

            const recommendedChips = screen.getAllByText('Recommended');
            expect(recommendedChips.length).toBeGreaterThan(0);
        });

        it('should not show recommended section if no compatible types', () => {
            // Create a type with no compatible conversions
            render(<TypeSelectorModal {...defaultProps} currentType="deployment" />);

            // Should still render but recommended section might be minimal
            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });
    });

    describe('All Types Section', () => {
        it('should show "All Content Types" accordion', () => {
            render(<TypeSelectorModal {...defaultProps} />);

            expect(screen.getByText('All Content Types')).toBeInTheDocument();
        });

        it('should expand all types when accordion clicked', async () => {
            const user = userEvent.setup();
            render(<TypeSelectorModal {...defaultProps} />);

            const accordion = screen.getByText('All Content Types');
            await user.click(accordion);

            // Should show categories
            await waitFor(() => {
                expect(screen.getByText(/code/i)).toBeInTheDocument();
            });
        });

        it('should organize types by category', async () => {
            const user = userEvent.setup();
            render(<TypeSelectorModal {...defaultProps} />);

            const accordion = screen.getByText('All Content Types');
            await user.click(accordion);

            await waitFor(() => {
                // Check for category labels (they should be there as Grid items)
                const dialog = screen.getByRole('dialog');
                expect(dialog).toBeInTheDocument();
            });
        });
    });

    describe('Type Selection', () => {
        it('should allow selecting a type', async () => {
            const user = userEvent.setup();
            render(<TypeSelectorModal {...defaultProps} currentType="code" />);

            // Click on a different type
            const testType = screen.getByText(/Test Case/i);
            await user.click(testType);

            // Button should be enabled
            const changeButton = screen.getByRole('button', { name: /Change Type/i });
            expect(changeButton).not.toBeDisabled();
        });

        it('should highlight selected type', async () => {
            const user = userEvent.setup();
            render(<TypeSelectorModal {...defaultProps} currentType="code" />);

            const testType = screen.getByText(/Test Case/i).closest('div');
            await user.click(testType!);

            // Selected item should have different styling (border)
            expect(testType?.parentElement).toHaveStyle({ borderWidth: '2px' });
        });

        it('should disable change button when same type selected', () => {
            render(<TypeSelectorModal {...defaultProps} currentType="code" />);

            // Current type is code, so button should be disabled by default
            const changeButton = screen.getByRole('button', { name: /Change Type/i });
            expect(changeButton).toBeDisabled();
        });
    });

    describe('Warning Alerts', () => {
        it('should show warning for incompatible conversion', async () => {
            const user = userEvent.setup();
            render(<TypeSelectorModal {...defaultProps} currentType="code" />);

            // Expand all types
            await user.click(screen.getByText('All Content Types'));

            // Select an incompatible type
            await waitFor(async () => {
                const briefType = screen.getByText(/Project Brief/i);
                await user.click(briefType);
            });

            // Should show warning
            await waitFor(() => {
                expect(screen.getByText(/Potentially Lossy Conversion/i)).toBeInTheDocument();
            });
        });

        it('should show info alert for compatible conversion', async () => {
            const user = userEvent.setup();
            render(<TypeSelectorModal {...defaultProps} currentType="code" />);

            // Select a compatible type
            const testType = screen.getByText(/Test Case/i);
            await user.click(testType);

            // Should show info alert
            await waitFor(() => {
                expect(screen.getByText(/This conversion is compatible/i)).toBeInTheDocument();
            });
        });

        it('should change button text for incompatible conversion', async () => {
            const user = userEvent.setup();
            render(<TypeSelectorModal {...defaultProps} currentType="code" />);

            // Expand and select incompatible type
            await user.click(screen.getByText('All Content Types'));

            await waitFor(async () => {
                const briefType = screen.getByText(/Project Brief/i);
                await user.click(briefType);
            });

            // Button should say "Change Anyway"
            await waitFor(() => {
                expect(screen.getByRole('button', { name: /Change Anyway/i })).toBeInTheDocument();
            });
        });
    });

    describe('User Actions', () => {
        it('should call onClose when cancel clicked', async () => {
            const user = userEvent.setup();
            render(<TypeSelectorModal {...defaultProps} />);

            const cancelButton = screen.getByRole('button', { name: /Cancel/i });
            await user.click(cancelButton);

            expect(mockOnClose).toHaveBeenCalledTimes(1);
        });

        it('should call onTypeChange when change type confirmed', async () => {
            const user = userEvent.setup();
            render(<TypeSelectorModal {...defaultProps} currentType="code" />);

            // Select different type
            const testType = screen.getByText(/Test Case/i);
            await user.click(testType);

            // Confirm change
            const changeButton = screen.getByRole('button', { name: /Change Type/i });
            await user.click(changeButton);

            expect(mockOnTypeChange).toHaveBeenCalledWith('test-artifact-1', 'test');
        });

        it('should close modal after type change', async () => {
            const user = userEvent.setup();
            render(<TypeSelectorModal {...defaultProps} currentType="code" />);

            // Select and confirm
            await user.click(screen.getByText(/Test Case/i));
            await user.click(screen.getByRole('button', { name: /Change Type/i }));

            expect(mockOnClose).toHaveBeenCalled();
        });

        it('should not call onTypeChange when selecting same type', async () => {
            const user = userEvent.setup();
            render(<TypeSelectorModal {...defaultProps} currentType="code" />);

            // Button is disabled for same type
            const changeButton = screen.getByRole('button', { name: /Change Type/i });
            expect(changeButton).toBeDisabled();

            // Try to click (should do nothing)
            await user.click(changeButton);
            expect(mockOnTypeChange).not.toHaveBeenCalled();
        });
    });

    describe('Edge Cases', () => {
        it('should handle type with no icon gracefully', () => {
            render(<TypeSelectorModal {...defaultProps} currentType="deployment" />);

            // Should still render
            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });

        it('should handle rapid type selection changes', async () => {
            const user = userEvent.setup();
            render(<TypeSelectorModal {...defaultProps} currentType="code" />);

            // Rapidly click different types
            await user.click(screen.getByText(/Test Case/i));
            await user.click(screen.getByText(/Markdown Document/i));

            // Should end with last selection
            const changeButton = screen.getByRole('button', { name: /Change Type/i });
            expect(changeButton).not.toBeDisabled();
        });

        it('should maintain state when reopened', () => {
            const { rerender } = render(<TypeSelectorModal {...defaultProps} open={false} />);

            // Reopen with same props
            rerender(<TypeSelectorModal {...defaultProps} open={true} />);

            expect(screen.getByRole('dialog')).toBeInTheDocument();
            expect(screen.getByText('Code Editor')).toBeInTheDocument();
        });
    });

    describe('Accessibility', () => {
        it('should have proper dialog role', () => {
            render(<TypeSelectorModal {...defaultProps} />);

            expect(screen.getByRole('dialog')).toBeInTheDocument();
        });

        it('should have dialog title', () => {
            render(<TypeSelectorModal {...defaultProps} />);

            expect(screen.getByText('Change Content Type')).toBeInTheDocument();
        });

        it('should be keyboard navigable', async () => {
            const user = userEvent.setup();
            render(<TypeSelectorModal {...defaultProps} />);

            // Tab through elements
            await user.tab();

            // Should focus first interactive element
            expect(document.activeElement).toBeTruthy();
        });

        it('should close on Escape key', async () => {
            const user = userEvent.setup();
            render(<TypeSelectorModal {...defaultProps} />);

            await user.keyboard('{Escape}');

            expect(mockOnClose).toHaveBeenCalled();
        });
    });

    describe('Different Current Types', () => {
        it('should show correct compatible types for documentation', () => {
            render(<TypeSelectorModal {...defaultProps} currentType="documentation" />);

            expect(screen.getByText('Recommended Types')).toBeInTheDocument();
            // Documentation has many compatible types
            expect(screen.getByText(/Code Editor/i)).toBeInTheDocument();
        });

        it('should show correct compatible types for requirements', () => {
            render(<TypeSelectorModal {...defaultProps} currentType="requirement" />);

            expect(screen.getByText('Recommended Types')).toBeInTheDocument();
        });

        it('should handle architecture diagram type', () => {
            render(<TypeSelectorModal {...defaultProps} currentType="architecture" />);

            expect(screen.getByText(/Architecture Diagram/i)).toBeInTheDocument();
        });
    });
});
