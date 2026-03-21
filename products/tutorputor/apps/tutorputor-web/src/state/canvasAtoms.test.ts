import { describe, it, expect } from 'vitest';
import { atom } from 'jotai';
import {
  canvasNodesAtom,
  canvasEdgesAtom,
  selectedNodesAtom,
  selectedToolAtom,
  collaborationStateAtom,
  aiStateAtom,
  canvasSettingsAtom,
  canvasHistoryAtom,
} from './canvasAtoms';

describe('canvasAtoms', () => {
  describe('canvasNodesAtom', () => {
    it('should initialize with empty array', () => {
      const store = { get: (a: any) => a.init };
      expect(canvasNodesAtom.init).toEqual([]);
    });
  });

  describe('canvasEdgesAtom', () => {
    it('should initialize with empty array', () => {
      expect(canvasEdgesAtom.init).toEqual([]);
    });
  });

  describe('selectedNodesAtom', () => {
    it('should initialize with empty array', () => {
      expect(selectedNodesAtom.init).toEqual([]);
    });
  });

  describe('selectedToolAtom', () => {
    it('should initialize with select tool', () => {
      expect(selectedToolAtom.init).toBe('select');
    });
  });

  describe('collaborationStateAtom', () => {
    it('should initialize with correct default state', () => {
      expect(collaborationStateAtom.init).toEqual({
        enabled: false,
        users: [],
        currentUser: null,
      });
    });
  });

  describe('aiStateAtom', () => {
    it('should initialize with correct default state', () => {
      expect(aiStateAtom.init).toEqual({
        suggestions: [],
        isProcessing: false,
        lastQuery: '',
      });
    });
  });

  describe('canvasSettingsAtom', () => {
    it('should initialize with correct default settings', () => {
      expect(canvasSettingsAtom.init).toEqual({
        snapToGrid: true,
        gridSize: 16,
        showMinimap: true,
        showGrid: true,
      });
    });
  });

  describe('canvasHistoryAtom', () => {
    it('should initialize with correct default state', () => {
      expect(canvasHistoryAtom.init).toEqual({
        past: [],
        present: { nodes: [], edges: [] },
        future: [],
      });
    });
  });
});
