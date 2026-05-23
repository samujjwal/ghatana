/**
 * @fileoverview Tests for ModelToBuilderAdapter.
 *
 * Verifies that:
 * - projectModelToBuilderDocument returns a valid BuilderDocument
 * - Component, page, and layout nodes in the model are projected as BuilderDocument nodes
 * - Hook and utility nodes are excluded (filtered by includeKinds)
 * - The returned document is owned by the model ID
 * - Metadata description is set from the model label
 * - An empty model produces an empty (but valid) BuilderDocument
 *
 * @doc.type test
 * @doc.purpose ModelToBuilderAdapter unit tests
 * @doc.layer studio
 * @doc.pattern UnitTest
 */

import { describe, it, expect } from 'vitest';
import {
  materializeProjectedBuilderDocument,
  projectModelToBuilderDocument,
} from '../ModelToBuilderAdapter.js';
import { createLogicalArtifactModel } from '@ghatana/artifact-contracts';
import type { LogicalArtifactModel, ArtifactNode } from '@ghatana/artifact-contracts';
import type { ProjectedBuilderDocument } from '@ghatana/artifact-compiler-ts';

// ============================================================================
// Helpers
// ============================================================================

function makeNode(
  id: string,
  kind: ArtifactNode['kind'],
  displayName = id,
): ArtifactNode {
  return {
    id,
    displayName,
    kind,
    exportedSymbols: [displayName],
    inferredProps: {},
    usesDesignSystem: false,
    classificationConfidence: 1,
    metadata: {},
  };
}

function makeModel(
  modelId: string,
  label: string,
  nodes: ArtifactNode[] = [],
): LogicalArtifactModel {
  const base = createLogicalArtifactModel(modelId, label);
  const nodeMap: LogicalArtifactModel['nodes'] = {};
  for (const n of nodes) {
    nodeMap[n.id] = n;
  }
  return { ...base, nodes: nodeMap };
}

// ============================================================================
// Tests
// ============================================================================

