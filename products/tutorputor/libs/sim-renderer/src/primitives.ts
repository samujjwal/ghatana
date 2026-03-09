/**
 * Rendering Primitives
 *
 * @doc.type module
 * @doc.purpose Provide low-level drawing primitives for simulation rendering
 * @doc.layer product
 * @doc.pattern Utility
 */

import type { LineStyle, FillStyle, TextStyle, ArrowHeadStyle, RenderContext } from "./types";

// =============================================================================
// Shape Primitives
// =============================================================================

/**
 * Draw a rectangle.
 */
export function drawRect(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    width: number,
    height: number,
    fill?: FillStyle,
    stroke?: LineStyle,
    cornerRadius?: number
): void {
    if (cornerRadius && cornerRadius > 0) {
        drawRoundedRect(ctx, x, y, width, height, cornerRadius, fill, stroke);
        return;
    }

    if (fill) {
        ctx.globalAlpha = fill.opacity ?? 1;
        ctx.fillStyle = fill.color;
        ctx.fillRect(x - width / 2, y - height / 2, width, height);
    }

    if (stroke) {
        ctx.globalAlpha = 1;
        ctx.strokeStyle = stroke.color;
        ctx.lineWidth = stroke.width;
        if (stroke.dash) ctx.setLineDash(stroke.dash);
        ctx.lineCap = stroke.cap ?? "butt";
        ctx.lineJoin = stroke.join ?? "miter";
        ctx.strokeRect(x - width / 2, y - height / 2, width, height);
        ctx.setLineDash([]);
    }

    ctx.globalAlpha = 1;
}

/**
 * Draw a rounded rectangle.
 */
export function drawRoundedRect(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    width: number,
    height: number,
    radius: number,
    fill?: FillStyle | string,
    stroke?: LineStyle | string,
    strokeWidth?: number
): void {
    const fillStyle: FillStyle | undefined = typeof fill === 'string' ? { color: fill } : fill;
    const strokeStyle: LineStyle | undefined = typeof stroke === 'string'
        ? { color: stroke, width: strokeWidth ?? 1 }
        : stroke;
    const fill2 = fillStyle;
    const stroke2 = strokeStyle;
    const left = x - width / 2;
    const top = y - height / 2;
    const r = Math.min(radius, width / 2, height / 2);

    ctx.beginPath();
    ctx.moveTo(left + r, top);
    ctx.lineTo(left + width - r, top);
    ctx.quadraticCurveTo(left + width, top, left + width, top + r);
    ctx.lineTo(left + width, top + height - r);
    ctx.quadraticCurveTo(left + width, top + height, left + width - r, top + height);
    ctx.lineTo(left + r, top + height);
    ctx.quadraticCurveTo(left, top + height, left, top + height - r);
    ctx.lineTo(left, top + r);
    ctx.quadraticCurveTo(left, top, left + r, top);
    ctx.closePath();

    if (fill2) {
        ctx.globalAlpha = fill2.opacity ?? 1;
        ctx.fillStyle = fill2.color;
        ctx.fill();
    }

    if (stroke2) {
        ctx.globalAlpha = 1;
        ctx.strokeStyle = stroke2.color;
        ctx.lineWidth = stroke2.width;
        if (stroke2.dash) ctx.setLineDash(stroke2.dash);
        ctx.stroke();
        ctx.setLineDash([]);
    }

    ctx.globalAlpha = 1;
}

/**
 * Draw a circle.
 */
export function drawCircle(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    radius: number,
    fill?: FillStyle | string,
    stroke?: LineStyle | string,
    strokeWidth?: number
): void {
    // Normalize legacy string-based API
    const fillStyle: FillStyle | undefined = typeof fill === 'string' ? { color: fill } : fill;
    const strokeStyle: LineStyle | undefined = typeof stroke === 'string'
        ? { color: stroke, width: strokeWidth ?? 1 }
        : stroke;

    ctx.beginPath();
    ctx.arc(x, y, radius, 0, Math.PI * 2);

    if (fillStyle) {
        ctx.globalAlpha = fillStyle.opacity ?? 1;
        ctx.fillStyle = fillStyle.color;
        ctx.fill();
    }

    if (strokeStyle) {
        ctx.globalAlpha = 1;
        ctx.strokeStyle = strokeStyle.color;
        ctx.lineWidth = strokeStyle.width;
        if (strokeStyle.dash) ctx.setLineDash(strokeStyle.dash);
        ctx.stroke();
        ctx.setLineDash([]);
    }

    ctx.globalAlpha = 1;
}

