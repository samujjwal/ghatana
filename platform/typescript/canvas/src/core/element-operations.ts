/**
 * Element Operations - Common CRUD operations for canvas elements
 * 
 * @doc.type class
 * @doc.purpose Provides standardized CRUD operations for all element types
 * @doc.layer core
 * @doc.pattern Service
 * 
 * Implements AFFiNE-style EdgelessCRUD operations:
 * - Add/remove elements
 * - Update element properties
 * - Batch operations
 * - Undo/redo support
 */

import { CanvasElement } from "../elements/base.js";
import { elementRegistry } from "./element-registry.js";
import { connectionManager } from "./connection-manager.js";
import type { BaseElementProps, Point } from "../types/index.js";
import { nanoid } from "nanoid";

/**
 * Element change event
 */
export interface ElementChange {
    type: 'add' | 'remove' | 'update';
    elementId: string;
    elementType: string;
    previousState?: Record<string, unknown>;
    newState?: Record<string, unknown>;
    timestamp: number;
}

/**
 * Element operation result
 */
export interface OperationResult {
    success: boolean;
    elementId?: string;
    error?: string;
}

/**
 * Batch operation
 */
export interface BatchOperation {
    type: 'add' | 'remove' | 'update';
    elementId?: string;
    props?: Record<string, unknown>;
}

/**
 * Element update props
 */
export type UpdateProps<T> = Partial<Omit<T, 'id' | 'type'>>;

/**
 * Element Operations Manager
 * 
 * Central manager for all element CRUD operations.
 * Provides:
 * - Type-safe element creation
 * - Batch operations
 * - Change tracking for undo/redo
 * - Event emission
 */
export class ElementOperations {
    private elements = new Map<string, CanvasElement>();
    private changeHistory: ElementChange[] = [];
    private redoStack: ElementChange[] = [];
    private maxHistoryLength = 100;

    /** Callback for canvas integration */
    private onChangeCallback?: (change: ElementChange) => void;

    /** Index generator for z-ordering */
    private indexCounter = 0;

    /**
     * Set change callback
     */
    setOnChange(callback: (change: ElementChange) => void): void {
        this.onChangeCallback = callback;
    }

    /**
     * Add element to canvas
     */
    addElement<T extends BaseElementProps>(
        type: string,
        props: Partial<T> = {}
    ): OperationResult {
        // Generate ID if not provided
        const id = props.id || nanoid();
        const index = props.index || this.generateIndex();

        // Create element through registry
        const fullProps = {
            ...props,
            id,
            index,
            xywh: props.xywh || '[0,0,100,100]',
        } as T;

        const element = elementRegistry.createElement<CanvasElement, T>(type, fullProps);

        if (!element) {
            return { success: false, error: `Unknown element type: ${type}` };
        }

        this.elements.set(id, element);

        // Track change
        this.trackChange({
            type: 'add',
            elementId: id,
            elementType: type,
            newState: this.serializeElement(element),
            timestamp: Date.now(),
        });

        return { success: true, elementId: id };
    }

    /**
     * Remove element from canvas
     */
    removeElement(elementId: string): OperationResult {
        const element = this.elements.get(elementId);
        if (!element) {
            return { success: false, error: `Element not found: ${elementId}` };
        }

        // Track change before removal
        this.trackChange({
            type: 'remove',
            elementId,
            elementType: element.type,
            previousState: this.serializeElement(element),
            timestamp: Date.now(),
        });

        // Remove associated connectors
        if (elementRegistry.isConnectable(element)) {
            const connectors = connectionManager.getConnectors(elementId);
            for (const connector of connectors) {
                connectionManager.deleteConnector(connector.id);
            }
        }

        this.elements.delete(elementId);

        return { success: true, elementId };
    }

    /**
     * Update element properties
     */
    updateElement<T extends CanvasElement>(
        elementId: string,
        props: UpdateProps<T>
    ): OperationResult {
        const element = this.elements.get(elementId);
        if (!element) {
            return { success: false, error: `Element not found: ${elementId}` };
        }

        const previousState = this.serializeElement(element);

        // Apply updates
        Object.assign(element, props);

        // Update connectors if position changed
        if ('xywh' in props && elementRegistry.isConnectable(element)) {
            connectionManager.updateConnectorsForElement(elementId);
        }

        // Track change
        this.trackChange({
            type: 'update',
            elementId,
            elementType: element.type,
            previousState,
            newState: this.serializeElement(element),
            timestamp: Date.now(),
        });

        return { success: true, elementId };
    }

    /**
     * Get element by ID
     */
    getElementById(elementId: string): CanvasElement | undefined {
        return this.elements.get(elementId);
    }

    /**
     * Get all elements
     */
    getElements(): CanvasElement[] {
        return Array.from(this.elements.values());
    }

