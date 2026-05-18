import { mkdtemp, mkdir, writeFile } from 'fs/promises';
import { tmpdir } from 'os';
import { join } from 'path';
import { afterEach, describe, expect, it } from 'vitest';

import type { ArtifactExtractor, ExtractionResult } from '../../extractors/types';
import type { RepositorySnapshot } from '../../source-providers/types';
import { SynthesisPipeline } from '../pipeline';

const tempRoots: string[] = [];

async function createTempRepo(): Promise<string> {
  const rootPath = await mkdtemp(join(tmpdir(), 'yappc-pipeline-boundary-'));
  tempRoots.push(rootPath);
  return rootPath;
}

function createSnapshot(rootPath: string): RepositorySnapshot {
  return {
    snapshotId: 'snapshot-boundary-1',
    snapshotRef: {
      provider: 'local-folder',
      repoId: rootPath,
      commitSha: 'abc123',
    },
    localRootPath: rootPath,
    files: [
      {
        relativePath: 'src/InScope.tsx',
        absolutePath: join(rootPath, 'src', 'InScope.tsx'),
        materialized: true,
        sizeBytes: 64,
        lastModifiedAt: '2026-05-17T00:00:00.000Z',
        checksum: 'a'.repeat(64),
      },
    ],
    snapshotAt: '2026-05-17T00:00:00.000Z',
    shallow: false,
    diagnostics: [],
    contentHash: 'b'.repeat(64),
    contentChecksum: 'c'.repeat(64),
    tenantId: 'tenant-1',
    workspaceId: 'workspace-1',
    projectId: 'project-1',
  };
}

const passthroughExtractor: ArtifactExtractor = {
  identity: {
    id: 'snapshot-boundary-extractor',
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
      extractorId: 'snapshot-boundary-extractor',
      extractorVersion: '1.0.0',
      artifact: record,
      nodes: [],
      edges: [],
      unresolvedEdges: [],
      modelElements: [],
      residualIslands: [],
      errors: [],
      warnings: [],
      durationMs: 1,
    };
  },
};

describe('SynthesisPipeline snapshot boundary', () => {
  afterEach(async () => {
    await Promise.all(
      tempRoots.splice(0).map(async rootPath => {
        const { rm } = await import('fs/promises');
        await rm(rootPath, { recursive: true, force: true });
      }),
    );
  });

  it('scans and extracts only files present in snapshot.files', async () => {
    const rootPath = await createTempRepo();
    await mkdir(join(rootPath, 'src'), { recursive: true });
    await writeFile(join(rootPath, 'src', 'InScope.tsx'), 'export const InScope = () => null;\n');
    await writeFile(join(rootPath, 'src', 'OutOfScope.tsx'), 'export const OutOfScope = () => null;\n');

    const snapshot = createSnapshot(rootPath);
    const pipeline = new SynthesisPipeline({
      extractors: [passthroughExtractor],
      scannerConfig: {
        includeGlobs: ['**/*.tsx'],
        excludeGlobs: [],
      },
    });

    const result = await pipeline.runFromSnapshot(snapshot);

    const extractedPaths = result.extractionResults.map(entry => entry.artifact.relativePath);

    expect(result.errors).toEqual([]);
    expect(result.stats.scannedFiles).toBe(1);
    expect(extractedPaths).toEqual(['src/InScope.tsx']);
    expect(extractedPaths).not.toContain('src/OutOfScope.tsx');
  });
});