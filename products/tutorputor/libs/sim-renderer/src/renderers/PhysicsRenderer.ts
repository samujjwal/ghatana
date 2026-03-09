/**
 * Physics Renderer - Mechanics & Dynamics
 *
 * @doc.type module
 * @doc.purpose Render physics simulation entities (bodies, springs, vectors, particles)
 * @doc.layer product
 * @doc.pattern Renderer
 */

import type {
    PhysicsBodyEntity,
    PhysicsSpringEntity,
    PhysicsVectorEntity,
    PhysicsParticleEntity,
} from "@ghatana/tutorputor-contracts/v1/simulation";
import type { EntityRenderer, RenderContext } from "../types";
import {
    drawRect,
    drawCircle,
    drawPolygon,
    drawText,
    drawSpring,
    drawVector,
    applyGlow,
    clearGlow,
} from "../primitives";

// =============================================================================
// Rigid Body Renderer
// =============================================================================

/**
 * Renderer for physics rigid bodies.
 */
export const physicsBodyRenderer: EntityRenderer<PhysicsBodyEntity> = {
    entityTypes: ["rigidBody"],
    domain: "PHYSICS",

    render(entity, context, isHovered = false, isSelected = false): void {
        const { ctx, theme, zoom, worldToScreen } = context;
        const screen = worldToScreen(entity.x, entity.y);

        const fillColor = entity.color ?? theme.primary;
        const strokeColor = entity.strokeColor ?? theme.border;
        const size = (entity.scale ?? 1) * 50 * zoom;

        if (isHovered || isSelected) {
            applyGlow(ctx, fillColor, isSelected ? 20 : 12);
        }

        const fill = { color: fillColor, opacity: entity.opacity ?? 1 };
        const stroke = { color: strokeColor, width: entity.strokeWidth ?? 2 };

        // Save context for rotation
        ctx.save();
        ctx.translate(screen.x, screen.y);
        ctx.rotate(entity.rotation ?? 0);

        switch (entity.shape ?? "circle") {
            case "rect":
                const width = (entity.width ?? size);
                const height = (entity.height ?? size * 0.6);
                drawRect(ctx, 0, 0, width * zoom, height * zoom, fill, stroke, 4);
                break;

            case "polygon":
                if (entity.vertices && entity.vertices.length >= 3) {
                    const scaledVertices = entity.vertices.map((v) => ({
                        x: v.x * zoom,
                        y: v.y * zoom,
                    }));
                    drawPolygon(ctx, scaledVertices, fill, stroke);
                }
                break;

            case "circle":
            default:
                drawCircle(ctx, 0, 0, size / 2, fill, stroke);
                break;
        }

        ctx.restore();
        clearGlow(ctx);

        // Draw mass label if significant
        if (entity.mass > 0) {
            drawText(ctx, `${entity.mass}kg`, screen.x, screen.y, {
                color: theme.foreground,
                fontSize: 11 * zoom,
                fontWeight: "500",
            });
        }

        // Draw label
        if (entity.label) {
            drawText(ctx, entity.label, screen.x, screen.y + size / 2 + 14 * zoom, {
                color: theme.neutral,
                fontSize: 10 * zoom,
                fontWeight: "500",
            });
        }

        // Draw velocity vector if present
        if (entity.velocityX !== undefined && entity.velocityY !== undefined) {
            const vMag = Math.sqrt(
                entity.velocityX * entity.velocityX + entity.velocityY * entity.velocityY
            );
            if (vMag > 0.1) {
                const vAngle = Math.atan2(entity.velocityY, entity.velocityX);
                drawVector(
                    ctx,
                    screen.x,
                    screen.y,
                    vMag,
                    vAngle,
                    { color: theme.success, width: 2 },
                    undefined,
                    zoom * 2
                );
            }
        }
    },

    hitTest(entity, worldX, worldY): boolean {
        const size = (entity.scale ?? 1) * 50;

        if (entity.shape === "circle") {
            const dx = worldX - entity.x;
            const dy = worldY - entity.y;
            return dx * dx + dy * dy <= (size / 2) * (size / 2);
        }

        // Rectangle hit test
        const width = entity.width ?? size;
        const height = entity.height ?? size * 0.6;
        const dx = Math.abs(worldX - entity.x);
        const dy = Math.abs(worldY - entity.y);
        return dx < width / 2 && dy < height / 2;
    },

    getBounds(entity): { x: number; y: number; width: number; height: number } {
        const size = (entity.scale ?? 1) * 50;
        const width = entity.width ?? size;
        const height = entity.height ?? size;
        return {
            x: entity.x - width / 2,
            y: entity.y - height / 2,
            width,
            height,
        };
    },
};

