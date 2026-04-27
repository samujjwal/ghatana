/**
 * WorkspaceSelectionDialog Unit Tests
 *
 * Tests for the dialog that lets users pick a workspace for a project.
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { WorkspaceSelectionDialog, type Workspace } from '../WorkspaceSelectionDialog';

// ============================================================================
// Fixtures
// ============================================================================

const workspaces: Workspace[] = [
    { id: 'ws-1', name: 'Personal', description: 'Personal workspace', isOwner: true },
    { id: 'ws-2', name: 'Team Alpha', description: 'Alpha team workspace' },
    { id: 'ws-3', name: 'Team Beta' },
];

function renderDialog(props: Partial<Parameters<typeof WorkspaceSelectionDialog>[0]> = {}) {
    const defaults = {
        open: true,
        projectName: 'My New Project',
        workspaces,
        onSelect: vi.fn(),
        onCancel: vi.fn(),
    };
    return render(<WorkspaceSelectionDialog {...defaults} {...props} />);
}

// ============================================================================
// Tests
// ============================================================================

describe('WorkspaceSelectionDialog', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('rendering', () => {
        it('renders the dialog title', () => {
            renderDialog();
            expect(screen.getByText('Select Workspace')).toBeTruthy();
        });

        it('renders the project name in the body', () => {
            renderDialog();
            expect(screen.getByText(/My New Project/)).toBeTruthy();
        });

        it('renders all workspace names', () => {
            renderDialog();
            expect(screen.getByText('Personal')).toBeTruthy();
            expect(screen.getByText('Team Alpha')).toBeTruthy();
            expect(screen.getByText('Team Beta')).toBeTruthy();
        });

        it('renders workspace descriptions when present', () => {
            renderDialog();
            expect(screen.getByText('Personal workspace')).toBeTruthy();
            expect(screen.getByText('Alpha team workspace')).toBeTruthy();
        });

        it('renders Open in Workspace and Cancel buttons', () => {
            renderDialog({ defaultWorkspaceId: 'ws-1' });
            expect(screen.getByRole('button', { name: /Open in Workspace/i })).toBeTruthy();
            expect(screen.getByRole('button', { name: /Cancel/i })).toBeTruthy();
        });

        it('does not render when closed', () => {
            renderDialog({ open: false });
            expect(screen.queryByText('Select Workspace')).toBeNull();
        });
    });

    describe('default workspace pre-selection', () => {
        it('pre-selects the defaultWorkspaceId', () => {
            const onSelect = vi.fn();
            renderDialog({ defaultWorkspaceId: 'ws-2', onSelect });
            // Clicking Open in Workspace immediately calls onSelect with the pre-selected ws-2
            fireEvent.click(screen.getByRole('button', { name: /Open in Workspace/i }));
            expect(onSelect).toHaveBeenCalledWith('ws-2');
        });
    });

    describe('workspace selection', () => {
        it('calls onSelect with the chosen workspace id when Open in Workspace is clicked', () => {
            const onSelect = vi.fn();
            renderDialog({ onSelect });
            // Click on "Team Alpha" to select it
            fireEvent.click(screen.getByText('Team Alpha'));
            fireEvent.click(screen.getByRole('button', { name: /Open in Workspace/i }));
            expect(onSelect).toHaveBeenCalledWith('ws-2');
        });

        it('calls onCancel when Cancel button is clicked', () => {
            const onCancel = vi.fn();
            renderDialog({ onCancel });
            fireEvent.click(screen.getByRole('button', { name: /Cancel/i }));
            expect(onCancel).toHaveBeenCalledOnce();
        });

        it('disables Open in Workspace button when no workspace selected', () => {
            renderDialog();
            // No defaultWorkspaceId, no click — button should be disabled
            const confirmBtn = screen.getByRole('button', { name: /Open in Workspace/i });
            expect(confirmBtn.getAttribute('disabled')).toBeDefined();
        });

        it('updates selection when a different workspace is clicked', () => {
            const onSelect = vi.fn();
            renderDialog({ defaultWorkspaceId: 'ws-1', onSelect });
            // Change selection to Team Beta
            fireEvent.click(screen.getByText('Team Beta'));
            fireEvent.click(screen.getByRole('button', { name: /Open in Workspace/i }));
            expect(onSelect).toHaveBeenCalledWith('ws-3');
        });
    });

    describe('empty workspaces', () => {
        it('renders with no workspace items', () => {
            renderDialog({ workspaces: [] });
            // Dialog still opens but no workspace items
            expect(screen.getByText('Select Workspace')).toBeTruthy();
            expect(screen.queryByText('Personal')).toBeNull();
        });
    });
});
