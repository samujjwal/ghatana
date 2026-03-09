/**
 * Pipeline Node Element for Ghatana Workflows
 * Represents data-driven nodes with inputs/outputs, status, and metadata
 */

import { BaseElementProps, CanvasElementType } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";
import { getTheme } from "../theme/defaults.js";

/**
 * Port definition for pipeline node connections
 */
export interface PipelinePort {
    id: string;
    label: string;
    type: "input" | "output";
    dataType?: string;
    position: "top" | "right" | "bottom" | "left";
}

/**
 * Pipeline node status
 */
export type PipelineNodeStatus =
    | "idle"
    | "running"
    | "success"
    | "error"
    | "warning";

/**
 * Pipeline node properties
 */
export interface PipelineNodeProps extends BaseElementProps {
    label: string;
    nodeType: string;
    status?: PipelineNodeStatus;
    inputs?: PipelinePort[];
    outputs?: PipelinePort[];
    metadata?: Record<string, unknown>;
    backgroundColor?: string;
    borderColor?: string;
    textColor?: string;
    icon?: string;
}

/**
 * Pipeline Node Element
 * Specialized element for workflow and data pipeline visualization
 */
export class PipelineNodeElement extends CanvasElement {
    public label: string;
    public nodeType: string;
    public status: PipelineNodeStatus;
    public inputs: PipelinePort[];
    public outputs: PipelinePort[];
    public metadata: Record<string, unknown>;
    public backgroundColor: string;
    public borderColor: string;
    public textColor: string;
    public icon?: string;

    constructor(props: PipelineNodeProps) {
        super(props);
        const theme = getTheme();

        this.label = props.label;
        this.nodeType = props.nodeType;
        this.status = props.status ?? "idle";
        this.inputs = props.inputs ?? [];
        this.outputs = props.outputs ?? [];
        this.metadata = props.metadata ?? {};
        this.backgroundColor = props.backgroundColor ?? theme.colors.shapeFillColor;
        this.borderColor = props.borderColor ?? theme.colors.shapeStrokeColor;
        this.textColor = props.textColor ?? theme.colors.shapeTextColor;
        this.icon = props.icon;
    }

    get type(): CanvasElementType {
        return "shape";
    }

    render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
        ctx.save();
        this.applyTransform(ctx);

        const bound = this.getBounds();
        const headerHeight = 40;
        const radius = 8;

        // Semantic Zoom Optimization
        const isLowDetail = zoom < 0.3; // Bird's Eye
        const isMediumDetail = zoom < 0.7; // Overview

        // Draw node background with status color
        ctx.fillStyle = this.getStatusColor();
        ctx.strokeStyle = this.borderColor;
        ctx.lineWidth = 2 / zoom; // Maintain line width visual consistency

        if (isLowDetail) {
            // bird_eye: simplified render (just a box)
            this.drawRoundedRect(ctx, bound, radius);
            ctx.restore();
            return;
        }

        this.drawRoundedRect(ctx, bound, radius);

        // Draw header section
        ctx.fillStyle = this.darkenColor(this.getStatusColor(), 0.1);
        this.drawRoundedRect(
            ctx,
            { x: bound.x, y: bound.y, w: bound.w, h: headerHeight },
            radius
        );

        if (isMediumDetail) {
            // overview: nodes + icons/labels but no ports
            this.renderHeader(ctx, bound, headerHeight);
            ctx.restore();
            return;
        }

        // detailed: Full node rendering
        this.renderHeader(ctx, bound, headerHeight);

        // Draw ports
        this.drawPorts(ctx, bound);

        // Draw status indicator
        this.drawStatusIndicator(ctx, bound);

