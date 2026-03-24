/**
 * Infinite Viewport
 *
 * Manages the infinite canvas viewport with pan, zoom, and navigation.
 * Implements virtualization for performance with large canvases.
 *
 * @doc.type component
 * @doc.purpose Infinite canvas viewport management
 * @doc.layer core
 * @doc.pattern Container Component
 */

import React, {
    useRef,
    useEffect,
    useCallback,
    useState,
    useMemo,
    type ReactNode,
    type RefObject,
    type WheelEvent,
    type MouseEvent,
    type TouchEvent,
} from 'react';

// ============================================================================
// Viewport Types
// ============================================================================

/**
 * Viewport transform state
 */
export interface ViewportTransform {
    /** X offset (pan) */
    x: number;
    /** Y offset (pan) */
    y: number;
    /** Zoom level (1 = 100%) */
    zoom: number;
}

/**
 * Viewport bounds
 */
export interface ViewportBounds {
    /** Left edge in canvas coordinates */
    left: number;
    /** Top edge in canvas coordinates */
    top: number;
    /** Right edge in canvas coordinates */
    right: number;
    /** Bottom edge in canvas coordinates */
    bottom: number;
    /** Width in canvas coordinates */
    width: number;
    /** Height in canvas coordinates */
    height: number;
}

/**
 * Point in screen coordinates
 */
export interface ScreenPoint {
    x: number;
    y: number;
}

/**
 * Point in canvas coordinates
 */
export interface CanvasPoint {
    x: number;
    y: number;
}

// ============================================================================
// Viewport Configuration
// ============================================================================

/**
 * Viewport configuration
 */
export interface ViewportConfig {
    /** Minimum zoom level */
    minZoom: number;
    /** Maximum zoom level */
    maxZoom: number;
    /** Zoom step for wheel events */
    zoomStep: number;
    /** Enable wheel zoom */
    wheelZoom: boolean;
    /** Enable pinch zoom */
    pinchZoom: boolean;
    /** Enable pan on drag (when using pan tool) */
    panOnDrag: boolean;
    /** Enable scroll-to-pan */
    scrollToPan: boolean;
    /** Enable inertia for pan/zoom */
    inertia: boolean;
    /** Inertia friction */
    friction: number;
    /** Show grid */
    showGrid: boolean;
    /** Grid size */
    gridSize: number;
    /** Grid snap enabled */
    snapToGrid: boolean;
    /** Show minimap */
    showMinimap: boolean;
    /** Show rulers */
    showRulers: boolean;
}

/**
 * Default configuration
 */
const DEFAULT_CONFIG: ViewportConfig = {
    minZoom: 0.1,
    maxZoom: 10,
    zoomStep: 0.1,
    wheelZoom: true,
    pinchZoom: true,
    panOnDrag: true,
    scrollToPan: true,
    inertia: true,
    friction: 0.92,
    showGrid: true,
    gridSize: 20,
    snapToGrid: true,
    showMinimap: true,
    showRulers: true,
};

// ============================================================================
// Viewport Props
// ============================================================================

export interface InfiniteViewportProps {
    /** Initial viewport transform */
    initialTransform?: Partial<ViewportTransform>;
    /** Controlled transform (makes component controlled) */
    transform?: ViewportTransform;
    /** Transform change callback */
    onTransformChange?: (transform: ViewportTransform) => void;
    /** Configuration overrides */
    config?: Partial<ViewportConfig>;
    /** Viewport container style */
    style?: React.CSSProperties;
    /** Viewport container className */
    className?: string;
    /** Children to render in the viewport */
    children?: ReactNode;
    /** Background color */
    backgroundColor?: string;
    /** Ref to container element */
    containerRef?: RefObject<HTMLDivElement>;
    /** Callback when viewport bounds change */
    onBoundsChange?: (bounds: ViewportBounds) => void;
}

// ============================================================================
// Viewport Hook
// ============================================================================

/**
 * Hook return type
 */
