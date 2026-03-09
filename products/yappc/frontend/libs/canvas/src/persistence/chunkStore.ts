/**
 * Feature 2.16: Large Model Paging
 * 
 * Implements chunked loading, lazy hydration, and delta saving for large canvas documents.
 * Enables enterprise users to work with documents containing thousands of elements
 * without performance degradation.
 * 
 * Key Capabilities:
 * - Chunked loading: Large documents load incrementally
 * - Lazy hydration: Off-screen chunks hydrate on demand
 * - Delta saves: Only modified chunks persist, reducing network/storage overhead
 * - Spatial indexing: Fast chunk lookup based on viewport
 * - LRU caching: Automatic chunk eviction with configurable limits
 * - Stream loading: Progress reporting for large document loads
 * 
 * @module persistence/chunkStore
 */

/**
 * Represents a spatial chunk of the canvas
 */
export interface Chunk<T = unknown> {
  /** Unique identifier for this chunk */
  id: string;
  /** X coordinate of chunk's top-left corner */
  x: number;
  /** Y coordinate of chunk's top-left corner */
  y: number;
  /** Width of the chunk in world units */
  width: number;
  /** Height of the chunk in world units */
  height: number;
  /** Elements within this chunk */
  elements: T[];
  /** Whether this chunk has been hydrated (loaded) */
  hydrated: boolean;
  /** Whether this chunk has unsaved changes */
  dirty: boolean;
  /** Timestamp of last modification */
  lastModified: number;
  /** Timestamp of last access (for LRU) */
  lastAccessed: number;
  /** Version number for conflict detection */
  version: number;
}

/**
 * Configuration for the chunk system
 */
export interface ChunkConfig {
  /** Size of each chunk in world units */
  chunkSize: number;
  /** Maximum number of chunks to keep in memory */
  maxCachedChunks: number;
  /** Padding around viewport for preloading (in chunks) */
  preloadPadding: number;
  /** Whether to automatically save dirty chunks */
  autoSave: boolean;
  /** Auto-save interval in milliseconds */
  autoSaveInterval: number;
  /** Maximum elements per chunk (soft limit, warning if exceeded) */
  maxElementsPerChunk: number;
}

/**
 * Represents a viewport rectangle for chunk queries
 */
export interface ViewportBounds {
  /** Left edge X coordinate */
  x: number;
  /** Top edge Y coordinate */
  y: number;
  /** Viewport width */
  width: number;
  /** Viewport height */
  height: number;
}

/**
 * Represents an element with spatial position
 */
export interface SpatialElement {
  /** Element ID */
  id: string;
  /** X position */
  x: number;
  /** Y position */
  y: number;
  /** Element width (optional) */
  width?: number;
  /** Element height (optional) */
  height?: number;
  /** Additional element data */
  [key: string]: unknown;
}

/**
 * Progress callback for stream loading
 */
export interface LoadProgress {
  /** Number of chunks loaded so far */
  chunksLoaded: number;
  /** Total number of chunks to load */
  totalChunks: number;
  /** Number of elements loaded */
  elementsLoaded: number;
  /** Total estimated elements */
  totalElements: number;
  /** Progress percentage (0-100) */
  percentage: number;
}

/**
 * State for the chunk store
 */
export interface ChunkStoreState<T = SpatialElement> {
  /** Map of chunk ID to chunk */
  chunks: Map<string, Chunk<T>>;
  /** Spatial index: maps grid coordinates to chunk IDs */
  spatialIndex: Map<string, string>;
  /** Configuration */
  config: ChunkConfig;
  /** Chunks in LRU order (most recently used first) */
  lruOrder: string[];
  /** Set of currently loading chunk IDs */
  loadingChunks: Set<string>;
  /** Set of dirty (unsaved) chunk IDs */
  dirtyChunks: Set<string>;
  /** Auto-save timer ID */
  autoSaveTimer?: NodeJS.Timeout;
  /** Statistics */
  stats: {
    totalElements: number;
    hydratedChunks: number;
    cacheHits: number;
    cacheMisses: number;
    savesPerformed: number;
  };
}

/**
 * Default chunk configuration
 */
