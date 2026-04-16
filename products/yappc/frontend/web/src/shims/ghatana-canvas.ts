/**
 * Ghatana Canvas Shim
 * Stub implementation for canvas package imports
 */
import type { ReactNode } from 'react';

export const Canvas = () => null;
export const CanvasProvider = ({ children }: { children: ReactNode }) => children;
export const useCanvas = () => ({});
export const CanvasManager = () => null;
export const ReactComponentRenderer = () => null;

// Layout components
export const CanvasChromeLayout = ({ children }: { children: ReactNode }) => children;

// Hooks
export const useCanvasCommands = () => ({
  zoomIn: () => {},
  zoomOut: () => {},
  fitView: () => {},
  centerView: () => {},
});
export const useCanvasTelemetry = () => ({
  trackEvent: () => {},
  trackError: () => {},
});

// Atoms/state
export const chromeCalmModeAtom = { init: false };
export const chromeInspectorVisibleAtom = { init: false };
export const chromeLeftRailVisibleAtom = { init: true };
export const chromeMinimapVisibleAtom = { init: true };
export const chromeZoomLevelAtom = { init: 1 };

// Default export
export default Canvas;
