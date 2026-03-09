/**
 * @doc.type module
 * @doc.purpose Adapter exports for rendering backends
 * @doc.layer core
 * @doc.pattern Barrel
 */

export {
    type RenderingBackend,
    type RenderableElement,
    type RenderingCapabilities,
    type IRenderingAdapter,
    type RenderingAdapterFactory,
    KONVA_CAPABILITIES,
    REACT_FLOW_CAPABILITIES,
    WEBGL_CAPABILITIES,
    renderingAdapterRegistry,
    registerRenderingAdapter,
    getRenderingAdapter,
    selectBestAdapter,
} from './renderingAdapter';

export { KonvaRenderingAdapter } from './konvaAdapter';
