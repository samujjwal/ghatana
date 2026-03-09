/**
 * Rendering Adapter Abstraction
 *
 * Provides a unified interface for different rendering backends:
 * - React Flow (SVG/DOM) - node/edge diagrams
 * - Konva (Canvas 2D) - physics simulations, games
 * - WebGL - high-performance 3D/large datasets
 *
 * @doc.type module
 * @doc.purpose Multi-backend rendering abstraction
 * @doc.layer core
 * @doc.pattern Adapter
 */

/**
 * Supported rendering backends
 */
export type RenderingBackend = 'react-flow' | 'konva' | 'webgl' | 'svg';

/**
 * Base element that all rendering backends support
 */
export interface RenderableElement {
    id: string;
    type: string;
    x: number;
    y: number;
    width?: number;
    height?: number;
    rotation?: number;
    opacity?: number;
    visible?: boolean;
    metadata?: Record<string, unknown>;
}

/**
 * Rendering capabilities per backend
 */
export interface RenderingCapabilities {
    backend: RenderingBackend;
    maxElements: number;
    supportsPhysics: boolean;
    supportsAnimations: boolean;
    supports3D: boolean;
    supportsOffscreen: boolean;
    supportsHitDetection: boolean;
    supportsCustomShapes: boolean;
}

/**
 * Konva-specific capabilities
 */
export const KONVA_CAPABILITIES: RenderingCapabilities = {
    backend: 'konva',
    maxElements: 10000,
    supportsPhysics: true,
    supportsAnimations: true,
    supports3D: false,
    supportsOffscreen: true,
    supportsHitDetection: true,
    supportsCustomShapes: true,
};

/**
 * React Flow-specific capabilities
 */
export const REACT_FLOW_CAPABILITIES: RenderingCapabilities = {
    backend: 'react-flow',
    maxElements: 5000,
    supportsPhysics: false,
    supportsAnimations: true,
    supports3D: false,
    supportsOffscreen: false,
    supportsHitDetection: true,
    supportsCustomShapes: true,
};

/**
 * WebGL-specific capabilities
 */
export const WEBGL_CAPABILITIES: RenderingCapabilities = {
    backend: 'webgl',
    maxElements: 100000,
    supportsPhysics: true,
    supportsAnimations: true,
    supports3D: true,
    supportsOffscreen: true,
    supportsHitDetection: true,
    supportsCustomShapes: true,
};

/**
 * Rendering adapter interface
 */
export interface IRenderingAdapter<TElement extends RenderableElement = RenderableElement> {
    /** Backend type */
    readonly backend: RenderingBackend;

    /** Capabilities of this adapter */
    readonly capabilities: RenderingCapabilities;

    /** Initialize the renderer */
    initialize(container: HTMLElement): Promise<void>;

    /** Destroy and cleanup */
    destroy(): void;

    /** Render elements */
    render(elements: TElement[]): void;

    /** Get current state */
    getState(): TElement[];

    /** Handle element selection */
    selectElement(id: string): void;

    /** Clear selection */
    clearSelection(): void;

    /** Convert to serializable format */
    serialize(): Record<string, unknown>;

    /** Restore from serialized format */
    deserialize(data: Record<string, unknown>): void;
}

/**
 * Factory function type for creating adapters
 */
export type RenderingAdapterFactory<TElement extends RenderableElement = RenderableElement> = (
    options?: Record<string, unknown>
) => IRenderingAdapter<TElement>;

/**
 * Registry of available rendering adapters
 */
export const renderingAdapterRegistry: Map<RenderingBackend, RenderingAdapterFactory> = new Map();

/**
 * Register a rendering adapter
 */
export function registerRenderingAdapter(
    backend: RenderingBackend,
    factory: RenderingAdapterFactory
): void {
    renderingAdapterRegistry.set(backend, factory);
}

/**
 * Get a rendering adapter
 */
export function getRenderingAdapter<TElement extends RenderableElement = RenderableElement>(
    backend: RenderingBackend,
    options?: Record<string, unknown>
): IRenderingAdapter<TElement> | null {
    const factory = renderingAdapterRegistry.get(backend);
    if (!factory) {
        console.warn(`Rendering adapter for '${backend}' not registered`);
        return null;
    }
    return factory(options) as IRenderingAdapter<TElement>;
}

/**
 * Select best adapter based on requirements
 */
export function selectBestAdapter(requirements: {
    needsPhysics?: boolean;
    needs3D?: boolean;
    expectedElementCount?: number;
    preferredBackend?: RenderingBackend;
}): RenderingBackend {
    const { needsPhysics, needs3D, expectedElementCount = 1000, preferredBackend } = requirements;

    // If preferred and it meets requirements, use it
    if (preferredBackend) {
        const caps =
            preferredBackend === 'konva'
                ? KONVA_CAPABILITIES
                : preferredBackend === 'webgl'
                    ? WEBGL_CAPABILITIES
                    : REACT_FLOW_CAPABILITIES;

        if (
            (!needsPhysics || caps.supportsPhysics) &&
            (!needs3D || caps.supports3D) &&
            expectedElementCount <= caps.maxElements
        ) {
            return preferredBackend;
        }
    }

    // Auto-select based on requirements
    if (needs3D || expectedElementCount > 10000) {
        return 'webgl';
    }

    if (needsPhysics) {
        return 'konva';
    }

    return 'react-flow';
}