describe('projectModelToBuilderDocument', () => {
  describe('empty model', () => {
    it('returns a valid BuilderDocument for an empty model', () => {
      const model = makeModel('empty-id', 'Empty Model');
      const doc = projectModelToBuilderDocument(model);

      expect(doc).toBeDefined();
      expect(typeof doc.documentId).toBe('string');
      expect(doc.schemaVersion).toBeDefined();
    });

    it('sets the document owner to the model ID', () => {
      const model = makeModel('my-model-id', 'My Label');
      const doc = projectModelToBuilderDocument(model);

      // createBuilderDocument(owner, ...) uses the first arg as owner, not documentId
      expect(doc.owner).toBe('my-model-id');
    });

    it('sets the metadata description to the model label', () => {
      const model = makeModel('m1', 'Widget Repo');
      const doc = projectModelToBuilderDocument(model);

      expect((doc.metadata as { description?: string }).description).toBe('Widget Repo');
    });
  });

  describe('node projection', () => {
    it('includes component-kind nodes as BuilderDocument nodes', () => {
      const model = makeModel('m1', 'Test', [
        makeNode('btn', 'component', 'Button'),
      ]);
      const doc = projectModelToBuilderDocument(model);
      const nodeList = Object.values(doc.nodes);
      const contractNames = nodeList.map((n) => n.contractName);
      expect(contractNames).toContain('Button');
    });

    it('carries inferred source props into projected Builder nodes for editing', () => {
      const buttonNode = {
        ...makeNode('src/Button.tsx', 'component', 'Button'),
        inferredProps: { label: 'string' },
      };
      const model = makeModel('m1', 'Test', [buttonNode]);

      const doc = projectModelToBuilderDocument(model);

      expect(doc.nodes['src/Button.tsx']?.props).toHaveProperty('label');
    });

    it('includes page-kind nodes', () => {
      const model = makeModel('m1', 'Test', [
        makeNode('home', 'page', 'HomePage'),
      ]);
      const doc = projectModelToBuilderDocument(model);
      const nodeList = Object.values(doc.nodes);
      const contractNames = nodeList.map((n) => n.contractName);
      expect(contractNames).toContain('HomePage');
    });

    it('includes layout-kind nodes', () => {
      const model = makeModel('m1', 'Test', [
        makeNode('shell', 'layout', 'AppShell'),
      ]);
      const doc = projectModelToBuilderDocument(model);
      const nodeList = Object.values(doc.nodes);
      const contractNames = nodeList.map((n) => n.contractName);
      expect(contractNames).toContain('AppShell');
    });

    it('excludes hook-kind nodes', () => {
      const model = makeModel('m1', 'Test', [
        makeNode('useAuth', 'hook', 'useAuth'),
      ]);
      const doc = projectModelToBuilderDocument(model);
      const nodeList = Object.values(doc.nodes);
      const contractNames = nodeList.map((n) => n.contractName);
      // Hooks should not appear in the builder document
      expect(contractNames).not.toContain('useAuth');
    });

    it('excludes utility-kind nodes', () => {
      const model = makeModel('m1', 'Test', [
        makeNode('formatDate', 'utility', 'formatDate'),
      ]);
      const doc = projectModelToBuilderDocument(model);
      const nodeList = Object.values(doc.nodes);
      const contractNames = nodeList.map((n) => n.contractName);
      expect(contractNames).not.toContain('formatDate');
    });

    it('excludes service-kind nodes', () => {
      const model = makeModel('m1', 'Test', [
        makeNode('apiService', 'service', 'ApiService'),
      ]);
      const doc = projectModelToBuilderDocument(model);
      const nodeList = Object.values(doc.nodes);
      const contractNames = nodeList.map((n) => n.contractName);
      expect(contractNames).not.toContain('ApiService');
    });

    it('produces a document with one node per included artifact', () => {
      const model = makeModel('m1', 'Test', [
        makeNode('btn', 'component', 'Button'),
        makeNode('home', 'page', 'HomePage'),
        makeNode('hook', 'hook', 'useAuth'),
      ]);
      const doc = projectModelToBuilderDocument(model);
      // RootContainer is always present; plus Button and HomePage (2 included nodes)
      const nodeList = Object.values(doc.nodes);
      const contractNames = nodeList.map((n) => n.contractName);
      // Should have Button + HomePage (hook excluded), plus the implicit RootContainer
      expect(contractNames).toContain('Button');
      expect(contractNames).toContain('HomePage');
      expect(contractNames).not.toContain('useAuth');
    });

    it('preserves projected node IDs for coherent slot references', () => {
      const projected = makeProjectedDocument({
        parent: {
          id: 'parent',
          contractName: 'Parent',
          props: {},
          slots: { children: ['child'] },
          bindings: [],
          metadata: { name: 'Parent', locked: false, hidden: false },
        },
        child: {
          id: 'child',
          contractName: 'Child',
          props: {},
          slots: {},
          bindings: [],
          metadata: { name: 'Child', locked: false, hidden: false },
        },
      });

      const doc = materializeProjectedBuilderDocument(projected, {
        owner: 'test-owner',
        description: 'Projected',
      });

      expect(doc.nodes.parent?.slots.children).toEqual(['child']);
      expect(doc.nodes.child?.contractName).toBe('Child');
    });

    it('drops invalid projected slot IDs and records diagnostics', () => {
      const projected = makeProjectedDocument({
        parent: {
          id: 'parent',
          contractName: 'Parent',
          props: {},
          slots: { children: ['child', 'missing'] },
          bindings: [],
          metadata: { name: 'Parent', locked: false, hidden: false },
        },
        child: {
          id: 'child',
          contractName: 'Child',
          props: {},
          slots: {},
          bindings: [],
          metadata: { name: 'Child', locked: false, hidden: false },
        },
      });

      const doc = materializeProjectedBuilderDocument(projected, {
        owner: 'test-owner',
      });

      expect(doc.nodes.parent?.slots.children).toEqual(['child']);
      expect(doc.metadata.artifactProjectionDiagnostics).toEqual([
        'Dropped invalid slot reference "missing" from "parent.children": unknown.',
      ]);
    });
  });
});

function makeProjectedDocument(
  nodes: ProjectedBuilderDocument['nodes'],
): ProjectedBuilderDocument {
  const rootChildren = Object.keys(nodes);
  return {
    schemaVersion: '1.0.0',
    documentId: 'projected-doc',
    label: 'Projected',
    nodes,
    layout: {
      rootId: 'root',
      nodes: {
        root: { children: rootChildren },
        ...Object.fromEntries(rootChildren.map((id) => [id, { children: [] }])),
      },
    },
    metadata: {
      createdAt: new Date(0).toISOString(),
      updatedAt: new Date(0).toISOString(),
      sourceModelId: 'model',
    },
  };
}
