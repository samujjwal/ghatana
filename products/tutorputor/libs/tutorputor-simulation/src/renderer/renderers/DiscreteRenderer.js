/**
 * Discrete Renderer - Algorithms & Data Structures
 *
 * @doc.type module
 * @doc.purpose Render discrete simulation entities (nodes, edges, pointers)
 * @doc.layer product
 * @doc.pattern Renderer
 */
import { drawRect, drawCircle, drawDiamond, drawRegularPolygon, drawText, drawArrow, drawLine, drawQuadraticCurve, applyGlow, clearGlow, } from "../primitives";
// =============================================================================
// Node Renderer
// =============================================================================
/**
 * Renderer for discrete algorithm nodes.
 */
export const discreteNodeRenderer = {
    entityTypes: ["node"],
    domain: "CS_DISCRETE",
    render(entity, context, isHovered = false, isSelected = false) {
        const { ctx, theme, zoom, worldToScreen } = context;
        const screen = worldToScreen(entity.x, entity.y);
        const size = (entity.scale ?? 1) * 40 * zoom;
        // Determine colors based on state
        let fillColor = entity.color ?? theme.primary;
        let strokeColor = entity.strokeColor ?? theme.border;
        if (entity.highlighted) {
            fillColor = theme.highlight;
        }
        if (entity.visited) {
            fillColor = theme.success;
        }
        if (entity.comparing) {
            fillColor = theme.warning;
        }
        if (entity.sorted) {
            fillColor = theme.success;
            strokeColor = theme.success;
        }
        // Apply hover/selection effects
        if (isHovered || isSelected) {
            applyGlow(ctx, fillColor, isSelected ? 20 : 12);
        }
        const fill = { color: fillColor, opacity: entity.opacity ?? 1 };
        const stroke = { color: strokeColor, width: entity.strokeWidth ?? 2 };
        // Render shape
        switch (entity.shape ?? "rect") {
            case "circle":
                drawCircle(ctx, screen.x, screen.y, size / 2, fill, stroke);
                break;
            case "diamond":
                drawDiamond(ctx, screen.x, screen.y, size, size, fill, stroke);
                break;
            case "hexagon":
                drawRegularPolygon(ctx, screen.x, screen.y, size / 2, 6, fill, stroke);
                break;
            case "rect":
            default:
                drawRect(ctx, screen.x, screen.y, size, size * 0.75, fill, stroke, 6);
                break;
        }
        clearGlow(ctx);
        // Draw value
        if (entity.value !== undefined) {
            drawText(ctx, String(entity.value), screen.x, screen.y, {
                color: theme.foreground,
                fontSize: 14 * zoom,
                fontWeight: "600",
            });
        }
        // Draw label below
        if (entity.label) {
            drawText(ctx, entity.label, screen.x, screen.y + size / 2 + 14 * zoom, {
                color: theme.neutral,
                fontSize: 11 * zoom,
                fontWeight: "500",
            });
        }
    },
    hitTest(entity, worldX, worldY, context) {
        const size = (entity.scale ?? 1) * 40;
        const dx = Math.abs(worldX - entity.x);
        const dy = Math.abs(worldY - entity.y);
        return dx < size / 2 && dy < size / 2;
    },
    getBounds(entity) {
        const size = (entity.scale ?? 1) * 40;
        return {
            x: entity.x - size / 2,
            y: entity.y - size / 2,
            width: size,
            height: size,
        };
    },
};
// =============================================================================
// Edge Renderer
// =============================================================================
/**
 * Renderer for discrete algorithm edges (connections between nodes).
 */
