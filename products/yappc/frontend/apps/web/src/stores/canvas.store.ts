/**
 * Canvas State Management
 * 
 * Centralized canvas state atoms for YAPPC application.
 * Re-exports atoms from canvas library for centralized access.
 * 
 * @doc.type module
 * @doc.purpose Canvas state management
 * @doc.layer infrastructure
 */

import { atom } from 'jotai';
import type { Node, Edge, Viewport } from '@xyflow/react';

// Canvas core state
export const canvasNodesAtom = atom<Node[]>([]);
export const canvasEdgesAtom = atom<Edge[]>([]);
export const canvasViewportAtom = atom<Viewport>({ x: 0, y: 0, zoom: 1 });

// Application-specific canvas state
export const canvasSelectionAtom = atom<string[]>([]);

export const canvasModeAtom = atom<'select' | 'pan' | 'draw' | 'text'>('select');

export const canvasHistoryAtom = atom<{
  past: Array<{ nodes: Node[]; edges: Edge[] }>;
  future: Array<{ nodes: Node[]; edges: Edge[] }>;
}>({
  past: [],
  future: [],
});
