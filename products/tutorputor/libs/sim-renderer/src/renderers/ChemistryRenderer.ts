/**
 * Chemistry Renderer - Molecules & Reactions
 *
 * @doc.type module
 * @doc.purpose Render chemistry simulation entities (atoms, bonds, molecules, reactions)
 * @doc.layer product
 * @doc.pattern Renderer
 */

import type {
    ChemAtomEntity,
    ChemBondEntity,
    ChemMoleculeEntity,
    ChemReactionArrowEntity,
    ChemEnergyProfileEntity,
} from "@ghatana/tutorputor-contracts/v1/simulation";
import type { EntityRenderer, RenderContext } from "../types";
import {
    drawCircle,
    drawText,
    drawBond,
    drawLine,
    drawArrow,
    drawBidirectionalArrow,
    applyGlow,
    clearGlow,
} from "../primitives";

// =============================================================================
// Element Colors (CPK coloring convention)
// =============================================================================

const ELEMENT_COLORS: Record<string, string> = {
    H: "#FFFFFF",
    C: "#909090",
    N: "#3050F8",
    O: "#FF0D0D",
    F: "#90E050",
    Cl: "#1FF01F",
    Br: "#A62929",
    I: "#940094",
    S: "#FFFF30",
    P: "#FF8000",
    Fe: "#E06633",
    Na: "#AB5CF2",
    K: "#8F40D4",
    Ca: "#3DFF00",
    Mg: "#8AFF00",
    default: "#FF1493",
};

function getElementColor(element: string): string {
    return ELEMENT_COLORS[element] ?? ELEMENT_COLORS.default ?? '#FF1493';
}

// =============================================================================
// Atom Renderer
// =============================================================================

/**
 * Renderer for chemistry atoms.
 */
export const chemAtomRenderer: EntityRenderer<ChemAtomEntity> = {
    entityTypes: ["atom"],
    domain: "CHEMISTRY",

    render(entity, context, isHovered = false, isSelected = false): void {
        const { ctx, theme, zoom, worldToScreen } = context;
        const screen = worldToScreen(entity.x, entity.y);

        const color = entity.color ?? getElementColor(entity.element);
        const size = (entity.scale ?? 1) * 30 * zoom;

        if (isHovered || isSelected) {
            applyGlow(ctx, color, isSelected ? 20 : 12);
        }

        // Draw atom circle
        drawCircle(
            ctx,
            screen.x,
            screen.y,
            size,
            { color, opacity: entity.opacity ?? 1 },
            { color: theme.border, width: 2 }
        );

        clearGlow(ctx);

        // Draw element symbol
        drawText(ctx, entity.element, screen.x, screen.y, {
            color: entity.element === "C" ? "#FFFFFF" : theme.foreground,
            fontSize: 14 * zoom,
            fontWeight: "bold",
        });

        // Draw charge if present
        if (entity.charge !== undefined && entity.charge !== 0) {
            const chargeStr =
                entity.charge > 0
                    ? `${entity.charge > 1 ? entity.charge : ""}+`
                    : `${entity.charge < -1 ? Math.abs(entity.charge) : ""}−`;

            drawText(ctx, chargeStr, screen.x + size * 0.7, screen.y - size * 0.7, {
                color: theme.foreground,
                fontSize: 10 * zoom,
                fontWeight: "bold",
            });
        }

        // Draw isotope number if present
        if (entity.isotope !== undefined) {
            drawText(
                ctx,
                String(entity.isotope),
                screen.x - size * 0.7,
                screen.y - size * 0.7,
                {
                    color: theme.neutral,
                    fontSize: 9 * zoom,
                }
            );
        }

        // Draw label
        if (entity.label) {
            drawText(ctx, entity.label, screen.x, screen.y + size + 12 * zoom, {
                color: theme.neutral,
                fontSize: 10 * zoom,
            });
        }
    },

    hitTest(entity, worldX, worldY): boolean {
        const size = (entity.scale ?? 1) * 30;
        const dx = worldX - entity.x;
        const dy = worldY - entity.y;
        return dx * dx + dy * dy <= size * size;
    },

    getBounds(entity): { x: number; y: number; width: number; height: number } {
        const size = (entity.scale ?? 1) * 30;
        return {
            x: entity.x - size,
            y: entity.y - size,
            width: size * 2,
            height: size * 2,
        };
    },
};

// =============================================================================
// Bond Renderer
// =============================================================================

/**
 * Renderer for chemistry bonds.
 */
