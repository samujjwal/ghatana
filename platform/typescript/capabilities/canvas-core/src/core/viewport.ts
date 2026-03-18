import { Point, CanvasOptions } from "../types/index.js";
import { CanvasElement } from "../elements/base.js";
import { Bound } from "../utils/bounds.js";
import { BehaviorSubject } from "rxjs";

export enum ZoomLevel {
  BIRD_EYE = "bird_eye", // < 0.3x: Blocks only, no text
  OVERVIEW = "overview", // 0.3x - 0.7x: Headers visible
  DETAILED = "detailed", // 0.7x - 2.0x: Full content
  MICROSCOPIC = "microscopic" // > 2.0x: Debug/Pixel data
}

export class Viewport {
  public zoom: number = 1;
  public centerX: number = 0;
  public centerY: number = 0;
  public width: number;
  public height: number;

  // Observable for zoom changes (AFFiNE parity)
  public zoom$: BehaviorSubject<number>;
  public semanticLevel$: BehaviorSubject<ZoomLevel>;

  constructor(options: CanvasOptions) {
    this.width = options.width;
    this.height = options.height;
    // Default the camera to origin so positive coordinates are visible in the top-left
    // Historically the renderer used the canvas center as the camera origin
    // (see dist/core/viewport.js). Use the center by default so existing
    // element coordinates (which assume a centered camera) align with clicks.
    this.centerX = options.width / 2;
    this.centerY = options.height / 2;

    this.zoom$ = new BehaviorSubject(this.zoom);
    this.semanticLevel$ = new BehaviorSubject(this.getSemanticLevel());
  }

  setZoom(zoom: number, focusX?: number, focusY?: number): void {
    const oldLevel = this.getSemanticLevel();
    const oldZoom = this.zoom;

    // Expand range for hierarchical nav
    this.zoom = Math.max(0.05, Math.min(20, zoom));

    // If focus point is provided, zoom centered on that point
    // Formula: C' = P - (P - C) * (z / z')
    if (focusX !== undefined && focusY !== undefined) {
      const zoomRatio = oldZoom / this.zoom;
      this.centerX = focusX - (focusX - this.centerX) * zoomRatio;
      this.centerY = focusY - (focusY - this.centerY) * zoomRatio;
    }

    this.zoom$.next(this.zoom);

    const newLevel = this.getSemanticLevel();
    if (oldLevel !== newLevel) {
      this.semanticLevel$.next(newLevel);
    }
  }

  getSemanticLevel(): ZoomLevel {
    if (this.zoom < 0.3) return ZoomLevel.BIRD_EYE;
    if (this.zoom < 0.7) return ZoomLevel.OVERVIEW;
    if (this.zoom < 2.0) return ZoomLevel.DETAILED;
    return ZoomLevel.MICROSCOPIC;
  }

  pan(deltaX: number, deltaY: number): void {
    this.centerX -= deltaX / this.zoom;
    this.centerY -= deltaY / this.zoom;
  }

  screenToCanvas(screenPoint: Point): Point {
    return {
      x: (screenPoint.x - this.width / 2) / this.zoom + this.centerX,
      y: (screenPoint.y - this.height / 2) / this.zoom + this.centerY,
    };
  }

  canvasToScreen(canvasPoint: Point): Point {
    return {
      x: (canvasPoint.x - this.centerX) * this.zoom + this.width / 2,
      y: (canvasPoint.y - this.centerY) * this.zoom + this.height / 2,
    };
  }

  getVisibleBounds(): Bound {
    const topLeft = this.screenToCanvas({ x: 0, y: 0 });
    const bottomRight = this.screenToCanvas({ x: this.width, y: this.height });

    return Bound.fromXYWH(
      topLeft.x,
      topLeft.y,
      bottomRight.x - topLeft.x,
      bottomRight.y - topLeft.y,
    );
  }

  isElementVisible(element: CanvasElement): boolean {
    const elementBounds = element.getBounds();
    const visibleBounds = this.getVisibleBounds();

    return elementBounds.intersects(visibleBounds);
  }

  reset(): void {
    this.zoom = 1;
    this.centerX = 0;
    this.centerY = 0;
  }
}
