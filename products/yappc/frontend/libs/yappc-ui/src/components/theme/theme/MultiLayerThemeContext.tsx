import React, { createContext, useContext, useMemo, useState } from 'react';

type PaletteMode = 'light' | 'dark';
type ThemeOptions = Record<string, unknown>;

export interface ThemeLayer {
  id: string;
  name: string;
  description?: string;
  options: Partial<ThemeOptions>;
  priority: number;
}

export interface MultiLayerThemeContextValue {
  mode: PaletteMode;
  setMode: (mode: PaletteMode) => void;
  layers: ThemeLayer[];
  addLayer: (layer: ThemeLayer) => void;
  removeLayer: (layerId: string) => void;
  updateLayer: (layerId: string, options: Partial<ThemeOptions>) => void;
  mergedThemeOptions: ThemeOptions;
  baseLayer?: ThemeLayer;
  brandLayer?: ThemeLayer;
  workspaceLayer?: ThemeLayer;
  appLayer?: ThemeLayer;
  setBrandLayer: (options: Partial<ThemeOptions>) => void;
  setWorkspaceLayer: (options: Partial<ThemeOptions>) => void;
  setAppLayer: (options: Partial<ThemeOptions>) => void;
}

export const LayerPriority = {
  BASE: 0,
  BRAND: 100,
  WORKSPACE: 200,
  APP: 300,
  OVERRIDE: 1000,
} as const;

const MultiLayerThemeContext = createContext<
  MultiLayerThemeContextValue | undefined
>(undefined);

function deepMergeThemeOptions(
  ...options: Partial<ThemeOptions>[]
): ThemeOptions {
  const result: Record<string, unknown> = {};

  for (const option of options) {
    if (!option) continue;

    for (const [key, value] of Object.entries(option)) {
      if (value === undefined) continue;

      if (
        typeof value === 'object' &&
        !Array.isArray(value) &&
        value !== null
      ) {
        result[key] = deepMergeThemeOptions(
          result[key] || {},
          value as Partial<ThemeOptions>
        );
      } else {
        result[key] = value;
      }
    }
  }

  return result as ThemeOptions;
}

export interface MultiLayerThemeProviderProps {
  children: React.ReactNode;
  initialMode?: PaletteMode;
  baseThemeOptions?: ThemeOptions;
  brandThemeOptions?: Partial<ThemeOptions>;
  workspaceThemeOptions?: Partial<ThemeOptions>;
  appThemeOptions?: Partial<ThemeOptions>;
}

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

  const addLayer = (layer: ThemeLayer) => {
    setLayers((previous) =>
      [...previous.filter((existing) => existing.id !== layer.id), layer].sort(
        (left, right) => left.priority - right.priority
      )
    );
  };

  const removeLayer = (layerId: string) => {
    setLayers((previous) => previous.filter((layer) => layer.id !== layerId));
  };

  const updateLayer = (layerId: string, options: Partial<ThemeOptions>) => {
    setLayers((previous) =>
      previous.map((layer) =>
        layer.id === layerId
          ? { ...layer, options: deepMergeThemeOptions(layer.options, options) }
          : layer
      )
    );
  };

  const setBrandLayer = (options: Partial<ThemeOptions>) => {
    updateLayer('brand', options);
  };

  const setWorkspaceLayer = (options: Partial<ThemeOptions>) => {
    updateLayer('workspace', options);
  };

  const setAppLayer = (options: Partial<ThemeOptions>) => {
    updateLayer('app', options);
  };

  const mergedThemeOptions = useMemo(() => {
    const sortedLayers = [...layers].sort(
      (left, right) => left.priority - right.priority
    );
    const layerOptions = sortedLayers.map((layer) => layer.options);
    return deepMergeThemeOptions({ palette: { mode } }, ...layerOptions);
  }, [layers, mode]);

  const baseLayer = layers.find((layer) => layer.id === 'base');
  const brandLayer = layers.find((layer) => layer.id === 'brand');
  const workspaceLayer = layers.find((layer) => layer.id === 'workspace');
  const appLayer = layers.find((layer) => layer.id === 'app');

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

export function useMultiLayerTheme(): MultiLayerThemeContextValue {
  const context = useContext(MultiLayerThemeContext);
  if (!context) {
    throw new Error(
      'useMultiLayerTheme must be used within MultiLayerThemeProvider'
    );
  }
  return context;
}

export function useThemeMode() {
  const { mode, setMode } = useMultiLayerTheme();
  return { mode, setMode };
}

export function useBrandTheme() {
  const { brandLayer, setBrandLayer } = useMultiLayerTheme();
  return { brandLayer, setBrandLayer };
}

export function useWorkspaceTheme() {
  const { workspaceLayer, setWorkspaceLayer } = useMultiLayerTheme();
  return { workspaceLayer, setWorkspaceLayer };
}

export function useAppTheme() {
  const { appLayer, setAppLayer } = useMultiLayerTheme();
  return { appLayer, setAppLayer };
}
