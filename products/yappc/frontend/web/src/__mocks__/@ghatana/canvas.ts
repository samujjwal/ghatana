/**
 * Mock for @ghatana/canvas package.
 * Used in vitest environment where the canvas dist files reference
 * unavailable external dependencies like @ghatana/theme/provider.
 */
import React from 'react';
import { vi } from 'vitest';

export const CanvasChromeLayout = ({ children }: { children: React.ReactNode }) => children as React.ReactElement;
export const useCanvasCommands = () => ({
  executeCommand: vi.fn(),
  canUndo: false,
  canRedo: false,
  undo: vi.fn(),
  redo: vi.fn(),
});
export const useCanvasTelemetry = () => ({
  trackEvent: vi.fn(),
  trackError: vi.fn(),
});

// Hybrid exports (used via @ghatana/canvas/hybrid)
export const GraphLayer = ({ children }: { children?: React.ReactNode }) =>
  React.createElement('div', { 'data-testid': 'graph-layer' }, children);

export const CanvasEngine = vi.fn(() => ({
  mount: vi.fn(),
  unmount: vi.fn(),
  render: vi.fn(),
}));

export const SpatialIndex = vi.fn(() => ({
  index: vi.fn(),
  query: vi.fn(),
  clear: vi.fn(),
}));
