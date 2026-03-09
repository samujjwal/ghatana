/**
 * Primitives Stories
 *
 * @doc.type stories
 * @doc.purpose Storybook stories for low-level drawing primitives
 * @doc.layer product
 * @doc.pattern Story
 */

import type { Meta, StoryObj } from '@storybook/react';
import React, { useRef, useEffect } from 'react';
import {
    drawRoundedRect,
    drawCircle,
    drawDiamond,
    drawHexagon,
    drawLine,
    drawArrow,
    drawBezierCurve,
    drawText,
    drawChemicalBond,
    drawVector,
    drawSpring,
} from '../primitives';

// Simple canvas wrapper for primitive demonstrations
interface PrimitiveCanvasProps {
    width?: number;
    height?: number;
    draw: (ctx: CanvasRenderingContext2D, width: number, height: number) => void;
    backgroundColor?: string;
}

const PrimitiveCanvas: React.FC<PrimitiveCanvasProps> = ({
    width = 400,
    height = 300,
    draw,
    backgroundColor = '#f8fafc',
}) => {
    const canvasRef = useRef<HTMLCanvasElement>(null);

    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;

        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        // Setup
        ctx.fillStyle = backgroundColor;
        ctx.fillRect(0, 0, width, height);

        // Translate to center
        ctx.save();
        ctx.translate(width / 2, height / 2);

        // Draw
        draw(ctx, width, height);

        ctx.restore();
    }, [draw, width, height, backgroundColor]);

    return (
        <canvas
            ref={canvasRef}
            width={width}
            height={height}
            style={{
                border: '1px solid #e2e8f0',
                borderRadius: '8px',
            }}
        />
    );
};

const meta: Meta<typeof PrimitiveCanvas> = {
    title: 'Simulation/Primitives',
    component: PrimitiveCanvas,
    parameters: {
        layout: 'centered',
        docs: {
            description: {
                component: 'Low-level drawing primitives used by domain renderers.',
            },
        },
    },
};

export default meta;
type Story = StoryObj<typeof PrimitiveCanvas>;

// =============================================================================
// Shapes
// =============================================================================

export const Shapes: Story = {
    args: {
        width: 500,
        height: 150,
        draw: (ctx) => {
            // Rounded Rectangle
            drawRoundedRect(ctx, -180, -30, 80, 60, 8, '#3b82f6', '#1e40af', 2);
            drawText(ctx, 'Rect', -140, 50, '#64748b', '12px sans-serif', 'center');

            // Circle
            drawCircle(ctx, -60, 0, 30, '#10b981', '#047857', 2);
            drawText(ctx, 'Circle', -60, 50, '#64748b', '12px sans-serif', 'center');

            // Diamond
            drawDiamond(ctx, 60, 0, 40, '#f59e0b', '#d97706', 2);
            drawText(ctx, 'Diamond', 60, 50, '#64748b', '12px sans-serif', 'center');

            // Hexagon
            drawHexagon(ctx, 180, 0, 30, '#8b5cf6', '#7c3aed', 2);
            drawText(ctx, 'Hexagon', 180, 50, '#64748b', '12px sans-serif', 'center');
        },
    },
    parameters: {
        docs: {
            description: {
                story: 'Basic shapes: rounded rectangle, circle, diamond, and hexagon.',
            },
        },
    },
};

// =============================================================================
// Lines
// =============================================================================

export const Lines: Story = {
    args: {
        width: 500,
        height: 200,
        draw: (ctx) => {
            // Simple line
            drawLine(ctx, -200, -50, -100, -50, '#64748b', 2);
            drawText(ctx, 'Line', -150, -30, '#64748b', '12px sans-serif', 'center');

            // Dashed line
            ctx.setLineDash([5, 5]);
            drawLine(ctx, -50, -50, 50, -50, '#64748b', 2);
            ctx.setLineDash([]);
            drawText(ctx, 'Dashed', 0, -30, '#64748b', '12px sans-serif', 'center');

            // Arrow
            drawArrow(ctx, 100, -50, 200, -50, '#3b82f6', 2, 10);
            drawText(ctx, 'Arrow', 150, -30, '#64748b', '12px sans-serif', 'center');

            // Bezier curve
            drawBezierCurve(
                ctx,
                -200, 50,
                -150, 0,
                -50, 100,
                0, 50,
                '#10b981',
                2
            );
            drawText(ctx, 'Bezier', -100, 80, '#64748b', '12px sans-serif', 'center');

            // Curved arrow
            drawArrow(ctx, 50, 50, 200, 50, '#f59e0b', 2, 10, true);
            drawText(ctx, 'Curved Arrow', 125, 30, '#64748b', '12px sans-serif', 'center');
        },
    },
    parameters: {
        docs: {
            description: {
                story: 'Line types: simple, dashed, arrows, and bezier curves.',
            },
        },
    },
};

// =============================================================================
// Vectors
// =============================================================================

