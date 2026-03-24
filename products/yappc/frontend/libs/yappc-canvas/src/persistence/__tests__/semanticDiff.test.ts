import { describe, it, expect } from 'vitest';

import {
  diff,
  applyPatch,
  generateDiffSummary,
  exportPatchesJSON,
  importPatchesJSON,
  mergeDiffs,
  filterDiffByType,
  type CanvasDocument,
  type DiffOptions,
  type JSONPatchOperation,
} from '../semanticDiff';

describe('semanticDiff', () => {
  describe('Basic Diffing', () => {
    it('should detect no changes for identical documents', () => {
      const doc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 0, y: 0 },
        ],
      };
      
      const result = diff(doc, doc);
      
      expect(result.hasChanges).toBe(false);
      expect(result.added).toEqual([]);
      expect(result.removed).toEqual([]);
      expect(result.modified).toEqual([]);
      expect(result.statistics.totalChanges).toBe(0);
    });

    it('should detect added nodes', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 0, y: 0 },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 0, y: 0 },
          { id: '2', type: 'circle', x: 100, y: 100 },
        ],
      };
      
      const result = diff(oldDoc, newDoc);
      
      expect(result.hasChanges).toBe(true);
      expect(result.added.length).toBe(1);
      expect(result.added[0].elementId).toBe('2');
      expect(result.added[0].operation).toBe('add');
      expect(result.added[0].changeType).toBe('structural');
      expect(result.statistics.elementsAdded).toBe(1);
    });

    it('should detect removed nodes', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 0, y: 0 },
          { id: '2', type: 'circle', x: 100, y: 100 },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 0, y: 0 },
        ],
      };
      
      const result = diff(oldDoc, newDoc);
      
      expect(result.hasChanges).toBe(true);
      expect(result.removed.length).toBe(1);
      expect(result.removed[0].elementId).toBe('2');
      expect(result.removed[0].operation).toBe('remove');
      expect(result.statistics.elementsRemoved).toBe(1);
    });

    it('should detect modified nodes', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 0, y: 0, color: 'red' },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 0, y: 0, color: 'blue' },
        ],
      };
      
      const result = diff(oldDoc, newDoc);
      
      expect(result.hasChanges).toBe(true);
      expect(result.modified.length).toBe(1);
      expect(result.modified[0].elementId).toBe('1');
      expect(result.modified[0].operation).toBe('replace');
      expect(result.modified[0].properties.length).toBe(1);
      expect(result.modified[0].properties[0].property).toBe('color');
      expect(result.modified[0].properties[0].oldValue).toBe('red');
      expect(result.modified[0].properties[0].newValue).toBe('blue');
    });
  });

  describe('Change Type Classification', () => {
    it('should classify structural changes', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', parent: null },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'circle', parent: 'group1' },
        ],
      };
      
      const result = diff(oldDoc, newDoc);
      
      expect(result.modified[0].changeType).toBe('structural');
      // 2 structural properties changed (type and parent)
      expect(result.statistics.structuralChanges).toBe(2);
    });

    it('should classify styling changes', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', color: 'red', fontSize: 12 },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', color: 'blue', fontSize: 14 },
        ],
      };
      
      const result = diff(oldDoc, newDoc);
      
      expect(result.modified[0].changeType).toBe('styling');
      // 2 styling properties changed (color and fontSize)
      expect(result.statistics.stylingChanges).toBe(2);
    });

    it('should classify content changes', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'text', text: 'Hello' },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'text', text: 'World' },
        ],
      };
      
      const result = diff(oldDoc, newDoc);
      
      expect(result.modified[0].changeType).toBe('content');
      expect(result.statistics.contentChanges).toBe(1);
    });

    it('should classify metadata changes', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', name: 'Node 1', tags: ['tag1'] },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', name: 'Node One', tags: ['tag1', 'tag2'] },
        ],
      };
      
      const result = diff(oldDoc, newDoc);
      
      expect(result.modified[0].changeType).toBe('metadata');
      // Property-level count: 2 metadata properties changed
      expect(result.statistics.metadataChanges).toBe(2);
    });

    it('should prioritize structural over other changes', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', color: 'red', parent: null },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', color: 'blue', parent: 'group1' },
        ],
      };
      
      const result = diff(oldDoc, newDoc);
      
      expect(result.modified[0].changeType).toBe('structural');
      expect(result.modified[0].properties.length).toBe(2);
    });
  });

  describe('Move Detection', () => {
    it('should detect element moves', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 0, y: 0 },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 100, y: 50 },
        ],
      };
      
      const result = diff(oldDoc, newDoc, { detectMoves: true });
      
      expect(result.moved.length).toBe(1);
      expect(result.moved[0].elementId).toBe('1');
      expect(result.moved[0].operation).toBe('move');
      expect(result.statistics.elementsMoved).toBe(1);
    });

    it('should not classify as move if other properties changed', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 0, y: 0, color: 'red' },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 100, y: 50, color: 'blue' },
        ],
      };
      
      const result = diff(oldDoc, newDoc, { detectMoves: true });
      
      expect(result.moved.length).toBe(0);
      expect(result.modified.length).toBe(1);
    });

    it('should allow metadata changes with moves', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 0, y: 0, updatedAt: 100 },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 100, y: 50, updatedAt: 200 },
        ],
      };
      
      const result = diff(oldDoc, newDoc, { detectMoves: true });
      
      expect(result.moved.length).toBe(1);
    });
  });

  describe('Edge Diffing', () => {
    it('should detect added edges', () => {
      const oldDoc: CanvasDocument = {
        edges: [],
      };
      
      const newDoc: CanvasDocument = {
        edges: [
          { id: 'e1', source: '1', target: '2' },
        ],
      };
      
      const result = diff(oldDoc, newDoc);
      
      expect(result.added.length).toBe(1);
      expect(result.added[0].elementType).toBe('edge');
      expect(result.added[0].elementId).toBe('e1');
    });

    it('should detect removed edges', () => {
      const oldDoc: CanvasDocument = {
        edges: [
          { id: 'e1', source: '1', target: '2' },
        ],
      };
      
      const newDoc: CanvasDocument = {
        edges: [],
      };
      
      const result = diff(oldDoc, newDoc);
      
      expect(result.removed.length).toBe(1);
      expect(result.removed[0].elementType).toBe('edge');
    });

    it('should detect modified edges', () => {
      const oldDoc: CanvasDocument = {
        edges: [
          { id: 'e1', source: '1', target: '2', label: 'connects' },
        ],
      };
      
      const newDoc: CanvasDocument = {
        edges: [
          { id: 'e1', source: '1', target: '2', label: 'links' },
        ],
      };
      
      const result = diff(oldDoc, newDoc);
      
      expect(result.modified.length).toBe(1);
      expect(result.modified[0].properties[0].property).toBe('label');
    });
  });

  describe('Diff Options', () => {
    it('should ignore specified properties', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 0, updatedAt: 100 },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 0, updatedAt: 200 },
        ],
      };
      
      const result = diff(oldDoc, newDoc, { ignoreProperties: ['updatedAt'] });
      
      expect(result.hasChanges).toBe(false);
    });

    it('should respect custom structural properties', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', customProp: 'value1' },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', customProp: 'value2' },
        ],
      };
      
      const result = diff(oldDoc, newDoc, {
        structuralProperties: ['customProp'],
      });
      
      expect(result.modified[0].changeType).toBe('structural');
    });

    it('should respect custom styling properties', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', customStyle: 'style1' },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', customStyle: 'style2' },
        ],
      };
      
      const result = diff(oldDoc, newDoc, {
        stylingProperties: ['customStyle'],
      });
      
      expect(result.modified[0].changeType).toBe('styling');
    });

    it('should disable move detection when option is false', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 0, y: 0 },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 100, y: 50 },
        ],
      };
      
      const result = diff(oldDoc, newDoc, { detectMoves: false });
      
      expect(result.moved.length).toBe(0);
      expect(result.modified.length).toBe(1);
    });
  });

  describe('JSON Patch Generation', () => {
    it('should generate add patch for new elements', () => {
      const oldDoc: CanvasDocument = { nodes: [] };
      const newDoc: CanvasDocument = {
        nodes: [{ id: '1', type: 'rect' }],
      };
      
      const result = diff(oldDoc, newDoc, { generatePatches: true });
      
      expect(result.patches.length).toBeGreaterThan(0);
      expect(result.patches[0].op).toBe('add');
      expect(result.patches[0].path).toBe('/nodes/-');
    });

    it('should generate remove patch for deleted elements', () => {
      const oldDoc: CanvasDocument = {
        nodes: [{ id: '1', type: 'rect' }],
      };
      const newDoc: CanvasDocument = { nodes: [] };
      
      const result = diff(oldDoc, newDoc, { generatePatches: true });
      
      expect(result.patches.length).toBeGreaterThan(0);
      expect(result.patches[0].op).toBe('remove');
      expect(result.patches[0].path).toMatch(/^\/nodes\/\d+$/);
    });

    it('should generate replace patches for modified properties', () => {
      const oldDoc: CanvasDocument = {
        nodes: [{ id: '1', type: 'rect', color: 'red' }],
      };
      const newDoc: CanvasDocument = {
        nodes: [{ id: '1', type: 'rect', color: 'blue' }],
      };
      
      const result = diff(oldDoc, newDoc, { generatePatches: true });
      
      expect(result.patches.length).toBeGreaterThan(0);
      expect(result.patches[0].op).toBe('replace');
      expect(result.patches[0].path).toMatch(/\/nodes\/\d+\/color$/);
      expect(result.patches[0].value).toBe('blue');
    });

    it('should not generate patches when option is false', () => {
      const oldDoc: CanvasDocument = { nodes: [] };
      const newDoc: CanvasDocument = {
        nodes: [{ id: '1', type: 'rect' }],
      };
      
      const result = diff(oldDoc, newDoc, { generatePatches: false });
      
      expect(result.patches).toEqual([]);
    });
  });

  describe('Patch Application', () => {
    it('should apply add patch', () => {
      const doc: CanvasDocument = { nodes: [] };
      const patches: JSONPatchOperation[] = [
        { op: 'add', path: '/nodes/-', value: { id: '1', type: 'rect' } },
      ];
      
      const result = applyPatch(doc, patches);
      
      expect(result.nodes).toHaveLength(1);
      expect(result.nodes![0].id).toBe('1');
    });

    it('should apply remove patch', () => {
      const doc: CanvasDocument = {
        nodes: [{ id: '1', type: 'rect' }],
      };
      const patches: JSONPatchOperation[] = [
        { op: 'remove', path: '/nodes/0' },
      ];
      
      const result = applyPatch(doc, patches);
      
      expect(result.nodes).toHaveLength(0);
    });

    it('should apply replace patch', () => {
      const doc: CanvasDocument = {
        nodes: [{ id: '1', type: 'rect', color: 'red' }],
      };
      const patches: JSONPatchOperation[] = [
        { op: 'replace', path: '/nodes/0/color', value: 'blue' },
      ];
      
      const result = applyPatch(doc, patches);
      
      expect(result.nodes![0].color).toBe('blue');
    });

    it('should apply multiple patches in sequence', () => {
      const doc: CanvasDocument = {
        nodes: [{ id: '1', type: 'rect', color: 'red' }],
      };
      const patches: JSONPatchOperation[] = [
        { op: 'replace', path: '/nodes/0/color', value: 'blue' },
        { op: 'add', path: '/nodes/-', value: { id: '2', type: 'circle' } },
      ];
      
      const result = applyPatch(doc, patches);
      
      expect(result.nodes![0].color).toBe('blue');
      expect(result.nodes).toHaveLength(2);
      expect(result.nodes![1].id).toBe('2');
    });

    it('should not mutate original document', () => {
      const doc: CanvasDocument = {
        nodes: [{ id: '1', type: 'rect', color: 'red' }],
      };
      const patches: JSONPatchOperation[] = [
        { op: 'replace', path: '/nodes/0/color', value: 'blue' },
      ];
      
      applyPatch(doc, patches);
      
      expect(doc.nodes![0].color).toBe('red'); // Original unchanged
    });
  });

  describe('Diff Statistics', () => {
    it('should calculate correct statistics', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 0 },
          { id: '2', type: 'circle', color: 'red' },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '2', type: 'circle', color: 'blue' },
          { id: '3', type: 'triangle', x: 100 },
        ],
      };
      
      const result = diff(oldDoc, newDoc);
      
      expect(result.statistics.elementsAdded).toBe(1);
      expect(result.statistics.elementsRemoved).toBe(1);
      expect(result.statistics.elementsModified).toBe(1);
      expect(result.statistics.totalChanges).toBe(3);
    });

    it('should count change types correctly', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 0, color: 'red', text: 'Hello', name: 'Node 1' },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'circle', x: 100, color: 'blue', text: 'World', name: 'Node One' },
        ],
      };
      
      const result = diff(oldDoc, newDoc);
      
      // Each property change is counted
      expect(result.statistics.structuralChanges).toBeGreaterThan(0); // type change
      expect(result.statistics.stylingChanges).toBeGreaterThan(0); // x, color changes
      expect(result.statistics.contentChanges).toBeGreaterThan(0); // text change
      expect(result.statistics.metadataChanges).toBeGreaterThan(0); // name change
      expect(result.statistics.totalChanges).toBe(1); // 1 element modified
    });
  });

  describe('Diff Summary Generation', () => {
    it('should generate summary for no changes', () => {
      const doc: CanvasDocument = { nodes: [] };
      const result = diff(doc, doc);
      const summary = generateDiffSummary(result);
      
      expect(summary).toBe('No changes detected');
    });

    it('should generate summary with changes', () => {
      const oldDoc: CanvasDocument = { nodes: [] };
      const newDoc: CanvasDocument = {
        nodes: [{ id: '1', type: 'rect' }],
      };
      
      const result = diff(oldDoc, newDoc);
      const summary = generateDiffSummary(result);
      
      expect(summary).toContain('Changes Summary');
      expect(summary).toContain('Added: 1 elements');
      expect(summary).toContain('Structural: 1');
    });

    it('should include all change statistics in summary', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', x: 0 },
          { id: '2', type: 'circle', color: 'red' },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '2', type: 'circle', color: 'blue' },
          { id: '3', type: 'triangle', text: 'New' },
        ],
      };
      
      const result = diff(oldDoc, newDoc);
      const summary = generateDiffSummary(result);
      
      expect(summary).toContain('Added:');
      expect(summary).toContain('Removed:');
      expect(summary).toContain('Modified:');
    });
  });

  describe('Patch Export/Import', () => {
    it('should export patches to JSON', () => {
      const patches: JSONPatchOperation[] = [
        { op: 'add', path: '/nodes/-', value: { id: '1' } },
        { op: 'replace', path: '/nodes/0/color', value: 'blue' },
      ];
      
      const json = exportPatchesJSON(patches);
      const parsed = JSON.parse(json);
      
      expect(parsed).toHaveLength(2);
      expect(parsed[0].op).toBe('add');
    });

    it('should import patches from JSON', () => {
      const json = JSON.stringify([
        { op: 'add', path: '/nodes/-', value: { id: '1' } },
      ]);
      
      const patches = importPatchesJSON(json);
      
      expect(patches).toHaveLength(1);
      expect(patches[0].op).toBe('add');
    });

    it('should throw error for invalid JSON', () => {
      expect(() => importPatchesJSON('invalid json')).toThrow('Failed to parse');
    });

    it('should throw error if patches not an array', () => {
      const json = JSON.stringify({ op: 'add' });
      
      expect(() => importPatchesJSON(json)).toThrow('must be an array');
    });
  });

  describe('Diff Merging', () => {
    it('should merge multiple diffs', () => {
      const diff1 = diff(
        { nodes: [] },
        { nodes: [{ id: '1', type: 'rect' }] }
      );
      
      const diff2 = diff(
        { nodes: [] },
        { nodes: [{ id: '2', type: 'circle' }] }
      );
      
      const merged = mergeDiffs([diff1, diff2]);
      
      expect(merged.added.length).toBe(2);
      expect(merged.statistics.elementsAdded).toBe(2);
    });

    it('should merge empty diffs', () => {
      const emptyDoc: CanvasDocument = { nodes: [] };
      const diff1 = diff(emptyDoc, emptyDoc);
      const diff2 = diff(emptyDoc, emptyDoc);
      
      const merged = mergeDiffs([diff1, diff2]);
      
      expect(merged.hasChanges).toBe(false);
      expect(merged.statistics.totalChanges).toBe(0);
    });

    it('should combine all patches', () => {
      const diff1 = diff(
        { nodes: [] },
        { nodes: [{ id: '1', type: 'rect' }] },
        { generatePatches: true }
      );
      
      const diff2 = diff(
        { nodes: [{ id: '1', type: 'rect', color: 'red' }] },
        { nodes: [{ id: '1', type: 'rect', color: 'blue' }] },
        { generatePatches: true }
      );
      
      const merged = mergeDiffs([diff1, diff2]);
      
      expect(merged.patches.length).toBeGreaterThan(0);
    });
  });

  describe('Diff Filtering', () => {
    it('should filter by structural changes', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', color: 'red', parent: null },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'circle', color: 'blue', parent: 'group1' },
        ],
      };
      
      const fullDiff = diff(oldDoc, newDoc);
      const filtered = filterDiffByType(fullDiff, ['structural']);
      
      expect(filtered.hasChanges).toBe(true);
      expect(filtered.statistics.structuralChanges).toBeGreaterThan(0);
      expect(filtered.statistics.stylingChanges).toBe(0);
    });

    it('should filter by styling changes', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', color: 'red', x: 0, name: 'Node' },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', color: 'blue', x: 100, name: 'Node 1' },
        ],
      };
      
      const fullDiff = diff(oldDoc, newDoc);
      const filtered = filterDiffByType(fullDiff, ['styling']);
      
      expect(filtered.hasChanges).toBe(true);
      expect(filtered.statistics.stylingChanges).toBeGreaterThan(0);
      expect(filtered.statistics.metadataChanges).toBe(0);
    });

    it('should filter by multiple change types', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', color: 'red', text: 'Hello', name: 'Node' },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect', color: 'blue', text: 'World', name: 'Node 1' },
        ],
      };
      
      const fullDiff = diff(oldDoc, newDoc);
      const filtered = filterDiffByType(fullDiff, ['styling', 'content']);
      
      expect(filtered.hasChanges).toBe(true);
      expect(filtered.statistics.stylingChanges).toBeGreaterThan(0);
      expect(filtered.statistics.contentChanges).toBeGreaterThan(0);
      // Metadata change (name) should not be counted
      expect(filtered.statistics.metadataChanges).toBe(0);
    });

    it('should return empty diff when no matching changes', () => {
      const oldDoc: CanvasDocument = {
        nodes: [{ id: '1', type: 'rect', color: 'red' }],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [{ id: '1', type: 'rect', color: 'blue' }],
      };
      
      const fullDiff = diff(oldDoc, newDoc);
      const filtered = filterDiffByType(fullDiff, ['structural']);
      
      expect(filtered.hasChanges).toBe(false);
      expect(filtered.statistics.totalChanges).toBe(0);
    });
  });

  describe('Complex Scenarios', () => {
    it('should handle documents with both nodes and edges', () => {
      const oldDoc: CanvasDocument = {
        nodes: [{ id: '1', type: 'rect' }],
        edges: [{ id: 'e1', source: '1', target: '2' }],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          { id: '1', type: 'rect' },
          { id: '2', type: 'circle' },
        ],
        edges: [{ id: 'e1', source: '1', target: '2', label: 'connects' }],
      };
      
      const result = diff(oldDoc, newDoc);
      
      expect(result.added.length).toBe(1); // 1 node added
      expect(result.modified.length).toBe(1); // 1 edge modified
    });

    it('should handle deeply nested property changes', () => {
      const oldDoc: CanvasDocument = {
        nodes: [
          { 
            id: '1',
            type: 'rect',
            data: { nested: { value: 1 } }
          },
        ],
      };
      
      const newDoc: CanvasDocument = {
        nodes: [
          {
            id: '1',
            type: 'rect',
            data: { nested: { value: 2 } }
          },
        ],
      };
      
      const result = diff(oldDoc, newDoc);
      
      expect(result.hasChanges).toBe(true);
      expect(result.modified.length).toBe(1);
    });

    it('should handle large documents efficiently', () => {
      const oldNodes = Array.from({ length: 100 }, (_, i) => ({
        id: `node${i}`,
        type: 'rect',
        x: i * 10,
        y: i * 10,
      }));
      
      const newNodes = oldNodes.map(n => ({ ...n, color: 'blue' }));
      
      const oldDoc: CanvasDocument = { nodes: oldNodes };
      const newDoc: CanvasDocument = { nodes: newNodes };
      
      const start = Date.now();
      const result = diff(oldDoc, newDoc);
      const duration = Date.now() - start;
      
      expect(result.modified.length).toBe(100);
      expect(duration).toBeLessThan(1000); // Should complete in under 1 second
    });
  });
});
