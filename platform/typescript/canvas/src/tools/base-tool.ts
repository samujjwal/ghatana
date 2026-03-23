/**
 * Base Tool - Abstract base class for all canvas tools
 * 
 * @doc.type class
 * @doc.purpose Abstract base class providing common tool functionality
 * @doc.layer tools
 * @doc.pattern Template
 * 
 * Provides common functionality for all tools including:
 * - Pointer event handling
 * - Coordinate transformation
 * - Activation/deactivation lifecycle
 */

import type { Point, ToolOptions } from "../types/index.js";
import type { CanvasElement } from "../elements/base.js";

/**
 * Abstract base class for all canvas tools
 */
export abstract class BaseTool {
    protected isActive: boolean = false;
    protected startPoint: Point | null = null;
    protected currentElement: CanvasElement | null = null;

    constructor(protected options: ToolOptions) { }

    /**
     * Handle pointer down event
     */
    abstract onPointerDown(event: PointerEvent, canvas: HTMLCanvasElement): void;

    /**
     * Handle pointer move event
     */
    abstract onPointerMove(event: PointerEvent, canvas: HTMLCanvasElement): void;

    /**
     * Handle pointer up event
     */
    abstract onPointerUp(event: PointerEvent, canvas: HTMLCanvasElement): void;

    /**
     * Get cursor style for this tool
     */
    getCursor(): string {
        return 'default';
    }

    /**
     * Activate the tool
     */
    activate(): void {
        this.isActive = true;
    }

    /**
     * Deactivate the tool
     */
    deactivate(): void {
        this.isActive = false;
        this.cleanup();
    }

    /**
     * Cleanup tool state
     */
    protected cleanup(): void {
        this.startPoint = null;
        this.currentElement = null;
    }

    /**
     * Get mouse position in canvas coordinates
     * Handles viewport transformation if available
     */
    protected getMousePosition(event: PointerEvent, canvas: HTMLCanvasElement): Point {
        const rect = canvas.getBoundingClientRect();
        const screenPoint = {
            x: event.clientX - rect.left,
            y: event.clientY - rect.top,
        };

        // If canvas has viewport attached, convert to canvas coordinates
        const viewport = (canvas as unknown as Record<string, unknown>).__viewport as Record<string, unknown> | undefined;
        if (viewport && typeof viewport.screenToCanvas === 'function') {
            return (viewport.screenToCanvas as (p: { x: number; y: number }) => Point)(screenPoint);
        }

        return screenPoint;
    }

    /**
     * Check if tool is currently active
     */
    isToolActive(): boolean {
        return this.isActive;
    }

    /**
     * Get tool options
     */
    getOptions(): ToolOptions {
        return { ...this.options };
    }

    /**
     * Update tool options
     */
    setOptions(options: Partial<ToolOptions>): void {
        this.options = { ...this.options, ...options };
    }
}
