/**
 * Simulation Renderer Hooks
 *
 * @doc.type module
 * @doc.purpose Provide React hooks for simulation rendering
 * @doc.layer product
 * @doc.pattern Hook
 */
import { useCallback, useEffect, useRef, useState } from "react";
import { defaultTheme } from "./types";
import { allRenderers } from "./renderers";
import { applyEasing } from "./easing";
// =============================================================================
// Renderer Registry Hook
// =============================================================================
/**
 * Create and manage a renderer registry.
 */
export function useRendererRegistry() {
    const registryRef = useRef(new Map());
    // Initialize with all renderers
    useEffect(() => {
        for (const renderer of allRenderers) {
            for (const entityType of renderer.entityTypes) {
                registryRef.current.set(entityType, renderer);
            }
        }
    }, []);
    const register = useCallback((renderer) => {
        for (const entityType of renderer.entityTypes) {
            registryRef.current.set(entityType, renderer);
        }
    }, []);
    const getRenderer = useCallback((entityType) => {
        return registryRef.current.get(entityType);
    }, []);
    const getRenderersByDomain = useCallback((domain) => {
        return Array.from(registryRef.current.values()).filter((r) => r.domain === domain);
    }, []);
    const hasRenderer = useCallback((entityType) => {
        return registryRef.current.has(entityType);
    }, []);
    return {
        register,
        getRenderer,
        getRenderersByDomain,
        hasRenderer,
    };
}
/**
 * Create a render context for the canvas.
 */
export function useRenderContext({ canvasRef, entities, width, height, zoom = 1, panOffset = { x: 0, y: 0 }, theme: themeOverrides, }) {
    const [timestamp, setTimestamp] = useState(0);
    const lastTimestampRef = useRef(0);
    const entityMapRef = useRef(new Map());
    // Update entity map
    useEffect(() => {
        entityMapRef.current.clear();
        for (const entity of entities) {
            entityMapRef.current.set(entity.id, entity);
        }
    }, [entities]);
    // Animation frame
    useEffect(() => {
        let frameId;
        const tick = (time) => {
            setTimestamp(time);
            frameId = requestAnimationFrame(tick);
        };
        frameId = requestAnimationFrame(tick);
        return () => cancelAnimationFrame(frameId);
    }, []);
    const canvas = canvasRef.current;
    if (!canvas)
        return null;
    const ctx = canvas.getContext("2d");
    if (!ctx)
        return null;
    const theme = { ...defaultTheme, ...themeOverrides };
    const deltaTime = timestamp - lastTimestampRef.current;
    lastTimestampRef.current = timestamp;
    const worldToScreen = (worldX, worldY) => {
        const centerX = width / 2;
        const centerY = height / 2;
        return {
            x: worldX * zoom + centerX + panOffset.x,
            y: worldY * zoom + centerY + panOffset.y,
        };
    };
    const screenToWorld = (screenX, screenY) => {
        const centerX = width / 2;
        const centerY = height / 2;
        return {
            x: (screenX - centerX - panOffset.x) / zoom,
            y: (screenY - centerY - panOffset.y) / zoom,
        };
    };
    return {
        ctx,
        zoom,
        panOffset,
        width,
        height,
        timestamp,
        deltaTime,
        theme,
        entities: entityMapRef.current,
        worldToScreen,
        screenToWorld,
    };
}
/**
 * Manage entity animations.
 */