export const Vectors: Story = {
    args: {
        width: 400,
        height: 300,
        draw: (ctx) => {
            // Origin point
            drawCircle(ctx, 0, 0, 8, '#1e293b', '#0f172a', 2);

            // Velocity vector
            drawVector(ctx, 0, 0, 80, -60, '#3b82f6', 2, 'v');

            // Force vector
            drawVector(ctx, 0, 0, 60, 80, '#ef4444', 2, 'F');

            // Acceleration vector
            drawVector(ctx, 0, 0, -70, -40, '#f59e0b', 2, 'a');

            // Labels
            drawText(ctx, 'Velocity (blue)', 80, -80, '#3b82f6', '12px sans-serif', 'left');
            drawText(ctx, 'Force (red)', 80, 80, '#ef4444', '12px sans-serif', 'left');
            drawText(ctx, 'Accel (yellow)', -140, -60, '#f59e0b', '12px sans-serif', 'left');
        },
    },
    parameters: {
        docs: {
            description: {
                story: 'Physics vectors with labels: velocity, force, and acceleration.',
            },
        },
    },
};

// =============================================================================
// Springs
// =============================================================================

export const Springs: Story = {
    args: {
        width: 500,
        height: 200,
        draw: (ctx) => {
            // Anchor points
            drawCircle(ctx, -180, 0, 10, '#1e293b', '#0f172a', 2);
            drawCircle(ctx, 180, 0, 10, '#1e293b', '#0f172a', 2);

            // Compressed spring
            drawSpring(ctx, -180, 0, -80, 0, 8, 6, '#ef4444', 2);
            drawText(ctx, 'Compressed', -130, 40, '#64748b', '12px sans-serif', 'center');

            // Resting spring
            drawSpring(ctx, -40, 0, 40, 0, 8, 6, '#10b981', 2);
            drawText(ctx, 'Resting', 0, 40, '#64748b', '12px sans-serif', 'center');

            // Stretched spring
            drawSpring(ctx, 80, 0, 180, 0, 8, 6, '#3b82f6', 2);
            drawText(ctx, 'Stretched', 130, 40, '#64748b', '12px sans-serif', 'center');
        },
    },
    parameters: {
        docs: {
            description: {
                story: 'Spring visualization: compressed, resting, and stretched states.',
            },
        },
    },
};

// =============================================================================
// Chemical Bonds
// =============================================================================

export const ChemicalBonds: Story = {
    args: {
        width: 500,
        height: 200,
        draw: (ctx) => {
            // Atom endpoints
            const bondY = -30;
            const atomColor = '#64748b';

            // Single bond
            drawCircle(ctx, -180, bondY, 15, atomColor, '#475569', 2);
            drawCircle(ctx, -100, bondY, 15, atomColor, '#475569', 2);
            drawChemicalBond(ctx, -165, bondY, -115, bondY, 'single', '#1e293b', 2);
            drawText(ctx, 'Single', -140, bondY + 50, '#64748b', '12px sans-serif', 'center');

            // Double bond
            drawCircle(ctx, -40, bondY, 15, atomColor, '#475569', 2);
            drawCircle(ctx, 40, bondY, 15, atomColor, '#475569', 2);
            drawChemicalBond(ctx, -25, bondY, 25, bondY, 'double', '#1e293b', 2);
            drawText(ctx, 'Double', 0, bondY + 50, '#64748b', '12px sans-serif', 'center');

            // Triple bond
            drawCircle(ctx, 100, bondY, 15, atomColor, '#475569', 2);
            drawCircle(ctx, 180, bondY, 15, atomColor, '#475569', 2);
            drawChemicalBond(ctx, 115, bondY, 165, bondY, 'triple', '#1e293b', 2);
            drawText(ctx, 'Triple', 140, bondY + 50, '#64748b', '12px sans-serif', 'center');
        },
    },
    parameters: {
        docs: {
            description: {
                story: 'Chemical bond types: single, double, and triple bonds.',
            },
        },
    },
};

// =============================================================================
// Text Styles
// =============================================================================

export const TextStyles: Story = {
    args: {
        width: 400,
        height: 250,
        draw: (ctx) => {
            drawText(ctx, 'Left Aligned', -150, -80, '#1e293b', '16px sans-serif', 'left');
            drawText(ctx, 'Center Aligned', 0, -40, '#1e293b', '16px sans-serif', 'center');
            drawText(ctx, 'Right Aligned', 150, 0, '#1e293b', '16px sans-serif', 'right');

            drawText(ctx, 'Small Text', 0, 40, '#64748b', '12px sans-serif', 'center');
            drawText(ctx, 'Bold Text', 0, 70, '#1e293b', 'bold 16px sans-serif', 'center');
            drawText(ctx, 'Subscript₂', -60, 100, '#3b82f6', '14px sans-serif', 'center');
            drawText(ctx, 'Superscript²⁺', 60, 100, '#ef4444', '14px sans-serif', 'center');
        },
    },
    parameters: {
        docs: {
            description: {
                story: 'Text rendering with different alignments and styles.',
            },
        },
    },
};

