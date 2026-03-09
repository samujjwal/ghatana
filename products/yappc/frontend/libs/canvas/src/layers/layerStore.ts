/**
 * Layer Management System
 *
 * Provides hierarchical layer organization with:
 * - Layer CRUD operations (create, read, update, delete)
 * - Z-index management and reordering
 * - Visibility toggles and locking
 * - Tag-based element organization
 * - Saved viewpoint bookmarks
 * - Permission-based layer access control
 *
 * @module layers/layerStore
 */

/**
 *
 */
export interface Layer {
  /** Unique layer identifier */
  id: string;
  /** Display name */
  name: string;
  /** Z-index for stacking order (higher = on top) */
  zIndex: number;
  /** Visibility state */
  visible: boolean;
  /** Lock state (prevents editing) */
  locked: boolean;
  /** Optional opacity (0-1) */
  opacity?: number;
  /** Element IDs assigned to this layer */
  elementIds: string[];
  /** Optional parent layer for hierarchical organization */
  parentId?: string;
  /** Creation timestamp */
  createdAt: number;
  /** Last modified timestamp */
  updatedAt: number;
  /** Optional color for visual distinction */
  color?: string;
  /** Optional description */
  description?: string;
}

/**
 *
 */
export interface Tag {
  /** Unique tag identifier */
  id: string;
  /** Tag label */
  label: string;
  /** Tag color */
  color: string;
  /** Element IDs with this tag */
  elementIds: string[];
  /** Creation timestamp */
  createdAt: number;
}

/**
 *
 */
export interface Viewpoint {
  /** Unique viewpoint identifier */
  id: string;
  /** Viewpoint name */
  name: string;
  /** Viewport position */
  viewport: {
    x: number;
    y: number;
    zoom: number;
  };
  /** Visible layer IDs */
  visibleLayers: string[];
  /** Selected element IDs */
  selectedElements?: string[];
  /** Optional description */
  description?: string;
  /** Creation timestamp */
  createdAt: number;
}

/**
 *
 */
export type Permission = 'none' | 'view' | 'edit' | 'admin';

/**
 *
 */
export interface LayerPermission {
  /** Layer ID */
  layerId: string;
  /** User or role ID */
  principalId: string;
  /** Permission level: 'none' | 'view' | 'edit' | 'admin' */
  permission: Permission;
}

/**
 *
 */
export interface LayerStore {
  /** All layers by ID */
  layers: Map<string, Layer>;
  /** All tags by ID */
  tags: Map<string, Tag>;
  /** All viewpoints by ID */
  viewpoints: Map<string, Viewpoint>;
  /** Layer permissions */
  permissions: Map<string, LayerPermission>;
  /** Current active layer ID */
  activeLayerId: string | null;
  /** Layer order (for rendering) */
  layerOrder: string[];
}

/**
 * Create a new layer store
 */
export function createLayerStore(): LayerStore {
  return {
    layers: new Map(),
    tags: new Map(),
    viewpoints: new Map(),
    permissions: new Map(),
    activeLayerId: null,
    layerOrder: [],
  };
}

/**
 * Create a new layer
 */
export function createLayer(
  store: LayerStore,
  options: {
    name: string;
    parentId?: string;
    color?: string;
    description?: string;
  }
): Layer {
  const now = Date.now();
  const id = `layer-${now}-${Math.random().toString(36).substr(2, 9)}`;
  
  // Calculate z-index (max + 1, or 0 for first layer)
  const maxZIndex = Array.from(store.layers.values()).reduce(
    (max, layer) => Math.max(max, layer.zIndex),
    -1
  );

  const layer: Layer = {
    id,
    name: options.name,
    zIndex: maxZIndex + 1,
    visible: true,
    locked: false,
    opacity: 1,
    elementIds: [],
    parentId: options.parentId,
    createdAt: now,
    updatedAt: now,
    color: options.color,
    description: options.description,
  };

  store.layers.set(id, layer);
  store.layerOrder.push(id);

  // Set as active if first layer
  if (store.layers.size === 1) {
    store.activeLayerId = id;
  }

  return layer;
}

/**
 * Get layer by ID
 */
export function getLayer(store: LayerStore, layerId: string): Layer | undefined {
  return store.layers.get(layerId);
}

/**
 * Get all layers
 */
export function getAllLayers(store: LayerStore): Layer[] {
  return Array.from(store.layers.values());
}

/**
 * Get layers in render order (sorted by z-index)
 */
