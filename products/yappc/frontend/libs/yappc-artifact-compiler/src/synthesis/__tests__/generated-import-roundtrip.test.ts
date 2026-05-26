/**
 * @fileoverview Generated artifact import round-trip tests.
 *
 * @doc.type test
 * @doc.purpose Proves generated YAPPC source is decompiled into SemanticProductModel IR and round-trips without model drift
 * @doc.layer product
 * @doc.pattern Integration Test
 */

import { mkdir, mkdtemp, rm, writeFile } from 'fs/promises';
import { tmpdir } from 'os';
import { join } from 'path';
import { afterEach, describe, expect, it } from 'vitest';

import { buildChangePlan } from '../../compile-back/types';
import {
  ExtractorRegistry,
  registerCanonicalExtractors,
} from '../../extractors/extractor-registry';
import type { RepositorySnapshot } from '../../source-providers/types';
import { SynthesisPipeline } from '../pipeline';

const tempRoots: string[] = [];

async function createGeneratedProject(): Promise<string> {
  const rootPath = await mkdtemp(join(tmpdir(), 'yappc-generated-roundtrip-'));
  tempRoots.push(rootPath);
  await mkdir(join(rootPath, 'src', 'components'), { recursive: true });
  await writeFile(
    join(rootPath, 'src', 'components', 'GeneratedCounter.tsx'),
    `import { useState } from 'react';

export interface GeneratedCounterProps {
  label: string;
  initial?: number;
}

export function GeneratedCounter({ label, initial = 0 }: GeneratedCounterProps) {
  const [count, setCount] = useState(initial);

  return (
    <section aria-label={label}>
      <button onClick={() => setCount(value => value + 1)}>
        {label}: {count}
      </button>
    </section>
  );
}
`,
  );
  return rootPath;
}

function createGeneratedSnapshot(rootPath: string): RepositorySnapshot {
  const relativePath = 'src/components/GeneratedCounter.tsx';
  return {
    snapshotId: 'generated-snapshot-1',
    snapshotRef: {
      provider: 'artifact-registry',
      repoId: 'tenant-1/workspace-1/project-1/generated',
      commitSha: 'generated-commit-1',
    },
    localRootPath: rootPath,
    files: [
      {
        relativePath,
        absolutePath: join(rootPath, relativePath),
        materialized: true,
        sizeBytes: 512,
        lastModifiedAt: '2026-05-26T00:00:00.000Z',
        checksum: 'a'.repeat(64),
      },
    ],
    snapshotAt: '2026-05-26T00:00:00.000Z',
    shallow: false,
    diagnostics: [],
    contentHash: 'b'.repeat(64),
    contentChecksum: 'c'.repeat(64),
    tenantId: 'tenant-1',
    workspaceId: 'workspace-1',
    projectId: 'project-1',
  };
}

function createPipeline(registry: ExtractorRegistry): SynthesisPipeline {
  return new SynthesisPipeline(
    {
      extractors: registry.getAll(),
      scannerConfig: {
        includeGlobs: ['src/**/*.tsx'],
        excludeGlobs: [],
        respectGitignore: false,
      },
    },
    registry,
  );
}

describe('generated artifact compiler import round-trip', () => {
  afterEach(async () => {
    await Promise.all(
      tempRoots.splice(0).map(rootPath => rm(rootPath, { recursive: true, force: true })),
    );
  });

  it('decompiles generated React source into logical IR and recompares with zero model diff', async () => {
    const rootPath = await createGeneratedProject();
    const snapshot = createGeneratedSnapshot(rootPath);
    const registry = registerCanonicalExtractors(new ExtractorRegistry());
    const pipeline = createPipeline(registry);

    const first = await pipeline.runFromSnapshot(snapshot);
    const second = await pipeline.runFromSnapshot(snapshot);

    expect(first.errors).toEqual([]);
    expect(first.stats.scannedFiles).toBe(1);
    expect(first.stats.modelElementsGenerated).toBe(1);

    const component = first.model.elements.find(element => element.name === 'GeneratedCounter');
    expect(component).toBeDefined();
    expect(component?.kind).toBe('component');
    if (component?.kind === 'component') {
      expect(component.provenance.extractorId).toBe('typescript-component');
      expect(component.provenance.sourcePaths).toEqual(['src/components/GeneratedCounter.tsx']);
      expect(component.props.map(prop => prop.name)).toEqual(['label', 'initial']);
      expect(component.props.find(prop => prop.name === 'initial')?.defaultValue).toBe(0);
      expect(component.graphNodeIds[0]).toContain('artifact://artifact-registry/');
    }

    expect(second.model.id).toBe(first.model.id);
    expect(second.graph.nodes.map(node => node.id)).toEqual(first.graph.nodes.map(node => node.id));
    expect(buildChangePlan(first.model.elements, second.model.elements)).toEqual([]);
  });
});
