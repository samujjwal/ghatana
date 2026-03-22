/**
 * CanvasPersistence Tests
 * 
 * Unit tests for CanvasPersistence service module.
 * Tests save/load, versioning, auto-save, and command pattern.
 * 
 * @doc.type test
 * @doc.purpose Unit tests for CanvasPersistence
 * @doc.layer product
 * @doc.pattern Unit Testing
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { CanvasPersistence } from '../CanvasPersistence';
import type { CanvasState, CanvasSnapshot, Command } from '../CanvasPersistence';

describe('CanvasPersistence', () => {
    let persistence: CanvasPersistence;
    let mockCanvasState: CanvasState;

    beforeEach(() => {
        // Clear localStorage
        localStorage.clear();

        // Reset persistence with localStorage backend
        persistence = new CanvasPersistence({
            backend: 'localStorage',
            autoSave: { enabled: false, interval: 30000 }, // Disable auto-save for tests
            maxSnapshots: 5,
        });

        // Mock canvas state
        mockCanvasState = {
            elements: [
                { id: 'node-1', position: { x: 0, y: 0 }, data: {} },
                { id: 'node-2', position: { x: 100, y: 100 }, data: {} },
            ],
            connections: [
                { id: 'edge-1', source: 'node-1', target: 'node-2' },
            ],
            viewport: { x: 0, y: 0, zoom: 1 },
        };
    });

    afterEach(() => {
        persistence.destroy();
    });

    describe('Save and Load', () => {
        it('should save canvas state to localStorage', async () => {
            await persistence.save('project-1', 'canvas-1', mockCanvasState, {
                label: 'Test Save',
            });

            const key = 'canvas:project-1:canvas-1';
            const savedData = localStorage.getItem(key);

            expect(savedData).toBeTruthy();

            const parsed = JSON.parse(savedData!);
            expect(parsed.data.elements.length).toBe(2);
            expect(parsed.label).toBe('Test Save');
        });

        it('should load canvas state from localStorage', async () => {
            // First save
            await persistence.save('project-1', 'canvas-1', mockCanvasState);

            // Then load
            const loaded = await persistence.load('project-1', 'canvas-1');

            expect(loaded).toBeTruthy();
            expect(loaded?.data.elements.length).toBe(2);
            expect(loaded?.data.connections.length).toBe(1);
        });

        it('should return null when loading non-existent canvas', async () => {
            const loaded = await persistence.load('non-existent', 'canvas');
            expect(loaded).toBeNull();
        });

        it('should overwrite existing save', async () => {
            // Save first version
            await persistence.save('project-1', 'canvas-1', mockCanvasState, {
                label: 'Version 1',
            });

            // Save second version
            const updatedState = {
                ...mockCanvasState,
                elements: [...mockCanvasState.elements, { id: 'node-3', position: { x: 200, y: 200 }, data: {} }],
            };

            await persistence.save('project-1', 'canvas-1', updatedState, {
                label: 'Version 2',
            });

            // Load and verify it's the second version
            const loaded = await persistence.load('project-1', 'canvas-1');
            expect(loaded?.data.elements.length).toBe(3);
        });
    });

    describe('Version History', () => {
        it('should create manual snapshot', async () => {
            await persistence.createSnapshot(
                'project-1',
                'canvas-1',
                mockCanvasState,
                'Initial Version',
                'First snapshot'
            );

            const history = await persistence.getVersionHistory('project-1', 'canvas-1');

            expect(history.length).toBe(1);
            expect(history[0].label).toBe('Initial Version');
            expect(history[0].description).toBe('First snapshot');
        });

        it('should respect maxSnapshots limit', async () => {
            // Create 10 snapshots (max is 5)
            for (let i = 0; i < 10; i++) {
                await persistence.createSnapshot(
                    'project-1',
                    'canvas-1',
                    mockCanvasState,
                    `Version ${i + 1}`
                );
            }

            const history = await persistence.getVersionHistory('project-1', 'canvas-1');

            // Should only keep the latest 5
            expect(history.length).toBe(5);
            expect(history[0].label).toBe('Version 10'); // Newest first
            expect(history[4].label).toBe('Version 6'); // Oldest kept
        });

        it('should restore from snapshot', async () => {
            // Create initial snapshot
            await persistence.createSnapshot(
                'project-1',
                'canvas-1',
                mockCanvasState,
                'Version 1'
            );

            // Modify state and create another snapshot
            const modifiedState = {
                ...mockCanvasState,
                elements: [mockCanvasState.elements[0]], // Remove second node
            };

            await persistence.createSnapshot(
                'project-1',
                'canvas-1',
                modifiedState,
                'Version 2'
            );

            // Get first snapshot and restore
            const history = await persistence.getVersionHistory('project-1', 'canvas-1');
            const firstSnapshot = history[1]; // Oldest is at index 1

            const restored = await persistence.restoreSnapshot(firstSnapshot.id);

            expect(restored?.elements.length).toBe(2); // Original state
        });

        it('should delete snapshot', async () => {
            await persistence.createSnapshot(
                'project-1',
                'canvas-1',
                mockCanvasState,
                'Test Version'
            );

            const history = await persistence.getVersionHistory('project-1', 'canvas-1');
            const snapshotId = history[0].id;

            await persistence.deleteSnapshot(snapshotId);

            const updatedHistory = await persistence.getVersionHistory('project-1', 'canvas-1');
            expect(updatedHistory.length).toBe(0);
        });

        it('should compute diff between snapshots', async () => {
            // Create first snapshot
            await persistence.createSnapshot(
                'project-1',
                'canvas-1',
                mockCanvasState,
                'Version 1'
            );

            // Modify state
            const modifiedState = {
                ...mockCanvasState,
                elements: [
                    ...mockCanvasState.elements,
                    { id: 'node-3', position: { x: 200, y: 200 }, data: {} },
                ],
                connections: [], // Remove all edges
            };

            // Create second snapshot
            await persistence.createSnapshot(
                'project-1',
                'canvas-1',
                modifiedState,
                'Version 2'
            );

            const history = await persistence.getVersionHistory('project-1', 'canvas-1');

            const diff = await persistence.diffVersions(history[0], history[1]);

            expect(diff.added.nodes).toBe(1); // Added node-3
            expect(diff.removed.nodes).toBe(0);
            expect(diff.removed.edges).toBe(1); // Removed edge-1
        });
    });

    describe('Command Pattern', () => {
        it('should execute command and add to history', async () => {
            const command: Command = {
                id: 'cmd-1',
                type: 'add-node',
                timestamp: Date.now(),
                execute: vi.fn().mockResolvedValue(undefined),
                undo: vi.fn().mockResolvedValue(undefined),
            };

            await persistence.executeCommand(command);

            expect(command.execute).toHaveBeenCalled();

            const stack = persistence.getCommandStack();
            expect(stack.past.length).toBe(1);
            expect(stack.past[0].id).toBe('cmd-1');
        });

        it('should undo last command', async () => {
            const command: Command = {
                id: 'cmd-1',
                type: 'add-node',
                timestamp: Date.now(),
                execute: vi.fn().mockResolvedValue(undefined),
                undo: vi.fn().mockResolvedValue(undefined),
            };

            await persistence.executeCommand(command);
            await persistence.undo();

            expect(command.undo).toHaveBeenCalled();

            const stack = persistence.getCommandStack();
            expect(stack.past.length).toBe(0);
            expect(stack.future.length).toBe(1);
        });

        it('should redo last undone command', async () => {
            const command: Command = {
                id: 'cmd-1',
                type: 'add-node',
                timestamp: Date.now(),
                execute: vi.fn().mockResolvedValue(undefined),
                undo: vi.fn().mockResolvedValue(undefined),
                redo: vi.fn().mockResolvedValue(undefined),
            };

            await persistence.executeCommand(command);
            await persistence.undo();
            await persistence.redo();

            expect(command.redo || command.execute).toHaveBeenCalled();

            const stack = persistence.getCommandStack();
            expect(stack.past.length).toBe(1);
            expect(stack.future.length).toBe(0);
        });

        it('should clear future stack on new command', async () => {
            const command1: Command = {
                id: 'cmd-1',
                type: 'add-node',
                timestamp: Date.now(),
                execute: vi.fn().mockResolvedValue(undefined),
                undo: vi.fn().mockResolvedValue(undefined),
            };

            const command2: Command = {
                id: 'cmd-2',
                type: 'add-node',
                timestamp: Date.now(),
                execute: vi.fn().mockResolvedValue(undefined),
                undo: vi.fn().mockResolvedValue(undefined),
            };

            await persistence.executeCommand(command1);
            await persistence.undo();
            await persistence.executeCommand(command2);

            const stack = persistence.getCommandStack();
            expect(stack.future.length).toBe(0); // Future should be cleared
            expect(stack.past.length).toBe(1);
            expect(stack.past[0].id).toBe('cmd-2');
        });

        it('should respect maxSize limit', async () => {
            // Execute 60 commands (max is 50)
            for (let i = 0; i < 60; i++) {
                const command: Command = {
                    id: `cmd-${i}`,
                    type: 'add-node',
                    timestamp: Date.now(),
                    execute: vi.fn().mockResolvedValue(undefined),
                    undo: vi.fn().mockResolvedValue(undefined),
                };

                await persistence.executeCommand(command);
            }

            const stack = persistence.getCommandStack();
            expect(stack.past.length).toBe(50); // Should only keep 50
            expect(stack.past[0].id).toBe('cmd-59'); // Most recent
            expect(stack.past[49].id).toBe('cmd-10'); // Oldest kept
        });

        it('should not undo when past is empty', async () => {
            const canUndo = await persistence.undo();
            expect(canUndo).toBe(false);
        });

        it('should not redo when future is empty', async () => {
            const canRedo = await persistence.redo();
            expect(canRedo).toBe(false);
        });
    });

    describe('Auto-Save', () => {
        it('should start auto-save with interval', () => {
            vi.useFakeTimers();

            const getState = vi.fn(() => mockCanvasState);

            persistence.startAutoSave(getState, "project-1", "canvas-1");

            // Fast-forward 1 second
            vi.advanceTimersByTime(1000);

            expect(getState).toHaveBeenCalledTimes(1);

            vi.useRealTimers();
        });

        it('should stop auto-save', () => {
            vi.useFakeTimers();

            const getState = vi.fn(() => mockCanvasState);

            persistence.startAutoSave(getState, "project-1", "canvas-1");
            persistence.stopAutoSave();

            // Fast-forward 2 seconds
            vi.advanceTimersByTime(2000);

            // Should not be called after stopping
            expect(getState).not.toHaveBeenCalled();

            vi.useRealTimers();
        });
    });

    describe('State Management', () => {
        it('should track current project and canvas', async () => {
            await persistence.save('project-1', 'canvas-1', mockCanvasState);

            expect(persistence.getCurrentProject()).toBe('project-1');
            expect(persistence.getCurrentCanvas()).toBe('canvas-1');
        });

        it('should check if canvas has unsaved changes', () => {
            expect(persistence.hasUnsavedChanges()).toBe(false);

            // Execute a command (creates unsaved change)
            const command: Command = {
                id: 'cmd-1',
                type: 'add-node',
                timestamp: Date.now(),
                execute: vi.fn().mockResolvedValue(undefined),
                undo: vi.fn().mockResolvedValue(undefined),
            };

            persistence.executeCommand(command);

            // Now there should be unsaved changes
            expect(persistence.hasUnsavedChanges()).toBe(true);
        });
    });

    describe('Cleanup', () => {
        it('should cleanup resources on destroy', () => {
            persistence.startAutoSave(() => mockCanvasState, 'project-1', 'canvas-1');
            persistence.destroy();

            const stack = persistence.getCommandStack();
            expect(stack.past.length).toBe(0);
            expect(stack.future.length).toBe(0);
        });
    });
});
