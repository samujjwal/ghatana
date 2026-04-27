/**
 * CommandPalette Unit Tests
 *
 * Tests for the global command palette with fuzzy search.
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CommandPalette } from '../CommandPalette';
import type { ActionState } from '../../../services/ActionRegistry';

// ============================================================================
// Mocks
// ============================================================================

const mockExecute = vi.fn();
const mockFormatShortcut = (s: string) => s.replace('mod', '⌘');

const makeAction = (id: string, label: string, overrides = {}) => ({
    id,
    label,
    description: `Description for ${label}`,
    category: 'edit' as const,
    shortcut: undefined,
    isDangerous: false,
    handler: vi.fn(),
    context: {},
    ...overrides,
});

vi.mock('../../../services/ActionRegistry', async (importOriginal) => {
    const actual = await importOriginal<typeof import('../../../services/ActionRegistry')>();
    return {
        ...actual,
        useActions: vi.fn(() => ({
            grouped: [
                {
                    category: 'edit',
                    label: 'Edit',
                    actions: [
                        makeAction('edit.undo', 'Undo', { shortcut: 'mod+z' }),
                        makeAction('edit.copy', 'Copy'),
                    ],
                },
                {
                    category: 'ai',
                    label: 'AI',
                    actions: [
                        makeAction('ai.open', 'Ask AI', { description: 'Open AI assistant' }),
                    ],
                },
            ],
            execute: mockExecute,
            formatShortcut: mockFormatShortcut,
        })),
    };
});

// ============================================================================
// Fixtures
// ============================================================================

const defaultState: ActionState = {
    currentPhase: null,
    currentRoute: '/',
    projectId: null,
    hasSelection: false,
    selectionType: null,
    selectionCount: 0,
    isCanvasActive: false,
    canUndo: false,
    canRedo: false,
    isDirty: false,
};

function renderPalette(overrides: { open?: boolean; onOpenChange?: (v: boolean) => void } = {}) {
    return render(
        <CommandPalette
            state={defaultState}
            open={overrides.open ?? true}
            onOpenChange={overrides.onOpenChange ?? vi.fn()}
        />
    );
}

// ============================================================================
// Tests
// ============================================================================

describe('CommandPalette', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('visibility', () => {
        it('renders the search input when open', () => {
            renderPalette({ open: true });
            expect(screen.getByPlaceholderText('Search actions...')).toBeTruthy();
        });

        it('does not render content when closed', () => {
            renderPalette({ open: false });
            expect(screen.queryByPlaceholderText('Search actions...')).toBeNull();
        });
    });

    describe('action listing', () => {
        it('renders action labels', () => {
            renderPalette({ open: true });
            expect(screen.getByText('Undo')).toBeTruthy();
            expect(screen.getByText('Copy')).toBeTruthy();
            expect(screen.getByText('Ask AI')).toBeTruthy();
        });

        it('renders action descriptions', () => {
            renderPalette({ open: true });
            expect(screen.getByText('Open AI assistant')).toBeTruthy();
        });

        it('renders shortcut for actions that have one', () => {
            renderPalette({ open: true });
            // mod+z is formatted via mockFormatShortcut as ⌘+z
            expect(screen.getByText('⌘+z')).toBeTruthy();
        });

        it('renders navigation hint footer when no query', () => {
            renderPalette({ open: true });
            expect(screen.getByText('↑↓ Navigate')).toBeTruthy();
            expect(screen.getByText('↵ Execute')).toBeTruthy();
        });
    });

    describe('search/filter behavior', () => {
        it('filters actions when query matches label', () => {
            renderPalette({ open: true });
            const input = screen.getByPlaceholderText('Search actions...');
            fireEvent.change(input, { target: { value: 'Undo' } });
            // highlightMatches splits text into spans — use regex to find any element containing "Undo"
            expect(screen.getByText(/Undo/)).toBeTruthy();
        });

        it('shows empty state when no actions match', () => {
            renderPalette({ open: true });
            const input = screen.getByPlaceholderText('Search actions...');
            fireEvent.change(input, { target: { value: 'xyznotfound' } });
            expect(screen.getByText(/No actions found/i)).toBeTruthy();
        });

        it('hides footer when query is set', () => {
            renderPalette({ open: true });
            const input = screen.getByPlaceholderText('Search actions...');
            fireEvent.change(input, { target: { value: 'Undo' } });
            expect(screen.queryByText('↑↓ Navigate')).toBeNull();
        });
    });

    describe('keyboard navigation', () => {
        it('calls execute when Enter is pressed on selected action', () => {
            renderPalette({ open: true });
            const input = screen.getByPlaceholderText('Search actions...');
            fireEvent.keyDown(input, { key: 'Enter' });
            // First action (Undo) should execute
            expect(mockExecute).toHaveBeenCalledWith('edit.undo');
        });

        it('calls onOpenChange(false) after Enter execution', () => {
            const onOpenChange = vi.fn();
            renderPalette({ open: true, onOpenChange });
            const input = screen.getByPlaceholderText('Search actions...');
            fireEvent.keyDown(input, { key: 'Enter' });
            expect(onOpenChange).toHaveBeenCalledWith(false);
        });

        it('calls onOpenChange(false) on Escape key', () => {
            const onOpenChange = vi.fn();
            renderPalette({ open: true, onOpenChange });
            const input = screen.getByPlaceholderText('Search actions...');
            fireEvent.keyDown(input, { key: 'Escape' });
            expect(onOpenChange).toHaveBeenCalledWith(false);
        });
    });

    describe('action execution via click', () => {
        it('calls execute with action id when action item is clicked', () => {
            renderPalette({ open: true });
            // Click on "Copy" action
            const copyTexts = screen.getAllByText('Copy');
            fireEvent.click(copyTexts[0]);
            expect(mockExecute).toHaveBeenCalledWith('edit.copy');
        });

        it('closes palette after clicking an action', () => {
            const onOpenChange = vi.fn();
            renderPalette({ open: true, onOpenChange });
            const copyTexts = screen.getAllByText('Copy');
            fireEvent.click(copyTexts[0]);
            expect(onOpenChange).toHaveBeenCalledWith(false);
        });
    });

    describe('ESC chip', () => {
        it('renders the ESC chip as a keyboard hint', () => {
            renderPalette({ open: true });
            expect(screen.getByText('ESC')).toBeTruthy();
        });
    });
});
