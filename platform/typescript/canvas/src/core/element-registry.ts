/**
 * Element Registry - Pluggable Element Type System
 * 
 * @doc.type class
 * @doc.purpose Registry for pluggable canvas element types with constructor, renderer, and tool mappings
 * @doc.layer core
 * @doc.pattern Registry
 * 
 * Provides AFFiNE-style extensibility where new element types can be registered
 * dynamically without modifying core canvas code.
 */

import { CanvasElement } from "../elements/base.js";
import type { CanvasElementType, BaseElementProps } from "../types/index.js";

/**
 * Element renderer function signature
 * Matches AFFiNE's ElementRenderer pattern
 */
export type ElementRenderer<T extends CanvasElement = CanvasElement> = (
    model: T,
    ctx: CanvasRenderingContext2D,
    zoom: number,
    options?: ElementRenderOptions
) => void;

/**
 * DOM-based element renderer for complex elements
 * Used for elements that need HTML overlays (like editable text)
 */
export type DomElementRenderer<T extends CanvasElement = CanvasElement> = (
    model: T,
    domElement: HTMLElement,
    zoom: number
) => void;

/**
 * Element constructor type
 */
export type ElementConstructor<
    T extends CanvasElement = CanvasElement,
    P extends BaseElementProps = BaseElementProps
> = new (props: P) => T;

/**
 * Options passed to element renderers
 */
export interface ElementRenderOptions {
    /** Whether the element is selected */
    selected?: boolean;
    /** Whether to show handles */
    showHandles?: boolean;
    /** Viewport bounds for culling */
    viewportBounds?: { x: number; y: number; w: number; h: number };
    /** Whether this is a preview/ghost render */
    isPreview?: boolean;
}

/**
 * Element behavior interface - common operations all elements support
 */
export interface ElementBehavior {
    /** Whether element can be connected to */
    connectable: boolean;
    /** Whether element can contain other elements */
    groupable: boolean;
    /** Whether element supports text editing */
    editable: boolean;
    /** Whether element can be resized */
    resizable: boolean;
    /** Whether element can be rotated */
    rotatable: boolean;
    /** Resize constraints */
    resizeConstraints?: {
        lockRatio?: boolean;
        minWidth?: number;
        minHeight?: number;
        maxWidth?: number;
        maxHeight?: number;
    };
    /** Connection points for connectors */
    connectionPoints?: 'corners' | 'edges' | 'center' | 'custom';
}

/**
 * Default behaviors for elements
 */
export const DEFAULT_ELEMENT_BEHAVIOR: ElementBehavior = {
    connectable: true,
    groupable: true,
    editable: false,
    resizable: true,
    rotatable: true,
    connectionPoints: 'edges',
};

/**
 * Registration entry for an element type
 */
export interface ElementRegistration<
    T extends CanvasElement = CanvasElement,
    P extends BaseElementProps = BaseElementProps
> {
    /** Element type identifier */
    type: CanvasElementType | string;
    /** Constructor for creating instances */
    ctor: ElementConstructor<T, P>;
    /** Canvas renderer (required) */
    renderer: ElementRenderer<T>;
    /** Optional DOM renderer for hybrid rendering */
    domRenderer?: DomElementRenderer<T>;
    /** Element behaviors */
    behavior: ElementBehavior;
    /** Default props factory */
    defaultProps?: () => Partial<P>;
    /** Serializer for persistence */
    serialize?: (element: T) => Record<string, unknown>;
    /** Deserializer for loading */
    deserialize?: (data: Record<string, unknown>) => P;
}

/**
 * Simplified element definition used for built-in element registration.
 * More convenient than ElementRegistration for registering elements
 * that use factory functions instead of constructor + renderer pairs.
 */
export interface ElementDefinition<P extends BaseElementProps = BaseElementProps> {
    /** Element type identifier */
    type: CanvasElementType | string;
    /** Display name */
    name: string;
    /** Description */
    description: string;
    /** Category for grouping */
    category: string;
    /** Display icon */
    icon: string;
    /** Factory function to create element instance */
    factory: (props: P) => CanvasElement;
    /** Default property values */
    defaultProps: Partial<P>;
    /** Element capabilities */
    capabilities: ElementBehavior;
}

/**
 * Element Registry
 * 
 * Singleton registry for all canvas element types.
 * Inspired by AFFiNE's elementsCtorMap and ElementRendererIdentifier patterns.
 */
export class ElementRegistry {
    private static instance: ElementRegistry;

    private registrations = new Map<string, ElementRegistration>();
    private renderers = new Map<string, ElementRenderer>();
    private domRenderers = new Map<string, DomElementRenderer>();
    private behaviors = new Map<string, ElementBehavior>();

    private constructor() { }

    static getInstance(): ElementRegistry {
        if (!ElementRegistry.instance) {
            ElementRegistry.instance = new ElementRegistry();
        }
        return ElementRegistry.instance;
    }

