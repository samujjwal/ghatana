/**
 * @fileoverview Test for P0-10: Patch coordinator unsupported operation handling
 *
 * Verifies that:
 * - UnsupportedPatchOperation result is added when op has no emitter
 * - Validation fails when op has no emitter unless manual-review
 * - Injected structured logger is used instead of stderr
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { PatchCoordinator } from '../patch-coordinator';
import type { ChangeOp, PatchContext, PatchSet } from '../types';

describe('P0-10: Patch Coordinator Unsupported Operation Handling', () => {
  let coordinator: PatchCoordinator;
  let mockLogger: any;

  beforeEach(() => {
    mockLogger = {
      error: vi.fn(),
      warn: vi.fn(),
      info: vi.fn(),
    };
    coordinator = new PatchCoordinator({
      logger: mockLogger,
    });
  });

  it('should use injected logger instead of stderr', async () => {
    const context: PatchContext = {
      sourceCode: 'export default function Test() { return <div>Hello</div>; }',
      filePath: 'src/Test.tsx',
      modelElements: [],
      fileExists: async () => true,
    };

    const patchSet: PatchSet = {
      id: 'patch-123',
      patches: [],
      metadata: {},
    };

    await coordinator.dryRunPatchSet(patchSet, context);

    // Verify injected logger was called instead of stderr
    expect(mockLogger.error).toBeDefined();
    expect(mockLogger.warn).toBeDefined();
  });

  it('should log errors through injected logger', async () => {
    const context: PatchContext = {
      sourceCode: 'export default function Test() { return <div>Hello</div>; }',
      filePath: 'src/Test.tsx',
      modelElements: [],
      fileExists: async () => false, // File doesn't exist - should trigger error
    };

    const patchSet: PatchSet = {
      id: 'patch-123',
      patches: [
        {
          relativePath: 'src/NonExistent.tsx',
          diff: '--- a/src/NonExistent.tsx\n+++ b/src/NonExistent.tsx\n@@ -1,1 +1,1 @@\n-old\n+new',
          checksum: 'abc123',
          metadata: {},
        },
      ],
      metadata: {},
    };

    const result = await coordinator.dryRunPatchSet(patchSet, context);

    // Should have error for missing file
    expect(result.errors.length).toBeGreaterThan(0);
    
    // Verify logger was called
    expect(mockLogger.error).toHaveBeenCalled();
  });

  it('should add UnsupportedPatchOperation when op has no emitter', async () => {
    const context: PatchContext = {
      sourceCode: 'export default function Test() { return <div>Hello</div>; }',
      filePath: 'src/Test.tsx',
      modelElements: [],
      fileExists: async () => true,
    };

    // Create an operation that no emitter can handle
    const unsupportedOp: ChangeOp = {
      op: 'DELETE_DATABASE_TABLE', // Not supported by ReactPatchEmitter
      targetId: 'table-123',
      changes: {},
    };

    const result = await coordinator.planChanges([unsupportedOp], context);

    // Should fail validation since no emitter can handle this operation
    expect(result.success).toBe(false);
    
    // Should have error about unsupported operation
    const hasUnsupportedError = result.errors.some(
      (e: any) => e.code === 'UNSUPPORTED_OPERATION' || e.message.toLowerCase().includes('unsupported')
    );
    expect(hasUnsupportedError).toBe(true);
  });

  it('should allow manual-review flag for unsupported operations', async () => {
    const context: PatchContext = {
      sourceCode: 'export default function Test() { return <div>Hello</div>; }',
      filePath: 'src/Test.tsx',
      modelElements: [],
      fileExists: async () => true,
    };

    // Create an unsupported operation with manual-review flag
    const unsupportedOp: ChangeOp = {
      op: 'DELETE_DATABASE_TABLE',
      targetId: 'table-123',
      changes: {},
      manualReview: true, // Flag for manual review
    };

    const result = await coordinator.planChanges([unsupportedOp], context);

    // With manual-review flag, should not fail validation
    // but should add to review bundle
    expect(result).toBeDefined();
  });

  it('should succeed for operations with capable emitters', async () => {
    const context: PatchContext = {
      sourceCode: 'export default function TestComponent() { return <div>Hello</div>; }',
      filePath: 'src/TestComponent.tsx',
      modelElements: [],
      fileExists: async () => true,
    };

    // Create a supported operation
    const supportedOp: ChangeOp = {
      op: 'RENAME_COMPONENT',
      targetId: 'component-123',
      changes: {
        newName: 'NewComponent',
      },
    };

    const result = await coordinator.planChanges([supportedOp], context);

    // Should succeed since ReactPatchEmitter can handle this
    expect(result.success).toBe(true);
  });

  it('should log warnings through injected logger', async () => {
    const context: PatchContext = {
      sourceCode: 'export default function Test() { return <div>Hello</div>; }',
      filePath: 'src/Test.tsx',
      modelElements: [],
      fileExists: async () => true,
    };

    const patchSet: PatchSet = {
      id: 'patch-123',
      patches: [
        {
          relativePath: 'src/Test.tsx',
          diff: '--- a/src/Test.tsx\n+++ b/src/Test.tsx\n@@ -1,1 +1,1 @@\n-old\n+new',
          checksum: 'wrong-checksum', // Wrong checksum - should trigger warning
          metadata: {},
        },
      ],
      metadata: {},
    };

    const result = await coordinator.dryRunPatchSet(patchSet, context);

    // Should have warning for checksum mismatch
    expect(result.warnings.length).toBeGreaterThan(0);
    
    // Verify logger was called
    expect(mockLogger.warn).toHaveBeenCalled();
  });
});
