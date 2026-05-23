/**
 * @fileoverview Tests for evidence pack contracts.
 *
 * Exercises the real Zod schemas for generated validation, preview, source
 * acquisition, and aggregate evidence packs.
 */

import { describe, expect, it } from 'vitest';
import {
  EvidencePackSchema,
  GeneratedArtifactValidationEvidenceSchema,
  PreviewValidationEvidenceSchema,
  SourceAcquisitionEvidenceSchema,
} from '../evidence.js';
import { createPerfectFidelityReport, createResidualIslandReport } from '../fidelity.js';

const NOW = '2024-01-15T10:00:00.000Z';

function makeValidationPipelineResult(passed: boolean = true) {
  return {
    targetId: 'model-1',
    passed,
    findings: passed
      ? []
      : [
          {
            code: 'TS2304',
            message: "Cannot find name 'Button'.",
            severity: 'error' as const,
            category: 'typescript' as const,
          },
        ],
    errorCount: passed ? 0 : 1,
    warningCount: 0,
    infoCount: 0,
    validatedAt: NOW,
    durationMs: 25,
  };
}

describe('GeneratedArtifactValidationEvidenceSchema', () => {
  it('parses stage-level generated validation evidence', () => {
    const evidence = GeneratedArtifactValidationEvidenceSchema.parse({
      targetId: 'model-1',
      passed: false,
      pipeline: makeValidationPipelineResult(false),
      typeScriptDiagnostics: [
        {
          code: 'TS2304',
          message: "Cannot find name 'Button'.",
          severity: 'error',
          category: 'typescript',
        },
      ],
      stages: [
        {
          stageId: 'typecheck',
          status: 'failed',
          summary: 'TypeScript typecheck reported one finding.',
          findings: [
            {
              code: 'TS2304',
              message: "Cannot find name 'Button'.",
              severity: 'error',
              category: 'typescript',
            },
          ],
          report: {
            label: 'TypeScript report',
            relativePath: 'reports/typecheck.json',
            contentType: 'application/json',
          },
        },
        {
          stageId: 'build',
          status: 'not-run',
          summary: 'Build was not run for this evidence pack.',
        },
      ],
      artifacts: [
        {
          label: 'src/Button.tsx',
          relativePath: 'src/Button.tsx',
          contentType: 'text/tsx',
        },
      ],
      validatedAt: NOW,
      durationMs: 25,
    });

    expect(evidence.stages[0]?.status).toBe('failed');
    expect(evidence.typeScriptDiagnostics[0]?.code).toBe('TS2304');
  });

  it('rejects aggregate pass disagreement with the validation pipeline', () => {
    expect(() =>
      GeneratedArtifactValidationEvidenceSchema.parse({
        targetId: 'model-1',
        passed: true,
        pipeline: makeValidationPipelineResult(false),
        stages: [],
        validatedAt: NOW,
      }),
    ).toThrow(/pipeline\.passed/);
  });

  it('rejects passed evidence that contains a failed stage', () => {
    expect(() =>
      GeneratedArtifactValidationEvidenceSchema.parse({
        targetId: 'model-1',
        passed: true,
        pipeline: makeValidationPipelineResult(true),
        stages: [
          {
            stageId: 'build',
            status: 'failed',
            summary: 'Build failed.',
            findings: [
              {
                code: 'stage/build',
                message: 'Build failed.',
                severity: 'error',
                category: 'build',
              },
            ],
          },
        ],
        validatedAt: NOW,
      }),
    ).toThrow(/failed stage/);
  });
});

describe('PreviewValidationEvidenceSchema', () => {
  it('parses isolated runtime preview evidence with sandbox policy', () => {
    const evidence = PreviewValidationEvidenceSchema.parse({
      targetId: 'model-1',
      mode: 'isolated-runtime',
      status: 'passed',
      summary: 'Preview rendered successfully.',
      findings: [],
      preview: {
        label: 'Preview smoke HTML',
        relativePath: 'reports/preview.html',
        contentType: 'text/html',
      },
      sandboxPolicy: {
        allowScripts: true,
        allowSameOrigin: false,
        allowPopups: false,
        allowForms: false,
      },
      renderedAt: NOW,
      durationMs: 12,
    });

    expect(evidence.mode).toBe('isolated-runtime');
    expect(evidence.sandboxPolicy.allowSameOrigin).toBe(false);
  });
});

describe('SourceAcquisitionEvidenceSchema', () => {
  it('parses source acquisition evidence with materialized inventory reference', () => {
    const evidence = SourceAcquisitionEvidenceSchema.parse({
      acquisitionId: 'job-1',
      descriptor: {
        kind: 'github',
        uri: 'https://github.com/example/repo',
        ref: 'main',
        label: 'Example repository',
      },
      status: 'complete',
      scope: {
        tenantId: 'tenant-1',
        workspaceId: 'workspace-1',
        projectId: 'project-1',
      },
      fileCount: 2,
      totalBytes: 128,
      inventory: {
        label: 'Source inventory',
        relativePath: 'acquisition/job-1/inventory.json',
        contentType: 'application/json',
      },
      recordedAt: NOW,
      correlationId: 'corr-1',
    });

    expect(evidence.status).toBe('complete');
    expect(evidence.inventory?.relativePath).toContain('inventory.json');
  });
});

describe('EvidencePackSchema', () => {
  it('parses evidence packs with validation, preview, and acquisition channels', () => {
    const pack = EvidencePackSchema.parse({
      evidenceId: 'evidence-1',
      createdAt: NOW,
      modelId: 'model-1',
      label: 'Round trip evidence',
      stage: 'round-trip',
      fidelity: createPerfectFidelityReport('model-1'),
      residuals: createResidualIslandReport([]),
      validationResult: makeValidationPipelineResult(true),
      generatedValidationEvidence: {
        targetId: 'model-1',
        passed: true,
        pipeline: makeValidationPipelineResult(true),
        stages: [
          {
            stageId: 'typecheck',
            status: 'passed',
            summary: 'TypeScript typecheck passed.',
          },
        ],
        artifacts: [
          {
            label: 'src/App.tsx',
            relativePath: 'src/App.tsx',
            contentType: 'text/tsx',
          },
        ],
        validatedAt: NOW,
      },
      previewEvidence: {
        targetId: 'model-1',
        mode: 'safe-static',
        status: 'passed',
        summary: 'Preview smoke passed.',
        sandboxPolicy: {
          allowScripts: true,
          allowSameOrigin: false,
          allowPopups: false,
          allowForms: false,
        },
        renderedAt: NOW,
      },
      sourceAcquisitionEvidence: {
        acquisitionId: 'job-1',
        status: 'complete',
        fileCount: 1,
        totalBytes: 42,
        recordedAt: NOW,
      },
    });

    expect(pack.generatedValidationEvidence?.stages[0]?.stageId).toBe('typecheck');
    expect(pack.previewEvidence?.mode).toBe('safe-static');
    expect(pack.sourceAcquisitionEvidence?.status).toBe('complete');
  });
});
