/**
 * Level of Detail (LOD) System
 * 
 * Implements zoom-based rendering optimizations where distant/zoomed-out
 * elements are simplified or replaced with glyphs to maintain 60fps performance.
 * 
 * Features:
 * - Zoom threshold-based detail levels
 * - Glyph rendering for simplified elements
 * - Progressive rendering strategies
 * - Memory-efficient LOD switching
 * 
 * @module rendering/lodSystem
 */

import type { CanvasElement } from '../types/canvas-document';

/**
 * Level of detail enum
 */
export enum LODLevel {
  /** Full detail - render all features */
  FULL = 'full',
  /** Medium detail - simplify some features */
  MEDIUM = 'medium',
  /** Low detail - minimal representation */
  LOW = 'low',
  /** Glyph only - simple shape/icon */
  GLYPH = 'glyph',
  /** Hidden - don't render at all */
  HIDDEN = 'hidden',
}

/**
 * LOD configuration for zoom thresholds
 */
export interface LODConfig {
  /** Zoom level for full detail (e.g., 1.0) */
  fullDetailThreshold: number;
  /** Zoom level for medium detail (e.g., 0.5) */
  mediumDetailThreshold: number;
  /** Zoom level for low detail (e.g., 0.25) */
  lowDetailThreshold: number;
  /** Zoom level for glyph only (e.g., 0.1) */
  glyphThreshold: number;
  /** Minimum zoom to show anything */
  hideThreshold: number;
}

/**
 * LOD rendering instructions for an element
 */
export interface LODRenderInstruction {
  elementId: string;
  level: LODLevel;
  /** Whether to render text labels */
  showLabels: boolean;
  /** Whether to render shadows/effects */
  showEffects: boolean;
  /** Whether to render detailed content */
  showContent: boolean;
  /** Glyph size if using glyph rendering */
  glyphSize?: number;
  /** Simplified geometry if applicable */
  simplifiedGeometry?: unknown;
}

/**
 * Element type LOD configuration
 */
export interface ElementTypeLODConfig {
  type: string;
  config: LODConfig;
  /** Custom glyph renderer */
  glyphRenderer?: (element: CanvasElement) => string;
}

/**
 * Default LOD configuration
 */
export const DEFAULT_LOD_CONFIG: LODConfig = {
  fullDetailThreshold: 0.75, // Above 75% zoom = full detail
  mediumDetailThreshold: 0.4, // 40-75% zoom = medium detail
  lowDetailThreshold: 0.2, // 20-40% zoom = low detail
  glyphThreshold: 0.1, // 10-20% zoom = glyph only
  hideThreshold: 0.05, // Below 5% zoom = hidden
};

/**
 * Aggressive LOD config for performance mode
 */
export const PERFORMANCE_LOD_CONFIG: LODConfig = {
  fullDetailThreshold: 1.0, // Only 100% zoom = full detail
  mediumDetailThreshold: 0.6,
  lowDetailThreshold: 0.3,
  glyphThreshold: 0.15,
  hideThreshold: 0.08,
};

/**
 * Lenient LOD config for quality mode
 */
export const QUALITY_LOD_CONFIG: LODConfig = {
  fullDetailThreshold: 0.5, // More zoom levels show full detail
  mediumDetailThreshold: 0.25,
  lowDetailThreshold: 0.1,
  glyphThreshold: 0.05,
  hideThreshold: 0.02,
};

/**
 * Create an LOD system manager
 * 
 * @example
 * ```ts
 * const lod = createLODSystem({
 *   fullDetailThreshold: 0.8,
 * });
 * 
 * // Get rendering instructions for current zoom
 * const instructions = lod.getLODInstructions(elements, 0.5);
 * 
 * // Determine LOD level for specific zoom
 * const level = lod.getLODLevel(0.3); // => LODLevel.LOW
 * ```
 */