// =============================================================================
// Spring Renderer
// =============================================================================

/**
 * Renderer for physics springs.
 */
export const physicsSpringRenderer: EntityRenderer<PhysicsSpringEntity> = {
    entityTypes: ["spring"],
    domain: "PHYSICS",

    render(entity, context, isHovered = false): void {
        const { ctx, theme, zoom, entities, worldToScreen } = context;

        const anchorId = entity.anchorId ?? entity.body1Id;
        const attachId = entity.attachId ?? entity.body2Id;
        const anchorEntity = anchorId ? entities.get(anchorId) : undefined;
        const attachEntity = attachId ? entities.get(attachId) : undefined;

        if (!anchorEntity || !attachEntity) return;

        const anchor = worldToScreen(anchorEntity.x, anchorEntity.y);
        const attach = worldToScreen(attachEntity.x, attachEntity.y);

        const color = entity.color ?? theme.neutral;

        if (isHovered) {
            applyGlow(ctx, color, 10);
        }

        // Calculate current stretch
        const dx = attach.x - anchor.x;
        const dy = attach.y - anchor.y;
        const currentLength = Math.sqrt(dx * dx + dy * dy);
        const stretch = currentLength / (entity.restLength * zoom);

        // Color based on stretch
        let springColor = color;
        if (stretch > 1.2) {
            springColor = theme.danger; // Stretched
        } else if (stretch < 0.8) {
            springColor = theme.warning; // Compressed
        }

        // Draw spring coils
        const coils = Math.max(5, Math.floor(entity.restLength / 10));
        const amplitude = 8 * zoom;

        drawSpring(
            ctx,
            anchor.x,
            anchor.y,
            attach.x,
            attach.y,
            coils,
            amplitude,
            { color: springColor, width: 2 * zoom }
        );

        clearGlow(ctx);

        // Draw stiffness label
        if (entity.label) {
            const midX = (anchor.x + attach.x) / 2;
            const midY = (anchor.y + attach.y) / 2 - 15 * zoom;
            drawText(ctx, entity.label, midX, midY, {
                color: theme.neutral,
                fontSize: 10 * zoom,
            });
        }
    },

    hitTest(entity, worldX, worldY, context): boolean {
        const { entities } = context;
        const anchorId = entity.anchorId ?? entity.body1Id;
        const attachId = entity.attachId ?? entity.body2Id;
        const anchorEntity = anchorId ? entities.get(anchorId) : undefined;
        const attachEntity = attachId ? entities.get(attachId) : undefined;

        if (!anchorEntity || !attachEntity) return false;

        // Line distance check
        const threshold = 15;
        const dx = attachEntity.x - anchorEntity.x;
        const dy = attachEntity.y - anchorEntity.y;
        const lengthSq = dx * dx + dy * dy;

        if (lengthSq === 0) return false;

        const t = Math.max(
            0,
            Math.min(
                1,
                ((worldX - anchorEntity.x) * dx + (worldY - anchorEntity.y) * dy) /
                lengthSq
            )
        );

        const closestX = anchorEntity.x + t * dx;
        const closestY = anchorEntity.y + t * dy;
        const distSq =
            (worldX - closestX) * (worldX - closestX) +
            (worldY - closestY) * (worldY - closestY);

        return distSq < threshold * threshold;
    },

    getBounds(entity, context): { x: number; y: number; width: number; height: number } {
        const { entities } = context;
        const anchorId = entity.anchorId ?? entity.body1Id;
        const attachId = entity.attachId ?? entity.body2Id;
        const anchorEntity = anchorId ? entities.get(anchorId) : undefined;
        const attachEntity = attachId ? entities.get(attachId) : undefined;

        if (!anchorEntity || !attachEntity) {
            return { x: entity.x, y: entity.y, width: 0, height: 0 };
        }

        const minX = Math.min(anchorEntity.x, attachEntity.x);
        const minY = Math.min(anchorEntity.y, attachEntity.y);
        const maxX = Math.max(anchorEntity.x, attachEntity.x);
        const maxY = Math.max(anchorEntity.y, attachEntity.y);

        return {
            x: minX,
            y: minY,
            width: maxX - minX,
            height: maxY - minY,
        };
    },
};

// =============================================================================
// Vector Renderer
// =============================================================================

/**
 * Renderer for physics vectors (force, velocity, acceleration).
 */