    /**
     * Get elements by type
     */
    getElementsByType(type: string): CanvasElement[] {
        return Array.from(this.elements.values()).filter(e => e.type === type);
    }

    /**
     * Execute batch operations
     */
    batch(operations: BatchOperation[]): OperationResult[] {
        const results: OperationResult[] = [];

        for (const op of operations) {
            switch (op.type) {
                case 'add':
                    results.push(this.addElement(op.props?.type as string || 'shape', op.props));
                    break;
                case 'remove':
                    if (op.elementId) {
                        results.push(this.removeElement(op.elementId));
                    }
                    break;
                case 'update':
                    if (op.elementId && op.props) {
                        results.push(this.updateElement(op.elementId, op.props));
                    }
                    break;
            }
        }

        return results;
    }

    /**
     * Undo last operation
     */
    undo(): OperationResult {
        const lastChange = this.changeHistory.pop();
        if (!lastChange) {
            return { success: false, error: 'Nothing to undo' };
        }

        // Push to redo stack
        this.redoStack.push(lastChange);

        // Reverse the change
        switch (lastChange.type) {
            case 'add':
                // Remove the added element
                this.elements.delete(lastChange.elementId);
                break;
            case 'remove':
                // Re-add the removed element
                if (lastChange.previousState) {
                    const element = this.deserializeElement(lastChange.elementType, lastChange.previousState);
                    if (element) {
                        this.elements.set(lastChange.elementId, element);
                    }
                }
                break;
            case 'update':
                // Restore previous state
                if (lastChange.previousState) {
                    const element = this.elements.get(lastChange.elementId);
                    if (element) {
                        Object.assign(element, lastChange.previousState);
                    }
                }
                break;
        }

        return { success: true, elementId: lastChange.elementId };
    }

    /**
     * Redo last undone operation
     */
    redo(): OperationResult {
        const change = this.redoStack.pop();
        if (!change) {
            return { success: false, error: 'Nothing to redo' };
        }

        // Push back to history
        this.changeHistory.push(change);

        // Re-apply the change
        switch (change.type) {
            case 'add':
                if (change.newState) {
                    const element = this.deserializeElement(change.elementType, change.newState);
                    if (element) {
                        this.elements.set(change.elementId, element);
                    }
                }
                break;
            case 'remove':
                this.elements.delete(change.elementId);
                break;
            case 'update':
                if (change.newState) {
                    const element = this.elements.get(change.elementId);
                    if (element) {
                        Object.assign(element, change.newState);
                    }
                }
                break;
        }

        return { success: true, elementId: change.elementId };
    }

    /**
     * Clear all elements
     */
    clear(): void {
        this.elements.clear();
        this.changeHistory = [];
        this.redoStack = [];
    }

    /**
     * Generate unique index for z-ordering
     */
    generateIndex(): string {
        this.indexCounter++;
        return `a${this.indexCounter.toString().padStart(5, '0')}`;
    }

    /**
     * Track element change
     */
    private trackChange(change: ElementChange): void {
        this.changeHistory.push(change);

        // Trim history if too long
        if (this.changeHistory.length > this.maxHistoryLength) {
            this.changeHistory.shift();
        }

        // Clear redo stack on new change
        this.redoStack = [];

        // Notify listener
        this.onChangeCallback?.(change);
    }

    /**
     * Serialize element for storage/undo
     */
    private serializeElement(element: CanvasElement): Record<string, unknown> {
        const registration = elementRegistry.getRegistration(element.type);
        if (registration?.serialize) {
            return registration.serialize(element);
        }

        // Default serialization - spread element first, then override with specific props
        const { id, type, xywh, rotate, index, ...rest } = element as unknown as Record<string, unknown>;
        return {
            ...rest,
            id,
            type,
            xywh,
            rotate,
            index,
        };
    }

    /**
     * Deserialize element from storage/undo
     */
    private deserializeElement(
        type: string,
        data: Record<string, unknown>
    ): CanvasElement | null {
        const registration = elementRegistry.getRegistration(type);

        if (registration?.deserialize) {
            const props = registration.deserialize(data);
            return elementRegistry.createElement(type, props);
        }

        // Default deserialization - cast through unknown to avoid type error
        return elementRegistry.createElement(type, data as unknown as BaseElementProps);
    }

    /**
     * Get change history
     */
    getHistory(): ElementChange[] {
        return [...this.changeHistory];
    }

    /**
     * Check if undo is available
     */
    canUndo(): boolean {
        return this.changeHistory.length > 0;
    }

    /**
     * Check if redo is available
     */
    canRedo(): boolean {
        return this.redoStack.length > 0;
    }
}

// Export singleton instance
export const elementOperations = new ElementOperations();
