import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useBuilderAI } from '../useBuilderAI';
import type { UseBuilderAIOptions } from '../useBuilderAI';
import { AIHookRegistry, createDefaultAIHookRegistry } from '@ghatana/ui-builder';
import type { AIHookFn, AIHookProposal, AIHookNoOp } from '@ghatana/ui-builder';
import type { BuilderDocument } from '@ghatana/ui-builder';
import type { BuilderAISuggestionPayload, BuilderAIActionPayload } from '@ghatana/platform-events';
import { BuilderEvents } from '@ghatana/platform-events';

// ============================================================================
// Minimal BuilderDocument stub — just enough for the hook context
// ============================================================================

const stubDocument: BuilderDocument = {
  id: 'doc-test',
  version: 1,
  nodes: {},
  rootId: 'root',
  metadata: { title: 'Test' },
};

// ============================================================================
// Helpers
// ============================================================================

function makeOptions(
  overrides: Partial<UseBuilderAIOptions> = {},
): UseBuilderAIOptions & { emit: ReturnType<typeof vi.fn> } {
  const emit = vi.fn<[string, BuilderAISuggestionPayload | BuilderAIActionPayload], void>();
  return {
    registry: createDefaultAIHookRegistry(),
    document: stubDocument,
    emit,
    ...overrides,
  };
}

/** Minimal lineage fixture. */
const lineage = {
  hookKind: 'missing-prop-repair' as const,
  triggeredAt: Date.now(),
  confidence: 0.85,
  reason: 'Missing required props detected',
  reversibility: 'reversible' as const,
};

const stubProposal: AIHookProposal = {
  hookKind: 'missing-prop-repair',
  confidence: 0.85,
  summary: 'Fill missing required props',
  nodeProposals: [
    {
      nodeId: 'node-1',
      propsUpdate: { label: 'Default Label' },
      description: 'Add default label',
    },
  ],
  lineage,
};

const stubNoOp: AIHookNoOp = {
  hookKind: 'token-normalization',
  reason: 'No tokens to normalize',
};

// ============================================================================
// Note: useBuilderAI uses React hooks (useCallback, useRef) but is designed
// as a pure orchestration hook with no rendering side effects. We test it
// directly by calling the functions it returns — no renderHook required.
// The React runtime is not needed because we call the factory directly for
// the pure orchestration behaviour.
//
// Caveat: useCallback and useRef must be mocked since we're not in a React
// component tree. We mock react's hooks to return stable references.
// ============================================================================

vi.mock('react', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react')>();
  return {
    ...actual,
    useCallback: (fn: () => unknown) => fn,
    useRef: (initial: unknown) => ({ current: initial }),
  };
});

// ============================================================================
// Tests
// ============================================================================

