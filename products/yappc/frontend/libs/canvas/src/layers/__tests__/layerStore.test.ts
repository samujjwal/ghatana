/**
 * Layer Store Tests
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  createLayerStore,
  createLayer,
  getLayer,
  getAllLayers,
  getLayersInRenderOrder,
  updateLayer,
  deleteLayer,
  reorderLayers,
  moveLayerUp,
  moveLayerDown,
  moveLayerToTop,
  moveLayerToBottom,
  assignElementToLayer,
  assignElementsToLayer,
  removeElementFromLayer,
  getLayerForElement,
  toggleLayerVisibility,
  toggleLayerLock,
  setActiveLayer,
  getActiveLayer,
  createTag,
  getTag,
  getAllTags,
  updateTag,
  deleteTag,
  addTagToElement,
  removeTagFromElement,
  getTagsForElement,
  getElementsByTag,
  searchTags,
  createViewpoint,
  getViewpoint,
  getAllViewpoints,
  updateViewpoint,
  deleteViewpoint,
  applyViewpoint,
  setLayerPermission,
  getLayerPermission,
  canViewLayer,
  canEditLayer,
  canAdminLayer,
  getVisibleLayersForUser,
  removeLayerPermissions,
  type LayerStore,
} from '../layerStore';

describe('layerStore', () => {
  let store: LayerStore;

  beforeEach(() => {
    store = createLayerStore();
  });

  describe('Store Creation', () => {
    it('should create empty store', () => {
      expect(store.layers.size).toBe(0);
      expect(store.tags.size).toBe(0);
      expect(store.viewpoints.size).toBe(0);
      expect(store.permissions.size).toBe(0);
      expect(store.activeLayerId).toBeNull();
      expect(store.layerOrder).toEqual([]);
    });
  });

  describe('Layer CRUD', () => {
    it('should create layer', () => {
      const layer = createLayer(store, { name: 'Background' });

      expect(layer.id).toBeDefined();
      expect(layer.name).toBe('Background');
      expect(layer.zIndex).toBe(0);
      expect(layer.visible).toBe(true);
      expect(layer.locked).toBe(false);
      expect(layer.elementIds).toEqual([]);
      expect(store.layers.size).toBe(1);
    });

    it('should set first layer as active', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      expect(store.activeLayerId).toBe(layer.id);
    });

    it('should create layer with options', () => {
      const layer = createLayer(store, {
        name: 'Overlay',
        color: '#ff0000',
        description: 'Top layer',
      });

      expect(layer.color).toBe('#ff0000');
      expect(layer.description).toBe('Top layer');
    });

    it('should assign incremental z-index', () => {
      const layer1 = createLayer(store, { name: 'Layer 1' });
      const layer2 = createLayer(store, { name: 'Layer 2' });
      const layer3 = createLayer(store, { name: 'Layer 3' });

      expect(layer1.zIndex).toBe(0);
      expect(layer2.zIndex).toBe(1);
      expect(layer3.zIndex).toBe(2);
    });

    it('should get layer by ID', () => {
      const layer = createLayer(store, { name: 'Test' });
      const retrieved = getLayer(store, layer.id);

      expect(retrieved).toBeDefined();
      expect(retrieved!.id).toBe(layer.id);
    });

    it('should return undefined for non-existent layer', () => {
      const retrieved = getLayer(store, 'non-existent');
      expect(retrieved).toBeUndefined();
    });

    it('should get all layers', () => {
      createLayer(store, { name: 'Layer 1' });
      createLayer(store, { name: 'Layer 2' });
      createLayer(store, { name: 'Layer 3' });

      const layers = getAllLayers(store);
      expect(layers.length).toBe(3);
    });

    it('should get layers in render order', () => {
      const layer1 = createLayer(store, { name: 'Bottom' });
      const layer2 = createLayer(store, { name: 'Middle' });
      const layer3 = createLayer(store, { name: 'Top' });

      const layers = getLayersInRenderOrder(store);
      
      expect(layers[0].id).toBe(layer1.id);
      expect(layers[1].id).toBe(layer2.id);
      expect(layers[2].id).toBe(layer3.id);
    });

    it('should update layer properties', () => {
      const layer = createLayer(store, { name: 'Original' });
      const updated = updateLayer(store, layer.id, {
        name: 'Updated',
        color: '#0000ff',
      });

      expect(updated).toBeDefined();
      expect(updated!.name).toBe('Updated');
      expect(updated!.color).toBe('#0000ff');
      expect(updated!.updatedAt).toBeGreaterThanOrEqual(layer.createdAt);
    });

    it('should return null when updating non-existent layer', () => {
      const updated = updateLayer(store, 'non-existent', { name: 'Test' });
      expect(updated).toBeNull();
    });

    it('should delete layer', () => {
      const layer = createLayer(store, { name: 'To Delete' });
      const deleted = deleteLayer(store, layer.id);

      expect(deleted).toBe(true);
      expect(store.layers.size).toBe(0);
      expect(store.layerOrder.length).toBe(0);
    });

    it('should update active layer after deletion', () => {
      const layer1 = createLayer(store, { name: 'Layer 1' });
      const layer2 = createLayer(store, { name: 'Layer 2' });

      setActiveLayer(store, layer2.id);
      deleteLayer(store, layer2.id);

      expect(store.activeLayerId).toBe(layer1.id);
    });

    it('should return false when deleting non-existent layer', () => {
      const deleted = deleteLayer(store, 'non-existent');
      expect(deleted).toBe(false);
    });
  });

  describe('Layer Ordering', () => {
    let layer1: ReturnType<typeof createLayer>;
    let layer2: ReturnType<typeof createLayer>;
    let layer3: ReturnType<typeof createLayer>;

    beforeEach(() => {
      layer1 = createLayer(store, { name: 'Layer 1' });
      layer2 = createLayer(store, { name: 'Layer 2' });
      layer3 = createLayer(store, { name: 'Layer 3' });
    });

    it('should reorder layers', () => {
      // Move layer1 to position 2 (top)
      reorderLayers(store, layer1.id, 2);

      expect(store.layerOrder).toEqual([layer2.id, layer3.id, layer1.id]);
      expect(getLayer(store, layer1.id)!.zIndex).toBe(2);
      expect(getLayer(store, layer2.id)!.zIndex).toBe(0);
      expect(getLayer(store, layer3.id)!.zIndex).toBe(1);
    });

    it('should move layer up', () => {
      moveLayerUp(store, layer1.id);

      expect(store.layerOrder).toEqual([layer2.id, layer1.id, layer3.id]);
    });

    it('should not move top layer up', () => {
      const result = moveLayerUp(store, layer3.id);

      expect(result).toBe(false);
      expect(store.layerOrder).toEqual([layer1.id, layer2.id, layer3.id]);
    });

    it('should move layer down', () => {
      moveLayerDown(store, layer2.id);

      expect(store.layerOrder).toEqual([layer2.id, layer1.id, layer3.id]);
    });

    it('should not move bottom layer down', () => {
      const result = moveLayerDown(store, layer1.id);

      expect(result).toBe(false);
      expect(store.layerOrder).toEqual([layer1.id, layer2.id, layer3.id]);
    });

    it('should move layer to top', () => {
      moveLayerToTop(store, layer1.id);

      expect(store.layerOrder[store.layerOrder.length - 1]).toBe(layer1.id);
      expect(getLayer(store, layer1.id)!.zIndex).toBe(2);
    });

    it('should move layer to bottom', () => {
      moveLayerToBottom(store, layer3.id);

      expect(store.layerOrder[0]).toBe(layer3.id);
      expect(getLayer(store, layer3.id)!.zIndex).toBe(0);
    });
  });

  describe('Element Assignment', () => {
    it('should assign element to layer', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      const assigned = assignElementToLayer(store, 'element-1', layer.id);

      expect(assigned).toBe(true);
      expect(layer.elementIds).toContain('element-1');
    });

    it('should move element between layers', () => {
      const layer1 = createLayer(store, { name: 'Layer 1' });
      const layer2 = createLayer(store, { name: 'Layer 2' });

      assignElementToLayer(store, 'element-1', layer1.id);
      assignElementToLayer(store, 'element-1', layer2.id);

      expect(getLayer(store, layer1.id)!.elementIds).not.toContain('element-1');
      expect(getLayer(store, layer2.id)!.elementIds).toContain('element-1');
    });

    it('should assign multiple elements', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      assignElementsToLayer(store, ['el-1', 'el-2', 'el-3'], layer.id);

      const retrieved = getLayer(store, layer.id)!;
      expect(retrieved.elementIds).toHaveLength(3);
      expect(retrieved.elementIds).toContain('el-1');
      expect(retrieved.elementIds).toContain('el-2');
      expect(retrieved.elementIds).toContain('el-3');
    });

    it('should remove element from layer', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      assignElementToLayer(store, 'element-1', layer.id);
      removeElementFromLayer(store, 'element-1', layer.id);

      expect(getLayer(store, layer.id)!.elementIds).not.toContain('element-1');
    });

    it('should get layer for element', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      assignElementToLayer(store, 'element-1', layer.id);

      const found = getLayerForElement(store, 'element-1');
      expect(found).toBeDefined();
      expect(found!.id).toBe(layer.id);
    });

    it('should return undefined for unassigned element', () => {
      createLayer(store, { name: 'Layer 1' });
      const found = getLayerForElement(store, 'unassigned');
      
      expect(found).toBeUndefined();
    });
  });

  describe('Layer Visibility & Locking', () => {
    it('should toggle layer visibility', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      
      toggleLayerVisibility(store, layer.id);
      expect(getLayer(store, layer.id)!.visible).toBe(false);

      toggleLayerVisibility(store, layer.id);
      expect(getLayer(store, layer.id)!.visible).toBe(true);
    });

    it('should toggle layer lock', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      
      toggleLayerLock(store, layer.id);
      expect(getLayer(store, layer.id)!.locked).toBe(true);

      toggleLayerLock(store, layer.id);
      expect(getLayer(store, layer.id)!.locked).toBe(false);
    });
  });

  describe('Active Layer', () => {
    it('should set active layer', () => {
      const layer1 = createLayer(store, { name: 'Layer 1' });
      const layer2 = createLayer(store, { name: 'Layer 2' });

      setActiveLayer(store, layer2.id);
      expect(store.activeLayerId).toBe(layer2.id);

      const active = getActiveLayer(store);
      expect(active).toBeDefined();
      expect(active!.id).toBe(layer2.id);
    });

    it('should clear active layer', () => {
      createLayer(store, { name: 'Layer 1' });
      setActiveLayer(store, null);

      expect(store.activeLayerId).toBeNull();
      expect(getActiveLayer(store)).toBeNull();
    });

    it('should reject invalid layer ID', () => {
      const result = setActiveLayer(store, 'non-existent');
      expect(result).toBe(false);
    });
  });

  describe('Tag Management', () => {
    it('should create tag', () => {
      const tag = createTag(store, 'Important', '#ff0000');

      expect(tag.id).toBeDefined();
      expect(tag.label).toBe('Important');
      expect(tag.color).toBe('#ff0000');
      expect(tag.elementIds).toEqual([]);
      expect(store.tags.size).toBe(1);
    });

    it('should get tag by ID', () => {
      const tag = createTag(store, 'Test', '#00ff00');
      const retrieved = getTag(store, tag.id);

      expect(retrieved).toBeDefined();
      expect(retrieved!.id).toBe(tag.id);
    });

    it('should get all tags', () => {
      createTag(store, 'Tag 1', '#ff0000');
      createTag(store, 'Tag 2', '#00ff00');
      createTag(store, 'Tag 3', '#0000ff');

      const tags = getAllTags(store);
      expect(tags.length).toBe(3);
    });

    it('should update tag', () => {
      const tag = createTag(store, 'Original', '#000000');
      const updated = updateTag(store, tag.id, {
        label: 'Updated',
        color: '#ffffff',
      });

      expect(updated).toBeDefined();
      expect(updated!.label).toBe('Updated');
      expect(updated!.color).toBe('#ffffff');
    });

    it('should delete tag', () => {
      const tag = createTag(store, 'To Delete', '#000000');
      const deleted = deleteTag(store, tag.id);

      expect(deleted).toBe(true);
      expect(store.tags.size).toBe(0);
    });

    it('should add tag to element', () => {
      const tag = createTag(store, 'Important', '#ff0000');
      addTagToElement(store, 'element-1', tag.id);

      expect(getTag(store, tag.id)!.elementIds).toContain('element-1');
    });

    it('should remove tag from element', () => {
      const tag = createTag(store, 'Important', '#ff0000');
      addTagToElement(store, 'element-1', tag.id);
      removeTagFromElement(store, 'element-1', tag.id);

      expect(getTag(store, tag.id)!.elementIds).not.toContain('element-1');
    });

    it('should get tags for element', () => {
      const tag1 = createTag(store, 'Tag 1', '#ff0000');
      const tag2 = createTag(store, 'Tag 2', '#00ff00');
      
      addTagToElement(store, 'element-1', tag1.id);
      addTagToElement(store, 'element-1', tag2.id);

      const tags = getTagsForElement(store, 'element-1');
      expect(tags.length).toBe(2);
      expect(tags.map(t => t.id)).toContain(tag1.id);
      expect(tags.map(t => t.id)).toContain(tag2.id);
    });

    it('should get elements by tag', () => {
      const tag = createTag(store, 'Important', '#ff0000');
      addTagToElement(store, 'element-1', tag.id);
      addTagToElement(store, 'element-2', tag.id);

      const elements = getElementsByTag(store, tag.id);
      expect(elements).toHaveLength(2);
      expect(elements).toContain('element-1');
      expect(elements).toContain('element-2');
    });

    it('should search tags by label', () => {
      createTag(store, 'Important', '#ff0000');
      createTag(store, 'Urgent', '#ff00ff');
      createTag(store, 'Normal', '#0000ff');

      const results = searchTags(store, 'imp');
      expect(results.length).toBe(1);
      expect(results[0].label).toBe('Important');
    });

    it('should search tags case-insensitively', () => {
      createTag(store, 'Important', '#ff0000');
      
      const results = searchTags(store, 'IMPORT');
      expect(results.length).toBe(1);
    });
  });

  describe('Viewpoint Management', () => {
    it('should create viewpoint', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      const viewpoint = createViewpoint(store, {
        name: 'Overview',
        viewport: { x: 0, y: 0, zoom: 1 },
        visibleLayers: [layer.id],
      });

      expect(viewpoint.id).toBeDefined();
      expect(viewpoint.name).toBe('Overview');
      expect(viewpoint.viewport).toEqual({ x: 0, y: 0, zoom: 1 });
      expect(viewpoint.visibleLayers).toContain(layer.id);
      expect(store.viewpoints.size).toBe(1);
    });

    it('should create viewpoint with selection', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      const viewpoint = createViewpoint(store, {
        name: 'Detail View',
        viewport: { x: 100, y: 100, zoom: 2 },
        visibleLayers: [layer.id],
        selectedElements: ['element-1', 'element-2'],
        description: 'Focus on specific elements',
      });

      expect(viewpoint.selectedElements).toEqual(['element-1', 'element-2']);
      expect(viewpoint.description).toBe('Focus on specific elements');
    });

    it('should get viewpoint by ID', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      const viewpoint = createViewpoint(store, {
        name: 'Test',
        viewport: { x: 0, y: 0, zoom: 1 },
        visibleLayers: [layer.id],
      });

      const retrieved = getViewpoint(store, viewpoint.id);
      expect(retrieved).toBeDefined();
      expect(retrieved!.id).toBe(viewpoint.id);
    });

    it('should get all viewpoints', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      createViewpoint(store, {
        name: 'View 1',
        viewport: { x: 0, y: 0, zoom: 1 },
        visibleLayers: [layer.id],
      });
      createViewpoint(store, {
        name: 'View 2',
        viewport: { x: 100, y: 100, zoom: 2 },
        visibleLayers: [layer.id],
      });

      const viewpoints = getAllViewpoints(store);
      expect(viewpoints.length).toBe(2);
    });

    it('should update viewpoint', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      const viewpoint = createViewpoint(store, {
        name: 'Original',
        viewport: { x: 0, y: 0, zoom: 1 },
        visibleLayers: [layer.id],
      });

      const updated = updateViewpoint(store, viewpoint.id, {
        name: 'Updated',
        viewport: { x: 50, y: 50, zoom: 1.5 },
      });

      expect(updated).toBeDefined();
      expect(updated!.name).toBe('Updated');
      expect(updated!.viewport.x).toBe(50);
    });

    it('should delete viewpoint', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      const viewpoint = createViewpoint(store, {
        name: 'To Delete',
        viewport: { x: 0, y: 0, zoom: 1 },
        visibleLayers: [layer.id],
      });

      const deleted = deleteViewpoint(store, viewpoint.id);
      expect(deleted).toBe(true);
      expect(store.viewpoints.size).toBe(0);
    });

    it('should apply viewpoint', () => {
      const layer1 = createLayer(store, { name: 'Layer 1' });
      const layer2 = createLayer(store, { name: 'Layer 2' });
      
      const viewpoint = createViewpoint(store, {
        name: 'Test View',
        viewport: { x: 100, y: 200, zoom: 1.5 },
        visibleLayers: [layer1.id],
        selectedElements: ['element-1'],
      });

      let appliedViewport: any = null;
      const visibilityChanges: Record<string, boolean> = {};
      let appliedSelection: string[] | null = null;

      const applied = applyViewpoint(store, viewpoint.id, {
        setViewport: (vp) => { appliedViewport = vp; },
        setLayerVisibility: (id, visible) => { visibilityChanges[id] = visible; },
        setSelection: (ids) => { appliedSelection = ids; },
      });

      expect(applied).toBe(true);
      expect(appliedViewport).toEqual({ x: 100, y: 200, zoom: 1.5 });
      expect(appliedSelection).toEqual(['element-1']);
    });
  });

  describe('Permission Management', () => {
    it('should set layer permission', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      const perm = setLayerPermission(store, layer.id, 'user-1', 'view');

      expect(perm.layerId).toBe(layer.id);
      expect(perm.principalId).toBe('user-1');
      expect(perm.permission).toBe('view');
    });

    it('should get layer permission', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      setLayerPermission(store, layer.id, 'user-1', 'view');

      const perm = getLayerPermission(store, layer.id, 'user-1');
      expect(perm).toBe('view');
    });

    it('should default to edit permission', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      const perm = getLayerPermission(store, layer.id, 'user-unknown');
      
      expect(perm).toBe('edit');
    });

    it('should check view permission', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      setLayerPermission(store, layer.id, 'user-1', 'view');

      expect(canViewLayer(store, layer.id, 'user-1')).toBe(true);
      expect(canEditLayer(store, layer.id, 'user-1')).toBe(false);
      expect(canAdminLayer(store, layer.id, 'user-1')).toBe(false);
    });

    it('should check edit permission', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      setLayerPermission(store, layer.id, 'user-1', 'edit');

      expect(canViewLayer(store, layer.id, 'user-1')).toBe(true);
      expect(canEditLayer(store, layer.id, 'user-1')).toBe(true);
      expect(canAdminLayer(store, layer.id, 'user-1')).toBe(false);
    });

    it('should check admin permission', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      setLayerPermission(store, layer.id, 'user-1', 'admin');

      expect(canViewLayer(store, layer.id, 'user-1')).toBe(true);
      expect(canEditLayer(store, layer.id, 'user-1')).toBe(true);
      expect(canAdminLayer(store, layer.id, 'user-1')).toBe(true);
    });

    it('should check none permission', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      setLayerPermission(store, layer.id, 'user-1', 'none');

      expect(canViewLayer(store, layer.id, 'user-1')).toBe(false);
      expect(canEditLayer(store, layer.id, 'user-1')).toBe(false);
      expect(canAdminLayer(store, layer.id, 'user-1')).toBe(false);
    });

    it('should get visible layers for user', () => {
      const layer1 = createLayer(store, { name: 'Layer 1' });
      const layer2 = createLayer(store, { name: 'Layer 2' });
      const layer3 = createLayer(store, { name: 'Layer 3' });

      setLayerPermission(store, layer1.id, 'user-1', 'view');
      setLayerPermission(store, layer2.id, 'user-1', 'edit');
      setLayerPermission(store, layer3.id, 'user-1', 'none');

      const visible = getVisibleLayersForUser(store, 'user-1');
      expect(visible.length).toBe(2);
      expect(visible.map(l => l.id)).toContain(layer1.id);
      expect(visible.map(l => l.id)).toContain(layer2.id);
      expect(visible.map(l => l.id)).not.toContain(layer3.id);
    });

    it('should remove layer permissions', () => {
      const layer = createLayer(store, { name: 'Layer 1' });
      setLayerPermission(store, layer.id, 'user-1', 'view');
      setLayerPermission(store, layer.id, 'user-2', 'edit');

      removeLayerPermissions(store, layer.id);

      const perm1 = store.permissions.get(`${layer.id}:user-1`);
      const perm2 = store.permissions.get(`${layer.id}:user-2`);
      
      expect(perm1).toBeUndefined();
      expect(perm2).toBeUndefined();
    });
  });
});