export interface UseViewportReturn {
    transform: ViewportTransform;
    bounds: ViewportBounds;
    containerRef: RefObject<HTMLDivElement>;
    setTransform: (transform: Partial<ViewportTransform>) => void;
    panTo: (point: CanvasPoint, animate?: boolean) => void;
    zoomTo: (zoom: number, center?: ScreenPoint, animate?: boolean) => void;
    fitContent: (padding?: number, animate?: boolean) => void;
    resetView: (animate?: boolean) => void;
    screenToCanvas: (point: ScreenPoint) => CanvasPoint;
    canvasToScreen: (point: CanvasPoint) => ScreenPoint;
}

/**
 * Hook for viewport management
 */
export function useViewport(
    config: ViewportConfig = DEFAULT_CONFIG,
    initialTransform: Partial<ViewportTransform> = {}
): UseViewportReturn {
    const containerRef = useRef<HTMLDivElement>(null);

    const [transform, setTransformState] = useState<ViewportTransform>({
        x: initialTransform.x ?? 0,
        y: initialTransform.y ?? 0,
        zoom: initialTransform.zoom ?? 1,
    });

    const [bounds, setBounds] = useState<ViewportBounds>({
        left: 0,
        top: 0,
        right: 0,
        bottom: 0,
        width: 0,
        height: 0,
    });

    // Calculate bounds when transform or container changes
    useEffect(() => {
        const container = containerRef.current;
        if (!container) return;

        const rect = container.getBoundingClientRect();
        const width = rect.width / transform.zoom;
        const height = rect.height / transform.zoom;

        setBounds({
            left: -transform.x / transform.zoom,
            top: -transform.y / transform.zoom,
            right: (-transform.x + rect.width) / transform.zoom,
            bottom: (-transform.y + rect.height) / transform.zoom,
            width,
            height,
        });
    }, [transform]);

    // Set transform with clamping
    const setTransform = useCallback(
        (partial: Partial<ViewportTransform>) => {
            setTransformState((prev) => {
                let newZoom = partial.zoom ?? prev.zoom;
                newZoom = Math.max(config.minZoom, Math.min(config.maxZoom, newZoom));

                return {
                    x: partial.x ?? prev.x,
                    y: partial.y ?? prev.y,
                    zoom: newZoom,
                };
            });
        },
        [config.minZoom, config.maxZoom]
    );

    // Pan to a canvas point
    const panTo = useCallback(
        (point: CanvasPoint, animate = true) => {
            const container = containerRef.current;
            if (!container) return;

            const rect = container.getBoundingClientRect();
            const newX = -point.x * transform.zoom + rect.width / 2;
            const newY = -point.y * transform.zoom + rect.height / 2;

            // TODO: Implement animation
            setTransform({ x: newX, y: newY });
        },
        [transform.zoom, setTransform]
    );

    // Zoom to a specific level
    const zoomTo = useCallback(
        (zoom: number, center?: ScreenPoint, animate = true) => {
            const container = containerRef.current;
            if (!container) return;

            const rect = container.getBoundingClientRect();
            const pivotX = center?.x ?? rect.width / 2;
            const pivotY = center?.y ?? rect.height / 2;

            // Calculate new position to keep pivot point in place
            const canvasPivot = {
                x: (pivotX - transform.x) / transform.zoom,
                y: (pivotY - transform.y) / transform.zoom,
            };

            const newZoom = Math.max(config.minZoom, Math.min(config.maxZoom, zoom));

            const newX = pivotX - canvasPivot.x * newZoom;
            const newY = pivotY - canvasPivot.y * newZoom;

            // TODO: Implement animation
            setTransform({ x: newX, y: newY, zoom: newZoom });
        },
        [transform, config.minZoom, config.maxZoom, setTransform]
    );

    // Fit content in view
    const fitContent = useCallback(
        (padding = 50, animate = true) => {
            // This would need access to content bounds
            // For now, just reset to center
            resetView(animate);
        },
        []
    );

    // Reset view to default
    const resetView = useCallback(
        (animate = true) => {
            setTransform({ x: 0, y: 0, zoom: 1 });
        },
        [setTransform]
    );

    // Convert screen coordinates to canvas coordinates
    const screenToCanvas = useCallback(
        (point: ScreenPoint): CanvasPoint => {
            const container = containerRef.current;
            if (!container) return { x: point.x, y: point.y };

            const rect = container.getBoundingClientRect();
            return {
                x: (point.x - rect.left - transform.x) / transform.zoom,
                y: (point.y - rect.top - transform.y) / transform.zoom,
            };
        },
        [transform]
    );

    // Convert canvas coordinates to screen coordinates
    const canvasToScreen = useCallback(
        (point: CanvasPoint): ScreenPoint => {
            const container = containerRef.current;
            if (!container) return { x: point.x, y: point.y };

            const rect = container.getBoundingClientRect();
            return {
                x: point.x * transform.zoom + transform.x + rect.left,
                y: point.y * transform.zoom + transform.y + rect.top,
            };
        },
        [transform]
    );

    return {
        transform,
        bounds,
        containerRef,
        setTransform,
        panTo,
        zoomTo,
        fitContent,
        resetView,
        screenToCanvas,
        canvasToScreen,
    };
}