export const DEFAULT_CHUNK_CONFIG: ChunkConfig = {
  chunkSize: 1000, // 1000x1000 world units per chunk
  maxCachedChunks: 100, // Keep up to 100 chunks in memory
  preloadPadding: 1, // Preload 1 chunk around viewport
  autoSave: true,
  autoSaveInterval: 30000, // Auto-save every 30 seconds
  maxElementsPerChunk: 1000,
};

/**
 * Create a new chunk store state
 */
export function createChunkStore<T = SpatialElement>(
  config: Partial<ChunkConfig> = {}
): ChunkStoreState<T> {
  return {
    chunks: new Map(),
    spatialIndex: new Map(),
    config: { ...DEFAULT_CHUNK_CONFIG, ...config },
    lruOrder: [],
    loadingChunks: new Set(),
    dirtyChunks: new Set(),
    stats: {
      totalElements: 0,
      hydratedChunks: 0,
      cacheHits: 0,
      cacheMisses: 0,
      savesPerformed: 0,
    },
  };
}

/**
 * Get chunk coordinates from world position
 */
export function getChunkCoords(
  x: number,
  y: number,
  chunkSize: number
): { cx: number; cy: number } {
  return {
    cx: Math.floor(x / chunkSize),
    cy: Math.floor(y / chunkSize),
  };
}

/**
 * Generate chunk ID from coordinates
 */
export function generateChunkId(cx: number, cy: number): string {
  return `chunk_${cx}_${cy}`;
}

/**
 * Get chunk ID for a world position
 */
export function getChunkIdForPosition(
  x: number,
  y: number,
  chunkSize: number
): string {
  const { cx, cy } = getChunkCoords(x, y, chunkSize);
  return generateChunkId(cx, cy);
}

/**
 * Create a new chunk
 */
export function createChunk<T = SpatialElement>(
  cx: number,
  cy: number,
  chunkSize: number,
  elements: T[] = []
): Chunk<T> {
  const now = Date.now();
  return {
    id: generateChunkId(cx, cy),
    x: cx * chunkSize,
    y: cy * chunkSize,
    width: chunkSize,
    height: chunkSize,
    elements,
    hydrated: elements.length > 0,
    dirty: false,
    lastModified: now,
    lastAccessed: now,
    version: 1,
  };
}

/**
 * Get chunk IDs that intersect with viewport
 */
export function getChunksInViewport(
  viewport: ViewportBounds,
  chunkSize: number,
  padding: number = 0
): string[] {
  // Expand viewport by padding (in chunk units)
  const expandedViewport = {
    x: viewport.x - padding * chunkSize,
    y: viewport.y - padding * chunkSize,
    width: viewport.width + 2 * padding * chunkSize,
    height: viewport.height + 2 * padding * chunkSize,
  };

  const startCoords = getChunkCoords(expandedViewport.x, expandedViewport.y, chunkSize);
  const endCoords = getChunkCoords(
    expandedViewport.x + expandedViewport.width,
    expandedViewport.y + expandedViewport.height,
    chunkSize
  );

  const chunkIds: string[] = [];
  for (let cx = startCoords.cx; cx <= endCoords.cx; cx++) {
    for (let cy = startCoords.cy; cy <= endCoords.cy; cy++) {
      chunkIds.push(generateChunkId(cx, cy));
    }
  }

  return chunkIds;
}

/**
 * Get or create chunk for a position
 */
export function getOrCreateChunk<T = SpatialElement>(
  state: ChunkStoreState<T>,
  x: number,
  y: number
): { state: ChunkStoreState<T>; chunk: Chunk<T> } {
  const chunkId = getChunkIdForPosition(x, y, state.config.chunkSize);
  const { cx, cy } = getChunkCoords(x, y, state.config.chunkSize);

  let chunk = state.chunks.get(chunkId);
  if (!chunk) {
    chunk = createChunk<T>(cx, cy, state.config.chunkSize);
    state.chunks.set(chunkId, chunk);
    state.spatialIndex.set(`${cx},${cy}`, chunkId);
  }

  return { state, chunk };
}

/**
 * Add element to appropriate chunk
 */
