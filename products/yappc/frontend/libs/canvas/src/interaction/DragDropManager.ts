/**
 * Drag & Drop Manager
 *
 * State machine for managing drag and drop operations on the canvas.
 * Handles palette-to-canvas, canvas-to-canvas, and external drops.
 *
 * @doc.type singleton
 * @doc.purpose Drag and drop state management
 * @doc.layer core
 * @doc.pattern State Machine, Observer
 */

import type { ArtifactContract, UniqueId, UniversalNode } from '../model/contracts';

// ============================================================================
// Drag Source Types
// ============================================================================

/**
 * Types of drag sources
 */
export type DragSourceType =
    | 'palette'
    | 'canvas'
    | 'tree'
    | 'external-file'
    | 'external-url'
    | 'clipboard';

/**
 * Drag source data for palette items
 */
export interface PaletteDragSource {
    type: 'palette';
    contract: ArtifactContract;
    preview?: HTMLElement;
}

/**
 * Drag source data for canvas nodes
 */
export interface CanvasDragSource {
    type: 'canvas';
    nodeIds: UniqueId[];
    nodes: UniversalNode[];
    originalPositions: Map<UniqueId, { x: number; y: number }>;
    isCopy: boolean;
}

/**
 * Drag source data for tree items
 */
export interface TreeDragSource {
    type: 'tree';
    nodeId: UniqueId;
    node: UniversalNode;
}

/**
 * Drag source data for external files
 */
export interface ExternalFileDragSource {
    type: 'external-file';
    files: File[];
    dataTransfer: DataTransfer;
}

/**
 * Drag source data for external URLs
 */
export interface ExternalUrlDragSource {
    type: 'external-url';
    url: string;
    dataTransfer: DataTransfer;
}

/**
 * Drag source data for clipboard paste
 */
export interface ClipboardDragSource {
    type: 'clipboard';
    nodes: UniversalNode[];
}

/**
 * Union of all drag sources
 */
export type DragSource =
    | PaletteDragSource
    | CanvasDragSource
    | TreeDragSource
    | ExternalFileDragSource
    | ExternalUrlDragSource
    | ClipboardDragSource;

// ============================================================================
// Drop Target Types
// ============================================================================

/**
 * Drop target types
 */
export type DropTargetType = 'canvas' | 'node' | 'slot' | 'tree';

/**
 * Drop target information
 */
export interface DropTarget {
    type: DropTargetType;
    /** Target node ID (if dropping on a node) */
    nodeId?: UniqueId;
    /** Target view ID */
    viewId: string;
    /** Slot name (if dropping in a specific slot) */
    slot?: string;
    /** Insert index (for ordered children) */
    index?: number;
    /** Canvas coordinates */
    position: { x: number; y: number };
    /** Whether drop is allowed */
    allowed: boolean;
    /** Reason if not allowed */
    reason?: string;
}

// ============================================================================
// Drag State
// ============================================================================

/**
 * Drag operation state
 */
export type DragState =
    | 'idle'
    | 'pending'
    | 'dragging'
    | 'over-target'
    | 'dropping'
    | 'cancelled';

/**
 * Complete drag context
 */
export interface DragContext {
    /** Current drag state */
    state: DragState;
    /** Drag source */
    source: DragSource | null;
    /** Current drop target */
    target: DropTarget | null;
    /** Start position (screen coordinates) */
    startPosition: { x: number; y: number } | null;
    /** Current position (screen coordinates) */
    currentPosition: { x: number; y: number } | null;
    /** Drag delta */
    delta: { x: number; y: number };
    /** Modifier keys */
    modifiers: {
        shift: boolean;
        ctrl: boolean;
        alt: boolean;
        meta: boolean;
    };
    /** Timestamp when drag started */
    startTime: number | null;
    /** Preview element */
    previewElement: HTMLElement | null;
}

/**
 * Default drag context
 */
const DEFAULT_CONTEXT: DragContext = {
    state: 'idle',
    source: null,
    target: null,
    startPosition: null,
    currentPosition: null,
    delta: { x: 0, y: 0 },
    modifiers: { shift: false, ctrl: false, alt: false, meta: false },
    startTime: null,
    previewElement: null,
};

// ============================================================================
// Drag Events
// ============================================================================

/**
 * Drag event types
 */
export type DragEventType =
    | 'drag-start'
    | 'drag-move'
    | 'drag-over'
    | 'drag-leave'
    | 'drag-end'
    | 'drop'
    | 'cancel';

/**
 * Drag event payload
 */
export interface DragEvent {
    type: DragEventType;
    context: DragContext;
    timestamp: number;
}

/**
 * Drag event listener
 */
export type DragEventListener = (event: DragEvent) => void;

// ============================================================================
// Drag & Drop Manager Implementation
// ============================================================================

/**
 * Configuration for drag and drop manager
 */
