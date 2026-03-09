/**
 * UI Component Element
 * Wraps actual React/HTML components for rendering in the canvas
 */

import { BaseElementProps, CanvasElementType, PointTestOptions } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";
import { getTheme } from "../theme/defaults.js";

export type UIComponentType =
    | "button"
    | "input"
    | "textarea"
    | "select"
    | "checkbox"
    | "radio"
    | "card"
    | "table"
    | "form"
    | "modal"
    | "dialog"
    | "alert"
    | "tabs"
    | "badge"
    | "spinner"
    | "divider"
    | "avatar"
    | "chart"
    | "data-grid"
    | "app-header"
    | "sidebar"
    | "toolbar"
    | "line-chart"
    | "bar-chart"
    | "pie-chart"
    | "area-chart"
    | "donut-chart"
    | "metric-chart"
    | "sparkline-chart";

export interface UIComponentProps extends BaseElementProps {
    componentType: UIComponentType;
    props: Record<string, unknown>;
    children?: string | UIComponentElement[];
    className?: string;
    style?: Record<string, unknown>;
    events?: Record<string, Function>;
    parentId?: string;
    childIds?: string[];
}

/**
 * UIComponentElement - Represents an actual HTML/React component on the canvas
 * This element renders as a real DOM element overlaid on the canvas
 */
export class UIComponentElement extends CanvasElement {
    public componentType: UIComponentType;
    public props: Record<string, unknown>;
    public children?: string | UIComponentElement[];
    public className?: string;
    public style?: Record<string, unknown>;
    public events?: Record<string, Function>;
    public domElement?: HTMLElement;
    public parentId?: string;
    public childIds: string[] = [];

    constructor(props: UIComponentProps) {
        super(props);
        const theme = getTheme();

        this.componentType = props.componentType;
        this.props = props.props || {};
        this.children = props.children;
        this.className = props.className || "";
        this.style = props.style || {};
        this.events = props.events || {};
        this.parentId = props.parentId;
        this.childIds = props.childIds || [];
    }

    get type(): CanvasElementType {
        return "shape"; // Register as shape for now, can extend type system later
    }

    /**
     * Render selection box and placeholder on canvas
     * Actual component is rendered via DOM overlay
     */
    render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
        ctx.save();
        this.applyTransform(ctx);

        const bound = this.getBounds();

        // Draw selection box if selected
        if (this.selected) {
            ctx.strokeStyle = "#3b82f6";
            ctx.lineWidth = 2 / zoom;
            ctx.setLineDash([5 / zoom, 5 / zoom]);
            ctx.strokeRect(bound.x, bound.y, bound.w, bound.h);
            ctx.setLineDash([]);
        } else {
            // Draw subtle border to show component bounds
            ctx.strokeStyle = "#e5e7eb";
            ctx.lineWidth = 1 / zoom;
            ctx.strokeRect(bound.x, bound.y, bound.w, bound.h);
        }

        // Draw component type label at low zoom
        if (zoom < 0.5) {
            ctx.fillStyle = "#6b7280";
            ctx.font = `${12 / zoom}px sans-serif`;
            ctx.textAlign = "center";
            ctx.textBaseline = "middle";
            ctx.fillText(
                this.componentType.toUpperCase(),
                bound.x + bound.w / 2,
                bound.y + bound.h / 2
            );
        }

        ctx.restore();
    }

    includesPoint(x: number, y: number, options?: PointTestOptions): boolean {
        const bound = this.getBounds();
        return bound.containsPoint({ x, y });
    }

    /**
     * Get component configuration for DOM rendering
     */
    getComponentConfig(): { componentType: string; props: Record<string, unknown>; children?: string; className?: string; style?: Record<string, unknown>; events?: Record<string, Function> } {
        return {
            componentType: this.componentType,
            props: this.props,
            children: typeof this.children === 'string' ? this.children : undefined,
            className: this.className,
            style: this.style,
            events: this.events
        };
    }

    /**
     * Update component props
     */
    updateProps(newProps: Partial<Record<string, unknown>>) {
        this.props = { ...this.props, ...newProps };
    }

    /**
     * Add a child component
     */
    addChild(child: UIComponentElement): void {
        if (!this.childIds.includes(child.id)) {
            this.childIds.push(child.id);
            child.parentId = this.id;
        }
    }

    /**
     * Remove a child component
     */
    removeChild(childId: string): void {
        const index = this.childIds.indexOf(childId);
        if (index > -1) {
            this.childIds.splice(index, 1);
        }
    }

    /**
     * Get all child components
     */
    getChildren(): string[] {
        return [...this.childIds];
    }

    /**
     * Check if this component can contain children
     */
    canContainChildren(): boolean {
        const containerTypes = ['card', 'form', 'modal', 'dialog', 'app-header', 'sidebar', 'toolbar'];
        return containerTypes.includes(this.componentType);
    }

    /**
     * Check if this component is a child of another component
     */
    isChild(): boolean {
        return !!this.parentId;
    }

    /**
     * Check if this component has children
     */
    hasChildren(): boolean {
        return this.childIds.length > 0;
    }

    /**
     * Update component style
     */
    updateStyle(newStyle: Partial<Record<string, unknown>>) {
        this.style = { ...this.style, ...newStyle };
    }
}