/**
 * Draw an ellipse.
 */
export function drawEllipse(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    radiusX: number,
    radiusY: number,
    fill?: FillStyle,
    stroke?: LineStyle,
    rotation = 0
): void {
    ctx.beginPath();
    ctx.ellipse(x, y, radiusX, radiusY, rotation, 0, Math.PI * 2);

    if (fill) {
        ctx.globalAlpha = fill.opacity ?? 1;
        ctx.fillStyle = fill.color;
        ctx.fill();
    }

    if (stroke) {
        ctx.globalAlpha = 1;
        ctx.strokeStyle = stroke.color;
        ctx.lineWidth = stroke.width;
        if (stroke.dash) ctx.setLineDash(stroke.dash);
        ctx.stroke();
        ctx.setLineDash([]);
    }

    ctx.globalAlpha = 1;
}

/**
 * Draw a polygon.
 */
export function drawPolygon(
    ctx: CanvasRenderingContext2D,
    points: Array<{ x: number; y: number }>,
    fill?: FillStyle,
    stroke?: LineStyle
): void {
    if (points.length < 3) return;

    ctx.beginPath();
    ctx.moveTo(points[0]!.x, points[0]!.y);
    for (let i = 1; i < points.length; i++) {
        ctx.lineTo(points[i]!.x, points[i]!.y);
    }
    ctx.closePath();

    if (fill) {
        ctx.globalAlpha = fill.opacity ?? 1;
        ctx.fillStyle = fill.color;
        ctx.fill();
    }

    if (stroke) {
        ctx.globalAlpha = 1;
        ctx.strokeStyle = stroke.color;
        ctx.lineWidth = stroke.width;
        if (stroke.dash) ctx.setLineDash(stroke.dash);
        ctx.stroke();
        ctx.setLineDash([]);
    }

    ctx.globalAlpha = 1;
}

/**
 * Draw a regular polygon (hexagon, pentagon, etc.).
 */
export function drawRegularPolygon(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    radius: number,
    sides: number,
    fill?: FillStyle,
    stroke?: LineStyle,
    rotation = -Math.PI / 2
): void {
    const points: Array<{ x: number; y: number }> = [];
    const angleStep = (Math.PI * 2) / sides;

    for (let i = 0; i < sides; i++) {
        const angle = rotation + i * angleStep;
        points.push({
            x: x + radius * Math.cos(angle),
            y: y + radius * Math.sin(angle),
        });
    }

    drawPolygon(ctx, points, fill, stroke);
}

/**
 * Draw a diamond shape.
 */
export function drawDiamond(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    widthOrSize: number,
    heightOrFill?: number | string,
    fillOrStroke?: FillStyle | string,
    strokeOrWidth?: LineStyle | number
): void {
    let hw: number, hh: number;
    let fill: FillStyle | undefined;
    let stroke: LineStyle | undefined;
    if (typeof heightOrFill === 'string') {
        // Legacy: (ctx, x, y, size, fillColor, strokeColor, strokeWidth)
        hw = widthOrSize / 2;
        hh = widthOrSize / 2;
        fill = { color: heightOrFill };
        stroke = typeof fillOrStroke === 'string'
            ? { color: fillOrStroke, width: (strokeOrWidth as number) ?? 1 }
            : undefined;
    } else {
        hw = widthOrSize / 2;
        hh = (heightOrFill ?? widthOrSize) / 2;
        fill = fillOrStroke as FillStyle | undefined;
        stroke = strokeOrWidth as LineStyle | undefined;
    }
    const points = [
        { x: x, y: y - hh },
        { x: x + hw, y: y },
        { x: x, y: y + hh },
        { x: x - hw, y: y },
    ];
    drawPolygon(ctx, points, fill, stroke);
}

