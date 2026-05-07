import { describe, expect, it } from 'vitest';

import {
  applyUserEdit,
  generateCodegenPreview,
  type CodegenPreviewOptions,
} from '../CodegenPreview';

const provenance: CodegenPreviewOptions['provenance'] = {
  requirementId: 'req-checkout-cta',
  phase: 'generate',
  canvasNodeId: 'node-primary-cta',
  sourceArtifactId: 'artifact-page-builder-checkout',
  confidence: 0.91,
  approvingActorId: 'reviewer-42',
  approvedAt: '2026-05-07T16:00:00.000Z',
};

const previewOptions: CodegenPreviewOptions = {
  generatedFilePath: 'src/generated/CheckoutPage.tsx',
  language: 'typescript',
  artifactType: 'source',
  provenance,
};

describe('CodegenPreview provenance', () => {
  it('links generated files and every diff region to requirement, phase, canvas node, source artifact, confidence, and reviewer', () => {
    const preview = generateCodegenPreview(
      'export const title = "Old";',
      'export const title = "Checkout";',
      previewOptions,
    );

    expect(preview.generatedFiles).toHaveLength(1);
    expect(preview.generatedFiles[0]).toMatchObject({
      path: 'src/generated/CheckoutPage.tsx',
      language: 'typescript',
      artifactType: 'source',
      provenance,
    });
    expect(preview.generatedFiles[0]?.diffRegionIds).toEqual(['diff-0']);
    expect(preview.diff).toHaveLength(1);
    expect(preview.diff[0]?.provenance).toEqual(provenance);
  });

  it('preserves provenance when user-owned generated regions are edited after preview creation', () => {
    const preview = generateCodegenPreview(
      '// @user-owned\nconst label = "Old";\n// @end-region',
      '// @user-owned\nconst label = "Generated";\n// @end-region',
      previewOptions,
    );

    const editedPreview = applyUserEdit(preview, 2, 'const label = "Reviewed";');

    expect(editedPreview.generatedFiles[0]?.provenance).toEqual(provenance);
    expect(editedPreview.diff.every((region) => region.provenance === provenance)).toBe(true);
  });

  it('rejects generated artifact provenance with out-of-range confidence', () => {
    expect(() =>
      generateCodegenPreview('old', 'new', {
        ...previewOptions,
        provenance: {
          ...provenance,
          confidence: 1.2,
        },
      }),
    ).toThrow(/confidence must be between 0 and 1/i);
  });
});