export function getLayersInRenderOrder(store: LayerStore): Layer[] {
  return Array.from(store.layers.values()).sort((a, b) => a.zIndex - b.zIndex);
}

/**
 * Update layer properties
 */
export function updateLayer(
  store: LayerStore,
  layerId: string,
  updates: Partial<Omit<Layer, 'id' | 'createdAt' | 'updatedAt'>>
): Layer | null {
  const layer = store.layers.get(layerId);
  if (!layer) return null;

  const updated: Layer = {
    ...layer,
    ...updates,
    updatedAt: Date.now(),
  };

  store.layers.set(layerId, updated);
  return updated;
}

/**
 * Delete layer
 */
export function deleteLayer(store: LayerStore, layerId: string): boolean {
  const layer = store.layers.get(layerId);
  if (!layer) return false;

  // Remove from store
  store.layers.delete(layerId);
  store.layerOrder = store.layerOrder.filter((id) => id !== layerId);

  // Update active layer if needed
  if (store.activeLayerId === layerId) {
    store.activeLayerId = store.layerOrder.length > 0 ? store.layerOrder[0] : null;
  }

  // Move orphaned elements to default layer or remove from layers
  const defaultLayer = store.layerOrder.length > 0 ? store.layers.get(store.layerOrder[0]) : null;
  if (defaultLayer) {
    defaultLayer.elementIds.push(...layer.elementIds);
  }

  return true;
}

/**
 * Reorder layers
 */
export function reorderLayers(
  store: LayerStore,
  layerId: string,
  newIndex: number
): boolean {
  const currentIndex = store.layerOrder.indexOf(layerId);
  if (currentIndex === -1) return false;

  // Remove from current position
  store.layerOrder.splice(currentIndex, 1);

  // Insert at new position
  store.layerOrder.splice(newIndex, 0, layerId);

  // Update z-indices to match new order
  store.layerOrder.forEach((id, index) => {
    const layer = store.layers.get(id);
    if (layer) {
      layer.zIndex = index;
      layer.updatedAt = Date.now();
    }
  });

  return true;
}

/**
 * Move layer up in stack
 */
export function moveLayerUp(store: LayerStore, layerId: string): boolean {
  const currentIndex = store.layerOrder.indexOf(layerId);
  if (currentIndex === -1 || currentIndex === store.layerOrder.length - 1) {
    return false;
  }

  return reorderLayers(store, layerId, currentIndex + 1);
}

/**
 * Move layer down in stack
 */
export function moveLayerDown(store: LayerStore, layerId: string): boolean {
  const currentIndex = store.layerOrder.indexOf(layerId);
  if (currentIndex === -1 || currentIndex === 0) {
    return false;
  }

  return reorderLayers(store, layerId, currentIndex - 1);
}

/**
 * Move layer to top
 */
export function moveLayerToTop(store: LayerStore, layerId: string): boolean {
  const currentIndex = store.layerOrder.indexOf(layerId);
  if (currentIndex === -1 || currentIndex === store.layerOrder.length - 1) {
    return false;
  }

  return reorderLayers(store, layerId, store.layerOrder.length - 1);
}

/**
 * Move layer to bottom
 */
export function moveLayerToBottom(store: LayerStore, layerId: string): boolean {
  const currentIndex = store.layerOrder.indexOf(layerId);
  if (currentIndex === -1 || currentIndex === 0) {
    return false;
  }

  return reorderLayers(store, layerId, 0);
}

/**
 * Assign element to layer
 */
export function assignElementToLayer(
  store: LayerStore,
  elementId: string,
  layerId: string
): boolean {
  const layer = store.layers.get(layerId);
  if (!layer) return false;

  // Remove from other layers
  store.layers.forEach((l) => {
    l.elementIds = l.elementIds.filter((id) => id !== elementId);
  });

  // Add to target layer
  if (!layer.elementIds.includes(elementId)) {
    layer.elementIds.push(elementId);
    layer.updatedAt = Date.now();
  }

  return true;
}

/**
 * Assign multiple elements to layer
 */
export function assignElementsToLayer(
  store: LayerStore,
  elementIds: string[],
  layerId: string
): boolean {
  const layer = store.layers.get(layerId);
  if (!layer) return false;

  elementIds.forEach((elementId) => {
    assignElementToLayer(store, elementId, layerId);
  });

  return true;
}

/**
 * Remove element from layer
 */