describe('useBuilderAI', () => {
  describe('runAllHooks', () => {
    it('calls registry.invokeAll with correct context', async () => {
      const opts = makeOptions();
      const invokeAll = vi.spyOn(opts.registry, 'invokeAll').mockResolvedValue([]);
      const { runAllHooks } = useBuilderAI(opts);

      await runAllHooks();

      expect(invokeAll).toHaveBeenCalledWith(
        expect.objectContaining({ document: stubDocument }),
      );
    });

    it('passes rootNodeId override to context', async () => {
      const opts = makeOptions();
      const invokeAll = vi.spyOn(opts.registry, 'invokeAll').mockResolvedValue([]);
      const { runAllHooks } = useBuilderAI(opts);

      await runAllHooks({ rootNodeId: 'node-abc' });

      expect(invokeAll).toHaveBeenCalledWith(
        expect.objectContaining({ rootNodeId: 'node-abc' }),
      );
    });

    it('returns all hook results', async () => {
      const opts = makeOptions();
      vi.spyOn(opts.registry, 'invokeAll').mockResolvedValue([stubProposal, stubNoOp]);
      const { runAllHooks } = useBuilderAI(opts);

      const results = await runAllHooks();
      expect(results).toHaveLength(2);
    });

    it('emits AI_SUGGESTION_SHOWN for each proposal result', async () => {
      const opts = makeOptions();
      vi.spyOn(opts.registry, 'invokeAll').mockResolvedValue([stubProposal]);
      const { runAllHooks } = useBuilderAI(opts);

      await runAllHooks();

      expect(opts.emit).toHaveBeenCalledWith(
        BuilderEvents.AI_SUGGESTION_SHOWN,
        expect.objectContaining<Partial<BuilderAISuggestionPayload>>({
          kind: 'component',
          affectedComponentIds: ['node-1'],
        }),
      );
    });

    it('does NOT emit for no-op results', async () => {
      const opts = makeOptions();
      vi.spyOn(opts.registry, 'invokeAll').mockResolvedValue([stubNoOp]);
      const { runAllHooks } = useBuilderAI(opts);

      await runAllHooks();

      expect(opts.emit).not.toHaveBeenCalled();
    });

    it('emits for proposal but not for no-op in mixed results', async () => {
      const opts = makeOptions();
      vi.spyOn(opts.registry, 'invokeAll').mockResolvedValue([stubProposal, stubNoOp]);
      const { runAllHooks } = useBuilderAI(opts);

      await runAllHooks();

      expect(opts.emit).toHaveBeenCalledTimes(1);
      expect(opts.emit.mock.calls[0]?.[0]).toBe(BuilderEvents.AI_SUGGESTION_SHOWN);
    });
  });

  describe('acceptSuggestion', () => {
    it('emits AI_SUGGESTION_ACCEPTED', () => {
      const opts = makeOptions();
      const { acceptSuggestion } = useBuilderAI(opts);

      acceptSuggestion({ suggestionId: 'sug-1', proposal: stubProposal });

      const acceptedCall = opts.emit.mock.calls.find(
        ([name]) => name === BuilderEvents.AI_SUGGESTION_ACCEPTED,
      );
      expect(acceptedCall).toBeDefined();
      expect(acceptedCall?.[1]).toMatchObject({ suggestionId: 'sug-1' });
    });

    it('emits AI_ACTION_APPLIED after accepting', () => {
      const opts = makeOptions();
      const { acceptSuggestion } = useBuilderAI(opts);

      acceptSuggestion({ suggestionId: 'sug-1', proposal: stubProposal });

      const appliedCall = opts.emit.mock.calls.find(
        ([name]) => name === BuilderEvents.AI_ACTION_APPLIED,
      );
      expect(appliedCall).toBeDefined();
    });

    it('includes affectedComponentIds from proposal', () => {
      const opts = makeOptions();
      const { acceptSuggestion } = useBuilderAI(opts);

      acceptSuggestion({ suggestionId: 'sug-1', proposal: stubProposal });

      const acceptedPayload = opts.emit.mock.calls.find(
        ([name]) => name === BuilderEvents.AI_SUGGESTION_ACCEPTED,
      )?.[1] as BuilderAISuggestionPayload;

      expect(acceptedPayload.affectedComponentIds).toEqual(['node-1']);
    });

    it('visibility contract has APPROVED approval state on accepted call', () => {
      const opts = makeOptions();
      const { acceptSuggestion } = useBuilderAI(opts);

      acceptSuggestion({ suggestionId: 'sug-1', proposal: stubProposal });

      const acceptedPayload = opts.emit.mock.calls.find(
        ([name]) => name === BuilderEvents.AI_SUGGESTION_ACCEPTED,
      )?.[1] as BuilderAISuggestionPayload;

      expect(acceptedPayload.visibilityContract.approvalState).toBe('APPROVED');
    });
  });

  describe('rejectSuggestion', () => {
    it('emits AI_SUGGESTION_REJECTED', () => {
      const opts = makeOptions();
      const { rejectSuggestion } = useBuilderAI(opts);

      rejectSuggestion({ suggestionId: 'sug-2', proposal: stubProposal });

      const rejectedCall = opts.emit.mock.calls.find(
        ([name]) => name === BuilderEvents.AI_SUGGESTION_REJECTED,
      );
      expect(rejectedCall).toBeDefined();
      expect(rejectedCall?.[1]).toMatchObject({ suggestionId: 'sug-2' });
    });

    it('visibility contract has REJECTED approval state', () => {
      const opts = makeOptions();
      const { rejectSuggestion } = useBuilderAI(opts);

      rejectSuggestion({ suggestionId: 'sug-2', proposal: stubProposal });

      const payload = opts.emit.mock.calls.find(
        ([name]) => name === BuilderEvents.AI_SUGGESTION_REJECTED,
      )?.[1] as BuilderAISuggestionPayload;

      expect(payload.visibilityContract.approvalState).toBe('REJECTED');
    });

    it('does NOT emit AI_ACTION_APPLIED on rejection', () => {
      const opts = makeOptions();
      const { rejectSuggestion } = useBuilderAI(opts);

      rejectSuggestion({ suggestionId: 'sug-2', proposal: stubProposal });

      const appliedCall = opts.emit.mock.calls.find(
        ([name]) => name === BuilderEvents.AI_ACTION_APPLIED,
      );
      expect(appliedCall).toBeUndefined();
    });
  });

  describe('hook kind to suggestion kind mapping', () => {
    const mappingCases: Array<[string, BuilderAISuggestionPayload['kind']]> = [
      ['auto-layout-cleanup', 'layout'],
      ['accessibility-fix', 'component'],
      ['token-normalization', 'style'],
      ['action-wiring', 'binding'],
      ['missing-prop-repair', 'component'],
      ['responsive-adjustment', 'component'],
    ];

    for (const [hookKind, expectedKind] of mappingCases) {
      it(`maps hook kind '${hookKind}' to suggestion kind '${expectedKind}'`, async () => {
        const proposal: AIHookProposal = {
          ...stubProposal,
          hookKind: hookKind as AIHookProposal['hookKind'],
        };
        const opts = makeOptions();
        vi.spyOn(opts.registry, 'invokeAll').mockResolvedValue([proposal]);

        const { runAllHooks } = useBuilderAI(opts);
        await runAllHooks();

        const emittedPayload = opts.emit.mock.calls[0]?.[1] as BuilderAISuggestionPayload;
        expect(emittedPayload.kind).toBe(expectedKind);
      });
    }
  });
});