// ============================================================================
// Infinite Viewport Component
// ============================================================================

/**
 * Infinite Viewport Component
 *
 * Provides an infinite pannable/zoomable canvas container.
 */
export const InfiniteViewport: React.FC<InfiniteViewportProps> = ({
    initialTransform,
    transform: controlledTransform,
    onTransformChange,
    config: configOverrides,
    style,
    className,
    children,
    backgroundColor = '#f8fafc',
    containerRef: externalRef,
    onBoundsChange,
}) => {
    const config = useMemo(
        () => ({ ...DEFAULT_CONFIG, ...configOverrides }),
        [configOverrides]
    );

    const internalRef = useRef<HTMLDivElement>(null);
    const containerRef = externalRef || internalRef;

    const [internalTransform, setInternalTransform] = useState<ViewportTransform>({
        x: initialTransform?.x ?? 0,
        y: initialTransform?.y ?? 0,
        zoom: initialTransform?.zoom ?? 1,
    });

    // Use controlled or internal transform
    const transform = controlledTransform ?? internalTransform;

    // Track interaction state
    const [isPanning, setIsPanning] = useState(false);
    const [startPan, setStartPan] = useState<ScreenPoint | null>(null);
    const [startTransform, setStartTransform] = useState<ViewportTransform | null>(null);

    // Handle transform change
    const setTransform = useCallback(
        (newTransform: Partial<ViewportTransform>) => {
            const updated = { ...transform, ...newTransform };
            updated.zoom = Math.max(config.minZoom, Math.min(config.maxZoom, updated.zoom));

            if (controlledTransform) {
                onTransformChange?.(updated);
            } else {
                setInternalTransform(updated);
                onTransformChange?.(updated);
            }
        },
        [transform, controlledTransform, onTransformChange, config.minZoom, config.maxZoom]
    );

    // Calculate and report bounds
    useEffect(() => {
        const container = containerRef.current;
        if (!container || !onBoundsChange) return;

        const rect = container.getBoundingClientRect();
        onBoundsChange({
            left: -transform.x / transform.zoom,
            top: -transform.y / transform.zoom,
            right: (-transform.x + rect.width) / transform.zoom,
            bottom: (-transform.y + rect.height) / transform.zoom,
            width: rect.width / transform.zoom,
            height: rect.height / transform.zoom,
        });
    }, [transform, onBoundsChange, containerRef]);

    // Handle wheel zoom
    const handleWheel = useCallback(
        (e: WheelEvent<HTMLDivElement>) => {
            if (!config.wheelZoom) return;

            e.preventDefault();

            const container = containerRef.current;
            if (!container) return;

            const rect = container.getBoundingClientRect();
            const mouseX = e.clientX - rect.left;
            const mouseY = e.clientY - rect.top;

            // Calculate zoom
            const delta = -e.deltaY * config.zoomStep * 0.01;
            const newZoom = Math.max(
                config.minZoom,
                Math.min(config.maxZoom, transform.zoom * (1 + delta))
            );

            // Zoom towards mouse position
            const canvasX = (mouseX - transform.x) / transform.zoom;
            const canvasY = (mouseY - transform.y) / transform.zoom;

            const newX = mouseX - canvasX * newZoom;
            const newY = mouseY - canvasY * newZoom;

            setTransform({ x: newX, y: newY, zoom: newZoom });
        },
        [config.wheelZoom, config.zoomStep, config.minZoom, config.maxZoom, transform, setTransform, containerRef]
    );

    // Handle pan start
    const handlePanStart = useCallback(
        (e: MouseEvent<HTMLDivElement>) => {
            if (!config.panOnDrag) return;
            if (e.button !== 1 && !e.altKey) return; // Middle button or Alt+click

            e.preventDefault();
            setIsPanning(true);
            setStartPan({ x: e.clientX, y: e.clientY });
            setStartTransform({ ...transform });
        },
        [config.panOnDrag, transform]
    );

    // Handle pan move
    const handlePanMove = useCallback(
        (e: MouseEvent<HTMLDivElement>) => {
            if (!isPanning || !startPan || !startTransform) return;

            const deltaX = e.clientX - startPan.x;
            const deltaY = e.clientY - startPan.y;

            setTransform({
                x: startTransform.x + deltaX,
                y: startTransform.y + deltaY,
            });
        },
        [isPanning, startPan, startTransform, setTransform]
    );

    // Handle pan end
    const handlePanEnd = useCallback(() => {
        setIsPanning(false);
        setStartPan(null);
        setStartTransform(null);
    }, []);

    // Grid pattern
    const gridPattern = useMemo(() => {
        if (!config.showGrid) return null;

        const gridSize = config.gridSize * transform.zoom;
        const offsetX = transform.x % gridSize;
        const offsetY = transform.y % gridSize;

        return (
            <pattern
                id="viewport-grid"
                width={gridSize}
                height={gridSize}
                patternUnits="userSpaceOnUse"
                x={offsetX}
                y={offsetY}
            >
                <path
                    d={`M ${gridSize} 0 L 0 0 0 ${gridSize}`}
                    fill="none"
                    stroke="rgba(0,0,0,0.05)"
                    strokeWidth="1"
                />
            </pattern>
        );
    }, [config.showGrid, config.gridSize, transform]);

    return (
        <div
            ref={containerRef}
            className={className}
            style={{
                position: 'relative',
                width: '100%',
                height: '100%',
                overflow: 'hidden',
                backgroundColor,
                cursor: isPanning ? 'grabbing' : 'default',
                ...style,
            }}
            onWheel={handleWheel}
            onMouseDown={handlePanStart}
            onMouseMove={handlePanMove}
            onMouseUp={handlePanEnd}
            onMouseLeave={handlePanEnd}
        >
            {/* Grid background */}
            {config.showGrid && (
                <svg
                    style={{
                        position: 'absolute',
                        top: 0,
                        left: 0,
                        width: '100%',
                        height: '100%',
                        pointerEvents: 'none',
                    }}
                >
                    <defs>{gridPattern}</defs>
                    <rect width="100%" height="100%" fill="url(#viewport-grid)" />
                </svg>
            )}

            {/* Content container */}
            <div
                style={{
                    position: 'absolute',
                    transformOrigin: '0 0',
                    transform: `translate(${transform.x}px, ${transform.y}px) scale(${transform.zoom})`,
                    willChange: 'transform',
                }}
            >
                {children}
            </div>

            {/* Zoom indicator */}
            <div
                style={{
                    position: 'absolute',
                    bottom: 16,
                    right: 16,
                    padding: '4px 8px',
                    backgroundColor: 'rgba(255,255,255,0.9)',
                    borderRadius: 4,
                    fontSize: 12,
                    fontFamily: 'monospace',
                    boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
                }}
            >
                {Math.round(transform.zoom * 100)}%
            </div>
        </div>
    );
};

export default InfiniteViewport;
