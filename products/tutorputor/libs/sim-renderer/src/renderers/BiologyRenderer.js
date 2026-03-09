/**
 * Biology Renderer - Cells & Molecular Biology
 *
 * @doc.type module
 * @doc.purpose Render biology simulation entities (cells, organelles, enzymes, genes)
 * @doc.layer product
 * @doc.pattern Renderer
 */
import { drawCircle, drawEllipse, drawRect, drawText, drawLine, applyGlow, clearGlow, } from "../primitives";
// =============================================================================
// Organelle Colors
// =============================================================================
const ORGANELLE_COLORS = {
    nucleus: "#4A5568",
    mitochondria: "#F56565",
    ribosome: "#805AD5",
    er: "#38B2AC",
    golgi: "#ECC94B",
    lysosome: "#ED8936",
    chloroplast: "#48BB78",
    vacuole: "#63B3ED",
    default: "#A0AEC0",
};
// =============================================================================
// Cell Renderer
// =============================================================================
/**
 * Renderer for biological cells.
 */
export const bioCellRenderer = {
    entityTypes: ["cell"],
    domain: "BIOLOGY",
    render(entity, context, isHovered = false, isSelected = false) {
        const { ctx, theme, zoom, worldToScreen } = context;
        const screen = worldToScreen(entity.x, entity.y);
        const width = (entity.width ?? 200) * zoom;
        const height = (entity.height ?? 150) * zoom;
        const color = entity.color ?? theme.primary;
        if (isHovered || isSelected) {
            applyGlow(ctx, color, isSelected ? 25 : 15);
        }
        // Draw cell membrane (double line for lipid bilayer)
        ctx.globalAlpha = entity.opacity ?? 1;
        // Outer membrane
        ctx.beginPath();
        ctx.ellipse(screen.x, screen.y, width / 2, height / 2, 0, 0, Math.PI * 2);
        ctx.strokeStyle = color;
        ctx.lineWidth = 4 * zoom;
        ctx.stroke();
        // Inner membrane (for eukaryotes)
        if (entity.cellType === "eukaryote" || entity.cellType === "plant" || entity.cellType === "animal") {
            ctx.beginPath();
            ctx.ellipse(screen.x, screen.y, width / 2 - 4 * zoom, height / 2 - 4 * zoom, 0, 0, Math.PI * 2);
            ctx.strokeStyle = color;
            ctx.lineWidth = 2 * zoom;
            ctx.setLineDash([4, 2]);
            ctx.stroke();
            ctx.setLineDash([]);
        }
        // Cytoplasm fill
        ctx.beginPath();
        ctx.ellipse(screen.x, screen.y, width / 2 - 6 * zoom, height / 2 - 6 * zoom, 0, 0, Math.PI * 2);
        ctx.fillStyle = `${color}20`;
        ctx.fill();
        // Cell wall for plant cells
        if (entity.cellType === "plant") {
            ctx.beginPath();
            ctx.ellipse(screen.x, screen.y, width / 2 + 8 * zoom, height / 2 + 8 * zoom, 0, 0, Math.PI * 2);
            ctx.strokeStyle = theme.success;
            ctx.lineWidth = 6 * zoom;
            ctx.stroke();
        }
        ctx.globalAlpha = 1;
        clearGlow(ctx);
        // Draw label
        if (entity.label) {
            drawText(ctx, entity.label, screen.x, screen.y + height / 2 + 20 * zoom, {
                color: theme.foreground,
                fontSize: 12 * zoom,
                fontWeight: "600",
            });
        }
        // Draw cell type
        if (entity.cellType) {
            drawText(ctx, entity.cellType, screen.x, screen.y - height / 2 - 12 * zoom, {
                color: theme.neutral,
                fontSize: 10 * zoom,
            });
        }
    },
    hitTest(entity, worldX, worldY) {
        const width = entity.width ?? 200;
        const height = entity.height ?? 150;
        const dx = (worldX - entity.x) / (width / 2);
        const dy = (worldY - entity.y) / (height / 2);
        return dx * dx + dy * dy <= 1;
    },
    getBounds(entity) {
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
// Organelle Renderer
// =============================================================================
/**
 * Renderer for cell organelles.
 */
export const bioOrganelleRenderer = {
    entityTypes: ["organelle"],
    domain: "BIOLOGY",
    render(entity, context, isHovered = false) {
        const { ctx, theme, zoom, worldToScreen } = context;
        const screen = worldToScreen(entity.x, entity.y);
        const color = entity.color ?? ORGANELLE_COLORS[entity.organelleType] ?? ORGANELLE_COLORS.default;
        const size = (entity.scale ?? 1) * 30 * zoom;
        if (isHovered) {
            applyGlow(ctx, color, 12);
        }
        const fill = { color, opacity: entity.opacity ?? 0.8 };
        const stroke = { color: theme.border, width: 1.5 };
        switch (entity.organelleType) {
            case "nucleus":
                // Large circular with double membrane
                drawCircle(ctx, screen.x, screen.y, size * 1.5, fill, stroke);
                drawCircle(ctx, screen.x, screen.y, size * 1.2, undefined, {
                    color,
                    width: 1,
                    dash: [3, 2],
                });
                // Nucleolus
                drawCircle(ctx, screen.x + size * 0.3, screen.y - size * 0.2, size * 0.4, {
                    color: `${color}CC`,
                });
                break;
            case "mitochondria":
                // Elongated with inner folds
                drawEllipse(ctx, screen.x, screen.y, size * 1.5, size * 0.8, fill, stroke);
                // Cristae (inner folds)
                for (let i = -3; i <= 3; i++) {
                    const xOffset = i * size * 0.35;
                    drawLine(ctx, screen.x + xOffset, screen.y - size * 0.5, screen.x + xOffset, screen.y + size * 0.3, {
                        color: theme.foreground,
                        width: 1,
                    });
                }
                break;
            case "ribosome":
                // Small circular
                drawCircle(ctx, screen.x, screen.y, size * 0.5, fill, stroke);
                break;
            case "er":
                // Wavy sheets
                ctx.beginPath();
                ctx.moveTo(screen.x - size * 1.5, screen.y);
                for (let i = 0; i < 4; i++) {
                    const x1 = screen.x - size * 1.5 + i * size * 0.75;
                    const x2 = x1 + size * 0.75;
                    ctx.quadraticCurveTo(x1 + size * 0.375, screen.y - size * 0.5, x2, screen.y);
                }
                ctx.strokeStyle = color;
                ctx.lineWidth = 3 * zoom;
                ctx.stroke();
                break;
            case "golgi":
                // Stacked cisternae
                for (let i = 0; i < 4; i++) {
                    const yOffset = (i - 1.5) * size * 0.4;
                    const curveAmount = (i - 1.5) * size * 0.1;
                    ctx.beginPath();
                    ctx.moveTo(screen.x - size, screen.y + yOffset);
                    ctx.quadraticCurveTo(screen.x, screen.y + yOffset + curveAmount, screen.x + size, screen.y + yOffset);
                    ctx.strokeStyle = color;
                    ctx.lineWidth = 4 * zoom;
                    ctx.lineCap = "round";
                    ctx.stroke();
                }
                break;
            case "lysosome":
                // Circular with rough edge
                drawCircle(ctx, screen.x, screen.y, size, fill, stroke);
                // Dots inside for enzymes
                for (let i = 0; i < 5; i++) {
                    const angle = (i / 5) * Math.PI * 2;
                    const r = size * 0.5;
                    drawCircle(ctx, screen.x + r * Math.cos(angle), screen.y + r * Math.sin(angle), size * 0.15, {
                        color: theme.foreground,
                    });
                }
                break;
            case "chloroplast":
                // Large ellipse with thylakoids
                drawEllipse(ctx, screen.x, screen.y, size * 1.8, size, fill, stroke);
                // Thylakoid stacks (grana)
                for (let i = -1; i <= 1; i++) {
                    const xOffset = i * size * 0.9;
                    for (let j = 0; j < 3; j++) {
                        const yOffset = (j - 1) * size * 0.25;
                        drawEllipse(ctx, screen.x + xOffset, screen.y + yOffset, size * 0.3, size * 0.1, {
                            color: `${color}AA`,
                        });
                    }
                }
                break;
            case "vacuole":
                // Large circular
                drawCircle(ctx, screen.x, screen.y, size * 2, { color, opacity: 0.3 }, stroke);
                break;
            default:
                drawCircle(ctx, screen.x, screen.y, size, fill, stroke);
        }
        clearGlow(ctx);
        // Draw activity indicator
        if (entity.activity !== undefined && entity.activity > 0) {
            const activitySize = size * 0.3 * entity.activity;
            ctx.beginPath();
            ctx.arc(screen.x + size, screen.y - size, activitySize, 0, Math.PI * 2);
            ctx.fillStyle = theme.success;
            ctx.fill();
        }
        // Draw label
        if (entity.label) {
            drawText(ctx, entity.label, screen.x, screen.y + size + 12 * zoom, {
                color: theme.foreground,
                fontSize: 9 * zoom,
            });
        }
    },
    hitTest(entity, worldX, worldY) {
        const size = (entity.scale ?? 1) * 30;
        const dx = worldX - entity.x;
        const dy = worldY - entity.y;
        return dx * dx + dy * dy <= size * size * 2;
    },
    getBounds(entity) {
        const size = (entity.scale ?? 1) * 30 * 2;
        return {
            x: entity.x - size,
            y: entity.y - size,
            width: size * 2,
            height: size * 2,
        };
    },
};
// =============================================================================
// Compartment Renderer
// =============================================================================
/**
 * Renderer for biological compartments (reaction chambers).
 */
export const bioCompartmentRenderer = {
    entityTypes: ["compartment"],
    domain: "BIOLOGY",
    render(entity, context, isHovered = false) {
        const { ctx, theme, zoom, worldToScreen } = context;
        const screen = worldToScreen(entity.x, entity.y);
        const width = (entity.width ?? 120) * zoom;
        const height = (entity.height ?? 80) * zoom;
        const color = entity.color ?? theme.secondary;
        if (isHovered) {
            applyGlow(ctx, color, 10);
        }
        // Draw compartment box
        drawRect(ctx, screen.x, screen.y, width, height, { color, opacity: 0.2 }, { color, width: 2 }, 8);
        clearGlow(ctx);
        // Draw volume label
        drawText(ctx, `V=${entity.volume}`, screen.x, screen.y - height / 2 - 10 * zoom, {
            color: theme.neutral,
            fontSize: 9 * zoom,
        });
        // Draw concentration bars
        const concentrations = Object.entries(entity.concentration);
        const barWidth = (width - 20 * zoom) / Math.max(concentrations.length, 1);
        concentrations.forEach(([name, value], index) => {
            const barX = screen.x - width / 2 + 10 * zoom + index * barWidth + barWidth / 2;
            const maxHeight = height * 0.6;
            const barHeight = Math.min(value / 100, 1) * maxHeight;
            // Bar background
            drawRect(ctx, barX, screen.y + height / 4, barWidth * 0.6, maxHeight, { color: theme.border, opacity: 0.3 });
            // Bar fill
            drawRect(ctx, barX, screen.y + height / 4 + (maxHeight - barHeight) / 2, barWidth * 0.6, barHeight, {
                color: theme.primary,
                opacity: 0.8,
            });
            // Label
            drawText(ctx, name, barX, screen.y + height / 2 + 8 * zoom, {
                color: theme.neutral,
                fontSize: 8 * zoom,
            });
        });
        // Draw label
        if (entity.label) {
            drawText(ctx, entity.label, screen.x, screen.y + height / 2 + 20 * zoom, {
                color: theme.foreground,
                fontSize: 10 * zoom,
                fontWeight: "500",
            });
        }
    },
    hitTest(entity, worldX, worldY) {
        const width = entity.width ?? 120;
        const height = entity.height ?? 80;
        const dx = Math.abs(worldX - entity.x);
        const dy = Math.abs(worldY - entity.y);
        return dx < width / 2 && dy < height / 2;
    },
    getBounds(entity) {
        const width = entity.width ?? 120;
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
// Enzyme Renderer
// =============================================================================
/**
 * Renderer for enzymes.
 */
export const bioEnzymeRenderer = {
    entityTypes: ["enzyme"],
    domain: "BIOLOGY",
    render(entity, context, isHovered = false) {
        const { ctx, theme, zoom, worldToScreen } = context;
        const screen = worldToScreen(entity.x, entity.y);
        const size = (entity.scale ?? 1) * 40 * zoom;
        const color = entity.color ?? (entity.active ? theme.success : theme.neutral);
        if (isHovered) {
            applyGlow(ctx, color, 12);
        }
        // Draw enzyme body (pac-man like shape for active site)
        ctx.beginPath();
        const mouthAngle = entity.active ? 0.3 : 0.1;
        ctx.arc(screen.x, screen.y, size, mouthAngle, Math.PI * 2 - mouthAngle);
        ctx.lineTo(screen.x, screen.y);
        ctx.closePath();
        ctx.fillStyle = color;
        ctx.globalAlpha = entity.opacity ?? 1;
        ctx.fill();
        ctx.strokeStyle = theme.border;
        ctx.lineWidth = 2;
        ctx.stroke();
        ctx.globalAlpha = 1;
        clearGlow(ctx);
        // Draw name
        drawText(ctx, entity.name, screen.x, screen.y, {
            color: theme.foreground,
            fontSize: 10 * zoom,
            fontWeight: "600",
        });
        // Draw substrate/product if present
        if (entity.substrate) {
            drawText(ctx, `S: ${entity.substrate}`, screen.x + size + 10 * zoom, screen.y - 8 * zoom, {
                color: theme.neutral,
                fontSize: 8 * zoom,
                align: "left",
            });
        }
        if (entity.product) {
            drawText(ctx, `P: ${entity.product}`, screen.x + size + 10 * zoom, screen.y + 8 * zoom, {
                color: theme.neutral,
                fontSize: 8 * zoom,
                align: "left",
            });
        }
        // Activity indicator
        if (entity.active) {
            ctx.beginPath();
            ctx.arc(screen.x + size * 0.7, screen.y - size * 0.7, 5 * zoom, 0, Math.PI * 2);
            ctx.fillStyle = theme.success;
            ctx.fill();
        }
    },
    hitTest(entity, worldX, worldY) {
        const size = (entity.scale ?? 1) * 40;
        const dx = worldX - entity.x;
        const dy = worldY - entity.y;
        return dx * dx + dy * dy <= size * size;
    },
    getBounds(entity) {
        const size = (entity.scale ?? 1) * 40;
        return {
            x: entity.x - size,
            y: entity.y - size,
            width: size * 2,
            height: size * 2,
        };
    },
};
// =============================================================================
// Signal Renderer
// =============================================================================
/**
 * Renderer for signaling molecules.
 */
export const bioSignalRenderer = {
    entityTypes: ["signal"],
    domain: "BIOLOGY",
    render(entity, context) {
        const { ctx, theme, zoom, worldToScreen } = context;
        const screen = worldToScreen(entity.x, entity.y);
        // Size based on concentration
        const baseSize = 15 * zoom;
        const size = baseSize * Math.min(1 + entity.concentration / 100, 2);
        // Color based on signal type
        let color;
        switch (entity.signalType) {
            case "hormone":
                color = theme.primary;
                break;
            case "neurotransmitter":
                color = theme.warning;
                break;
            case "cytokine":
                color = theme.danger;
                break;
            case "ion":
                color = theme.success;
                break;
            default:
                color = theme.neutral;
        }
        // Draw signal particle with glow
        applyGlow(ctx, color, 8);
        drawCircle(ctx, screen.x, screen.y, size, { color, opacity: 0.8 });
        clearGlow(ctx);
        // Draw type indicator
        const typeSymbol = entity.signalType.charAt(0).toUpperCase();
        drawText(ctx, typeSymbol, screen.x, screen.y, {
            color: theme.foreground,
            fontSize: 8 * zoom,
            fontWeight: "bold",
        });
    },
    hitTest(entity, worldX, worldY) {
        const size = 15 * Math.min(1 + entity.concentration / 100, 2);
        const dx = worldX - entity.x;
        const dy = worldY - entity.y;
        return dx * dx + dy * dy <= size * size;
    },
    getBounds(entity) {
        const size = 15 * Math.min(1 + entity.concentration / 100, 2);
        return {
            x: entity.x - size,
            y: entity.y - size,
            width: size * 2,
            height: size * 2,
        };
    },
};
// =============================================================================
// Gene Renderer
// =============================================================================
/**
 * Renderer for genes.
 */
export const bioGeneRenderer = {
    entityTypes: ["gene"],
    domain: "BIOLOGY",
    render(entity, context, isHovered = false) {
        const { ctx, theme, zoom, worldToScreen } = context;
        const screen = worldToScreen(entity.x, entity.y);
        const width = (entity.width ?? 100) * zoom;
        const height = 20 * zoom;
        const color = entity.color ?? (entity.promoterActive ? theme.success : theme.neutral);
        if (isHovered) {
            applyGlow(ctx, color, 10);
        }
        // Draw gene body (arrow shape)
        ctx.beginPath();
        ctx.moveTo(screen.x - width / 2, screen.y - height / 2);
        ctx.lineTo(screen.x + width / 2 - height, screen.y - height / 2);
        ctx.lineTo(screen.x + width / 2, screen.y);
        ctx.lineTo(screen.x + width / 2 - height, screen.y + height / 2);
        ctx.lineTo(screen.x - width / 2, screen.y + height / 2);
        ctx.closePath();
        ctx.fillStyle = color;
        ctx.globalAlpha = entity.opacity ?? 0.8;
        ctx.fill();
        ctx.strokeStyle = theme.border;
        ctx.lineWidth = 1;
        ctx.stroke();
        ctx.globalAlpha = 1;
        clearGlow(ctx);
        // Draw promoter region
        const promoterWidth = 15 * zoom;
        ctx.fillStyle = entity.promoterActive ? theme.success : theme.danger;
        ctx.fillRect(screen.x - width / 2, screen.y - height / 2, promoterWidth, height);
        // Draw expression level bar
        if (entity.expressionLevel !== undefined) {
            const barY = screen.y + height / 2 + 5 * zoom;
            const barHeight = 4 * zoom;
            const exprWidth = width * entity.expressionLevel;
            drawRect(ctx, screen.x, barY, width, barHeight, { color: theme.border, opacity: 0.3 });
            drawRect(ctx, screen.x - width / 2 + exprWidth / 2, barY, exprWidth, barHeight, { color: theme.primary });
        }
        // Draw label
        if (entity.label) {
            drawText(ctx, entity.label, screen.x, screen.y, {
                color: theme.foreground,
                fontSize: 10 * zoom,
                fontWeight: "500",
            });
        }
        // Draw product
        if (entity.product) {
            drawText(ctx, `→ ${entity.product}`, screen.x + width / 2 + 20 * zoom, screen.y, {
                color: theme.neutral,
                fontSize: 9 * zoom,
                align: "left",
            });
        }
    },
    hitTest(entity, worldX, worldY) {
        const width = entity.width ?? 100;
        const height = 20;
        const dx = Math.abs(worldX - entity.x);
        const dy = Math.abs(worldY - entity.y);
        return dx < width / 2 && dy < height / 2;
    },
    getBounds(entity) {
        const width = entity.width ?? 100;
        const height = 20;
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
export const biologyRenderers = [
    bioCellRenderer,
    bioOrganelleRenderer,
    bioCompartmentRenderer,
    bioEnzymeRenderer,
    bioSignalRenderer,
    bioGeneRenderer,
];
//# sourceMappingURL=BiologyRenderer.js.map