    /**
     * Register a new element type from an ElementRegistration
     */
    register<T extends CanvasElement, P extends BaseElementProps>(
        registration: ElementRegistration<T, P>
    ): void;
    /**
     * Register a new element type from an ElementDefinition (simplified form)
     */
    register<P extends BaseElementProps>(
        definition: ElementDefinition<P>
    ): void;
    register<T extends CanvasElement, P extends BaseElementProps>(
        registrationOrDefinition: ElementRegistration<T, P> | ElementDefinition<P>
    ): void {
        if ('ctor' in registrationOrDefinition) {
            // Full ElementRegistration path
            const { type, ctor, renderer, domRenderer, behavior } = registrationOrDefinition;
            this.registrations.set(type, registrationOrDefinition as unknown as ElementRegistration);
            this.renderers.set(type, renderer as ElementRenderer);
            this.behaviors.set(type, behavior);

            if (domRenderer) {
                this.domRenderers.set(type, domRenderer as DomElementRenderer);
            }
        } else {
            // Simplified ElementDefinition path
            const def = registrationOrDefinition;
            this.behaviors.set(def.type, def.capabilities);
            // Store as a lightweight registration for createElement support
            const reg = {
                type: def.type,
                behavior: def.capabilities,
                __factory: def.factory,
                __defaultProps: def.defaultProps,
            };
            this.registrations.set(def.type, reg as unknown as ElementRegistration);
        }
    }

    /**
     * Unregister an element type
     */
    unregister(type: string): void {
        this.registrations.delete(type);
        this.renderers.delete(type);
        this.domRenderers.delete(type);
        this.behaviors.delete(type);
    }

    /**
     * Get constructor for element type
     */
    getConstructor<T extends CanvasElement, P extends BaseElementProps>(
        type: string
    ): ElementConstructor<T, P> | undefined {
        const reg = this.registrations.get(type);
        return reg?.ctor as ElementConstructor<T, P> | undefined;
    }

    /**
     * Get renderer for element type
     */
    getRenderer<T extends CanvasElement>(type: string): ElementRenderer<T> | undefined {
        return this.renderers.get(type) as ElementRenderer<T> | undefined;
    }

    /**
     * Get DOM renderer for element type
     */
    getDomRenderer<T extends CanvasElement>(type: string): DomElementRenderer<T> | undefined {
        return this.domRenderers.get(type) as DomElementRenderer<T> | undefined;
    }

    /**
     * Get behavior definition for element type
     */
    getBehavior(type: string): ElementBehavior {
        return this.behaviors.get(type) || DEFAULT_ELEMENT_BEHAVIOR;
    }

    /**
     * Get registration for element type
     */
    getRegistration<T extends CanvasElement, P extends BaseElementProps>(
        type: string
    ): ElementRegistration<T, P> | undefined {
        return this.registrations.get(type) as ElementRegistration<T, P> | undefined;
    }

    /**
     * Get all registered element types
     */
    getRegisteredTypes(): string[] {
        return Array.from(this.registrations.keys());
    }

    /**
     * Check if element type is registered
     */
    hasType(type: string): boolean {
        return this.registrations.has(type);
    }

    /**
     * Create element instance from type and props
     */
    createElement<T extends CanvasElement, P extends BaseElementProps>(
        type: string,
        props: P
    ): T | null {
        const registration = this.registrations.get(type);
        if (!registration) {
            console.warn(`[ElementRegistry] Unknown element type: ${type}`);
            return null;
        }

        const reg = registration as unknown as Record<string, unknown>;
        const factory = reg.__factory as ((props: P) => T) | undefined;
        const storedDefaults = reg.__defaultProps as Partial<P> | undefined;
        const defaultProps = storedDefaults || registration.defaultProps?.() || {};
        const mergedProps = { ...defaultProps, ...props };

        if (factory) {
            return factory(mergedProps as P) as T;
        }

        return new registration.ctor(mergedProps as BaseElementProps) as T;
    }

    /**
     * Render element using registered renderer
     */
    renderElement(
        element: CanvasElement,
        ctx: CanvasRenderingContext2D,
        zoom: number,
        options?: ElementRenderOptions
    ): void {
        const renderer = this.renderers.get(element.type);
        if (renderer) {
            renderer(element, ctx, zoom, options);
        } else {
            // Fallback to element's own render method
            element.render(ctx, zoom);
        }
    }

    /**
     * Check if element is connectable
     */
    isConnectable(element: CanvasElement): boolean {
        return this.getBehavior(element.type).connectable;
    }

    /**
     * Check if element is groupable
     */
    isGroupable(element: CanvasElement): boolean {
        return this.getBehavior(element.type).groupable;
    }

    /**
     * Check if element is editable
     */
    isEditable(element: CanvasElement): boolean {
        return this.getBehavior(element.type).editable;
    }
}

/**
 * Extension helper for registering element renderers
 * Matches AFFiNE's ElementRendererExtension pattern
 */
export function ElementRendererExtension<T extends CanvasElement>(
    type: string,
    renderer: ElementRenderer<T>
): { type: string; renderer: ElementRenderer<T> } {
    return { type, renderer };
}

/**
 * Extension helper for registering DOM element renderers
 * Matches AFFiNE's DomElementRendererExtension pattern
 */
export function DomElementRendererExtension<T extends CanvasElement>(
    type: string,
    renderer: DomElementRenderer<T>
): { type: string; renderer: DomElementRenderer<T> } {
    return { type, renderer };
}

// Export singleton instance
export const elementRegistry = ElementRegistry.getInstance();
