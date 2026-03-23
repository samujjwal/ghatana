/**
 * Highlighter Element - Semi-transparent marker strokes
 * 
 * @doc.type class
 * @doc.purpose Highlighter/marker tool strokes with transparency
 * @doc.layer elements
 * @doc.pattern Element
 * 
 * Implements AFFiNE-style highlighter with:
 * - Semi-transparent strokes
 * - Pressure sensitivity support
 * - Rounded caps for marker effect
 */

import { BaseElementProps, CanvasElementType, Point } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";

export interface HighlighterProps extends BaseElementProps {
    /** Stroke points with optional pressure */
    points: Array<{ x: number; y: number; pressure?: number }>;
    /** Highlighter color (will be rendered semi-transparent) */
    color: string;
    /** Line width */
    lineWidth: number;
    /** Opacity (0-1) */
    opacity?: number;
}

/**
 * Highlighter Element
 * 
 * Renders semi-transparent marker-style strokes.
 * Different from BrushElement in:
 * - Always semi-transparent
 * - Uses square line cap for marker effect
 * - Thicker default stroke
 */
export class HighlighterElement extends CanvasElement {
    public points: Array<{ x: number; y: number; pressure?: number }>;
    public color: string;
    public lineWidth: number;
    public opacity: number;

    constructor(props: HighlighterProps) {
        super(props);
        this.points = props.points || [];
        this.color = props.color || '#ffeb3b';
        this.lineWidth = props.lineWidth || 20;
        this.opacity = props.opacity ?? 0.4;
    }

    get type(): CanvasElementType | 'highlighter' {
        return 'highlighter' as unknown as CanvasElementType;
    }

    render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
        if (this.points.length < 2) return;

        ctx.save();
        this.applyTransform(ctx);

        // Set highlighter style
        ctx.globalAlpha = this.opacity;
        ctx.strokeStyle = this.color;
        ctx.lineWidth = this.lineWidth / zoom;
        ctx.lineCap = 'square'; // Square cap for marker effect
        ctx.lineJoin = 'round';
        ctx.globalCompositeOperation = 'multiply'; // Blend mode for highlighter effect

        // Draw stroke
        ctx.beginPath();
        ctx.moveTo(this.points[0].x, this.points[0].y);

        for (let i = 1; i < this.points.length; i++) {
            const point = this.points[i];
            const prevPoint = this.points[i - 1];

            // Optional: vary width based on pressure
            if (point.pressure !== undefined && prevPoint.pressure !== undefined) {
                const avgPressure = (point.pressure + prevPoint.pressure) / 2;
                ctx.lineWidth = (this.lineWidth * avgPressure) / zoom;
            }

            ctx.lineTo(point.x, point.y);
        }

        ctx.stroke();

        ctx.restore();
    }

    includesPoint(x: number, y: number): boolean {
        const tolerance = this.lineWidth / 2 + 4;

        for (let i = 1; i < this.points.length; i++) {
            const p1 = this.points[i - 1];
            const p2 = this.points[i];
            const distance = this.pointToLineDistance({ x, y }, p1, p2);
            if (distance <= tolerance) {
                return true;
            }
        }

        return false;
    }

    private pointToLineDistance(
        point: Point,
        lineStart: Point,
        lineEnd: Point
    ): number {
        const A = point.x - lineStart.x;
        const B = point.y - lineStart.y;
        const C = lineEnd.x - lineStart.x;
        const D = lineEnd.y - lineStart.y;

        const dot = A * C + B * D;
        const lenSq = C * C + D * D;
        let param = -1;

        if (lenSq !== 0) {
            param = dot / lenSq;
        }

        let xx, yy;

        if (param < 0) {
            xx = lineStart.x;
            yy = lineStart.y;
        } else if (param > 1) {
            xx = lineEnd.x;
            yy = lineEnd.y;
        } else {
            xx = lineStart.x + param * C;
            yy = lineStart.y + param * D;
        }

        const dx = point.x - xx;
        const dy = point.y - yy;

        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Add point to highlighter stroke
     */
    addPoint(point: { x: number; y: number; pressure?: number }): void {
        this.points.push(point);
        this.updateBounds();
    }

    /**
     * Update element bounds based on points
     */
    private updateBounds(): void {
        if (this.points.length === 0) return;

        const padding = this.lineWidth / 2;
        let minX = Infinity, minY = Infinity;
        let maxX = -Infinity, maxY = -Infinity;

        for (const point of this.points) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }

        this.xywh = JSON.stringify([
            minX - padding,
            minY - padding,
            maxX - minX + padding * 2,
            maxY - minY + padding * 2,
        ]);
    }

    /**
     * Get SVG path commands for export
     */
    getSvgPath(): string {
        if (this.points.length < 2) return '';

        let path = `M ${this.points[0].x} ${this.points[0].y}`;
        for (let i = 1; i < this.points.length; i++) {
            path += ` L ${this.points[i].x} ${this.points[i].y}`;
        }
        return path;
    }
}