export interface DragDropConfig {
    /** Minimum drag distance to start drag */
    minDragDistance: number;
    /** Show preview during drag */
    showPreview: boolean;
    /** Auto-scroll near edges */
    autoScroll: boolean;
    /** Auto-scroll speed */
    autoScrollSpeed: number;
    /** Auto-scroll margin from edge */
    autoScrollMargin: number;
    /** Enable snap to grid during drag */
    snapToGrid: boolean;
    /** Grid size for snapping */
    gridSize: number;
    /** Enable guides */
    showGuides: boolean;
}

/**
 * Default configuration
 */
const DEFAULT_CONFIG: DragDropConfig = {
    minDragDistance: 5,
    showPreview: true,
    autoScroll: true,
    autoScrollSpeed: 10,
    autoScrollMargin: 50,
    snapToGrid: true,
    gridSize: 8,
    showGuides: true,
};

/**
 * Drag & Drop Manager - Singleton
 *
 * Manages all drag and drop operations on the canvas.
 */
export class DragDropManager {
    private static instance: DragDropManager | null = null;

    private context: DragContext = { ...DEFAULT_CONTEXT };
    private config: DragDropConfig;
    private listeners: Set<DragEventListener> = new Set();
    private autoScrollInterval: ReturnType<typeof setInterval> | null = null;

    private constructor(config: Partial<DragDropConfig> = {}) {
        this.config = { ...DEFAULT_CONFIG, ...config };
    }

    /**
     * Get singleton instance
     */
    static getInstance(config?: Partial<DragDropConfig>): DragDropManager {
        if (!DragDropManager.instance) {
            DragDropManager.instance = new DragDropManager(config);
        }
        return DragDropManager.instance;
    }

    /**
     * Reset singleton (for testing)
     */
    static resetInstance(): void {
        if (DragDropManager.instance) {
            DragDropManager.instance.cancel();
        }
        DragDropManager.instance = null;
    }

    // ============================================================================
    // Public API
    // ============================================================================

    /**
     * Get current drag context
     */
    getContext(): Readonly<DragContext> {
        return { ...this.context };
    }

    /**
     * Check if currently dragging
     */
    isDragging(): boolean {
        return this.context.state === 'dragging' || this.context.state === 'over-target';
    }

    /**
     * Start a drag operation
     */
    startDrag(source: DragSource, position: { x: number; y: number }): void {
        if (this.context.state !== 'idle') {
            this.cancel();
        }

        this.context = {
            ...DEFAULT_CONTEXT,
            state: 'pending',
            source,
            startPosition: { ...position },
            currentPosition: { ...position },
            startTime: Date.now(),
        };

        this.emit({ type: 'drag-start', context: this.context, timestamp: Date.now() });
    }

    /**
     * Update drag position
     */
    updatePosition(
        position: { x: number; y: number },
        modifiers: DragContext['modifiers']
    ): void {
        if (this.context.state === 'idle' || this.context.state === 'cancelled') {
            return;
        }

        const start = this.context.startPosition!;
        const delta = {
            x: position.x - start.x,
            y: position.y - start.y,
        };

        // Check if we should transition to dragging
        if (this.context.state === 'pending') {
            const distance = Math.sqrt(delta.x ** 2 + delta.y ** 2);
            if (distance >= this.config.minDragDistance) {
                this.context.state = 'dragging';
                this.createPreview();
            }
        }

        // Update context
        this.context.currentPosition = { ...position };
        this.context.delta = delta;
        this.context.modifiers = { ...modifiers };

        // Update preview position
        if (this.context.previewElement) {
            this.updatePreviewPosition(position);
        }

        this.emit({ type: 'drag-move', context: this.context, timestamp: Date.now() });
    }

    /**
     * Set drop target
     */
    setDropTarget(target: DropTarget | null): void {
        if (!this.isDragging()) return;

        const previousTarget = this.context.target;
        this.context.target = target;

        if (target && target.allowed) {
            this.context.state = 'over-target';
            this.emit({ type: 'drag-over', context: this.context, timestamp: Date.now() });
        } else {
            this.context.state = 'dragging';
            if (previousTarget) {
                this.emit({
                    type: 'drag-leave',
                    context: this.context,
                    timestamp: Date.now(),
                });
            }
        }
    }

    /**
     * Execute drop
     */
    drop(): DropResult | null {
        if (!this.isDragging() || !this.context.target?.allowed) {
            this.cancel();
            return null;
        }

        this.context.state = 'dropping';
        this.emit({ type: 'drop', context: this.context, timestamp: Date.now() });

        const result: DropResult = {
            success: true,
            source: this.context.source!,
            target: this.context.target,
            position: this.context.target.position,
        };

        this.cleanup();
        return result;
    }

    /**
     * Cancel drag operation
     */
    cancel(): void {
        if (this.context.state === 'idle') return;

        this.context.state = 'cancelled';
        this.emit({ type: 'cancel', context: this.context, timestamp: Date.now() });
        this.cleanup();
    }

    /**
     * Subscribe to drag events
     */
    subscribe(listener: DragEventListener): () => void {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    }

    /**
     * Update configuration
     */
    updateConfig(config: Partial<DragDropConfig>): void {
        this.config = { ...this.config, ...config };
    }

