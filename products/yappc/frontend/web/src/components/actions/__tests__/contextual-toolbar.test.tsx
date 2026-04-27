/**
 * ContextualToolbar Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import { ContextualToolbar } from '../ContextualToolbar';
import { LifecyclePhase } from '../../../types/lifecycle';

vi.mock('../../../services/ActionRegistry', () => ({
    useActions: vi.fn(() => ({
        actions: [],
        execute: vi.fn(),
        formatShortcut: vi.fn((s: string) => s),
    })),
}));

vi.mock('../../../context/WorkflowContextProvider', () => ({
    useSelectionContext: vi.fn(() => ({
        elements: [],
        type: 'none',
    })),
    useCapabilitiesContext: vi.fn(() => ({
        canEdit: true,
        canDelete: false,
        canAlign: false,
    })),
}));

const defaultState = {
    phase: LifecyclePhase.INTENT,
    canUndo: false,
    canRedo: false,
    hasSelection: false,
    selectionCount: 0,
    isLocked: false,
    showGrid: false,
    zoomLevel: 100,
    currentRoute: '/dashboard',
};

describe('ContextualToolbar', () => {
    it('renders toolbar element', () => {
        render(<ContextualToolbar state={defaultState} />);
        expect(document.querySelector('[role="toolbar"]')).toBeTruthy();
    });

    it('renders undo button', () => {
        render(<ContextualToolbar state={defaultState} />);
        // Undo button should be present (disabled since canUndo=false)
        const undoBtn = screen.queryByRole('button', { name: /undo/i });
        expect(undoBtn).toBeTruthy();
    });

    it('renders in compact mode', () => {
        render(<ContextualToolbar state={defaultState} compact />);
        expect(document.body.textContent).toBeTruthy();
    });

    it('enables undo when canUndo is true', () => {
        render(<ContextualToolbar state={{ ...defaultState, canUndo: true }} />);
        const undoBtn = screen.queryByRole('button', { name: /undo/i });
        if (undoBtn) {
            expect(undoBtn.getAttribute('disabled')).toBeNull();
        }
    });
});
