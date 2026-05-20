/**
 * @fileoverview Tests for scan job, validation pipeline, and diff record contracts.
 *
 * Verifies that the Zod schemas in scan.ts parse correctly and reject invalid
 * inputs. All tests invoke real Zod schema validation — no object-literal theatre.
 */

import { describe, it, expect } from 'vitest';
import {
  AcquisitionJobSchema,
  ScanJobSchema,
  FileScanResultSchema,
  ScanResultSchema,
  ValidationFindingSchema,
  ValidationPipelineResultSchema,
  DiffHunkSchema,
  DiffRecordSchema,
  RoundTripDiffReportSchema,
} from '../scan.js';
import { computeFidelityReport, createPerfectFidelityReport, createResidualIslandReport } from '../fidelity.js';

// ============================================================================
// Helpers
// ============================================================================

const NOW = '2024-01-15T10:00:00.000Z';
const UUID = '00000000-0000-0000-0000-000000000001';

function makePerfectFidelity() {
  return createPerfectFidelityReport(UUID);
}

function makeEmptyResiduals() {
  return createResidualIslandReport([]);
}

// ============================================================================
// AcquisitionJob
// ============================================================================

describe('AcquisitionJobSchema', () => {
  it('parses a minimal pending acquisition job', () => {
    const result = AcquisitionJobSchema.parse({
      jobId: UUID,
      status: 'pending',
      descriptor: {
        kind: 'local-folder',
        uri: '/workspace/repo',
        label: 'My Repo',
      },
      createdAt: NOW,
    });
    expect(result.status).toBe('pending');
    expect(result.jobId).toBe(UUID);
  });

  it('parses a completed acquisition job with optional fields', () => {
    const result = AcquisitionJobSchema.parse({
      jobId: UUID,
      status: 'complete',
      descriptor: { kind: 'local-folder', uri: '/tmp/repo', label: 'Repo' },
      createdAt: NOW,
      startedAt: NOW,
      completedAt: NOW,
      totalBytes: 102400,
      fileCount: 42,
      localWorkspacePath: '/tmp/workspace',
      correlationId: 'corr-123',
    });
    expect(result.status).toBe('complete');
    expect(result.fileCount).toBe(42);
  });

  it('rejects an invalid status', () => {
    expect(() =>
      AcquisitionJobSchema.parse({
        jobId: UUID,
        status: 'unknown-status',
        descriptor: { kind: 'local-folder', uri: '/tmp', label: 'x' },
        createdAt: NOW,
      }),
    ).toThrow();
  });

  it('rejects a missing jobId', () => {
    expect(() =>
      AcquisitionJobSchema.parse({
        status: 'pending',
        descriptor: { kind: 'local-folder', uri: '/tmp', label: 'x' },
        createdAt: NOW,
      }),
    ).toThrow();
  });
});

// ============================================================================
// ScanJob
// ============================================================================

describe('ScanJobSchema', () => {
  it('parses a minimal scan job', () => {
    const result = ScanJobSchema.parse({
      jobId: UUID,
      status: 'pending',
      acquisitionJobId: UUID,
      createdAt: NOW,
    });
    expect(result.status).toBe('pending');
  });

  it('parses a partial scan job (some files failed)', () => {
    const result = ScanJobSchema.parse({
      jobId: UUID,
      status: 'partial',
      acquisitionJobId: UUID,
      totalFileCount: 100,
      parsedFileCount: 95,
      failedFileCount: 5,
      createdAt: NOW,
      startedAt: NOW,
      completedAt: NOW,
      durationMs: 1234.5,
    });
    expect(result.status).toBe('partial');
    expect(result.failedFileCount).toBe(5);
  });

  it('rejects negative totalFileCount', () => {
    expect(() =>
      ScanJobSchema.parse({
        jobId: UUID,
        status: 'running',
        acquisitionJobId: UUID,
        createdAt: NOW,
        totalFileCount: -1,
      }),
    ).toThrow();
  });
});

// ============================================================================
// ScanResult
// ============================================================================

describe('ScanResultSchema', () => {
  it('parses a complete scan result', () => {
    const fidelity = makePerfectFidelity();
    const residuals = makeEmptyResiduals();

    const result = ScanResultSchema.parse({
      scanJobId: UUID,
      modelId: UUID,
      files: [
        {
          sourceFile: {
            relativePath: 'src/App.tsx',
            contentType: 'text/typescript',
            kind: 'unknown',
            sizeBytes: 2048,
            // contentHash omitted — it is optional; when provided it must be a 64-char hex SHA-256
          },
          parsed: true,
          parseDurationMs: 12.5,
        },
      ],
      fidelity,
      residuals,
      completedAt: NOW,
      durationMs: 500,
    });

    expect(result.files).toHaveLength(1);
    expect(result.files[0]?.parsed).toBe(true);
    expect(result.fidelity.score).toBe(1);
  });

  it('rejects negative durationMs', () => {
    const fidelity = makePerfectFidelity();
    const residuals = makeEmptyResiduals();
    expect(() =>
      ScanResultSchema.parse({
        scanJobId: UUID,
        modelId: UUID,
        files: [],
        fidelity,
        residuals,
        completedAt: NOW,
        durationMs: -100,
      }),
    ).toThrow();
  });
});

