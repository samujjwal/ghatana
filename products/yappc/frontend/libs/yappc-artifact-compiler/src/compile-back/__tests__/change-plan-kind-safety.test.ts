/**
 * @fileoverview Type-safety contract tests for ChangeOp kind enum and ChangeOpSchema.
 *
 * Verifies that:
 * - Only valid implemented ChangeOpKind values are accepted
 * - ChangeOpSchema validates required fields and rejects unknown kinds
 * - autoApplyConfidence is clamped to [0,1]
 */

import { describe, it, expect } from 'vitest';
import { ChangeOpSchema, ChangeOpKindSchema } from '../types';

const VALID_CHANGE_OP_BASE = {
  id: 'op-1',
  kind: 'add-component' as const,
  targetElementId: 'elem-abc',
  description: 'Add new component MyCard',
  autoApplyConfidence: 0.9,
};

describe('ChangeOpKindSchema — exhaustive kind contract', () => {
  const validKinds = [
    'add-component',
    'remove-component',
    'update-component-props',
    'rename-component',
    'manual-review',
    'unsupported-operation',
  ] as const;

  for (const kind of validKinds) {
    it(`accepts valid kind "${kind}"`, () => {
      expect(ChangeOpKindSchema.parse(kind)).toBe(kind);
    });
  }

  it('rejects unknown kind "upsert"', () => {
    expect(() => ChangeOpKindSchema.parse('upsert')).toThrow();
  });

  it('rejects empty string kind', () => {
    expect(() => ChangeOpKindSchema.parse('')).toThrow();
  });

  it('rejects numeric kind', () => {
    expect(() => ChangeOpKindSchema.parse(42)).toThrow();
  });
});

describe('ChangeOpSchema — field validation', () => {
  it('parses a minimal add operation', () => {
    const op = ChangeOpSchema.parse(VALID_CHANGE_OP_BASE);

    expect(op.id).toBe('op-1');
    expect(op.kind).toBe('add-component');
    expect(op.targetElementId).toBe('elem-abc');
    expect(op.autoApplyConfidence).toBe(0.9);
    expect(op.before).toBeUndefined();
    expect(op.after).toBeUndefined();
  });

  it('parses a modify operation with before/after states', () => {
    const op = ChangeOpSchema.parse({
      ...VALID_CHANGE_OP_BASE,
      kind: 'update-component-props',
      before: { name: 'OldName' },
      after: { name: 'NewName' },
    });

    expect(op.kind).toBe('update-component-props');
    expect(op.before).toEqual({ name: 'OldName' });
    expect(op.after).toEqual({ name: 'NewName' });
  });

  it('rejects autoApplyConfidence > 1', () => {
    expect(() =>
      ChangeOpSchema.parse({ ...VALID_CHANGE_OP_BASE, autoApplyConfidence: 1.5 })
    ).toThrow();
  });

  it('rejects autoApplyConfidence < 0', () => {
    expect(() =>
      ChangeOpSchema.parse({ ...VALID_CHANGE_OP_BASE, autoApplyConfidence: -0.1 })
    ).toThrow();
  });

  it('accepts autoApplyConfidence at boundary 0', () => {
    const op = ChangeOpSchema.parse({ ...VALID_CHANGE_OP_BASE, autoApplyConfidence: 0 });
    expect(op.autoApplyConfidence).toBe(0);
  });

  it('accepts autoApplyConfidence at boundary 1', () => {
    const op = ChangeOpSchema.parse({ ...VALID_CHANGE_OP_BASE, autoApplyConfidence: 1 });
    expect(op.autoApplyConfidence).toBe(1);
  });

  it('rejects empty id', () => {
    expect(() =>
      ChangeOpSchema.parse({ ...VALID_CHANGE_OP_BASE, id: '' })
    ).toThrow();
  });

  it('rejects empty targetElementId', () => {
    expect(() =>
      ChangeOpSchema.parse({ ...VALID_CHANGE_OP_BASE, targetElementId: '' })
    ).toThrow();
  });

  it('rejects empty description', () => {
    expect(() =>
      ChangeOpSchema.parse({ ...VALID_CHANGE_OP_BASE, description: '' })
    ).toThrow();
  });

  it('rejects unknown kind in ChangeOpSchema', () => {
    expect(() =>
      ChangeOpSchema.parse({ ...VALID_CHANGE_OP_BASE, kind: 'delete' })
    ).toThrow();
  });

  it('parses remove op without after state', () => {
    const op = ChangeOpSchema.parse({
      ...VALID_CHANGE_OP_BASE,
      kind: 'remove-component',
      before: { id: 'elem-abc', name: 'SomeElement' },
      autoApplyConfidence: 0.7,
    });

    expect(op.kind).toBe('remove-component');
    expect(op.before).toBeDefined();
    expect(op.after).toBeUndefined();
  });
});
