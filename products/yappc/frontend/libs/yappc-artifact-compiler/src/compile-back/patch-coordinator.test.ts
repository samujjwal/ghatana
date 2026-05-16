import { createHash } from 'crypto';
import { describe, expect, it, vi } from 'vitest';

import { PatchCoordinator } from './patch-coordinator';
import type { ChangePlan, PatchSet, ValidationResult } from './types';
import type { SemanticModelElement } from '../model/types';
import type { ResidualIsland } from '../residual/types';

const changeId = '5a92bb68-1a11-4b42-a02f-3f2ac85a8180';
const changePlanId = 'e6d6e4eb-f56c-463d-b82c-71de11cb4f92';

function createChangePlan(): ChangePlan {
  return {
    id: changePlanId,
    sourceModelId: '8f459e6c-7cbc-4c5f-9caa-f81c5728c04f',
    targetModelId: 'b77aa68c-8eab-4d20-a355-cd05844f9b56',
    changes: [
      {
        id: changeId,
        elementId: '6e953d5a-db54-4d09-99d7-a487bfb5c2df',
        kind: 'rename-component',
        description: 'Rename Button to PrimaryButton',
        before: 'Button',
        after: 'PrimaryButton',
        changedAt: new Date().toISOString(),
        autoApplyConfidence: 0.9,
        reviewRequired: false,
      },
    ],
    createdAt: new Date().toISOString(),
    estimatedImpact: {
      addedElements: 0,
      removedElements: 0,
      modifiedElements: 1,
      affectedFiles: 1,
    },
  };
}

function createValidationResult(valid: boolean): ValidationResult {
  return {
    id: 'd0cc6efc-7f85-4811-83ef-0cbf6129eafd',
    valid,
    errors: [],
    warnings: [],
    validatedAt: new Date().toISOString(),
    validatorId: 'patch-coordinator',
  };
}

function createElementMap(): ReadonlyMap<string, SemanticModelElement> {
  return new Map([
    [
      '6e953d5a-db54-4d09-99d7-a487bfb5c2df',
      {
        id: '6e953d5a-db54-4d09-99d7-a487bfb5c2df',
        kind: 'component',
        name: 'Button',
        provenance: {
          sourcePaths: ['src/Button.tsx'],
        },
      } as unknown as SemanticModelElement,
    ],
  ]);
}

describe('PatchCoordinator', () => {
  it('marks duplicate file targets as conflicted in review bundles', async () => {
    const coordinator = new PatchCoordinator();
    const patchSet: PatchSet = {
      id: '3aef75d4-1de0-45d8-b968-c4707f69b8ba',
      createdAt: new Date().toISOString(),
      changeOps: [],
      patches: [
        {
          relativePath: 'src/Button.tsx',
          diff: '@@ -1,1 +1,1 @@\n-Button\n+PrimaryButton',
          ranges: [{ startLine: 0, startColumn: 0, endLine: 0, endColumn: 6, nodeType: 'ComponentDeclaration' }],
          isAtomic: true,
          sourceChangeOpId: changeId,
          emitterId: 'react',
          baseChecksum: 'before-a',
          targetChecksum: 'after-a',
        },
        {
          relativePath: 'src/Button.tsx',
          diff: '@@ -3,1 +3,1 @@\n-old\n+new',
          ranges: [{ startLine: 2, startColumn: 0, endLine: 2, endColumn: 3, nodeType: 'JsxElement' }],
          isAtomic: true,
          sourceChangeOpId: 'f8545388-a0bf-4f34-9f4f-bfdf16772d0f',
          emitterId: 'react',
          baseChecksum: 'before-b',
          targetChecksum: 'after-b',
        },
      ],
      preservedResiduals: [],
      reviewRequiredPatches: [],
      stats: {
        totalChangeOps: 2,
        totalPatches: 2,
        autoApplicable: 2,
        requiresReview: 0,
        preservedResiduals: 0,
      },
    };

    const reviewBundle = await coordinator.buildReviewBundle(
      createChangePlan(),
      patchSet,
      createValidationResult(true),
      new Map<string, ResidualIsland>(),
      createElementMap(),
    );

    expect(reviewBundle.patches).toHaveLength(2);
    expect(reviewBundle.patches.every((patch) => patch.validationStatus === 'conflicted')).toBe(true);
    expect(reviewBundle.patches.every((patch) => patch.canAutoApply === false)).toBe(true);
    expect(reviewBundle.patches[0]?.ranges).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ nodeType: 'ComponentDeclaration' }),
      ]),
    );
    expect(reviewBundle.patches[0]?.baseChecksum).toBe('before-a');
    expect(reviewBundle.patches[0]?.targetChecksum).toBe('after-a');
  });

  it('merges validator warnings into change-plan validation', async () => {
    const validate = vi.fn().mockResolvedValue({
      errors: [],
      warnings: [
        {
          code: 'CUSTOM_RULE',
          message: 'Custom validator warning',
          changeId,
        },
      ],
    });
    const coordinator = new PatchCoordinator({
      validators: [
        {
          id: 'custom-validator',
          validate,
        },
      ],
    });

    const validation = await coordinator.validateChangePlan(
      createChangePlan(),
      createElementMap(),
      new Map<string, ResidualIsland>(),
    );

    expect(validate).toHaveBeenCalledTimes(1);
    expect(validation.warnings).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'CUSTOM_RULE',
          message: 'Custom validator warning',
          changeId,
        }),
      ]),
    );
  });

  it('fails dry-run validation when the workspace checksum is stale', async () => {
    const coordinator = new PatchCoordinator();
    const source = 'export function Button(): JSX.Element {\n  return <Button />;\n}\n';
    const patchSet: PatchSet = {
      id: '4fcdaf74-e321-4461-9eeb-e5fb67e67f41',
      createdAt: new Date().toISOString(),
      changeOps: [],
      patches: [
        {
          relativePath: 'src/Button.tsx',
          diff: '--- a/src/Button.tsx\n+++ b/src/Button.tsx\n@@ -1,1 +1,1 @@\n-export function Button\n+export function PrimaryButton',
          ranges: [{ startLine: 0, startColumn: 0, endLine: 0, endColumn: 22, nodeType: 'ComponentDeclaration' }],
          isAtomic: true,
          sourceChangeOpId: changeId,
          emitterId: 'react-patch-emitter',
          baseChecksum: createHash('sha256').update('stale-source').digest('hex'),
          targetChecksum: createHash('sha256').update(source).digest('hex'),
        },
      ],
      preservedResiduals: [],
      reviewRequiredPatches: [],
      stats: {
        totalChangeOps: 1,
        totalPatches: 1,
        autoApplicable: 1,
        requiresReview: 0,
        preservedResiduals: 0,
      },
    };

    const result = await coordinator.dryRunPatchSet(patchSet, {
      readFile: async () => source,
      fileExists: async () => true,
      residuals: new Map<string, ResidualIsland>(),
      elementSourcePaths: new Map(),
    });

    expect(result.valid).toBe(false);
    expect(result.errors).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          code: 'PATCH_BASE_CHECKSUM_MISMATCH',
          filePath: 'src/Button.tsx',
          changeId,
        }),
      ]),
    );
  });
});