/**
 * @fileoverview Integration tests for source acquisition + synthesis.
 */

import { execFile } from 'child_process';
import { mkdtemp, mkdir, writeFile } from 'fs/promises';
import { tmpdir } from 'os';
import { join } from 'path';
import { promisify } from 'util';
import { afterEach, describe, expect, it } from 'vitest';

import type { ArtifactExtractor, ExtractionResult } from '../extractors/types';
import { LocalFolderProvider } from '../source-providers/local-folder-provider';
import { SynthesisPipeline } from '../synthesis/pipeline';

const execFileAsync = promisify(execFile);
const tempRoots: string[] = [];

async function createTempRepo(): Promise<string> {
  const rootPath = await mkdtemp(join(tmpdir(), 'yappc-import-flow-'));
  tempRoots.push(rootPath);
  return rootPath;
}

const componentExtractor: ArtifactExtractor = {
  identity: {
    id: 'integration-extractor',
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
      extractorId: 'integration-extractor',
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
            extractorId: 'integration-extractor',
            extractorVersion: '1.0.0',
            sourcePaths: [record.relativePath],
            kind: 'exact',
            extractedAt: '2026-05-15T00:00:00.000Z',
          },
          securityFlags: [],
          privacyFlags: [],
          tags: [],
          graphNodeIds: [],
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

describe('Import pipeline E2E flow', () => {
  afterEach(async () => {
    await Promise.all(
      tempRoots.splice(0).map(async (rootPath) => {
        const { rm } = await import('fs/promises');
        await rm(rootPath, { recursive: true, force: true });
      }),
    );
  });

  it('creates a content-pinned dirty snapshot and carries it through synthesis', async () => {
    const rootPath = await createTempRepo();
    await execFileAsync('git', ['init'], { cwd: rootPath });
    await execFileAsync('git', ['config', 'user.email', 'copilot@example.com'], { cwd: rootPath });
    await execFileAsync('git', ['config', 'user.name', 'GitHub Copilot'], { cwd: rootPath });

    await mkdir(join(rootPath, 'src'), { recursive: true });
    const absolutePath = join(rootPath, 'src', 'ExampleCard.tsx');
    await writeFile(
      absolutePath,
      [
        "import React from 'react';",
        '',
        'export function ExampleCard(): JSX.Element {',
        '  return <div>Example</div>;',
        '}',
        '',
      ].join('\n'),
    );
    await execFileAsync('git', ['add', '.'], { cwd: rootPath });
    await execFileAsync('git', ['commit', '-m', 'initial'], { cwd: rootPath });
    await writeFile(
      absolutePath,
      [
        "import React from 'react';",
        '',
        'export function ExampleCard(): JSX.Element {',
        '  return <section>Example</section>;',
        '}',
        '',
      ].join('\n'),
    );

    const provider = new LocalFolderProvider();
    const snapshot = await provider.resolve(rootPath);

    expect(snapshot.snapshotRef.provider).toBe('local-folder');
    expect(snapshot.snapshotRef.commitSha).toBeDefined();
    expect(snapshot.diagnostics).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'LOCAL_DIRTY_WORKTREE',
        }),
      ]),
    );

    const pipeline = new SynthesisPipeline({
      extractors: [componentExtractor],
      residualConfidenceThreshold: 0.5,
      scannerConfig: {
        includeGlobs: ['**/*.tsx'],
        excludeGlobs: [],
      },
    });

    const result = await pipeline.runFromSnapshot(snapshot);

    expect(result.snapshot?.snapshotRef.commitSha).toBe(snapshot.snapshotRef.commitSha);
    expect(result.stats.scannedFiles).toBeGreaterThan(0);
    expect(result.stats.residualIslandsGenerated).toBe(1);
    expect(result.model.residualIslandIds).toEqual(['df9d0d6d-8ff4-4d20-b46d-eec8ec9dcbf2']);
    expect(result.residualIslands[0]?.sourceLocation.filePath).toBe('src/ExampleCard.tsx');
    expect(result.residualIslands[0]?.reviewRequired).toBe(true);
    expect(result.errors).toEqual([]);
  });
});
