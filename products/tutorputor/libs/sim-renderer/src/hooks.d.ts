/**
 * Simulation Renderer Hooks
 *
 * @doc.type module
 * @doc.purpose Provide React hooks for simulation rendering
 * @doc.layer product
 * @doc.pattern Hook
 */
import type { SimEntity, SimEntityId } from "@ghatana/tutorputor-contracts/v1/simulation";
import type { RenderContext, RenderTheme, RendererRegistry, EntityAnimation } from "./types";
/**
 * Create and manage a renderer registry.
 */
export declare function useRendererRegistry(): RendererRegistry;
interface UseRenderContextOptions {
    canvasRef: React.RefObject<HTMLCanvasElement | null>;
    entities: SimEntity[];
    width: number;
    height: number;
    zoom?: number;
    panOffset?: {
        x: number;
        y: number;
    };
    theme?: Partial<RenderTheme>;
}
/**
 * Create a render context for the canvas.
 */
export declare function useRenderContext({ canvasRef, entities, width, height, zoom, panOffset, theme: themeOverrides, }: UseRenderContextOptions): RenderContext | null;
interface UseAnimationOptions {
    onUpdate?: (animatedValues: Map<SimEntityId, Record<string, number>>) => void;
}
/**
 * Manage entity animations.
 */
export declare function useAnimation({ onUpdate }?: UseAnimationOptions): {
    animate: (animation: Omit<EntityAnimation, "startTime">) => void;
    stopAnimation: (entityId: SimEntityId, property?: string) => void;
    stopAllAnimations: () => void;
    getAnimatedValue: (entityId: SimEntityId, property: string) => number | undefined;
};
interface UseHitTestOptions {
    registry: RendererRegistry;
    context: RenderContext | null;
    entities: SimEntity[];
}
/**
 * Perform hit testing on entities.
 */
export declare function useHitTest({ registry, context, entities }: UseHitTestOptions): {
    hitTest: (screenX: number, screenY: number) => SimEntity | null;
};
interface UseCanvasRenderingOptions {
    canvasRef: React.RefObject<HTMLCanvasElement | null>;
    registry: RendererRegistry;
    context: RenderContext | null;
    entities: SimEntity[];
    hoveredEntityId?: SimEntityId | null;
    selectedEntityIds?: Set<SimEntityId>;
    showGrid?: boolean;
    backgroundColor?: string;
}
/**
 * Render entities to a canvas.
 */
export declare function useCanvasRendering({ canvasRef, registry, context, entities, hoveredEntityId, selectedEntityIds, showGrid, backgroundColor, }: UseCanvasRenderingOptions): void;
export {};
//# sourceMappingURL=hooks.d.ts.map