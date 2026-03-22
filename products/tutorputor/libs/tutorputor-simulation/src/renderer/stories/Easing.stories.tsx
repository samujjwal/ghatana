/**
 * Easing Functions Stories
 *
 * @doc.type stories
 * @doc.purpose Storybook stories for animation easing functions
 * @doc.layer product
 * @doc.pattern Story
 */

import type { Meta, StoryObj } from '@storybook/react';
import React, { useRef, useEffect, useState, useCallback } from 'react';
import {
    linear,
    easeInQuad,
    easeOutQuad,
    easeInOutQuad,
    easeInCubic,
    easeOutCubic,
    easeInOutCubic,
    easeInElastic,
    easeOutElastic,
    easeInOutElastic,
    easeInBounce,
    easeOutBounce,
    spring,
    overshoot,
    getEasingFunction,
    type EasingFunction,
} from '../easing';
import { drawLine, drawCircle, drawText } from '../primitives';

// Canvas component for easing curve visualization
interface EasingCurveProps {
    width?: number;
    height?: number;
    easingFn: (t: number) => number;
    name: string;
    animate?: boolean;
}

const EasingCurve: React.FC<EasingCurveProps> = ({
    width = 200,
    height = 200,
    easingFn,
    name,
    animate = false,
}) => {
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const [progress, setProgress] = useState(0);
    const animationRef = useRef<number | undefined>(undefined);

    const draw = useCallback(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;

        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        const padding = 30;
        const graphWidth = width - padding * 2;
        const graphHeight = height - padding * 2;

        // Clear
        ctx.fillStyle = '#f8fafc';
        ctx.fillRect(0, 0, width, height);

        // Draw axes
        ctx.strokeStyle = '#e2e8f0';
        ctx.lineWidth = 1;

        // Y axis
        ctx.beginPath();
        ctx.moveTo(padding, padding);
        ctx.lineTo(padding, height - padding);
        ctx.stroke();

        // X axis
        ctx.beginPath();
        ctx.moveTo(padding, height - padding);
        ctx.lineTo(width - padding, height - padding);
        ctx.stroke();

        // Draw curve
        ctx.beginPath();
        ctx.strokeStyle = '#3b82f6';
        ctx.lineWidth = 2;

        for (let i = 0; i <= 100; i++) {
            const t = i / 100;
            const x = padding + t * graphWidth;
            const y = height - padding - easingFn(t) * graphHeight;

            if (i === 0) {
                ctx.moveTo(x, y);
            } else {
                ctx.lineTo(x, y);
            }
        }
        ctx.stroke();

        // Draw animated dot if enabled
        if (animate && progress > 0) {
            const t = progress;
            const x = padding + t * graphWidth;
            const y = height - padding - easingFn(t) * graphHeight;
            drawCircle(ctx, x, y, 6, '#ef4444', '#dc2626', 2);

            // Show current value
            drawText(
                ctx,
                `t=${t.toFixed(2)}, y=${easingFn(t).toFixed(2)}`,
                width / 2,
                height - 10,
                '#64748b',
                '10px sans-serif',
                'center'
            );
        }

        // Draw name
        drawText(ctx, name, width / 2, 15, '#1e293b', 'bold 12px sans-serif', 'center');

        // Axis labels
        drawText(ctx, '0', padding - 10, height - padding + 15, '#94a3b8', '10px sans-serif', 'center');
        drawText(ctx, '1', width - padding, height - padding + 15, '#94a3b8', '10px sans-serif', 'center');
        drawText(ctx, '1', padding - 15, padding + 5, '#94a3b8', '10px sans-serif', 'center');
        drawText(ctx, 't', width - padding + 10, height - padding + 5, '#94a3b8', '10px sans-serif', 'left');
        drawText(ctx, 'y', padding - 5, padding - 10, '#94a3b8', '10px sans-serif', 'center');
    }, [easingFn, name, width, height, animate, progress]);

    useEffect(() => {
        draw();
    }, [draw]);

    useEffect(() => {
        if (!animate) return;

        let startTime: number | null = null;
        const duration = 2000;

        const tick = (timestamp: number) => {
            if (!startTime) startTime = timestamp;
            const elapsed = timestamp - startTime;
            const t = Math.min(elapsed / duration, 1);
            setProgress(t);

            if (t < 1) {
                animationRef.current = requestAnimationFrame(tick);
            } else {
                // Reset after a pause
                setTimeout(() => {
                    startTime = null;
                    setProgress(0);
                    animationRef.current = requestAnimationFrame(tick);
                }, 1000);
            }
        };

        animationRef.current = requestAnimationFrame(tick);

        return () => {
            if (animationRef.current) {
                cancelAnimationFrame(animationRef.current);
            }
        };
    }, [animate]);

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

