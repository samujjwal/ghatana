/**
 * LevelDropdown Component Tests
 * 
 * Tests for the abstraction level dropdown including:
 * - Rendering all 4 levels
 * - Zoom controls (zoom out, drill down)
 * - Keyboard navigation (Arrow keys, Enter, Escape)
 * - Accessibility (aria-labels, roles)
 * - Level selection
 * 
 * @doc.type test
 * @doc.purpose LevelDropdown unit tests
 * @doc.layer product
 */

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

import { LevelDropdown } from '../LevelDropdown';
import type { AbstractionLevel } from '@ghatana/yappc-types/abstractionLevel';

describe('LevelDropdown', () => {
    const defaultProps = {
        value: 'component' as AbstractionLevel,
        onChange: vi.fn(),
        canDrillDown: true,
        canZoomOut: true,
        onDrillDown: vi.fn(),
        onZoomOut: vi.fn(),
    };

    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('Rendering', () => {
        it('renders the trigger button with current level', () => {
            render(<LevelDropdown {...defaultProps} />);
            
            expect(screen.getByRole('button', { name: /abstraction level: component/i })).toBeInTheDocument();
            expect(screen.getByText('Component')).toBeInTheDocument();
        });

        it('renders all 4 levels when dropdown is open', async () => {
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} />);
            
            await user.click(screen.getByRole('button', { name: /abstraction level/i }));
            
            // Use getAllByRole to get all options
            const options = screen.getAllByRole('option');
            expect(options).toHaveLength(4);
            
            // Check each level option exists
            expect(screen.getByRole('option', { name: /system/i })).toBeInTheDocument();
            expect(screen.getByRole('option', { name: /component/i })).toBeInTheDocument();
            expect(screen.getByRole('option', { name: /file/i })).toBeInTheDocument();
            expect(screen.getByRole('option', { name: /code/i })).toBeInTheDocument();
        });

        it('displays descriptions for each level', async () => {
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} />);
            
            await user.click(screen.getByRole('button', { name: /abstraction level/i }));
            
            expect(screen.getByText('High-level architecture view')).toBeInTheDocument();
            expect(screen.getByText('Component relationships')).toBeInTheDocument();
            expect(screen.getByText('File-level details')).toBeInTheDocument();
            expect(screen.getByText('Implementation details')).toBeInTheDocument();
        });
    });

    describe('Zoom Controls', () => {
        it('renders zoom out button when onZoomOut is provided', () => {
            render(<LevelDropdown {...defaultProps} />);
            
            expect(screen.getByRole('button', { name: /zoom out/i })).toBeInTheDocument();
        });

        it('renders drill down button when onDrillDown is provided', () => {
            render(<LevelDropdown {...defaultProps} />);
            
            expect(screen.getByRole('button', { name: /drill down/i })).toBeInTheDocument();
        });

        it('disables zoom out when canZoomOut is false', () => {
            render(<LevelDropdown {...defaultProps} canZoomOut={false} />);
            
            expect(screen.getByRole('button', { name: /cannot zoom out/i })).toBeDisabled();
        });

        it('disables drill down when canDrillDown is false', () => {
            render(<LevelDropdown {...defaultProps} canDrillDown={false} />);
            
            expect(screen.getByRole('button', { name: /cannot drill down/i })).toBeDisabled();
        });

        it('calls onZoomOut when zoom out button is clicked', async () => {
            const onZoomOut = vi.fn();
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} onZoomOut={onZoomOut} />);
            
            await user.click(screen.getByRole('button', { name: /zoom out/i }));
            
            expect(onZoomOut).toHaveBeenCalledTimes(1);
        });

        it('calls onDrillDown when drill down button is clicked', async () => {
            const onDrillDown = vi.fn();
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} onDrillDown={onDrillDown} />);
            
            await user.click(screen.getByRole('button', { name: /drill down/i }));
            
            expect(onDrillDown).toHaveBeenCalledTimes(1);
        });

        it('shows next level name in drill down tooltip', () => {
            render(<LevelDropdown {...defaultProps} value="component" />);
            
            const drillDownBtn = screen.getByRole('button', { name: /drill down to file/i });
            expect(drillDownBtn).toHaveAttribute('title', expect.stringContaining('File'));
        });

        it('shows previous level name in zoom out tooltip', () => {
            render(<LevelDropdown {...defaultProps} value="component" />);
            
            const zoomOutBtn = screen.getByRole('button', { name: /zoom out to system/i });
            expect(zoomOutBtn).toHaveAttribute('title', expect.stringContaining('System'));
        });
    });

    describe('Accessibility', () => {
        it('has correct aria attributes on trigger', () => {
            render(<LevelDropdown {...defaultProps} />);
            
            const trigger = screen.getByRole('button', { name: /abstraction level/i });
            expect(trigger).toHaveAttribute('aria-haspopup', 'listbox');
            expect(trigger).toHaveAttribute('aria-expanded', 'false');
        });

        it('updates aria-expanded when dropdown opens', async () => {
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} />);
            
            const trigger = screen.getByRole('button', { name: /abstraction level/i });
            await user.click(trigger);
            
            expect(trigger).toHaveAttribute('aria-expanded', 'true');
        });

        it('has listbox role on dropdown menu', async () => {
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} />);
            
            await user.click(screen.getByRole('button', { name: /abstraction level/i }));
            
            expect(screen.getByRole('listbox')).toBeInTheDocument();
        });

        it('has option role on each level item', async () => {
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} />);
            
            await user.click(screen.getByRole('button', { name: /abstraction level/i }));
            
            const options = screen.getAllByRole('option');
            expect(options).toHaveLength(4);
        });

        it('marks selected option with aria-selected', async () => {
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} value="file" />);
            
            await user.click(screen.getByRole('button', { name: /abstraction level/i }));
            
            const fileOption = screen.getByRole('option', { name: /file/i });
            expect(fileOption).toHaveAttribute('aria-selected', 'true');
        });

        it('zoom out button has descriptive aria-label', () => {
            render(<LevelDropdown {...defaultProps} value="component" />);
            
            const zoomOutBtn = screen.getByRole('button', { name: /zoom out to system level/i });
            expect(zoomOutBtn).toHaveAttribute('aria-label', expect.stringContaining('Alt+Up'));
        });

        it('drill down button has descriptive aria-label', () => {
            render(<LevelDropdown {...defaultProps} value="component" />);
            
            const drillDownBtn = screen.getByRole('button', { name: /drill down to file level/i });
            expect(drillDownBtn).toHaveAttribute('aria-label', expect.stringContaining('Alt+Down'));
        });
    });

    describe('Keyboard Navigation', () => {
        it('opens dropdown on Enter key', async () => {
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} />);
            
            const trigger = screen.getByRole('button', { name: /abstraction level/i });
            trigger.focus();
            await user.keyboard('{Enter}');
            
            expect(screen.getByRole('listbox')).toBeInTheDocument();
        });

        it('opens dropdown on ArrowDown key', async () => {
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} />);
            
            const trigger = screen.getByRole('button', { name: /abstraction level/i });
            trigger.focus();
            await user.keyboard('{ArrowDown}');
            
            expect(screen.getByRole('listbox')).toBeInTheDocument();
        });

        it('closes dropdown on Escape key', async () => {
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} />);
            
            await user.click(screen.getByRole('button', { name: /abstraction level/i }));
            expect(screen.getByRole('listbox')).toBeInTheDocument();
            
            await user.keyboard('{Escape}');
            
            await waitFor(() => {
                expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
            });
        });

        it('navigates options with ArrowDown', async () => {
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} value="system" />);
            
            await user.click(screen.getByRole('button', { name: /abstraction level/i }));
            await user.keyboard('{ArrowDown}');
            
            const componentOption = screen.getByRole('option', { name: /component/i });
            expect(document.activeElement).toBe(componentOption);
        });

        it('navigates options with ArrowUp', async () => {
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} value="component" />);
            
            await user.click(screen.getByRole('button', { name: /abstraction level/i }));
            await user.keyboard('{ArrowUp}');
            
            const systemOption = screen.getByRole('option', { name: /system/i });
            expect(document.activeElement).toBe(systemOption);
        });

        it('selects option on Enter key', async () => {
            const onChange = vi.fn();
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} onChange={onChange} value="system" />);
            
            await user.click(screen.getByRole('button', { name: /abstraction level/i }));
            await user.keyboard('{ArrowDown}'); // Move to Component
            await user.keyboard('{Enter}');
            
            expect(onChange).toHaveBeenCalledWith('component');
        });
    });

    describe('Level Selection', () => {
        it('calls onChange when level is clicked', async () => {
            const onChange = vi.fn();
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} onChange={onChange} />);
            
            await user.click(screen.getByRole('button', { name: /abstraction level/i }));
            await user.click(screen.getByRole('option', { name: /file/i }));
            
            expect(onChange).toHaveBeenCalledWith('file');
        });

        it('closes dropdown after selection', async () => {
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} />);
            
            await user.click(screen.getByRole('button', { name: /abstraction level/i }));
            await user.click(screen.getByRole('option', { name: /file/i }));
            
            await waitFor(() => {
                expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
            });
        });

        it('returns focus to trigger after selection', async () => {
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} />);
            
            const trigger = screen.getByRole('button', { name: /abstraction level/i });
            await user.click(trigger);
            await user.click(screen.getByRole('option', { name: /file/i }));
            
            await waitFor(() => {
                expect(document.activeElement).toBe(trigger);
            });
        });
    });

    describe('Disabled State', () => {
        it('does not open when disabled', async () => {
            const user = userEvent.setup();
            render(<LevelDropdown {...defaultProps} disabled />);
            
            await user.click(screen.getByRole('button', { name: /abstraction level/i }));
            
            expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
        });

        it('has disabled attribute on trigger', () => {
            render(<LevelDropdown {...defaultProps} disabled />);
            
            expect(screen.getByRole('button', { name: /abstraction level/i })).toBeDisabled();
        });

        it('disables zoom controls when disabled', () => {
            render(<LevelDropdown {...defaultProps} disabled />);
            
            expect(screen.getByRole('button', { name: /zoom out/i })).toBeDisabled();
            expect(screen.getByRole('button', { name: /drill down/i })).toBeDisabled();
        });
    });

    describe('Edge Cases', () => {
        it('disables zoom out at System level', () => {
            render(<LevelDropdown {...defaultProps} value="system" canZoomOut={false} />);
            
            expect(screen.getByRole('button', { name: /cannot zoom out/i })).toBeDisabled();
        });

        it('disables drill down at Code level', () => {
            render(<LevelDropdown {...defaultProps} value="code" canDrillDown={false} />);
            
            expect(screen.getByRole('button', { name: /cannot drill down/i })).toBeDisabled();
        });
    });
});