export function removeElementFromLayer(
  store: LayerStore,
  elementId: string,
  layerId: string
): boolean {
  const layer = store.layers.get(layerId);
  if (!layer) return false;

  layer.elementIds = layer.elementIds.filter((id) => id !== elementId);
  layer.updatedAt = Date.now();

  return true;
}

/**
 * Get layer for element
 */
export function getLayerForElement(
  store: LayerStore,
  elementId: string
): Layer | undefined {
  return Array.from(store.layers.values()).find((layer) =>
    layer.elementIds.includes(elementId)
  );
}

/**
 * Toggle layer visibility
 */
export function toggleLayerVisibility(
  store: LayerStore,
  layerId: string
): boolean {
  const layer = store.layers.get(layerId);
  if (!layer) return false;

  layer.visible = !layer.visible;
  layer.updatedAt = Date.now();

  return true;
}

/**
 * Toggle layer lock
 */
export function toggleLayerLock(
  store: LayerStore,
  layerId: string
): boolean {
  const layer = store.layers.get(layerId);
  if (!layer) return false;

  layer.locked = !layer.locked;
  layer.updatedAt = Date.now();

  return true;
}

/**
 * Set active layer
 */
export function setActiveLayer(
  store: LayerStore,
  layerId: string | null
): boolean {
  if (layerId !== null && !store.layers.has(layerId)) {
    return false;
  }

  store.activeLayerId = layerId;
  return true;
}

/**
 * Get active layer
 */
export function getActiveLayer(store: LayerStore): Layer | null {
  return store.activeLayerId ? store.layers.get(store.activeLayerId) || null : null;
}

// ==================== Tag Management ====================

/**
 * Create a new tag
 */
export function createTag(
  store: LayerStore,
  label: string,
  color: string
): Tag {
  const id = `tag-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  const tag: Tag = {
    id,
    label,
    color,
    elementIds: [],
    createdAt: Date.now(),
  };

  store.tags.set(id, tag);
  return tag;
}

/**
 * Get tag by ID
 */
export function getTag(store: LayerStore, tagId: string): Tag | undefined {
  return store.tags.get(tagId);
}

/**
 * Get all tags
 */
export function getAllTags(store: LayerStore): Tag[] {
  return Array.from(store.tags.values());
}

/**
 * Update tag
 */
export function updateTag(
  store: LayerStore,
  tagId: string,
  updates: Partial<Omit<Tag, 'id' | 'createdAt'>>
): Tag | null {
  const tag = store.tags.get(tagId);
  if (!tag) return null;

  const updated: Tag = {
    ...tag,
    ...updates,
  };

  store.tags.set(tagId, updated);
  return updated;
}

/**
 * Delete tag
 */
export function deleteTag(store: LayerStore, tagId: string): boolean {
  return store.tags.delete(tagId);
}

/**
 * Add tag to element
 */
export function addTagToElement(
  store: LayerStore,
  elementId: string,
  tagId: string
): boolean {
  const tag = store.tags.get(tagId);
  if (!tag) return false;

  if (!tag.elementIds.includes(elementId)) {
    tag.elementIds.push(elementId);
  }

  return true;
}

/**
 * Remove tag from element
 */
export function removeTagFromElement(
  store: LayerStore,
  elementId: string,
  tagId: string
): boolean {
  const tag = store.tags.get(tagId);
  if (!tag) return false;

  tag.elementIds = tag.elementIds.filter((id) => id !== elementId);
  return true;
}

/**
 * Get tags for element
 */
export function getTagsForElement(store: LayerStore, elementId: string): Tag[] {
  return Array.from(store.tags.values()).filter((tag) =>
    tag.elementIds.includes(elementId)
  );
}

/**
 * Get elements for tag
 */
export function getElementsByTag(store: LayerStore, tagId: string): string[] {
  const tag = store.tags.get(tagId);
  return tag ? tag.elementIds : [];
}

/**
 * Search tags by label
 */
export function searchTags(store: LayerStore, query: string): Tag[] {
  const lowerQuery = query.toLowerCase();
  return Array.from(store.tags.values()).filter((tag) =>
    tag.label.toLowerCase().includes(lowerQuery)
  );
}

// ==================== Viewpoint Management ====================

/**
 * Create a viewpoint (saved view)
 */
export function createViewpoint(
  store: LayerStore,
  options: {
    name: string;
    viewport: { x: number; y: number; zoom: number };
    visibleLayers: string[];
    selectedElements?: string[];
    description?: string;
  }
): Viewpoint {
  const id = `viewpoint-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  const viewpoint: Viewpoint = {
    id,
    name: options.name,
    viewport: options.viewport,
    visibleLayers: options.visibleLayers,
    selectedElements: options.selectedElements,
    description: options.description,
    createdAt: Date.now(),
  };

  store.viewpoints.set(id, viewpoint);
  return viewpoint;
}

