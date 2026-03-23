/**
 * Frame Element - Container element for grouping and organizing canvas content
 * 
 * @doc.type class
 * @doc.purpose Container element for visually grouping elements on canvas
 * @doc.layer elements
 * @doc.pattern Element
 * 
 * Implements AFFiNE-style frames for:
 * - Visual grouping of elements
 * - Named sections/areas
 * - Export boundaries
 * - Presentation slides
 */

import { BaseElementProps, CanvasElementType } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";
import { themeManager, YAPPCTheme } from "../theme/index.js";

export interface FrameProps extends BaseElementProps {
    /** Frame title */
    title?: string;
    /** Background color */
    backgroundColor?: string;
    /** Border color */
    borderColor?: string;
    /** Border width */
    borderWidth?: number;
    /** Border radius */
    borderRadius?: number;
    /** Whether frame is collapsed */
    collapsed?: boolean;
    /** Child element IDs */
    childIds?: string[];
    /** Frame presentation order */
    presentationIndex?: number;
    /** Whether to show title */
    showTitle?: boolean;
}

/**
 * Frame Element
 * 
 * Visual container for grouping elements. Unlike Group which is purely logical,
 * Frame has a visible boundary and title. Used for:
 * - Creating sections in a whiteboard
 * - Defining export/print areas
 * - Presentation slides
 */
export class FrameElement extends CanvasElement {
    public title: string;
    public backgroundColor: string;
    public borderColor: string;
    public borderWidth: number;
    public borderRadius: number;
    public collapsed: boolean;
    public childIds: string[];
    public presentationIndex?: number;
    public showTitle: boolean;

    private static readonly TITLE_HEIGHT = 32;
    private static readonly TITLE_PADDING = 12;

    constructor(props: FrameProps) {
        super(props);
        const theme = themeManager.getTheme();

        this.title = props.title || 'Frame';
        this.backgroundColor = props.backgroundColor || 'rgba(255, 255, 255, 0.8)';
        this.borderColor = props.borderColor || theme.colors.border.medium;
        this.borderWidth = props.borderWidth ?? 2;
        this.borderRadius = props.borderRadius ?? 8;
        this.collapsed = props.collapsed ?? false;
        this.childIds = props.childIds ?? [];
        this.presentationIndex = props.presentationIndex;
        this.showTitle = props.showTitle ?? true;
    }

    get type(): CanvasElementType | 'frame' {
        return 'frame' as unknown as CanvasElementType;
    }

    render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
        ctx.save();
        this.applyTransform(ctx);

        const bound = this.getBounds();
        const theme = themeManager.getTheme();

        // Draw background
        this.drawBackground(ctx, bound, zoom);

        // Draw border
        this.drawBorder(ctx, bound, zoom);

        // Draw title if visible and zoom is high enough
        if (this.showTitle && zoom > 0.3) {
            this.drawTitle(ctx, bound, zoom, theme);
        }