export function addElement<T extends SpatialElement>(
  state: ChunkStoreState<T>,
  element: T
): ChunkStoreState<T> {
  const { state: newState, chunk } = getOrCreateChunk(state, element.x, element.y);

  // Remove element from old chunk if it exists
  newState.chunks.forEach((c) => {
    c.elements = c.elements.filter((e) => e.id !== element.id);
  });

  // Add to new chunk
  chunk.elements.push(element);
  chunk.dirty = true;
  chunk.lastModified = Date.now();

  // Mark chunk as dirty
  newState.dirtyChunks.add(chunk.id);
  newState.stats.totalElements++;

  // Check if chunk exceeds recommended size
  if (chunk.elements.length > newState.config.maxElementsPerChunk) {
    console.warn(
      `Chunk ${chunk.id} contains ${chunk.elements.length} elements, ` +
        `exceeding recommended maximum of ${newState.config.maxElementsPerChunk}`
    );
  }

  return newState;
}

/**
 * Remove element from its chunk
 */
export function removeElement<T extends SpatialElement>(
  state: ChunkStoreState<T>,
  elementId: string
): ChunkStoreState<T> {
  const newState = { ...state };
  let found = false;

  newState.chunks.forEach((chunk) => {
    const initialLength = chunk.elements.length;
    chunk.elements = chunk.elements.filter((e) => e.id !== elementId);
    if (chunk.elements.length < initialLength) {
      chunk.dirty = true;
      chunk.lastModified = Date.now();
      newState.dirtyChunks.add(chunk.id);
      found = true;
    }
  });

  if (found) {
    newState.stats.totalElements--;
  }

  return newState;
}

/**
 * Update element position (may move to different chunk)
 */
export function updateElementPosition<T extends SpatialElement>(
  state: ChunkStoreState<T>,
  elementId: string,
  newX: number,
  newY: number
): ChunkStoreState<T> {
  // Find element in current chunk
  let element: T | undefined;
  state.chunks.forEach((chunk) => {
    const found = chunk.elements.find((e) => e.id === elementId);
    if (found) {
      element = found;
    }
  });

  if (!element) {
    return state;
  }

  // Remove from current chunk
  let newState = removeElement(state, elementId);

  // Update position
  element = { ...element, x: newX, y: newY } as T;

  // Add to new chunk
  newState = addElement(newState, element);

  return newState;
}

/**
 * Get all elements in viewport
 */
export function getElementsInViewport<T = SpatialElement>(
  state: ChunkStoreState<T>,
  viewport: ViewportBounds
): T[] {
  const chunkIds = getChunksInViewport(viewport, state.config.chunkSize);
  const elements: T[] = [];

  chunkIds.forEach((chunkId) => {
    const chunk = state.chunks.get(chunkId);
    if (chunk && chunk.hydrated) {
      elements.push(...chunk.elements);
      state.stats.cacheHits++;
    } else {
      state.stats.cacheMisses++;
    }
  });

  return elements;
}

/**
 * Hydrate (load) a chunk
 */
export async function hydrateChunk<T = SpatialElement>(
  state: ChunkStoreState<T>,
  chunkId: string,
  loader: (chunkId: string) => Promise<T[]>
): Promise<ChunkStoreState<T>> {
  // Check if already loading
  if (state.loadingChunks.has(chunkId)) {
    return state;
  }

  // Check if already hydrated
  const chunk = state.chunks.get(chunkId);
  if (chunk && chunk.hydrated) {
    updateLRU(state, chunkId);
    chunk.lastAccessed = Date.now();
    return state;
  }

  // Mark as loading
  state.loadingChunks.add(chunkId);

  try {
    // Load elements
    const elements = await loader(chunkId);

    // Get chunk coords from ID
    const match = chunkId.match(/chunk_(-?\d+)_(-?\d+)/);
    if (!match) {
      throw new Error(`Invalid chunk ID: ${chunkId}`);
    }
    const cx = parseInt(match[1], 10);
    const cy = parseInt(match[2], 10);

    // Create or update chunk
    const hydratedChunk = createChunk<T>(cx, cy, state.config.chunkSize, elements);
    state.chunks.set(chunkId, hydratedChunk);
    state.loadingChunks.delete(chunkId);
    state.stats.hydratedChunks++;
    state.stats.totalElements += elements.length;

    // Update LRU
    updateLRU(state, chunkId);

    // Evict old chunks if needed
    evictOldChunks(state);

    return state;
  } catch (error) {
    state.loadingChunks.delete(chunkId);
    throw error;
  }
}

