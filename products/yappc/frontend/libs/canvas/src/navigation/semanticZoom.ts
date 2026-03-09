/**
 * Semantic Zoom & Drill-down Navigation
 *
 * Provides zoom-based level-of-detail control and nested canvas navigation:
 * - Zoom threshold rendering (show/hide details based on zoom level)
 * - Drill-down portal navigation with breadcrumb tracking
 * - Lazy loading of nested scenes with caching
 * - Level-of-detail (LOD) configurations per element type
 *
 * @module navigation/semanticZoom
 */

/**
 *
 */
export interface ZoomThreshold {
  /** Minimum zoom level to show this detail */
  minZoom: number;
  /** Maximum zoom level to show this detail (optional) */
  maxZoom?: number;
  /** Detail level name (e.g., 'overview', 'normal', 'detailed') */
  level: string;
  /** Render function or flag for this detail level */
  render: boolean | ((zoom: number) => boolean);
}

/**
 *
 */
export interface LODConfig {
  /** Element type or category */
  elementType: string;
  /** Zoom thresholds for different detail levels */
  thresholds: ZoomThreshold[];
  /** Default detail level when zoom is between thresholds */
  defaultLevel?: string;
}

/**
 *
 */
export interface NestedScene {
  /** Unique scene identifier */
  id: string;
  /** Parent scene ID (null for root) */
  parentId: string | null;
  /** Scene name */
  name: string;
  /** Portal element ID that leads to this scene */
  portalElementId?: string;
  /** Scene data (canvas state) */
  data: unknown;
  /** Load timestamp (for cache management) */
  loadedAt?: number;
  /** Whether scene is currently loaded */
  loaded: boolean;
}

/**
 *
 */
export interface BreadcrumbItem {
  /** Scene ID */
  sceneId: string;
  /** Scene name */
  name: string;
  /** Portal element that led here */
  portalElementId?: string;
}

/**
 *
 */
export interface DrillDownState {
  /** Current scene stack (breadcrumb trail) */
  sceneStack: BreadcrumbItem[];
  /** All loaded nested scenes by ID */
  scenes: Map<string, NestedScene>;
  /** Scene loading cache */
  cache: Map<string, NestedScene>;
  /** Maximum cache size */
  maxCacheSize: number;
  /** Loading state */
  loading: Set<string>;
}

/**
 *
 */
export interface SemanticZoomConfig {
  /** LOD configurations by element type */
  lodConfigs: Map<string, LODConfig>;
  /** Current zoom level */
  currentZoom: number;
  /** Whether semantic zoom is enabled */
  enabled: boolean;
  /** Smooth transition duration (ms) */
  transitionDuration?: number;
}

/**
 * Create empty semantic zoom configuration
 */
export function createSemanticZoomConfig(
  enabled = true,
  currentZoom = 1.0
): SemanticZoomConfig {
  return {
    lodConfigs: new Map(),
    currentZoom,
    enabled,
    transitionDuration: 300,
  };
}

/**
 * Create empty drill-down state
 */
export function createDrillDownState(maxCacheSize = 10): DrillDownState {
  return {
    sceneStack: [],
    scenes: new Map(),
    cache: new Map(),
    maxCacheSize,
    loading: new Set(),
  };
}

/**
 * Register LOD configuration for element type
 */
export function registerLODConfig(
  config: SemanticZoomConfig,
  lodConfig: LODConfig
): void {
  config.lodConfigs.set(lodConfig.elementType, lodConfig);
}

/**
 * Get LOD configuration for element type
 */
export function getLODConfig(
  config: SemanticZoomConfig,
  elementType: string
): LODConfig | undefined {
  return config.lodConfigs.get(elementType);
}

/**
 * Get all registered LOD configurations
 */
export function getAllLODConfigs(config: SemanticZoomConfig): LODConfig[] {
  return Array.from(config.lodConfigs.values());
}

/**
 * Determine which detail level should be rendered at current zoom
 */
