/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi } from 'vitest';

import {
  createSemanticZoomConfig,
  createDrillDownState,
  registerLODConfig,
  getLODConfig,
  getAllLODConfigs,
  getActiveDetailLevel,
  shouldRenderElement,
  getVisibleElementTypes,
  updateZoomLevel,
  createNestedScene,
  drillDown,
  drillUp,
  navigateToScene,
  getCurrentScene,
  getParentScene,
  getScene,
  getBreadcrumbs,
  loadNestedScene,
  isSceneLoading,
  isSceneCached,
  clearCache,
  getSceneDepth,
  isAtRoot,
  resetToRoot,
  createStandardLODConfig,
  createPerformanceLODConfig,
  createLabelLODConfig,
  type LODConfig,
  type NestedScene,
} from '../semanticZoom';

describe('semanticZoom', () => {
  describe('Configuration Creation', () => {
    it('should create empty semantic zoom config', () => {
      const config = createSemanticZoomConfig();

      expect(config.lodConfigs.size).toBe(0);
      expect(config.currentZoom).toBe(1.0);
      expect(config.enabled).toBe(true);
      expect(config.transitionDuration).toBe(300);
    });

    it('should create config with custom zoom', () => {
      const config = createSemanticZoomConfig(true, 2.5);

      expect(config.currentZoom).toBe(2.5);
      expect(config.enabled).toBe(true);
    });

    it('should create disabled config', () => {
      const config = createSemanticZoomConfig(false);

      expect(config.enabled).toBe(false);
    });

    it('should create empty drill-down state', () => {
      const state = createDrillDownState();

      expect(state.sceneStack).toEqual([]);
      expect(state.scenes.size).toBe(0);
      expect(state.cache.size).toBe(0);
      expect(state.maxCacheSize).toBe(10);
      expect(state.loading.size).toBe(0);
    });

    it('should create drill-down state with custom cache size', () => {
      const state = createDrillDownState(20);

      expect(state.maxCacheSize).toBe(20);
    });
  });

  describe('LOD Configuration', () => {
    it('should register LOD config', () => {
      const config = createSemanticZoomConfig();
      const lodConfig: LODConfig = {
        elementType: 'node',
        thresholds: [
          { minZoom: 0.5, maxZoom: 2.0, level: 'normal', render: true },
        ],
      };

      registerLODConfig(config, lodConfig);

      expect(config.lodConfigs.size).toBe(1);
      expect(config.lodConfigs.get('node')).toBe(lodConfig);
    });

    it('should get LOD config by type', () => {
      const config = createSemanticZoomConfig();
      const lodConfig: LODConfig = {
        elementType: 'edge',
        thresholds: [
          { minZoom: 0.5, level: 'visible', render: true },
        ],
      };

      registerLODConfig(config, lodConfig);
      const retrieved = getLODConfig(config, 'edge');

      expect(retrieved).toBe(lodConfig);
    });

    it('should return undefined for unknown type', () => {
      const config = createSemanticZoomConfig();
      const retrieved = getLODConfig(config, 'unknown');

      expect(retrieved).toBeUndefined();
    });

    it('should get all LOD configs', () => {
      const config = createSemanticZoomConfig();
      const nodeLOD: LODConfig = {
        elementType: 'node',
        thresholds: [{ minZoom: 0.5, level: 'normal', render: true }],
      };
      const edgeLOD: LODConfig = {
        elementType: 'edge',
        thresholds: [{ minZoom: 1.0, level: 'normal', render: true }],
      };

      registerLODConfig(config, nodeLOD);
      registerLODConfig(config, edgeLOD);

      const all = getAllLODConfigs(config);

      expect(all).toHaveLength(2);
      expect(all).toContain(nodeLOD);
      expect(all).toContain(edgeLOD);
    });
  });

  describe('Detail Level Detection', () => {
    it('should return normal level when disabled', () => {
      const config = createSemanticZoomConfig(false);
      const level = getActiveDetailLevel(config, 'node');

      expect(level).toBe('normal');
    });

    it('should return normal for unconfigured type', () => {
      const config = createSemanticZoomConfig(true, 1.0);
      const level = getActiveDetailLevel(config, 'unknown');

      expect(level).toBe('normal');
    });

    it('should return correct level for zoom in range', () => {
      const config = createSemanticZoomConfig(true, 1.5);
      const lodConfig: LODConfig = {
        elementType: 'node',
        thresholds: [
          { minZoom: 0.5, maxZoom: 1.0, level: 'simple', render: true },
          { minZoom: 1.0, maxZoom: 2.0, level: 'normal', render: true },
          { minZoom: 2.0, level: 'detailed', render: true },
        ],
      };

      registerLODConfig(config, lodConfig);
      const level = getActiveDetailLevel(config, 'node');

      expect(level).toBe('normal');
    });

    it('should return null when no threshold matches', () => {
      const config = createSemanticZoomConfig(true, 0.3);
      const lodConfig: LODConfig = {
        elementType: 'node',
        thresholds: [
          { minZoom: 0.5, maxZoom: 2.0, level: 'normal', render: true },
        ],
      };

      registerLODConfig(config, lodConfig);
      const level = getActiveDetailLevel(config, 'node');

      expect(level).toBeNull();
    });

    it('should use default level when provided', () => {
      const config = createSemanticZoomConfig(true, 0.3);
      const lodConfig: LODConfig = {
        elementType: 'node',
        thresholds: [
          { minZoom: 0.5, maxZoom: 2.0, level: 'normal', render: true },
        ],
        defaultLevel: 'fallback',
      };

      registerLODConfig(config, lodConfig);
      const level = getActiveDetailLevel(config, 'node');

      expect(level).toBe('fallback');
    });

    it('should evaluate render function', () => {
      const config = createSemanticZoomConfig(true, 1.2);
      const renderFn = vi.fn((zoom: number) => zoom > 1.0);
      const lodConfig: LODConfig = {
        elementType: 'label',
        thresholds: [
          { minZoom: 0.5, level: 'visible', render: renderFn },
        ],
      };

      registerLODConfig(config, lodConfig);
      const level = getActiveDetailLevel(config, 'label');

      expect(renderFn).toHaveBeenCalledWith(1.2);
      expect(level).toBe('visible');
    });

    it('should return null when render function returns false', () => {
      const config = createSemanticZoomConfig(true, 0.8);
      const renderFn = vi.fn((zoom: number) => zoom > 1.0);
      const lodConfig: LODConfig = {
        elementType: 'label',
        thresholds: [
          { minZoom: 0.5, level: 'visible', render: renderFn },
        ],
      };

      registerLODConfig(config, lodConfig);
      const level = getActiveDetailLevel(config, 'label');

      expect(renderFn).toHaveBeenCalledWith(0.8);
      expect(level).toBeNull();
    });

    it('should handle render boolean false', () => {
      const config = createSemanticZoomConfig(true, 1.0);
      const lodConfig: LODConfig = {
        elementType: 'node',
        thresholds: [
          { minZoom: 0.5, maxZoom: 2.0, level: 'hidden', render: false },
        ],
      };

      registerLODConfig(config, lodConfig);
      const level = getActiveDetailLevel(config, 'node');

      expect(level).toBeNull();
    });
  });

  describe('Element Rendering', () => {
    it('should render all elements when disabled', () => {
      const config = createSemanticZoomConfig(false);
      const shouldRender = shouldRenderElement(config, 'node');

      expect(shouldRender).toBe(true);
    });

    it('should render element when level is active', () => {
      const config = createSemanticZoomConfig(true, 1.0);
      const lodConfig: LODConfig = {
        elementType: 'node',
        thresholds: [
          { minZoom: 0.5, maxZoom: 2.0, level: 'normal', render: true },
        ],
      };

      registerLODConfig(config, lodConfig);
      const shouldRender = shouldRenderElement(config, 'node');

      expect(shouldRender).toBe(true);
    });

    it('should not render element when no level is active', () => {
      const config = createSemanticZoomConfig(true, 0.3);
      const lodConfig: LODConfig = {
        elementType: 'node',
        thresholds: [
          { minZoom: 0.5, maxZoom: 2.0, level: 'normal', render: true },
        ],
      };

      registerLODConfig(config, lodConfig);
      const shouldRender = shouldRenderElement(config, 'node');

      expect(shouldRender).toBe(false);
    });

    it('should get visible element types', () => {
      const config = createSemanticZoomConfig(true, 1.0);
      const nodeLOD: LODConfig = {
        elementType: 'node',
        thresholds: [
          { minZoom: 0.5, maxZoom: 2.0, level: 'normal', render: true },
        ],
      };
      const labelLOD: LODConfig = {
        elementType: 'label',
        thresholds: [
          { minZoom: 1.5, level: 'visible', render: true },
        ],
      };

      registerLODConfig(config, nodeLOD);
      registerLODConfig(config, labelLOD);

      const visible = getVisibleElementTypes(config);

      expect(visible).toContain('node');
      expect(visible).not.toContain('label');
    });
  });

  describe('Zoom Updates', () => {
    it('should update zoom level', () => {
      const config = createSemanticZoomConfig(true, 1.0);
      const updated = updateZoomLevel(config, 2.5);

      expect(updated.currentZoom).toBe(2.5);
      expect(config.currentZoom).toBe(1.0); // Original unchanged
    });
  });

  describe('Nested Scene Creation', () => {
    it('should create nested scene', () => {
      const data = { nodes: [], edges: [] };
      const scene = createNestedScene('scene-1', 'Main', null, data);

      expect(scene.id).toBe('scene-1');
      expect(scene.name).toBe('Main');
      expect(scene.parentId).toBeNull();
      expect(scene.data).toBe(data);
      expect(scene.loaded).toBe(true);
      expect(scene.loadedAt).toBeGreaterThan(0);
    });

    it('should create nested scene with parent and portal', () => {
      const data = { nodes: [], edges: [] };
      const scene = createNestedScene('scene-2', 'Detail', 'scene-1', data, 'portal-1');

      expect(scene.parentId).toBe('scene-1');
      expect(scene.portalElementId).toBe('portal-1');
    });
  });

  describe('Drill-down Navigation', () => {
    it('should drill down into scene', () => {
      const state = createDrillDownState();
      const scene = createNestedScene('scene-1', 'Main', null, {});

      const updated = drillDown(state, scene, 'portal-1');

      expect(updated.sceneStack).toHaveLength(1);
      expect(updated.sceneStack[0].sceneId).toBe('scene-1');
      expect(updated.sceneStack[0].name).toBe('Main');
      expect(updated.sceneStack[0].portalElementId).toBe('portal-1');
      expect(updated.scenes.get('scene-1')).toBe(scene);
      expect(updated.cache.has('scene-1')).toBe(true);
    });

    it('should drill down multiple levels', () => {
      const state = createDrillDownState();
      const scene1 = createNestedScene('scene-1', 'Main', null, {});
      const scene2 = createNestedScene('scene-2', 'Detail', 'scene-1', {});

      let updated = drillDown(state, scene1);
      updated = drillDown(updated, scene2, 'portal-1');

      expect(updated.sceneStack).toHaveLength(2);
      expect(updated.sceneStack[1].sceneId).toBe('scene-2');
    });

    it('should drill up one level', () => {
      const state = createDrillDownState();
      const scene1 = createNestedScene('scene-1', 'Main', null, {});
      const scene2 = createNestedScene('scene-2', 'Detail', 'scene-1', {});

      let updated = drillDown(state, scene1);
      updated = drillDown(updated, scene2);
      updated = drillUp(updated);

      expect(updated.sceneStack).toHaveLength(1);
      expect(updated.sceneStack[0].sceneId).toBe('scene-1');
    });

    it('should not drill up when at root', () => {
      const state = createDrillDownState();
      const scene = createNestedScene('scene-1', 'Main', null, {});

      let updated = drillDown(state, scene);
      updated = drillUp(updated);

      expect(updated.sceneStack).toHaveLength(1);
    });

    it('should not drill up when stack is empty', () => {
      const state = createDrillDownState();
      const updated = drillUp(state);

      expect(updated.sceneStack).toHaveLength(0);
    });

    it('should navigate to specific scene in breadcrumb', () => {
      const state = createDrillDownState();
      const scene1 = createNestedScene('scene-1', 'Main', null, {});
      const scene2 = createNestedScene('scene-2', 'Detail', 'scene-1', {});
      const scene3 = createNestedScene('scene-3', 'SubDetail', 'scene-2', {});

      let updated = drillDown(state, scene1);
      updated = drillDown(updated, scene2);
      updated = drillDown(updated, scene3);
      updated = navigateToScene(updated, 'scene-2');

      expect(updated.sceneStack).toHaveLength(2);
      expect(updated.sceneStack[1].sceneId).toBe('scene-2');
    });

    it('should not navigate to scene not in stack', () => {
      const state = createDrillDownState();
      const scene = createNestedScene('scene-1', 'Main', null, {});

      let updated = drillDown(state, scene);
      updated = navigateToScene(updated, 'scene-99');

      expect(updated.sceneStack).toHaveLength(1);
    });
  });

  describe('Scene Retrieval', () => {
    it('should get current scene', () => {
      const state = createDrillDownState();
      const scene1 = createNestedScene('scene-1', 'Main', null, {});
      const scene2 = createNestedScene('scene-2', 'Detail', 'scene-1', {});

      let updated = drillDown(state, scene1);
      updated = drillDown(updated, scene2);

      const current = getCurrentScene(updated);

      expect(current?.id).toBe('scene-2');
    });

    it('should return null when no current scene', () => {
      const state = createDrillDownState();
      const current = getCurrentScene(state);

      expect(current).toBeNull();
    });

    it('should get parent scene', () => {
      const state = createDrillDownState();
      const scene1 = createNestedScene('scene-1', 'Main', null, {});
      const scene2 = createNestedScene('scene-2', 'Detail', 'scene-1', {});

      let updated = drillDown(state, scene1);
      updated = drillDown(updated, scene2);

      const parent = getParentScene(updated);

      expect(parent?.id).toBe('scene-1');
    });

    it('should return null when no parent', () => {
      const state = createDrillDownState();
      const scene = createNestedScene('scene-1', 'Main', null, {});

      const updated = drillDown(state, scene);
      const parent = getParentScene(updated);

      expect(parent).toBeNull();
    });

    it('should get scene by ID', () => {
      const state = createDrillDownState();
      const scene = createNestedScene('scene-1', 'Main', null, {});

      const updated = drillDown(state, scene);
      const retrieved = getScene(updated, 'scene-1');

      expect(retrieved).toBe(scene);
    });

    it('should return null for unknown scene ID', () => {
      const state = createDrillDownState();
      const retrieved = getScene(state, 'unknown');

      expect(retrieved).toBeNull();
    });

    it('should get breadcrumbs', () => {
      const state = createDrillDownState();
      const scene1 = createNestedScene('scene-1', 'Main', null, {});
      const scene2 = createNestedScene('scene-2', 'Detail', 'scene-1', {});

      let updated = drillDown(state, scene1);
      updated = drillDown(updated, scene2, 'portal-1');

      const breadcrumbs = getBreadcrumbs(updated);

      expect(breadcrumbs).toHaveLength(2);
      expect(breadcrumbs[0].sceneId).toBe('scene-1');
      expect(breadcrumbs[1].sceneId).toBe('scene-2');
      expect(breadcrumbs[1].portalElementId).toBe('portal-1');
    });
  });

  describe('Async Scene Loading', () => {
    it('should load scene from loader', async () => {
      const state = createDrillDownState();
      const sceneData = createNestedScene('scene-1', 'Loaded', null, { test: true });
      const loader = vi.fn(async () => sceneData);

      const result = await loadNestedScene(state, 'scene-1', loader);

      expect(loader).toHaveBeenCalledWith('scene-1');
      expect(result.scene).toBe(sceneData);
      expect(result.state.scenes.get('scene-1')).toBe(sceneData);
      expect(result.state.cache.has('scene-1')).toBe(true);
    });

    it('should return cached scene without loading', async () => {
      const state = createDrillDownState();
      const cachedScene = createNestedScene('scene-1', 'Cached', null, {});
      state.cache.set('scene-1', cachedScene);

      const loader = vi.fn();
      const result = await loadNestedScene(state, 'scene-1', loader);

      expect(loader).not.toHaveBeenCalled();
      expect(result.scene).toBe(cachedScene);
    });

    it('should throw when scene is already loading', async () => {
      const state = createDrillDownState();
      state.loading.add('scene-1');

      const loader = vi.fn();

      await expect(loadNestedScene(state, 'scene-1', loader)).rejects.toThrow(
        'Scene scene-1 is already loading'
      );
    });

    it('should remove loading flag on error', async () => {
      const state = createDrillDownState();
      const loader = vi.fn(async () => {
        throw new Error('Load failed');
      });

      await expect(loadNestedScene(state, 'scene-1', loader)).rejects.toThrow('Load failed');
      expect(state.loading.has('scene-1')).toBe(false);
    });
  });

  describe('Cache Management', () => {
    it('should check if scene is loading', () => {
      const state = createDrillDownState();
      state.loading.add('scene-1');

      expect(isSceneLoading(state, 'scene-1')).toBe(true);
      expect(isSceneLoading(state, 'scene-2')).toBe(false);
    });

    it('should check if scene is cached', () => {
      const state = createDrillDownState();
      const scene = createNestedScene('scene-1', 'Cached', null, {});
      state.cache.set('scene-1', scene);

      expect(isSceneCached(state, 'scene-1')).toBe(true);
      expect(isSceneCached(state, 'scene-2')).toBe(false);
    });

    it('should clear cache', () => {
      const state = createDrillDownState();
      const scene1 = createNestedScene('scene-1', 'One', null, {});
      const scene2 = createNestedScene('scene-2', 'Two', null, {});

      state.cache.set('scene-1', scene1);
      state.cache.set('scene-2', scene2);

      const updated = clearCache(state);

      expect(updated.cache.size).toBe(0);
      expect(state.cache.size).toBe(2); // Original unchanged
    });

    it('should evict oldest cache entries when over limit', () => {
      const state = createDrillDownState(2); // Max 2 items

      const scene1 = createNestedScene('scene-1', 'One', null, {});
      scene1.loadedAt = 1000;
      const scene2 = createNestedScene('scene-2', 'Two', null, {});
      scene2.loadedAt = 2000;
      const scene3 = createNestedScene('scene-3', 'Three', null, {});
      scene3.loadedAt = 3000;

      let updated = drillDown(state, scene1);
      updated = drillDown(updated, scene2);
      updated = drillDown(updated, scene3);

      // Should keep scene-2 and scene-3 (newest), evict scene-1
      expect(updated.cache.size).toBe(2);
      expect(updated.cache.has('scene-1')).toBe(false);
      expect(updated.cache.has('scene-2')).toBe(true);
      expect(updated.cache.has('scene-3')).toBe(true);
    });
  });

  describe('Scene Hierarchy Utilities', () => {
    it('should get scene depth', () => {
      const state = createDrillDownState();
      const scene1 = createNestedScene('scene-1', 'Main', null, {});
      const scene2 = createNestedScene('scene-2', 'Detail', 'scene-1', {});

      let updated = drillDown(state, scene1);
      expect(getSceneDepth(updated)).toBe(1);

      updated = drillDown(updated, scene2);
      expect(getSceneDepth(updated)).toBe(2);
    });

    it('should check if at root', () => {
      const state = createDrillDownState();
      const scene1 = createNestedScene('scene-1', 'Main', null, {});
      const scene2 = createNestedScene('scene-2', 'Detail', 'scene-1', {});

      let updated = drillDown(state, scene1);
      expect(isAtRoot(updated)).toBe(true);

      updated = drillDown(updated, scene2);
      expect(isAtRoot(updated)).toBe(false);
    });

    it('should reset to root scene', () => {
      const state = createDrillDownState();
      const scene1 = createNestedScene('scene-1', 'Main', null, {});
      const scene2 = createNestedScene('scene-2', 'Detail', 'scene-1', {});
      const scene3 = createNestedScene('scene-3', 'SubDetail', 'scene-2', {});

      let updated = drillDown(state, scene1);
      updated = drillDown(updated, scene2);
      updated = drillDown(updated, scene3);
      updated = resetToRoot(updated);

      expect(updated.sceneStack).toHaveLength(1);
      expect(updated.sceneStack[0].sceneId).toBe('scene-1');
    });

    it('should not fail reset on empty stack', () => {
      const state = createDrillDownState();
      const updated = resetToRoot(state);

      expect(updated.sceneStack).toHaveLength(0);
    });
  });

  describe('Predefined LOD Configurations', () => {
    it('should create standard LOD config', () => {
      const lodConfig = createStandardLODConfig('node');

      expect(lodConfig.elementType).toBe('node');
      expect(lodConfig.thresholds).toHaveLength(3);
      expect(lodConfig.defaultLevel).toBe('normal');

      expect(lodConfig.thresholds[0].level).toBe('overview');
      expect(lodConfig.thresholds[1].level).toBe('normal');
      expect(lodConfig.thresholds[2].level).toBe('detailed');
    });

    it('should create performance LOD config', () => {
      const lodConfig = createPerformanceLODConfig('complex-element');

      expect(lodConfig.elementType).toBe('complex-element');
      expect(lodConfig.thresholds).toHaveLength(2);
      expect(lodConfig.defaultLevel).toBe('simple');

      expect(lodConfig.thresholds[0].level).toBe('simple');
      expect(lodConfig.thresholds[1].level).toBe('full');
    });

    it('should create label LOD config', () => {
      const lodConfig = createLabelLODConfig();

      expect(lodConfig.elementType).toBe('label');
      expect(lodConfig.thresholds).toHaveLength(1);
      expect(lodConfig.defaultLevel).toBeUndefined();

      const threshold = lodConfig.thresholds[0];
      expect(threshold.level).toBe('visible');
      expect(threshold.minZoom).toBe(0.75);
      expect(typeof threshold.render).toBe('function');
    });

    it('should hide labels below 75% zoom', () => {
      const config = createSemanticZoomConfig(true, 0.5);
      const lodConfig = createLabelLODConfig();

      registerLODConfig(config, lodConfig);

      const shouldRender = shouldRenderElement(config, 'label');
      expect(shouldRender).toBe(false);
    });

    it('should show labels at 75% zoom and above', () => {
      const config = createSemanticZoomConfig(true, 1.0);
      const lodConfig = createLabelLODConfig();

      registerLODConfig(config, lodConfig);

      const shouldRender = shouldRenderElement(config, 'label');
      expect(shouldRender).toBe(true);
    });
  });
});
