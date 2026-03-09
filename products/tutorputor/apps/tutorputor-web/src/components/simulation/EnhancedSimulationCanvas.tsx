/**
 * Enhanced SimulationCanvas - Using sim-renderer library.
 *
 * @doc.type component
 * @doc.purpose Renders simulation keyframes using domain-specific renderers
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useRef, useEffect, useCallback, useState, useMemo } from 'react';
import type { SimKeyframe, SimEntityBase, SimEntity, SimEntityId } from '@ghatana/tutorputor-contracts/v1/simulation';
import {
    useRendererRegistry,
    useRenderContext,
    useCanvasRendering,
    useHitTest,
    useAnimation,
    defaultTheme,
    type RenderTheme,
} from '@ghatana/tutorputor-sim-renderer';

/**
 * Enhanced canvas props.
 */
interface EnhancedSimulationCanvasProps {
    /** Current keyframe to render */
    keyframe: SimKeyframe;
    /** Canvas width */
    width?: number;
    /** Canvas height */
    height?: number;
    /** Background color */
    backgroundColor?: string;
    /** Grid visibility */
    showGrid?: boolean;
    /** Click handler for entities */
    onEntityClick?: (entity: SimEntityBase) => void;
    /** Hover handler for entities */
    onEntityHover?: (entity: SimEntityBase | null) => void;
    /** Selection change handler */
    onSelectionChange?: (entityIds: Set<SimEntityId>) => void;
    /** Zoom level (1.0 = 100%) */
    zoom?: number;
    /** Pan offset */
    panOffset?: { x: number; y: number };
    /** Enable pan/zoom controls */
    enableControls?: boolean;
    /** Enable multi-select */
    enableMultiSelect?: boolean;
    /** Custom theme overrides */
    theme?: Partial<RenderTheme>;
    /** Accessibility label */
    ariaLabel?: string;
    /** Class name for styling */
    className?: string;
}

/**
 * Enhanced SimulationCanvas component using sim-renderer library.
 */
