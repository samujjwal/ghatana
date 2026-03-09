/**
 * Medicine Renderer - Pharmacokinetics & Epidemiology
 *
 * @doc.type module
 * @doc.purpose Render medicine simulation entities (compartments, doses, infection agents)
 * @doc.layer product
 * @doc.pattern Renderer
 */
import { drawCircle, drawRect, drawText, drawLine, applyGlow, clearGlow, } from "../primitives";
// =============================================================================
// PK Compartment Renderer
// =============================================================================
/**
 * Renderer for pharmacokinetic compartments.
 */
export const medCompartmentRenderer = {
    entityTypes: ["pkCompartment"],
    domain: "MEDICINE",
    render(entity, context, isHovered = false, isSelected = false) {
        const { ctx, theme, zoom, worldToScreen } = context;
        const screen = worldToScreen(entity.x, entity.y);
        const width = (entity.width ?? 100) * zoom;
        const height = (entity.height ?? 80) * zoom;
        // Color based on compartment type
        let color;
        switch (entity.compartmentType) {
            case "central":
                color = theme.primary;
                break;
            case "peripheral":
                color = theme.secondary;
                break;
            case "effect":
                color = theme.success;
                break;
            default:
                color = theme.neutral;
        }
        if (isHovered || isSelected) {
            applyGlow(ctx, color, isSelected ? 20 : 12);
        }
        // Draw compartment box
        drawRect(ctx, screen.x, screen.y, width, height, { color, opacity: 0.3 }, { color, width: 2 }, 8);
        clearGlow(ctx);
        // Draw compartment type label
        drawText(ctx, entity.compartmentType.toUpperCase(), screen.x, screen.y - height / 2 + 12 * zoom, {
            color: theme.foreground,
            fontSize: 10 * zoom,
            fontWeight: "600",
        });
        // Draw concentration level (fill)
        const maxConcentration = 100; // Assume max for visualization
        const fillRatio = Math.min(entity.concentration / maxConcentration, 1);
        const fillHeight = (height - 30 * zoom) * fillRatio;
        ctx.fillStyle = `${color}60`;
        ctx.fillRect(screen.x - width / 2 + 5 * zoom, screen.y + height / 2 - 5 * zoom - fillHeight, width - 10 * zoom, fillHeight);
        // Draw concentration value
        drawText(ctx, `C: ${entity.concentration.toFixed(1)}`, screen.x, screen.y, {
            color: theme.foreground,
            fontSize: 12 * zoom,
            fontWeight: "500",
        });
        // Draw volume
        drawText(ctx, `V: ${entity.volume}L`, screen.x, screen.y + 15 * zoom, {
            color: theme.neutral,
            fontSize: 9 * zoom,
        });
        // Draw rate constants if present
        if (entity.ke !== undefined) {
            drawText(ctx, `ke: ${entity.ke}`, screen.x - width / 2 - 30 * zoom, screen.y, {
                color: theme.danger,
                fontSize: 8 * zoom,
                align: "right",
            });
        }
        if (entity.k12 !== undefined) {
            drawText(ctx, `k12: ${entity.k12}`, screen.x + width / 2 + 10 * zoom, screen.y - 10 * zoom, {
                color: theme.neutral,
                fontSize: 8 * zoom,
                align: "left",
            });
        }
        if (entity.k21 !== undefined) {
            drawText(ctx, `k21: ${entity.k21}`, screen.x + width / 2 + 10 * zoom, screen.y + 10 * zoom, {
                color: theme.neutral,
                fontSize: 8 * zoom,
                align: "left",
            });
        }
        // Draw label
        if (entity.label) {
            drawText(ctx, entity.label, screen.x, screen.y + height / 2 + 15 * zoom, {
                color: theme.foreground,
                fontSize: 10 * zoom,
                fontWeight: "500",
            });
        }
    },
    hitTest(entity, worldX, worldY) {
        const width = entity.width ?? 100;
        const height = entity.height ?? 80;
        const dx = Math.abs(worldX - entity.x);
        const dy = Math.abs(worldY - entity.y);
        return dx < width / 2 && dy < height / 2;
    },
    getBounds(entity) {
        const width = entity.width ?? 100;
        const height = entity.height ?? 80;
        return {
            x: entity.x - width / 2,
            y: entity.y - height / 2,
            width,
            height,
        };
    },
};
// =============================================================================
// Dose Renderer
// =============================================================================
/**
 * Renderer for drug doses.
 */
