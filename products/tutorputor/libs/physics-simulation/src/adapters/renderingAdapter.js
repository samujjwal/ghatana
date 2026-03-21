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
 * Konva-specific capabilities
 */
export const KONVA_CAPABILITIES = {
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
export const REACT_FLOW_CAPABILITIES = {
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
export const WEBGL_CAPABILITIES = {
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
 * Registry of available rendering adapters
 */
export const renderingAdapterRegistry = new Map();
/**
 * Register a rendering adapter
 */
export function registerRenderingAdapter(backend, factory) {
    renderingAdapterRegistry.set(backend, factory);
}
/**
 * Get a rendering adapter
 */
export function getRenderingAdapter(backend, options) {
    const factory = renderingAdapterRegistry.get(backend);
    if (!factory) {
        console.warn(`Rendering adapter for '${backend}' not registered`);
        return null;
    }
    return factory(options);
}
/**
 * Select best adapter based on requirements
 */
export function selectBestAdapter(requirements) {
    const { needsPhysics, needs3D, expectedElementCount = 1000, preferredBackend } = requirements;
    // If preferred and it meets requirements, use it
    if (preferredBackend) {
        const caps = preferredBackend === 'konva'
            ? KONVA_CAPABILITIES
            : preferredBackend === 'webgl'
                ? WEBGL_CAPABILITIES
                : REACT_FLOW_CAPABILITIES;
        if ((!needsPhysics || caps.supportsPhysics) &&
            (!needs3D || caps.supports3D) &&
            expectedElementCount <= caps.maxElements) {
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
//# sourceMappingURL=renderingAdapter.js.map