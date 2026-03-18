/**
 * Connector Tool - Tool for creating connections between elements
 * 
 * @doc.type class
 * @doc.purpose Tool for drawing connectors between canvas elements
 * @doc.layer tools
 * @doc.pattern Tool
 * 
 * Implements AFFiNE-style connector creation with:
 * - Source element detection
 * - Target element snapping
 * - Multiple connector modes (straight, orthogonal, curved)
 * - Visual feedback during drawing
 */

import { BaseTool } from "./base-tool.js";
import { ConnectorElement, type ConnectorProps } from "../elements/connector.js";
import { connectionManager, ConnectorMode } from "../core/connection-manager.js";
import type { Point, ToolOptions } from "../types/index.js";
import { nanoid } from "nanoid";

export interface ConnectorToolOptions extends ToolOptions {
    mode?: ConnectorMode;
    strokeColor?: string;
    strokeWidth?: number;
    arrowStyle?: 'none' | 'arrow' | 'diamond';
}

/**
 * Connector Tool
 * 
 * Creates connections between elements. Workflow:
 * 1. Click on source element (or free point)
 * 2. Drag to target element (or free point)
 * 3. Release to create connector
 */
export class ConnectorTool extends BaseTool {
    private mode: ConnectorMode = ConnectorMode.Straight;
    private strokeColor: string = '#1e40af';
    private strokeWidth: number = 2;
    private arrowStyle: 'none' | 'arrow' | 'diamond' = 'arrow';

    private sourcePoint: Point | null = null;
    private sourceElementId: string | null = null;
    private previewConnector: ConnectorElement | null = null;
    private isDrawing = false;

    constructor(options: ConnectorToolOptions) {
        super(options);
        this.mode = options.mode ?? ConnectorMode.Straight;
        this.strokeColor = options.strokeColor ?? '#1e40af';
        this.strokeWidth = options.strokeWidth ?? 2;
        this.arrowStyle = options.arrowStyle ?? 'arrow';
    }

    onPointerDown(event: PointerEvent, canvas: HTMLCanvasElement): void {
        const point = this.getMousePosition(event, canvas);
        this.sourcePoint = point;
        this.isDrawing = true;

        // Check if clicking on an element
        canvas.dispatchEvent(
            new CustomEvent('connector-source-check', {
                detail: {
                    point,
                    callback: (elementId: string | null) => {
                        this.sourceElementId = elementId;
                    }
                },
            })
        );

        // Create preview connector
        this.createPreviewConnector(point, point);

        // Emit creation event for preview
        canvas.dispatchEvent(
            new CustomEvent('element-create', {
                detail: { element: this.previewConnector, isPreview: true },
            })
        );
    }

    onPointerMove(event: PointerEvent, canvas: HTMLCanvasElement): void {
        if (!this.isDrawing || !this.sourcePoint || !this.previewConnector) return;

        const point = this.getMousePosition(event, canvas);

        // Update preview connector endpoint
        this.previewConnector.endPoint = point;

        // Recalculate bounds
        const minX = Math.min(this.sourcePoint.x, point.x);
        const minY = Math.min(this.sourcePoint.y, point.y);
        const maxX = Math.max(this.sourcePoint.x, point.x);
        const maxY = Math.max(this.sourcePoint.y, point.y);
        this.previewConnector.xywh = JSON.stringify([minX, minY, maxX - minX || 1, maxY - minY || 1]);

        // Emit update event
        canvas.dispatchEvent(
            new CustomEvent('element-update', {
                detail: { element: this.previewConnector },
            })
        );

        // Check for potential target element
        canvas.dispatchEvent(
            new CustomEvent('connector-target-check', {
                detail: {
                    point,
                    sourceId: this.sourceElementId,
                },
            })
        );
    }

    onPointerUp(event: PointerEvent, canvas: HTMLCanvasElement): void {
        if (!this.isDrawing || !this.sourcePoint) {
            this.cleanup();
            return;
        }

        const point = this.getMousePosition(event, canvas);
        let targetElementId: string | null = null;

        // Check if releasing on an element
        canvas.dispatchEvent(
            new CustomEvent('connector-target-check', {
                detail: {
                    point,
                    sourceId: this.sourceElementId,
                    callback: (elementId: string | null) => {
                        targetElementId = elementId;
                    }
                },
            })
        );

        // Remove preview connector
        if (this.previewConnector) {
            canvas.dispatchEvent(
                new CustomEvent('element-remove-preview', {
                    detail: { element: this.previewConnector },
                })
            );
        }

        // Create final connector if there's meaningful distance
        const distance = Math.sqrt(
            Math.pow(point.x - this.sourcePoint.x, 2) +
            Math.pow(point.y - this.sourcePoint.y, 2)
        );

        if (distance > 10) {
            // Create the actual connector through connection manager
            const connector = connectionManager.createConnector({
                source: this.sourceElementId
                    ? { id: this.sourceElementId }
                    : { position: this.sourcePoint },
                target: targetElementId
                    ? { id: targetElementId }
                    : { position: point },
                mode: this.mode,
                strokeColor: this.strokeColor,
                strokeWidth: this.strokeWidth,
                arrowStyle: this.arrowStyle,
            });

            if (connector) {
                // Emit finalize event
                canvas.dispatchEvent(
                    new CustomEvent('element-finalize', {
                        detail: { element: connector },
                    })
                );
            }
        }

        this.cleanup();
    }

    /**
     * Create preview connector for visual feedback
     */
    private createPreviewConnector(start: Point, end: Point): void {
        const connectorType = this.mode === ConnectorMode.Straight ? 'straight'
            : this.mode === ConnectorMode.Orthogonal ? 'orthogonal'
                : 'curved';

        const props: ConnectorProps = {
            id: `preview-${nanoid()}`,
            xywh: JSON.stringify([start.x, start.y, 1, 1]),
            index: Date.now().toString(),
            startPoint: start,
            endPoint: end,
            strokeColor: this.strokeColor,
            strokeWidth: this.strokeWidth,
            connectorType,
            arrowStyle: this.arrowStyle,
        };

        this.previewConnector = new ConnectorElement(props);
    }

    getCursor(): string {
        return 'crosshair';
    }

    protected cleanup(): void {
        super.cleanup();
        this.sourcePoint = null;
        this.sourceElementId = null;
        this.previewConnector = null;
        this.isDrawing = false;
    }

    /**
     * Set connector mode
     */
    setMode(mode: ConnectorMode): void {
        this.mode = mode;
    }

    /**
     * Set stroke color
     */
    setStrokeColor(color: string): void {
        this.strokeColor = color;
    }

    /**
     * Set stroke width
     */
    setStrokeWidth(width: number): void {
        this.strokeWidth = width;
    }

    /**
     * Set arrow style
     */
    setArrowStyle(style: 'none' | 'arrow' | 'diamond'): void {
        this.arrowStyle = style;
    }
}