// Grid of all easing functions
interface EasingGridProps {
    animate?: boolean;
}

const EasingGrid: React.FC<EasingGridProps> = ({ animate = false }) => {
    const easingFunctions: { name: string; fn: (t: number) => number }[] = [
        { name: 'linear', fn: linear },
        { name: 'easeInQuad', fn: easeInQuad },
        { name: 'easeOutQuad', fn: easeOutQuad },
        { name: 'easeInOutQuad', fn: easeInOutQuad },
        { name: 'easeInCubic', fn: easeInCubic },
        { name: 'easeOutCubic', fn: easeOutCubic },
        { name: 'easeInOutCubic', fn: easeInOutCubic },
        { name: 'easeOutElastic', fn: easeOutElastic },
        { name: 'easeOutBounce', fn: easeOutBounce },
        { name: 'spring', fn: spring },
        { name: 'overshoot', fn: overshoot },
    ];

    return (
        <div
            style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(4, 1fr)',
                gap: '16px',
                padding: '16px',
            }}
        >
            {easingFunctions.map(({ name, fn }) => (
                <EasingCurve
                    key={name}
                    name={name}
                    easingFn={fn}
                    width={180}
                    height={180}
                    animate={animate}
                />
            ))}
        </div>
    );
};

const meta: Meta<typeof EasingGrid> = {
    title: 'Simulation/Easing',
    component: EasingGrid,
    parameters: {
        layout: 'centered',
        docs: {
            description: {
                component: 'Animation easing functions for smooth transitions in simulations.',
            },
        },
    },
};

export default meta;
type Story = StoryObj<typeof EasingGrid>;

// =============================================================================
// All Easing Functions
// =============================================================================

export const AllEasingFunctions: Story = {
    args: {
        animate: false,
    },
    parameters: {
        docs: {
            description: {
                story: 'Grid showing all available easing functions.',
            },
        },
    },
};

// =============================================================================
// Animated
// =============================================================================

export const Animated: Story = {
    args: {
        animate: true,
    },
    parameters: {
        docs: {
            description: {
                story: 'Animated demonstration of easing functions.',
            },
        },
    },
};

// =============================================================================
// Individual Curves
// =============================================================================

export const Linear: Story = {
    render: () => <EasingCurve name="linear" easingFn={linear} width={300} height={300} animate />,
    parameters: {
        docs: {
            description: {
                story: 'Linear interpolation: constant speed throughout.',
            },
        },
    },
};

export const EaseInOut: Story = {
    render: () => <EasingCurve name="easeInOutCubic" easingFn={easeInOutCubic} width={300} height={300} animate />,
    parameters: {
        docs: {
            description: {
                story: 'Cubic ease-in-out: slow start, fast middle, slow end.',
            },
        },
    },
};

export const Elastic: Story = {
    render: () => <EasingCurve name="easeOutElastic" easingFn={easeOutElastic} width={300} height={300} animate />,
    parameters: {
        docs: {
            description: {
                story: 'Elastic ease-out: overshoots and bounces like a spring.',
            },
        },
    },
};

export const Bounce: Story = {
    render: () => <EasingCurve name="easeOutBounce" easingFn={easeOutBounce} width={300} height={300} animate />,
    parameters: {
        docs: {
            description: {
                story: 'Bounce ease-out: bounces at the end like a ball.',
            },
        },
    },
};

