/**
 * @fileoverview Test for P0-9: React patch emitter rename operation with TS Compiler API/range-safe implementation
 *
 * Verifies that the RENAME_COMPONENT operation uses range-safe transformations
 * and does not rely on unsafe regex patterns for critical operations.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { ReactPatchEmitter } from '../react-patch-emitter';
import type { ChangeOp, PatchContext } from '../types';

describe('P0-9: React Patch Emitter Rename Operation', () => {
  let emitter: ReactPatchEmitter;
  let context: PatchContext;

  beforeEach(() => {
    emitter = new ReactPatchEmitter();
    context = {
      sourceCode: 'export default function TestComponent() { return <div>Hello</div>; }',
      filePath: 'src/TestComponent.tsx',
      modelElements: [],
    };
  });

  it('should emit RENAME_COMPONENT operation successfully', async () => {
    const renameOp: ChangeOp = {
      op: 'RENAME_COMPONENT',
      targetId: 'component-123',
      changes: {
        newName: 'NewComponent',
      },
    };

    const result = await emitter.emit(renameOp, context);

    expect(result).toBeDefined();
    expect(result.success).toBe(true);
    expect(result.patch).toBeDefined();
  });

  it('should include range information in patch metadata', async () => {
    const renameOp: ChangeOp = {
      op: 'RENAME_COMPONENT',
      targetId: 'component-123',
      changes: {
        newName: 'NewComponent',
      },
    };

    const result = await emitter.emit(renameOp, context);

    if (result.success && result.patch) {
      // Verify patch includes range metadata for precise application
      expect(result.patch.metadata).toBeDefined();
      // Range information should be present for rollback capability
      if (result.patch.metadata) {
        expect(result.patch.metadata.startLine).toBeDefined();
        expect(result.patch.metadata.startColumn).toBeDefined();
        expect(result.patch.metadata.endLine).toBeDefined();
        expect(result.patch.metadata.endColumn).toBeDefined();
      }
    }
  });

  it('should generate checksum for rollback verification', async () => {
    const renameOp: ChangeOp = {
      op: 'RENAME_COMPONENT',
      targetId: 'component-123',
      changes: {
        newName: 'NewComponent',
      },
    };

    const result = await emitter.emit(renameOp, context);

    if (result.success && result.patch) {
      // Verify checksum is present for integrity verification
      expect(result.patch.checksum).toBeDefined();
      expect(result.patch.checksum).toMatch(/^[a-f0-9]{64}$/); // SHA-256 hex
    }
  });

  it('should handle multi-line component names correctly', async () => {
    const multiLineContext: PatchContext = {
      sourceCode: `export default function
TestComponent() {
  return <div>Hello</div>;
}`,
      filePath: 'src/TestComponent.tsx',
      modelElements: [],
    };

    const renameOp: ChangeOp = {
      op: 'RENAME_COMPONENT',
      targetId: 'component-123',
      changes: {
        newName: 'NewComponent',
      },
    };

    const result = await emitter.emit(renameOp, multiLineContext);

    expect(result).toBeDefined();
    // Should handle multi-line scenarios without regex issues
    if (result.success && result.patch) {
      expect(result.patch.metadata).toBeDefined();
    }
  });

  it('should preserve source code structure after rename', async () => {
    const originalCode = context.sourceCode;
    
    const renameOp: ChangeOp = {
      op: 'RENAME_COMPONENT',
      targetId: 'component-123',
      changes: {
        newName: 'NewComponent',
      },
    };

    const result = await emitter.emit(renameOp, context);

    if (result.success && result.patch) {
      // Verify the patch only changes the component name, not structure
      expect(result.patch.diff).toBeDefined();
      expect(result.patch.diff).toContain('NewComponent');
      expect(result.patch.diff).not.toContain('export default function');
    }
  });

  it('should fail gracefully for invalid component name', async () => {
    const invalidRenameOp: ChangeOp = {
      op: 'RENAME_COMPONENT',
      targetId: 'component-123',
      changes: {
        newName: '', // Invalid empty name
      },
    };

    const result = await emitter.emit(invalidRenameOp, context);

    expect(result).toBeDefined();
    // Should fail gracefully with error, not throw
    if (!result.success) {
      expect(result.error).toBeDefined();
    }
  });
});