// =============================================================================
// Complex Shape Composition
// =============================================================================

export const ComplexComposition: Story = {
    args: {
        width: 500,
        height: 350,
        draw: (ctx) => {
            // Draw a simple atom with electron shells
            const centerX = 0;
            const centerY = 0;

            // Nucleus
            drawCircle(ctx, centerX, centerY, 25, '#ef4444', '#dc2626', 3);
            drawText(ctx, 'C', centerX, centerY + 5, '#ffffff', 'bold 18px sans-serif', 'center');

            // Electron shells (dashed circles)
            ctx.setLineDash([5, 5]);
            ctx.strokeStyle = '#94a3b8';
            ctx.lineWidth = 1;

            [60, 100].forEach((radius) => {
                ctx.beginPath();
                ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
                ctx.stroke();
            });

            ctx.setLineDash([]);

            // Electrons
            const electrons = [
                { angle: 0, radius: 60 },
                { angle: Math.PI, radius: 60 },
                { angle: Math.PI / 3, radius: 100 },
                { angle: (2 * Math.PI) / 3, radius: 100 },
                { angle: (4 * Math.PI) / 3, radius: 100 },
                { angle: (5 * Math.PI) / 3, radius: 100 },
            ];

            electrons.forEach(({ angle, radius }) => {
                const x = centerX + Math.cos(angle) * radius;
                const y = centerY + Math.sin(angle) * radius;
                drawCircle(ctx, x, y, 8, '#3b82f6', '#1d4ed8', 2);
            });

            // Labels
            drawText(ctx, 'Carbon Atom (Bohr Model)', 0, 140, '#475569', '14px sans-serif', 'center');
            drawText(ctx, '2 electrons in K shell, 4 in L shell', 0, 160, '#94a3b8', '12px sans-serif', 'center');
        },
    },
    parameters: {
        docs: {
            description: {
                story: 'Complex composition: Carbon atom with electron shells (Bohr model).',
            },
        },
    },
};

// =============================================================================
// All Primitives Grid
// =============================================================================

export const AllPrimitives: Story = {
    args: {
        width: 600,
        height: 500,
        draw: (ctx) => {
            const gridSize = 150;
            const positions = [
                { x: -gridSize, y: -gridSize, label: 'Rect' },
                { x: 0, y: -gridSize, label: 'Circle' },
                { x: gridSize, y: -gridSize, label: 'Diamond' },
                { x: -gridSize, y: 0, label: 'Hexagon' },
                { x: 0, y: 0, label: 'Arrow' },
                { x: gridSize, y: 0, label: 'Vector' },
                { x: -gridSize, y: gridSize, label: 'Spring' },
                { x: 0, y: gridSize, label: 'Bond' },
                { x: gridSize, y: gridSize, label: 'Bezier' },
            ];

            positions.forEach(({ x, y, label }) => {
                // Draw grid cell
                ctx.strokeStyle = '#e2e8f0';
                ctx.lineWidth = 1;
                ctx.strokeRect(x - 70, y - 50, 140, 100);

                // Draw primitive
                switch (label) {
                    case 'Rect':
                        drawRoundedRect(ctx, x - 30, y - 20, 60, 40, 6, '#3b82f6', '#1e40af', 2);
                        break;
                    case 'Circle':
                        drawCircle(ctx, x, y, 25, '#10b981', '#047857', 2);
                        break;
                    case 'Diamond':
                        drawDiamond(ctx, x, y, 30, '#f59e0b', '#d97706', 2);
                        break;
                    case 'Hexagon':
                        drawHexagon(ctx, x, y, 25, '#8b5cf6', '#7c3aed', 2);
                        break;
                    case 'Arrow':
                        drawArrow(ctx, x - 30, y, x + 30, y, '#ef4444', 2, 8);
                        break;
                    case 'Vector':
                        drawVector(ctx, x, y + 15, 25, -30, '#ec4899', 2, 'F');
                        break;
                    case 'Spring':
                        drawSpring(ctx, x - 35, y, x + 35, y, 6, 5, '#06b6d4', 2);
                        break;
                    case 'Bond':
                        drawChemicalBond(ctx, x - 30, y, x + 30, y, 'double', '#1e293b', 2);
                        break;
                    case 'Bezier':
                        drawBezierCurve(ctx, x - 30, y + 10, x - 10, y - 30, x + 10, y + 30, x + 30, y - 10, '#a855f7', 2);
                        break;
                }

                // Label
                drawText(ctx, label, x, y + 45, '#64748b', '11px sans-serif', 'center');
            });
        },
    },
    parameters: {
        docs: {
            description: {
                story: 'Grid showing all available drawing primitives.',
            },
        },
    },
};