export const chemBondRenderer: EntityRenderer<ChemBondEntity> = {
    entityTypes: ["bond"],
    domain: "CHEMISTRY",

    render(entity, context, isHovered = false): void {
        const { ctx, theme, zoom, entities, worldToScreen } = context;

        const atom1 = entities.get(entity.atom1Id);
        const atom2 = entities.get(entity.atom2Id);

        if (!atom1 || !atom2) return;

        const screen1 = worldToScreen(atom1.x, atom1.y);
        const screen2 = worldToScreen(atom2.x, atom2.y);

        const color = entity.color ?? theme.neutral;

        if (isHovered) {
            applyGlow(ctx, color, 10);
        }

        // Adjust endpoints to not overlap with atoms
        const atomSize = 30 * zoom;
        const dx = screen2.x - screen1.x;
        const dy = screen2.y - screen1.y;
        const length = Math.sqrt(dx * dx + dy * dy);
        const ux = dx / length;
        const uy = dy / length;

        const start = {
            x: screen1.x + ux * atomSize,
            y: screen1.y + uy * atomSize,
        };
        const end = {
            x: screen2.x - ux * atomSize,
            y: screen2.y - uy * atomSize,
        };

        // Handle stereochemistry (wedge bonds)
        if (entity.stereochemistry === "up") {
            // Wedge bond (filled triangle)
            ctx.beginPath();
            const perpX = -uy * 6 * zoom;
            const perpY = ux * 6 * zoom;
            ctx.moveTo(start.x, start.y);
            ctx.lineTo(end.x + perpX, end.y + perpY);
            ctx.lineTo(end.x - perpX, end.y - perpY);
            ctx.closePath();
            ctx.fillStyle = color;
            ctx.fill();
        } else if (entity.stereochemistry === "down") {
            // Dashed wedge bond
            const segments = 8;
            for (let i = 0; i < segments; i++) {
                const t1 = i / segments;
                const t2 = (i + 0.5) / segments;
                const x1 = start.x + dx * t1;
                const y1 = start.y + dy * t1;
                const x2 = start.x + dx * t2;
                const y2 = start.y + dy * t2;
                const width = (i / segments) * 6 * zoom;
                const perpX = -uy * width;
                const perpY = ux * width;

                ctx.beginPath();
                ctx.moveTo(x1 + perpX, y1 + perpY);
                ctx.lineTo(x2 + perpX, y2 + perpY);
                ctx.lineTo(x2 - perpX, y2 - perpY);
                ctx.lineTo(x1 - perpX, y1 - perpY);
                ctx.closePath();
                ctx.fillStyle = color;
                ctx.fill();
            }
        } else {
            // Regular bond (single, double, triple, aromatic)
            drawBond(
                ctx,
                start.x,
                start.y,
                end.x,
                end.y,
                entity.bondOrder ?? 1,
                { color, width: 2 * zoom }
            );
        }

        clearGlow(ctx);
    },

    hitTest(entity, worldX, worldY, context): boolean {
        const { entities } = context;
        const atom1 = entities.get(entity.atom1Id);
        const atom2 = entities.get(entity.atom2Id);

        if (!atom1 || !atom2) return false;

        const threshold = 10;
        const dx = atom2.x - atom1.x;
        const dy = atom2.y - atom1.y;
        const lengthSq = dx * dx + dy * dy;

        if (lengthSq === 0) return false;

        const t = Math.max(
            0,
            Math.min(
                1,
                ((worldX - atom1.x) * dx + (worldY - atom1.y) * dy) / lengthSq
            )
        );

        const closestX = atom1.x + t * dx;
        const closestY = atom1.y + t * dy;
        const distSq =
            (worldX - closestX) * (worldX - closestX) +
            (worldY - closestY) * (worldY - closestY);

        return distSq < threshold * threshold;
    },

    getBounds(entity, context): { x: number; y: number; width: number; height: number } {
        const { entities } = context;
        const atom1 = entities.get(entity.atom1Id);
        const atom2 = entities.get(entity.atom2Id);

        if (!atom1 || !atom2) {
            return { x: entity.x, y: entity.y, width: 0, height: 0 };
        }

        const minX = Math.min(atom1.x, atom2.x);
        const minY = Math.min(atom1.y, atom2.y);
        const maxX = Math.max(atom1.x, atom2.x);
        const maxY = Math.max(atom1.y, atom2.y);

        return {
            x: minX,
            y: minY,
            width: maxX - minX,
            height: maxY - minY,
        };
    },
};

