import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';

import { buildChangePlan, type PatchContext } from '../../../../../libs/yappc-artifact-compiler/src/compile-back/types';
import { PatchCoordinator } from '../../../../../libs/yappc-artifact-compiler/src/compile-back/patch-coordinator';
import type { SemanticModelElement } from '../../../../../libs/yappc-artifact-compiler/src/model/types';

const cleanupRoots: string[] = [];

function buildComponentElement(id: string, name: string): SemanticModelElement {
  return {
    id,
    kind: 'component',
    name,
    confidence: 0.95,
    provenance: {
      extractorId: 'web-integration-extractor',
      extractorVersion: '1.0.0',
      sourcePaths: ['src/Widget.tsx'],
      kind: 'exact',
      extractedAt: '2026-05-15T00:00:00.000Z',
    },
    securityFlags: [],
    privacyFlags: [],
    tags: [],
    graphNodeIds: [],
    sourceRefs: ['src/Widget.tsx'],
    residualIslandIds: [],
    contractName: name,
    props: [],
    slots: [],
    events: [],
    variants: [],
    stateConnections: [],
    dataDependencies: [],
    styleDependencies: [],
    storyIds: [],
    builderCanvasHints: {},
  };
}

describe('patch-review flow', () => {
  afterEach(async () => {
    await Promise.all(cleanupRoots.splice(0).map((rootPath) => rm(rootPath, { recursive: true, force: true })));
  });

  it('builds, validates, and dry-runs rename patches before review', async () => {
    const rootPath = await mkdtemp(join(tmpdir(), 'yappc-web-patch-flow-'));
    cleanupRoots.push(rootPath);

    const relativePath = 'src/Widget.tsx';
    const absolutePath = join(rootPath, relativePath);
    await mkdir(join(rootPath, 'src'), { recursive: true });
    await writeFile(
      absolutePath,
      [
        'export function Widget(): JSX.Element {',
        '  return <Widget />;',
        '}',
        '',
      ].join('\n'),
    );

    const before = [buildComponentElement('d8f9ca88-49d2-4f31-9e2a-c6e2cbcccbf4', 'Widget')];
    const after = [buildComponentElement('d8f9ca88-49d2-4f31-9e2a-c6e2cbcccbf4', 'PrimaryWidget')];

    const changeOps = buildChangePlan(before, after);
    expect(changeOps).toHaveLength(1);
    expect(changeOps[0]?.kind).toBe('rename-component');

    const patchContext: PatchContext = {
      readFile: async () => readFile(absolutePath, 'utf-8'),
      fileExists: async () => true,
      residuals: new Map(),
      elementSourcePaths: new Map([[before[0]!.id, [relativePath]]]),
    };

    const coordinator = new PatchCoordinator();
    const patchSet = await coordinator.buildPatchSet(
      changeOps,
      new Map([[before[0]!.id, before[0]!]]),
      new Map(),
      patchContext,
    );

    expect(patchSet.patches).toHaveLength(1);
    expect(patchSet.patches[0]?.diff).toContain('PrimaryWidget');
    expect(patchSet.patches[0]?.ranges).toHaveLength(1);
    expect(patchSet.patches[0]?.baseChecksum).toBeDefined();
    expect(patchSet.patches[0]?.targetChecksum).toBeDefined();

    const changePlan = {
      id: '60e8d4e9-c7da-4f20-8ed6-9f71689fd754',
      sourceModelId: '31f11f8d-5ad4-4acf-9d3f-94188d337f48',
      targetModelId: 'f7ce5ed6-26d4-4ef5-b85c-66fcf6a702a8',
      changes: changeOps.map((op) => ({
        id: '203f9552-4ea2-437f-aec1-13f3d6f74442',
        elementId: op.targetElementId,
        kind: op.kind,
        description: op.description,
        before: op.before,
        after: op.after,
        changedAt: new Date().toISOString(),
        autoApplyConfidence: op.autoApplyConfidence,
        reviewRequired: false,
      })),
      createdAt: new Date().toISOString(),
      estimatedImpact: {
        addedElements: 0,
        removedElements: 0,
        modifiedElements: 1,
        affectedFiles: 1,
      },
    };

    const planValidation = await coordinator.validateChangePlan(
      changePlan,
      new Map([[before[0]!.id, before[0]!]]),
      new Map(),
    );
    expect(planValidation.valid).toBe(true);

    const dryRunValidation = await coordinator.dryRunPatchSet(patchSet, patchContext);
    expect(dryRunValidation.valid).toBe(true);

    const reviewBundle = await coordinator.buildReviewBundle(
      changePlan,
      patchSet,
      dryRunValidation,
      new Map(),
      new Map([[before[0]!.id, before[0]!]]),
    );

    expect(reviewBundle.validation.valid).toBe(true);
    expect(reviewBundle.patches[0]?.validationStatus).toBe('validated');
  });
});