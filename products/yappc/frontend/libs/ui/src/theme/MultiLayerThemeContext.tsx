/**
 * Multi-Layer Theme Context
 *
 * Implements a composable, multi-layer theming system:
 * - Base Layer: Foundation tokens (colors, typography, spacing)
 * - Brand Layer: Brand-specific customizations
 * - Workspace Layer: Workspace-level preferences
 * - App Layer: Application-specific overrides
 *
 * Each layer can override tokens from previous layers, creating
 * a flexible and maintainable theming hierarchy.
 */

import React, { createContext, useContext, useMemo, useState } from 'react';

/** Color mode — replaces @mui/material PaletteMode */
type PaletteMode = 'light' | 'dark';

/** Theme options — replaces @mui/material/styles ThemeOptions */
type ThemeOptions = Record<string, unknown>;

/**
 * Theme layer interface
 */
export interface ThemeLayer {
  id: string;
  name: string;
  description?: string;
  options: Partial<ThemeOptions>;
  priority: number; // Higher priority = applied later
}

/**
 * Theme context value
 */
export interface MultiLayerThemeContextValue {
  // Current mode
  mode: PaletteMode;
  setMode: (mode: PaletteMode) => void;

  // Layer management
  layers: ThemeLayer[];
  addLayer: (layer: ThemeLayer) => void;
  removeLayer: (layerId: string) => void;
  updateLayer: (layerId: string, options: Partial<ThemeOptions>) => void;

  // Merged theme options
  mergedThemeOptions: ThemeOptions;

  // Quick access to specific layers
  baseLayer?: ThemeLayer;
  brandLayer?: ThemeLayer;
  workspaceLayer?: ThemeLayer;
  appLayer?: ThemeLayer;

  // Layer setters
  setBrandLayer: (options: Partial<ThemeOptions>) => void;
  setWorkspaceLayer: (options: Partial<ThemeOptions>) => void;
  setAppLayer: (options: Partial<ThemeOptions>) => void;
}

/**
 * Default base layer priorities
 */
export const LayerPriority = {
  BASE: 0,
  BRAND: 100,
  WORKSPACE: 200,
  APP: 300,
  OVERRIDE: 1000,
} as const;

/**
 * Create theme context
 */
const MultiLayerThemeContext = createContext<MultiLayerThemeContextValue | undefined>(undefined);

/**
 * Deep merge theme options
 * Handles nested objects like palette, typography, etc.
 */
function deepMergeThemeOptions(...options: Partial<ThemeOptions>[]): ThemeOptions {
  const result: Record<string, unknown> = {};

  for (const option of options) {
    if (!option) continue;

    for (const [key, value] of Object.entries(option)) {
      if (value === undefined) continue;

      if (typeof value === 'object' && !Array.isArray(value) && value !== null) {
        // Recursively merge objects
        result[key] = deepMergeThemeOptions(result[key] || {}, value as Partial<ThemeOptions>);
      } else {
        // Overwrite primitive values and arrays
        result[key] = value;
      }
    }
  }

  return result as ThemeOptions;
}

/**
 * Multi-layer theme provider props
 */
export interface MultiLayerThemeProviderProps {
  children: React.ReactNode;
  initialMode?: PaletteMode;
  baseThemeOptions?: ThemeOptions;
  brandThemeOptions?: Partial<ThemeOptions>;
  workspaceThemeOptions?: Partial<ThemeOptions>;
  appThemeOptions?: Partial<ThemeOptions>;
}

/**
 * Multi-layer theme provider
 *
 * Usage:
 * ```tsx
 * <MultiLayerThemeProvider
 *   baseThemeOptions={baseTheme}
 *   brandThemeOptions={{ palette: { primary: { main: '#ff0000' } } }}
 *   workspaceThemeOptions={{ typography: { fontFamily: 'Roboto' } }}
 * >
 *   <App />
 * </MultiLayerThemeProvider>
 * ```
 */