// =============================================================================
// Molecule Renderer (container, renders atoms/bonds)
// =============================================================================

/**
 * Renderer for molecule containers (draws bounding box/highlight).
 */
export const chemMoleculeRenderer: EntityRenderer<ChemMoleculeEntity> = {
    entityTypes: ["molecule"],
    domain: "CHEMISTRY",

    render(entity, context, isHovered = false, isSelected = false): void {
        const { ctx, theme, zoom, worldToScreen } = context;
        const screen = worldToScreen(entity.x, entity.y);

        // Draw molecule label/name if present
        if (entity.name || entity.formula) {
            const label = entity.name ?? entity.formula ?? "";
            drawText(ctx, label, screen.x, screen.y - 50 * zoom, {
                color: isSelected ? theme.primary : theme.foreground,
                fontSize: 14 * zoom,
                fontWeight: "600",
            });
        }

        // Draw selection/hover indicator
        if (isHovered || isSelected) {
            ctx.strokeStyle = isSelected ? theme.primary : theme.neutral;
            ctx.lineWidth = 2;
            ctx.setLineDash([4, 4]);
            ctx.strokeRect(
                screen.x - 60 * zoom,
                screen.y - 40 * zoom,
                120 * zoom,
                80 * zoom
            );
            ctx.setLineDash([]);
        }
    },

    hitTest(entity, worldX, worldY): boolean {
        const dx = Math.abs(worldX - entity.x);
        const dy = Math.abs(worldY - entity.y);
        return dx < 60 && dy < 40;
    },

    getBounds(entity): { x: number; y: number; width: number; height: number } {
        return {
            x: entity.x - 60,
            y: entity.y - 40,
            width: 120,
            height: 80,
        };
    },
};

// =============================================================================
// Reaction Arrow Renderer
// =============================================================================

/**
 * Renderer for reaction arrows.
 */
export const chemReactionArrowRenderer: EntityRenderer<ChemReactionArrowEntity> = {
    entityTypes: ["reactionArrow"],
    domain: "CHEMISTRY",

    render(entity, context, isHovered = false): void {
        const { ctx, theme, zoom, worldToScreen } = context;
        const screen = worldToScreen(entity.x, entity.y);

        const color = entity.color ?? theme.foreground;
        const length = (entity.width ?? 80) * zoom;

        if (isHovered) {
            applyGlow(ctx, color, 10);
        }

        const startX = screen.x - length / 2;
        const endX = screen.x + length / 2;
        const y = screen.y;

        switch (entity.arrowType) {
            case "forward":
                drawArrow(ctx, startX, y, endX, y, { color, width: 2 }, {
                    size: 12 * zoom,
                    angle: Math.PI / 6,
                    filled: true,
                });
                break;

            case "reverse":
                drawArrow(ctx, endX, y, startX, y, { color, width: 2 }, {
                    size: 12 * zoom,
                    angle: Math.PI / 6,
                    filled: true,
                });
                break;

            case "equilibrium":
                // Double arrows
                drawArrow(
                    ctx,
                    startX,
                    y - 4 * zoom,
                    endX,
                    y - 4 * zoom,
                    { color, width: 1.5 },
                    { size: 8 * zoom, angle: Math.PI / 6, filled: true }
                );
                drawArrow(
                    ctx,
                    endX,
                    y + 4 * zoom,
                    startX,
                    y + 4 * zoom,
                    { color, width: 1.5 },
                    { size: 8 * zoom, angle: Math.PI / 6, filled: true }
                );
                break;

            case "resonance":
                drawBidirectionalArrow(ctx, startX, y, endX, y, { color, width: 2 }, {
                    size: 10 * zoom,
                    angle: Math.PI / 6,
                    filled: true,
                });
                break;
        }

        clearGlow(ctx);

        // Draw conditions above arrow
        if (entity.conditions && entity.conditions.length > 0) {
            const conditionsText = entity.conditions.join(", ");
            drawText(ctx, conditionsText, screen.x, y - 18 * zoom, {
                color: theme.neutral,
                fontSize: 10 * zoom,
            });
        }

        // Draw catalyst below arrow
        if (entity.catalyst) {
            drawText(ctx, entity.catalyst, screen.x, y + 18 * zoom, {
                color: theme.neutral,
                fontSize: 10 * zoom,
            });
        }
    },

    hitTest(entity, worldX, worldY): boolean {
        const width = entity.width ?? 80;
        const dx = Math.abs(worldX - entity.x);
        const dy = Math.abs(worldY - entity.y);
        return dx < width / 2 && dy < 20;
    },

    getBounds(entity): { x: number; y: number; width: number; height: number } {
        const width = entity.width ?? 80;
        return {
            x: entity.x - width / 2,
            y: entity.y - 20,
            width,
            height: 40,
        };
    },
};

