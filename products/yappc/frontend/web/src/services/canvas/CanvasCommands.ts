/**
 * Canvas Commands
 * 
 * Command pattern implementations for canvas operations.
 * Enables undo/redo functionality for all canvas modifications.
 * 
 * @doc.type module
 * @doc.purpose Command pattern for undo/redo
 * @doc.layer product
 * @doc.pattern Command Pattern
 */

import type { Command } from './CanvasPersistence';
import type { CanvasElement, CanvasConnection, CanvasState } from '../../components/canvas/workspace/canvasAtoms';
import type { XYPosition } from '@xyflow/react';

/**
 * Base command implementation
 */
abstract class BaseCommand implements Command {
    public id: string;
    public type: string;
    public timestamp: number;

    constructor(type: string) {
        this.id = `cmd-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        this.type = type;
        this.timestamp = Date.now();
    }

    abstract execute(): Promise<void>;
    abstract undo(): Promise<void>;
    redo?(): Promise<void>;
}

/**
 * Add Element Command
 */
export class AddElementCommand extends BaseCommand {
    private element: CanvasElement;
    private setState: (fn: (state: CanvasState) => CanvasState) => void;

    constructor(
        element: CanvasElement,
        setState: (fn: (state: CanvasState) => CanvasState) => void
    ) {
        super('add-element');
        this.element = element;
        this.setState = setState;
    }

    async execute(): Promise<void> {
        this.setState(state => ({
            ...state,
            elements: [...state.elements, this.element],
        }));
    }

    async undo(): Promise<void> {
        this.setState(state => ({
            ...state,
            elements: state.elements.filter((e: unknown) => e.id !== this.element.id),
        }));
    }

    async redo(): Promise<void> {
        await this.execute();
    }
}

/**
 * Remove Element Command
 */
export class RemoveElementCommand extends BaseCommand {
    private elementId: string;
    private removedElement: CanvasElement | null = null;
    private setState: (fn: (state: CanvasState) => CanvasState) => void;

    constructor(
        elementId: string,
        setState: (fn: (state: CanvasState) => CanvasState) => void
    ) {
        super('remove-element');
        this.elementId = elementId;
        this.setState = setState;
    }

    async execute(): Promise<void> {
        this.setState(state => {
            this.removedElement = state.elements.find((e: unknown) => e.id === this.elementId) || null;
            return {
                ...state,
                elements: state.elements.filter((e: unknown) => e.id !== this.elementId),
            };
        });
    }

    async undo(): Promise<void> {
        if (this.removedElement) {
            this.setState(state => ({
                ...state,
                elements: [...state.elements, this.removedElement!],
            }));
        }
    }

    async redo(): Promise<void> {
        await this.execute();
    }
}

/**
 * State Snapshot Command
 * Used for legacy history support where we snapshot the entire state
 */
export class StateSnapshotCommand<T = CanvasState> extends BaseCommand {
    private beforeState: T;
    private afterState: T;
    private setCanvasState: (state: T) => void;

    constructor(
        beforeState: T,
        afterState: T,
        setCanvasState: (state: T) => void,
        type: string = 'state-change'
    ) {
        super(type);
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.setCanvasState = setCanvasState;
    }

    async execute(): Promise<void> {
        // For snapshot commands, execute applies the after state
        this.setCanvasState(this.afterState);
    }

    async undo(): Promise<void> {
        this.setCanvasState(this.beforeState);
    }

    async redo(): Promise<void> {
        this.setCanvasState(this.afterState);
    }
}

/**
 * Move Element Command
 */
export class MoveElementCommand extends BaseCommand {
    private elementId: string;
    private oldPosition: XYPosition;
    private newPosition: XYPosition;
    private setState: (fn: (state: CanvasState) => CanvasState) => void;

    constructor(
        elementId: string,
        oldPosition: XYPosition,
        newPosition: XYPosition,
        setState: (fn: (state: CanvasState) => CanvasState) => void
    ) {
        super('move-element');
        this.elementId = elementId;
        this.oldPosition = oldPosition;
        this.newPosition = newPosition;
        this.setState = setState;
    }

    async execute(): Promise<void> {
        this.setState(state => ({
            ...state,
            elements: state.elements.map((e: unknown) =>
                e.id === this.elementId
                    ? { ...e, position: this.newPosition }
                    : e
            ),
        }));
    }

    async undo(): Promise<void> {
        this.setState(state => ({
            ...state,
            elements: state.elements.map((e: unknown) =>
                e.id === this.elementId
                    ? { ...e, position: this.oldPosition }
                    : e
            ),
        }));
    }

    async redo(): Promise<void> {
        await this.execute();
    }
}

/**
 * Update Element Command
 */
export class UpdateElementCommand extends BaseCommand {
    private elementId: string;
    private oldData: Partial<CanvasElement>;
    private newData: Partial<CanvasElement>;
    private setState: (fn: (state: CanvasState) => CanvasState) => void;

    constructor(
        elementId: string,
        oldData: Partial<CanvasElement>,
        newData: Partial<CanvasElement>,
        setState: (fn: (state: CanvasState) => CanvasState) => void
    ) {
        super('update-element');
        this.elementId = elementId;
        this.oldData = oldData;
        this.newData = newData;
        this.setState = setState;
    }

    async execute(): Promise<void> {
        this.setState(state => ({
            ...state,
            elements: state.elements.map((e: unknown) =>
                e.id === this.elementId
                    ? { ...e, ...this.newData }
                    : e
            ),
        }));
    }

    async undo(): Promise<void> {
        this.setState(state => ({
            ...state,
            elements: state.elements.map((e: unknown) =>
                e.id === this.elementId
                    ? { ...e, ...this.oldData }
                    : e
            ),
        }));
    }

    async redo(): Promise<void> {
        await this.execute();
    }
}

/**
 * Add Connection Command
 */
export class AddConnectionCommand extends BaseCommand {
    private connection: CanvasConnection;
    private setState: (fn: (state: CanvasState) => CanvasState) => void;

    constructor(
        connection: CanvasConnection,
        setState: (fn: (state: CanvasState) => CanvasState) => void
    ) {
        super('add-connection');
        this.connection = connection;
        this.setState = setState;
    }

    async execute(): Promise<void> {
        this.setState(state => ({
            ...state,
            connections: [...state.connections, this.connection],
        }));
    }

    async undo(): Promise<void> {
        this.setState(state => ({
            ...state,
            connections: state.connections.filter((e: unknown) => e.id !== this.connection.id),
        }));
    }

    async redo(): Promise<void> {
        await this.execute();
    }
}

/**
 * Remove Connection Command
 */
export class RemoveConnectionCommand extends BaseCommand {
    private connectionId: string;
    private removedConnection: CanvasConnection | null = null;
    private setState: (fn: (state: CanvasState) => CanvasState) => void;

    constructor(
        connectionId: string,
        setState: (fn: (state: CanvasState) => CanvasState) => void
    ) {
        super('remove-connection');
        this.connectionId = connectionId;
        this.setState = setState;
    }

    async execute(): Promise<void> {
        this.setState(state => {
            this.removedConnection = state.connections.find((c: unknown) => c.id === this.connectionId) || null;
            return {
                ...state,
                connections: state.connections.filter((c: unknown) => c.id !== this.connectionId),
            };
        });
    }

    async undo(): Promise<void> {
        if (this.removedConnection) {
            this.setState(state => ({
                ...state,
                connections: [...state.connections, this.removedConnection!],
            }));
        }
    }

    async redo(): Promise<void> {
        await this.execute();
    }
}

/**
 * Batch Command - executes multiple commands as one
 */
export class BatchCommand extends BaseCommand {
    private commands: Command[];

    constructor(commands: Command[]) {
        super('batch');
        this.commands = commands;
    }

    async execute(): Promise<void> {
        for (const command of this.commands) {
            await command.execute();
        }
    }

    async undo(): Promise<void> {
        // Undo in reverse order
        for (let i = this.commands.length - 1; i >= 0; i--) {
            await this.commands[i].undo();
        }
    }

    async redo(): Promise<void> {
        await this.execute();
    }
}

/**
 * Command factory for creating common commands
 */
export class CommandFactory {
    static createAddElement(
        element: CanvasElement,
        setState: (fn: (state: CanvasState) => CanvasState) => void
    ): AddElementCommand {
        return new AddElementCommand(element, setState);
    }

    static createRemoveElement(
        elementId: string,
        setState: (fn: (state: CanvasState) => CanvasState) => void
    ): RemoveElementCommand {
        return new RemoveElementCommand(elementId, setState);
    }

    static createMoveElement(
        elementId: string,
        oldPosition: XYPosition,
        newPosition: XYPosition,
        setState: (fn: (state: CanvasState) => CanvasState) => void
    ): MoveElementCommand {
        return new MoveElementCommand(elementId, oldPosition, newPosition, setState);
    }

    static createUpdateElement(
        elementId: string,
        oldData: Partial<CanvasElement>,
        newData: Partial<CanvasElement>,
        setState: (fn: (state: CanvasState) => CanvasState) => void
    ): UpdateElementCommand {
        return new UpdateElementCommand(elementId, oldData, newData, setState);
    }

    static createAddConnection(
        connection: CanvasConnection,
        setState: (fn: (state: CanvasState) => CanvasState) => void
    ): AddConnectionCommand {
        return new AddConnectionCommand(connection, setState);
    }

    static createRemoveConnection(
        connectionId: string,
        setState: (fn: (state: CanvasState) => CanvasState) => void
    ): RemoveConnectionCommand {
        return new RemoveConnectionCommand(connectionId, setState);
    }

    static createBatch(commands: Command[]): BatchCommand {
        return new BatchCommand(commands);
    }
}