export function createLODSystem(config: Partial<LODConfig> = {}) {
  const cfg = { ...DEFAULT_LOD_CONFIG, ...config };
  
  // Type-specific configs
  const typeConfigs = new Map<string, ElementTypeLODConfig>();

  /**
   * Register type-specific LOD configuration
   */
  function registerTypeConfig(typeConfig: ElementTypeLODConfig): void {
    typeConfigs.set(typeConfig.type, typeConfig);
  }

  /**
   * Get LOD level for a given zoom level
   */
  function getLODLevel(zoom: number): LODLevel {
    if (zoom < cfg.hideThreshold) return LODLevel.HIDDEN;
    if (zoom < cfg.glyphThreshold) return LODLevel.GLYPH;
    if (zoom < cfg.lowDetailThreshold) return LODLevel.LOW;
    if (zoom < cfg.mediumDetailThreshold) return LODLevel.LOW;
    if (zoom < cfg.fullDetailThreshold) return LODLevel.MEDIUM;
    return LODLevel.FULL;
  }

  /**
   * Get LOD rendering instructions for elements
   */
  function getLODInstructions(
    elements: CanvasElement[],
    zoom: number
  ): LODRenderInstruction[] {
    return elements.map(element => {
      const typeConfig = typeConfigs.get(element.type);
      const elementConfig = typeConfig?.config || cfg;
      
      const level = getLODLevelForElement(element, zoom, elementConfig);
      
      return {
        elementId: element.id,
        level,
        showLabels: shouldShowLabels(level, zoom),
        showEffects: shouldShowEffects(level),
        showContent: shouldShowContent(level),
        glyphSize: level === LODLevel.GLYPH ? calculateGlyphSize(element, zoom) : undefined,
      };
    });
  }

  /**
   * Get LOD level for specific element
   */
  function getLODLevelForElement(
    element: CanvasElement,
    zoom: number,
    elementConfig: LODConfig = cfg
  ): LODLevel {
    if (zoom < elementConfig.hideThreshold) return LODLevel.HIDDEN;
    if (zoom < elementConfig.glyphThreshold) return LODLevel.GLYPH;
    if (zoom < elementConfig.lowDetailThreshold) return LODLevel.LOW;
    if (zoom < elementConfig.mediumDetailThreshold) return LODLevel.LOW;
    if (zoom < elementConfig.fullDetailThreshold) return LODLevel.MEDIUM;
    return LODLevel.FULL;
  }

  /**
   * Check if labels should be shown
   */
  function shouldShowLabels(level: LODLevel, zoom: number): boolean {
    if (level === LODLevel.HIDDEN) return false;
    if (level === LODLevel.GLYPH) return zoom > cfg.glyphThreshold * 1.5;
    return true;
  }

  /**
   * Check if effects should be shown
   */
  function shouldShowEffects(level: LODLevel): boolean {
    return level === LODLevel.FULL;
  }

  /**
   * Check if content should be shown
   */
  function shouldShowContent(level: LODLevel): boolean {
    return level === LODLevel.FULL || level === LODLevel.MEDIUM;
  }

  /**
   * Calculate appropriate glyph size
   */
  function calculateGlyphSize(element: CanvasElement, zoom: number): number {
    const baseSize = Math.min(element.bounds.width, element.bounds.height);
    return Math.max(8, baseSize * zoom);
  }

  /**
   * Get render quality multiplier (0-1)
   */
  function getQualityMultiplier(zoom: number): number {
    if (zoom >= cfg.fullDetailThreshold) return 1.0;
    if (zoom >= cfg.mediumDetailThreshold) return 0.7;
    if (zoom >= cfg.lowDetailThreshold) return 0.4;
    if (zoom >= cfg.glyphThreshold) return 0.2;
    return 0.1;
  }

  /**
   * Filter elements by LOD level
   */
  function filterByLOD(
    elements: CanvasElement[],
    zoom: number,
    minLevel: LODLevel = LODLevel.GLYPH
  ): CanvasElement[] {
    const minLevelValue = getLODLevelValue(minLevel);
    
    return elements.filter(element => {
      const level = getLODLevelForElement(element, zoom);
      return getLODLevelValue(level) >= minLevelValue;
    });
  }

  /**
   * Get numeric value for LOD level (for comparison)
   */
  function getLODLevelValue(level: LODLevel): number {
    switch (level) {
      case LODLevel.HIDDEN: return 0;
      case LODLevel.GLYPH: return 1;
      case LODLevel.LOW: return 2;
      case LODLevel.MEDIUM: return 3;
      case LODLevel.FULL: return 4;
      default: return 0;
    }
  }

  /**
   * Check if element should be rendered
   */
  function shouldRenderElement(element: CanvasElement, zoom: number): boolean {
    const level = getLODLevelForElement(element, zoom);
    return level !== LODLevel.HIDDEN;
  }

  /**
   * Get simplified geometry for element
   */
  function getSimplifiedGeometry(
    element: CanvasElement,
    level: LODLevel
  ): unknown {
    if (level === LODLevel.FULL) return null; // Use original geometry
    
    // Return simplified representations
    switch (level) {
      case LODLevel.GLYPH:
        return {
          type: 'rect',
          width: element.bounds.width,
          height: element.bounds.height,
        };
      case LODLevel.LOW:
      case LODLevel.MEDIUM:
        return {
          type: 'simplified',
          // Would contain simplified path data
        };
      default:
        return null;
    }
  }

  return {
    getLODLevel,
    getLODInstructions,
    getLODLevelForElement,
    shouldShowLabels,
    shouldShowEffects,
    shouldShowContent,
    getQualityMultiplier,
    filterByLOD,
    shouldRenderElement,
    getSimplifiedGeometry,
    registerTypeConfig,
    config: cfg,
  };
}

