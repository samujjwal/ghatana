/**
 * @fileoverview Integration tests for the patch review flow.
 */

import { mkdtemp, mkdir, writeFile } from 'fs/promises';
import { tmpdir } from 'os';
import { join } from 'path';
import { afterEach, describe, expect, it } from 'vitest';

import { buildChangePlan } from '../compile-back/types';
import { PatchCoordinator } from '../compile-back/patch-coordinator';
import type { PatchContext } from '../compile-back/types';
import type { SemanticModelElement } from '../model/types';

const tempRoots: string[] = [];

async function createTempRoot(): Promise<string> {
  const rootPath = await mkdtemp(join(tmpdir(), 'yappc-patch-review-'));
  tempRoots.push(rootPath);
  return rootPath;
}

function buildComponentElement(name: string): SemanticModelElement {
  return {
    id: '91c394e7-d45f-4dd6-9f0c-c2a2cc0d18c0',
    kind: 'component',
    name,
    confidence: 0.95,
    provenance: {
      extractorId: 'test-extractor',
      extractorVersion: '1.0.0',
      sourcePaths: ['src/Button.tsx'],
      kind: 'exact',
      extractedAt: '2026-05-15T00:00:00.000Z',
    },
    securityFlags: [],
    privacyFlags: [],
    tags: [],
    graphNodeIds: [],
    sourceRefs: ['src/Button.tsx'],
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

describe('Patch review E2E flow', () => {
  afterEach(async () => {
    await Promise.all(
      tempRoots.splice(0).map(async (rootPath) => {
        const { rm } = await import('fs/promises');
        await rm(rootPath, { recursive: true, force: true });
      }),
    );
  });

  it('builds, validates, and reviews a real rename patch end to end', async () => {
    const rootPath = await createTempRoot();
    const relativePath = 'src/Button.tsx';
    const absolutePath = join(rootPath, relativePath);
    await mkdir(join(rootPath, 'src'), { recursive: true });
    await writeFile(
      absolutePath,
      [
        'export function Button(): JSX.Element {',
        '  return <Button />;',
        '}',
        '',
      ].join('\n'),
    );

    const before = [buildComponentElement('Button')];
    const after = [buildComponentElement('PrimaryButton')];
    const changeOps = buildChangePlan(before, after);

    expect(changeOps).toHaveLength(1);
    expect(changeOps[0]?.kind).toBe('rename-component');

    const context: PatchContext = {
      readFile: async () => (await import('fs/promises')).readFile(absolutePath, 'utf-8'),
      fileExists: async () => true,
      residuals: new Map(),
      elementSourcePaths: new Map([[before[0]!.id, [relativePath]]]),
    };

    const coordinator = new PatchCoordinator();
    const patchSet = await coordinator.buildPatchSet(
      changeOps,
      new Map([[before[0]!.id, before[0]!]]),
      new Map(),
      context,
    );

    expect(patchSet.patches).toHaveLength(1);
    expect(patchSet.patches[0]?.diff).toContain('+export function PrimaryButton(): JSX.Element {');
    expect(patchSet.patches[0]?.ranges).toHaveLength(1);
    expect(patchSet.patches[0]?.baseChecksum).toBeDefined();
    expect(patchSet.patches[0]?.targetChecksum).toBeDefined();

    const changePlan = {
      id: 'e6d6e4eb-f56c-463d-b82c-71de11cb4f92',
      sourceModelId: '8f459e6c-7cbc-4c5f-9caa-f81c5728c04f',
      targetModelId: 'b77aa68c-8eab-4d20-a355-cd05844f9b56',
      changes: changeOps.map((op) => ({
        id: '5a92bb68-1a11-4b42-a02f-3f2ac85a8180',
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

    const validation = await coordinator.validateChangePlan(
      changePlan,
      new Map([[before[0]!.id, before[0]!]]),
      new Map(),
    );
    expect(validation.valid).toBe(true);

    const dryRun = await coordinator.dryRunPatchSet(patchSet, context);
    expect(dryRun.valid).toBe(true);

    const reviewBundle = await coordinator.buildReviewBundle(
      changePlan,
      patchSet,
      dryRun,
      new Map(),
      new Map([[before[0]!.id, before[0]!]]),
    );
    expect(reviewBundle.patches).toHaveLength(1);
    expect(reviewBundle.validation.valid).toBe(true);
    expect(reviewBundle.patches[0]?.ranges).toHaveLength(1);
    expect(reviewBundle.patches[0]?.validationStatus).toBe('validated');

    const rollbackMetadata = await coordinator.createRollbackMetadata(
      changePlan.id,
      patchSet.id,
      '3a0e9c94-e315-4c20-b2c5-53db9aaf84db',
      'ec2d22c6-1529-425b-8b4e-d041f79bf16c',
      'Revert rename during review',
      'user-1',
    );
    expect(rollbackMetadata.originalChangePlanId).toBe(changePlan.id);
    expect(rollbackMetadata.success).toBe(false);
    expect(rollbackMetadata.reason).toBe('Revert rename during review');
  });
});
