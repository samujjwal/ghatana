/**
 * Canvas Action Handlers
 * 
 * Concrete implementations for all canvas operations.
 * Handles element creation, manipulation, and canvas state management.
 * 
 * @doc.type handlers
 * @doc.purpose Canvas operation handlers
 * @doc.layer core
 */

import { ActionContext } from '../core/action-registry';
import { nanoid } from 'nanoid';

/**
 * Canvas element types
 */
export type CanvasElementType =
    | 'service'
    | 'database'
    | 'api-contract'
    | 'component'
    | 'screen'
    | 'wireframe'
    | 'code-block'
    | 'function'
    | 'class'
    | 'shape'
    | 'text'
    | 'frame'
    | 'connector';

/**
 * Base canvas element interface
 */
export interface CanvasElement {
    id: string;
    type: CanvasElementType;
    x: number;
    y: number;
    width: number;
    height: number;
    label?: string;
    data?: Record<string, unknown>;
    style?: Record<string, unknown>;
}

/**
 * Canvas state manager
 */
class CanvasStateManager {
    private elements: Map<string, CanvasElement> = new Map();
    private listeners: Array<(elements: CanvasElement[]) => void> = [];

    addElement(element: CanvasElement): void {
        this.elements.set(element.id, element);
        this.notifyListeners();
        console.log(`➕ Added element: ${element.type} (${element.id})`);
    }

    removeElement(id: string): void {
        this.elements.delete(id);
        this.notifyListeners();
        console.log(`➖ Removed element: ${id}`);
    }

    updateElement(id: string, updates: Partial<CanvasElement>): void {
        const element = this.elements.get(id);
        if (element) {
            Object.assign(element, updates);
            this.notifyListeners();
            console.log(`🔄 Updated element: ${id}`);
        }
    }

    getElement(id: string): CanvasElement | undefined {
        return this.elements.get(id);
    }

    getAllElements(): CanvasElement[] {
        return Array.from(this.elements.values());
    }

    subscribe(listener: (elements: CanvasElement[]) => void): () => void {
        this.listeners.push(listener);
        return () => {
            this.listeners = this.listeners.filter(l => l !== listener);
        };
    }

    private notifyListeners(): void {
        const elements = this.getAllElements();
        this.listeners.forEach(listener => listener(elements));
    }

    clear(): void {
        this.elements.clear();
        this.notifyListeners();
        console.log('🗑️ Cleared all elements');
    }
}

// Global canvas state
const canvasState = new CanvasStateManager();

/**
 * Get global canvas state manager
 */
export function getCanvasState(): CanvasStateManager {
    return canvasState;
}

/**
 * Layer-specific element handlers
 */
export const LayerHandlers = {
    /**
     * Add service node (Architecture layer)
     */
    addService: async (context: ActionContext): Promise<void> => {
        const element: CanvasElement = {
            id: nanoid(),
            type: 'service',
            x: 100 + Math.random() * 400,
            y: 100 + Math.random() * 300,
            width: 180,
            height: 100,
            label: 'New Service',
            data: {
                layer: context.layer,
                phase: context.phase,
                endpoints: [],
                dependencies: [],
            },
            style: {
                fillColor: '#3b82f6',
                strokeColor: '#1e40af',
                strokeWidth: 2,
            },
        };
        canvasState.addElement(element);
    },

    /**
     * Add database node (Architecture layer)
     */
    addDatabase: async (context: ActionContext): Promise<void> => {
        const element: CanvasElement = {
            id: nanoid(),
            type: 'database',
            x: 100 + Math.random() * 400,
            y: 100 + Math.random() * 300,
            width: 120,
            height: 120,
            label: 'New Database',
            data: {
                layer: context.layer,
                phase: context.phase,
                schema: {},
                connections: [],
            },
            style: {
                fillColor: '#10b981',
                strokeColor: '#047857',
                strokeWidth: 2,
            },
        };
        canvasState.addElement(element);
    },

    /**
     * Add API contract (Architecture layer)
     */
    addApiContract: async (context: ActionContext): Promise<void> => {
        const element: CanvasElement = {
            id: nanoid(),
            type: 'api-contract',
            x: 100 + Math.random() * 400,
            y: 100 + Math.random() * 300,
            width: 200,
            height: 150,
            label: 'API Contract',
            data: {
                layer: context.layer,
                phase: context.phase,
                endpoints: [],
                methods: [],
            },
            style: {
                fillColor: '#f59e0b',
                strokeColor: '#d97706',
                strokeWidth: 2,
            },
        };
        canvasState.addElement(element);
    },

    /**
     * Add UI component (Design layer)
     */
    addComponent: async (context: ActionContext): Promise<void> => {
        const element: CanvasElement = {
            id: nanoid(),
            type: 'component',
            x: 100 + Math.random() * 400,
            y: 100 + Math.random() * 300,
            width: 160,
            height: 120,
            label: 'Component',
            data: {
                layer: context.layer,
                phase: context.phase,
                props: {},
                state: {},
            },
            style: {
                fillColor: '#8b5cf6',
                strokeColor: '#6d28d9',
                strokeWidth: 2,
            },
        };
        canvasState.addElement(element);
    },

    /**
     * Add screen (Design layer)
     */
    addScreen: async (context: ActionContext): Promise<void> => {
        const element: CanvasElement = {
            id: nanoid(),
            type: 'screen',
            x: 100 + Math.random() * 400,
            y: 100 + Math.random() * 300,
            width: 300,
            height: 400,
            label: 'Screen',
            data: {
                layer: context.layer,
                phase: context.phase,
                components: [],
                navigation: {},
            },
            style: {
                fillColor: '#ec4899',
                strokeColor: '#be185d',
                strokeWidth: 2,
            },
        };
        canvasState.addElement(element);
    },

    /**
     * Add code block (Implementation layer)
     */
    addCodeBlock: async (context: ActionContext): Promise<void> => {
        const element: CanvasElement = {
            id: nanoid(),
            type: 'code-block',
            x: 100 + Math.random() * 400,
            y: 100 + Math.random() * 300,
            width: 400,
            height: 300,
            label: 'Code Block',
            data: {
                layer: context.layer,
                phase: context.phase,
                language: 'typescript',
                code: '// Add your code here',
            },
            style: {
                fillColor: '#1f2937',
                strokeColor: '#111827',
                strokeWidth: 1,
            },
        };
        canvasState.addElement(element);
    },

    /**
     * Add function (Implementation layer)
     */
    addFunction: async (context: ActionContext): Promise<void> => {
        const element: CanvasElement = {
            id: nanoid(),
            type: 'function',
            x: 100 + Math.random() * 400,
            y: 100 + Math.random() * 300,
            width: 200,
            height: 100,
            label: 'function()',
            data: {
                layer: context.layer,
                phase: context.phase,
                parameters: [],
                returnType: 'void',
            },
            style: {
                fillColor: '#06b6d4',
                strokeColor: '#0891b2',
                strokeWidth: 2,
            },
        };
        canvasState.addElement(element);
    },
};