/**
 * Glyph rendering utilities
 */
export const GlyphRenderers = {
  /**
   * Render element as simple rectangle
   */
  rectangle(element: CanvasElement): string {
    const { width, height } = element.bounds;
    return `<rect width="${width}" height="${height}" fill="currentColor" opacity="0.5" />`;
  },

  /**
   * Render element as circle
   */
  circle(element: CanvasElement): string {
    const size = Math.min(element.bounds.width, element.bounds.height);
    const radius = size / 2;
    return `<circle r="${radius}" fill="currentColor" opacity="0.5" />`;
  },

  /**
   * Render element as icon placeholder
   */
  icon(element: CanvasElement, iconName?: string): string {
    const size = Math.min(element.bounds.width, element.bounds.height);
    return `<rect width="${size}" height="${size}" fill="currentColor" opacity="0.3" rx="4" />`;
  },

  /**
   * Render element as text label
   */
  label(element: CanvasElement, text: string): string {
    return `<text x="50%" y="50%" text-anchor="middle" dominant-baseline="middle" opacity="0.7">${text}</text>`;
  },
};

/**
 * Progressive rendering utilities
 */
export const ProgressiveRendering = {
  /**
   * Create rendering batches based on priority
   */
  createBatches(
    elements: CanvasElement[],
    zoom: number,
    batchSize: number = 50
  ): CanvasElement[][] {
    const lod = createLODSystem();
    
    // Sort by LOD priority (full detail first, then medium, etc.)
    const levelPriority: Record<LODLevel, number> = {
      [LODLevel.FULL]: 4,
      [LODLevel.MEDIUM]: 3,
      [LODLevel.LOW]: 2,
      [LODLevel.GLYPH]: 1,
      [LODLevel.HIDDEN]: 0,
    };

    const sorted = [...elements].sort((a, b) => {
      const levelA = lod.getLODLevelForElement(a, zoom);
      const levelB = lod.getLODLevelForElement(b, zoom);
      return levelPriority[levelB] - levelPriority[levelA];
    });

    // Split into batches
    const batches: CanvasElement[][] = [];
    for (let i = 0; i < sorted.length; i += batchSize) {
      batches.push(sorted.slice(i, i + batchSize));
    }

    return batches;
  },

  /**
   * Determine optimal batch size based on performance
   */
  calculateOptimalBatchSize(
    elementCount: number,
    targetFrameTime: number = 16 // 60fps
  ): number {
    // Estimate: ~0.1ms per element to render
    const elementsPerFrame = targetFrameTime / 0.1;
    return Math.max(10, Math.min(100, Math.floor(elementsPerFrame)));
  },

  /**
   * Calculate progressive rendering schedule
   */
  createRenderSchedule(
    batches: CanvasElement[][],
    frameTime: number = 16
  ): number[] {
    // Return delays for each batch
    return batches.map((_, index) => index * frameTime);
  },
};

/**
 * LOD transition utilities
 */
