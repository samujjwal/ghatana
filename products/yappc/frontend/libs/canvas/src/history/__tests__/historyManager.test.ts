import { describe, it, expect, beforeEach } from 'vitest';

import {
  createHistoryManager,
  addHistory,
  undo,
  redo,
  canUndo,
  canRedo,
  clearHistory,
  batchHistory,
  createVersion,
  diffVersions,
  createTemplate,
  updateTemplate,
  filterTemplates,
  createAutosaveState,
  shouldAutosave,
  markDirty,
  markSaved,
  markSavePending,
  type HistoryState,
  type VersionDiff,
  type DocumentTemplate,
  type AutosaveState,
} from './historyManager';

describe('historyManager', () => {
  describe('History Management', () => {
    let history: HistoryState<{ value: number }>;

    beforeEach(() => {
      history = createHistoryManager({ value: 0 });
    });

    it('should create initial history state', () => {
      expect(history.past).toEqual([]);
      expect(history.future).toEqual([]);
      expect(history.current).toEqual({ value: 0 });
      expect(history.config.maxSize).toBe(50);
    });

    it('should add history entry', () => {
      const updated = addHistory(history, {
        type: 'increment',
        before: { value: 0 },
        after: { value: 1 },
      });

      expect(updated.past.length).toBe(1);
      expect(updated.past[0].type).toBe('increment');
      expect(updated.past[0].before).toEqual({ value: 0 });
      expect(updated.past[0].after).toEqual({ value: 1 });
      expect(updated.current).toEqual({ value: 1 });
      expect(updated.future).toEqual([]);
    });

    it('should clear future when new action added', () => {
      let state = addHistory(history, {
        type: 'increment',
        before: { value: 0 },
        after: { value: 1 },
      });

      state = addHistory(state, {
        type: 'increment',
        before: { value: 1 },
        after: { value: 2 },
      });

      // Undo once
      state = undo(state)!;
      expect(state.future.length).toBe(1);

      // Add new action - should clear future
      state = addHistory(state, {
        type: 'increment',
        before: { value: 1 },
        after: { value: 3 },
      });

      expect(state.future).toEqual([]);
    });

    it('should prune old entries when exceeding maxSize', () => {
      let state = createHistoryManager({ value: 0 }, { maxSize: 3 });

      for (let i = 0; i < 5; i++) {
        state = addHistory(state, {
          type: 'increment',
          before: { value: i },
          after: { value: i + 1 },
        });
      }

      expect(state.past.length).toBe(3); // Only last 3 kept
      expect(state.past[0].after).toEqual({ value: 3 }); // First kept entry
      expect(state.past[2].after).toEqual({ value: 5 }); // Last entry
    });

    it('should undo action', () => {
      let state = addHistory(history, {
        type: 'increment',
        before: { value: 0 },
        after: { value: 1 },
      });

      state = undo(state)!;

      expect(state.past.length).toBe(0);
      expect(state.future.length).toBe(1);
      expect(state.current).toEqual({ value: 0 });
    });

    it('should return null when nothing to undo', () => {
      const result = undo(history);
      expect(result).toBeNull();
    });

    it('should redo action', () => {
      let state = addHistory(history, {
        type: 'increment',
        before: { value: 0 },
        after: { value: 1 },
      });

      state = undo(state)!;
      state = redo(state)!;

      expect(state.past.length).toBe(1);
      expect(state.future.length).toBe(0);
      expect(state.current).toEqual({ value: 1 });
    });

    it('should return null when nothing to redo', () => {
      const result = redo(history);
      expect(result).toBeNull();
    });

    it('should check undo availability', () => {
      expect(canUndo(history)).toBe(false);

      const updated = addHistory(history, {
        type: 'increment',
        before: { value: 0 },
        after: { value: 1 },
      });

      expect(canUndo(updated)).toBe(true);
    });

    it('should check redo availability', () => {
      expect(canRedo(history)).toBe(false);

      let state = addHistory(history, {
        type: 'increment',
        before: { value: 0 },
        after: { value: 1 },
      });

      state = undo(state)!;

      expect(canRedo(state)).toBe(true);
    });

    it('should clear history', () => {
      let state = addHistory(history, {
        type: 'increment',
        before: { value: 0 },
        after: { value: 1 },
      });

      state = clearHistory(state);

      expect(state.past).toEqual([]);
      expect(state.future).toEqual([]);
    });

    it('should handle multiple undo/redo operations', () => {
      let state = history;

      // Add 3 actions
      for (let i = 0; i < 3; i++) {
        state = addHistory(state, {
          type: 'increment',
          before: { value: i },
          after: { value: i + 1 },
        });
      }

      expect(state.current).toEqual({ value: 3 });

      // Undo 2 times
      state = undo(state)!;
      state = undo(state)!;

      expect(state.current).toEqual({ value: 1 });
      expect(state.past.length).toBe(1);
      expect(state.future.length).toBe(2);

      // Redo 1 time
      state = redo(state)!;

      expect(state.current).toEqual({ value: 2 });
      expect(state.past.length).toBe(2);
      expect(state.future.length).toBe(1);
    });
  });

  describe('History Batching', () => {
    it('should batch consecutive same-type entries within time window', () => {
      let history = createHistoryManager({ value: 0 }, { batchWindow: 500 });

      const baseTime = Date.now();

      // Add 3 entries of same type within 500ms
      history = addHistory(history, {
        type: 'drag',
        before: { value: 0 },
        after: { value: 1 },
      });
      // Mock timestamp
      history.past[0].timestamp = baseTime;

      history = addHistory(history, {
        type: 'drag',
        before: { value: 1 },
        after: { value: 2 },
      });
      history.past[1].timestamp = baseTime + 200;

      history = addHistory(history, {
        type: 'drag',
        before: { value: 2 },
        after: { value: 3 },
      });
      history.past[2].timestamp = baseTime + 400;

      const batched = batchHistory(history);

      expect(batched.past.length).toBe(1); // 3 entries batched into 1
      expect(batched.past[0].before).toEqual({ value: 0 });
      expect(batched.past[0].after).toEqual({ value: 3 });
    });

    it('should not batch different types', () => {
      let history = createHistoryManager({ value: 0 });

      history = addHistory(history, {
        type: 'drag',
        before: { value: 0 },
        after: { value: 1 },
      });

      history = addHistory(history, {
        type: 'resize',
        before: { value: 1 },
        after: { value: 2 },
      });

      const batched = batchHistory(history);

      expect(batched.past.length).toBe(2); // Not batched
    });

    it('should not batch entries outside time window', () => {
      let history = createHistoryManager({ value: 0 }, { batchWindow: 500 });

      const baseTime = Date.now();

      history = addHistory(history, {
        type: 'drag',
        before: { value: 0 },
        after: { value: 1 },
      });
      history.past[0].timestamp = baseTime;

      history = addHistory(history, {
        type: 'drag',
        before: { value: 1 },
        after: { value: 2 },
      });
      history.past[1].timestamp = baseTime + 600; // Outside window

      const batched = batchHistory(history);

      expect(batched.past.length).toBe(2); // Not batched
    });

    it('should handle empty history', () => {
      const history = createHistoryManager({ value: 0 });
      const batched = batchHistory(history);

      expect(batched.past).toEqual([]);
    });
  });

  describe('Version Management', () => {
    it('should create version snapshot', () => {
      const state = { elements: [], viewport: { zoom: 1 } };
      const version = createVersion('doc-1', state, {
        version: 1,
        author: 'user-1',
        description: 'Initial version',
      });

      expect(version.documentId).toBe('doc-1');
      expect(version.version).toBe(1);
      expect(version.state).toEqual(state);
      expect(version.author).toBe('user-1');
      expect(version.description).toBe('Initial version');
      expect(version.timestamp).toBeGreaterThan(0);
    });

    it('should diff versions - added fields', () => {
      const before = { a: 1 };
      const after = { a: 1, b: 2 };

      const diffs = diffVersions(before, after);

      expect(diffs.length).toBe(1);
      expect(diffs[0].type).toBe('added');
      expect(diffs[0].path).toEqual(['b']);
      expect(diffs[0].after).toBe(2);
    });

    it('should diff versions - removed fields', () => {
      const before = { a: 1, b: 2 };
      const after = { a: 1 };

      const diffs = diffVersions(before, after);

      expect(diffs.length).toBe(1);
      expect(diffs[0].type).toBe('removed');
      expect(diffs[0].path).toEqual(['b']);
      expect(diffs[0].before).toBe(2);
    });

    it('should diff versions - modified fields', () => {
      const before = { a: 1 };
      const after = { a: 2 };

      const diffs = diffVersions(before, after);

      expect(diffs.length).toBe(1);
      expect(diffs[0].type).toBe('modified');
      expect(diffs[0].path).toEqual(['a']);
      expect(diffs[0].before).toBe(1);
      expect(diffs[0].after).toBe(2);
    });

    it('should detect structural vs styling changes', () => {
      const before = { position: { x: 0 }, color: 'red' };
      const after = { position: { x: 10 }, color: 'blue' };

      const diffs = diffVersions(before, after);

      const positionDiff = diffs.find((d) => d.path[0] === 'position');
      const colorDiff = diffs.find((d) => d.path[0] === 'color');

      expect(positionDiff?.isStructural).toBe(true);
      expect(colorDiff?.isStructural).toBe(false);
    });

    it('should diff nested objects', () => {
      const before = { data: { label: 'A', width: 100 } };
      const after = { data: { label: 'B', width: 100 } };

      const diffs = diffVersions(before, after);

      expect(diffs.length).toBe(1);
      expect(diffs[0].path).toEqual(['data', 'label']);
      expect(diffs[0].before).toBe('A');
      expect(diffs[0].after).toBe('B');
    });

    it('should diff arrays', () => {
      const before = { items: [1, 2] };
      const after = { items: [1, 2, 3] };

      const diffs = diffVersions(before, after);

      expect(diffs.length).toBe(1);
      expect(diffs[0].type).toBe('added');
      expect(diffs[0].path).toEqual(['items', '2']);
      expect(diffs[0].after).toBe(3);
    });

    it('should handle identical versions', () => {
      const state = { a: 1, b: 2 };
      const diffs = diffVersions(state, state);

      expect(diffs).toEqual([]);
    });
  });

  describe('Template Management', () => {
    it('should create template', () => {
      const state = { elements: [], viewport: { zoom: 1 } };
      const template = createTemplate(state, {
        name: 'Blank Canvas',
        description: 'A blank canvas template',
        category: 'basic',
      });

      expect(template.name).toBe('Blank Canvas');
      expect(template.description).toBe('A blank canvas template');
      expect(template.category).toBe('basic');
      expect(template.state).toEqual(state);
      expect(template.createdAt).toBeGreaterThan(0);
      expect(template.updatedAt).toBe(template.createdAt);
    });

    it('should update template', () => {
      const template = createTemplate(
        { value: 1 },
        {
          name: 'Template 1',
        }
      );

      const updated = updateTemplate(template, {
        description: 'Updated description',
        tags: ['tag1', 'tag2'],
      });

      expect(updated.description).toBe('Updated description');
      expect(updated.tags).toEqual(['tag1', 'tag2']);
      expect(updated.updatedAt).toBeGreaterThanOrEqual(template.updatedAt);
    });

    it('should filter templates by category', () => {
      const templates = [
        createTemplate({ value: 1 }, { name: 'T1', category: 'basic' }),
        createTemplate({ value: 2 }, { name: 'T2', category: 'advanced' }),
        createTemplate({ value: 3 }, { name: 'T3', category: 'basic' }),
      ];

      const filtered = filterTemplates(templates, { category: 'basic' });

      expect(filtered.length).toBe(2);
      expect(filtered[0].name).toBe('T1');
      expect(filtered[1].name).toBe('T3');
    });

    it('should filter templates by tags', () => {
      const templates = [
        createTemplate({ value: 1 }, { name: 'T1', tags: ['flowchart'] }),
        createTemplate({ value: 2 }, { name: 'T2', tags: ['uml', 'diagram'] }),
        createTemplate(
          { value: 3 },
          { name: 'T3', tags: ['flowchart', 'process'] }
        ),
      ];

      const filtered = filterTemplates(templates, { tags: ['flowchart'] });

      expect(filtered.length).toBe(2);
      expect(filtered.every((t) => t.tags?.includes('flowchart'))).toBe(true);
    });

    it('should filter templates by author', () => {
      const templates = [
        createTemplate({ value: 1 }, { name: 'T1', author: 'user-1' }),
        createTemplate({ value: 2 }, { name: 'T2', author: 'user-2' }),
        createTemplate({ value: 3 }, { name: 'T3', author: 'user-1' }),
      ];

      const filtered = filterTemplates(templates, { author: 'user-1' });

      expect(filtered.length).toBe(2);
      expect(filtered.every((t) => t.author === 'user-1')).toBe(true);
    });

    it('should filter templates by search text', () => {
      const templates = [
        createTemplate(
          { value: 1 },
          { name: 'Flowchart Template', description: 'Basic flowchart' }
        ),
        createTemplate(
          { value: 2 },
          { name: 'UML Diagram', description: 'Class diagram' }
        ),
        createTemplate(
          { value: 3 },
          { name: 'Process Flow', description: 'Business process' }
        ),
      ];

      const filtered = filterTemplates(templates, { searchText: 'flow' });

      expect(filtered.length).toBe(2); // Matches "Flowchart" and "Process Flow"
    });

    it('should filter templates by multiple criteria', () => {
      const templates = [
        createTemplate(
          { value: 1 },
          {
            name: 'T1',
            category: 'basic',
            tags: ['flowchart'],
            author: 'user-1',
          }
        ),
        createTemplate(
          { value: 2 },
          {
            name: 'T2',
            category: 'basic',
            tags: ['uml'],
            author: 'user-2',
          }
        ),
        createTemplate(
          { value: 3 },
          {
            name: 'T3',
            category: 'advanced',
            tags: ['flowchart'],
            author: 'user-1',
          }
        ),
      ];

      const filtered = filterTemplates(templates, {
        category: 'basic',
        tags: ['flowchart'],
        author: 'user-1',
      });

      expect(filtered.length).toBe(1);
      expect(filtered[0].name).toBe('T1');
    });
  });

  describe('Autosave Coordination', () => {
    let autosave: AutosaveState;

    beforeEach(() => {
      autosave = createAutosaveState();
    });

    it('should create initial autosave state', () => {
      expect(autosave.enabled).toBe(true);
      expect(autosave.interval).toBe(5000);
      expect(autosave.lastSaved).toBeNull();
      expect(autosave.isDirty).toBe(false);
      expect(autosave.isPending).toBe(false);
    });

    it('should mark as dirty', () => {
      const updated = markDirty(autosave);

      expect(updated.isDirty).toBe(true);
    });

    it('should mark as saved', () => {
      let state = markDirty(autosave);
      const timestamp = Date.now();
      state = markSaved(state, timestamp);

      expect(state.isDirty).toBe(false);
      expect(state.isPending).toBe(false);
      expect(state.lastSaved).toBe(timestamp);
    });

    it('should mark as save pending', () => {
      const updated = markSavePending(autosave);

      expect(updated.isPending).toBe(true);
    });

    it('should trigger autosave when dirty and interval elapsed', () => {
      const pastTimestamp = Date.now() - 6000; // 6 seconds ago
      // Mark saved first (sets lastSaved), then mark dirty (needs save)
      let state = markSaved(autosave, pastTimestamp);
      state = markDirty(state);

      // Call shouldAutosave with current time to ensure interval check
      const should = shouldAutosave(state, Date.now());

      expect(should).toBe(true);
    });

    it('should not trigger when not dirty', () => {
      const should = shouldAutosave(autosave);

      expect(should).toBe(false);
    });

    it('should not trigger when save pending', () => {
      let state = markDirty(autosave);
      state = markSavePending(state);

      const should = shouldAutosave(state);

      expect(should).toBe(false);
    });

    it('should not trigger when interval not elapsed', () => {
      let state = markDirty(autosave);
      state = markSaved(state, Date.now() - 2000); // 2 seconds ago

      const should = shouldAutosave(state);

      expect(should).toBe(false);
    });

    it('should not trigger when disabled', () => {
      let state = createAutosaveState({ enabled: false });
      state = markDirty(state);

      const should = shouldAutosave(state);

      expect(should).toBe(false);
    });

    it('should trigger on first save when dirty', () => {
      const state = markDirty(autosave);

      const should = shouldAutosave(state);

      expect(should).toBe(true);
    });
  });
});
