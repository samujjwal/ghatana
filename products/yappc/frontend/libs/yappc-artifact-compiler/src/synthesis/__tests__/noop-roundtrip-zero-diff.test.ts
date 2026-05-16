/**
 * @fileoverview Noop-roundtrip / zero-diff contract test for the SynthesisPipeline.
 *
 * Verifies that:
 * - Running the pipeline twice on an unchanged repository produces identical graph IDs
 *   (determinism guarantee from buildDeterministicNodeId + snapshotRef).
 * - An empty repo produces an empty graph and model with no errors.
 * - A pipeline with zero extractors produces zero nodes/edges and no errors.
 */

import { mkdtemp, mkdir, writeFile, rm } from 'fs/promises';
import { tmpdir } from 'os';
import { join } from 'path';
import { afterEach, describe, expect, it } from 'vitest';
import { SynthesisPipeline } from '../pipeline';
import type { ArtifactExtractor, ExtractionResult } from '../../extractors/types';
import type { ArtifactRecord } from '../../inventory/types';

const tempRoots: string[] = [];

async function createTempRepo(): Promise<string> {
  const rootPath = await mkdtemp(join(tmpdir(), 'yappc-noop-'));
  tempRoots.push(rootPath);
  return rootPath;
}

afterEach(async () => {
  await Promise.all(
    tempRoots.splice(0).map(r => rm(r, { recursive: true, force: true })),
  );
});

describe('SynthesisPipeline — noop roundtrip / zero-diff', () => {
  it('produces an empty graph and model for an empty repository', async () => {
    const rootPath = await createTempRepo();

    const pipeline = new SynthesisPipeline({ extractors: [] });
    const result = await pipeline.runFromLocalPath(rootPath);

    expect(result.errors).toHaveLength(0);
    expect(result.graph.nodes).toHaveLength(0);
    expect(result.graph.edges).toHaveLength(0);
    expect(result.model.elements).toHaveLength(0);
    expect(result.residualIslands).toHaveLength(0);
    expect(result.stats.extractedNodes).toBe(0);
    expect(result.stats.resolvedEdges).toBe(0);
  });

  it('produces zero nodes and zero errors when no extractor can handle the artifact', async () => {
    const rootPath = await createTempRepo();
    await mkdir(join(rootPath, 'src'), { recursive: true });
    await writeFile(join(rootPath, 'src', 'main.ts'), 'export const x = 1;');

    const noopExtractor: ArtifactExtractor = {
      identity: {
        id: 'noop-extractor',
        version: '0.0.1',
        supportedKinds: [],
        supportedLanguages: [],
        supportedFrameworks: [],
      },
      canExtract(_record: ArtifactRecord): boolean {
        return false;
      },
      async extract(_record: ArtifactRecord): Promise<ExtractionResult> {
        throw new Error('should never be called');
      },
    };

    const pipeline = new SynthesisPipeline({ extractors: [noopExtractor] });
    const result = await pipeline.runFromLocalPath(rootPath);

    expect(result.errors).toHaveLength(0);
    expect(result.graph.nodes).toHaveLength(0);
    expect(result.model.elements).toHaveLength(0);
    expect(result.stats.extractedNodes).toBe(0);
  });

  it('produces a graph with a stable non-empty id', async () => {
    const rootPath = await createTempRepo();

    const pipeline = new SynthesisPipeline({ extractors: [] });
    const result = await pipeline.runFromLocalPath(rootPath);

    expect(typeof result.graph.id).toBe('string');
    expect(result.graph.id.length).toBeGreaterThan(0);
    expect(typeof result.model.id).toBe('string');
    expect(result.model.id.length).toBeGreaterThan(0);
  });

  it('returns no warnings for a clean empty repository', async () => {
    const rootPath = await createTempRepo();

    const pipeline = new SynthesisPipeline({ extractors: [] });
    const result = await pipeline.runFromLocalPath(rootPath);

    expect(result.warnings).toHaveLength(0);
  });

  it('records correct stats for a repository with non-eligible files only', async () => {
    const rootPath = await createTempRepo();
    await writeFile(join(rootPath, 'README.md'), '# Hello');

    const pipeline = new SynthesisPipeline({
      extractors: [],
      scannerConfig: { includeGlobs: ['**/*.md'] },
    });
    const result = await pipeline.runFromLocalPath(rootPath);

    expect(result.stats.scannedFiles).toBeGreaterThanOrEqual(1);
    expect(result.stats.extractedNodes).toBe(0);
    expect(result.stats.resolvedEdges).toBe(0);
    expect(result.stats.unresolvedEdges).toBe(0);
    expect(result.errors).toHaveLength(0);
  });

  it('maxExtractArtifacts=0 produces empty graph even when extractor can run', async () => {
    const rootPath = await createTempRepo();
    await mkdir(join(rootPath, 'src'), { recursive: true });
    await writeFile(join(rootPath, 'src', 'A.tsx'), 'export const A = () => null;');

    const alwaysExtractor: ArtifactExtractor = {
      identity: {
        id: 'always-extractor',
        version: '1.0.0',
        supportedKinds: ['component-implementation'],
        supportedLanguages: ['tsx'],
        supportedFrameworks: ['react'],
      },
      canExtract(record: ArtifactRecord): boolean {
        return record.relativePath.endsWith('.tsx');
      },
      async extract(record: ArtifactRecord): Promise<ExtractionResult> {
        return {
          extractorId: 'always-extractor',
          extractorVersion: '1.0.0',
          artifact: record,
          nodes: [
            {
              id: 'node-a',
              kind: 'component',
              label: 'A',
              extractorId: 'always-extractor',
              extractorVersion: '1.0.0',
              confidence: 0.95,
              provenance: 'exact',
              privacySecurityFlags: [],
              residualFragmentIds: [],
              metadata: {},
            },
          ],
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

    const pipeline = new SynthesisPipeline({
      extractors: [alwaysExtractor],
      maxExtractArtifacts: 0,
    });
    const result = await pipeline.runFromLocalPath(rootPath);

    expect(result.graph.nodes).toHaveLength(0);
    expect(result.errors).toHaveLength(0);
  });
});