export const discreteEdgeRenderer = {
    entityTypes: ["edge"],
    domain: "CS_DISCRETE",
    render(entity, context, isHovered = false, isSelected = false) {
        const { ctx, theme, zoom, entities, worldToScreen } = context;
        // Get source and target nodes
        const sourceNode = entities.get(entity.sourceId);
        const targetNode = entities.get(entity.targetId);
        if (!sourceNode || !targetNode)
            return;
        const source = worldToScreen(sourceNode.x, sourceNode.y);
        const target = worldToScreen(targetNode.x, targetNode.y);
        const lineColor = entity.color ?? theme.neutral;
        const lineWidth = (entity.strokeWidth ?? 2) * zoom;
        // Apply hover/selection effects
        if (isHovered || isSelected) {
            applyGlow(ctx, lineColor, isSelected ? 15 : 8);
        }
        const lineStyle = { color: lineColor, width: lineWidth };
        if (entity.curved) {
            // Draw curved edge
            const midX = (source.x + target.x) / 2;
            const midY = (source.y + target.y) / 2;
            const dx = target.x - source.x;
            const dy = target.y - source.y;
            const perpX = -dy * 0.3;
            const perpY = dx * 0.3;
            const cpX = midX + perpX;
            const cpY = midY + perpY;
            drawQuadraticCurve(ctx, source.x, source.y, cpX, cpY, target.x, target.y, lineStyle);
            // Draw arrow head for directed edges
            if (entity.directed) {
                // Calculate angle at the end of curve
                const t = 0.95;
                const endX = (1 - t) * (1 - t) * source.x + 2 * (1 - t) * t * cpX + t * t * target.x;
                const endY = (1 - t) * (1 - t) * source.y + 2 * (1 - t) * t * cpY + t * t * target.y;
                const angle = Math.atan2(target.y - endY, target.x - endX);
                drawArrowHead(ctx, target.x, target.y, angle, 10 * zoom, lineColor);
            }
        }
        else {
            if (entity.directed) {
                drawArrow(ctx, source.x, source.y, target.x, target.y, lineStyle, {
                    size: 10 * zoom,
                    angle: Math.PI / 6,
                    filled: true,
                    color: lineColor,
                });
            }
            else {
                drawLine(ctx, source.x, source.y, target.x, target.y, lineStyle);
            }
        }
        clearGlow(ctx);
        // Draw weight label if present
        if (entity.weight !== undefined) {
            const midX = (source.x + target.x) / 2;
            const midY = (source.y + target.y) / 2 - 12 * zoom;
            // Background for weight
            ctx.fillStyle = theme.background;
            ctx.fillRect(midX - 12, midY - 8, 24, 16);
            drawText(ctx, String(entity.weight), midX, midY, {
                color: theme.foreground,
                fontSize: 11 * zoom,
                fontWeight: "500",
            });
        }
    },
    hitTest(entity, worldX, worldY, context) {
        const { entities } = context;
        const sourceNode = entities.get(entity.sourceId);
        const targetNode = entities.get(entity.targetId);
        if (!sourceNode || !targetNode)
            return false;
        // Simple line distance check
        const threshold = 10;
        const dx = targetNode.x - sourceNode.x;
        const dy = targetNode.y - sourceNode.y;
        const lengthSq = dx * dx + dy * dy;
        if (lengthSq === 0)
            return false;
        const t = Math.max(0, Math.min(1, ((worldX - sourceNode.x) * dx + (worldY - sourceNode.y) * dy) / lengthSq));
        const closestX = sourceNode.x + t * dx;
        const closestY = sourceNode.y + t * dy;
        const distSq = (worldX - closestX) * (worldX - closestX) +
            (worldY - closestY) * (worldY - closestY);
        return distSq < threshold * threshold;
    },
    getBounds(entity, context) {
        const { entities } = context;
        const sourceNode = entities.get(entity.sourceId);
        const targetNode = entities.get(entity.targetId);
        if (!sourceNode || !targetNode) {
            return { x: entity.x, y: entity.y, width: 0, height: 0 };
        }
        const minX = Math.min(sourceNode.x, targetNode.x);
        const minY = Math.min(sourceNode.y, targetNode.y);
        const maxX = Math.max(sourceNode.x, targetNode.x);
        const maxY = Math.max(sourceNode.y, targetNode.y);
        return {
            x: minX,
            y: minY,
            width: maxX - minX,
            height: maxY - minY,
        };
    },
};
// =============================================================================
// Pointer Renderer
// =============================================================================
/**
 * Renderer for pointers (i, j, left, right markers).
 */
