/**
 * Tests for Feature 2.16: Large Model Paging
 * Comprehensive test coverage for chunked loading, lazy hydration, and delta saving
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

import {
  createChunkStore,
  getChunkCoords,
  generateChunkId,
  getChunkIdForPosition,
  createChunk,
  getChunksInViewport,
  getOrCreateChunk,
  addElement,
  removeElement,
  updateElementPosition,
  getElementsInViewport,
  hydrateChunk,
  preloadViewport,
  streamLoadDocument,
  getDirtyChunks,
  saveDirtyChunks,
  startAutoSave,
  stopAutoSave,
  getChunkStatistics,
  clearChunks,
  exportChunkStore,
  importChunkStore,
  DEFAULT_CHUNK_CONFIG,
} from '../chunkStore';

import type {
  Chunk,
  ChunkConfig,
  ChunkStoreState,
  SpatialElement,
  ViewportBounds,
  LoadProgress,
} from '../chunkStore';

describe('Feature 2.16: Large Model Paging - chunkStore', () => {
  // Sample test element type
  interface TestElement extends SpatialElement {
    name: string;
  }

  // Helper to create test elements
  const createElement = (id: string, x: number, y: number, name: string): TestElement => ({
    id,
    x,
    y,
    width: 100,
    height: 100,
    name,
  });

  describe('State Creation', () => {
    it('should create chunk store with default config', () => {
      const state = createChunkStore();

      expect(state.chunks.size).toBe(0);
      expect(state.spatialIndex.size).toBe(0);
      expect(state.config).toEqual(DEFAULT_CHUNK_CONFIG);
      expect(state.lruOrder).toEqual([]);
      expect(state.loadingChunks.size).toBe(0);
      expect(state.dirtyChunks.size).toBe(0);
      expect(state.stats).toEqual({
        totalElements: 0,
        hydratedChunks: 0,
        cacheHits: 0,
        cacheMisses: 0,
        savesPerformed: 0,
      });
    });

    it('should create chunk store with custom config', () => {
      const customConfig: Partial<ChunkConfig> = {
        chunkSize: 500,
        maxCachedChunks: 50,
        preloadPadding: 2,
        autoSave: false,
      };

      const state = createChunkStore(customConfig);

      expect(state.config.chunkSize).toBe(500);
      expect(state.config.maxCachedChunks).toBe(50);
      expect(state.config.preloadPadding).toBe(2);
      expect(state.config.autoSave).toBe(false);
      expect(state.config.autoSaveInterval).toBe(DEFAULT_CHUNK_CONFIG.autoSaveInterval);
    });
  });

  describe('Chunk Coordinate System', () => {
    it('should calculate chunk coordinates correctly', () => {
      const chunkSize = 1000;

      expect(getChunkCoords(0, 0, chunkSize)).toEqual({ cx: 0, cy: 0 });
      expect(getChunkCoords(500, 500, chunkSize)).toEqual({ cx: 0, cy: 0 });
      expect(getChunkCoords(1000, 1000, chunkSize)).toEqual({ cx: 1, cy: 1 });
      expect(getChunkCoords(1500, 2500, chunkSize)).toEqual({ cx: 1, cy: 2 });
      expect(getChunkCoords(-500, -500, chunkSize)).toEqual({ cx: -1, cy: -1 });
    });

    it('should generate chunk IDs from coordinates', () => {
      expect(generateChunkId(0, 0)).toBe('chunk_0_0');
      expect(generateChunkId(1, 2)).toBe('chunk_1_2');
      expect(generateChunkId(-1, -2)).toBe('chunk_-1_-2');
    });

    it('should get chunk ID for world position', () => {
      const chunkSize = 1000;

      expect(getChunkIdForPosition(500, 500, chunkSize)).toBe('chunk_0_0');
      expect(getChunkIdForPosition(1500, 2500, chunkSize)).toBe('chunk_1_2');
      expect(getChunkIdForPosition(-500, -500, chunkSize)).toBe('chunk_-1_-1');
    });
  });

  describe('Chunk Creation', () => {
    it('should create empty chunk with correct properties', () => {
      const chunk = createChunk<TestElement>(0, 0, 1000);

      expect(chunk.id).toBe('chunk_0_0');
      expect(chunk.x).toBe(0);
      expect(chunk.y).toBe(0);
      expect(chunk.width).toBe(1000);
      expect(chunk.height).toBe(1000);
      expect(chunk.elements).toEqual([]);
      expect(chunk.hydrated).toBe(false);
      expect(chunk.dirty).toBe(false);
      expect(chunk.version).toBe(1);
    });

    it('should create chunk with elements', () => {
      const elements = [
        createElement('e1', 100, 100, 'Element 1'),
        createElement('e2', 200, 200, 'Element 2'),
      ];

      const chunk = createChunk<TestElement>(0, 0, 1000, elements);

      expect(chunk.elements).toEqual(elements);
      expect(chunk.hydrated).toBe(true);
    });

    it('should create chunks at different coordinates', () => {
      const chunk1 = createChunk(1, 2, 1000);
      expect(chunk1.id).toBe('chunk_1_2');
      expect(chunk1.x).toBe(1000);
      expect(chunk1.y).toBe(2000);

      const chunk2 = createChunk(-1, -1, 500);
      expect(chunk2.id).toBe('chunk_-1_-1');
      expect(chunk2.x).toBe(-500);
      expect(chunk2.y).toBe(-500);
    });
  });

  describe('Viewport Chunk Queries', () => {
    it('should find chunks in viewport without padding', () => {
      const viewport: ViewportBounds = { x: 0, y: 0, width: 2000, height: 2000 };
      const chunkIds = getChunksInViewport(viewport, 1000, 0);

      expect(chunkIds).toHaveLength(9); // 3x3 grid
      expect(chunkIds).toContain('chunk_0_0');
      expect(chunkIds).toContain('chunk_1_1');
      expect(chunkIds).toContain('chunk_2_2');
    });

    it('should find chunks in viewport with padding', () => {
      const viewport: ViewportBounds = { x: 1000, y: 1000, width: 1000, height: 1000 };
      const chunkIds = getChunksInViewport(viewport, 1000, 1);

      // Viewport covers chunk_1_1 and chunk_2_2
      // With padding=1, should include surrounding chunks
      expect(chunkIds.length).toBeGreaterThan(4);
      expect(chunkIds).toContain('chunk_0_0'); // Top-left padding
      expect(chunkIds).toContain('chunk_1_1'); // Center
      expect(chunkIds).toContain('chunk_3_3'); // Bottom-right padding
    });

    it('should handle viewport at origin', () => {
      const viewport: ViewportBounds = { x: 0, y: 0, width: 1000, height: 1000 };
      const chunkIds = getChunksInViewport(viewport, 1000, 0);

      expect(chunkIds).toContain('chunk_0_0');
      expect(chunkIds).toContain('chunk_1_1');
    });

    it('should handle negative coordinates', () => {
      const viewport: ViewportBounds = { x: -1000, y: -1000, width: 1000, height: 1000 };
      const chunkIds = getChunksInViewport(viewport, 1000, 0);

      expect(chunkIds).toContain('chunk_-1_-1');
      expect(chunkIds).toContain('chunk_0_0');
    });
  });

  describe('Element Management', () => {
    it('should add element to correct chunk', () => {
      const state = createChunkStore<TestElement>();
      const element = createElement('e1', 500, 500, 'Element 1');

      const newState = addElement(state, element);

      expect(newState.stats.totalElements).toBe(1);
      const chunk = newState.chunks.get('chunk_0_0');
      expect(chunk).toBeDefined();
      expect(chunk?.elements).toHaveLength(1);
      expect(chunk?.elements[0]).toEqual(element);
      expect(chunk?.dirty).toBe(true);
      expect(newState.dirtyChunks.has('chunk_0_0')).toBe(true);
    });

    it('should add multiple elements to same chunk', () => {
      let state = createChunkStore<TestElement>();
      const e1 = createElement('e1', 100, 100, 'Element 1');
      const e2 = createElement('e2', 200, 200, 'Element 2');

      state = addElement(state, e1);
      state = addElement(state, e2);

      expect(state.stats.totalElements).toBe(2);
      const chunk = state.chunks.get('chunk_0_0');
      expect(chunk?.elements).toHaveLength(2);
    });

    it('should add elements to different chunks', () => {
      let state = createChunkStore<TestElement>();
      const e1 = createElement('e1', 500, 500, 'Element 1');
      const e2 = createElement('e2', 1500, 1500, 'Element 2');

      state = addElement(state, e1);
      state = addElement(state, e2);

      expect(state.stats.totalElements).toBe(2);
      expect(state.chunks.size).toBe(2);
      expect(state.chunks.get('chunk_0_0')?.elements).toHaveLength(1);
      expect(state.chunks.get('chunk_1_1')?.elements).toHaveLength(1);
    });

    it('should remove element from chunk', () => {
      let state = createChunkStore<TestElement>();
      const element = createElement('e1', 500, 500, 'Element 1');

      state = addElement(state, element);
      state = removeElement(state, 'e1');

      expect(state.stats.totalElements).toBe(0);
      const chunk = state.chunks.get('chunk_0_0');
      expect(chunk?.elements).toHaveLength(0);
    });

    it('should handle removing non-existent element', () => {
      const state = createChunkStore<TestElement>();
      const newState = removeElement(state, 'non-existent');

      expect(newState.stats.totalElements).toBe(0);
    });

    it('should update element position within same chunk', () => {
      let state = createChunkStore<TestElement>();
      const element = createElement('e1', 100, 100, 'Element 1');

      state = addElement(state, element);
      state = updateElementPosition(state, 'e1', 200, 200);

      const chunk = state.chunks.get('chunk_0_0');
      expect(chunk?.elements).toHaveLength(1);
      expect(chunk?.elements[0].x).toBe(200);
      expect(chunk?.elements[0].y).toBe(200);
    });

    it('should update element position to different chunk', () => {
      let state = createChunkStore<TestElement>();
      const element = createElement('e1', 500, 500, 'Element 1');

      state = addElement(state, element);
      state = updateElementPosition(state, 'e1', 1500, 1500);

      expect(state.chunks.get('chunk_0_0')?.elements).toHaveLength(0);
      expect(state.chunks.get('chunk_1_1')?.elements).toHaveLength(1);
      expect(state.chunks.get('chunk_1_1')?.elements[0].x).toBe(1500);
    });

    it('should warn when chunk exceeds recommended size', () => {
      const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
      const state = createChunkStore<TestElement>({ maxElementsPerChunk: 2 });

      let newState = state;
      newState = addElement(newState, createElement('e1', 100, 100, 'Element 1'));
      newState = addElement(newState, createElement('e2', 200, 200, 'Element 2'));
      newState = addElement(newState, createElement('e3', 300, 300, 'Element 3'));

      expect(consoleSpy).toHaveBeenCalledWith(
        expect.stringContaining('exceeding recommended maximum')
      );
      consoleSpy.mockRestore();
    });
  });

  describe('Viewport Element Queries', () => {
    it('should get elements in viewport from hydrated chunks', () => {
      let state = createChunkStore<TestElement>();
      const e1 = createElement('e1', 500, 500, 'Element 1');
      const e2 = createElement('e2', 1500, 1500, 'Element 2');

      state = addElement(state, e1);
      state = addElement(state, e2);

      // Mark chunks as hydrated
      state.chunks.forEach((chunk) => {
        chunk.hydrated = true;
      });

      // Viewport that doesn't reach chunk boundaries
      const viewport: ViewportBounds = { x: 0, y: 0, width: 999, height: 999 };
      const elements = getElementsInViewport(state, viewport);

      expect(elements).toHaveLength(1);
      expect(elements[0].id).toBe('e1');
    });

    it('should track cache hits for hydrated chunks', () => {
      let state = createChunkStore<TestElement>();
      state = addElement(state, createElement('e1', 500, 500, 'Element 1'));
      state.chunks.forEach((chunk) => {
        chunk.hydrated = true;
      });

      const viewport: ViewportBounds = { x: 0, y: 0, width: 1000, height: 1000 };
      getElementsInViewport(state, viewport);

      expect(state.stats.cacheHits).toBeGreaterThan(0);
    });

    it('should track cache misses for non-hydrated chunks', () => {
      const state = createChunkStore<TestElement>();

      const viewport: ViewportBounds = { x: 0, y: 0, width: 1000, height: 1000 };
      getElementsInViewport(state, viewport);

      expect(state.stats.cacheMisses).toBeGreaterThan(0);
    });
  });

  describe('Chunk Hydration', () => {
    it('should hydrate chunk with loader', async () => {
      const state = createChunkStore<TestElement>();
      const elements = [createElement('e1', 100, 100, 'Element 1')];
      const loader = vi.fn().mockResolvedValue(elements);

      await hydrateChunk(state, 'chunk_0_0', loader);

      expect(loader).toHaveBeenCalledWith('chunk_0_0');
      const chunk = state.chunks.get('chunk_0_0');
      expect(chunk?.hydrated).toBe(true);
      expect(chunk?.elements).toEqual(elements);
      expect(state.stats.hydratedChunks).toBe(1);
    });

    it('should not reload already hydrated chunk', async () => {
      const state = createChunkStore<TestElement>();
      const elements = [createElement('e1', 100, 100, 'Element 1')];
      const loader = vi.fn().mockResolvedValue(elements);

      await hydrateChunk(state, 'chunk_0_0', loader);
      await hydrateChunk(state, 'chunk_0_0', loader);

      expect(loader).toHaveBeenCalledTimes(1);
    });

    it('should update LRU on hydration', async () => {
      const state = createChunkStore<TestElement>();
      const loader = vi.fn().mockResolvedValue([]);

      await hydrateChunk(state, 'chunk_0_0', loader);

      expect(state.lruOrder).toContain('chunk_0_0');
    });

    it('should handle loader errors', async () => {
      const state = createChunkStore<TestElement>();
      const loader = vi.fn().mockRejectedValue(new Error('Load failed'));

      await expect(hydrateChunk(state, 'chunk_0_0', loader)).rejects.toThrow('Load failed');
      expect(state.loadingChunks.has('chunk_0_0')).toBe(false);
    });

    it('should evict old chunks when cache is full', async () => {
      const state = createChunkStore<TestElement>({ maxCachedChunks: 2 });
      const loader = vi.fn().mockResolvedValue([]);

      await hydrateChunk(state, 'chunk_0_0', loader);
      await hydrateChunk(state, 'chunk_1_1', loader);
      await hydrateChunk(state, 'chunk_2_2', loader);

      // Only 2 chunks should remain hydrated
      const hydratedCount = Array.from(state.chunks.values()).filter((c) => c.hydrated).length;
      expect(hydratedCount).toBeLessThanOrEqual(2);
    });

    it('should warn when attempting to evict dirty chunks', async () => {
      const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
      const state = createChunkStore<TestElement>({ maxCachedChunks: 2 });
      const loader = vi.fn().mockResolvedValue([]);

      // Load 2 chunks (max capacity)
      await hydrateChunk(state, 'chunk_0_0', loader);
      await hydrateChunk(state, 'chunk_1_1', loader);
      
      // Mark first chunk as dirty
      const chunk = state.chunks.get('chunk_0_0');
      if (chunk) {
        chunk.dirty = true;
        state.dirtyChunks.add('chunk_0_0');
      }

      // Try to load a third chunk, which should trigger eviction
      await hydrateChunk(state, 'chunk_2_2', loader);

      // Should have warned about dirty chunk
      expect(consoleSpy).toHaveBeenCalledWith(
        expect.stringContaining('Cannot evict dirty chunk')
      );
      
      // The dirty chunk might get dehydrated anyway since we can't hold infinite chunks
      // but the warning should have been issued
      consoleSpy.mockRestore();
    });
  });

  describe('Viewport Preloading', () => {
    it('should preload chunks around viewport', async () => {
      const state = createChunkStore<TestElement>({ preloadPadding: 1 });
      const loader = vi.fn().mockResolvedValue([]);

      const viewport: ViewportBounds = { x: 0, y: 0, width: 1000, height: 1000 };
      await preloadViewport(state, viewport, loader);

      // Should load more than just viewport chunks due to padding
      expect(loader).toHaveBeenCalled();
      expect(loader.mock.calls.length).toBeGreaterThan(4);
    });
  });

  describe('Stream Loading', () => {
    it('should stream load document with progress', async () => {
      const state = createChunkStore<TestElement>();
      const loader = vi.fn().mockResolvedValue([createElement('e1', 100, 100, 'Element 1')]);
      const onProgress = vi.fn();

      const chunkIds = ['chunk_0_0', 'chunk_1_1', 'chunk_2_2'];
      await streamLoadDocument(state, chunkIds, loader, onProgress);

      expect(loader).toHaveBeenCalledTimes(3);
      expect(onProgress).toHaveBeenCalledTimes(3);

      // Check final progress
      const finalProgress = onProgress.mock.calls[2][0] as LoadProgress;
      expect(finalProgress.chunksLoaded).toBe(3);
      expect(finalProgress.totalChunks).toBe(3);
      expect(finalProgress.percentage).toBe(100);
    });

    it('should work without progress callback', async () => {
      const state = createChunkStore<TestElement>();
      const loader = vi.fn().mockResolvedValue([]);

      const chunkIds = ['chunk_0_0', 'chunk_1_1'];
      await expect(streamLoadDocument(state, chunkIds, loader)).resolves.toBeDefined();
    });
  });

  describe('Delta Saving', () => {
    it('should get dirty chunks', () => {
      let state = createChunkStore<TestElement>();
      state = addElement(state, createElement('e1', 500, 500, 'Element 1'));
      state = addElement(state, createElement('e2', 1500, 1500, 'Element 2'));

      const dirtyChunks = getDirtyChunks(state);

      expect(dirtyChunks).toHaveLength(2);
      expect(dirtyChunks.every((c) => c.dirty)).toBe(true);
    });

    it('should save dirty chunks', async () => {
      let state = createChunkStore<TestElement>();
      state = addElement(state, createElement('e1', 500, 500, 'Element 1'));
      state = addElement(state, createElement('e2', 1500, 1500, 'Element 2'));

      const saver = vi.fn().mockResolvedValue(undefined);
      await saveDirtyChunks(state, saver);

      expect(saver).toHaveBeenCalledTimes(2);
      expect(state.dirtyChunks.size).toBe(0);
      expect(state.stats.savesPerformed).toBe(2);
      expect(Array.from(state.chunks.values()).every((c) => !c.dirty)).toBe(true);
    });

    it('should increment version on save', async () => {
      let state = createChunkStore<TestElement>();
      state = addElement(state, createElement('e1', 500, 500, 'Element 1'));

      const chunk = state.chunks.get('chunk_0_0');
      const initialVersion = chunk?.version || 0;

      const saver = vi.fn().mockResolvedValue(undefined);
      await saveDirtyChunks(state, saver);

      expect(state.chunks.get('chunk_0_0')?.version).toBe(initialVersion + 1);
    });
  });

  describe('Auto-Save', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.restoreAllMocks();
    });

    it('should start auto-save timer', () => {
      const state = createChunkStore<TestElement>({ autoSave: true, autoSaveInterval: 1000 });
      const saver = vi.fn().mockResolvedValue(undefined);

      startAutoSave(state, saver);

      expect(state.autoSaveTimer).toBeDefined();
    });

    it('should not start timer if auto-save disabled', () => {
      const state = createChunkStore<TestElement>({ autoSave: false });
      const saver = vi.fn().mockResolvedValue(undefined);

      startAutoSave(state, saver);

      expect(state.autoSaveTimer).toBeUndefined();
    });

    it('should trigger save on interval', async () => {
      let state = createChunkStore<TestElement>({ autoSave: true, autoSaveInterval: 1000 });
      state = addElement(state, createElement('e1', 500, 500, 'Element 1'));

      const saver = vi.fn().mockResolvedValue(undefined);
      startAutoSave(state, saver);

      await vi.advanceTimersByTimeAsync(1000);

      expect(saver).toHaveBeenCalled();
    });

    it('should stop auto-save timer', () => {
      const state = createChunkStore<TestElement>({ autoSave: true });
      const saver = vi.fn().mockResolvedValue(undefined);

      startAutoSave(state, saver);
      const timerId = state.autoSaveTimer;
      stopAutoSave(state);

      expect(state.autoSaveTimer).toBeUndefined();
    });
  });

  describe('Statistics', () => {
    it('should calculate statistics correctly', () => {
      let state = createChunkStore<TestElement>({ maxCachedChunks: 10 });

      // Add elements to multiple chunks
      state = addElement(state, createElement('e1', 500, 500, 'Element 1'));
      state = addElement(state, createElement('e2', 1500, 1500, 'Element 2'));
      state = addElement(state, createElement('e3', 2500, 2500, 'Element 3'));

      // Mark chunks as hydrated
      state.chunks.forEach((chunk) => {
        chunk.hydrated = true;
      });
      state.stats.hydratedChunks = state.chunks.size;

      const stats = getChunkStatistics(state);

      expect(stats.totalChunks).toBe(3);
      expect(stats.hydratedChunks).toBe(3);
      expect(stats.dirtyChunks).toBe(3);
      expect(stats.totalElements).toBe(3);
      expect(stats.averageElementsPerChunk).toBe(1);
      expect(stats.memoryUsageEstimate).toBeGreaterThan(0);
    });

    it('should calculate cache hit rate', () => {
      const state = createChunkStore<TestElement>();
      state.stats.cacheHits = 80;
      state.stats.cacheMisses = 20;

      const stats = getChunkStatistics(state);

      expect(stats.cacheHitRate).toBe(0.8);
    });

    it('should handle zero cache accesses', () => {
      const state = createChunkStore<TestElement>();

      const stats = getChunkStatistics(state);

      expect(stats.cacheHitRate).toBe(0);
    });
  });

  describe('Clear Chunks', () => {
    it('should clear all chunks and reset state', () => {
      let state = createChunkStore<TestElement>();
      state = addElement(state, createElement('e1', 500, 500, 'Element 1'));
      startAutoSave(state, vi.fn());

      const clearedState = clearChunks(state);

      expect(clearedState.chunks.size).toBe(0);
      expect(clearedState.spatialIndex.size).toBe(0);
      expect(clearedState.lruOrder).toEqual([]);
      expect(clearedState.dirtyChunks.size).toBe(0);
      expect(clearedState.stats.totalElements).toBe(0);
      expect(clearedState.autoSaveTimer).toBeUndefined();
    });
  });

  describe('Export/Import', () => {
    it('should export chunk store state', () => {
      let state = createChunkStore<TestElement>();
      state = addElement(state, createElement('e1', 500, 500, 'Element 1'));
      state = addElement(state, createElement('e2', 1500, 1500, 'Element 2'));

      const exported = exportChunkStore(state);

      expect(exported.chunks).toHaveLength(2);
      expect(exported.config).toEqual(state.config);
      expect(exported.stats).toEqual(state.stats);
    });

    it('should import chunk store state', () => {
      let state = createChunkStore<TestElement>();
      state = addElement(state, createElement('e1', 500, 500, 'Element 1'));
      state = addElement(state, createElement('e2', 1500, 1500, 'Element 2'));

      const exported = exportChunkStore(state);
      const imported = importChunkStore(exported);

      expect(imported.chunks.size).toBe(2);
      expect(imported.stats).toEqual(exported.stats);
      expect(imported.config).toEqual(exported.config);
    });

    it('should preserve dirty chunks on import', () => {
      let state = createChunkStore<TestElement>();
      state = addElement(state, createElement('e1', 500, 500, 'Element 1'));

      const exported = exportChunkStore(state);
      const imported = importChunkStore(exported);

      expect(imported.dirtyChunks.size).toBe(1);
    });

    it('should preserve hydrated chunks on import', () => {
      let state = createChunkStore<TestElement>();
      state = addElement(state, createElement('e1', 500, 500, 'Element 1'));
      state.chunks.forEach((chunk) => {
        chunk.hydrated = true;
      });

      const exported = exportChunkStore(state);
      const imported = importChunkStore(exported);

      const hydratedChunks = Array.from(imported.chunks.values()).filter((c) => c.hydrated);
      expect(hydratedChunks.length).toBe(1);
    });

    it('should round-trip complex state', () => {
      let state = createChunkStore<TestElement>({ chunkSize: 500, maxCachedChunks: 50 });

      // Add elements across multiple chunks
      for (let i = 0; i < 10; i++) {
        state = addElement(
          state,
          createElement(`e${i}`, i * 600, i * 600, `Element ${i}`)
        );
      }

      const exported = exportChunkStore(state);
      const imported = importChunkStore(exported);

      expect(imported.chunks.size).toBe(state.chunks.size);
      expect(imported.stats.totalElements).toBe(state.stats.totalElements);
      expect(imported.config.chunkSize).toBe(state.config.chunkSize);
    });
  });

  describe('Edge Cases', () => {
    it('should handle elements at chunk boundaries', () => {
      let state = createChunkStore<TestElement>({ chunkSize: 1000 });

      // Element exactly on chunk boundary
      state = addElement(state, createElement('e1', 1000, 1000, 'Boundary'));

      expect(state.chunks.size).toBe(1);
      expect(state.chunks.has('chunk_1_1')).toBe(true);
    });

    it('should handle very large coordinates', () => {
      let state = createChunkStore<TestElement>();

      state = addElement(state, createElement('e1', 100000, 100000, 'Far away'));

      expect(state.chunks.size).toBe(1);
      const chunkId = getChunkIdForPosition(100000, 100000, state.config.chunkSize);
      expect(state.chunks.has(chunkId)).toBe(true);
    });

    it('should handle negative coordinates', () => {
      let state = createChunkStore<TestElement>();

      state = addElement(state, createElement('e1', -500, -500, 'Negative'));

      expect(state.chunks.size).toBe(1);
      expect(state.chunks.has('chunk_-1_-1')).toBe(true);
    });

    it('should handle concurrent chunk hydration attempts', async () => {
      const state = createChunkStore<TestElement>();
      const loader = vi.fn().mockResolvedValue([]);

      // Start two hydrations of same chunk
      const promise1 = hydrateChunk(state, 'chunk_0_0', loader);
      const promise2 = hydrateChunk(state, 'chunk_0_0', loader);

      await Promise.all([promise1, promise2]);

      // Loader should only be called once
      expect(loader).toHaveBeenCalledTimes(1);
    });

    it('should handle empty viewport query', () => {
      const state = createChunkStore<TestElement>();
      const viewport: ViewportBounds = { x: 0, y: 0, width: 0, height: 0 };

      const elements = getElementsInViewport(state, viewport);

      expect(elements).toEqual([]);
    });
  });
});
