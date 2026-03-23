/**
 * DOM Overlay Manager
 * Manages rendering of actual HTML/React components over the canvas
 */

import { UIComponentElement } from "../elements/ui-component.js";
import { Viewport } from "../core/viewport.js";
import { ReactComponentRenderer } from "./react-component-renderer.js";

export interface DOMOverlayConfig {
    container: HTMLElement;
    viewport: Viewport;
}

/** Configuration for a DOM-rendered component element */
interface ComponentConfig {
    componentType: string;
    type?: string;
    id?: string;
    props?: Record<string, unknown>;
    children?: unknown;
    className?: string;
    style?: Record<string, unknown>;
    events?: Record<string, unknown>;
    selected?: boolean;
    [key: string]: unknown;
}

/**
 * DOMOverlayManager - Renders actual DOM elements positioned over canvas
 */
export class DOMOverlayManager {
    private container: HTMLElement;
    private overlayContainer: HTMLDivElement;
    private viewport: Viewport;
    private componentElements: Map<string, UIComponentElement> = new Map();
    private domElements: Map<string, HTMLElement> = new Map();
    private reactRenderer: ReactComponentRenderer;

    constructor(config: DOMOverlayConfig) {
        this.container = config.container;
        this.viewport = config.viewport;
        this.reactRenderer = new ReactComponentRenderer();

        // Create overlay container
        this.overlayContainer = document.createElement("div");
        this.overlayContainer.className = "yappc-dom-overlay";
        this.overlayContainer.style.position = "absolute";
        this.overlayContainer.style.top = "0";
        this.overlayContainer.style.left = "0";
        this.overlayContainer.style.width = "100%";
        this.overlayContainer.style.height = "100%";
        this.overlayContainer.style.pointerEvents = "none";
        this.overlayContainer.style.overflow = "hidden";
        this.overlayContainer.style.zIndex = "10";

        this.container.appendChild(this.overlayContainer);
    }

    /**
     * Add a UI component element to be rendered
     */
    addComponent(element: UIComponentElement): void {
        this.componentElements.set(element.id, element);
        this.renderComponent(element);
    }

    /**
     * Remove a UI component element
     */
    removeComponent(elementId: string): void {
        const domElement = this.domElements.get(elementId);
        if (domElement) {
            domElement.remove();
            this.domElements.delete(elementId);
        }
        this.componentElements.delete(elementId);
    }

    /**
     * Update all component positions (called on viewport change or element move)
     */
    updatePositions(): void {
        this.componentElements.forEach((element) => {
            this.updateComponentPosition(element);
        });
    }

    /**
     * Render a single component
     */
    private renderComponent(element: UIComponentElement): void {
        const config = element.getComponentConfig();

        // Create or get existing DOM element
        let domElement = this.domElements.get(element.id);

        if (!domElement) {
            domElement = this.createDOMElement(config);
            this.domElements.set(element.id, domElement);
            this.overlayContainer.appendChild(domElement);

            // Render React component in the DOM element
            this.reactRenderer.renderComponent(domElement, config);
        } else {
            this.updateDOMElement(domElement, config);
            // Update React component
            this.reactRenderer.updateComponent(domElement, config);
        }

        // Update position
        this.updateComponentPosition(element);

        // Store reference
        element.domElement = domElement;
    }

    /**
     * Create React component container for Ghatana UI components
     */
    private createDOMElement(config: ComponentConfig): HTMLElement {
        // Create a container div that will hold the React component
        const element = document.createElement("div");
        element.setAttribute("data-ghatana-component", config.componentType);
        element.setAttribute("data-component-id", config.id || "");

        // Store component config for React rendering
        (element as unknown as Record<string, unknown>).__ghatanaConfig = {
            componentType: config.componentType,
            props: config.props || {},
            children: config.children,
            className: config.className,
            style: config.style || {},
            events: config.events || {}
        };

        // Add placeholder content for visual feedback during development
        this.setPlaceholderContent(element, config);


        // Apply inline styles
        if (config.style) {
            this.applyStyles(element, config.style);
        }

        // Apply default component styles
        this.applyDefaultStyles(element, config.componentType);

        // Enable pointer events on the element
        element.style.pointerEvents = "auto";

        // Attach event listeners
        if (config.events) {
            Object.entries(config.events).forEach(([eventName, handler]) => {
                element.addEventListener(eventName, handler as EventListener);
            });
        }

        // Add selection visual feedback
        if (config.selected) {
            element.style.outline = "2px solid #3b82f6";
            element.style.outlineOffset = "2px";
        }

        return element;
    }