    /**
     * Get snapped position
     */
    getSnappedPosition(position: { x: number; y: number }): { x: number; y: number } {
        if (!this.config.snapToGrid) return position;

        const gridSize = this.config.gridSize;
        return {
            x: Math.round(position.x / gridSize) * gridSize,
            y: Math.round(position.y / gridSize) * gridSize,
        };
    }

    // ============================================================================
    // Private Methods
    // ============================================================================

    /**
     * Emit drag event
     */
    private emit(event: DragEvent): void {
        for (const listener of this.listeners) {
            try {
                listener(event);
            } catch (error) {
                console.error('Drag event listener error:', error);
            }
        }
    }

    /**
     * Create drag preview element
     */
    private createPreview(): void {
        if (!this.config.showPreview || !this.context.source) return;

        const source = this.context.source;
        let preview: HTMLElement;

        switch (source.type) {
            case 'palette':
                preview = this.createPalettePreview(source);
                break;
            case 'canvas':
                preview = this.createCanvasPreview(source);
                break;
            default:
                preview = this.createDefaultPreview();
        }

        preview.style.position = 'fixed';
        preview.style.pointerEvents = 'none';
        preview.style.zIndex = '10000';
        preview.style.opacity = '0.8';
        preview.classList.add('drag-preview');

        document.body.appendChild(preview);
        this.context.previewElement = preview;
    }

    /**
     * Create preview for palette items
     */
    private createPalettePreview(source: PaletteDragSource): HTMLElement {
        if (source.preview) {
            return source.preview.cloneNode(true) as HTMLElement;
        }

        const preview = document.createElement('div');
        preview.style.width = `${source.contract.defaults.width}px`;
        preview.style.height = `${source.contract.defaults.height}px`;
        preview.style.backgroundColor = '#e2e8f0';
        preview.style.border = '2px dashed #6366f1';
        preview.style.borderRadius = '8px';
        preview.style.display = 'flex';
        preview.style.alignItems = 'center';
        preview.style.justifyContent = 'center';
        preview.style.fontSize = '12px';
        preview.style.color = '#64748b';
        preview.textContent = source.contract.identity.name;

        return preview;
    }

    /**
     * Create preview for canvas nodes
     */
    private createCanvasPreview(source: CanvasDragSource): HTMLElement {
        const preview = document.createElement('div');
        preview.style.backgroundColor = '#f1f5f9';
        preview.style.border = '2px solid #6366f1';
        preview.style.borderRadius = '8px';
        preview.style.padding = '8px';
        preview.style.fontSize = '12px';
        preview.style.color = '#1e293b';

        if (source.nodeIds.length === 1) {
            preview.textContent = source.nodes[0].name;
            preview.style.width = `${source.nodes[0].transform.width}px`;
            preview.style.height = `${source.nodes[0].transform.height}px`;
        } else {
            preview.textContent = `${source.nodeIds.length} items`;
            preview.style.width = '100px';
            preview.style.height = '60px';
        }

        return preview;
    }

    /**
     * Create default preview
     */
    private createDefaultPreview(): HTMLElement {
        const preview = document.createElement('div');
        preview.style.width = '100px';
        preview.style.height = '60px';
        preview.style.backgroundColor = '#e2e8f0';
        preview.style.border = '2px dashed #94a3b8';
        preview.style.borderRadius = '8px';
        return preview;
    }

    /**
     * Update preview position
     */
    private updatePreviewPosition(position: { x: number; y: number }): void {
        if (!this.context.previewElement) return;

        const preview = this.context.previewElement;
        preview.style.left = `${position.x - preview.offsetWidth / 2}px`;
        preview.style.top = `${position.y - preview.offsetHeight / 2}px`;
    }

    /**
     * Cleanup after drag ends
     */
    private cleanup(): void {
        // Remove preview
        if (this.context.previewElement) {
            this.context.previewElement.remove();
        }

        // Stop auto-scroll
        if (this.autoScrollInterval) {
            clearInterval(this.autoScrollInterval);
            this.autoScrollInterval = null;
        }

        // Reset context
        this.context = { ...DEFAULT_CONTEXT };

        this.emit({ type: 'drag-end', context: this.context, timestamp: Date.now() });
    }
}

// ============================================================================
// Drop Result
// ============================================================================

/**
 * Result of a drop operation
 */
export interface DropResult {
    success: boolean;
    source: DragSource;
    target: DropTarget;
    position: { x: number; y: number };
    error?: string;
}

// ============================================================================
// Convenience Functions
// ============================================================================

/**
 * Get the global drag drop manager
 */
export function getDragDropManager(
    config?: Partial<DragDropConfig>
): DragDropManager {
    return DragDropManager.getInstance(config);
}

/**
 * Check if currently dragging
 */
export function isDragging(): boolean {
    return getDragDropManager().isDragging();
}

/**
 * Get current drag context
 */
export function getDragContext(): Readonly<DragContext> {
    return getDragDropManager().getContext();
}