/**
 * Universal element handlers
 */
export const UniversalHandlers = {
    /**
     * Add shape
     */
    addShape: async (context: ActionContext): Promise<void> => {
        const element: CanvasElement = {
            id: nanoid(),
            type: 'shape',
            x: 100 + Math.random() * 400,
            y: 100 + Math.random() * 300,
            width: 150,
            height: 100,
            label: 'Shape',
            data: {
                layer: context.layer,
                phase: context.phase,
                shapeType: 'rectangle',
            },
            style: {
                fillColor: '#6b7280',
                strokeColor: '#4b5563',
                strokeWidth: 2,
            },
        };
        canvasState.addElement(element);
    },

    /**
     * Add text
     */
    addText: async (context: ActionContext): Promise<void> => {
        const element: CanvasElement = {
            id: nanoid(),
            type: 'text',
            x: 100 + Math.random() * 400,
            y: 100 + Math.random() * 300,
            width: 200,
            height: 50,
            label: 'Text',
            data: {
                layer: context.layer,
                phase: context.phase,
                content: 'Add your text here',
                fontSize: 16,
            },
            style: {
                fillColor: 'transparent',
                strokeColor: 'transparent',
            },
        };
        canvasState.addElement(element);
    },

    /**
     * Add frame
     */
    addFrame: async (context: ActionContext): Promise<void> => {
        const element: CanvasElement = {
            id: nanoid(),
            type: 'frame',
            x: 50 + Math.random() * 300,
            y: 50 + Math.random() * 200,
            width: 600,
            height: 400,
            label: `${context.phase} Frame`,
            data: {
                layer: context.layer,
                phase: context.phase,
                children: [],
            },
            style: {
                fillColor: 'transparent',
                strokeColor: '#9ca3af',
                strokeWidth: 2,
            },
        };
        canvasState.addElement(element);
    },
};

/**
 * Element manipulation handlers
 */
export const ManipulationHandlers = {
    /**
     * Delete selected elements
     */
    deleteElements: async (elementIds: string[]): Promise<void> => {
        elementIds.forEach(id => canvasState.removeElement(id));
    },

    /**
     * Duplicate elements
     */
    duplicateElements: async (elementIds: string[]): Promise<void> => {
        elementIds.forEach(id => {
            const original = canvasState.getElement(id);
            if (original) {
                const duplicate: CanvasElement = {
                    ...original,
                    id: nanoid(),
                    x: original.x + 20,
                    y: original.y + 20,
                    label: `${original.label} (Copy)`,
                };
                canvasState.addElement(duplicate);
            }
        });
    },

    /**
     * Move elements
     */
    moveElements: async (
        elementIds: string[],
        deltaX: number,
        deltaY: number
    ): Promise<void> => {
        elementIds.forEach(id => {
            const element = canvasState.getElement(id);
            if (element) {
                canvasState.updateElement(id, {
                    x: element.x + deltaX,
                    y: element.y + deltaY,
                });
            }
        });
    },

    /**
     * Resize element
     */
    resizeElement: async (
        elementId: string,
        width: number,
        height: number
    ): Promise<void> => {
        canvasState.updateElement(elementId, { width, height });
    },

    /**
     * Update element label
     */
    updateLabel: async (elementId: string, label: string): Promise<void> => {
        canvasState.updateElement(elementId, { label });
    },
};

/**
 * Export all handlers
 */
export const CanvasHandlers = {
    ...LayerHandlers,
    ...UniversalHandlers,
    ...ManipulationHandlers,
};
