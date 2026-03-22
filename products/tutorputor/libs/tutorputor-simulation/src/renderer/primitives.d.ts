/**
 * Rendering Primitives
 *
 * @doc.type module
 * @doc.purpose Provide low-level drawing primitives for simulation rendering
 * @doc.layer product
 * @doc.pattern Utility
 */
import type { LineStyle, FillStyle, TextStyle, ArrowHeadStyle } from "./types";
/**
 * Draw a rectangle.
 */
export declare function drawRect(ctx: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, fill?: FillStyle, stroke?: LineStyle, cornerRadius?: number): void;
/**
 * Draw a rounded rectangle.
 */
export declare function drawRoundedRect(ctx: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, radius: number, fill?: FillStyle, stroke?: LineStyle): void;
/**
 * Draw a circle.
 */
export declare function drawCircle(ctx: CanvasRenderingContext2D, x: number, y: number, radius: number, fill?: FillStyle, stroke?: LineStyle): void;
/**
 * Draw an ellipse.
 */
export declare function drawEllipse(ctx: CanvasRenderingContext2D, x: number, y: number, radiusX: number, radiusY: number, fill?: FillStyle, stroke?: LineStyle, rotation?: number): void;
/**
 * Draw a polygon.
 */
export declare function drawPolygon(ctx: CanvasRenderingContext2D, points: Array<{
    x: number;
    y: number;
}>, fill?: FillStyle, stroke?: LineStyle): void;
/**
 * Draw a regular polygon (hexagon, pentagon, etc.).
 */
export declare function drawRegularPolygon(ctx: CanvasRenderingContext2D, x: number, y: number, radius: number, sides: number, fill?: FillStyle, stroke?: LineStyle, rotation?: number): void;
/**
 * Draw a diamond shape.
 */
export declare function drawDiamond(ctx: CanvasRenderingContext2D, x: number, y: number, width: number, height: number, fill?: FillStyle, stroke?: LineStyle): void;
/**
 * Draw a line.
 */
export declare function drawLine(ctx: CanvasRenderingContext2D, x1: number, y1: number, x2: number, y2: number, style: LineStyle): void;
/**
 * Draw a curved line (quadratic bezier).
 */
export declare function drawQuadraticCurve(ctx: CanvasRenderingContext2D, x1: number, y1: number, cpX: number, cpY: number, x2: number, y2: number, style: LineStyle): void;
/**
 * Draw a curved line (cubic bezier).
 */
export declare function drawBezierCurve(ctx: CanvasRenderingContext2D, x1: number, y1: number, cp1X: number, cp1Y: number, cp2X: number, cp2Y: number, x2: number, y2: number, style: LineStyle): void;
/**
 * Draw an arrow.
 */
export declare function drawArrow(ctx: CanvasRenderingContext2D, x1: number, y1: number, x2: number, y2: number, style: LineStyle, headStyle?: ArrowHeadStyle): void;
/**
 * Draw a double-headed arrow (bidirectional).
 */
export declare function drawBidirectionalArrow(ctx: CanvasRenderingContext2D, x1: number, y1: number, x2: number, y2: number, style: LineStyle, headStyle?: ArrowHeadStyle): void;
/**
 * Draw text.
 */
export declare function drawText(ctx: CanvasRenderingContext2D, text: string, x: number, y: number, style: TextStyle): void;
/**
 * Measure text width.
 */
export declare function measureText(ctx: CanvasRenderingContext2D, text: string, style: TextStyle): number;
/**
 * Draw a spring between two points.
 */
export declare function drawSpring(ctx: CanvasRenderingContext2D, x1: number, y1: number, x2: number, y2: number, coils: number, amplitude: number, style: LineStyle): void;
/**
 * Draw a vector (arrow with magnitude label).
 */
export declare function drawVector(ctx: CanvasRenderingContext2D, x: number, y: number, magnitude: number, angle: number, style: LineStyle, label?: string, scale?: number): void;
/**
 * Draw a bond (for chemistry).
 */
export declare function drawBond(ctx: CanvasRenderingContext2D, x1: number, y1: number, x2: number, y2: number, bondOrder: 1 | 2 | 3 | 1.5, style: LineStyle): void;
/**
 * Draw a glow/highlight effect around a shape.
 */
export declare function applyGlow(ctx: CanvasRenderingContext2D, color: string, blur: number): void;
/**
 * Clear glow/highlight effect.
 */
export declare function clearGlow(ctx: CanvasRenderingContext2D): void;
//# sourceMappingURL=primitives.d.ts.map