export const EnhancedSimulationCanvas: React.FC<EnhancedSimulationCanvasProps> = ({
    keyframe,
    width = 800,
    height = 600,
    backgroundColor = '#f8fafc',
    showGrid = true,
    onEntityClick,
    onEntityHover,
    onSelectionChange,
    zoom = 1,
    panOffset = { x: 0, y: 0 },
    enableControls = true,
    enableMultiSelect = false,
    theme: themeOverrides,
    ariaLabel = 'Simulation Canvas',
    className,
}) => {
    const canvasRef = useRef<HTMLCanvasElement>(null);

    // Local state for interactions
    const [localZoom, setLocalZoom] = useState(zoom);
    const [localPan, setLocalPan] = useState(panOffset);
    const [hoveredEntityId, setHoveredEntityId] = useState<SimEntityId | null>(null);
    const [selectedEntityIds, setSelectedEntityIds] = useState<Set<SimEntityId>>(new Set());
    const [isDragging, setIsDragging] = useState(false);
    const dragStart = useRef({ x: 0, y: 0 });

    // Initialize renderer registry
    const registry = useRendererRegistry();

    // Extract entities from keyframe
    const entities = useMemo((): SimEntity[] => {
        return keyframe.entities as SimEntity[];
    }, [keyframe.entities]);

    // Create render context
    const context = useRenderContext({
        canvasRef,
        entities,
        width,
        height,
        zoom: localZoom,
        panOffset: localPan,
        theme: themeOverrides,
    });

    // Hit testing
    const { hitTest } = useHitTest({
        registry,
        context,
        entities,
    });

    // Animation system
    const { animate } = useAnimation();

    // Render entities to canvas
    useCanvasRendering({
        canvasRef,
        registry,
        context,
        entities,
        hoveredEntityId,
        selectedEntityIds,
        showGrid,
        backgroundColor,
    });

    // Draw annotations (not part of sim-renderer, specific to SimulationCanvas)
    useEffect(() => {
        if (!context || !keyframe.annotations) return;

        const { ctx, worldToScreen, zoom: currentZoom, theme } = context;

        for (const annotation of keyframe.annotations) {
            const screen = worldToScreen(
                annotation.position?.x ?? 0,
                annotation.position?.y ?? 0
            );

            // Draw callout background
            ctx.fillStyle = 'rgba(255, 255, 255, 0.95)';
            ctx.strokeStyle = theme.border;
            ctx.lineWidth = 1;

            const padding = 10;
            ctx.font = `${14 * currentZoom}px ${theme.fontFamily}`;
            const textWidth = ctx.measureText(annotation.text).width;
            const boxWidth = textWidth + padding * 2;
            const boxHeight = 28 * currentZoom;

            ctx.beginPath();
            ctx.roundRect(screen.x - boxWidth / 2, screen.y - boxHeight / 2, boxWidth, boxHeight, 4);
            ctx.fill();
            ctx.stroke();

            // Draw text
            ctx.fillStyle = theme.foreground;
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';
            ctx.fillText(annotation.text, screen.x, screen.y);
        }
    }, [context, keyframe.annotations]);

    /**
     * Handle mouse move.
     */
    const handleMouseMove = useCallback(
        (e: React.MouseEvent<HTMLCanvasElement>) => {
            const rect = canvasRef.current?.getBoundingClientRect();
            if (!rect) return;

            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;

            if (isDragging && enableControls) {
                setLocalPan({
                    x: localPan.x + (x - dragStart.current.x),
                    y: localPan.y + (y - dragStart.current.y),
                });
                dragStart.current = { x, y };
            } else {
                const entity = hitTest(x, y);
                const newHoveredId = entity?.id ?? null;

                if (newHoveredId !== hoveredEntityId) {
                    setHoveredEntityId(newHoveredId);
                    onEntityHover?.(entity);
                }
            }
        },
        [isDragging, enableControls, localPan, hitTest, hoveredEntityId, onEntityHover]
    );

    /**
     * Handle mouse down.
     */
    const handleMouseDown = useCallback(
        (e: React.MouseEvent<HTMLCanvasElement>) => {
            const rect = canvasRef.current?.getBoundingClientRect();
            if (!rect) return;

            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;

            if (e.button === 0) {
                const entity = hitTest(x, y);

                if (entity) {
                    onEntityClick?.(entity);

                    // Handle selection
                    if (enableMultiSelect && (e.ctrlKey || e.metaKey)) {
                        // Toggle selection
                        setSelectedEntityIds((prev) => {
                            const newSet = new Set(prev);
                            if (newSet.has(entity.id)) {
                                newSet.delete(entity.id);
                            } else {
                                newSet.add(entity.id);
                            }
                            onSelectionChange?.(newSet);
                            return newSet;
                        });
                    } else {
                        // Single selection
                        const newSet = new Set([entity.id]);
                        setSelectedEntityIds(newSet);
                        onSelectionChange?.(newSet);
                    }
                } else if (enableControls) {
                    // Start panning
                    setIsDragging(true);
                    dragStart.current = { x, y };

                    // Clear selection when clicking empty space
                    if (!e.ctrlKey && !e.metaKey) {
                        setSelectedEntityIds(new Set());
                        onSelectionChange?.(new Set());
                    }
                }
            }
        },
        [hitTest, onEntityClick, enableControls, enableMultiSelect, onSelectionChange]
    );

    /**
     * Handle mouse up.
     */
    const handleMouseUp = useCallback(() => {
        setIsDragging(false);
    }, []);

    /**
     * Handle wheel zoom.
     */
    const handleWheel = useCallback(
        (e: React.WheelEvent<HTMLCanvasElement>) => {
            if (!enableControls) return;

            e.preventDefault();
            const delta = e.deltaY > 0 ? 0.9 : 1.1;
            setLocalZoom((prev) => Math.max(0.1, Math.min(5, prev * delta)));
        },
        [enableControls]
    );

    /**
     * Handle keyboard navigation.
     */
    const handleKeyDown = useCallback(
        (e: React.KeyboardEvent<HTMLCanvasElement>) => {
            if (!enableControls) return;

            const panStep = 20;

            switch (e.key) {
                case 'ArrowUp':
                    e.preventDefault();
                    setLocalPan((prev) => ({ ...prev, y: prev.y + panStep }));
                    break;
                case 'ArrowDown':
                    e.preventDefault();
                    setLocalPan((prev) => ({ ...prev, y: prev.y - panStep }));
                    break;
                case 'ArrowLeft':
                    e.preventDefault();
                    setLocalPan((prev) => ({ ...prev, x: prev.x + panStep }));
                    break;
                case 'ArrowRight':
                    e.preventDefault();
                    setLocalPan((prev) => ({ ...prev, x: prev.x - panStep }));
                    break;
                case '+':
                case '=':
                    e.preventDefault();
                    setLocalZoom((prev) => Math.min(5, prev * 1.1));
                    break;
                case '-':
                    e.preventDefault();
                    setLocalZoom((prev) => Math.max(0.1, prev * 0.9));
                    break;
                case '0':
                    e.preventDefault();
                    setLocalZoom(1);
                    setLocalPan({ x: 0, y: 0 });
                    break;
                case 'Escape':
                    setSelectedEntityIds(new Set());
                    onSelectionChange?.(new Set());
                    break;
            }
        },
        [enableControls, onSelectionChange]
    );

    // Update local state when props change
    useEffect(() => {
        setLocalZoom(zoom);
    }, [zoom]);

    useEffect(() => {
        setLocalPan(panOffset);
    }, [panOffset]);

    // Determine cursor
    const cursor = useMemo(() => {
        if (isDragging) return 'grabbing';
        if (hoveredEntityId) return 'pointer';
        if (enableControls) return 'grab';
        return 'default';
    }, [isDragging, hoveredEntityId, enableControls]);

    return (
        <canvas
            ref={canvasRef}
            width={width}
            height={height}
            onMouseMove={handleMouseMove}
            onMouseDown={handleMouseDown}
            onMouseUp={handleMouseUp}
            onMouseLeave={handleMouseUp}
            onWheel={handleWheel}
            onKeyDown={handleKeyDown}
            className={className}
            style={{
                cursor,
                borderRadius: '8px',
                boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
            }}
            role="img"
            aria-label={ariaLabel}
            tabIndex={0}
        />
    );
};

export default EnhancedSimulationCanvas;
