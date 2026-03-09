import { Point, CanvasOptions } from "../types/index.js";
import { CanvasElement } from "../elements/base.js";
import { Viewport } from "./viewport.js";
import { LayerManager } from "./layer-manager.js";
import { ToolManager } from "../tools/index.js";

export interface CanvasEvents {
  elementAdd: (element: CanvasElement) => void;
  elementRemove: (element: CanvasElement) => void;
  elementSelect: (element: CanvasElement) => void;
  elementUpdate: (element: CanvasElement) => void;
  elementClick: (element: CanvasElement) => void;
  viewportChange: (viewport: Viewport) => void;
  // AI Observer Events
  aiIntentDetected: (intent: { type: string; confidence: number; context: Record<string, unknown> }) => void;
}

/**
 * AI Observation Layer
 * Tracks implicit user actions to feed the Agent Learning System
 */
class AIObservationLayer {
  constructor(private render: CanvasRenderer) { }

  public trackInteraction(type: string, target: CanvasElement | Record<string, unknown> | null) {
    // Debounced simplified emission of semantic intent
    // In a real implementation this would analyze the graph structure
    this.render.emit('aiIntentDetected', {
      type,
      confidence: 0.9,
      context: {
        targetId: target?.id,
        timestamp: Date.now()
      }
    });
  }
}

export class CanvasRenderer {
  protected canvas!: HTMLCanvasElement;
  protected ctx!: CanvasRenderingContext2D;
  protected container: HTMLElement;
  public viewport: Viewport;
  private layerManager: LayerManager;
  private toolManager: ToolManager;
  private aiLayer: AIObservationLayer; // Native AI
  private elements: CanvasElement[] = [];
  private selectedElements: CanvasElement[] = [];
  // eslint-disable-next-line @typescript-eslint/no-unsafe-function-type
  private eventListeners: Map<keyof CanvasEvents, Array<Function>> = new Map();
  private isRendering = false;
  private animationId: number | null = null;

  constructor(container: HTMLElement, options: CanvasOptions) {
    this.container = container;
    this.viewport = new Viewport(options);
    this.layerManager = new LayerManager();
    this.toolManager = new ToolManager();

    // Initialize AI Layer
    this.aiLayer = new AIObservationLayer(this);

    this.setupCanvas();
    this.bindEvents();
    this.startRenderLoop();
  }

  public on<K extends keyof CanvasEvents>(event: K, handler: CanvasEvents[K]): void {
    if (!this.eventListeners.has(event)) {
      this.eventListeners.set(event, []);
    }
    this.eventListeners.get(event)!.push(handler);
  }

