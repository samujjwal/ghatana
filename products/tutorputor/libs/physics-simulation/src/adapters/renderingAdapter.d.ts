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
export declare const KONVA_CAPABILITIES: RenderingCapabilities;
/**
 * React Flow-specific capabilities
 */
export declare const REACT_FLOW_CAPABILITIES: RenderingCapabilities;
/**
 * WebGL-specific capabilities
 */
export declare const WEBGL_CAPABILITIES: RenderingCapabilities;
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
export type RenderingAdapterFactory<TElement extends RenderableElement = RenderableElement> = (options?: Record<string, unknown>) => IRenderingAdapter<TElement>;
/**
 * Registry of available rendering adapters
 */
export declare const renderingAdapterRegistry: Map<RenderingBackend, RenderingAdapterFactory>;
/**
 * Register a rendering adapter
 */
export declare function registerRenderingAdapter(backend: RenderingBackend, factory: RenderingAdapterFactory): void;
/**
 * Get a rendering adapter
 */
export declare function getRenderingAdapter<TElement extends RenderableElement = RenderableElement>(backend: RenderingBackend, options?: Record<string, unknown>): IRenderingAdapter<TElement> | null;
/**
 * Select best adapter based on requirements
 */
export declare function selectBestAdapter(requirements: {
    needsPhysics?: boolean;
    needs3D?: boolean;
    expectedElementCount?: number;
    preferredBackend?: RenderingBackend;
}): RenderingBackend;
//# sourceMappingURL=renderingAdapter.d.ts.map