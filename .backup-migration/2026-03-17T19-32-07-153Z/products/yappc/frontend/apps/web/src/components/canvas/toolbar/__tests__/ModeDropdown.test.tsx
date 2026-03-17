/**
 * ModeDropdown Component Tests
 * 
 * Tests for the canvas mode dropdown including:
 * - Rendering all 7 modes
 * - Keyboard navigation (Arrow keys, Enter, Escape)
 * - Accessibility (aria-labels, roles)
 * - Mode selection
 * 
 * @doc.type test
 * @doc.purpose ModeDropdown unit tests
 * @doc.layer product
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

import { ModeDropdown } from '../ModeDropdown';
import type { CanvasMode } from '@ghatana/yappc-types/canvasMode';

describe('ModeDropdown', () => {
    const defaultProps = {
        value: 'diagram' as CanvasMode,
        onChange: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('Rendering', () => {
        it('renders the trigger button with current mode', () => {
            render(<ModeDropdown {...defaultProps} />);
            
            expect(screen.getByRole('button', { name: /canvas mode: diagram/i })).toBeInTheDocument();
            expect(screen.getByText('Diagram')).toBeInTheDocument();
        });

        it('renders all 7 modes when dropdown is open', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} />);
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            
            // Use getAllByRole to get all options
            const options = screen.getAllByRole('option');
            expect(options).toHaveLength(7);
            
            // Check each mode option exists
            expect(screen.getByRole('option', { name: /brainstorm/i })).toBeInTheDocument();
            expect(screen.getByRole('option', { name: /diagram/i })).toBeInTheDocument();
            expect(screen.getByRole('option', { name: /design/i })).toBeInTheDocument();
            expect(screen.getByRole('option', { name: /code/i })).toBeInTheDocument();
            expect(screen.getByRole('option', { name: /test/i })).toBeInTheDocument();
            expect(screen.getByRole('option', { name: /deploy/i })).toBeInTheDocument();
            expect(screen.getByRole('option', { name: /observe/i })).toBeInTheDocument();
        });

        it('displays keyboard shortcuts for each mode', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} />);
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            
            expect(screen.getByText('1')).toBeInTheDocument();
            expect(screen.getByText('2')).toBeInTheDocument();
            expect(screen.getByText('3')).toBeInTheDocument();
            expect(screen.getByText('4')).toBeInTheDocument();
            expect(screen.getByText('5')).toBeInTheDocument();
            expect(screen.getByText('6')).toBeInTheDocument();
            expect(screen.getByText('7')).toBeInTheDocument();
        });

        it('displays descriptions for each mode', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} />);
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            
            expect(screen.getByText('Sketch ideas freely')).toBeInTheDocument();
            expect(screen.getByText('Structure components')).toBeInTheDocument();
            expect(screen.getByText('Define UI/UX')).toBeInTheDocument();
            expect(screen.getByText('Write implementation')).toBeInTheDocument();
        });
    });

    describe('Accessibility', () => {
        it('has correct aria attributes on trigger', () => {
            render(<ModeDropdown {...defaultProps} />);
            
            const trigger = screen.getByRole('button', { name: /canvas mode/i });
            expect(trigger).toHaveAttribute('aria-haspopup', 'listbox');
            expect(trigger).toHaveAttribute('aria-expanded', 'false');
        });

        it('updates aria-expanded when dropdown opens', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} />);
            
            const trigger = screen.getByRole('button', { name: /canvas mode/i });
            await user.click(trigger);
            
            expect(trigger).toHaveAttribute('aria-expanded', 'true');
        });

        it('has listbox role on dropdown menu', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} />);
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            
            expect(screen.getByRole('listbox')).toBeInTheDocument();
        });

        it('has option role on each mode item', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} />);
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            
            const options = screen.getAllByRole('option');
            expect(options).toHaveLength(7);
        });

        it('marks selected option with aria-selected', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} value="design" />);
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            
            const designOption = screen.getByRole('option', { name: /design/i });
            expect(designOption).toHaveAttribute('aria-selected', 'true');
        });
    });

    describe('Keyboard Navigation', () => {
        it('opens dropdown on Enter key', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} />);
            
            const trigger = screen.getByRole('button', { name: /canvas mode/i });
            trigger.focus();
            await user.keyboard('{Enter}');
            
            expect(screen.getByRole('listbox')).toBeInTheDocument();
        });

        it('opens dropdown on ArrowDown key', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} />);
            
            const trigger = screen.getByRole('button', { name: /canvas mode/i });
            trigger.focus();
            await user.keyboard('{ArrowDown}');
            
            expect(screen.getByRole('listbox')).toBeInTheDocument();
        });

        it('closes dropdown on Escape key', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} />);
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            expect(screen.getByRole('listbox')).toBeInTheDocument();
            
            await user.keyboard('{Escape}');
            
            await waitFor(() => {
                expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
            });
        });

        it('navigates options with ArrowDown', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} value="brainstorm" />);
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            
            // First option should be focused initially
            await user.keyboard('{ArrowDown}');
            
            // Focus should move to next option
            const diagramOption = screen.getByRole('option', { name: /diagram/i });
            expect(document.activeElement).toBe(diagramOption);
        });

        it('navigates options with ArrowUp', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} value="diagram" />);
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            
            await user.keyboard('{ArrowUp}');
            
            const brainstormOption = screen.getByRole('option', { name: /brainstorm/i });
            expect(document.activeElement).toBe(brainstormOption);
        });

        it('wraps to first option when pressing ArrowDown on last', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} value="observe" />);
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            
            await user.keyboard('{ArrowDown}');
            
            const brainstormOption = screen.getByRole('option', { name: /brainstorm/i });
            expect(document.activeElement).toBe(brainstormOption);
        });

        it('selects option on Enter key', async () => {
            const onChange = vi.fn();
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} onChange={onChange} value="brainstorm" />);
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            await user.keyboard('{ArrowDown}'); // Move to Diagram
            await user.keyboard('{Enter}');
            
            expect(onChange).toHaveBeenCalledWith('diagram');
        });

        it('jumps to first option on Home key', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} value="code" />);
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            await user.keyboard('{Home}');
            
            const brainstormOption = screen.getByRole('option', { name: /brainstorm/i });
            expect(document.activeElement).toBe(brainstormOption);
        });

        it('jumps to last option on End key', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} value="brainstorm" />);
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            await user.keyboard('{End}');
            
            const observeOption = screen.getByRole('option', { name: /observe/i });
            expect(document.activeElement).toBe(observeOption);
        });
    });

    describe('Mode Selection', () => {
        it('calls onChange when mode is clicked', async () => {
            const onChange = vi.fn();
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} onChange={onChange} />);
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            await user.click(screen.getByRole('option', { name: /code/i }));
            
            expect(onChange).toHaveBeenCalledWith('code');
        });

        it('closes dropdown after selection', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} />);
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            await user.click(screen.getByRole('option', { name: /code/i }));
            
            await waitFor(() => {
                expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
            });
        });

        it('returns focus to trigger after selection', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} />);
            
            const trigger = screen.getByRole('button', { name: /canvas mode/i });
            await user.click(trigger);
            await user.click(screen.getByRole('option', { name: /code/i }));
            
            await waitFor(() => {
                expect(document.activeElement).toBe(trigger);
            });
        });
    });

    describe('Disabled State', () => {
        it('does not open when disabled', async () => {
            const user = userEvent.setup();
            render(<ModeDropdown {...defaultProps} disabled />);
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            
            expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
        });

        it('has disabled attribute on trigger', () => {
            render(<ModeDropdown {...defaultProps} disabled />);
            
            expect(screen.getByRole('button', { name: /canvas mode/i })).toBeDisabled();
        });
    });

    describe('Outside Click', () => {
        it('closes dropdown when clicking outside', async () => {
            const user = userEvent.setup();
            render(
                <div>
                    <ModeDropdown {...defaultProps} />
                    <button data-testid="outside">Outside</button>
                </div>
            );
            
            await user.click(screen.getByRole('button', { name: /canvas mode/i }));
            expect(screen.getByRole('listbox')).toBeInTheDocument();
            
            await user.click(screen.getByTestId('outside'));
            
            await waitFor(() => {
                expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
            });
        });
    });
});