        ctx.restore();
    }

    private drawBackground(
        ctx: CanvasRenderingContext2D,
        bound: { x: number; y: number; w: number; h: number },
        zoom: number
    ): void {
        ctx.fillStyle = this.backgroundColor;

        if (this.borderRadius > 0) {
            this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
            ctx.fill();
        } else {
            ctx.fillRect(bound.x, bound.y, bound.w, bound.h);
        }
    }

    private drawBorder(
        ctx: CanvasRenderingContext2D,
        bound: { x: number; y: number; w: number; h: number },
        zoom: number
    ): void {
        ctx.strokeStyle = this.borderColor;
        ctx.lineWidth = this.borderWidth / zoom;
        ctx.setLineDash([]);

        if (this.borderRadius > 0) {
            this.drawRoundedRect(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
            ctx.stroke();
        } else {
            ctx.strokeRect(bound.x, bound.y, bound.w, bound.h);
        }
    }

    private drawTitle(
        ctx: CanvasRenderingContext2D,
        bound: { x: number; y: number; w: number; h: number },
        zoom: number,
        theme: YAPPCTheme
    ): void {
        const titleHeight = FrameElement.TITLE_HEIGHT;
        const padding = FrameElement.TITLE_PADDING;

        // Title background
        ctx.fillStyle = this.borderColor;
        const titleBgHeight = titleHeight;

        if (this.borderRadius > 0) {
            // Draw rounded top only
            ctx.beginPath();
            ctx.moveTo(bound.x + this.borderRadius, bound.y);
            ctx.lineTo(bound.x + bound.w - this.borderRadius, bound.y);
            ctx.quadraticCurveTo(bound.x + bound.w, bound.y, bound.x + bound.w, bound.y + this.borderRadius);
            ctx.lineTo(bound.x + bound.w, bound.y + titleBgHeight);
            ctx.lineTo(bound.x, bound.y + titleBgHeight);
            ctx.lineTo(bound.x, bound.y + this.borderRadius);
            ctx.quadraticCurveTo(bound.x, bound.y, bound.x + this.borderRadius, bound.y);
            ctx.closePath();
            ctx.fill();
        } else {
            ctx.fillRect(bound.x, bound.y, bound.w, titleBgHeight);
        }

        // Title text
        const fontSize = Math.max(12, 14 / zoom);
        ctx.fillStyle = '#ffffff';
        ctx.font = `600 ${fontSize}px ${theme.typography.fontFamily}`;
        ctx.textAlign = 'left';
        ctx.textBaseline = 'middle';

        // Truncate title if too long
        const maxWidth = bound.w - padding * 2;
        let displayTitle = this.title;
        let textWidth = ctx.measureText(displayTitle).width;

        if (textWidth > maxWidth) {
            while (textWidth > maxWidth && displayTitle.length > 3) {
                displayTitle = displayTitle.slice(0, -1);
                textWidth = ctx.measureText(displayTitle + '...').width;
            }
            displayTitle += '...';
        }

        ctx.fillText(displayTitle, bound.x + padding, bound.y + titleBgHeight / 2);

        // Presentation index badge
        if (this.presentationIndex !== undefined && zoom > 0.5) {
            this.drawPresentationBadge(ctx, bound, zoom, theme);
        }
    }

    private drawPresentationBadge(
        ctx: CanvasRenderingContext2D,
        bound: { x: number; y: number; w: number; h: number },
        zoom: number,
        theme: YAPPCTheme
    ): void {
        const badgeSize = 24;
        const badgeX = bound.x + bound.w - badgeSize - 8;
        const badgeY = bound.y + 4;

        // Badge background
        ctx.fillStyle = theme.colors.accent;
        ctx.beginPath();
        ctx.arc(badgeX + badgeSize / 2, badgeY + badgeSize / 2, badgeSize / 2, 0, Math.PI * 2);
        ctx.fill();

        // Badge number
        ctx.fillStyle = '#ffffff';
        ctx.font = `600 ${12}px ${theme.typography.fontFamily}`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(String(this.presentationIndex! + 1), badgeX + badgeSize / 2, badgeY + badgeSize / 2);
    }

    private drawRoundedRect(
        ctx: CanvasRenderingContext2D,
        x: number,
        y: number,
        w: number,
        h: number,
        r: number
    ): void {
        ctx.beginPath();
        ctx.moveTo(x + r, y);
        ctx.lineTo(x + w - r, y);
        ctx.quadraticCurveTo(x + w, y, x + w, y + r);
        ctx.lineTo(x + w, y + h - r);
        ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
        ctx.lineTo(x + r, y + h);
        ctx.quadraticCurveTo(x, y + h, x, y + h - r);
        ctx.lineTo(x, y + r);
        ctx.quadraticCurveTo(x, y, x + r, y);
        ctx.closePath();
    }

    includesPoint(x: number, y: number): boolean {
        const bound = this.getBounds();
        return bound.containsPoint({ x, y });
    }

    /**
     * Add child element
     */
    addChild(elementId: string): void {
        if (!this.childIds.includes(elementId)) {
            this.childIds.push(elementId);
        }
    }

    /**
     * Remove child element
     */
    removeChild(elementId: string): void {
        const index = this.childIds.indexOf(elementId);
        if (index !== -1) {
            this.childIds.splice(index, 1);
        }
    }

    /**
     * Check if element is a child
     */
    hasChild(elementId: string): boolean {
        return this.childIds.includes(elementId);
    }

    /**
     * Get content area bounds (excluding title)
     */
    getContentBounds(): Bound {
        const bound = this.getBounds();
        if (this.showTitle) {
            return Bound.fromXYWH(
                bound.x,
                bound.y + FrameElement.TITLE_HEIGHT,
                bound.w,
                bound.h - FrameElement.TITLE_HEIGHT
            );
        }
        return bound;
    }
}