export function MultiLayerThemeProvider({
  children,
  initialMode = 'light',
  baseThemeOptions = {},
  brandThemeOptions,
  workspaceThemeOptions,
  appThemeOptions,
}: MultiLayerThemeProviderProps) {
  const [mode, setMode] = useState<PaletteMode>(initialMode);
  const [layers, setLayers] = useState<ThemeLayer[]>(() => {
    const initialLayers: ThemeLayer[] = [
      {
        id: 'base',
        name: 'Base',
        description: 'Foundation theme tokens',
        options: baseThemeOptions,
        priority: LayerPriority.BASE,
      },
    ];

    if (brandThemeOptions) {
      initialLayers.push({
        id: 'brand',
        name: 'Brand',
        description: 'Brand-specific customizations',
        options: brandThemeOptions,
        priority: LayerPriority.BRAND,
      });
    }

    if (workspaceThemeOptions) {
      initialLayers.push({
        id: 'workspace',
        name: 'Workspace',
        description: 'Workspace preferences',
        options: workspaceThemeOptions,
        priority: LayerPriority.WORKSPACE,
      });
    }

    if (appThemeOptions) {
      initialLayers.push({
        id: 'app',
        name: 'Application',
        description: 'App-specific overrides',
        options: appThemeOptions,
        priority: LayerPriority.APP,
      });
    }

    return initialLayers;
  });

  // Layer management functions
  const addLayer = (layer: ThemeLayer) => {
    setLayers((prev) => [...prev.filter((l) => l.id !== layer.id), layer].sort((a, b) => a.priority - b.priority));
  };

  const removeLayer = (layerId: string) => {
    setLayers((prev) => prev.filter((l) => l.id !== layerId));
  };

  const updateLayer = (layerId: string, options: Partial<ThemeOptions>) => {
    setLayers((prev) =>
      prev.map((layer) =>
        layer.id === layerId ? { ...layer, options: deepMergeThemeOptions(layer.options, options) } : layer
      )
    );
  };

  // Quick layer setters
  const setBrandLayer = (options: Partial<ThemeOptions>) => {
    updateLayer('brand', options);
  };

  const setWorkspaceLayer = (options: Partial<ThemeOptions>) => {
    updateLayer('workspace', options);
  };

  const setAppLayer = (options: Partial<ThemeOptions>) => {
    updateLayer('app', options);
  };

  // Merge all layers
  const mergedThemeOptions = useMemo(() => {
    const sortedLayers = [...layers].sort((a, b) => a.priority - b.priority);
    const layerOptions = sortedLayers.map((l) => l.options);

    // Include mode in the merged options
    return deepMergeThemeOptions({ palette: { mode } }, ...layerOptions);
  }, [layers, mode]);

  // Quick access to specific layers
  const baseLayer = layers.find((l) => l.id === 'base');
  const brandLayer = layers.find((l) => l.id === 'brand');
  const workspaceLayer = layers.find((l) => l.id === 'workspace');
  const appLayer = layers.find((l) => l.id === 'app');

  const value: MultiLayerThemeContextValue = {
    mode,
    setMode,
    layers,
    addLayer,
    removeLayer,
    updateLayer,
    mergedThemeOptions,
    baseLayer,
    brandLayer,
    workspaceLayer,
    appLayer,
    setBrandLayer,
    setWorkspaceLayer,
    setAppLayer,
  };

  return (
    <MultiLayerThemeContext.Provider value={value}>
      {children}
    </MultiLayerThemeContext.Provider>
  );
}

/**
 * Hook to access multi-layer theme context
 */
export function useMultiLayerTheme(): MultiLayerThemeContextValue {
  const context = useContext(MultiLayerThemeContext);
  if (!context) {
    throw new Error('useMultiLayerTheme must be used within MultiLayerThemeProvider');
  }
  return context;
}

/**
 * Hook for theme mode (light/dark)
 */
export function useThemeMode() {
  const { mode, setMode } = useMultiLayerTheme();
  return { mode, setMode };
}

/**
 * Hook for brand layer customization
 */
export function useBrandTheme() {
  const { brandLayer, setBrandLayer } = useMultiLayerTheme();
  return { brandLayer, setBrandLayer };
}

/**
 * Hook for workspace layer customization
 */
export function useWorkspaceTheme() {
  const { workspaceLayer, setWorkspaceLayer } = useMultiLayerTheme();
  return { workspaceLayer, setWorkspaceLayer };
}

/**
 * Hook for app layer customization
 */
export function useAppTheme() {
  const { appLayer, setAppLayer } = useMultiLayerTheme();
  return { appLayer, setAppLayer };
}
