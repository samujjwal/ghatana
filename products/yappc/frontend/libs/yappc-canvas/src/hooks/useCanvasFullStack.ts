/**
 * Consolidated Canvas Full-Stack Hook
 * 
 * Replaces: useFullStackMode + full-stack features
 * Provides: Full-stack development mode
 */

import { useCallback, useState } from 'react';

export type FullStackMode = 'frontend' | 'backend' | 'database' | 'split';

export interface Layer {
  id: string;
  name: string;
  type: 'frontend' | 'backend' | 'database';
  visible: boolean;
}

export interface UseCanvasFullStackOptions {
  canvasId: string;
  stack?: 'frontend' | 'backend' | 'database' | 'all';
}

export interface UseCanvasFullStackReturn {
  mode: FullStackMode;
  setMode: (mode: FullStackMode) => void;
  
  layers: Layer[];
  activeLayer: Layer | null;
  setActiveLayer: (layerId: string) => void;
  toggleLayerVisibility: (layerId: string) => void;
  
  isSplitView: boolean;
  splitRatio: number;
  setSplitRatio: (ratio: number) => void;
}

export function useCanvasFullStack(
  options: UseCanvasFullStackOptions
): UseCanvasFullStackReturn {
  const { canvasId, stack = 'all' } = options;

  const [mode, setMode] = useState<FullStackMode>('split');
  const [layers] = useState<Layer[]>([
    { id: 'frontend', name: 'Frontend', type: 'frontend', visible: true },
    { id: 'backend', name: 'Backend', type: 'backend', visible: true },
    { id: 'database', name: 'Database', type: 'database', visible: true },
  ]);
  const [activeLayer, setActiveLayerState] = useState<Layer | null>(layers[0]);
  const [splitRatio, setSplitRatio] = useState(0.5);

  const isSplitView = mode === 'split';

  const setActiveLayer = useCallback(
    (layerId: string) => {
      const layer = layers.find(l => l.id === layerId);
      if (layer) setActiveLayerState(layer);
    },
    [layers]
  );

  const toggleLayerVisibility = useCallback((layerId: string) => {
    // Toggle layer visibility
  }, []);

  return {
    mode,
    setMode,
    layers,
    activeLayer,
    setActiveLayer,
    toggleLayerVisibility,
    isSplitView,
    splitRatio,
    setSplitRatio,
  };
}
