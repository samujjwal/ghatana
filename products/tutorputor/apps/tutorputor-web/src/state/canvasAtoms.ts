import { atom } from 'jotai';
import type { Node, Edge } from '@xyflow/react';

// Canvas state atoms
export const canvasNodesAtom = atom<Node[]>([]);
export const canvasEdgesAtom = atom<Edge[]>([]);
export const selectedNodesAtom = atom<string[]>([]);
export const selectedToolAtom = atom<string>('select');

// Collaboration state
export interface CollaborationUser {
  id: string;
  name: string;
  color: string;
  cursor?: { x: number; y: number };
}

export interface CollaborationState {
  enabled: boolean;
  users: CollaborationUser[];
  currentUser: CollaborationUser | null;
}

export const collaborationStateAtom = atom<CollaborationState>({
  enabled: false,
  users: [],
  currentUser: null,
});

// AI state
export interface AISuggestion {
  id: string;
  type: 'layout' | 'content' | 'organization';
  title: string;
  description: string;
  confidence: number;
}

export interface AIState {
  suggestions: AISuggestion[];
  isProcessing: boolean;
  lastQuery: string;
}

export const aiStateAtom = atom<AIState>({
  suggestions: [],
  isProcessing: false,
  lastQuery: '',
});

// Canvas settings
export const canvasSettingsAtom = atom({
  snapToGrid: true,
  gridSize: 16,
  showMinimap: true,
  showGrid: true,
});

// History for undo/redo
export interface CanvasHistory {
  past: Array<{ nodes: Node[]; edges: Edge[] }>;
  present: { nodes: Node[]; edges: Edge[] };
  future: Array<{ nodes: Node[]; edges: Edge[] }>;
}

export const canvasHistoryAtom = atom<CanvasHistory>({
  past: [],
  present: { nodes: [], edges: [] },
  future: [],
});
