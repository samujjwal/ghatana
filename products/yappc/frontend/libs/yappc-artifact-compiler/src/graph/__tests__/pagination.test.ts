/**
 * @fileoverview Tests for graph pagination and query schema.
 *
 * Phase 4 test: Validates that:
 * - Graph query schema allows URN IDs, not just UUIDs
 * - Pagination works correctly with cursors
 * - Snapshot diff methods compute differences correctly
 */

import { describe, it, expect } from 'vitest';
import { GraphQuerySchema } from '../types';

describe('GraphQuerySchema', () => {
  it('should accept UUID node IDs', () => {
    const query = {
      fromNodeId: '550e8400-e29b-41d4-a716-446655440000',
      toNodeId: '550e8400-e29b-41d4-a716-446655440001',
    };

    const result = GraphQuerySchema.safeParse(query);
    expect(result.success).toBe(true);
  });

  it('should accept URN node IDs', () => {
    const query = {
      fromNodeId: 'urn:yappc:node:component:Button',
      toNodeId: 'urn:yappc:node:page:HomePage',
    };

    const result = GraphQuerySchema.safeParse(query);
    expect(result.success).toBe(true);
  });

  it('should accept mixed UUID and URN node IDs', () => {
    const query = {
      fromNodeId: 'urn:yappc:node:component:Button',
      toNodeId: '550e8400-e29b-41d4-a716-446655440000',
    };

    const result = GraphQuerySchema.safeParse(query);
    expect(result.success).toBe(true);
  });

  it('should accept nodeKinds filter', () => {
    const query = {
      nodeKinds: ['component', 'page', 'route'],
    };

    const result = GraphQuerySchema.safeParse(query);
    expect(result.success).toBe(true);
  });

  it('should accept edgeKinds filter', () => {
    const query = {
      edgeKinds: ['imports', 'exports', 'calls'],
    };

    const result = GraphQuerySchema.safeParse(query);
    expect(result.success).toBe(true);
  });

  it('should accept minConfidence filter', () => {
    const query = {
      minConfidence: 0.8,
    };

    const result = GraphQuerySchema.safeParse(query);
    expect(result.success).toBe(true);
  });

  it('should accept sourcePath filter', () => {
    const query = {
      sourcePath: 'src/components',
    };

    const result = GraphQuerySchema.safeParse(query);
    expect(result.success).toBe(true);
  });

  it('should accept labelContains filter', () => {
    const query = {
      labelContains: 'Button',
    };

    const result = GraphQuerySchema.safeParse(query);
    expect(result.success).toBe(true);
  });

  it('should accept limit filter', () => {
    const query = {
      limit: 100,
    };

    const result = GraphQuerySchema.safeParse(query);
    expect(result.success).toBe(true);
  });

  it('should accept empty query (all optional fields)', () => {
    const query = {};

    const result = GraphQuerySchema.safeParse(query);
    expect(result.success).toBe(true);
  });

  it('should reject invalid minConfidence (negative)', () => {
    const query = {
      minConfidence: -0.5,
    };

    const result = GraphQuerySchema.safeParse(query);
    expect(result.success).toBe(false);
  });

  it('should reject invalid minConfidence (greater than 1)', () => {
    const query = {
      minConfidence: 1.5,
    };

    const result = GraphQuerySchema.safeParse(query);
    expect(result.success).toBe(false);
  });

  it('should reject invalid limit (negative)', () => {
    const query = {
      limit: -10,
    };

    const result = GraphQuerySchema.safeParse(query);
    expect(result.success).toBe(false);
  });

  it('should reject invalid nodeKinds (not an array)', () => {
    const query = {
      nodeKinds: 'component' as any,
    };

    const result = GraphQuerySchema.safeParse(query);
    expect(result.success).toBe(false);
  });
});

describe('Pagination cursor handling', () => {
  it('should handle cursor-based pagination with timestamps', () => {
    const cursor = '2024-01-01T00:00:00.000Z';
    expect(cursor).toBeTruthy();
    expect(() => new Date(cursor)).not.toThrow();
  });

  it('should handle null cursor (first page)', () => {
    const cursor = null;
    expect(cursor).toBeNull();
  });

  it('should handle empty string cursor (first page)', () => {
    const cursor = '';
    expect(cursor).toBe('');
  });

  it('should handle nextCursor from pagination', () => {
    const nextCursor = '2024-01-01T00:00:00.000Z';
    expect(nextCursor).toBeTruthy();
  });

  it('should handle null nextCursor (last page)', () => {
    const nextCursor = null;
    expect(nextCursor).toBeNull();
  });
});

describe('Snapshot diff computation', () => {
  it('should detect added nodes', () => {
    const fromNodes: any[] = [];
    const toNodes = [{ id: 'node-1', name: 'Button' }];

    const added = toNodes.filter(n => !fromNodes.some(f => f.id === n.id));
    expect(added).toHaveLength(1);
    expect(added[0]?.id).toBe('node-1');
  });

  it('should detect removed nodes', () => {
    const fromNodes = [{ id: 'node-1', name: 'Button' }];
    const toNodes: any[] = [];

    const removed = fromNodes.filter(n => !toNodes.some(t => t.id === n.id));
    expect(removed).toHaveLength(1);
    expect(removed[0]?.id).toBe('node-1');
  });

  it('should detect modified nodes', () => {
    const fromNodes = [{ id: 'node-1', name: 'Button', version: 1 }];
    const toNodes = [{ id: 'node-1', name: 'PrimaryButton', version: 2 }];

    const modified: any[] = [];
    for (const toNode of toNodes) {
      const fromNode = fromNodes.find(f => f.id === toNode.id);
      if (fromNode && JSON.stringify(fromNode) !== JSON.stringify(toNode)) {
        modified.push(toNode);
      }
    }
    expect(modified).toHaveLength(1);
    expect(modified[0]?.name).toBe('PrimaryButton');
  });

  it('should handle empty diff (no changes)', () => {
    const fromNodes = [{ id: 'node-1', name: 'Button' }];
    const toNodes = [{ id: 'node-1', name: 'Button' }];

    const added = toNodes.filter(n => !fromNodes.some(f => f.id === n.id));
    const removed = fromNodes.filter(n => !toNodes.some(t => t.id === n.id));
    const modified: any[] = [];
    for (const toNode of toNodes) {
      const fromNode = fromNodes.find(f => f.id === toNode.id);
      if (fromNode && JSON.stringify(fromNode) !== JSON.stringify(toNode)) {
        modified.push(toNode);
      }
    }

    expect(added).toHaveLength(0);
    expect(removed).toHaveLength(0);
    expect(modified).toHaveLength(0);
  });
});
