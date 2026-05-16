/**
 * @fileoverview Test for P0-9: React patch emitter unsupported operation restriction
 *
 * Verifies that canEmit returns false for unsupported operations and returns
 * explicit unsupported result instead of silently failing.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { ReactPatchEmitter } from '../react-patch-emitter';
import type { ChangeOp, PatchContext } from '../types';

describe('P0-9: React Patch Emitter Unsupported Operation Restriction', () => {
  let emitter: ReactPatchEmitter;
  let context: PatchContext;

  beforeEach(() => {
    emitter = new ReactPatchEmitter();
    context = {
      sourceCode: 'export default function Test() { return <div>Hello</div>; }',
      filePath: 'src/Test.tsx',
      modelElements: [],
    };
  });

  it('should return false for unsupported operation types', () => {
    const unsupportedOp: ChangeOp = {
      op: 'DELETE_DATABASE_TABLE', // Not implemented for React
      targetId: 'table-123',
      changes: {},
    };

    expect(emitter.canEmit(unsupportedOp)).toBe(false);
  });

  it('should return true for implemented operation types', () => {
    const supportedOp: ChangeOp = {
      op: 'RENAME_COMPONENT',
      targetId: 'component-123',
      changes: {
        newName: 'NewComponent',
      },
    };

    expect(emitter.canEmit(supportedOp)).toBe(true);
  });

  it('should return explicit unsupported result for unsupported operations', async () => {
    const unsupportedOp: ChangeOp = {
      op: 'DELETE_DATABASE_TABLE',
      targetId: 'table-123',
      changes: {},
    };

    const result = await emitter.emit(unsupportedOp, context);

    // Should return explicit unsupported result, not throw or return null
    expect(result).toBeDefined();
    expect(result.success).toBe(false);
    expect(result.error).toContain('unsupported');
  });

  it('should handle RENAME_COMPONENT operation', async () => {
    const renameOp: ChangeOp = {
      op: 'RENAME_COMPONENT',
      targetId: 'component-123',
      changes: {
        newName: 'NewComponent',
      },
    };

    const result = await emitter.emit(renameOp, context);

    expect(result).toBeDefined();
    // Should not have unsupported error
    if (!result.success) {
      expect(result.error).not.toContain('unsupported');
    }
  });

  it('should handle UPDATE_PROPS operation', async () => {
    const updatePropsOp: ChangeOp = {
      op: 'UPDATE_PROPS',
      targetId: 'component-123',
      changes: {
        props: { className: 'new-class' },
      },
    };

    const result = await emitter.emit(updatePropsOp, context);

    expect(result).toBeDefined();
    // Should not have unsupported error
    if (!result.success) {
      expect(result.error).not.toContain('unsupported');
    }
  });

  it('should return false for null/undefined operation', () => {
    expect(emitter.canEmit(null as any)).toBe(false);
    expect(emitter.canEmit(undefined as any)).toBe(false);
  });

  it('should return false for operation with invalid op type', () => {
    const invalidOp: ChangeOp = {
      op: 'INVALID_OP_TYPE' as any,
      targetId: 'component-123',
      changes: {},
    };

    expect(emitter.canEmit(invalidOp)).toBe(false);
  });
});
