import { describe, expect, it } from 'vitest';
import { createDocumentId, serializeDocument } from '@ghatana/ui-builder';

import {
  compileImportedSourceToPageArtifacts,
  compileSemanticModelToPageArtifacts,
  importPageArtifactsFromCode,
} from '../artifactCompilerBridge';
import { createEmptyBuilderDocument } from '../pageArtifactDocument';

describe('artifactCompilerBridge', () => {
  it('creates a default page artifact when semantic model has no pages', () => {
    const artifacts = compileSemanticModelToPageArtifacts(
      { id: 'model-1', name: 'No Pages Model' },
      'tester',
    );

    expect(artifacts).toHaveLength(1);
    expect(artifacts[0]?.artifactId).toBe('model-1');
    expect(artifacts[0]?.source).toBe('generated');
  });

  it('creates one artifact per semantic page and preserves residual islands', () => {
    const pageDocument = createEmptyBuilderDocument('Page A', 'tester');
    const artifacts = compileSemanticModelToPageArtifacts(
      {
        id: 'model-2',
        pages: [
          {
            id: 'page-1',
            name: 'Page A',
            builderDocument: pageDocument,
            residualIslands: [{ id: 'island-1' }, { id: 'island-2' }],
            confidence: 0.8,
          },
        ],
      },
      'tester',
    );

    expect(artifacts).toHaveLength(1);
    expect(artifacts[0]?.artifactId).toBe('page-1');
    expect(artifacts[0]?.source).toBe('decompiled');
    expect(artifacts[0]?.residualIslandIds).toEqual(['island-1', 'island-2']);
    expect(artifacts[0]?.roundTripFidelity?.confidence).toBe(0.8);
    expect(artifacts[0]?.artifactGraph?.provenance.residualIslandIds).toEqual(['island-1', 'island-2']);
    expect(artifacts[0]?.artifactGraph?.edges[0]).toMatchObject({
      kind: 'part-of',
      from: 'page-1:page',
      to: 'model-2:product',
    });
    expect(artifacts[0]?.artifactGraph?.edges).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          kind: 'residual-of',
          to: 'page-1:page',
        }),
      ]),
    );
  });

  it('persists multi-page semantic imports into one shared product graph', () => {
    const artifacts = compileSemanticModelToPageArtifacts(
      {
        id: 'commerce-app',
        name: 'Commerce App',
        pages: [
          { id: 'home-page', name: 'Home' },
          { id: 'checkout-page', name: 'Checkout' },
        ],
      },
      'tester',
    );

    expect(artifacts).toHaveLength(2);
    expect(artifacts[0]?.artifactGraph?.graphId).toBe('commerce-app:graph');
    expect(artifacts[1]?.artifactGraph?.graphId).toBe('commerce-app:graph');
    expect(artifacts[0]?.artifactGraph?.nodes).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          id: 'commerce-app:product',
          kind: 'product',
          metadata: expect.objectContaining({ pageCount: 2 }),
        }),
        expect.objectContaining({
          id: 'home-page:page',
          kind: 'page',
          metadata: expect.objectContaining({ currentArtifact: true, pageIndex: 0 }),
        }),
        expect.objectContaining({
          id: 'checkout-page:page',
          kind: 'page',
          metadata: expect.objectContaining({ peerPage: true, pageIndex: 1 }),
        }),
      ]),
    );
    expect(artifacts[1]?.artifactGraph?.edges).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          kind: 'part-of',
          from: 'checkout-page:page',
          to: 'commerce-app:product',
        }),
        expect.objectContaining({
          kind: 'part-of',
          from: 'home-page:page',
          to: 'commerce-app:product',
        }),
      ]),
    );
  });

  it('imports artifacts from serialized semantic model text', () => {
    const document = createEmptyBuilderDocument('Imported Page', 'tester');
    const serializedModel = JSON.stringify({
      id: 'semantic-model',
      pages: [
        {
          id: 'import-page-1',
          name: 'Imported Page',
          serializedBuilderDocument: serializeDocument({
            ...document,
            id: createDocumentId(),
          }),
        },
      ],
    });

    const artifacts = importPageArtifactsFromCode(serializedModel, 'tester');

    expect(artifacts).toHaveLength(1);
    expect(artifacts[0]?.artifactId).toBe('import-page-1');
    expect(artifacts[0]?.source).toBe('decompiled');
    expect(artifacts[0]?.serializedBuilderDocument.name).toBe('Imported Page');
  });

  it('creates imported page artifacts from source import metadata', () => {
    const artifacts = compileImportedSourceToPageArtifacts(
      {
        projectId: 'proj-42',
        componentName: 'InboxPanel',
        source: '/tmp/InboxPanel.tsx',
        sourceType: 'tsx',
        importedAt: '2026-05-01T10:00:00.000Z',
      },
      'importer',
    );

    expect(artifacts).toHaveLength(1);
    expect(artifacts[0]?.artifactId).toBe('proj-42-inbox-panel');
    expect(artifacts[0]?.source).toBe('imported');
    expect(artifacts[0]?.updatedAt).toBe('2026-05-01T10:00:00.000Z');
    expect(artifacts[0]?.artifactGraph).toMatchObject({
      graphId: 'proj-42-inbox-panel:graph',
      projectId: 'proj-42',
      sourceType: 'tsx',
      source: '/tmp/InboxPanel.tsx',
      provenance: {
        createdBy: 'importer',
        compiler: 'yappc-artifact-compiler',
      },
    });
  });

  it('preserves source graph nodes, edges, and source locations from extracted components', () => {
    const artifacts = compileImportedSourceToPageArtifacts(
      {
        projectId: 'proj-graph',
        componentName: 'ContactForm',
        source: '/src/ContactForm.tsx',
        sourceType: 'route',
        importedAt: '2026-05-01T10:00:00.000Z',
        extractedComponents: [
          {
            name: 'ContactForm',
            isDefaultExport: true,
            jsxUsage: ['Card', 'Button'],
            props: [{ name: 'onSubmit', type: '() => void', required: true }],
            slots: [{ name: 'children', multiple: true, required: false }],
            hooksUsed: ['useState'],
            accessibility: { role: 'form' },
            sourceLocation: {
              filePath: '/src/ContactForm.tsx',
              startLine: 3,
              startColumn: 1,
              endLine: 12,
              endColumn: 2,
            },
          },
        ],
      },
      'importer',
    );

    const graph = artifacts[0]?.artifactGraph;
    expect(graph?.nodes).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          id: 'proj-graph-contact-form:source',
          kind: 'source',
        }),
        expect.objectContaining({
          id: 'proj-graph-contact-form:component:ContactForm',
          kind: 'component',
          sourceLocation: expect.objectContaining({ startLine: 3 }),
          metadata: expect.objectContaining({
            defaultExport: true,
            propCount: 1,
            slotCount: 1,
            hookCount: 1,
            hasAccessibilityMetadata: true,
          }),
        }),
        expect.objectContaining({
          id: 'proj-graph-contact-form:component-ref:Card',
          kind: 'component',
        }),
      ]),
    );
    expect(graph?.edges).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          kind: 'derived-from',
          from: 'proj-graph-contact-form:page',
          to: 'proj-graph-contact-form:source',
        }),
        expect.objectContaining({
          kind: 'contains',
          to: 'proj-graph-contact-form:component:ContactForm',
        }),
        expect.objectContaining({
          kind: 'references',
          to: 'proj-graph-contact-form:component-ref:Button',
        }),
      ]),
    );
    expect(graph?.provenance.confidence).toBeGreaterThan(0.7);
  });
});