  public off<K extends keyof CanvasEvents>(event: K, handler: CanvasEvents[K]): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      const index = listeners.indexOf(handler);
      if (index !== -1) {
        listeners.splice(index, 1);
      }
    }
  }

  private setupCanvas(): void {
    this.canvas = document.createElement("canvas");
    this.canvas.width = this.viewport.width;
    this.canvas.height = this.viewport.height;

    this.ctx = this.canvas.getContext("2d")!;
    if (!this.ctx) {
      throw new Error("Failed to get 2D context");
    }

    // Set canvas styles
    this.canvas.style.width = "100%";
    this.canvas.style.height = "100%";
    this.canvas.style.display = "block";

    this.container.appendChild(this.canvas);
    // Expose viewport on the DOM element so tools can map screen->canvas
    (this.canvas as unknown as Record<string, unknown>).__viewport = this.viewport;
  }

  private bindEvents(): void {
    // Mouse events
    this.canvas.addEventListener("mousedown", this.handleMouseDown.bind(this));
    this.canvas.addEventListener("mousemove", this.handleMouseMove.bind(this));
    this.canvas.addEventListener("mouseup", this.handleMouseUp.bind(this));
    this.canvas.addEventListener("wheel", this.handleWheel.bind(this));

    // Touch events
    this.canvas.addEventListener(
      "touchstart",
      this.handleTouchStart.bind(this),
    );
    this.canvas.addEventListener("touchmove", this.handleTouchMove.bind(this));
    this.canvas.addEventListener("touchend", this.handleTouchEnd.bind(this));

    // Window resize
    window.addEventListener("resize", this.handleResize.bind(this));

    // Custom events for tools
    this.canvas.addEventListener(
      "element-create",
      this.handleElementCreate.bind(this),
    );
    this.canvas.addEventListener(
      "element-update",
      this.handleElementUpdate.bind(this),
    );
    this.canvas.addEventListener(
      "element-finalize",
      this.handleElementFinalize.bind(this),
    );
    this.canvas.addEventListener(
      "canvas-select",
      this.handleCanvasSelect.bind(this),
    );
    this.canvas.addEventListener("canvas-pan", this.handleCanvasPan.bind(this));
    this.canvas.addEventListener(
      "canvas-element-drag",
      this.handleElementDrag.bind(this),
    );
    this.canvas.addEventListener(
      "canvas-element-drag-end",
      this.handleElementDragEnd.bind(this),
    );
  }

  private startRenderLoop(): void {
    const render = () => {
      this.render();
      this.animationId = requestAnimationFrame(render);
    };
    render();
  }

  public render(): void {
    if (this.isRendering) return;
    this.isRendering = true;

    try {
      // Clear canvas
      this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);

      // Save context state
      this.ctx.save();

      // Apply viewport transformation: move origin to screen center, then pan/zoom
      this.ctx.translate(this.canvas.width / 2, this.canvas.height / 2);
      this.ctx.scale(this.viewport.zoom, this.viewport.zoom);
      this.ctx.translate(-this.viewport.centerX, -this.viewport.centerY);

      // Draw grid (optional)
      this.drawGrid();

      // Get visible elements
      const visibleBounds = this.viewport.getVisibleBounds();
      const visibleElements =
        this.layerManager.getVisibleElements(visibleBounds);

      // Render elements
      for (const element of visibleElements) {
        this.renderElement(element);
      }

      // Restore context state
      this.ctx.restore();
    } finally {
      this.isRendering = false;
    }
  }

  private renderElement(element: CanvasElement): void {
    try {
      element.render(this.ctx, this.viewport.zoom);

      // Draw selection outline if selected
      if (element.selected) {
        this.drawSelectionOutline(element);
      }
    } catch (error) {
      console.error("Error rendering element:", error);
    }
  }

  private drawSelectionOutline(element: CanvasElement): void {
    const bound = element.getBounds();

    this.ctx.save();
    this.ctx.strokeStyle = "#0066cc";
    this.ctx.lineWidth = 2 / this.viewport.zoom; // Consistent width regardless of zoom
    this.ctx.setLineDash([5 / this.viewport.zoom, 5 / this.viewport.zoom]);

    this.ctx.strokeRect(bound.x, bound.y, bound.w, bound.h);

    // Draw resize handles
    this.drawResizeHandles(bound);

    this.ctx.restore();
  }

  private drawResizeHandles(bound: {
    x: number;
    y: number;
    w: number;
    h: number;
  }): void {
    const handleSize = 8 / this.viewport.zoom;
    const handles = [
      { x: bound.x, y: bound.y }, // Top-left
      { x: bound.x + bound.w, y: bound.y }, // Top-right
      { x: bound.x, y: bound.y + bound.h }, // Bottom-left
      { x: bound.x + bound.w, y: bound.y + bound.h }, // Bottom-right
      { x: bound.x + bound.w / 2, y: bound.y }, // Top-middle
      { x: bound.x + bound.w / 2, y: bound.y + bound.h }, // Bottom-middle
      { x: bound.x, y: bound.y + bound.h / 2 }, // Left-middle
      { x: bound.x + bound.w, y: bound.y + bound.h / 2 }, // Right-middle
    ];

    this.ctx.fillStyle = "#ffffff";
    this.ctx.strokeStyle = "#0066cc";
    this.ctx.lineWidth = 1 / this.viewport.zoom;

    for (const handle of handles) {
      this.ctx.fillRect(
        handle.x - handleSize / 2,
        handle.y - handleSize / 2,
        handleSize,
        handleSize,
      );
      this.ctx.strokeRect(
        handle.x - handleSize / 2,
        handle.y - handleSize / 2,
        handleSize,
        handleSize,
      );
    }
  }

  private drawGrid(): void {
    const gridSize = 20;
    const visibleBounds = this.viewport.getVisibleBounds();

    this.ctx.save();
    this.ctx.strokeStyle = "#e0e0e0";
    this.ctx.lineWidth = 0.5;

    // Calculate grid boundaries
    const startX = Math.floor(visibleBounds.x / gridSize) * gridSize;
    const startY = Math.floor(visibleBounds.y / gridSize) * gridSize;
    const endX = visibleBounds.x + visibleBounds.w;
    const endY = visibleBounds.y + visibleBounds.h;

    // Draw vertical lines
    for (let x = startX; x <= endX; x += gridSize) {
      this.ctx.beginPath();
      this.ctx.moveTo(x, startY);
      this.ctx.lineTo(x, endY);
      this.ctx.stroke();
    }

    // Draw horizontal lines
    for (let y = startY; y <= endY; y += gridSize) {
      this.ctx.beginPath();
      this.ctx.moveTo(startX, y);
      this.ctx.lineTo(endX, y);
      this.ctx.stroke();
    }

    this.ctx.restore();
  }

  private handleMouseDown(event: MouseEvent): void {
    const point = this.getMousePosition(event);
    const canvasPoint = this.viewport.screenToCanvas(point);

    // Debug
    // eslint-disable-next-line no-console
    console.debug('[CanvasRenderer] mouseDown at', point, '-> canvas', canvasPoint);

    // AI Observation
    this.aiLayer.trackInteraction('interaction_start', { point: canvasPoint });

    this.toolManager.handlePointerDown(event as PointerEvent, this.canvas);
  }

  private handleMouseMove(event: MouseEvent): void {
    // Only track if interacting to avoid noise
    if (event.buttons > 0) {
      this.aiLayer.trackInteraction('interaction_move', { time: Date.now() });
    }
    this.toolManager.handlePointerMove(event as PointerEvent, this.canvas);
  }

  private handleMouseUp(event: MouseEvent): void {
    const point = this.getMousePosition(event);
    const canvasPoint = this.viewport.screenToCanvas(point);

    // AI Observation
    this.aiLayer.trackInteraction('interaction_end', { point: canvasPoint });

    this.toolManager.handlePointerUp(event as PointerEvent, this.canvas);
  }

  private handleElementCreate(event: Event): void {
    const customEvent = event as CustomEvent;
    const element = customEvent.detail.element;
    // Debug
    // eslint-disable-next-line no-console
    console.debug('[CanvasRenderer] element-create', element?.id, element?.xywh);

    this.addElement(element);
  }

  private handleElementUpdate(event: Event): void {
    const customEvent = event as CustomEvent;
    const element = customEvent.detail.element;
    this.render();
  }

  private handleElementFinalize(event: Event): void {
    const customEvent = event as CustomEvent;
    const element = customEvent.detail.element;
    this.emit("elementAdd", element);
  }

  private handleCanvasSelect(event: Event): void {
    const customEvent = event as CustomEvent;
    const point = customEvent.detail.point;

    // Tools dispatch canvas-space coordinates; accept point as-is.
    const canvasPoint = point;

    const clickedElement = this.getElementAtPoint(canvasPoint);
    // Debug
    // eslint-disable-next-line no-console
    console.debug('[CanvasRenderer] canvas-select at', canvasPoint, 'clickedElement=', clickedElement?.id);

    // Dump visible layer elements for debugging
    // eslint-disable-next-line no-console
    const layerElements = this.layerManager.getElements('default');
    // eslint-disable-next-line no-console
    console.debug('[CanvasRenderer] layerElements count=', layerElements.length);
    for (const el of layerElements) {
      try {
        // eslint-disable-next-line no-console
        console.debug('[CanvasRenderer] element', el.id, el.getBounds());
      } catch (e) {
        // eslint-disable-next-line no-console
        console.debug('[CanvasRenderer] element', el.id, 'getBounds error', e);
      }
    }

    if (clickedElement) {
      this.selectElement(clickedElement);
      // Set the dragged element in the select tool
      const selectTool = this.toolManager.getActiveTool() as unknown as Record<string, unknown>;
      if (selectTool && typeof selectTool.setDraggedElement === 'function') {
        const elementPos = this.getElementPosition(clickedElement);
        (selectTool as unknown as { setDraggedElement: (el: CanvasElement, pos: unknown) => void }).setDraggedElement(clickedElement, elementPos);
      }
    } else {
      this.clearSelection();
      // Clear dragged element
      const selectTool = this.toolManager.getActiveTool() as unknown as Record<string, unknown>;
      if (selectTool && typeof selectTool.setDraggedElement === 'function') {
        (selectTool as unknown as { setDraggedElement: (el: null, pos: null) => void }).setDraggedElement(null, null);
      }
    }
  }

  private handleCanvasPan(event: Event): void {
    const customEvent = event as CustomEvent;
    const { deltaX, deltaY } = customEvent.detail;
    this.viewport.pan(deltaX, deltaY);
    this.emit("viewportChange", this.viewport);
  }

  private handleElementDrag(event: Event): void {
    const customEvent = event as CustomEvent;
    const { element, deltaX, deltaY, startPos } = customEvent.detail;

    // Deltas are provided in canvas coordinates by tools (screen->canvas conversion done in tools).
    // Treat them as canvas deltas directly.
    const dx = deltaX;
    const dy = deltaY;

    const xywh = JSON.parse(element.xywh);
    const newX = (startPos?.x ?? xywh[0]) + dx;
    const newY = (startPos?.y ?? xywh[1]) + dy;
    const newW = xywh[2];
    const newH = xywh[3];

    element.xywh = JSON.stringify([newX, newY, newW, newH]);
    this.render();
  }

  private handleElementDragEnd(event: Event): void {
    const customEvent = event as CustomEvent;
    const { element } = customEvent.detail;
    if (!element) return;

    const gridSize = 20; // Align with grid rendering
    const snap = (value: number) => Math.round(value / gridSize) * gridSize;

    const xywh = JSON.parse(element.xywh);
    const snappedX = snap(xywh[0]);
    const snappedY = snap(xywh[1]);

    element.xywh = JSON.stringify([snappedX, snappedY, xywh[2], xywh[3]]);
    this.render();
  }

  private handleTouchStart(_event: TouchEvent): void {
    // Handle touch start
  }

  private handleTouchMove(_event: TouchEvent): void {
    // Handle touch move
  }

  private handleTouchEnd(_event: TouchEvent): void {
    // Handle touch end
  }

  private handleWheel(event: WheelEvent): void {
    event.preventDefault();

    // Check for zoom (Ctrl or Cmd key)
    if (event.ctrlKey || event.metaKey) {
      const point = this.getMousePosition(event);
      const canvasPoint = this.viewport.screenToCanvas(point);

      // Calculate zoom delta (slower zoom for better control)
      const zoomFactor = 1.05;
      const zoomDelta = event.deltaY > 0 ? 1 / zoomFactor : zoomFactor;
      const newZoom = this.viewport.zoom * zoomDelta;

      // Apply zoom centered on mouse position
      this.viewport.setZoom(newZoom, canvasPoint.x, canvasPoint.y);
    } else {
      // Standard panning
      this.viewport.pan(event.deltaX, event.deltaY);
    }

    this.emit("viewportChange", this.viewport);
    this.render();
  }

  private handleResize(): void {
    // Update canvas size
    const rect = this.container.getBoundingClientRect();
    this.canvas.width = rect.width;
    this.canvas.height = rect.height;

    this.viewport.width = rect.width;
    this.viewport.height = rect.height;

    this.render();
  }

  private getMousePosition(event: MouseEvent): Point {
    const rect = this.canvas.getBoundingClientRect();
    return {
      x: event.clientX - rect.left,
      y: event.clientY - rect.top,
    };
  }

  private getElementAtPoint(point: Point): CanvasElement | null {
    const elements = this.layerManager.getElements("default");

    // Check elements in reverse order (top to bottom)
    for (let i = elements.length - 1; i >= 0; i--) {
      const element = elements[i];
      if (element.includesPoint(point.x, point.y)) {
        return element;
      }
    }

    return null;
  }

  private getElementPosition(element: CanvasElement): Point {
    const xywh = JSON.parse(element.xywh);
    return {
      x: xywh[0],
      y: xywh[1]
    };
  }

  // Public API
  public addElement(element: CanvasElement): void {
    this.elements.push(element);
    this.layerManager.addElement(element);
    this.emit("elementAdd", element);
    this.render();
  }

  public removeElement(element: CanvasElement): void {
    const index = this.elements.indexOf(element);
    if (index !== -1) {
      this.elements.splice(index, 1);
      this.layerManager.removeElement(element);
      this.emit("elementRemove", element);
      this.render();
    }
  }

  public selectElement(element: CanvasElement): void {
    this.clearSelection();
    element.selected = true;
    this.selectedElements.push(element);
    this.emit("elementSelect", element);
    this.render();
  }

  public clearSelection(): void {
    for (const element of this.selectedElements) {
      element.selected = false;
    }
    this.selectedElements = [];
    this.render();
  }

  public getElements(): CanvasElement[] {
    return [...this.elements];
  }

  public getSelectedElements(): CanvasElement[] {
    return [...this.selectedElements];
  }

  public getViewport(): Viewport {
    return this.viewport;
  }

  // Tool manager proxy helpers
  public setActiveTool(name: string): void {
    this.toolManager.setActiveTool(name);
  }

  public getActiveToolName(): string {
    return this.toolManager.getActiveToolName();
  }

  public getToolNames(): string[] {
    return this.toolManager.getToolNames();
  }

  public emit<K extends keyof CanvasEvents>(
    event: K,
    ...args: Parameters<CanvasEvents[K]>
  ): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      for (const callback of listeners) {
        callback(...args);
      }
    }
  }

  public dispose(): void {
    if (this.animationId) {
      cancelAnimationFrame(this.animationId);
    }

    // Remove event listeners
    this.canvas.removeEventListener("mousedown", this.handleMouseDown);
    this.canvas.removeEventListener("mousemove", this.handleMouseMove);
    this.canvas.removeEventListener("mouseup", this.handleMouseUp);
    this.canvas.removeEventListener("wheel", this.handleWheel);

    window.removeEventListener("resize", this.handleResize);

    // Remove canvas from DOM
    if (this.container.contains(this.canvas)) {
      this.container.removeChild(this.canvas);
    }

    // Clear references
    this.eventListeners.clear();
    this.elements = [];
    this.selectedElements = [];
  }
}


