import { describe, it, expect, vi } from 'vitest';
import {
  AIHookRegistry,
  createDefaultAIHookRegistry,
  createNoOpHook,
  isAIHookProposal,
} from '../../ai/hooks.js';
import type { AIHookContext, AIHookResult, AIHookFn } from '../../ai/hooks.js';
import type { AIHookKind } from '../../ai/lineage.js';
import { createLineageEntry } from '../../ai/lineage.js';
import type { BuilderDocument } from '../../core/types.js';

function makeContext(overrides: Partial<AIHookContext> = {}): AIHookContext {
  return {
    document: {
      metadata: { trustLevel: 'GENERATED_TRUSTED' },
      nodes: {},
      rootNodes: [],
      designSystem: { componentContracts: [] },
    } as unknown as BuilderDocument,
    ...overrides,
  };
}

describe('createNoOpHook', () => {
  it('returns a no-op result for any context', async () => {
    const hook = createNoOpHook('accessibility-fix');
    const result = await hook(makeContext());
    expect(result.hookKind).toBe('accessibility-fix');
    expect(isAIHookProposal(result)).toBe(false);
  });

  it('uses custom reason when provided', async () => {
    const hook = createNoOpHook('token-normalization', 'Feature not implemented yet.');
    const result = await hook(makeContext());
    expect((result as { reason: string }).reason).toBe('Feature not implemented yet.');
  });
});

describe('isAIHookProposal', () => {
  it('returns true for a proposal (has nodeProposals)', () => {
    const entry = createLineageEntry('missing-prop-repair', 'Fixed', 0.9, []);
    const proposal: AIHookResult = {
      hookKind: 'missing-prop-repair',
      confidence: 0.9,
      summary: 'Fixed missing props',
      nodeProposals: [],
      lineage: entry,
    };
    expect(isAIHookProposal(proposal)).toBe(true);
  });

  it('returns false for a no-op', () => {
    const noOp: AIHookResult = { hookKind: 'accessibility-fix', reason: 'No suggestions.' };
    expect(isAIHookProposal(noOp)).toBe(false);
  });
});

describe('AIHookRegistry', () => {
  it('throws when invoking an unregistered hook', async () => {
    const registry = new AIHookRegistry();
    await expect(registry.invoke('accessibility-fix', makeContext())).rejects.toThrow(
      "No AI hook registered for kind 'accessibility-fix'.",
    );
  });

  it('has() returns false before registration', () => {
    const registry = new AIHookRegistry();
    expect(registry.has('token-normalization')).toBe(false);
  });

  it('register() and has() work correctly', () => {
    const registry = new AIHookRegistry();
    registry.register('token-normalization', createNoOpHook('token-normalization'));
    expect(registry.has('token-normalization')).toBe(true);
  });

  it('invoke() calls the registered function', async () => {
    const registry = new AIHookRegistry();
    const fn = vi.fn<AIHookFn>(async () => ({
      hookKind: 'token-normalization',
      reason: 'Nothing to do.',
    }));
    registry.register('token-normalization', fn);
    const ctx = makeContext();
    await registry.invoke('token-normalization', ctx);
    expect(fn).toHaveBeenCalledOnce();
    expect(fn).toHaveBeenCalledWith(ctx);
  });

  it('invoke() overwrites previous registration', async () => {
    const registry = new AIHookRegistry();
    registry.register('auto-layout-cleanup', createNoOpHook('auto-layout-cleanup', 'first'));
    registry.register('auto-layout-cleanup', createNoOpHook('auto-layout-cleanup', 'second'));
    const result = await registry.invoke('auto-layout-cleanup', makeContext());
    expect((result as { reason: string }).reason).toBe('second');
  });

  it('registeredKinds() returns all registered kinds', () => {
    const registry = new AIHookRegistry();
    registry.register('accessibility-fix', createNoOpHook('accessibility-fix'));
    registry.register('action-wiring', createNoOpHook('action-wiring'));
    const kinds = registry.registeredKinds();
    expect(kinds).toContain('accessibility-fix');
    expect(kinds).toContain('action-wiring');
    expect(kinds).toHaveLength(2);
  });

  it('invokeAll() runs all registered hooks and returns all results', async () => {
    const registry = new AIHookRegistry();
    registry.register('accessibility-fix', createNoOpHook('accessibility-fix'));
    registry.register('token-normalization', createNoOpHook('token-normalization'));
    const results = await registry.invokeAll(makeContext());
    expect(results).toHaveLength(2);
  });

  it('invokeAll() captures thrown hooks as no-op results without aborting others', async () => {
    const registry = new AIHookRegistry();
    const throwingHook: AIHookFn = async () => {
      throw new Error('Hook failure');
    };
    registry.register('accessibility-fix', throwingHook);
    registry.register('token-normalization', createNoOpHook('token-normalization'));
    const results = await registry.invokeAll(makeContext());
    expect(results).toHaveLength(2);
    // Both results should be no-ops (one from error, one from stub)
    for (const result of results) {
      expect(isAIHookProposal(result)).toBe(false);
    }
  });
});

describe('createDefaultAIHookRegistry', () => {
  it('has all 7 hook kinds registered as no-ops', async () => {
    const registry = createDefaultAIHookRegistry();
    const allKinds: readonly AIHookKind[] = [
      'missing-prop-repair',
      'token-normalization',
      'auto-layout-cleanup',
      'accessibility-fix',
      'responsive-adjustment',
      'property-completion',
      'action-wiring',
    ];
    expect(registry.registeredKinds()).toHaveLength(allKinds.length);
    for (const kind of allKinds) {
      expect(registry.has(kind)).toBe(true);
    }
  });

  it('invoking a default hook returns a no-op result', async () => {
    const registry = createDefaultAIHookRegistry();
    const result = await registry.invoke('missing-prop-repair', makeContext());
    expect(isAIHookProposal(result)).toBe(false);
  });

  it('default hooks can be replaced with real implementations', async () => {
    const registry = createDefaultAIHookRegistry();
    const lineage = createLineageEntry('property-completion', 'Completed size prop', 0.85, []);
    const realHook: AIHookFn = async () => ({
      hookKind: 'property-completion',
      confidence: 0.85,
      summary: 'Completed missing size prop using contract defaults.',
      nodeProposals: [],
      lineage,
    });
    registry.register('property-completion', realHook);
    const result = await registry.invoke('property-completion', makeContext());
    expect(isAIHookProposal(result)).toBe(true);
  });
});