export const discretePointerRenderer = {
    entityTypes: ["pointer"],
    domain: "CS_DISCRETE",
    render(entity, context, isHovered = false) {
        const { ctx, theme, zoom, entities, worldToScreen } = context;
        // Get target node
        const targetNode = entities.get(entity.targetId);
        if (!targetNode)
            return;
        const target = worldToScreen(targetNode.x, targetNode.y);
        const color = entity.color ?? theme.secondary;
        const size = 30 * zoom;
        if (isHovered) {
            applyGlow(ctx, color, 10);
        }
        switch (entity.style ?? "arrow") {
            case "bracket":
                // Draw bracket under node
                ctx.beginPath();
                ctx.moveTo(target.x - size / 2, target.y + size);
                ctx.lineTo(target.x - size / 2, target.y + size + 8);
                ctx.lineTo(target.x + size / 2, target.y + size + 8);
                ctx.lineTo(target.x + size / 2, target.y + size);
                ctx.strokeStyle = color;
                ctx.lineWidth = 2 * zoom;
                ctx.stroke();
                break;
            case "underline":
                // Draw underline under node
                drawLine(ctx, target.x - size / 2, target.y + size + 5, target.x + size / 2, target.y + size + 5, { color, width: 3 * zoom });
                break;
            case "arrow":
            default:
                // Draw arrow pointing to node from below
                drawArrow(ctx, target.x, target.y + size + 25 * zoom, target.x, target.y + size + 5, { color, width: 2 * zoom }, { size: 8 * zoom, angle: Math.PI / 6, filled: true });
                break;
        }
        clearGlow(ctx);
        // Draw pointer label
        if (entity.pointerLabel ?? entity.label) {
            drawText(ctx, entity.pointerLabel ?? entity.label ?? "", target.x, target.y + size + 35 * zoom, {
                color,
                fontSize: 12 * zoom,
                fontWeight: "600",
            });
        }
    },
    hitTest(entity, worldX, worldY, context) {
        const { entities } = context;
        const targetNode = entities.get(entity.targetId);
        if (!targetNode)
            return false;
        const dx = Math.abs(worldX - targetNode.x);
        const dy = Math.abs(worldY - (targetNode.y + 40));
        return dx < 15 && dy < 20;
    },
    getBounds(entity, context) {
        const { entities } = context;
        const targetNode = entities.get(entity.targetId);
        if (!targetNode) {
            return { x: entity.x, y: entity.y, width: 0, height: 0 };
        }
        return {
            x: targetNode.x - 15,
            y: targetNode.y + 25,
            width: 30,
            height: 30,
        };
    },
};
// =============================================================================
// Helper Functions
// =============================================================================
function drawArrowHead(ctx, x, y, angle, size, color) {
    const headAngle = Math.PI / 6;
    const x1 = x - size * Math.cos(angle - headAngle);
    const y1 = y - size * Math.sin(angle - headAngle);
    const x2 = x - size * Math.cos(angle + headAngle);
    const y2 = y - size * Math.sin(angle + headAngle);
    ctx.beginPath();
    ctx.moveTo(x, y);
    ctx.lineTo(x1, y1);
    ctx.lineTo(x2, y2);
    ctx.closePath();
    ctx.fillStyle = color;
    ctx.fill();
}
// =============================================================================
// Exports
// =============================================================================
export const discreteRenderers = [
    discreteNodeRenderer,
    discreteEdgeRenderer,
    discretePointerRenderer,
];
//# sourceMappingURL=DiscreteRenderer.js.map