// =============================================================================
// Line Primitives
// =============================================================================

/**
 * Draw a line.
 */
export function drawLine(
    ctx: CanvasRenderingContext2D,
    x1: number,
    y1: number,
    x2: number,
    y2: number,
    style: LineStyle | string,
    lineWidth?: number
): void {
    const s: LineStyle = typeof style === 'string'
        ? { color: style, width: lineWidth ?? 1 }
        : style;
    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.lineTo(x2, y2);
    ctx.strokeStyle = s.color;
    ctx.lineWidth = s.width;
    ctx.lineCap = s.cap ?? "round";
    ctx.lineJoin = s.join ?? "round";
    if (s.dash) ctx.setLineDash(s.dash);
    ctx.stroke();
    ctx.setLineDash([]);
}

/**
 * Draw a curved line (quadratic bezier).
 */
export function drawQuadraticCurve(
    ctx: CanvasRenderingContext2D,
    x1: number,
    y1: number,
    cpX: number,
    cpY: number,
    x2: number,
    y2: number,
    style: LineStyle | string,
    lineWidth?: number
): void {
    const s: LineStyle = typeof style === 'string' ? { color: style, width: lineWidth ?? 1 } : style;
    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.quadraticCurveTo(cpX, cpY, x2, y2);
    ctx.strokeStyle = s.color;
    ctx.lineWidth = s.width;
    ctx.lineCap = s.cap ?? "round";
    if (s.dash) ctx.setLineDash(s.dash);
    ctx.stroke();
    ctx.setLineDash([]);
}

/**
 * Draw a curved line (cubic bezier).
 */
export function drawBezierCurve(
    ctx: CanvasRenderingContext2D,
    x1: number,
    y1: number,
    cp1X: number,
    cp1Y: number,
    cp2X: number,
    cp2Y: number,
    x2: number,
    y2: number,
    style: LineStyle | string,
    lineWidth?: number
): void {
    const s: LineStyle = typeof style === 'string' ? { color: style, width: lineWidth ?? 1 } : style;
    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.bezierCurveTo(cp1X, cp1Y, cp2X, cp2Y, x2, y2);
    ctx.strokeStyle = s.color;
    ctx.lineWidth = s.width;
    ctx.lineCap = s.cap ?? "round";
    if (s.dash) ctx.setLineDash(s.dash);
    ctx.stroke();
    ctx.setLineDash([]);
}

/**
 * Draw an arrow.
 */
export function drawArrow(
    ctx: CanvasRenderingContext2D,
    x1: number,
    y1: number,
    x2: number,
    y2: number,
    style: LineStyle | string,
    headStyleOrWidth?: ArrowHeadStyle | number,
    headSizeOrCurved?: number | boolean,
    curved?: boolean
): void {
    const s: LineStyle = typeof style === 'string'
        ? { color: style, width: typeof headStyleOrWidth === 'number' ? headStyleOrWidth : 1 }
        : style;
    const headStyle: ArrowHeadStyle = (typeof headStyleOrWidth === 'object' && headStyleOrWidth !== null)
        ? headStyleOrWidth
        : { size: (typeof headSizeOrCurved === 'number' ? headSizeOrCurved : 10), angle: Math.PI / 6, filled: true };
    const isCurved = typeof headSizeOrCurved === 'boolean' ? headSizeOrCurved : (curved ?? false);
    void isCurved; // curved arrows not yet implemented in legacy path
    // Draw the line
    drawLine(ctx, x1, y1, x2, y2, s);

    // Draw the arrow head
    const arrowAngle = Math.atan2(y2 - y1, x2 - x1);
    const headSize2 = headStyle.size;
    const headAngle2 = headStyle.angle;

    const headX1 = x2 - headSize2 * Math.cos(arrowAngle - headAngle2);
    const headY1 = y2 - headSize2 * Math.sin(arrowAngle - headAngle2);
    const headX2 = x2 - headSize2 * Math.cos(arrowAngle + headAngle2);
    const headY2 = y2 - headSize2 * Math.sin(arrowAngle + headAngle2);

    ctx.beginPath();
    ctx.moveTo(x2, y2);
    ctx.lineTo(headX1, headY1);
    if (headStyle.filled) {
        ctx.lineTo(headX2, headY2);
        ctx.closePath();
        ctx.fillStyle = headStyle.color ?? s.color;
        ctx.fill();
    } else {
        ctx.moveTo(x2, y2);
        ctx.lineTo(headX2, headY2);
        ctx.strokeStyle = headStyle.color ?? s.color;
        ctx.lineWidth = s.width;
        ctx.stroke();
    }
}

