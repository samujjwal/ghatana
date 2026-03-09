/**
 * ZoomManager - Deep Zoom System (1%-1000%)
 * 
 * @doc.type class
 * @doc.purpose Manages zoom levels, transitions, and visibility filtering
 * @doc.layer core
 * @doc.pattern Manager
 */

export interface ZoomLevel {
    min: number;          // 0.01 (1%)
    max: number;          // 10.0 (1000%)
    current: number;      // Current zoom level
    step: number;         // Increment/decrement amount (0.1 or 10%)
}

export type ElementType =
    | 'phase-zones'
    | 'milestones'
    | 'artifact-groups'
    | 'nodes'
    | 'connections'
    | 'text'
    | 'node-details'
    | 'small-text'
    | 'all-nodes'
    | 'tasks'
    | 'micro-details'
    | 'node-metadata'
    | 'comments'
    | 'icons'
    | 'edit-handles'
    | 'text-cursor'
    | 'phase-backgrounds'
    | 'code-editor'
    | 'design-tools'
    | 'other-nodes'
    | 'pixels'
    | 'character-level'
    | 'design-tokens'
    | 'canvas-chrome';

export type UIChange =
    | 'show-phase-headers'
    | 'hide-toolbar-details'
    | 'show-zone-names'
    | 'collapse-tasks'
    | 'full-ui'
    | 'expanded-nodes'
    | 'show-properties-panel'
    | 'enable-inline-editing'
    | 'show-formatting'
    | 'fullscreen-mode'
    | 'monaco-editor'
    | 'pixel-grid'
    | 'measurement-tools';

export interface ZoomRange {
    label: string;
    range: [number, number];
    showElements: ElementType[];
    hideElements: ElementType[];
    uiChanges: UIChange[];
}

export type EasingFunction = (t: number) => number;

export const easeInOutCubic: EasingFunction = (t) => {
    return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
};

export const ZOOM_RANGES: ZoomRange[] = [
    {
        label: 'Project Overview',
        range: [0.01, 0.10],
        showElements: ['phase-zones', 'milestones'],
        hideElements: ['nodes', 'connections', 'text'],
        uiChanges: ['show-phase-headers', 'hide-toolbar-details']
    },
    {
        label: 'Phase View',
        range: [0.10, 0.25],
        showElements: ['phase-zones', 'artifact-groups'],
        hideElements: ['node-details', 'small-text'],
        uiChanges: ['show-zone-names', 'collapse-tasks']
    },
    {
        label: 'Working View (DEFAULT)',
        range: [0.25, 0.50],
        showElements: ['all-nodes', 'connections', 'tasks'],
        hideElements: ['micro-details'],
        uiChanges: ['full-ui', 'expanded-nodes']
    },
    {
        label: 'Detail View',
        range: [0.50, 1.00],
        showElements: ['node-metadata', 'comments', 'icons'],
        hideElements: [],
        uiChanges: ['show-properties-panel']
    },
    {
        label: 'Edit View',
        range: [1.00, 2.00],
        showElements: ['edit-handles', 'text-cursor'],
        hideElements: ['phase-backgrounds'],
        uiChanges: ['enable-inline-editing', 'show-formatting']
    },
    {
        label: 'Implementation View',
        range: [2.00, 5.00],
        showElements: ['code-editor', 'design-tools'],
        hideElements: ['other-nodes'],
        uiChanges: ['fullscreen-mode', 'monaco-editor']
    },
    {
        label: 'Micro View',
        range: [5.00, 10.00],
        showElements: ['pixels', 'character-level', 'design-tokens'],
        hideElements: ['canvas-chrome'],
        uiChanges: ['pixel-grid', 'measurement-tools']
    }
];

export interface ViewportState {
    x: number;
    y: number;
    zoom: number;
}

export interface Bounds {
    x: number;
    y: number;
    width: number;
    height: number;
    center: { x: number; y: number };
}

export class ZoomManager {
    private currentZoom: number = 0.5;  // Default 50% (Working View)
    private targetZoom: number = 0.5;
    private animating: boolean = false;
    private breadcrumbTrail: string[] = [];
    private zoomHistory: ViewportState[] = [];
    private currentHistoryIndex: number = -1;

    private onZoomChangeCallbacks: Array<(zoom: number) => void> = [];
    private onViewportChangeCallbacks: Array<(viewport: ViewportState) => void> = [];