export const physicsVectorRenderer: EntityRenderer<PhysicsVectorEntity> = {
    entityTypes: ["vector"],
    domain: "PHYSICS",

    render(entity, context, isHovered = false): void {
        const { ctx, theme, zoom, entities, worldToScreen } = context;

        // Get attachment point
        let startX = entity.x;
        let startY = entity.y;

        const effectiveAttachId = entity.attachId ?? entity.attachedToId;
        if (effectiveAttachId) {
            const attachEntity = entities.get(effectiveAttachId);
            if (attachEntity) {
                startX = attachEntity.x;
                startY = attachEntity.y;
            }
        }

        const screen = worldToScreen(startX, startY);

        // Color based on vector type
        let color: string;
        switch (entity.vectorType) {
            case "velocity":
                color = theme.success;
                break;
            case "acceleration":
                color = theme.warning;
                break;
            case "force":
                color = theme.danger;
                break;
            case "displacement":
                color = theme.secondary;
                break;
            default:
                color = entity.color ?? theme.primary;
        }

        if (isHovered) {
            applyGlow(ctx, color, 12);
        }

        const scale = zoom * 3;
        const label = entity.label ?? entity.vectorType;
        const magnitude = entity.magnitude ?? Math.sqrt((entity.dx ?? 0) ** 2 + (entity.dy ?? 0) ** 2);
        const angle = entity.angle ?? Math.atan2(entity.dy ?? 0, entity.dx ?? 0);

        drawVector(
            ctx,
            screen.x,
            screen.y,
            magnitude,
            angle,
            { color, width: 3 * zoom },
            label,
            scale
        );

        clearGlow(ctx);
    },

    hitTest(entity, worldX, worldY): boolean {
        // Check if point is near the vector line
        const magnitude = entity.magnitude ?? Math.sqrt((entity.dx ?? 0) ** 2 + (entity.dy ?? 0) ** 2);
        const angle = entity.angle ?? Math.atan2(entity.dy ?? 0, entity.dx ?? 0);
        const endX = entity.x + magnitude * Math.cos(angle) * 3;
        const endY = entity.y + magnitude * Math.sin(angle) * 3;

        const dx = endX - entity.x;
        const dy = endY - entity.y;
        const lengthSq = dx * dx + dy * dy;

        if (lengthSq === 0) return false;

        const t = Math.max(
            0,
            Math.min(
                1,
                ((worldX - entity.x) * dx + (worldY - entity.y) * dy) / lengthSq
            )
        );

        const closestX = entity.x + t * dx;
        const closestY = entity.y + t * dy;
        const distSq =
            (worldX - closestX) * (worldX - closestX) +
            (worldY - closestY) * (worldY - closestY);

        return distSq < 100;
    },

    getBounds(entity): { x: number; y: number; width: number; height: number } {
        const magnitude = entity.magnitude ?? Math.sqrt((entity.dx ?? 0) ** 2 + (entity.dy ?? 0) ** 2);
        const angle = entity.angle ?? Math.atan2(entity.dy ?? 0, entity.dx ?? 0);
        const endX = entity.x + magnitude * Math.cos(angle) * 3;
        const endY = entity.y + magnitude * Math.sin(angle) * 3;

        const minX = Math.min(entity.x, endX);
        const minY = Math.min(entity.y, endY);
        const maxX = Math.max(entity.x, endX);
        const maxY = Math.max(entity.y, endY);

        return {
            x: minX,
            y: minY,
            width: maxX - minX,
            height: maxY - minY,
        };
    },
};

// =============================================================================
// Particle Renderer
// =============================================================================

/**
 * Renderer for physics particles.
 */
export const physicsParticleRenderer: EntityRenderer<PhysicsParticleEntity> = {
    entityTypes: ["particle"],
    domain: "PHYSICS",

    render(entity, context): void {
        const { ctx, theme, zoom, worldToScreen } = context;
        const screen = worldToScreen(entity.x, entity.y);

        // Calculate opacity based on lifetime
        let opacity = entity.opacity ?? 1;
        if (entity.lifetime !== undefined && entity.age !== undefined) {
            opacity = Math.max(0, 1 - entity.age / entity.lifetime);
        }

        const color = entity.color ?? theme.primary;
        const size = (entity.scale ?? 1) * 8 * zoom;

        drawCircle(ctx, screen.x, screen.y, size, {
            color,
            opacity,
        });
    },

    hitTest(entity, worldX, worldY): boolean {
        const size = (entity.scale ?? 1) * 8;
        const dx = worldX - entity.x;
        const dy = worldY - entity.y;
        return dx * dx + dy * dy <= size * size;
    },

    getBounds(entity): { x: number; y: number; width: number; height: number } {
        const size = (entity.scale ?? 1) * 8;
        return {
            x: entity.x - size,
            y: entity.y - size,
            width: size * 2,
            height: size * 2,
        };
    },
};

// =============================================================================
// Exports
// =============================================================================

export const physicsRenderers = [
    physicsBodyRenderer,
    physicsSpringRenderer,
    physicsVectorRenderer,
    physicsParticleRenderer,
];