export const medDoseRenderer = {
    entityTypes: ["dose"],
    domain: "MEDICINE",
    render(entity, context, isHovered = false) {
        const { ctx, theme, zoom, worldToScreen } = context;
        const screen = worldToScreen(entity.x, entity.y);
        const size = (entity.scale ?? 1) * 30 * zoom;
        // Color and shape based on route
        let color;
        let shape;
        switch (entity.route) {
            case "iv":
                color = theme.danger;
                shape = "syringe";
                break;
            case "oral":
                color = theme.primary;
                shape = "pill";
                break;
            case "im":
            case "sc":
                color = theme.warning;
                shape = "syringe";
                break;
            case "topical":
                color = theme.success;
                shape = "patch";
                break;
            default:
                color = theme.neutral;
                shape = "pill";
        }
        if (isHovered) {
            applyGlow(ctx, color, 12);
        }
        switch (shape) {
            case "syringe":
                // Draw syringe shape
                drawRect(ctx, screen.x, screen.y, size * 2, size * 0.6, { color }, { color: theme.border, width: 1 }, 4);
                // Plunger
                drawRect(ctx, screen.x - size * 1.2, screen.y, size * 0.4, size * 0.5, { color: theme.neutral });
                // Needle
                drawLine(ctx, screen.x + size, screen.y, screen.x + size * 1.5, screen.y, {
                    color: theme.neutral,
                    width: 2,
                });
                break;
            case "pill":
                // Draw capsule/pill shape
                ctx.beginPath();
                ctx.ellipse(screen.x, screen.y, size, size * 0.5, 0, 0, Math.PI * 2);
                ctx.fillStyle = color;
                ctx.fill();
                ctx.strokeStyle = theme.border;
                ctx.lineWidth = 1;
                ctx.stroke();
                // Divider line
                drawLine(ctx, screen.x, screen.y - size * 0.5, screen.x, screen.y + size * 0.5, {
                    color: theme.background,
                    width: 2,
                });
                break;
            case "patch":
                // Draw square patch
                drawRect(ctx, screen.x, screen.y, size * 1.5, size * 1.5, { color, opacity: 0.7 }, { color: theme.border, width: 1 });
                // Cross pattern
                drawLine(ctx, screen.x - size * 0.5, screen.y, screen.x + size * 0.5, screen.y, {
                    color: theme.background,
                    width: 1,
                });
                drawLine(ctx, screen.x, screen.y - size * 0.5, screen.x, screen.y + size * 0.5, {
                    color: theme.background,
                    width: 1,
                });
                break;
        }
        clearGlow(ctx);
        // Draw dose amount
        drawText(ctx, `${entity.amount}mg`, screen.x, screen.y + size + 10 * zoom, {
            color: theme.foreground,
            fontSize: 10 * zoom,
            fontWeight: "500",
        });
        // Draw route label
        drawText(ctx, entity.route.toUpperCase(), screen.x, screen.y - size - 5 * zoom, {
            color: theme.neutral,
            fontSize: 8 * zoom,
        });
        // Draw bioavailability if present
        if (entity.bioavailability !== undefined) {
            drawText(ctx, `F: ${(entity.bioavailability * 100).toFixed(0)}%`, screen.x, screen.y + size + 22 * zoom, {
                color: theme.neutral,
                fontSize: 8 * zoom,
            });
        }
    },
    hitTest(entity, worldX, worldY) {
        const size = (entity.scale ?? 1) * 30;
        const dx = Math.abs(worldX - entity.x);
        const dy = Math.abs(worldY - entity.y);
        return dx < size * 1.5 && dy < size;
    },
    getBounds(entity) {
        const size = (entity.scale ?? 1) * 30;
        return {
            x: entity.x - size * 1.5,
            y: entity.y - size,
            width: size * 3,
            height: size * 2,
        };
    },
};
// =============================================================================
// Infection Agent Renderer
// =============================================================================
/**
 * Renderer for infection agents (epidemiology).
 */
