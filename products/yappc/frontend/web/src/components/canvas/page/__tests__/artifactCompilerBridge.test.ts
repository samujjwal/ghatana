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
  });
});
