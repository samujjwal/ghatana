/**
 * Minimap Panel Component
 * Feature 2.9: Minimap & Viewport Controls
 *
 * Provides a minimap overview of the canvas with:
 * - Node position visualization
 * - Viewport indicator overlay
 * - Click-to-pan interaction
 * - Drag viewport indicator
 * - Real-time synchronization with main canvas
 *
 * @module components/MinimapPanel
 */

import { ZoomIn as ZoomInIcon, ZoomOut as ZoomOutIcon, Maximize2 as FitScreenIcon } from 'lucide-react';
import {
  Box,
  IconButton,
  Tooltip,
  Surface as Paper,
} from '@ghatana/ui';
import { useRef, useEffect, useCallback, useState, type MouseEvent as ReactMouseEvent } from 'react';
import type { JSX } from 'react';

type DOMMouseEvent = globalThis.MouseEvent;

import {
    calculateCanvasBounds,
    worldToMinimapCoordinates,
    calculateMinimapViewport,
    handleMinimapClick,
    isPointInMinimapViewport,
    createMinimapConfig,
    zoomToSelection,
    applyKeyboardZoom,
    createZoomConfig,
} from '../viewport/minimapState';

import type { Point, Viewport } from '../viewport/infiniteSpace';
import type { MinimapNode, MinimapConfig } from '../viewport/minimapState';

/**
 *
 */
export interface MinimapPanelProps {
    /** Current viewport state */
    viewport: Viewport;
    /** Nodes to display on minimap */
    nodes: MinimapNode[];
    /** Callback when viewport should change */
    onViewportChange: (viewport: Viewport) => void;
    /** Optional minimap configuration */
    config?: Partial<MinimapConfig>;
    /** Optional className for styling */
    className?: string;
    /** Whether minimap is visible */
    visible?: boolean;
}

/**
 * Minimap Panel - provides overview and navigation controls
 */
