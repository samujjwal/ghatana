/**
 * @doc.type hook
 * @doc.purpose Manages canvas performance monitoring and metrics
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useEffect } from 'react';
import type { CanvasState, CanvasElement } from '@/components/canvas/workspace/canvasAtoms';

interface PerformanceMetrics {
  fps: number;
  elements: number;
  frameTime: number;
  renderTime: number;
}

interface UseCanvasPerformanceOptions {
  elementCount: number;
  setGlobalCanvas: (updater: (prev: CanvasState) => CanvasState) => void;
}

/**
 * Hook to manage canvas performance monitoring
 */
export function useCanvasPerformance({
  elementCount,
  setGlobalCanvas,
}: UseCanvasPerformanceOptions) {
  const [performancePanelOpen, setPerformancePanelOpen] = useState(false);
  const [performanceEnabled, setPerformanceEnabled] = useState(false);
  const [performanceMetrics, setPerformanceMetrics] = useState<PerformanceMetrics>({
    fps: 60,
    elements: elementCount,
    frameTime: 16,
    renderTime: 8,
  });

  const handleEnablePerformance = useCallback(() => {
    setPerformanceEnabled(true);
    setPerformanceMetrics({
      fps: 58,
      elements: elementCount,
      frameTime: 17,
      renderTime: 9,
    });
  }, [elementCount]);

  const seedPerformanceScenario = useCallback(() => {
    setGlobalCanvas((prev: CanvasState) => {
      const elements: CanvasElement[] = Array.from({ length: 100 }, (_, index) => {
        const row = Math.floor(index / 10);
        const column = index % 10;
        return {
          id: `performance-node-${index}`,
          kind: 'component' as const,
          type: index % 3 === 0 ? 'component' : index % 3 === 1 ? 'api' : 'data',
          position: {
            x: 120 + column * 180,
            y: 120 + row * 140,
          },
          data: { label: `Node ${index}` },
        };
      });

      const connections = elements.slice(1).map((element, index) => ({
        id: `performance-conn-${index}`,
        source: elements[index].id,
        target: element.id,
        animated: index % 2 === 0,
      }));

      return {
        ...prev,
        elements,
        connections,
      };
    });
  }, [setGlobalCanvas]);

  // Update element count in metrics when elements change
  useEffect(() => {
    if (performanceEnabled) {
      setPerformanceMetrics((prev) => ({
        ...prev,
        elements: elementCount,
      }));
    }
  }, [performanceEnabled, elementCount]);

  return {
    performancePanelOpen,
    setPerformancePanelOpen,
    performanceEnabled,
    setPerformanceEnabled,
    performanceMetrics,
    handleEnablePerformance,
    seedPerformanceScenario,
  };
}
