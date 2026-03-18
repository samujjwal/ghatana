/**
 * Layer Detection Utility
 *
 * Generic utility for detecting semantic layers based on zoom level.
 * This is canvas-agnostic and can be configured for any layer system.
 *
 * @doc.type utility
 * @doc.purpose Zoom-based semantic layer detection
 * @doc.layer core
 * @doc.pattern Strategy
 */

export interface LayerRange {
  name: string;
  minZoom: number;
  maxZoom: number;
  description?: string;
}

export interface LayerDetectionConfig {
  layers: LayerRange[];
  defaultLayer?: string;
}

/**
 * Default 5-layer configuration
 * Can be overridden by consumers
 */
export const DEFAULT_LAYER_CONFIG: LayerDetectionConfig = {
  layers: [
    {
      name: "architecture",
      minZoom: 0.01,
      maxZoom: 0.5,
      description: "System design, high-level flows",
    },
    {
      name: "design",
      minZoom: 0.5,
      maxZoom: 1.0,
      description: "Component design, wireframes",
    },
    {
      name: "component",
      minZoom: 1.0,
      maxZoom: 2.0,
      description: "Detailed components, interactions",
    },
    {
      name: "implementation",
      minZoom: 2.0,
      maxZoom: 5.0,
      description: "Code, logic, data structures",
    },
    {
      name: "detail",
      minZoom: 5.0,
      maxZoom: 100.0,
      description: "Line-by-line code, debugging",
    },
  ],
  defaultLayer: "component",
};

/**
 * Detect semantic layer from zoom level
 *
 * @param zoom - Current zoom level (1.0 = 100%)
 * @param config - Layer configuration (optional, uses default if not provided)
 * @returns Layer name
 *
 * @example
 * ```typescript
 * const layer = detectLayer(0.25); // "architecture"
 * const layer = detectLayer(1.5);  // "component"
 * const layer = detectLayer(8.0);  // "detail"
 * ```
 */
export function detectLayer(
  zoom: number,
  config: LayerDetectionConfig = DEFAULT_LAYER_CONFIG,
): string {
  // Find the layer that contains this zoom level
  const layer = config.layers.find(
    (l) => zoom >= l.minZoom && zoom < l.maxZoom,
  );

  return layer?.name || config.defaultLayer || config.layers[0].name;
}

/**
 * Get layer information for a given zoom level
 *
 * @param zoom - Current zoom level
 * @param config - Layer configuration
 * @returns Full layer range information
 */
export function getLayerInfo(
  zoom: number,
  config: LayerDetectionConfig = DEFAULT_LAYER_CONFIG,
): LayerRange | undefined {
  return config.layers.find((l) => zoom >= l.minZoom && zoom < l.maxZoom);
}

/**
 * Get all available layers
 *
 * @param config - Layer configuration
 * @returns Array of layer names
 */
export function getAvailableLayers(
  config: LayerDetectionConfig = DEFAULT_LAYER_CONFIG,
): string[] {
  return config.layers.map((l) => l.name);
}

/**
 * Get zoom range for a specific layer
 *
 * @param layerName - Name of the layer
 * @param config - Layer configuration
 * @returns Zoom range [min, max] or undefined if layer not found
 */
export function getLayerZoomRange(
  layerName: string,
  config: LayerDetectionConfig = DEFAULT_LAYER_CONFIG,
): [number, number] | undefined {
  const layer = config.layers.find((l) => l.name === layerName);
  return layer ? [layer.minZoom, layer.maxZoom] : undefined;
}

/**
 * Get semantic zoom level name (human-readable)
 *
 * @param zoom - Current zoom level
 * @param config - Layer configuration
 * @returns Human-readable zoom level name
 *
 * @example
 * ```typescript
 * getSemanticZoomLevel(0.25); // "Architecture View"
 * getSemanticZoomLevel(1.5);  // "Component View"
 * ```
 */
export function getSemanticZoomLevel(
  zoom: number,
  config: LayerDetectionConfig = DEFAULT_LAYER_CONFIG,
): string {
  const layer = getLayerInfo(zoom, config);
  if (!layer) return "Unknown View";

  // Convert layer name to title case with "View" suffix
  return layer.name.charAt(0).toUpperCase() + layer.name.slice(1) + " View";
}

/**
 * Check if zoom level is within a specific layer
 *
 * @param zoom - Current zoom level
 * @param layerName - Layer name to check
 * @param config - Layer configuration
 * @returns True if zoom is within the specified layer
 */
export function isInLayer(
  zoom: number,
  layerName: string,
  config: LayerDetectionConfig = DEFAULT_LAYER_CONFIG,
): boolean {
  return detectLayer(zoom, config) === layerName;
}

/**
 * Get next layer (zoom in)
 *
 * @param currentLayer - Current layer name
 * @param config - Layer configuration
 * @returns Next layer name or undefined if at max zoom
 */
export function getNextLayer(
  currentLayer: string,
  config: LayerDetectionConfig = DEFAULT_LAYER_CONFIG,
): string | undefined {
  const currentIndex = config.layers.findIndex((l) => l.name === currentLayer);
  if (currentIndex === -1 || currentIndex === config.layers.length - 1) {
    return undefined;
  }
  return config.layers[currentIndex + 1].name;
}

/**
 * Get previous layer (zoom out)
 *
 * @param currentLayer - Current layer name
 * @param config - Layer configuration
 * @returns Previous layer name or undefined if at min zoom
 */
export function getPreviousLayer(
  currentLayer: string,
  config: LayerDetectionConfig = DEFAULT_LAYER_CONFIG,
): string | undefined {
  const currentIndex = config.layers.findIndex((l) => l.name === currentLayer);
  if (currentIndex <= 0) {
    return undefined;
  }
  return config.layers[currentIndex - 1].name;
}

/**
 * Get suggested zoom level for a layer (midpoint of range)
 *
 * @param layerName - Layer name
 * @param config - Layer configuration
 * @returns Suggested zoom level or undefined if layer not found
 */
export function getSuggestedZoomForLayer(
  layerName: string,
  config: LayerDetectionConfig = DEFAULT_LAYER_CONFIG,
): number | undefined {
  const layer = config.layers.find((l) => l.name === layerName);
  if (!layer) return undefined;

  // Return midpoint of the range (geometric mean for better visual balance)
  return Math.sqrt(layer.minZoom * layer.maxZoom);
}