export function getActiveDetailLevel(
  config: SemanticZoomConfig,
  elementType: string
): string | null {
  if (!config.enabled) {
    return 'normal'; // Default level when disabled
  }

  const lodConfig = config.lodConfigs.get(elementType);
  if (!lodConfig) {
    return 'normal'; // Default for unconfigured types
  }

  const zoom = config.currentZoom;

  // Find matching threshold
  for (const threshold of lodConfig.thresholds) {
    const aboveMin = zoom >= threshold.minZoom;
    const belowMax = threshold.maxZoom === undefined || zoom <= threshold.maxZoom;

    if (aboveMin && belowMax) {
      // Check if render condition is met
      if (typeof threshold.render === 'function') {
        if (threshold.render(zoom)) {
          return threshold.level;
        }
      } else if (threshold.render) {
        return threshold.level;
      }
    }
  }

  return lodConfig.defaultLevel || null;
}

/**
 * Check if element should be rendered at current zoom level
 */
export function shouldRenderElement(
  config: SemanticZoomConfig,
  elementType: string
): boolean {
  if (!config.enabled) {
    return true; // Render everything when disabled
  }

  const activeLevel = getActiveDetailLevel(config, elementType);
  return activeLevel !== null;
}

/**
 * Get all element types visible at current zoom
 */
export function getVisibleElementTypes(config: SemanticZoomConfig): string[] {
  const visible: string[] = [];

  for (const [elementType] of config.lodConfigs) {
    const level = getActiveDetailLevel(config, elementType);
    if (level !== null) {
      visible.push(elementType);
    }
  }

  return visible;
}

/**
 * Update current zoom level
 */
export function updateZoomLevel(
  config: SemanticZoomConfig,
  zoom: number
): SemanticZoomConfig {
  return {
    ...config,
    currentZoom: zoom,
  };
}

/**
 * Create nested scene
 */
export function createNestedScene(
  id: string,
  name: string,
  parentId: string | null,
  data: unknown,
  portalElementId?: string
): NestedScene {
  return {
    id,
    parentId,
    name,
    portalElementId,
    data,
    loadedAt: Date.now(),
    loaded: true,
  };
}

/**
 * Drill down into nested scene
 */
export function drillDown(
  state: DrillDownState,
  scene: NestedScene,
  portalElementId?: string
): DrillDownState {
  // Add to scene stack
  const breadcrumb: BreadcrumbItem = {
    sceneId: scene.id,
    name: scene.name,
    portalElementId,
  };

  // Store scene
  state.scenes.set(scene.id, scene);

  // Add to cache
  state.cache.set(scene.id, scene);

  // Evict old cache entries if needed
  evictCache(state);

  return {
    ...state,
    sceneStack: [...state.sceneStack, breadcrumb],
  };
}

/**
 * Navigate back up the scene hierarchy
 */
export function drillUp(state: DrillDownState): DrillDownState {
  if (state.sceneStack.length <= 1) {
    return state; // Already at root
  }

  return {
    ...state,
    sceneStack: state.sceneStack.slice(0, -1),
  };
}

/**
 * Navigate to specific scene in breadcrumb trail
 */
export function navigateToScene(
  state: DrillDownState,
  sceneId: string
): DrillDownState {
  const index = state.sceneStack.findIndex((item) => item.sceneId === sceneId);

  if (index === -1) {
    return state; // Scene not in stack
  }

  return {
    ...state,
    sceneStack: state.sceneStack.slice(0, index + 1),
  };
}

/**
 * Get current scene
 */
export function getCurrentScene(state: DrillDownState): NestedScene | null {
  if (state.sceneStack.length === 0) {
    return null;
  }

  const current = state.sceneStack[state.sceneStack.length - 1];
  return state.scenes.get(current.sceneId) || null;
}

/**
 * Get parent scene
 */
export function getParentScene(state: DrillDownState): NestedScene | null {
  if (state.sceneStack.length < 2) {
    return null;
  }

  const parent = state.sceneStack[state.sceneStack.length - 2];
  return state.scenes.get(parent.sceneId) || null;
}

/**
 * Get scene by ID
 */
export function getScene(state: DrillDownState, sceneId: string): NestedScene | null {
  return state.scenes.get(sceneId) || null;
}

/**
 * Get breadcrumb trail
 */
export function getBreadcrumbs(state: DrillDownState): BreadcrumbItem[] {
  return [...state.sceneStack];
}

