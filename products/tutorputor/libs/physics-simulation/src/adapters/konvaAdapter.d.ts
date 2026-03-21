/**
 * Konva Rendering Adapter
 *
 * Implements the rendering adapter interface for Konva-based canvas rendering.
 * Optimized for physics simulations and interactive graphics.
 *
 * @doc.type class
 * @doc.purpose Konva rendering adapter implementation
 * @doc.layer core
 * @doc.pattern Adapter
 */
import type { PhysicsEntity } from '../types';
import { type IRenderingAdapter, type RenderingBackend, type RenderingCapabilities } from './renderingAdapter';
/**
 * Konva rendering adapter for physics entities
 */
export declare class KonvaRenderingAdapter implements IRenderingAdapter<PhysicsEntity> {
    readonly backend: RenderingBackend;
    readonly capabilities: RenderingCapabilities;
    private stage;
    private layer;
    private elements;
    private selectedId;
    private onSelect?;
    private onMove?;
    constructor(options?: {
        onSelect?: (id: string) => void;
        onMove?: (id: string, x: number, y: number) => void;
    });
    initialize(container: HTMLElement): Promise<void>;
    destroy(): void;
    render(entities: PhysicsEntity[]): void;
    private createShape;
    private updateShape;
    getState(): PhysicsEntity[];
    selectElement(id: string): void;
    clearSelection(): void;
    serialize(): Record<string, unknown>;
    deserialize(data: Record<string, unknown>): void;
}
export { KonvaRenderingAdapter as default };
//# sourceMappingURL=konvaAdapter.d.ts.map