/**
 * Update LRU order
 */
function updateLRU<T>(state: ChunkStoreState<T>, chunkId: string): void {
  // Remove from current position
  state.lruOrder = state.lruOrder.filter((id) => id !== chunkId);
  // Add to front
  state.lruOrder.unshift(chunkId);
}

/**
 * Evict least recently used chunks if cache is full
 */
function evictOldChunks<T>(state: ChunkStoreState<T>): void {
  let attemptsRemaining = state.lruOrder.length;
  
  while (
    state.lruOrder.length > state.config.maxCachedChunks &&
    attemptsRemaining > 0
  ) {
    attemptsRemaining--;
    
    // Get LRU chunk (oldest)
    const lruChunkId = state.lruOrder[state.lruOrder.length - 1];
    if (!lruChunkId) break;

    const chunk = state.chunks.get(lruChunkId);
    if (chunk) {
      // Don't evict dirty chunks
      if (chunk.dirty) {
        console.warn(`Cannot evict dirty chunk ${lruChunkId}, skipping`);
        // Move to front of LRU to prevent repeated warnings
        state.lruOrder = state.lruOrder.filter((id) => id !== lruChunkId);
        state.lruOrder.unshift(lruChunkId);
        continue;
      }

      // Dehydrate chunk (keep metadata but clear elements)
      const elementCount = chunk.elements.length;
      chunk.elements = [];
      chunk.hydrated = false;
      state.stats.hydratedChunks--;
      state.stats.totalElements -= elementCount;

      // Remove from LRU
      state.lruOrder.pop();
    } else {
      // Chunk doesn't exist, remove from LRU
      state.lruOrder.pop();
    }
  }
}

/**
 * Preload chunks around viewport
 */
export async function preloadViewport<T = SpatialElement>(
  state: ChunkStoreState<T>,
  viewport: ViewportBounds,
  loader: (chunkId: string) => Promise<T[]>
): Promise<ChunkStoreState<T>> {
  const chunkIds = getChunksInViewport(
    viewport,
    state.config.chunkSize,
    state.config.preloadPadding
  );

  // Load chunks in parallel
  const loadPromises = chunkIds.map((chunkId) => hydrateChunk(state, chunkId, loader));
  await Promise.all(loadPromises);

  return state;
}

/**
 * Stream load large document with progress reporting
 */
export async function streamLoadDocument<T = SpatialElement>(
  state: ChunkStoreState<T>,
  chunkIds: string[],
  loader: (chunkId: string) => Promise<T[]>,
  onProgress?: (progress: LoadProgress) => void
): Promise<ChunkStoreState<T>> {
  const totalChunks = chunkIds.length;
  let chunksLoaded = 0;
  let elementsLoaded = 0;

  for (const chunkId of chunkIds) {
    await hydrateChunk(state, chunkId, loader);

    const chunk = state.chunks.get(chunkId);
    if (chunk) {
      elementsLoaded += chunk.elements.length;
    }

    chunksLoaded++;

    if (onProgress) {
      onProgress({
        chunksLoaded,
        totalChunks,
        elementsLoaded,
        totalElements: state.stats.totalElements,
        percentage: (chunksLoaded / totalChunks) * 100,
      });
    }
  }

  return state;
}

/**
 * Get all dirty chunks (chunks with unsaved changes)
 */
export function getDirtyChunks<T = SpatialElement>(
  state: ChunkStoreState<T>
): Chunk<T>[] {
  return Array.from(state.dirtyChunks)
    .map((id) => state.chunks.get(id))
    .filter((chunk): chunk is Chunk<T> => chunk !== undefined);
}

/**
 * Save dirty chunks (delta save)
 */
export async function saveDirtyChunks<T = SpatialElement>(
  state: ChunkStoreState<T>,
  saver: (chunk: Chunk<T>) => Promise<void>
): Promise<ChunkStoreState<T>> {
  const dirtyChunks = getDirtyChunks(state);

  // Save chunks in parallel
  const savePromises = dirtyChunks.map(async (chunk) => {
    await saver(chunk);
    chunk.dirty = false;
    chunk.version++;
    state.dirtyChunks.delete(chunk.id);
    state.stats.savesPerformed++;
  });

  await Promise.all(savePromises);

  return state;
}