/**
 * Draw a double-headed arrow (bidirectional).
 */
export function drawBidirectionalArrow(
    ctx: CanvasRenderingContext2D,
    x1: number,
    y1: number,
    x2: number,
    y2: number,
    style: LineStyle | string,
    headStyleOrWidth?: ArrowHeadStyle | number
): void {
    const s: LineStyle = typeof style === 'string'
        ? { color: style, width: typeof headStyleOrWidth === 'number' ? headStyleOrWidth : 1 }
        : style;
    const headStyle: ArrowHeadStyle = (typeof headStyleOrWidth === 'object' && headStyleOrWidth !== null)
        ? headStyleOrWidth
        : { size: 10, angle: Math.PI / 6, filled: true };
    // Draw the line
    drawLine(ctx, x1, y1, x2, y2, s);

    const angle = Math.atan2(y2 - y1, x2 - x1);
    const headSize = headStyle.size;
    const headAngle = headStyle.angle;

    // Arrow head at end
    const endX1 = x2 - headSize * Math.cos(angle - headAngle);
    const endY1 = y2 - headSize * Math.sin(angle - headAngle);
    const endX2 = x2 - headSize * Math.cos(angle + headAngle);
    const endY2 = y2 - headSize * Math.sin(angle + headAngle);

    // Arrow head at start
    const startX1 = x1 + headSize * Math.cos(angle - headAngle);
    const startY1 = y1 + headSize * Math.sin(angle - headAngle);
    const startX2 = x1 + headSize * Math.cos(angle + headAngle);
    const startY2 = y1 + headSize * Math.sin(angle + headAngle);

    const fillColor = headStyle.color ?? s.color;

    // End arrow
    ctx.beginPath();
    ctx.moveTo(x2, y2);
    ctx.lineTo(endX1, endY1);
    ctx.lineTo(endX2, endY2);
    ctx.closePath();
    ctx.fillStyle = fillColor;
    ctx.fill();

    // Start arrow
    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.lineTo(startX1, startY1);
    ctx.lineTo(startX2, startY2);
    ctx.closePath();
    ctx.fillStyle = fillColor;
    ctx.fill();
}

// =============================================================================
// Text Primitives
// =============================================================================

/**
 * Draw text.
 */
export function drawText(
    ctx: CanvasRenderingContext2D,
    text: string,
    x: number,
    y: number,
    styleOrColor: TextStyle | string,
    fontStr?: string,
    alignStr?: CanvasTextAlign
): void {
    // Normalize legacy string-based API: (ctx, text, x, y, '#color', 'NNpx family', 'align')
    let style: TextStyle;
    if (typeof styleOrColor === 'string') {
        const boldMatch = fontStr?.match(/bold\s+(\d+)px\s+(.+)/);
        const normalMatch = fontStr?.match(/(\d+)px\s+(.+)/);
        if (boldMatch) {
            style = { color: styleOrColor, fontSize: parseInt(boldMatch[1]!), fontFamily: boldMatch[2]!, fontWeight: 'bold', align: alignStr };
        } else if (normalMatch) {
            style = { color: styleOrColor, fontSize: parseInt(normalMatch[1]!), fontFamily: normalMatch[2]!, align: alignStr };
        } else {
            style = { color: styleOrColor, fontSize: 12, align: alignStr };
        }
    } else {
        style = styleOrColor;
    }
    ctx.fillStyle = style.color;
    ctx.font = `${style.fontWeight ?? 'normal'} ${style.fontSize}px ${style.fontFamily ?? 'Inter, sans-serif'}`;
    ctx.textAlign = style.align ?? 'center';
    ctx.textBaseline = style.baseline ?? 'middle';
    ctx.fillText(text, x, y);
}