export function MinimapPanel({
    viewport,
    nodes,
    onViewportChange,
    config: configOverride,
    className,
    visible = true,
}: MinimapPanelProps): JSX.Element | null {
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const [isDragging, setIsDragging] = useState(false);
    const [config] = useState(() => createMinimapConfig(configOverride));
    const [zoomConfig] = useState(() => createZoomConfig());

    // Calculate derived state
    const canvasBounds = calculateCanvasBounds(nodes, config.padding);
    const minimapViewport = calculateMinimapViewport(viewport, canvasBounds, config);

    /**
     * Render minimap canvas
     */
    const renderMinimap = useCallback(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;

        const ctx = canvas.getContext('2d');
        if (!ctx) return;

        // Clear canvas
        ctx.clearRect(0, 0, config.width, config.height);

        // Draw background
        ctx.fillStyle = config.backgroundColor;
        ctx.fillRect(0, 0, config.width, config.height);

        // Draw nodes
        ctx.fillStyle = config.nodeColor;
        nodes.forEach((node) => {
            const minimapPos = worldToMinimapCoordinates(
                { x: node.x, y: node.y },
                canvasBounds,
                config
            );
            const minimapSize = {
                width: (node.width / canvasBounds.width) * (config.width - 2 * config.padding),
                height: (node.height / canvasBounds.height) * (config.height - 2 * config.padding),
            };

            ctx.fillRect(
                minimapPos.x,
                minimapPos.y,
                Math.max(1, minimapSize.width),
                Math.max(1, minimapSize.height)
            );
        });

        // Draw viewport indicator
        ctx.strokeStyle = config.viewportColor;
        ctx.lineWidth = 2;
        ctx.strokeRect(
            minimapViewport.x,
            minimapViewport.y,
            minimapViewport.width,
            minimapViewport.height
        );
    }, [nodes, canvasBounds, config, minimapViewport]);

    /**
     * Handle minimap click for navigation
     */
    const handleClick = useCallback(
        (event: ReactMouseEvent<HTMLCanvasElement>) => {
            const canvas = canvasRef.current;
            if (!canvas) return;

            const rect = canvas.getBoundingClientRect();
            const clickPoint: Point = {
                x: event.clientX - rect.left,
                y: event.clientY - rect.top,
            };

            const newCenter = handleMinimapClick(
                clickPoint,
                canvasBounds,
                config,
                viewport
            );

            onViewportChange({
                ...viewport,
                center: newCenter,
            });
        },
        [canvasBounds, config, viewport, onViewportChange]
    );

    /**
     * Handle mouse down for viewport drag
     */
    const handleMouseDown = useCallback(
        (event: ReactMouseEvent<HTMLCanvasElement>) => {
            const canvas = canvasRef.current;
            if (!canvas) return;

            const rect = canvas.getBoundingClientRect();
            const point: Point = {
                x: event.clientX - rect.left,
                y: event.clientY - rect.top,
            };

            if (isPointInMinimapViewport(point, minimapViewport)) {
                setIsDragging(true);
                event.preventDefault();
            }
        },
        [minimapViewport]
    );

    /**
     * Handle mouse move for viewport drag
     */
    const handleMouseMove = useCallback(
        (event: DOMMouseEvent) => {
            if (!isDragging) return;

            const canvas = canvasRef.current;
            if (!canvas) return;

            const rect = canvas.getBoundingClientRect();
            const point: Point = {
                x: event.clientX - rect.left,
                y: event.clientY - rect.top,
            };

            const newCenter = handleMinimapClick(
                point,
                canvasBounds,
                config,
                viewport
            );

            onViewportChange({
                ...viewport,
                center: newCenter,
            });
        },
        [isDragging, canvasBounds, config, viewport, onViewportChange]
    );

    /**
     * Handle mouse up for viewport drag
     */
    const handleMouseUp = useCallback(() => {
        setIsDragging(false);
    }, []);

    /**
     * Handle zoom in
     */
    const handleZoomIn = useCallback(() => {
        const newZoom = applyKeyboardZoom(viewport.zoom, 'in', zoomConfig);
        onViewportChange({
            ...viewport,
            zoom: newZoom,
        });
    }, [viewport, zoomConfig, onViewportChange]);

    /**
     * Handle zoom out
     */
    const handleZoomOut = useCallback(() => {
        const newZoom = applyKeyboardZoom(viewport.zoom, 'out', zoomConfig);
        onViewportChange({
            ...viewport,
            zoom: newZoom,
        });
    }, [viewport, zoomConfig, onViewportChange]);

    /**
     * Handle fit to screen
     */
    const handleFitScreen = useCallback(() => {
        const newViewport = zoomToSelection(
            nodes,
            { width: viewport.width, height: viewport.height },
            config.padding * 5, // More padding for fit-to-screen
            zoomConfig
        );
        onViewportChange(newViewport);
    }, [nodes, viewport, config, zoomConfig, onViewportChange]);

    // Render minimap when data changes
    useEffect(() => {
        renderMinimap();
    }, [renderMinimap]);

    // Set up drag event listeners
    useEffect(() => {
        if (isDragging) {
            window.addEventListener('mousemove', handleMouseMove);
            window.addEventListener('mouseup', handleMouseUp);

            return () => {
                window.removeEventListener('mousemove', handleMouseMove);
                window.removeEventListener('mouseup', handleMouseUp);
            };
        }
        return undefined;
    }, [isDragging, handleMouseMove, handleMouseUp]);

    if (!visible) {
        return null;
    }

    return (
        <Paper
            elevation={3}
            className={`absolute flex flex-col gap-2 bottom-[16px] right-[16px] p-2 bg-white dark:bg-gray-900 select-none ${className ?? ''}`}
        >
            {/* Zoom controls */}
            <Box className="flex justify-center gap-1">
                <Tooltip title="Zoom In (+)">
                    <IconButton
                        size="small"
                        onClick={handleZoomIn}
                        disabled={viewport.zoom >= zoomConfig.max}
                        aria-label="Zoom in"
                    >
                        <ZoomInIcon size={16} />
                    </IconButton>
                </Tooltip>
                <Tooltip title="Zoom Out (-)">
                    <IconButton
                        size="small"
                        onClick={handleZoomOut}
                        disabled={viewport.zoom <= zoomConfig.min}
                        aria-label="Zoom out"
                    >
                        <ZoomOutIcon size={16} />
                    </IconButton>
                </Tooltip>
                <Tooltip title="Fit to Screen">
                    <IconButton
                        size="small"
                        onClick={handleFitScreen}
                        disabled={nodes.length === 0}
                        aria-label="Fit to screen"
                    >
                        <FitScreenIcon size={16} />
                    </IconButton>
                </Tooltip>
            </Box>

            {/* Minimap canvas */}
            <canvas
                ref={canvasRef}
                width={config.width}
                height={config.height}
                onClick={handleClick}
                onMouseDown={handleMouseDown}
                style={{
                    cursor: isDragging ? 'grabbing' : 'pointer',
                    border: '1px solid',
                    borderColor: 'divider',
                    borderRadius: 4,
                }}
                aria-label="Canvas minimap"
            />

            {/* Zoom level indicator */}
            <Box
                className="text-center text-xs text-gray-500 dark:text-gray-400"
            >
                {Math.round(viewport.zoom * 100)}%
            </Box>
        </Paper>
    );
}

export default MinimapPanel;