/**
 * Start auto-save timer
 */
export function startAutoSave<T = SpatialElement>(
  state: ChunkStoreState<T>,
  saver: (chunk: Chunk<T>) => Promise<void>
): ChunkStoreState<T> {
  if (!state.config.autoSave) {
    return state;
  }

  // Clear existing timer
  if (state.autoSaveTimer) {
    clearInterval(state.autoSaveTimer);
  }

  // Start new timer
  state.autoSaveTimer = setInterval(() => {
    saveDirtyChunks(state, saver).catch((error) => {
      console.error('Auto-save failed:', error);
    });
  }, state.config.autoSaveInterval);

  return state;
}

/**
 * Stop auto-save timer
 */
export function stopAutoSave<T = SpatialElement>(
  state: ChunkStoreState<T>
): ChunkStoreState<T> {
  if (state.autoSaveTimer) {
    clearInterval(state.autoSaveTimer);
    state.autoSaveTimer = undefined;
  }
  return state;
}

/**
 * Get chunk statistics
 */
export function getChunkStatistics<T = SpatialElement>(
  state: ChunkStoreState<T>
): {
  totalChunks: number;
  hydratedChunks: number;
  dirtyChunks: number;
  totalElements: number;
  cacheHitRate: number;
  averageElementsPerChunk: number;
  memoryUsageEstimate: number;
} {
  const totalChunks = state.chunks.size;
  const hydratedChunks = state.stats.hydratedChunks;
  const dirtyChunks = state.dirtyChunks.size;
  const totalElements = state.stats.totalElements;

  const totalCacheAccesses = state.stats.cacheHits + state.stats.cacheMisses;
  const cacheHitRate =
    totalCacheAccesses > 0 ? state.stats.cacheHits / totalCacheAccesses : 0;

  const averageElementsPerChunk =
    hydratedChunks > 0 ? totalElements / hydratedChunks : 0;

  // Rough estimate: 1KB per element + chunk overhead
  const memoryUsageEstimate = totalElements * 1024 + totalChunks * 512;

  return {
    totalChunks,
    hydratedChunks,
    dirtyChunks,
    totalElements,
    cacheHitRate,
    averageElementsPerChunk,
    memoryUsageEstimate,
  };
}

/**
 * Clear all chunks (flush cache)
 */
export function clearChunks<T = SpatialElement>(
  state: ChunkStoreState<T>
): ChunkStoreState<T> {
  stopAutoSave(state);

  return {
    ...state,
    chunks: new Map(),
    spatialIndex: new Map(),
    lruOrder: [],
    loadingChunks: new Set(),
    dirtyChunks: new Set(),
    stats: {
      totalElements: 0,
      hydratedChunks: 0,
      cacheHits: 0,
      cacheMisses: 0,
      savesPerformed: 0,
    },
  };
}

/**
 * Export state for persistence
 */
export function exportChunkStore<T = SpatialElement>(
  state: ChunkStoreState<T>
): {
  chunks: Array<Chunk<T>>;
  config: ChunkConfig;
  stats: typeof state.stats;
} {
  return {
    chunks: Array.from(state.chunks.values()),
    config: state.config,
    stats: state.stats,
  };
}

/**
 * Import state from persistence
 */
export function importChunkStore<T = SpatialElement>(
  data: ReturnType<typeof exportChunkStore<T>>
): ChunkStoreState<T> {
  const state = createChunkStore<T>(data.config);
  state.stats = data.stats;

  data.chunks.forEach((chunk) => {
    state.chunks.set(chunk.id, chunk);
    const match = chunk.id.match(/chunk_(-?\d+)_(-?\d+)/);
    if (match) {
      const cx = parseInt(match[1], 10);
      const cy = parseInt(match[2], 10);
      state.spatialIndex.set(`${cx},${cy}`, chunk.id);
    }
    if (chunk.hydrated) {
      state.lruOrder.push(chunk.id);
    }
    if (chunk.dirty) {
      state.dirtyChunks.add(chunk.id);
    }
  });

  return state;
}