export function useAnimation({ onUpdate } = {}) {
    const stateRef = useRef({
        animations: [],
        frameId: null,
        isRunning: false,
    });
    const animatedValuesRef = useRef(new Map());
    const tick = useCallback((timestamp) => {
        const state = stateRef.current;
        const completedAnimations = [];
        for (const animation of state.animations) {
            const elapsed = timestamp - animation.startTime - animation.delay;
            if (elapsed < 0)
                continue;
            const progress = Math.min(elapsed / animation.duration, 1);
            const easedProgress = applyEasing(progress, animation.easing);
            const currentValue = animation.fromValue + (animation.toValue - animation.fromValue) * easedProgress;
            // Update animated value
            let entityValues = animatedValuesRef.current.get(animation.entityId);
            if (!entityValues) {
                entityValues = {};
                animatedValuesRef.current.set(animation.entityId, entityValues);
            }
            entityValues[animation.property] = currentValue;
            if (progress >= 1) {
                completedAnimations.push(animation);
            }
        }
        // Remove completed animations
        for (const completed of completedAnimations) {
            const index = state.animations.indexOf(completed);
            if (index !== -1) {
                state.animations.splice(index, 1);
            }
            completed.onComplete?.();
        }
        onUpdate?.(animatedValuesRef.current);
        if (state.animations.length > 0) {
            state.frameId = requestAnimationFrame(tick);
        }
        else {
            state.isRunning = false;
            state.frameId = null;
        }
    }, [onUpdate]);
    const animate = useCallback((animation) => {
        const state = stateRef.current;
        const fullAnimation = {
            ...animation,
            startTime: performance.now(),
        };
        state.animations.push(fullAnimation);
        if (!state.isRunning) {
            state.isRunning = true;
            state.frameId = requestAnimationFrame(tick);
        }
    }, [tick]);
    const stopAnimation = useCallback((entityId, property) => {
        const state = stateRef.current;
        state.animations = state.animations.filter((a) => {
            if (a.entityId !== entityId)
                return true;
            if (property && a.property !== property)
                return true;
            return false;
        });
    }, []);
    const stopAllAnimations = useCallback(() => {
        const state = stateRef.current;
        state.animations = [];
        if (state.frameId) {
            cancelAnimationFrame(state.frameId);
            state.frameId = null;
        }
        state.isRunning = false;
    }, []);
    const getAnimatedValue = useCallback((entityId, property) => {
        return animatedValuesRef.current.get(entityId)?.[property];
    }, []);
    // Cleanup on unmount
    useEffect(() => {
        return () => {
            const state = stateRef.current;
            if (state.frameId) {
                cancelAnimationFrame(state.frameId);
            }
        };
    }, []);
    return {
        animate,
        stopAnimation,
        stopAllAnimations,
        getAnimatedValue,
    };
}
/**
 * Perform hit testing on entities.
 */
export function useHitTest({ registry, context, entities }) {
    const hitTest = useCallback((screenX, screenY) => {
        if (!context)
            return null;
        const world = context.screenToWorld(screenX, screenY);
        // Test from top to bottom (reverse order for z-ordering)
        for (let i = entities.length - 1; i >= 0; i--) {
            const entity = entities[i];
            const renderer = registry.getRenderer(entity.type);
            if (renderer && renderer.hitTest(entity, world.x, world.y, context)) {
                return entity;
            }
        }
        return null;
    }, [registry, context, entities]);
    return { hitTest };
}
/**
 * Render entities to a canvas.
 */
export function useCanvasRendering({ canvasRef, registry, context, entities, hoveredEntityId, selectedEntityIds, showGrid = true, backgroundColor = "#f8fafc", }) {
    useEffect(() => {
        if (!context || !canvasRef.current)
            return;
        const { ctx, width, height, zoom, panOffset, theme } = context;
        // Clear canvas
        ctx.fillStyle = backgroundColor;
        ctx.fillRect(0, 0, width, height);
        // Draw grid
        if (showGrid) {
            const gridSize = 50 * zoom;
            const offsetX = ((panOffset.x % gridSize) + gridSize) % gridSize;
            const offsetY = ((panOffset.y % gridSize) + gridSize) % gridSize;
            ctx.strokeStyle = theme.border;
            ctx.lineWidth = 1;
            ctx.globalAlpha = 0.5;
            ctx.beginPath();
            for (let x = offsetX; x < width; x += gridSize) {
                ctx.moveTo(x, 0);
                ctx.lineTo(x, height);
            }
            for (let y = offsetY; y < height; y += gridSize) {
                ctx.moveTo(0, y);
                ctx.lineTo(width, y);
            }
            ctx.stroke();
            ctx.globalAlpha = 1;
        }
        // Sort entities by layer
        const sortedEntities = [...entities].sort((a, b) => (a.layer ?? 0) - (b.layer ?? 0));
        // Render entities
        for (const entity of sortedEntities) {
            if (entity.visible === false)
                continue;
            const renderer = registry.getRenderer(entity.type);
            if (!renderer)
                continue;
            const isHovered = entity.id === hoveredEntityId;
            const isSelected = selectedEntityIds?.has(entity.id) ?? false;
            renderer.render(entity, context, isHovered, isSelected);
        }
    }, [canvasRef, registry, context, entities, hoveredEntityId, selectedEntityIds, showGrid, backgroundColor]);
}
//# sourceMappingURL=hooks.js.map