// =============================================================================
// Energy Profile Renderer
// =============================================================================

/**
 * Renderer for reaction energy profiles.
 */
export const chemEnergyProfileRenderer: EntityRenderer<ChemEnergyProfileEntity> = {
    entityTypes: ["energyProfile"],
    domain: "CHEMISTRY",

    render(entity, context): void {
        const { ctx, theme, zoom, worldToScreen } = context;
        const screen = worldToScreen(entity.x, entity.y);

        if (!entity.points || entity.points.length < 2) return;

        const width = (entity.width ?? 200) * zoom;
        const height = (entity.height ?? 150) * zoom;
        const left = screen.x - width / 2;
        const top = screen.y - height / 2;

        // Find min/max for scaling
        const minY = Math.min(...entity.points.map((p) => p.y));
        const maxY = Math.max(...entity.points.map((p) => p.y));
        const minX = Math.min(...entity.points.map((p) => p.x));
        const maxX = Math.max(...entity.points.map((p) => p.x));

        const scaleX = (v: number) => left + ((v - minX) / (maxX - minX)) * width;
        const scaleY = (v: number) =>
            top + height - ((v - minY) / (maxY - minY)) * height;

        // Draw axes
        drawLine(ctx, left, top + height, left + width, top + height, {
            color: theme.border,
            width: 1,
        });
        drawLine(ctx, left, top, left, top + height, {
            color: theme.border,
            width: 1,
        });

        // Draw energy profile curve
        ctx.beginPath();
        const firstPoint = entity.points[0]!;
        ctx.moveTo(scaleX(firstPoint.x), scaleY(firstPoint.y));

        for (let i = 1; i < entity.points.length; i++) {
            const point = entity.points[i]!;
            ctx.lineTo(scaleX(point.x), scaleY(point.y));
        }

        ctx.strokeStyle = theme.primary;
        ctx.lineWidth = 2 * zoom;
        ctx.stroke();

        // Draw point labels
        for (const point of entity.points) {
            if (point.label) {
                drawText(ctx, point.label, scaleX(point.x), scaleY(point.y) - 10 * zoom, {
                    color: theme.foreground,
                    fontSize: 9 * zoom,
                });
            }
        }

        // Draw activation energy label
        if (entity.activationEnergy !== undefined) {
            drawText(
                ctx,
                `Ea = ${entity.activationEnergy}`,
                left + width - 40 * zoom,
                top + 15 * zoom,
                {
                    color: theme.danger,
                    fontSize: 10 * zoom,
                }
            );
        }

        // Draw deltaH label
        if (entity.deltaH !== undefined) {
            const sign = entity.deltaH >= 0 ? "+" : "";
            drawText(
                ctx,
                `ΔH = ${sign}${entity.deltaH}`,
                left + width - 40 * zoom,
                top + 30 * zoom,
                {
                    color: entity.deltaH >= 0 ? theme.danger : theme.success,
                    fontSize: 10 * zoom,
                }
            );
        }

        // Axis labels
        drawText(ctx, "Reaction Progress", screen.x, top + height + 20 * zoom, {
            color: theme.neutral,
            fontSize: 10 * zoom,
        });

        ctx.save();
        ctx.translate(left - 20 * zoom, screen.y);
        ctx.rotate(-Math.PI / 2);
        drawText(ctx, "Energy", 0, 0, {
            color: theme.neutral,
            fontSize: 10 * zoom,
        });
        ctx.restore();
    },

    hitTest(entity, worldX, worldY): boolean {
        const width = entity.width ?? 200;
        const height = entity.height ?? 150;
        const dx = Math.abs(worldX - entity.x);
        const dy = Math.abs(worldY - entity.y);
        return dx < width / 2 && dy < height / 2;
    },

    getBounds(entity): { x: number; y: number; width: number; height: number } {
        const width = entity.width ?? 200;
        const height = entity.height ?? 150;
        return {
            x: entity.x - width / 2,
            y: entity.y - height / 2,
            width,
            height,
        };
    },
};

// =============================================================================
// Exports
// =============================================================================

export const chemistryRenderers = [
    chemAtomRenderer,
    chemBondRenderer,
    chemMoleculeRenderer,
    chemReactionArrowRenderer,
    chemEnergyProfileRenderer,
];
