/**
 * @fileoverview Tests for code import and ownership-aware reconciliation.
 *
 * @doc.type test
 * @doc.purpose Ensure JSON/TSX/HTML import and reconciliation work correctly.
 */

import { describe, it, expect } from 'vitest';
import {
  importSource,
  importFromJson,
  importFromTsx,
  importFromHtml,
} from '../import.js';
import type { BuilderDocument, ComponentInstance } from '../types.js';
import { createDocumentId, createNodeId } from '../types.js';

// ----------------------------------------------------------------------------
// Fixtures
// ----------------------------------------------------------------------------

function makeDoc(overrides: Partial<BuilderDocument> = {}): BuilderDocument {
  return {
    id: createDocumentId(),
    version: '1',
    name: 'Test Document',
    designSystem: {
      id: 'ds-1',
      name: 'Test DS',
      version: '1.0.0',
      tokenSetIds: [],
      componentContracts: [],
      themeId: 'theme-1',
    },
    rootNodes: [],
    nodes: new Map(),
    metadata: { createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
    ...overrides,
  };
}

// ----------------------------------------------------------------------------
// JSON import tests
// ----------------------------------------------------------------------------

describe('importFromJson', () => {
  it('imports a valid BuilderDocument JSON', () => {
    const json = JSON.stringify({
      rootNodes: ['node-1'],
      nodes: {
        'node-1': {
          id: 'node-1',
          contractName: 'Button',
          props: { label: 'Click' },
          slots: {},
          bindings: [],
          metadata: {},
        },
      },
    });
    const doc = makeDoc();
    const result = importFromJson({ format: 'json', content: json }, doc);
    expect(result.status).toBe('clean');
    expect(result.addedNodeIds).toContain('node-1');
    expect(result.document.nodes.has('node-1')).toBe(true);
  });

  it('returns failed for invalid JSON', () => {
    const result = importFromJson({ format: 'json', content: '{invalid' }, makeDoc());
    expect(result.status).toBe('failed');
    expect(result.errorMessage).toContain('JSON parse error');
  });

  it('returns failed for JSON missing rootNodes', () => {
    const result = importFromJson({ format: 'json', content: '{"foo": "bar"}' }, makeDoc());
    expect(result.status).toBe('failed');
    expect(result.errorMessage).toContain('missing rootNodes');
  });

  it('round-trips with full fidelity', () => {
    const doc = makeDoc({
      name: 'Original',
      rootNodes: ['id-1'],
      nodes: new Map([
        [
          'id-1',
          {
            id: 'id-1',
            contractName: 'Card',
            props: { title: 'Test' },
            slots: {},
            bindings: [],
            metadata: {},
          },
        ],
      ]),
    });
    const serialized = JSON.stringify(doc);
    const result = importFromJson({ format: 'json', content: serialized }, makeDoc());
    expect(result.fidelity.canRoundTrip).toBe(true);
    expect(result.fidelity.confidence).toBe(1);
  });
});

// ----------------------------------------------------------------------------
// TSX import tests
// ----------------------------------------------------------------------------

describe('importFromTsx', () => {
  it('imports known components from TSX', () => {
    const tsx = '<Button variant="primary" disabled />';
    const contracts = new Set(['Button']);
    const result = importFromTsx({ format: 'tsx', content: tsx }, makeDoc(), contracts);
    expect(result.status).toBe('clean');
    expect(result.addedNodeIds.length).toBe(1);
    const node = result.document.nodes.get(result.addedNodeIds[0]);
    expect(node?.contractName).toBe('Button');
    expect(node?.props['variant']).toBe('primary');
    expect(node?.props['disabled']).toBe(true);
  });

  it('flags unknown components as review-required', () => {
    const tsx = '<UnknownWidget />';
    const contracts = new Set(['Button']);
    const result = importFromTsx({ format: 'tsx', content: tsx }, makeDoc(), contracts);
    expect(result.status).toBe('review-required');
    expect(result.conflicts.some((c) => c.conflictType === 'unsupported-pattern')).toBe(true);
  });

  it('parses multiple components', () => {
    const tsx = '<Button /><Card /><TextField />';
    const contracts = new Set(['Button', 'Card', 'TextField']);
    const result = importFromTsx({ format: 'tsx', content: tsx }, makeDoc(), contracts);
    expect(result.addedNodeIds.length).toBe(3);
  });

  it('marks review-required for empty TSX', () => {
    const result = importFromTsx({ format: 'tsx', content: '' }, makeDoc(), new Set());
    expect(result.fidelity.lossPoints.some((lp) => lp.type === 'custom-code')).toBe(true);
  });

  it('reduces confidence with loss points', () => {
    const tsx = '<Button /><Unknown />';
    const contracts = new Set(['Button']);
    const result = importFromTsx({ format: 'tsx', content: tsx }, makeDoc(), contracts);
    expect(result.fidelity.confidence).toBeLessThan(1);
    expect(result.fidelity.confidence).toBeGreaterThan(0);
  });
});

// ----------------------------------------------------------------------------
// HTML import tests
// ----------------------------------------------------------------------------

describe('importFromHtml', () => {
  it('imports ghatana-* custom elements', () => {
    const html = '<ghatana-button variant="contained" />';
    const result = importFromHtml({ format: 'html', content: html }, makeDoc());
    expect(result.status).toBe('clean');
    expect(result.addedNodeIds.length).toBe(1);
    const node = result.document.nodes.get(result.addedNodeIds[0]);
    expect(node?.contractName).toBe('Button');
  });

  it('converts kebab-case to PascalCase', () => {
    const html = '<ghatana-text-field />';
    const result = importFromHtml({ format: 'html', content: html }, makeDoc());
    const node = result.document.nodes.get(result.addedNodeIds[0]);
    expect(node?.contractName).toBe('TextField');
  });

  it('flags non-ghatana elements as loss points', () => {
    const html = '<div><span>Hello</span></div>';
    const result = importFromHtml({ format: 'html', content: html }, makeDoc());
    expect(result.fidelity.lossPoints.some((lp) => lp.type === 'unsupported-pattern')).toBe(true);
    expect(result.status).toBe('review-required');
  });

  it('is clean for ghatana-only HTML', () => {
    const html = '<ghatana-button /><ghatana-card />';
    const result = importFromHtml({ format: 'html', content: html }, makeDoc());
    expect(result.status).toBe('clean');
    expect(result.fidelity.canRoundTrip).toBe(true);
  });
});

// ----------------------------------------------------------------------------
// Unified dispatcher tests
// ----------------------------------------------------------------------------

describe('importSource', () => {
  it('dispatches to json importer', () => {
    const json = JSON.stringify({ rootNodes: [], nodes: {} });
    const result = importSource({ format: 'json', content: json }, makeDoc());
    expect(result.status).toBe('clean');
  });

  it('dispatches to tsx importer', () => {
    const result = importSource({ format: 'tsx', content: '<Button />', path: 'test.tsx' }, makeDoc(), {
      designSystemContracts: new Set(['Button']),
    });
    expect(result.addedNodeIds.length).toBe(1);
  });

  it('dispatches to html importer', () => {
    const result = importSource({ format: 'html', content: '<ghatana-button />' }, makeDoc());
    expect(result.addedNodeIds.length).toBe(1);
  });
});
