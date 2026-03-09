/**
 * Layer Service - Manage canvas layers and grouping
 */

import type { CanvasElement } from '../../components/canvas/workspace/canvasAtoms';
import type { Layer, GroupDefinition } from '../export/types';

/**
 *
 */
class LayerServiceClass {
  private layers: Map<string, Layer> = new Map();
  private groups: Map<string, GroupDefinition> = new Map();

  /**
   * Create a new layer
   */
  createLayer(name: string, elementIds: string[] = []): Layer {
    const layer: Layer = {
      id: `layer-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
      name,
      visible: true,
      locked: false,
      opacity: 1,
      zIndex: this.layers.size,
      elementIds,
    };

    this.layers.set(layer.id, layer);
    return layer;
  }

  /**
   * Get layer by ID
   */
  getLayer(id: string): Layer | null {
    return this.layers.get(id) || null;
  }

  /**
   * List all layers
   */
  listLayers(): Layer[] {
    return Array.from(this.layers.values()).sort((a, b) => a.zIndex - b.zIndex);
  }

  /**
   * Update layer
   */
  updateLayer(id: string, updates: Partial<Omit<Layer, 'id'>>): boolean {
    const layer = this.layers.get(id);
    if (!layer) return false;

    this.layers.set(id, { ...layer, ...updates });
    return true;
  }

  /**
   * Delete layer
   */
  deleteLayer(id: string): boolean {
    return this.layers.delete(id);
  }

  /**
   * Toggle layer visibility
   */
  toggleVisibility(id: string): boolean {
    const layer = this.layers.get(id);
    if (!layer) return false;

    layer.visible = !layer.visible;
    return true;
  }

  /**
   * Toggle layer lock
   */
  toggleLock(id: string): boolean {
    const layer = this.layers.get(id);
    if (!layer) return false;

    layer.locked = !layer.locked;
    return true;
  }

  /**
   * Reorder layers
   */
  reorderLayers(layerIds: string[]): void {
    layerIds.forEach((id, index) => {
      const layer = this.layers.get(id);
      if (layer) {
        layer.zIndex = index;
      }
    });
  }

  /**
   * Add elements to layer
   */
  addToLayer(layerId: string, elementIds: string[]): boolean {
    const layer = this.layers.get(layerId);
    if (!layer) return false;

    layer.elementIds = [...new Set([...layer.elementIds, ...elementIds])];
    return true;
  }

  /**
   * Remove elements from layer
   */
  removeFromLayer(layerId: string, elementIds: string[]): boolean {
    const layer = this.layers.get(layerId);
    if (!layer) return false;

    const toRemove = new Set(elementIds);
    layer.elementIds = layer.elementIds.filter((id) => !toRemove.has(id));
    return true;
  }

  /**
   * Create a group
   */
  createGroup(name: string, elementIds: string[]): GroupDefinition {
    const group: GroupDefinition = {
      id: `group-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
      name,
      elementIds,
      locked: false,
      collapsed: false,
    };

    this.groups.set(group.id, group);
    return group;
  }

  /**
   * Get group by ID
   */
  getGroup(id: string): GroupDefinition | null {
    return this.groups.get(id) || null;
  }

  /**
   * List all groups
   */
  listGroups(): GroupDefinition[] {
    return Array.from(this.groups.values());
  }

  /**
   * Ungroup
   */
  ungroup(id: string): string[] | null {
    const group = this.groups.get(id);
    if (!group) return null;

    const elementIds = [...group.elementIds];
    this.groups.delete(id);
    return elementIds;
  }

  /**
   * Add elements to group
   */
  addToGroup(groupId: string, elementIds: string[]): boolean {
    const group = this.groups.get(groupId);
    if (!group) return false;

    group.elementIds = [...new Set([...group.elementIds, ...elementIds])];
    return true;
  }

  /**
   * Remove elements from group
   */
  removeFromGroup(groupId: string, elementIds: string[]): boolean {
    const group = this.groups.get(groupId);
    if (!group) return false;

    const toRemove = new Set(elementIds);
    group.elementIds = group.elementIds.filter((id) => !toRemove.has(id));
    return true;
  }

  /**
   * Check if element is in a group
   */
  getElementGroup(elementId: string): GroupDefinition | null {
    for (const group of this.groups.values()) {
      if (group.elementIds.includes(elementId)) {
        return group;
      }
    }
    return null;
  }

  /**
   * Filter elements by layer visibility
   */
  filterByVisibility(elements: CanvasElement[]): CanvasElement[] {
    const visibleLayers = new Set(
      Array.from(this.layers.values())
        .filter((layer) => layer.visible)
        .flatMap((layer) => layer.elementIds),
    );

    // If no layers defined, show all
    if (this.layers.size === 0) {
      return elements;
    }

    return elements.filter((el) => visibleLayers.has(el.id));
  }

  /**
   * Clear all layers and groups
   */
  clear(): void {
    this.layers.clear();
    this.groups.clear();
  }
}

// Singleton instance
export const LayerService = new LayerServiceClass();
