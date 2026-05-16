import { mkdtemp, mkdir, writeFile } from 'fs/promises';
import { tmpdir } from 'os';
import { join } from 'path';
import { afterEach, describe, expect, it } from 'vitest';
import type { ArtifactExtractor, ExtractionResult } from '../../extractors/types';
import { SynthesisPipeline } from '../pipeline';

const tempRoots: string[] = [];

async function createTempRepo(): Promise<string> {
  const rootPath = await mkdtemp(join(tmpdir(), 'yappc-pipeline-'));
  tempRoots.push(rootPath);
  return rootPath;
}

const lowConfidenceExtractor: ArtifactExtractor = {
  identity: {
    id: 'test-extractor',
    version: '1.0.0',
    supportedKinds: ['component-implementation'],
    supportedLanguages: ['tsx'],
    supportedFrameworks: ['react'],
  },
  canExtract(record) {
    return record.relativePath.endsWith('.tsx');
  },
  async extract(record): Promise<ExtractionResult> {
    return {
      extractorId: 'test-extractor',
      extractorVersion: '1.0.0',
      artifact: record,
      nodes: [],
      edges: [],
      unresolvedEdges: [],
      modelElements: [
        {
          id: 'df9d0d6d-8ff4-4d20-b46d-eec8ec9dcbf2',
          kind: 'component',
          name: 'ExampleCard',
          confidence: 0.2,
          provenance: {
            extractorId: 'test-extractor',
            extractorVersion: '1.0.0',
            sourcePaths: [record.relativePath],
            kind: 'exact',
            extractedAt: '2026-05-15T00:00:00.000Z',
          },
          securityFlags: [],
          privacyFlags: [],
          tags: [],
          graphNodeIds: ['graph-node-1'],
          sourceRefs: [record.relativePath],
          residualIslandIds: [],
          contractName: 'ExampleCard',
          props: [],
          slots: [],
          events: [],
          variants: [],
          stateConnections: [],
          dataDependencies: [],
          styleDependencies: [],
          storyIds: [],
          builderCanvasHints: {},
        },
      ],
      residualIslands: [],
      errors: [],
      warnings: [],
      durationMs: 1,
    };
  },
};

describe('SynthesisPipeline low-confidence residuals', () => {
  afterEach(async () => {
    await Promise.all(
      tempRoots.splice(0).map(async rootPath => {
        const { rm } = await import('fs/promises');
        await rm(rootPath, { recursive: true, force: true });
      }),
    );
  });

  it('preserves raw source, checksum, and full-file span metadata', async () => {
    const rootPath = await createTempRepo();
    await mkdir(join(rootPath, 'src'), { recursive: true });
    const source = [
      "import React from 'react';",
      '',
      'export function ExampleCard(): JSX.Element {',
      '  return <div>Example</div>;',
      '}',
      '',
    ].join('\n');
    await writeFile(join(rootPath, 'src', 'ExampleCard.tsx'), source);

    const pipeline = new SynthesisPipeline({
      extractors: [lowConfidenceExtractor],
      residualConfidenceThreshold: 0.5,
      scannerConfig: {
        includeGlobs: ['**/*.tsx'],
        excludeGlobs: [],
      },
    });

    const result = await pipeline.runFromLocalPath(rootPath);

    expect(result.model.residualIslandIds).toEqual(['df9d0d6d-8ff4-4d20-b46d-eec8ec9dcbf2']);
    expect(result.stats.residualIslandsGenerated).toBe(1);
    const residual = result.residualIslands[0];
    expect(residual).toBeDefined();
    expect(residual?.originalSource).toBe(source);
    expect(residual?.rawFragmentRef).toBe('src/ExampleCard.tsx#full-file');
    expect(residual?.checksum).toHaveLength(64);
    expect(residual?.sourceLocation).toEqual({
      filePath: 'src/ExampleCard.tsx',
      startLine: 0,
      startColumn: 0,
      endLine: 5,
      endColumn: 0,
    });
    expect(residual?.reviewRequired).toBe(true);
    expect(residual?.risk).toBe('high');
    expect(result.model.elements).toHaveLength(0);
    expect(result.warnings).toEqual([]);
    expect(result.errors).toEqual([]);
  });
});