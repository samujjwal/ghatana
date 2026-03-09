/**
 * CanvasCommands Tests
 * 
 * Unit tests for canvas command pattern implementations.
 * Tests all command types (Add, Remove, Move, Update, Batch).
 * 
 * @doc.type test
 * @doc.purpose Unit tests for CanvasCommands
 * @doc.layer product
 * @doc.pattern Unit Testing
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
    AddElementCommand,
    RemoveElementCommand,
    MoveElementCommand,
    UpdateElementCommand,
    AddConnectionCommand,
    RemoveConnectionCommand,
    BatchCommand,
    CommandFactory,
} from '../CanvasCommands';
import type { CanvasState } from '../CanvasPersistence';

describe('CanvasCommands', () => {
    let mockState: CanvasState;
    let mockSetState: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        mockState = {
            elements: [
                { id: 'node-1', position: { x: 0, y: 0 }, data: { label: 'Node 1' } },
                { id: 'node-2', position: { x: 100, y: 100 }, data: { label: 'Node 2' } },
            ],
            connections: [
                { id: 'edge-1', source: 'node-1', target: 'node-2' },
            ],
            viewport: { x: 0, y: 0, zoom: 1 },
        };

        mockSetState = vi.fn((updater) => {
            if (typeof updater === 'function') {
                mockState = updater(mockState);
            } else {
                mockState = updater;
            }
        });
    });

    describe('AddElementCommand', () => {
        it('should add element on execute', async () => {
            const newNode = {
                id: 'node-3',
                position: { x: 200, y: 200 },
                data: { label: 'Node 3' },
            };

            const command = new AddElementCommand(newNode, mockSetState);

            await command.execute();

            expect(mockState.elements.length).toBe(3);
            expect(mockState.elements[2].id).toBe('node-3');
        });

        it('should remove element on undo', async () => {
            const newNode = {
                id: 'node-3',
                position: { x: 200, y: 200 },
                data: { label: 'Node 3' },
            };

            const command = new AddElementCommand(newNode, mockSetState);

            await command.execute();
            await command.undo();

            expect(mockState.elements.length).toBe(2);
            expect(mockState.elements.find(n => n.id === 'node-3')).toBeUndefined();
        });
    });

    describe('RemoveElementCommand', () => {
        it('should remove element on execute', async () => {
            const command = new RemoveElementCommand('node-1', mockSetState);

            await command.execute();

            expect(mockState.elements.length).toBe(1);
            expect(mockState.elements.find(n => n.id === 'node-1')).toBeUndefined();
        });

        it('should restore element on undo', async () => {
            const command = new RemoveElementCommand('node-1', mockSetState);

            await command.execute();
            await command.undo();

            expect(mockState.elements.length).toBe(2);
            // Elements are added to end, so restored element will be at index 1
            const restoredElement = mockState.elements.find(e => e.id === 'node-1');
            expect(restoredElement?.id).toBe('node-1');
        });

        it('should store removed element data', async () => {
            const command = new RemoveElementCommand('node-1', mockSetState);

            await command.execute();

            // Verify the element was stored for undo
            await command.undo();

            const restoredNode = mockState.elements.find(n => n.id === 'node-1');
            expect(restoredNode?.data.label).toBe('Node 1');
        });
    });

    describe('MoveElementCommand', () => {
        it('should move element on execute', async () => {
            const command = new MoveElementCommand(
                'node-1',
                { x: 0, y: 0 },
                { x: 50, y: 50 },
                mockSetState
            );

            await command.execute();

            const movedNode = mockState.elements.find(n => n.id === 'node-1');
            expect(movedNode?.position).toEqual({ x: 50, y: 50 });
        });

        it('should restore original position on undo', async () => {
            const command = new MoveElementCommand(
                'node-1',
                { x: 0, y: 0 },
                { x: 50, y: 50 },
                mockSetState
            );

            await command.execute();
            await command.undo();

            const node = mockState.elements.find(n => n.id === 'node-1');
            expect(node?.position).toEqual({ x: 0, y: 0 });
        });

        it('should move to new position on redo', async () => {
            const command = new MoveElementCommand(
                'node-1',
                { x: 0, y: 0 },
                { x: 50, y: 50 },
                mockSetState
            );

            await command.execute();
            await command.undo();
            await command.redo!();

            const node = mockState.elements.find(n => n.id === 'node-1');
            expect(node?.position).toEqual({ x: 50, y: 50 });
        });
    });

    describe('UpdateElementCommand', () => {
        it('should update element properties on execute', async () => {
            const command = new UpdateElementCommand(
                'node-1',
                { data: { label: 'Node 1' } },
                { data: { label: 'Updated Node' } },
                mockSetState
            );

            await command.execute();

            const updatedNode = mockState.elements.find(n => n.id === 'node-1');
            expect(updatedNode?.data.label).toBe('Updated Node');
        });

        it('should restore original properties on undo', async () => {
            const command = new UpdateElementCommand(

                'node-1',
                { data: { label: 'Node 1' } },
                { data: { label: 'Updated Node' } },
                mockSetState
            );

            await command.execute();
            await command.undo();

            const node = mockState.elements.find(n => n.id === 'node-1');
            expect(node?.data.label).toBe('Node 1');
        });

        it('should merge properties, not replace', async () => {
            const command = new UpdateElementCommand(
                
                'node-1',
                { data: { label: 'Node 1', type: 'default' } },
                { data: { label: 'Updated Node', type: 'default' } }, // Must include all properties
                mockSetState
            );

            await command.execute();

            const node = mockState.elements.find(n => n.id === 'node-1');
            expect(node?.data.label).toBe('Updated Node');
            expect(node?.data.type).toBe('default'); // Should preserve
        });
    });

    describe('AddConnectionCommand', () => {
        it('should add connection on execute', async () => {
            const command = new AddConnectionCommand(
                { id: 'edge-2', source: 'node-2', target: 'node-1' },
                mockSetState
            );

            await command.execute();

            expect(mockState.connections.length).toBe(2);
            expect(mockState.connections[1].id).toBe('edge-2');
        });

        it('should remove connection on undo', async () => {
            const command = new AddConnectionCommand(
                { id: 'edge-2', source: 'node-2', target: 'node-1' },
                mockSetState
            );

            await command.execute();
            await command.undo();

            expect(mockState.connections.length).toBe(1);
            expect(mockState.connections.find(e => e.id === 'edge-2')).toBeUndefined();
        });
    });

    describe('RemoveConnectionCommand', () => {
        it('should remove connection on execute', async () => {
            const command = new RemoveConnectionCommand('edge-1', mockSetState);

            await command.execute();

            expect(mockState.connections.length).toBe(0);
        });

        it('should restore connection on undo', async () => {
            const command = new RemoveConnectionCommand('edge-1', mockSetState);

            await command.execute();
            await command.undo();

            expect(mockState.connections.length).toBe(1);
            expect(mockState.connections[0].id).toBe('edge-1');
        });
    });

    describe('BatchCommand', () => {
        it('should execute all sub-commands', async () => {
            const addCommand = new AddElementCommand(
                
                { id: 'node-3', position: { x: 200, y: 200 }, data: {} },
                mockSetState
            );

            const moveCommand = new MoveElementCommand(
                
                'node-1',
                { x: 0, y: 0 },
                { x: 50, y: 50 },
                mockSetState
            );

            const batchCommand = new BatchCommand([addCommand, moveCommand]);

            await batchCommand.execute();

            // Verify both commands executed
            expect(mockState.elements.length).toBe(3); // Added node-3
            const movedNode = mockState.elements.find(n => n.id === 'node-1');
            expect(movedNode?.position).toEqual({ x: 50, y: 50 });
        });

        it('should undo all sub-commands in reverse order', async () => {
            const addCommand = new AddElementCommand(
                
                { id: 'node-3', position: { x: 200, y: 200 }, data: {} },
                mockSetState
            );

            const moveCommand = new MoveElementCommand(
                
                'node-1',
                { x: 0, y: 0 },
                { x: 50, y: 50 },
                mockSetState
            );

            const batchCommand = new BatchCommand([addCommand, moveCommand]);

            await batchCommand.execute();
            await batchCommand.undo();

            // Verify both commands were undone
            expect(mockState.elements.length).toBe(2); // Removed node-3
            const node = mockState.elements.find(n => n.id === 'node-1');
            expect(node?.position).toEqual({ x: 0, y: 0 });
        });

        it('should redo all sub-commands', async () => {
            const addCommand = new AddElementCommand(
                
                { id: 'node-3', position: { x: 200, y: 200 }, data: {} },
                mockSetState
            );

            const batchCommand = new BatchCommand([addCommand]);

            await batchCommand.execute();
            await batchCommand.undo();
            await batchCommand.redo!();

            expect(mockState.elements.length).toBe(3);
        });
    });

    describe('CommandFactory', () => {
        it('should create AddElement command', () => {
            const command = CommandFactory.createAddElement(
                
                { id: 'node-3', position: { x: 0, y: 0 }, data: {} },
                mockSetState
            );

            expect(command.type).toBe('add-element');
        });

        it('should create RemoveElement command', () => {
            const command = CommandFactory.createRemoveElement('node', 'node-1', mockSetState);

            expect(command.type).toBe('remove-element');
        });

        it('should create MoveElement command', () => {
            const command = CommandFactory.createMoveElement(
                
                'node-1',
                { x: 0, y: 0 },
                { x: 50, y: 50 },
                mockSetState
            );

            expect(command.type).toBe('move-element');
        });

        it('should create UpdateElement command', () => {
            const command = CommandFactory.createUpdateElement(
                
                'node-1',
                { data: {} },
                { data: { label: 'New' } },
                mockSetState
            );

            expect(command.type).toBe('update-element');
        });

        it('should create AddConnection command', () => {
            const command = CommandFactory.createAddConnection(
                { id: 'edge-2', source: 'node-1', target: 'node-2' },
                mockSetState
            );

            expect(command.type).toBe('add-connection');
        });

        it('should create RemoveConnection command', () => {
            const command = CommandFactory.createRemoveConnection('edge-1', mockSetState);

            expect(command.type).toBe('remove-connection');
        });

        it('should create Batch command', () => {
            const cmd1 = CommandFactory.createAddElement('node', { id: 'node-3' } as unknown, mockSetState);
            const cmd2 = CommandFactory.createAddElement('node', { id: 'node-4' } as unknown, mockSetState);

            const batchCommand = CommandFactory.createBatch([cmd1, cmd2]);

            expect(batchCommand.type).toBe('batch');
        });
    });
});
