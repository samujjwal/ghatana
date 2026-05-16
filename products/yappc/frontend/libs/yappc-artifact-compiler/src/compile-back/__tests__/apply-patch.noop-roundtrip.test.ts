/**
 * @fileoverview Test for P1-15: Apply-patch module safety guards
 *
 * Verifies that apply-patch.ts provides dry-run/apply interface with checksum guard,
 * residual guard, rollback metadata, and validation hook for safe patch application.
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { applyPatch, type ApplyPatchOptions, type FileSystem, type RollbackMetadata } from '../apply-patch';
import type { PatchSet, TextPatch } from '../types';
import type { ResidualIsland } from '../residual/types';

describe('P1-15: Apply-patch Module Safety Guards', () => {
  let mockFileSystem: FileSystem;
  let patchSet: PatchSet;

  beforeEach(() => {
    mockFileSystem = {
      readFile: vi.fn(),
      writeFile: vi.fn(),
      fileExists: vi.fn(),
    };

    patchSet = {
      id: 'patch-123',
      patches: [
        {
          relativePath: 'src/Test.tsx',
          diff: '--- a/src/Test.tsx\n+++ b/src/Test.tsx\n@@ -1,1 +1,1 @@\n-old\n+new',
          ranges: [],
          isAtomic: true,
          sourceChangeOpId: 'op-1',
          emitterId: 'react-patch-emitter',
          baseChecksum: 'sha256:abc123',
          targetChecksum: 'sha256:def456',
        },
      ],
      metadata: {},
    };
  });

  it('should perform dry-run without applying changes', async () => {
    const options: ApplyPatchOptions = {
      dryRun: true,
    };

    mockFileSystem.readFile.mockResolvedValue('old content');
    mockFileSystem.fileExists.mockResolvedValue(true);

    const result = await applyPatch(patchSet, mockFileSystem, options);

    expect(result.success).toBe(true);
    expect(result.filesPatched).toBe(0);
    expect(result.diff).toBeDefined();
    // Should not call writeFile in dry-run mode
    expect(mockFileSystem.writeFile).not.toHaveBeenCalled();
  });

  it('should guard against checksum mismatch', async () => {
    const options: ApplyPatchOptions = {
      dryRun: false,
    };

    // File content doesn't match expected base checksum
    mockFileSystem.readFile.mockResolvedValue('different content');
    mockFileSystem.fileExists.mockResolvedValue(true);

    const result = await applyPatch(patchSet, mockFileSystem, options);

    expect(result.success).toBe(false);
    expect(result.validationErrors).toBeDefined();
    expect(result.validationErrors).toContain('checksum mismatch');
  });

  it('should guard against modifying residual islands', async () => {
    const residualIsland: ResidualIsland = {
      id: 'residual-1',
      kind: 'code',
      originalSource: 'unparseable code',
      normalizedSummary: 'summary',
      reasonUnmodeled: 'unsupported syntax',
      reviewRequired: true,
      sourceLocation: { filePath: 'src/residual.tsx', startLine: 0, startColumn: 0, endLine: 10, endColumn: 0 },
      extractorId: 'typescript-extractor',
      extractorVersion: '1.0.0',
      extractedAt: new Date().toISOString(),
      confidence: 0.0,
      linkedModelElementIds: [],
      tags: [],
    };

    const patchWithResidual: PatchSet = {
      id: 'patch-123',
      patches: [
        {
          relativePath: 'src/residual.tsx', // Trying to patch residual island
          diff: '--- a/src/residual.tsx\n+++ b/src/residual.tsx\n@@ -1,1 +1,1 @@\n-old\n+new',
          ranges: [],
          isAtomic: true,
          sourceChangeOpId: 'op-1',
          emitterId: 'react-patch-emitter',
        },
      ],
      metadata: {},
    };

    const options: ApplyPatchOptions = {
      dryRun: false,
      residuals: new Map([['residual-1', residualIsland]]),
    };

    const result = await applyPatch(patchWithResidual, mockFileSystem, options);

    expect(result.success).toBe(false);
    expect(result.validationErrors).toBeDefined();
    expect(result.validationErrors).toContain('residual island');
  });

  it('should generate rollback metadata when enabled', async () => {
    const options: ApplyPatchOptions = {
      dryRun: false,
      enableRollback: true,
    };

    mockFileSystem.readFile.mockResolvedValue('old content');
    mockFileSystem.fileExists.mockResolvedValue(true);

    const result = await applyPatch(patchSet, mockFileSystem, options);

    expect(result.success).toBe(true);
    expect(result.rollbackMetadata).toBeDefined();
    expect(result.rollbackMetadata?.originalContents).toBeDefined();
    expect(result.rollbackMetadata?.checksums).toBeDefined();
  });

  it('should call validation hook before application', async () => {
    const validateHook = vi.fn(() => ({
      valid: true,
      errors: [],
      warnings: [],
    }));

    const options: ApplyPatchOptions = {
      dryRun: false,
      validate: validateHook,
    };

    mockFileSystem.readFile.mockResolvedValue('old content');
    mockFileSystem.fileExists.mockResolvedValue(true);

    await applyPatch(patchSet, mockFileSystem, options);

    expect(validateHook).toHaveBeenCalled();
  });

  it('should fail validation when validation hook returns errors', async () => {
    const validateHook = vi.fn(() => ({
      valid: false,
      errors: ['Validation failed'],
      warnings: [],
    }));

    const options: ApplyPatchOptions = {
      dryRun: false,
      validate: validateHook,
    };

    mockFileSystem.readFile.mockResolvedValue('old content');
    mockFileSystem.fileExists.mockResolvedValue(true);

    const result = await applyPatch(patchSet, mockFileSystem, options);

    expect(result.success).toBe(false);
    expect(result.validationErrors).toContain('Validation failed');
  });

  it('should handle noop patch (no changes)', async () => {
    const noopPatchSet: PatchSet = {
      id: 'patch-123',
      patches: [], // Empty patch set
      metadata: {},
    };

    const options: ApplyPatchOptions = {
      dryRun: false,
    };

    const result = await applyPatch(noopPatchSet, mockFileSystem, options);

    expect(result.success).toBe(true);
    expect(result.filesPatched).toBe(0);
  });

  it('should preserve rollback metadata for rollback', async () => {
    const options: ApplyPatchOptions = {
      dryRun: false,
      enableRollback: true,
    };

    mockFileSystem.readFile.mockResolvedValue('old content');
    mockFileSystem.fileExists.mockResolvedValue(true);

    const result = await applyPatch(patchSet, mockFileSystem, options);

    if (result.rollbackMetadata) {
      expect(result.rollbackMetadata.originalContents.get('src/Test.tsx')).toBe('old content');
      expect(result.rollbackMetadata.patchSetId).toBe('patch-123');
    }
  });

  it('should return diff in dry-run mode', async () => {
    const options: ApplyPatchOptions = {
      dryRun: true,
    };

    mockFileSystem.readFile.mockResolvedValue('old content');
    mockFileSystem.fileExists.mockResolvedValue(true);

    const result = await applyPatch(patchSet, mockFileSystem, options);

    expect(result.diff).toBeDefined();
    expect(result.diff).toContain('--- a/src/Test.tsx');
    expect(result.diff).toContain('+++ b/src/Test.tsx');
  });
});