// ============================================================================
// ValidationFinding + ValidationPipelineResult
// ============================================================================

describe('ValidationFindingSchema', () => {
  it('parses a TypeScript validation finding', () => {
    const finding = ValidationFindingSchema.parse({
      code: 'TS2304',
      message: "Cannot find name 'foo'.",
      severity: 'error',
      category: 'typescript',
    });
    expect(finding.severity).toBe('error');
    expect(finding.category).toBe('typescript');
  });

  it('rejects unknown severity', () => {
    expect(() =>
      ValidationFindingSchema.parse({
        code: 'X001',
        message: 'Bad',
        severity: 'critical', // not in enum
        category: 'other',
      }),
    ).toThrow();
  });

  it('rejects unknown category', () => {
    expect(() =>
      ValidationFindingSchema.parse({
        code: 'X001',
        message: 'Bad',
        severity: 'error',
        category: 'unknown-category',
      }),
    ).toThrow();
  });
});

describe('ValidationPipelineResultSchema', () => {
  it('parses a passed pipeline result', () => {
    const result = ValidationPipelineResultSchema.parse({
      targetId: UUID,
      passed: true,
      findings: [],
      errorCount: 0,
      warningCount: 0,
      infoCount: 0,
      validatedAt: NOW,
    });
    expect(result.passed).toBe(true);
    expect(result.errorCount).toBe(0);
  });

  it('parses a failed pipeline result with findings', () => {
    const result = ValidationPipelineResultSchema.parse({
      targetId: UUID,
      passed: false,
      findings: [
        {
          code: 'TS2304',
          message: "Cannot find name 'Button'.",
          severity: 'error',
          category: 'typescript',
        },
      ],
      errorCount: 1,
      warningCount: 0,
      infoCount: 0,
      validatedAt: NOW,
      durationMs: 250,
    });
    expect(result.passed).toBe(false);
    expect(result.errorCount).toBe(1);
    expect(result.findings[0]?.code).toBe('TS2304');
  });
});

// ============================================================================
// DiffRecord + RoundTripDiffReport
// ============================================================================

describe('DiffHunkSchema', () => {
  it('parses an "added" hunk', () => {
    const hunk = DiffHunkSchema.parse({
      kind: 'added',
      generatedStart: 10,
      lineCount: 5,
    });
    expect(hunk.kind).toBe('added');
    expect(hunk.lineCount).toBe(5);
  });

  it('rejects lineCount of 0', () => {
    expect(() =>
      DiffHunkSchema.parse({ kind: 'added', generatedStart: 1, lineCount: 0 }),
    ).toThrow();
  });
});

describe('DiffRecordSchema', () => {
  it('parses a semantically equivalent diff record', () => {
    const record = DiffRecordSchema.parse({
      diffId: UUID,
      originalPath: 'src/Button.tsx',
      generatedPath: 'src/Button.tsx',
      semanticallyEquivalent: true,
      hunks: [],
      addedLines: 0,
      removedLines: 0,
      unchangedLines: 100,
      diffedAt: NOW,
    });
    expect(record.semanticallyEquivalent).toBe(true);
    expect(record.unchangedLines).toBe(100);
  });
});

describe('RoundTripDiffReportSchema', () => {
  it('parses a complete round-trip diff report', () => {
    const fidelity = makePerfectFidelity();
    const residuals = makeEmptyResiduals();

    const report = RoundTripDiffReportSchema.parse({
      reportId: UUID,
      modelId: UUID,
      diffs: [],
      fidelity,
      residuals,
      isLossless: true,
      generatedAt: NOW,
    });
    expect(report.isLossless).toBe(true);
    expect(report.diffs).toHaveLength(0);
  });

  it('rejects missing isLossless field', () => {
    const fidelity = makePerfectFidelity();
    const residuals = makeEmptyResiduals();
    expect(() =>
      RoundTripDiffReportSchema.parse({
        reportId: UUID,
        modelId: UUID,
        diffs: [],
        fidelity,
        residuals,
        generatedAt: NOW,
        // isLossless intentionally missing
      }),
    ).toThrow();
  });
});