export const Spring: Story = {
    render: () => <EasingCurve name="spring" easingFn={spring} width={300} height={300} animate />,
    parameters: {
        docs: {
            description: {
                story: 'Spring: natural spring-like motion with overshoot.',
            },
        },
    },
};

export const Overshoot: Story = {
    render: () => <EasingCurve name="overshoot" easingFn={overshoot} width={300} height={300} animate />,
    parameters: {
        docs: {
            description: {
                story: 'Overshoot: goes past target then settles back.',
            },
        },
    },
};

// =============================================================================
// Comparison
// =============================================================================

interface ComparisonProps { }

const EasingComparison: React.FC<ComparisonProps> = () => {
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const [progress, setProgress] = useState(0);
    const animationRef = useRef<number | undefined>(undefined);

    const easings = [
        { name: 'linear', fn: linear, color: '#64748b' },
        { name: 'easeInCubic', fn: easeInCubic, color: '#ef4444' },
        { name: 'easeOutCubic', fn: easeOutCubic, color: '#10b981' },
        { name: 'easeInOutCubic', fn: easeInOutCubic, color: '#3b82f6' },
    ];

    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;

        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        const width = 500;
        const height = 300;
        const padding = 50;
        const graphWidth = width - padding * 2;
        const graphHeight = height - padding * 2;

        // Clear
        ctx.fillStyle = '#f8fafc';
        ctx.fillRect(0, 0, width, height);

        // Draw axes
        ctx.strokeStyle = '#e2e8f0';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(padding, padding);
        ctx.lineTo(padding, height - padding);
        ctx.lineTo(width - padding, height - padding);
        ctx.stroke();

        // Draw curves
        easings.forEach(({ fn, color }) => {
            ctx.beginPath();
            ctx.strokeStyle = color;
            ctx.lineWidth = 2;

            for (let i = 0; i <= 100; i++) {
                const t = i / 100;
                const x = padding + t * graphWidth;
                const y = height - padding - fn(t) * graphHeight;

                if (i === 0) {
                    ctx.moveTo(x, y);
                } else {
                    ctx.lineTo(x, y);
                }
            }
            ctx.stroke();
        });

        // Draw animated dots
        if (progress > 0) {
            easings.forEach(({ fn, color }) => {
                const t = progress;
                const x = padding + t * graphWidth;
                const y = height - padding - fn(t) * graphHeight;
                drawCircle(ctx, x, y, 6, color, color, 0);
            });
        }

        // Legend
        let legendY = 30;
        easings.forEach(({ name, color }) => {
            ctx.fillStyle = color;
            ctx.fillRect(width - 130, legendY - 8, 12, 12);
            drawText(ctx, name, width - 112, legendY, '#1e293b', '11px sans-serif', 'left');
            legendY += 20;
        });

        // Title
        drawText(ctx, 'Easing Function Comparison', width / 2, 20, '#1e293b', 'bold 14px sans-serif', 'center');
    }, [progress]);

    useEffect(() => {
        let startTime: number | null = null;
        const duration = 2000;

        const animate = (timestamp: number) => {
            if (!startTime) startTime = timestamp;
            const elapsed = timestamp - startTime;
            const t = Math.min(elapsed / duration, 1);
            setProgress(t);

            if (t < 1) {
                animationRef.current = requestAnimationFrame(animate);
            } else {
                setTimeout(() => {
                    startTime = null;
                    setProgress(0);
                    animationRef.current = requestAnimationFrame(animate);
                }, 1000);
            }
        };

        animationRef.current = requestAnimationFrame(animate);

        return () => {
            if (animationRef.current) {
                cancelAnimationFrame(animationRef.current);
            }
        };
    }, []);

    return (
        <canvas
            ref={canvasRef}
            width={500}
            height={300}
            style={{
                border: '1px solid #e2e8f0',
                borderRadius: '8px',
            }}
        />
    );
};

export const Comparison: Story = {
    render: () => <EasingComparison />,
    parameters: {
        docs: {
            description: {
                story: 'Side-by-side comparison of linear, ease-in, ease-out, and ease-in-out cubic functions.',
            },
        },
    },
};