export const LODTransitions = {
  /**
   * Calculate smooth transition between LOD levels
   */
  calculateTransitionOpacity(
    zoom: number,
    currentLevel: LODLevel,
    nextLevel: LODLevel,
    threshold: number
  ): number {
    const transitionZone = 0.05; // 5% transition zone
    const distance = Math.abs(zoom - threshold);
    
    if (distance > transitionZone) return 1.0;
    
    return distance / transitionZone;
  },

  /**
   * Check if in transition zone
   */
  isInTransitionZone(zoom: number, thresholds: number[]): boolean {
    const transitionZone = 0.05;
    return thresholds.some(threshold =>
      Math.abs(zoom - threshold) < transitionZone
    );
  },

  /**
   * Get blend factor between two LOD levels
   */
  getBlendFactor(zoom: number, lowerThreshold: number, upperThreshold: number): number {
    if (zoom <= lowerThreshold) return 0;
    if (zoom >= upperThreshold) return 1;
    
    const range = upperThreshold - lowerThreshold;
    return (zoom - lowerThreshold) / range;
  },
};

/**
 * Performance monitoring for LOD system
 */
export interface LODPerformanceMetrics {
  /** Average render time per frame (ms) */
  avgRenderTime: number;
  /** Number of elements rendered */
  renderedCount: number;
  /** Number of elements culled by LOD */
  culledCount: number;
  /** Current FPS */
  fps: number;
  /** LOD distribution */
  lodDistribution: Record<LODLevel, number>;
}

/**
 * Create LOD performance monitor
 */
export function createLODPerformanceMonitor() {
  const frameTimes: number[] = [];
  const maxSamples = 60;
  
  let lastFrameTime = performance.now();
  let metrics: LODPerformanceMetrics = {
    avgRenderTime: 0,
    renderedCount: 0,
    culledCount: 0,
    fps: 60,
    lodDistribution: {
      [LODLevel.FULL]: 0,
      [LODLevel.MEDIUM]: 0,
      [LODLevel.LOW]: 0,
      [LODLevel.GLYPH]: 0,
      [LODLevel.HIDDEN]: 0,
    },
  };

  /**
   *
   */
  function recordFrame(renderTime: number, instructions: LODRenderInstruction[]): void {
    frameTimes.push(renderTime);
    if (frameTimes.length > maxSamples) {
      frameTimes.shift();
    }

    const now = performance.now();
    const deltaTime = now - lastFrameTime;
    lastFrameTime = now;

    metrics.avgRenderTime = frameTimes.reduce((a, b) => a + b, 0) / frameTimes.length;
    metrics.fps = 1000 / deltaTime;
    metrics.renderedCount = instructions.filter(i => i.level !== LODLevel.HIDDEN).length;
    metrics.culledCount = instructions.filter(i => i.level === LODLevel.HIDDEN).length;

    // Calculate distribution
    const distribution: Record<LODLevel, number> = {
      [LODLevel.FULL]: 0,
      [LODLevel.MEDIUM]: 0,
      [LODLevel.LOW]: 0,
      [LODLevel.GLYPH]: 0,
      [LODLevel.HIDDEN]: 0,
    };

    for (const instr of instructions) {
      distribution[instr.level]++;
    }

    metrics.lodDistribution = distribution;
  }

  /**
   *
   */
  function getMetrics(): LODPerformanceMetrics {
    return { ...metrics };
  }

  /**
   *
   */
  function reset(): void {
    frameTimes.length = 0;
    lastFrameTime = performance.now();
    metrics = {
      avgRenderTime: 0,
      renderedCount: 0,
      culledCount: 0,
      fps: 60,
      lodDistribution: {
        [LODLevel.FULL]: 0,
        [LODLevel.MEDIUM]: 0,
        [LODLevel.LOW]: 0,
        [LODLevel.GLYPH]: 0,
        [LODLevel.HIDDEN]: 0,
      },
    };
  }

  return {
    recordFrame,
    getMetrics,
    reset,
  };
}

/**
 * Type definitions for LOD system
 */
export type LODSystemInstance = ReturnType<typeof createLODSystem>;
/**
 *
 */
export type LODPerformanceMonitor = ReturnType<typeof createLODPerformanceMonitor>;
