/**
 * Mock for @ghatana/canvas package.
 * Used in vitest environment where the canvas dist files reference
 * unavailable external dependencies like @ghatana/theme/provider.
 */
import React from 'react';
import { vi } from 'vitest';
import { atom } from 'jotai';

type MockCallback = (...args: unknown[]) => unknown;

const createMockCallback = (): MockCallback => vi.fn() as MockCallback;

export const CanvasChromeLayout = ({ children }: { children: React.ReactNode }) => children as React.ReactElement;
interface CanvasCommandsMock {
  executeCommand: MockCallback;
  canUndo: boolean;
  canRedo: boolean;
  undo: MockCallback;
  redo: MockCallback;
}

export const useCanvasCommands = (): CanvasCommandsMock => ({
  executeCommand: createMockCallback(),
  canUndo: false,
  canRedo: false,
  undo: createMockCallback(),
  redo: createMockCallback(),
});

interface CanvasTelemetryMock {
  trackEvent: MockCallback;
  trackError: MockCallback;
}

export const useCanvasTelemetry = (): CanvasTelemetryMock => ({
  trackEvent: createMockCallback(),
  trackError: createMockCallback(),
});

// Hybrid exports (used via @ghatana/canvas/hybrid)
export const GraphLayer = ({ children }: { children?: React.ReactNode }) =>
  React.createElement('div', { 'data-testid': 'graph-layer' }, children);

interface CanvasEngineMock {
  mount: MockCallback;
  unmount: MockCallback;
  render: MockCallback;
}

export const CanvasEngine = (): CanvasEngineMock => ({
  mount: createMockCallback(),
  unmount: createMockCallback(),
  render: createMockCallback(),
});

interface SpatialIndexMock {
  index: MockCallback;
  query: MockCallback;
  clear: MockCallback;
}

export const SpatialIndex = (): SpatialIndexMock => ({
  index: createMockCallback(),
  query: createMockCallback(),
  clear: createMockCallback(),
});

// Chrome atoms (used by canvas.tsx route)
export const chromeCalmModeAtom = atom<boolean>(false);
export const chromeInspectorVisibleAtom = atom<boolean>(false);
export const chromeLeftRailVisibleAtom = atom<boolean>(true);
export const chromeMinimapVisibleAtom = atom<boolean>(false);
export const chromeZoomLevelAtom = atom<number>(1.0);

// AI provider
export const AICanvasProvider = ({ children }: { children: React.ReactNode }) =>
  React.createElement(React.Fragment, null, children);
export type CanvasAIAdapter = {
  getSuggestions: () => Promise<unknown[]>;
  applyResult: () => Promise<void>;
  getCapabilities: () => { canSuggest: boolean; canLayout: boolean; canGenerate: boolean };
};