        ctx.restore();
    }

    private renderHeader(ctx: CanvasRenderingContext2D, bound: Bound, headerHeight: number): void {
        // Draw icon if present
        if (this.icon) {
            ctx.fillStyle = this.textColor;
            ctx.font = "20px system-ui";
            ctx.textAlign = "left";
            ctx.textBaseline = "middle";
            ctx.fillText(this.icon, bound.x + 12, bound.y + headerHeight / 2);
        }

        // Draw label
        ctx.fillStyle = this.textColor;
        ctx.font = "14px system-ui";
        ctx.textAlign = "left";
        ctx.textBaseline = "middle";
        const labelX = this.icon ? bound.x + 40 : bound.x + 12;
        ctx.fillText(this.label, labelX, bound.y + headerHeight / 2);

        // Draw node type badge
        ctx.fillStyle = this.borderColor;
        ctx.font = "10px system-ui";
        ctx.textAlign = "right";
        ctx.fillText(this.nodeType, bound.x + bound.w - 12, bound.y + headerHeight / 2);
    }

    private drawPorts(ctx: CanvasRenderingContext2D, bound: Bound): void {
        const portRadius = 6;
        const theme = getTheme();

        // Draw input ports on the left
        this.inputs.forEach((port, index) => {
            const y = bound.y + 60 + index * 30;
            ctx.fillStyle = theme.colors.connectorColor;
            ctx.beginPath();
            ctx.arc(bound.x, y, portRadius, 0, 2 * Math.PI);
            ctx.fill();
            ctx.strokeStyle = "#ffffff";
            ctx.lineWidth = 2;
            ctx.stroke();

            ctx.fillStyle = this.textColor;
            ctx.font = "11px system-ui";
            ctx.textAlign = "left";
            ctx.textBaseline = "middle";
            ctx.fillText(port.label, bound.x + 15, y);
        });

        // Draw output ports on the right
        this.outputs.forEach((port, index) => {
            const y = bound.y + 60 + index * 30;
            ctx.fillStyle = theme.colors.connectorColor;
            ctx.beginPath();
            ctx.arc(bound.x + bound.w, y, portRadius, 0, 2 * Math.PI);
            ctx.fill();
            ctx.strokeStyle = "#ffffff";
            ctx.lineWidth = 2;
            ctx.stroke();

            ctx.fillStyle = this.textColor;
            ctx.font = "11px system-ui";
            ctx.textAlign = "right";
            ctx.textBaseline = "middle";
            ctx.fillText(port.label, bound.x + bound.w - 15, y);
        });
    }

    private drawStatusIndicator(ctx: CanvasRenderingContext2D, bound: Bound): void {
        const indicatorSize = 12;
        const x = bound.x + bound.w - indicatorSize - 8;
        const y = bound.y + 8;

        ctx.fillStyle = this.getStatusColor();
        ctx.beginPath();
        ctx.arc(x, y, indicatorSize / 2, 0, 2 * Math.PI);
        ctx.fill();
        ctx.strokeStyle = "#ffffff";
        ctx.lineWidth = 2;
        ctx.stroke();
    }

    private getStatusColor(): string {
        switch (this.status) {
            case "running":
                return "#3b82f6";
            case "success":
                return "#10b981";
            case "error":
                return "#ef4444";
            case "warning":
                return "#f59e0b";
            case "idle":
            default:
                return this.backgroundColor;
        }
    }

    private darkenColor(color: string, factor: number): string {
        const hex = color.replace("#", "");
        const r = Math.max(0, parseInt(hex.substr(0, 2), 16) * (1 - factor));
        const g = Math.max(0, parseInt(hex.substr(2, 2), 16) * (1 - factor));
        const b = Math.max(0, parseInt(hex.substr(4, 2), 16) * (1 - factor));
        return `#${Math.round(r).toString(16).padStart(2, "0")}${Math.round(g).toString(16).padStart(2, "0")}${Math.round(b).toString(16).padStart(2, "0")}`;
    }

    private drawRoundedRect(
        ctx: CanvasRenderingContext2D,
        bound: { x: number; y: number; w: number; h: number },
        radius: number
    ): void {
        const r = Math.max(Math.min(radius, Math.min(bound.w, bound.h) / 2), 0);
        ctx.beginPath();
        ctx.moveTo(bound.x + r, bound.y);
        ctx.lineTo(bound.x + bound.w - r, bound.y);
        ctx.arcTo(bound.x + bound.w, bound.y, bound.x + bound.w, bound.y + r, r);
        ctx.lineTo(bound.x + bound.w, bound.y + bound.h - r);
        ctx.arcTo(bound.x + bound.w, bound.y + bound.h, bound.x + bound.w - r, bound.y + bound.h, r);
        ctx.lineTo(bound.x + r, bound.y + bound.h);
        ctx.arcTo(bound.x, bound.y + bound.h, bound.x, bound.y + bound.h - r, r);
        ctx.lineTo(bound.x, bound.y + r);
        ctx.arcTo(bound.x, bound.y, bound.x + r, bound.y, r);
        ctx.closePath();
        ctx.fill();
        ctx.stroke();
    }

    public getPortPosition(portId: string): { x: number; y: number } | null {
        const bound = this.getBounds();

        const inputIndex = this.inputs.findIndex(p => p.id === portId);
        if (inputIndex !== -1) {
            return {
                x: bound.x,
                y: bound.y + 60 + inputIndex * 30
            };
        }

        const outputIndex = this.outputs.findIndex(p => p.id === portId);
        if (outputIndex !== -1) {
            return {
                x: bound.x + bound.w,
                y: bound.y + 60 + outputIndex * 30
            };
        }

        return null;
    }

    public setStatus(status: PipelineNodeStatus): void {
        this.status = status;
    }

    public updateMetadata(metadata: Record<string, unknown>): void {
        this.metadata = { ...this.metadata, ...metadata };
    }

    includesPoint(x: number, y: number): boolean {
        const bound = this.getBounds();

        if (this.rotate !== 0) {
            return (
                x >= bound.x &&
                x <= bound.x + bound.w &&
                y >= bound.y &&
                y <= bound.y + bound.h
            );
        }

        return (
            x >= bound.x &&
            x <= bound.x + bound.w &&
            y >= bound.y &&
            y <= bound.y + bound.h
        );
    }
}
