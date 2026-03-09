import { CanvasElement } from "../elements/base.js";

export class LayerManager {
  private layers: Map<string, CanvasElement[]> = new Map();
  private layerOrder: string[] = [];
  private defaultLayer = "default";
  private _lastElementCount = -1;

  constructor() {
    this.addLayer(this.defaultLayer);
  }

  addLayer(id: string): void {
    if (!this.layers.has(id)) {
      this.layers.set(id, []);
      this.layerOrder.push(id);
    }
  }

  removeLayer(id: string): void {
    if (id !== this.defaultLayer) {
      this.layers.delete(id);
      this.layerOrder = this.layerOrder.filter((layerId) => layerId !== id);
    }
  }

  addElement(
    element: CanvasElement,
    layerId: string = this.defaultLayer,
  ): void {
    if (!this.layers.has(layerId)) {
      this.addLayer(layerId);
    }

    const layer = this.layers.get(layerId)!;
    layer.push(element);

    // Sort by index
    layer.sort((a, b) => a.index.localeCompare(b.index));
  }

  removeElement(element: CanvasElement): void {
    for (const layer of this.layers.values()) {
      const index = layer.indexOf(element);
      if (index !== -1) {
        layer.splice(index, 1);
        break;
      }
    }
  }

  moveElement(
    element: CanvasElement,
    fromLayer: string,
    toLayer: string,
  ): void {
    this.removeElement(element);
    this.addElement(element, toLayer);
  }

  getElements(layerId: string): CanvasElement[] {
    const layer = this.layers.get(layerId);
    if (!layer) {
      return [];
    }

    return layer;
  }

  getVisibleElements(viewportBounds: {
    x: number;
    y: number;
    w: number;
    h: number;
  }): CanvasElement[] {
    const visibleElements: CanvasElement[] = [];

    for (const layerId of this.layerOrder) {
      const layer = this.layers.get(layerId);
      if (layer) {
        for (const element of layer) {
          const elementBounds = element.getBounds();
          if (this.boundsIntersect(elementBounds, viewportBounds)) {
            visibleElements.push(element);
          }
        }
      }
    }

    return visibleElements;
  }

  private boundsIntersect(
    bound1: { x: number; y: number; w: number; h: number },
    bound2: { x: number; y: number; w: number; h: number },
  ): boolean {
    return !(
      bound1.x + bound1.w < bound2.x ||
      bound2.x + bound2.w < bound1.x ||
      bound1.y + bound1.h < bound2.y ||
      bound2.y + bound2.h < bound1.y
    );
  }

  setLayerOrder(order: string[]): void {
    // Validate all layers exist
    const validOrder = order.filter((id) => this.layers.has(id));

    // Add any missing layers to the end
    for (const layerId of this.layerOrder) {
      if (!validOrder.includes(layerId)) {
        validOrder.push(layerId);
      }
    }

    this.layerOrder = validOrder;
  }

  getLayerOrder(): string[] {
    return [...this.layerOrder];
  }

  clear(): void {
    this.layers.clear();
    this.layerOrder = [];
    this.addLayer(this.defaultLayer);
  }
}
