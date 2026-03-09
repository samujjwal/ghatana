/**
 * Rendering Primitives
 *
 * @doc.type module
 * @doc.purpose Provide low-level drawing primitives for simulation rendering
 * @doc.layer product
 * @doc.pattern Utility
 */
// =============================================================================
// Shape Primitives
// =============================================================================
/**
 * Draw a rectangle.
 */
export function drawRect(ctx, x, y, width, height, fill, stroke, cornerRadius) {
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
        if (stroke.dash)
            ctx.setLineDash(stroke.dash);
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
export function drawRoundedRect(ctx, x, y, width, height, radius, fill, stroke) {
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
    if (fill) {
        ctx.globalAlpha = fill.opacity ?? 1;
        ctx.fillStyle = fill.color;
        ctx.fill();
    }
    if (stroke) {
        ctx.globalAlpha = 1;
        ctx.strokeStyle = stroke.color;
        ctx.lineWidth = stroke.width;
        if (stroke.dash)
            ctx.setLineDash(stroke.dash);
        ctx.stroke();
        ctx.setLineDash([]);
    }
    ctx.globalAlpha = 1;
}
/**
 * Draw a circle.
 */
export function drawCircle(ctx, x, y, radius, fill, stroke) {
    ctx.beginPath();
    ctx.arc(x, y, radius, 0, Math.PI * 2);
    if (fill) {
        ctx.globalAlpha = fill.opacity ?? 1;
        ctx.fillStyle = fill.color;
        ctx.fill();
    }
    if (stroke) {
        ctx.globalAlpha = 1;
        ctx.strokeStyle = stroke.color;
        ctx.lineWidth = stroke.width;
        if (stroke.dash)
            ctx.setLineDash(stroke.dash);
        ctx.stroke();
        ctx.setLineDash([]);
    }
    ctx.globalAlpha = 1;
}
/**
 * Draw an ellipse.
 */
export function drawEllipse(ctx, x, y, radiusX, radiusY, fill, stroke, rotation = 0) {
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
        if (stroke.dash)
            ctx.setLineDash(stroke.dash);
        ctx.stroke();
        ctx.setLineDash([]);
    }
    ctx.globalAlpha = 1;
}
/**
 * Draw a polygon.
 */
export function drawPolygon(ctx, points, fill, stroke) {
    if (points.length < 3)
        return;
    ctx.beginPath();
    ctx.moveTo(points[0].x, points[0].y);
    for (let i = 1; i < points.length; i++) {
        ctx.lineTo(points[i].x, points[i].y);
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
        if (stroke.dash)
            ctx.setLineDash(stroke.dash);
        ctx.stroke();
        ctx.setLineDash([]);
    }
    ctx.globalAlpha = 1;
}
/**
 * Draw a regular polygon (hexagon, pentagon, etc.).
 */