    constructor(
        private getNode: (id: string) => any,
        private updateViewport: (viewport: Partial<ViewportState>) => void
    ) {
        this.pushHistory({ x: 0, y: 0, zoom: this.currentZoom });
    }

    /**
     * Get current zoom level
     */
    getCurrentZoom(): number {
        return this.currentZoom;
    }

    /**
     * Get current zoom range info
     */
    getCurrentZoomRange(): ZoomRange | undefined {
        return ZOOM_RANGES.find(
            range => this.currentZoom >= range.range[0] && this.currentZoom < range.range[1]
        );
    }

    /**
     * Check if element type should be visible at current zoom
     */
    isElementVisible(elementType: ElementType): boolean {
        const range = this.getCurrentZoomRange();
        if (!range) return true;

        return range.showElements.includes(elementType) &&
            !range.hideElements.includes(elementType);
    }

    /**
     * Check if UI change is active at current zoom
     */
    isUIChangeActive(uiChange: UIChange): boolean {
        const range = this.getCurrentZoomRange();
        if (!range) return false;

        return range.uiChanges.includes(uiChange);
    }

    /**
     * Smooth zoom with easing
     */
    zoomTo(
        target: number,
        duration: number = 300,
        easing: EasingFunction = easeInOutCubic
    ): Promise<void> {
        return new Promise((resolve) => {
            this.targetZoom = this.clamp(target, 0.01, 10);
            this.animating = true;

            const startZoom = this.currentZoom;
            const startTime = performance.now();

            const animate = (currentTime: number) => {
                const elapsed = currentTime - startTime;
                const progress = Math.min(elapsed / duration, 1);
                const easedProgress = easing(progress);

                this.currentZoom = startZoom + (this.targetZoom - startZoom) * easedProgress;

                // Update viewport
                this.updateViewport({ zoom: this.currentZoom });

                // Notify listeners
                this.notifyZoomChange(this.currentZoom);

                if (progress < 1) {
                    requestAnimationFrame(animate);
                } else {
                    this.animating = false;
                    this.onZoomComplete();
                    resolve();
                }
            };

            requestAnimationFrame(animate);
        });
    }

    /**
     * Zoom in by step
     */
    zoomIn(step: number = 0.1): Promise<void> {
        return this.zoomTo(this.currentZoom + step);
    }

    /**
     * Zoom out by step
     */
    zoomOut(step: number = 0.1): Promise<void> {
        return this.zoomTo(this.currentZoom - step);
    }

    /**
     * Reset zoom to 100%
     */
    resetZoom(): Promise<void> {
        return this.zoomTo(1.0);
    }

    /**
     * Zoom to fit bounds
     */
    zoomToFit(bounds: Bounds, padding: number = 50): Promise<void> {
        // Calculate zoom to fit bounds in viewport
        // This would need viewport dimensions passed in
        const targetZoom = this.calculateFitZoom(bounds);
        return this.zoomTo(targetZoom);
    }

    /**
     * Double-click to zoom into child
     */
    async zoomIntoNode(nodeId: string): Promise<void> {
        const node = this.getNode(nodeId);
        if (!node?.children || node.children.length === 0) return;

        // Calculate bounds of child content
        const childBounds = this.calculateChildrenBounds(node.children);

        // Calculate zoom to fit
        const targetZoom = this.calculateFitZoom(childBounds);

        // Animate to new zoom and position
        await this.zoomTo(targetZoom, 400);
        this.panTo(childBounds.center, 400);

        // Update breadcrumb trail
        this.breadcrumbTrail.push(nodeId);
    }

    /**
     * Navigate back to parent
     */
    async zoomOutToParent(): Promise<void> {
        if (this.breadcrumbTrail.length === 0) return;

        const parentId = this.breadcrumbTrail.pop()!;
        const parent = this.getNode(parentId);

        if (!parent) return;

        // Calculate parent view
        const parentBounds = this.getNodeBounds(parent);
        const targetZoom = this.calculateFitZoom(parentBounds);

        await this.zoomTo(targetZoom, 400);
        this.panTo(parentBounds.center, 400);
    }

    /**
     * Get breadcrumb trail
     */
    getBreadcrumbTrail(): string[] {
        return [...this.breadcrumbTrail];
    }

    /**
     * Navigate to specific node in breadcrumb
     */
    async navigateToBreadcrumb(index: number): Promise<void> {
        if (index < 0 || index >= this.breadcrumbTrail.length) return;

        // Remove items after index
        this.breadcrumbTrail = this.breadcrumbTrail.slice(0, index + 1);

        const nodeId = this.breadcrumbTrail[index];
        await this.zoomIntoNode(nodeId);
    }