export const medInfectionAgentRenderer = {
    entityTypes: ["infectionAgent"],
    domain: "MEDICINE",
    render(entity, context, isHovered = false) {
        const { ctx, theme, zoom, worldToScreen } = context;
        const screen = worldToScreen(entity.x, entity.y);
        // Size based on population
        const baseSize = 25 * zoom;
        const popRatio = Math.min(Math.log10(entity.population + 1) / 6, 1);
        const size = baseSize * (0.5 + popRatio);
        // Color based on agent type
        let color;
        switch (entity.agentType) {
            case "virus":
                color = theme.danger;
                break;
            case "bacteria":
                color = theme.warning;
                break;
            case "parasite":
                color = theme.secondary;
                break;
            case "fungus":
                color = theme.success;
                break;
            default:
                color = theme.neutral;
        }
        if (isHovered) {
            applyGlow(ctx, color, 15);
        }
        // Draw based on type
        switch (entity.agentType) {
            case "virus":
                // Icosahedral shape with spikes
                drawCircle(ctx, screen.x, screen.y, size, { color, opacity: 0.8 }, { color: theme.border, width: 1 });
                // Draw spikes
                for (let i = 0; i < 8; i++) {
                    const angle = (i / 8) * Math.PI * 2;
                    const spikeLength = size * 0.4;
                    const startX = screen.x + (size - 2) * Math.cos(angle);
                    const startY = screen.y + (size - 2) * Math.sin(angle);
                    const endX = screen.x + (size + spikeLength) * Math.cos(angle);
                    const endY = screen.y + (size + spikeLength) * Math.sin(angle);
                    drawLine(ctx, startX, startY, endX, endY, { color, width: 2 });
                    drawCircle(ctx, endX, endY, 3 * zoom, { color });
                }
                break;
            case "bacteria":
                // Rod shape
                ctx.beginPath();
                ctx.ellipse(screen.x, screen.y, size * 1.5, size * 0.6, 0, 0, Math.PI * 2);
                ctx.fillStyle = color;
                ctx.globalAlpha = 0.8;
                ctx.fill();
                ctx.strokeStyle = theme.border;
                ctx.lineWidth = 1;
                ctx.stroke();
                ctx.globalAlpha = 1;
                // Flagella
                ctx.beginPath();
                ctx.moveTo(screen.x + size * 1.5, screen.y);
                for (let i = 0; i < 3; i++) {
                    const x = screen.x + size * 1.5 + i * 8 * zoom;
                    const y = screen.y + (i % 2 === 0 ? -1 : 1) * 8 * zoom;
                    ctx.lineTo(x, y);
                }
                ctx.strokeStyle = color;
                ctx.lineWidth = 1.5;
                ctx.stroke();
                break;
            case "parasite":
                // Amoeba-like shape
                ctx.beginPath();
                for (let i = 0; i <= 12; i++) {
                    const angle = (i / 12) * Math.PI * 2;
                    const wobble = 1 + 0.3 * Math.sin(angle * 3);
                    const r = size * wobble;
                    const x = screen.x + r * Math.cos(angle);
                    const y = screen.y + r * Math.sin(angle);
                    if (i === 0)
                        ctx.moveTo(x, y);
                    else
                        ctx.lineTo(x, y);
                }
                ctx.closePath();
                ctx.fillStyle = color;
                ctx.globalAlpha = 0.7;
                ctx.fill();
                ctx.strokeStyle = theme.border;
                ctx.lineWidth = 1;
                ctx.stroke();
                ctx.globalAlpha = 1;
                // Nucleus
                drawCircle(ctx, screen.x - size * 0.2, screen.y, size * 0.3, { color: theme.foreground, opacity: 0.5 });
                break;
            case "fungus":
                // Branching hyphae
                ctx.strokeStyle = color;
                ctx.lineWidth = 3 * zoom;
                ctx.lineCap = "round";
                // Main stem
                drawLine(ctx, screen.x, screen.y + size, screen.x, screen.y - size, { color, width: 3 * zoom });
                // Branches
                for (let i = 0; i < 3; i++) {
                    const y = screen.y - size * 0.3 * i;
                    const branchLen = size * 0.6 * (1 - i * 0.2);
                    drawLine(ctx, screen.x, y, screen.x - branchLen, y - branchLen * 0.5, { color, width: 2 * zoom });
                    drawLine(ctx, screen.x, y, screen.x + branchLen, y - branchLen * 0.5, { color, width: 2 * zoom });
                }
                // Spore head
                drawCircle(ctx, screen.x, screen.y - size, size * 0.3, { color }, { color: theme.border, width: 1 });
                break;
        }
        clearGlow(ctx);
        // Draw population count
        const popText = entity.population >= 1e6 ? `${(entity.population / 1e6).toFixed(1)}M` : entity.population >= 1e3 ? `${(entity.population / 1e3).toFixed(1)}K` : String(entity.population);
        drawText(ctx, popText, screen.x, screen.y + size * 1.5 + 10 * zoom, {
            color: theme.foreground,
            fontSize: 10 * zoom,
            fontWeight: "500",
        });
        // Draw reproduction rate if present
        if (entity.reproductionRate !== undefined) {
            drawText(ctx, `R₀: ${entity.reproductionRate.toFixed(1)}`, screen.x, screen.y + size * 1.5 + 22 * zoom, {
                color: entity.reproductionRate > 1 ? theme.danger : theme.success,
                fontSize: 8 * zoom,
            });
        }
        // Draw label
        if (entity.label) {
            drawText(ctx, entity.label, screen.x, screen.y - size * 1.5 - 5 * zoom, {
                color: theme.neutral,
                fontSize: 9 * zoom,
            });
        }
    },
    hitTest(entity, worldX, worldY) {
        const baseSize = 25;
        const popRatio = Math.min(Math.log10(entity.population + 1) / 6, 1);
        const size = baseSize * (0.5 + popRatio);
        const dx = worldX - entity.x;
        const dy = worldY - entity.y;
        return dx * dx + dy * dy <= size * size * 2;
    },
    getBounds(entity) {
        const baseSize = 25;
        const popRatio = Math.min(Math.log10(entity.population + 1) / 6, 1);
        const size = baseSize * (0.5 + popRatio) * 1.5;
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
export const medicineRenderers = [
    medCompartmentRenderer,
    medDoseRenderer,
    medInfectionAgentRenderer,
];
//# sourceMappingURL=MedicineRenderer.js.map