export function drawRegularPolygon(ctx, x, y, radius, sides, fill, stroke, rotation = -Math.PI / 2) {
    const points = [];
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
export function drawDiamond(ctx, x, y, width, height, fill, stroke) {
    const hw = width / 2;
    const hh = height / 2;
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
export function drawLine(ctx, x1, y1, x2, y2, style) {
    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.lineTo(x2, y2);
    ctx.strokeStyle = style.color;
    ctx.lineWidth = style.width;
    ctx.lineCap = style.cap ?? "round";
    ctx.lineJoin = style.join ?? "round";
    if (style.dash)
        ctx.setLineDash(style.dash);
    ctx.stroke();
    ctx.setLineDash([]);
}
/**
 * Draw a curved line (quadratic bezier).
 */
export function drawQuadraticCurve(ctx, x1, y1, cpX, cpY, x2, y2, style) {
    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.quadraticCurveTo(cpX, cpY, x2, y2);
    ctx.strokeStyle = style.color;
    ctx.lineWidth = style.width;
    ctx.lineCap = style.cap ?? "round";
    if (style.dash)
        ctx.setLineDash(style.dash);
    ctx.stroke();
    ctx.setLineDash([]);
}
/**
 * Draw a curved line (cubic bezier).
 */
export function drawBezierCurve(ctx, x1, y1, cp1X, cp1Y, cp2X, cp2Y, x2, y2, style) {
    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.bezierCurveTo(cp1X, cp1Y, cp2X, cp2Y, x2, y2);
    ctx.strokeStyle = style.color;
    ctx.lineWidth = style.width;
    ctx.lineCap = style.cap ?? "round";
    if (style.dash)
        ctx.setLineDash(style.dash);
    ctx.stroke();
    ctx.setLineDash([]);
}
/**
 * Draw an arrow.
 */
export function drawArrow(ctx, x1, y1, x2, y2, style, headStyle = { size: 10, angle: Math.PI / 6, filled: true }) {
    // Draw the line
    drawLine(ctx, x1, y1, x2, y2, style);
    // Draw the arrow head
    const angle = Math.atan2(y2 - y1, x2 - x1);
    const headSize = headStyle.size;
    const headAngle = headStyle.angle;
    const headX1 = x2 - headSize * Math.cos(angle - headAngle);
    const headY1 = y2 - headSize * Math.sin(angle - headAngle);
    const headX2 = x2 - headSize * Math.cos(angle + headAngle);
    const headY2 = y2 - headSize * Math.sin(angle + headAngle);
    ctx.beginPath();
    ctx.moveTo(x2, y2);
    ctx.lineTo(headX1, headY1);
    if (headStyle.filled) {
        ctx.lineTo(headX2, headY2);
        ctx.closePath();
        ctx.fillStyle = headStyle.color ?? style.color;
        ctx.fill();
    }
    else {
        ctx.moveTo(x2, y2);
        ctx.lineTo(headX2, headY2);
        ctx.strokeStyle = headStyle.color ?? style.color;
        ctx.lineWidth = style.width;
        ctx.stroke();
    }
}
/**
 * Draw a double-headed arrow (bidirectional).
 */
export function drawBidirectionalArrow(ctx, x1, y1, x2, y2, style, headStyle = { size: 10, angle: Math.PI / 6, filled: true }) {
    // Draw the line
    drawLine(ctx, x1, y1, x2, y2, style);
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
    const fillColor = headStyle.color ?? style.color;
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
export function drawText(ctx, text, x, y, style) {
    ctx.fillStyle = style.color;
    ctx.font = `${style.fontWeight ?? "normal"} ${style.fontSize}px ${style.fontFamily ?? "Inter, sans-serif"}`;
    ctx.textAlign = style.align ?? "center";
    ctx.textBaseline = style.baseline ?? "middle";
    ctx.fillText(text, x, y);
}
/**
 * Measure text width.
 */
export function measureText(ctx, text, style) {
    ctx.font = `${style.fontWeight ?? "normal"} ${style.fontSize}px ${style.fontFamily ?? "Inter, sans-serif"}`;
    return ctx.measureText(text).width;
}
// =============================================================================
// Special Primitives
// =============================================================================
/**
 * Draw a spring between two points.
 */
export function drawSpring(ctx, x1, y1, x2, y2, coils, amplitude, style) {
    const dx = x2 - x1;
    const dy = y2 - y1;
    const length = Math.sqrt(dx * dx + dy * dy);
    const angle = Math.atan2(dy, dx);
    const coilLength = length / coils;
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
        }
        else {
            ctx.lineTo(baseX, baseY);
        }
    }
    ctx.strokeStyle = style.color;
    ctx.lineWidth = style.width;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.stroke();
}
/**
 * Draw a vector (arrow with magnitude label).
 */
export function drawVector(ctx, x, y, magnitude, angle, style, label, scale = 1) {
    const endX = x + magnitude * scale * Math.cos(angle);
    const endY = y + magnitude * scale * Math.sin(angle);
    drawArrow(ctx, x, y, endX, endY, style, {
        size: 12,
        angle: Math.PI / 6,
        filled: true,
    });
    if (label) {
        const labelX = (x + endX) / 2;
        const labelY = (y + endY) / 2 - 12;
        drawText(ctx, label, labelX, labelY, {
            color: style.color,
            fontSize: 12,
            fontWeight: "500",
        });
    }
}
/**
 * Draw a bond (for chemistry).
 */
export function drawBond(ctx, x1, y1, x2, y2, bondOrder, style) {
    const dx = x2 - x1;
    const dy = y2 - y1;
    const perpAngle = Math.atan2(dy, dx) + Math.PI / 2;
    const spacing = 4;
    if (bondOrder === 1) {
        drawLine(ctx, x1, y1, x2, y2, style);
    }
    else if (bondOrder === 2) {
        const offsetX = spacing * Math.cos(perpAngle);
        const offsetY = spacing * Math.sin(perpAngle);
        drawLine(ctx, x1 - offsetX, y1 - offsetY, x2 - offsetX, y2 - offsetY, style);
        drawLine(ctx, x1 + offsetX, y1 + offsetY, x2 + offsetX, y2 + offsetY, style);
    }
    else if (bondOrder === 3) {
        const offsetX = spacing * Math.cos(perpAngle);
        const offsetY = spacing * Math.sin(perpAngle);
        drawLine(ctx, x1, y1, x2, y2, style);
        drawLine(ctx, x1 - offsetX, y1 - offsetY, x2 - offsetX, y2 - offsetY, style);
        drawLine(ctx, x1 + offsetX, y1 + offsetY, x2 + offsetX, y2 + offsetY, style);
    }
    else if (bondOrder === 1.5) {
        // Resonance/aromatic - solid + dashed
        const offsetX = spacing * Math.cos(perpAngle);
        const offsetY = spacing * Math.sin(perpAngle);
        drawLine(ctx, x1 - offsetX, y1 - offsetY, x2 - offsetX, y2 - offsetY, style);
        drawLine(ctx, x1 + offsetX, y1 + offsetY, x2 + offsetX, y2 + offsetY, {
            ...style,
            dash: [4, 4],
        });
    }
}
/**
 * Draw a glow/highlight effect around a shape.
 */
export function applyGlow(ctx, color, blur) {
    ctx.shadowColor = color;
    ctx.shadowBlur = blur;
}
/**
 * Clear glow/highlight effect.
 */
export function clearGlow(ctx) {
    ctx.shadowColor = "transparent";
    ctx.shadowBlur = 0;
}
//# sourceMappingURL=primitives.js.map