/**
 * Get viewpoint by ID
 */
export function getViewpoint(
  store: LayerStore,
  viewpointId: string
): Viewpoint | undefined {
  return store.viewpoints.get(viewpointId);
}

/**
 * Get all viewpoints
 */
export function getAllViewpoints(store: LayerStore): Viewpoint[] {
  return Array.from(store.viewpoints.values());
}

/**
 * Update viewpoint
 */
export function updateViewpoint(
  store: LayerStore,
  viewpointId: string,
  updates: Partial<Omit<Viewpoint, 'id' | 'createdAt'>>
): Viewpoint | null {
  const viewpoint = store.viewpoints.get(viewpointId);
  if (!viewpoint) return null;

  const updated: Viewpoint = {
    ...viewpoint,
    ...updates,
  };

  store.viewpoints.set(viewpointId, updated);
  return updated;
}

/**
 * Delete viewpoint
 */
export function deleteViewpoint(store: LayerStore, viewpointId: string): boolean {
  return store.viewpoints.delete(viewpointId);
}

/**
 * Apply viewpoint (restore saved view state)
 */
export function applyViewpoint(
  store: LayerStore,
  viewpointId: string,
  callbacks: {
    setViewport: (viewport: { x: number; y: number; zoom: number }) => void;
    setLayerVisibility: (layerId: string, visible: boolean) => void;
    setSelection?: (elementIds: string[]) => void;
  }
): boolean {
  const viewpoint = store.viewpoints.get(viewpointId);
  if (!viewpoint) return false;

  // Restore viewport
  callbacks.setViewport(viewpoint.viewport);

  // Restore layer visibility
  store.layers.forEach((layer) => {
    const shouldBeVisible = viewpoint.visibleLayers.includes(layer.id);
    if (layer.visible !== shouldBeVisible) {
      callbacks.setLayerVisibility(layer.id, shouldBeVisible);
    }
  });

  // Restore selection if provided
  if (viewpoint.selectedElements && callbacks.setSelection) {
    callbacks.setSelection(viewpoint.selectedElements);
  }

  return true;
}

// ==================== Permission Management ====================

/**
 * Set layer permission
 */
export function setLayerPermission(
  store: LayerStore,
  layerId: string,
  principalId: string,
  permission: Permission
): LayerPermission {
  const key = `${layerId}:${principalId}`;
  const perm: LayerPermission = {
    layerId,
    principalId,
    permission,
  };

  store.permissions.set(key, perm);
  return perm;
}

/**
 * Get layer permission
 */
export function getLayerPermission(
  store: LayerStore,
  layerId: string,
  principalId: string
): Permission {
  const key = `${layerId}:${principalId}`;
  const perm = store.permissions.get(key);
  return perm ? perm.permission : 'edit'; // Default to edit
}

/**
 * Check if user can view layer
 */
export function canViewLayer(
  store: LayerStore,
  layerId: string,
  principalId: string
): boolean {
  const permission = getLayerPermission(store, layerId, principalId);
  return ['view', 'edit', 'admin'].includes(permission);
}

/**
 * Check if user can edit layer
 */
export function canEditLayer(
  store: LayerStore,
  layerId: string,
  principalId: string
): boolean {
  const permission = getLayerPermission(store, layerId, principalId);
  return ['edit', 'admin'].includes(permission);
}

/**
 * Check if user can admin layer
 */
export function canAdminLayer(
  store: LayerStore,
  layerId: string,
  principalId: string
): boolean {
  const permission = getLayerPermission(store, layerId, principalId);
  return permission === 'admin';
}

/**
 * Get layers visible to user
 */
export function getVisibleLayersForUser(
  store: LayerStore,
  principalId: string
): Layer[] {
  return Array.from(store.layers.values()).filter((layer) =>
    canViewLayer(store, layer.id, principalId)
  );
}

/**
 * Remove all permissions for layer
 */
export function removeLayerPermissions(store: LayerStore, layerId: string): void {
  const keysToDelete: string[] = [];
  store.permissions.forEach((perm, key) => {
    if (perm.layerId === layerId) {
      keysToDelete.push(key);
    }
  });
  keysToDelete.forEach((key) => store.permissions.delete(key));
}
