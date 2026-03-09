/**
 * Story Canvas - Wrapper for Storybook stories
 *
 * @doc.type component
 * @doc.purpose Provide a canvas wrapper for rendering entities in stories
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useRef, useEffect, useMemo } from 'react';
import type { SimEntity, SimEntityId } from '@ghatana/tutorputor-contracts/v1/simulation';
import { useRendererRegistry } from '../hooks';
import {
    defaultTheme,
    type RenderTheme,
    type RenderContext,
} from '../types';
import { allRenderers } from '../renderers';

interface StoryCanvasProps {
    /** Entities to render */
    entities: SimEntity[];
    /** Canvas width */
    width?: number;
    /** Canvas height */
    height?: number;
    /** Background color */
    backgroundColor?: string;
    /** Show grid */
    showGrid?: boolean;
    /** Zoom level */
    zoom?: number;
    /** Theme overrides */
    theme?: Partial<RenderTheme>;
    /** Simulation domain hint for story context */
    domain?: string;
}

/**
 * StoryCanvas component for Storybook stories.
 */
export const StoryCanvas: React.FC<StoryCanvasProps> = ({
    entities,
    width = 600,
    height = 400,
    backgroundColor = '#f8fafc',
    showGrid = true,
    zoom = 1,
    theme: themeOverrides,
}) => {
    const canvasRef = useRef<HTMLCanvasElement>(null);

    // Build entity map
    const entityMap = useMemo(() => {
        const map = new Map<SimEntityId, SimEntity>();
        for (const entity of entities) {
            map.set(entity.id, entity);
        }
        return map;
    }, [entities]);

    // Build renderer map
    const rendererMap = useMemo(() => {
        const map = new Map<string, (typeof allRenderers)[number]>();
        for (const renderer of allRenderers) {
            for (const type of renderer.entityTypes) {
                map.set(type, renderer);
            }
        }
        return map;
    }, []);

    // Render
    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;

        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        const theme: RenderTheme = { ...defaultTheme, ...themeOverrides };

        const panOffset = { x: 0, y: 0 };

        const worldToScreen = (worldX: number, worldY: number) => {
            const centerX = width / 2;
            const centerY = height / 2;
            return {
                x: worldX * zoom + centerX + panOffset.x,
                y: worldY * zoom + centerY + panOffset.y,
            };
        };

        const screenToWorld = (screenX: number, screenY: number) => {
            const centerX = width / 2;
            const centerY = height / 2;
            return {
                x: (screenX - centerX - panOffset.x) / zoom,
                y: (screenY - centerY - panOffset.y) / zoom,
            };
        };

        const context: RenderContext = {
            ctx,
            zoom,
            panOffset,
            width,
            height,
            timestamp: performance.now(),
            deltaTime: 16,
            theme,
            entities: entityMap,
            worldToScreen,
            screenToWorld,
        };

        // Clear canvas
        ctx.fillStyle = backgroundColor;
        ctx.fillRect(0, 0, width, height);

        // Draw grid
        if (showGrid) {
            const gridSize = 50 * zoom;
            ctx.strokeStyle = theme.border;
            ctx.lineWidth = 1;
            ctx.globalAlpha = 0.5;
            ctx.beginPath();

            for (let x = gridSize; x < width; x += gridSize) {
                ctx.moveTo(x, 0);
                ctx.lineTo(x, height);
            }

            for (let y = gridSize; y < height; y += gridSize) {
                ctx.moveTo(0, y);
                ctx.lineTo(width, y);
            }

            ctx.stroke();
            ctx.globalAlpha = 1;
        }

        // Draw entities
        for (const entity of entities) {
            const renderer = rendererMap.get(entity.type);
            if (renderer) {
                renderer.render(entity as never, context, false, false);
            }
        }
    }, [entities, width, height, backgroundColor, showGrid, zoom, themeOverrides, entityMap, rendererMap]);

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

export default StoryCanvas;