    /**
     * Placeholder content for dev-time feedback (optional)
     */
    private setPlaceholderContent(element: HTMLElement, config: ComponentConfig): void {
        if (config.children) {
            return; // real content provided
        }

        const label = config.props?.label || config.componentType || "Component";
        element.textContent = typeof label === "string" ? label : config.componentType ?? "Component";
    }

    /**
     * Update existing DOM element
     */
    private updateDOMElement(domElement: HTMLElement, config: ComponentConfig): void {
        // Update content based on type
        switch (config.componentType) {
            case "button":
                domElement.textContent = (config.children as string) || (config.props?.label as string) || "Button";
                break;
            case "input":
                (domElement as HTMLInputElement).value = (config.props?.value as string) || "";
                (domElement as HTMLInputElement).placeholder = (config.props?.placeholder as string) || "";
                break;
            case "text":
                domElement.textContent = (config.children as string) || (config.props?.text as string) || "Text";
                break;
        }

        // Update styles
        if (config.style) {
            this.applyStyles(domElement, config.style);
        }

        // Update selection state
        if (config.selected) {
            domElement.style.outline = "2px solid #3b82f6";
            domElement.style.outlineOffset = "2px";
        } else {
            domElement.style.outline = "none";
        }
    }

    /**
     * Apply styles to DOM element
     */
    private applyStyles(element: HTMLElement, styles: Record<string, unknown>): void {
        Object.entries(styles).forEach(([key, value]) => {
            (element.style as unknown as Record<string, string>)[key] = String(value);
        });
    }

    /**
     * Apply default styles based on component type
     */
    private applyDefaultStyles(element: HTMLElement, type: string): void {
        // Common styles
        element.style.boxSizing = "border-box";
        element.style.fontFamily = "system-ui, -apple-system, sans-serif";

        switch (type) {
            case "button":
                element.style.padding = "8px 16px";
                element.style.backgroundColor = "#3b82f6";
                element.style.color = "white";
                element.style.border = "none";
                element.style.borderRadius = "6px";
                element.style.fontSize = "14px";
                element.style.fontWeight = "500";
                element.style.cursor = "pointer";
                element.style.transition = "background-color 0.2s";
                break;

            case "input":
            case "textarea":
                element.style.padding = "8px 12px";
                element.style.border = "1px solid #d1d5db";
                element.style.borderRadius = "6px";
                element.style.fontSize = "14px";
                element.style.width = "100%";
                break;

            case "select":
                element.style.padding = "8px 12px";
                element.style.border = "1px solid #d1d5db";
                element.style.borderRadius = "6px";
                element.style.fontSize = "14px";
                element.style.backgroundColor = "white";
                break;

            case "card":
                element.style.backgroundColor = "white";
                element.style.border = "1px solid #e5e7eb";
                element.style.borderRadius = "8px";
                element.style.boxShadow = "0 1px 3px rgba(0,0,0,0.1)";
                break;

            case "table":
                element.style.width = "100%";
                element.style.borderCollapse = "collapse";
                element.style.backgroundColor = "white";
                break;

            case "text":
                element.style.fontSize = "14px";
                element.style.color = "#374151";
                element.style.margin = "0";
                break;
        }
    }

    /**
     * Update component position based on canvas coordinates and viewport
     */
    private updateComponentPosition(element: UIComponentElement): void {
        const domElement = this.domElements.get(element.id);
        if (!domElement) return;

        const bounds = element.getBounds();
        const zoom = this.viewport.zoom;

        // Convert canvas coordinates to screen coordinates based on viewport center
        const screenX = (bounds.x - (this.viewport.centerX - this.viewport.width / 2)) * zoom;
        const screenY = (bounds.y - (this.viewport.centerY - this.viewport.height / 2)) * zoom;
        const screenW = bounds.w * zoom;
        const screenH = bounds.h * zoom;

        // Apply position and size
        domElement.style.position = "absolute";
        domElement.style.left = `${screenX}px`;
        domElement.style.top = `${screenY}px`;
        domElement.style.width = `${screenW}px`;
        domElement.style.height = `${screenH}px`;

        // Apply rotation if needed
        if (element.rotate !== 0) {
            domElement.style.transform = `rotate(${element.rotate}deg)`;
            domElement.style.transformOrigin = "center center";
        } else {
            domElement.style.transform = "none";
        }
    }

    /**
     * Refresh all components
     */
    refresh(): void {
        this.componentElements.forEach((element) => {
            this.renderComponent(element);
        });
    }

    /**
     * Clear all components
     */
    clear(): void {
        this.overlayContainer.innerHTML = "";
        this.componentElements.clear();
        this.domElements.clear();
    }

    /**
     * Destroy the overlay manager
     */
    destroy(): void {
        this.clear();
        this.reactRenderer.cleanup();
        this.overlayContainer.remove();
    }
}
