import { describe, expect, it } from 'vitest';

import { LifecyclePhase } from '@/types/lifecycle';
import { deriveCanvasAccessPolicy, normalizeCanvasPolicyPhase } from '../canvasAccessPolicy';

describe('canvas access policy', () => {
  it('allows editable canvas work only during design phases with update access', () => {
    const policy = deriveCanvasAccessPolicy(LifecyclePhase.CONTEXT, {
      role: 'EDITOR',
      isOwned: true,
      capabilities: { read: true, update: true, create: true, delete: true, comment: true },
    });

    expect(policy.mode).toBe('design');
    expect(policy.canCreateArtifacts).toBe(true);
    expect(policy.canMoveNodes).toBe(true);
    expect(policy.readOnlyReason).toBeUndefined();
  });

  it('locks validation review even when the project is otherwise editable', () => {
    const policy = deriveCanvasAccessPolicy(LifecyclePhase.PLAN, {
      role: 'EDITOR',
      isOwned: true,
      capabilities: { read: true, update: true, create: true, delete: true, comment: true },
    });

    expect(policy.mode).toBe('review');
    expect(policy.canCreateArtifacts).toBe(false);
    expect(policy.canMutateArtifacts).toBe(false);
    expect(policy.canComment).toBe(true);
    expect(policy.readOnlyReason).toContain('validation review');
  });

  it('keeps included projects read-only regardless of phase', () => {
    const policy = deriveCanvasAccessPolicy(LifecyclePhase.CONTEXT, {
      role: 'VIEWER',
      isIncluded: true,
      readOnly: true,
      capabilities: { read: true, update: false, create: false, delete: false, comment: true },
    });

    expect(policy.canCreateArtifacts).toBe(false);
    expect(policy.canMoveNodes).toBe(false);
    expect(policy.canComment).toBe(true);
    expect(policy.readOnlyReason).toBe('You have view-only access to this project.');
  });

  it('does not invent write access when project metadata is missing', () => {
    const policy = deriveCanvasAccessPolicy(LifecyclePhase.CONTEXT, undefined);

    expect(policy.canCreateArtifacts).toBe(false);
    expect(policy.canGenerate).toBe(false);
    expect(policy.readOnlyReason).toBe('Canvas edits are unavailable in this mode.');
  });

  it('normalizes legacy phase strings before policy evaluation', () => {
    expect(normalizeCanvasPolicyPhase('GENERATE')).toBe(LifecyclePhase.EXECUTE);
    expect(normalizeCanvasPolicyPhase('RUN')).toBe(LifecyclePhase.VERIFY);
    expect(normalizeCanvasPolicyPhase(undefined)).toBe(LifecyclePhase.CONTEXT);
  });
});
