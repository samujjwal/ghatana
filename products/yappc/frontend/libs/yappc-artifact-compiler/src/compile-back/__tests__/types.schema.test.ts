/**
 * @fileoverview Test for P1-14: Compile-back types schema validation
 *
 * Verifies that ModelChange coverage includes page route, layout, token, API,
 * data entity, workflow operations, and unsupported/manual-review operation types.
 */

import { describe, it, expect } from 'vitest';
import {
  ChangeOpKindSchema,
  ChangeOpSchema,
  type ChangeOp,
  type ChangeOpKind,
} from '../types';

describe('P1-14: Compile-back Types Schema Validation', () => {
  it('should validate add-page-route operation', () => {
    const op: ChangeOp = {
      id: 'op-1',
      kind: 'add-page-route',
      targetElementId: 'route-123',
      description: 'Add new page route',
      before: undefined,
      after: { path: '/new-page', component: 'NewPage' },
      autoApplyConfidence: 0.9,
    };

    const result = ChangeOpSchema.safeParse(op);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.kind).toBe('add-page-route');
    }
  });

  it('should validate remove-page-route operation', () => {
    const op: ChangeOp = {
      id: 'op-2',
      kind: 'remove-page-route',
      targetElementId: 'route-123',
      description: 'Remove page route',
      before: { path: '/old-page', component: 'OldPage' },
      after: undefined,
      autoApplyConfidence: 0.8,
    };

    const result = ChangeOpSchema.safeParse(op);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.kind).toBe('remove-page-route');
    }
  });

  it('should validate update-page-route operation', () => {
    const op: ChangeOp = {
      id: 'op-3',
      kind: 'update-page-route',
      targetElementId: 'route-123',
      description: 'Update page route',
      before: { path: '/old-path' },
      after: { path: '/new-path' },
      autoApplyConfidence: 0.85,
    };

    const result = ChangeOpSchema.safeParse(op);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.kind).toBe('update-page-route');
    }
  });

  it('should validate layout operations', () => {
    const layoutOps: ChangeOpKind[] = ['add-layout', 'remove-layout', 'update-layout'];

    layoutOps.forEach((kind) => {
      const op: ChangeOp = {
        id: `op-${kind}`,
        kind,
        targetElementId: 'layout-123',
        description: `Layout operation: ${kind}`,
        before: kind === 'add-layout' ? undefined : { name: 'OldLayout' },
        after: kind === 'remove-layout' ? undefined : { name: 'NewLayout' },
        autoApplyConfidence: 0.9,
      };

      const result = ChangeOpSchema.safeParse(op);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.kind).toBe(kind);
      }
    });
  });

  it('should validate token operations', () => {
    const tokenOps: ChangeOpKind[] = ['add-token', 'remove-token', 'update-token'];

    tokenOps.forEach((kind) => {
      const op: ChangeOp = {
        id: `op-${kind}`,
        kind,
        targetElementId: 'token-123',
        description: `Token operation: ${kind}`,
        before: kind === 'add-token' ? undefined : { name: 'primary-color', value: '#000000' },
        after: kind === 'remove-token' ? undefined : { name: 'primary-color', value: '#ffffff' },
        autoApplyConfidence: 0.95,
      };

      const result = ChangeOpSchema.safeParse(op);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.kind).toBe(kind);
      }
    });
  });

  it('should validate API operations', () => {
    const apiOps: ChangeOpKind[] = ['add-api', 'remove-api', 'update-api'];

    apiOps.forEach((kind) => {
      const op: ChangeOp = {
        id: `op-${kind}`,
        kind,
        targetElementId: 'api-123',
        description: `API operation: ${kind}`,
        before: kind === 'add-api' ? undefined : { endpoint: '/api/users' },
        after: kind === 'remove-api' ? undefined : { endpoint: '/api/v1/users' },
        autoApplyConfidence: 0.88,
      };

      const result = ChangeOpSchema.safeParse(op);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.kind).toBe(kind);
      }
    });
  });

  it('should validate data entity operations', () => {
    const entityOps: ChangeOpKind[] = ['add-data-entity', 'remove-data-entity', 'update-data-entity'];

    entityOps.forEach((kind) => {
      const op: ChangeOp = {
        id: `op-${kind}`,
        kind,
        targetElementId: 'entity-123',
        description: `Data entity operation: ${kind}`,
        before: kind === 'add-data-entity' ? undefined : { name: 'User', fields: [] },
        after: kind === 'remove-data-entity' ? undefined : { name: 'User', fields: [{ name: 'email', type: 'string' }] },
        autoApplyConfidence: 0.82,
      };

      const result = ChangeOpSchema.safeParse(op);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.kind).toBe(kind);
      }
    });
  });

  it('should validate workflow operations', () => {
    const workflowOps: ChangeOpKind[] = ['add-workflow', 'remove-workflow', 'update-workflow'];

    workflowOps.forEach((kind) => {
      const op: ChangeOp = {
        id: `op-${kind}`,
        kind,
        targetElementId: 'workflow-123',
        description: `Workflow operation: ${kind}`,
        before: kind === 'add-workflow' ? undefined : { name: 'approval-flow' },
        after: kind === 'remove-workflow' ? undefined : { name: 'approval-flow-v2' },
        autoApplyConfidence: 0.75,
      };

      const result = ChangeOpSchema.safeParse(op);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.kind).toBe(kind);
      }
    });
  });

  it('should validate unsupported-operation type', () => {
    // P0-10: Operation that has no capable emitter
    const op: ChangeOp = {
      id: 'op-unsupported',
      kind: 'unsupported-operation',
      targetElementId: 'element-123',
      description: 'Operation with no capable emitter',
      before: { oldState: 'value' },
      after: { newState: 'value' },
      autoApplyConfidence: 0.0, // Should be 0 for unsupported
    };

    const result = ChangeOpSchema.safeParse(op);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.kind).toBe('unsupported-operation');
    }
  });

  it('should validate manual-review type', () => {
    // P1-14: Operation that requires manual review
    const op: ChangeOp = {
      id: 'op-manual-review',
      kind: 'manual-review',
      targetElementId: 'element-123',
      description: 'Complex change requiring manual review',
      before: { oldState: 'value' },
      after: { newState: 'value' },
      autoApplyConfidence: 0.5, // Lower confidence for manual review
    };

    const result = ChangeOpSchema.safeParse(op);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.kind).toBe('manual-review');
    }
  });

  it('should reject invalid operation kind', () => {
    const op = {
      id: 'op-invalid',
      kind: 'invalid-operation-kind' as any,
      targetElementId: 'element-123',
      description: 'Invalid operation',
      before: undefined,
      after: {},
      autoApplyConfidence: 0.9,
    };

    const result = ChangeOpSchema.safeParse(op);
    expect(result.success).toBe(false);
  });

  it('should enforce autoApplyConfidence range [0, 1]', () => {
    const invalidOp = {
      id: 'op-1',
      kind: 'add-component' as ChangeOpKind,
      targetElementId: 'element-123',
      description: 'Invalid confidence',
      before: undefined,
      after: {},
      autoApplyConfidence: 1.5, // Out of range
    };

    const result = ChangeOpSchema.safeParse(invalidOp);
    expect(result.success).toBe(false);
  });

  it('should require non-empty id and description', () => {
    const invalidOp = {
      id: '',
      kind: 'add-component' as ChangeOpKind,
      targetElementId: 'element-123',
      description: '',
      before: undefined,
      after: {},
      autoApplyConfidence: 0.9,
    };

    const result = ChangeOpSchema.safeParse(invalidOp);
    expect(result.success).toBe(false);
  });
});