/**
 * Measure text width.
 */
export function measureText(
    ctx: CanvasRenderingContext2D,
    text: string,
    style: TextStyle
): number {
    ctx.font = `${style.fontWeight ?? "normal"} ${style.fontSize}px ${style.fontFamily ?? "Inter, sans-serif"}`;
    return ctx.measureText(text).width;
}

// =============================================================================
// Special Primitives
// =============================================================================

/**
 * Draw a spring between two points.
 */
export function drawSpring(
    ctx: CanvasRenderingContext2D,
    x1: number,
    y1: number,
    x2: number,
    y2: number,
    coils: number,
    amplitude: number,
    style: LineStyle | string,
    lineWidth?: number
): void {
    const s: LineStyle = typeof style === 'string' ? { color: style, width: lineWidth ?? 1 } : style;
    const dx = x2 - x1;
    const dy = y2 - y1;
    const length = Math.sqrt(dx * dx + dy * dy);
    const angle = Math.atan2(dy, dx);

    const coilLength = length / coils;
    void coilLength; // reserved for future variable-pitch springs
    const perpAngle = angle + Math.PI / 2;

    ctx.beginPath();
    ctx.moveTo(x1, y1);

    for (let i = 0; i <= coils; i++) {
        const t = i / coils;
        const baseX = x1 + dx * t;
        const baseY = y1 + dy * t;

        // Zigzag pattern
        if (i > 0 && i < coils) {
            const offset = (i % 2 === 0 ? 1 : -1) * amplitude;
            const springX = baseX + offset * Math.cos(perpAngle);
            const springY = baseY + offset * Math.sin(perpAngle);
            ctx.lineTo(springX, springY);
        } else {
            ctx.lineTo(baseX, baseY);
        }
    }

    const sStyle = s;
    ctx.strokeStyle = sStyle.color;
    ctx.lineWidth = sStyle.width;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.stroke();
}

/**
 * Draw a vector (arrow with magnitude label).
 * Supports new API: (ctx, x, y, magnitude, angle, style, label?, scale?)
 * Supports legacy API: (ctx, x, y, dx, dy, color, width, label?)
 * When style is a string, dx/dy are used as pixel offsets from (x,y).
 */
export function drawVector(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    magnitudeOrDx: number,
    angleOrDy: number,
    style: LineStyle | string,
    labelOrWidth?: string | number,
    scaleOrLabel?: number | string
): void {
    let endX: number, endY: number;
    let s: LineStyle;
    let label: string | undefined;
    if (typeof style === 'string') {
        // Legacy: (ctx, x, y, dx, dy, color, width, label?)
        s = { color: style, width: typeof labelOrWidth === 'number' ? labelOrWidth : 1 };
        endX = x + magnitudeOrDx;
        endY = y + angleOrDy;
        label = typeof scaleOrLabel === 'string' ? scaleOrLabel : undefined;
    } else {
        s = style;
        const scale: number = typeof scaleOrLabel === 'number' ? scaleOrLabel : 1;
        endX = x + magnitudeOrDx * scale * Math.cos(angleOrDy);
        endY = y + magnitudeOrDx * scale * Math.sin(angleOrDy);
        label = typeof labelOrWidth === 'string' ? labelOrWidth : undefined;
    }

    drawArrow(ctx, x, y, endX, endY, s, {
        size: 12,
        angle: Math.PI / 6,
        filled: true,
    });

    if (label) {
        const labelX = (x + endX) / 2;
        const labelY = (y + endY) / 2 - 12;
        drawText(ctx, label, labelX, labelY, {
            color: s.color,
            fontSize: 12,
            fontWeight: "500",
        });
    }
}

