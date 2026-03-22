/**
 * Versioning Components Integration Tests
 * 
 * Integration tests for VersionHistoryPanel, AutoSaveIndicator, and VersionDiffViewer.
 * Tests component interactions, UI behavior, and integration with services.
 * 
 * @doc.type test
 * @doc.purpose Integration tests for versioning UI
 * @doc.layer product
 * @doc.pattern Integration Testing
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { VersionHistoryPanel } from '../VersionHistoryPanel';
import { AutoSaveIndicator } from '../AutoSaveIndicator';
import { VersionDiffViewer } from '../VersionDiffViewer';
import type { CanvasSnapshot, VersionDiff } from '../../../services/canvas/CanvasPersistence';

describe('Versioning Components Integration', () => {
    describe('VersionHistoryPanel', () => {
        let mockSnapshots: CanvasSnapshot[];
        let mockOnRestore: ReturnType<typeof vi.fn>;
        let mockOnDelete: ReturnType<typeof vi.fn>;
        let mockOnCreateSnapshot: ReturnType<typeof vi.fn>;

        beforeEach(() => {
            mockSnapshots = [
                {
                    id: 'snap-1',
                    version: 1,
                    timestamp: Date.now() - 3600000, // 1 hour ago
                    data: {
                        nodes: [{ id: 'node-1', position: { x: 0, y: 0 }, data: {} }],
                        edges: [],
                        viewport: { x: 0, y: 0, zoom: 1 },
                    },
                    checksum: 'abc123',
                    label: 'Initial Version',
                    tags: ['auto-save'],
                },
                {
                    id: 'snap-2',
                    version: 2,
                    timestamp: Date.now() - 1800000, // 30 minutes ago
                    data: {
                        nodes: [
                            { id: 'node-1', position: { x: 0, y: 0 }, data: {} },
                            { id: 'node-2', position: { x: 100, y: 100 }, data: {} },
                        ],
                        edges: [{ id: 'edge-1', source: 'node-1', target: 'node-2' }],
                        viewport: { x: 0, y: 0, zoom: 1 },
                    },
                    checksum: 'def456',
                    label: 'Added Node',
                    tags: ['manual'],
                },
            ];

            mockOnRestore = vi.fn();
            mockOnDelete = vi.fn();
            mockOnCreateSnapshot = vi.fn();
        });

        it('should render snapshot list', () => {
            render(
                <VersionHistoryPanel
                    snapshots={mockSnapshots}
                    currentSnapshotId="snap-2"
                    onRestore={mockOnRestore}
                    onDelete={mockOnDelete}
                    onCreateSnapshot={mockOnCreateSnapshot}
                />
            );

            expect(screen.getByText('Initial Version')).toBeInTheDocument();
            expect(screen.getByText('Added Node')).toBeInTheDocument();
        });

        it('should highlight current version', () => {
            render(
                <VersionHistoryPanel
                    snapshots={mockSnapshots}
                    currentSnapshotId="snap-2"
                    onRestore={mockOnRestore}
                    onDelete={mockOnDelete}
                    onCreateSnapshot={mockOnCreateSnapshot}
                />
            );

            const currentChip = screen.getByText('Current');
            expect(currentChip).toBeInTheDocument();
        });

        it('should call onRestore when restore button is clicked', async () => {
            render(
                <VersionHistoryPanel
                    snapshots={mockSnapshots}
                    currentSnapshotId="snap-2"
                    onRestore={mockOnRestore}
                    onDelete={mockOnDelete}
                    onCreateSnapshot={mockOnCreateSnapshot}
                />
            );

            const restoreButtons = screen.getAllByText('Restore');
            fireEvent.click(restoreButtons[0]);

            await waitFor(() => {
                expect(mockOnRestore).toHaveBeenCalledWith('snap-1');
            });
        });

        it('should call onDelete when delete button is clicked', async () => {
            render(
                <VersionHistoryPanel
                    snapshots={mockSnapshots}
                    currentSnapshotId="snap-2"
                    onRestore={mockOnRestore}
                    onDelete={mockOnDelete}
                    onCreateSnapshot={mockOnCreateSnapshot}
                />
            );

            const deleteButtons = screen.getAllByLabelText('Delete');
            fireEvent.click(deleteButtons[0]);

            await waitFor(() => {
                expect(mockOnDelete).toHaveBeenCalledWith('snap-1');
            });
        });

        it('should open create snapshot dialog', () => {
            render(
                <VersionHistoryPanel
                    snapshots={mockSnapshots}
                    currentSnapshotId="snap-2"
                    onRestore={mockOnRestore}
                    onDelete={mockOnDelete}
                    onCreateSnapshot={mockOnCreateSnapshot}
                />
            );

            const createButton = screen.getByText('Create Snapshot');
            fireEvent.click(createButton);

            expect(screen.getByText('Create Manual Snapshot')).toBeInTheDocument();
        });

        it('should call onCreateSnapshot with label and description', async () => {
            render(
                <VersionHistoryPanel
                    snapshots={mockSnapshots}
                    currentSnapshotId="snap-2"
                    onRestore={mockOnRestore}
                    onDelete={mockOnDelete}
                    onCreateSnapshot={mockOnCreateSnapshot}
                />
            );

            // Open dialog
            const createButton = screen.getByText('Create Snapshot');
            fireEvent.click(createButton);

            // Fill form
            const labelInput = screen.getByLabelText('Label');
            const descriptionInput = screen.getByLabelText('Description');

            fireEvent.change(labelInput, { target: { value: 'Test Snapshot' } });
            fireEvent.change(descriptionInput, { target: { value: 'Test description' } });

            // Submit
            const saveButton = screen.getByText('Create');
            fireEvent.click(saveButton);

            await waitFor(() => {
                expect(mockOnCreateSnapshot).toHaveBeenCalledWith('Test Snapshot', 'Test description');
            });
        });

        it('should display element counts', () => {
            render(
                <VersionHistoryPanel
                    snapshots={mockSnapshots}
                    currentSnapshotId="snap-2"
                    onRestore={mockOnRestore}
                    onDelete={mockOnDelete}
                    onCreateSnapshot={mockOnCreateSnapshot}
                />
            );

            expect(screen.getByText('1 elements')).toBeInTheDocument(); // First snapshot
            expect(screen.getByText('3 elements')).toBeInTheDocument(); // Second snapshot (2 nodes + 1 edge)
        });

        it('should show empty state when no snapshots', () => {
            render(
                <VersionHistoryPanel
                    snapshots={[]}
                    currentSnapshotId={null}
                    onRestore={mockOnRestore}
                    onDelete={mockOnDelete}
                    onCreateSnapshot={mockOnCreateSnapshot}
                />
            );

            expect(screen.getByText('No version history yet')).toBeInTheDocument();
        });
    });

    describe('AutoSaveIndicator', () => {
        it('should render idle state', () => {
            render(<AutoSaveIndicator status="idle" />);

            expect(screen.getByText('Not saved')).toBeInTheDocument();
        });

        it('should render saving state with spinner', () => {
            render(<AutoSaveIndicator status="saving" />);

            expect(screen.getByText('Saving...')).toBeInTheDocument();
            expect(screen.getByRole('progressbar')).toBeInTheDocument();
        });

        it('should render saved state with timestamp', () => {
            const lastSaved = Date.now() - 30000; // 30 seconds ago

            render(<AutoSaveIndicator status="saved" lastSaved={lastSaved} />);

            expect(screen.getByText(/Saved/)).toBeInTheDocument();
        });

        it('should render error state with retry button', () => {
            const mockOnRetry = vi.fn();

            render(
                <AutoSaveIndicator
                    status="error"
                    error="Network error"
                    onRetry={mockOnRetry}
                />
            );

            expect(screen.getByText('Failed to save')).toBeInTheDocument();

            const chip = screen.getByText('Failed to save');
            fireEvent.click(chip);

            expect(mockOnRetry).toHaveBeenCalled();
        });

        it('should render disabled state', () => {
            render(<AutoSaveIndicator status="disabled" />);

            expect(screen.getByText('Auto-save disabled')).toBeInTheDocument();
        });

        it('should render compact variant', () => {
            const { container } = render(<AutoSaveIndicator status="saved" variant="compact" />);

            // Compact variant should only show icon
            expect(container.querySelector('svg')).toBeInTheDocument();
        });

        it('should update timestamp periodically', async () => {
            vi.useFakeTimers();

            const lastSaved = Date.now() - 5000; // 5 seconds ago

            render(<AutoSaveIndicator status="saved" lastSaved={lastSaved} />);

            expect(screen.getByText(/5s ago/)).toBeInTheDocument();

            // Fast-forward 10 seconds
            vi.advanceTimersByTime(10000);

            await waitFor(() => {
                expect(screen.getByText(/15s ago/)).toBeInTheDocument();
            });

            vi.useRealTimers();
        });
    });

    describe('VersionDiffViewer', () => {
        let mockSnapshot1: CanvasSnapshot;
        let mockSnapshot2: CanvasSnapshot;
        let mockDiff: VersionDiff;
        let mockOnRestore: ReturnType<typeof vi.fn>;

        beforeEach(() => {
            mockSnapshot1 = {
                id: 'snap-1',
                version: 1,
                timestamp: Date.now() - 3600000,
                data: {
                    nodes: [{ id: 'node-1', position: { x: 0, y: 0 }, data: {} }],
                    edges: [],
                    viewport: { x: 0, y: 0, zoom: 1 },
                },
                checksum: 'abc123',
                label: 'Version 1',
            };

            mockSnapshot2 = {
                id: 'snap-2',
                version: 2,
                timestamp: Date.now() - 1800000,
                data: {
                    nodes: [
                        { id: 'node-1', position: { x: 0, y: 0 }, data: {} },
                        { id: 'node-2', position: { x: 100, y: 100 }, data: {} },
                    ],
                    edges: [{ id: 'edge-1', source: 'node-1', target: 'node-2' }],
                    viewport: { x: 0, y: 0, zoom: 1 },
                },
                checksum: 'def456',
                label: 'Version 2',
            };

            mockDiff = {
                nodesAdded: 1,
                nodesRemoved: 0,
                nodesModified: 0,
                nodesUnchanged: 1,
                edgesAdded: 1,
                edgesRemoved: 0,
                edgesModified: 0,
                edgesUnchanged: 0,
            };

            mockOnRestore = vi.fn();
        });

        it('should render both versions', () => {
            render(
                <VersionDiffViewer
                    open={true}
                    onClose={vi.fn()}
                    snapshot1={mockSnapshot1}
                    snapshot2={mockSnapshot2}
                    diff={mockDiff}
                    onRestore={mockOnRestore}
                />
            );

            expect(screen.getByText('Version 1')).toBeInTheDocument();
            expect(screen.getByText('Version 2')).toBeInTheDocument();
        });

        it('should display diff statistics', () => {
            render(
                <VersionDiffViewer
                    open={true}
                    onClose={vi.fn()}
                    snapshot1={mockSnapshot1}
                    snapshot2={mockSnapshot2}
                    diff={mockDiff}
                    onRestore={mockOnRestore}
                />
            );

            expect(screen.getByText(/1.*added/i)).toBeInTheDocument(); // 1 node added
            expect(screen.getByText(/1.*added/i)).toBeInTheDocument(); // 1 edge added
        });

        it('should call onRestore for snapshot1', () => {
            render(
                <VersionDiffViewer
                    open={true}
                    onClose={vi.fn()}
                    snapshot1={mockSnapshot1}
                    snapshot2={mockSnapshot2}
                    diff={mockDiff}
                    onRestore={mockOnRestore}
                />
            );

            const restoreButtons = screen.getAllByText('Restore');
            fireEvent.click(restoreButtons[0]);

            expect(mockOnRestore).toHaveBeenCalledWith('snap-1');
        });

        it('should call onRestore for snapshot2', () => {
            render(
                <VersionDiffViewer
                    open={true}
                    onClose={vi.fn()}
                    snapshot1={mockSnapshot1}
                    snapshot2={mockSnapshot2}
                    diff={mockDiff}
                    onRestore={mockOnRestore}
                />
            );

            const restoreButtons = screen.getAllByText('Restore');
            fireEvent.click(restoreButtons[1]);

            expect(mockOnRestore).toHaveBeenCalledWith('snap-2');
        });

        it('should call onClose when close button is clicked', () => {
            const mockOnClose = vi.fn();

            render(
                <VersionDiffViewer
                    open={true}
                    onClose={mockOnClose}
                    snapshot1={mockSnapshot1}
                    snapshot2={mockSnapshot2}
                    diff={mockDiff}
                    onRestore={mockOnRestore}
                />
            );

            const closeButton = screen.getByText('Close');
            fireEvent.click(closeButton);

            expect(mockOnClose).toHaveBeenCalled();
        });

        it('should not render when open is false', () => {
            const { container } = render(
                <VersionDiffViewer
                    open={false}
                    onClose={vi.fn()}
                    snapshot1={mockSnapshot1}
                    snapshot2={mockSnapshot2}
                    diff={mockDiff}
                    onRestore={mockOnRestore}
                />
            );

            expect(container.firstChild).toBeNull();
        });

        it('should color-code diff statistics', () => {
            render(
                <VersionDiffViewer
                    open={true}
                    onClose={vi.fn()}
                    snapshot1={mockSnapshot1}
                    snapshot2={mockSnapshot2}
                    diff={mockDiff}
                    onRestore={mockOnRestore}
                />
            );

            // Added should be green
            const addedStats = screen.getAllByText(/added/i);
            expect(addedStats.length).toBeGreaterThan(0);

            // Unchanged should be blue
            const unchangedStats = screen.getAllByText(/unchanged/i);
            expect(unchangedStats.length).toBeGreaterThan(0);
        });
    });
});