    /**
     * History navigation
     */
    canGoBack(): boolean {
        return this.currentHistoryIndex > 0;
    }

    canGoForward(): boolean {
        return this.currentHistoryIndex < this.zoomHistory.length - 1;
    }

    async goBack(): Promise<void> {
        if (!this.canGoBack()) return;

        this.currentHistoryIndex--;
        const state = this.zoomHistory[this.currentHistoryIndex];
        await this.restoreViewport(state);
    }

    async goForward(): Promise<void> {
        if (!this.canGoForward()) return;

        this.currentHistoryIndex++;
        const state = this.zoomHistory[this.currentHistoryIndex];
        await this.restoreViewport(state);
    }

    /**
     * Add zoom change listener
     */
    onZoomChange(callback: (zoom: number) => void): () => void {
        this.onZoomChangeCallbacks.push(callback);
        return () => {
            this.onZoomChangeCallbacks = this.onZoomChangeCallbacks.filter(cb => cb !== callback);
        };
    }

    /**
     * Add viewport change listener
     */
    onViewportChange(callback: (viewport: ViewportState) => void): () => void {
        this.onViewportChangeCallbacks.push(callback);
        return () => {
            this.onViewportChangeCallbacks = this.onViewportChangeCallbacks.filter(cb => cb !== callback);
        };
    }

    // Private helper methods

    private clamp(value: number, min: number, max: number): number {
        return Math.min(Math.max(value, min), max);
    }

    private calculateFitZoom(bounds: Bounds): number {
        // Simplified - would need viewport dimensions
        // Calculate zoom level to fit bounds in viewport
        return 1.0;
    }

    private calculateChildrenBounds(childIds: string[]): Bounds {
        // Calculate bounding box of all children
        const children = childIds.map(id => this.getNode(id)).filter(Boolean);

        if (children.length === 0) {
            return { x: 0, y: 0, width: 0, height: 0, center: { x: 0, y: 0 } };
        }

        let minX = Infinity;
        let minY = Infinity;
        let maxX = -Infinity;
        let maxY = -Infinity;

        for (const child of children) {
            const bounds = this.getNodeBounds(child);
            minX = Math.min(minX, bounds.x);
            minY = Math.min(minY, bounds.y);
            maxX = Math.max(maxX, bounds.x + bounds.width);
            maxY = Math.max(maxY, bounds.y + bounds.height);
        }

        return {
            x: minX,
            y: minY,
            width: maxX - minX,
            height: maxY - minY,
            center: { x: (minX + maxX) / 2, y: (minY + maxY) / 2 }
        };
    }

    private getNodeBounds(node: unknown): Bounds {
        return {
            x: node.position?.x || 0,
            y: node.position?.y || 0,
            width: node.size?.width || 100,
            height: node.size?.height || 100,
            center: {
                x: (node.position?.x || 0) + (node.size?.width || 100) / 2,
                y: (node.position?.y || 0) + (node.size?.height || 100) / 2
            }
        };
    }

    private panTo(center: { x: number; y: number }, duration: number): void {
        // Animate pan to center
        this.updateViewport({ x: center.x, y: center.y });
    }

    private onZoomComplete(): void {
        // Save to history
        this.pushHistory({
            x: 0, // Would need current viewport position
            y: 0,
            zoom: this.currentZoom
        });
    }

    private pushHistory(state: ViewportState): void {
        // Truncate forward history if we're not at the end
        if (this.currentHistoryIndex < this.zoomHistory.length - 1) {
            this.zoomHistory = this.zoomHistory.slice(0, this.currentHistoryIndex + 1);
        }

        this.zoomHistory.push(state);
        this.currentHistoryIndex = this.zoomHistory.length - 1;

        // Limit history size
        if (this.zoomHistory.length > 50) {
            this.zoomHistory.shift();
            this.currentHistoryIndex--;
        }
    }

    private async restoreViewport(state: ViewportState): Promise<void> {
        await this.zoomTo(state.zoom);
        this.updateViewport({ x: state.x, y: state.y });
    }

    private notifyZoomChange(zoom: number): void {
        for (const callback of this.onZoomChangeCallbacks) {
            callback(zoom);
        }
    }

    private notifyViewportChange(viewport: ViewportState): void {
        for (const callback of this.onViewportChangeCallbacks) {
            callback(viewport);
        }
    }
}