/**
 * Load nested scene (async simulation)
 */
export async function loadNestedScene(
  state: DrillDownState,
  sceneId: string,
  loader: (sceneId: string) => Promise<NestedScene>
): Promise<{ state: DrillDownState; scene: NestedScene }> {
  // Check if already loading
  if (state.loading.has(sceneId)) {
    throw new Error(`Scene ${sceneId} is already loading`);
  }

  // Check cache first
  const cached = state.cache.get(sceneId);
  if (cached) {
    return { state, scene: cached };
  }

  // Mark as loading
  state.loading.add(sceneId);

  try {
    // Load scene
    const scene = await loader(sceneId);

    // Update state
    state.scenes.set(sceneId, scene);
    state.cache.set(sceneId, scene);
    state.loading.delete(sceneId);

    // Evict old cache entries
    evictCache(state);

    return { state, scene };
  } catch (error) {
    state.loading.delete(sceneId);
    throw error;
  }
}

/**
 * Check if scene is loading
 */
export function isSceneLoading(state: DrillDownState, sceneId: string): boolean {
  return state.loading.has(sceneId);
}

/**
 * Check if scene is cached
 */
export function isSceneCached(state: DrillDownState, sceneId: string): boolean {
  return state.cache.has(sceneId);
}

/**
 * Clear scene cache
 */
export function clearCache(state: DrillDownState): DrillDownState {
  return {
    ...state,
    cache: new Map(),
  };
}

/**
 * Evict oldest cache entries when over limit
 */
function evictCache(state: DrillDownState): void {
  if (state.cache.size <= state.maxCacheSize) {
    return;
  }

  // Find oldest entries
  const entries = Array.from(state.cache.entries());
  entries.sort((a, b) => {
    const aTime = a[1].loadedAt || 0;
    const bTime = b[1].loadedAt || 0;
    return aTime - bTime;
  });

  // Remove oldest entries
  const toRemove = entries.slice(0, state.cache.size - state.maxCacheSize);
  for (const [id] of toRemove) {
    state.cache.delete(id);
  }
}

/**
 * Get depth in scene hierarchy
 */
export function getSceneDepth(state: DrillDownState): number {
  return state.sceneStack.length;
}

/**
 * Check if at root scene
 */
export function isAtRoot(state: DrillDownState): boolean {
  return state.sceneStack.length <= 1;
}

/**
 * Reset to root scene
 */
export function resetToRoot(state: DrillDownState): DrillDownState {
  if (state.sceneStack.length === 0) {
    return state;
  }

  return {
    ...state,
    sceneStack: [state.sceneStack[0]],
  };
}

/**
 * Create predefined LOD configuration for common use cases
 */
export function createStandardLODConfig(elementType: string): LODConfig {
  // Standard 3-level LOD: overview, normal, detailed
  return {
    elementType,
    thresholds: [
      {
        minZoom: 0.1,
        maxZoom: 0.5,
        level: 'overview',
        render: true,
      },
      {
        minZoom: 0.5,
        maxZoom: 2.0,
        level: 'normal',
        render: true,
      },
      {
        minZoom: 2.0,
        level: 'detailed',
        render: true,
      },
    ],
    defaultLevel: 'normal',
  };
}

/**
 * Create performance-optimized LOD for complex elements
 */
export function createPerformanceLODConfig(elementType: string): LODConfig {
  // Hide expensive elements at low zoom, show simple at medium, full at high
  return {
    elementType,
    thresholds: [
      {
        minZoom: 0.5,
        maxZoom: 1.5,
        level: 'simple',
        render: true,
      },
      {
        minZoom: 1.5,
        level: 'full',
        render: true,
      },
    ],
    defaultLevel: 'simple',
  };
}

/**
 * Create label visibility LOD
 */
export function createLabelLODConfig(): LODConfig {
  return {
    elementType: 'label',
    thresholds: [
      {
        minZoom: 0.75,
        level: 'visible',
        render: (zoom) => zoom >= 0.75, // Hide labels below 75% zoom
      },
    ],
    // No defaultLevel - returns null when below threshold (hides labels)
  };
}