/**
 * Draw a bond (for chemistry).
 */
export function drawBond(
    ctx: CanvasRenderingContext2D,
    x1: number,
    y1: number,
    x2: number,
    y2: number,
    bondOrder: 1 | 2 | 3 | 1.5,
    style: LineStyle | string,
    lineWidth?: number
): void {
    const s: LineStyle = typeof style === 'string' ? { color: style, width: lineWidth ?? 1 } : style;
    const dx = x2 - x1;
    const dy = y2 - y1;
    const perpAngle = Math.atan2(dy, dx) + Math.PI / 2;
    const spacing = 4;

    if (bondOrder === 1) {
        drawLine(ctx, x1, y1, x2, y2, s);
    } else if (bondOrder === 2) {
        const offsetX = spacing * Math.cos(perpAngle);
        const offsetY = spacing * Math.sin(perpAngle);
        drawLine(ctx, x1 - offsetX, y1 - offsetY, x2 - offsetX, y2 - offsetY, s);
        drawLine(ctx, x1 + offsetX, y1 + offsetY, x2 + offsetX, y2 + offsetY, s);
    } else if (bondOrder === 3) {
        const offsetX = spacing * Math.cos(perpAngle);
        const offsetY = spacing * Math.sin(perpAngle);
        drawLine(ctx, x1, y1, x2, y2, s);
        drawLine(ctx, x1 - offsetX, y1 - offsetY, x2 - offsetX, y2 - offsetY, s);
        drawLine(ctx, x1 + offsetX, y1 + offsetY, x2 + offsetX, y2 + offsetY, s);
    } else if (bondOrder === 1.5) {
        // Resonance/aromatic - solid + dashed
        const offsetX = spacing * Math.cos(perpAngle);
        const offsetY = spacing * Math.sin(perpAngle);
        drawLine(ctx, x1 - offsetX, y1 - offsetY, x2 - offsetX, y2 - offsetY, s);
        drawLine(ctx, x1 + offsetX, y1 + offsetY, x2 + offsetX, y2 + offsetY, {
            ...s,
            dash: [4, 4],
        });
    }
}

/**
 * Draw a hexagon (convenience wrapper for drawRegularPolygon with 6 sides).
 */
export function drawHexagon(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    radius: number,
    fill?: FillStyle | string,
    stroke?: LineStyle | string,
    strokeWidth?: number
): void {
    const fillStyle: FillStyle | undefined = typeof fill === 'string' ? { color: fill } : fill;
    const strokeStyle: LineStyle | undefined = typeof stroke === 'string'
        ? { color: stroke, width: strokeWidth ?? 1 }
        : stroke;
    drawRegularPolygon(ctx, x, y, radius, 6, fillStyle, strokeStyle);
}

/**
 * Draw a chemical bond using bond type string (legacy stories API).
 * Maps 'single' → 1, 'double' → 2, 'triple' → 3, 'aromatic' → 1.5.
 */
export function drawChemicalBond(
    ctx: CanvasRenderingContext2D,
    x1: number,
    y1: number,
    x2: number,
    y2: number,
    bondType: 'single' | 'double' | 'triple' | 'aromatic',
    color: string,
    lineWidth?: number
): void {
    const orderMap: Record<string, 1 | 2 | 3 | 1.5> = {
        single: 1, double: 2, triple: 3, aromatic: 1.5,
    };
    const bondOrder: 1 | 2 | 3 | 1.5 = orderMap[bondType] ?? 1;
    drawBond(ctx, x1, y1, x2, y2, bondOrder, color, lineWidth);
}

/**
 * Draw a glow/highlight effect around a shape.
 */
export function applyGlow(
    ctx: CanvasRenderingContext2D,
    color: string,
    blur: number
): void {
    ctx.shadowColor = color;
    ctx.shadowBlur = blur;
}

/**
 * Clear glow/highlight effect.
 */
export function clearGlow(ctx: CanvasRenderingContext2D): void {
    ctx.shadowColor = "transparent";
    ctx.shadowBlur = 0;
}
