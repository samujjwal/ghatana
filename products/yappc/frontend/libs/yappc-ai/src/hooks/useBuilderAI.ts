/**
 * @fileoverview Builder AI hook — connects the YAPPC builder to the
 * `@ghatana/ui-builder` `AIHookRegistry` and emits canonical
 * `@ghatana/platform-events` AI events when suggestions are shown,
 * accepted, rejected, or applied.
 *
 * Usage:
 * ```ts
 * const { runAllHooks, acceptSuggestion, rejectSuggestion } = useBuilderAI({
 *   registry,
 *   document,
 *   emit: platformEventBus.emit,
 * });
 *
 * // Trigger analysis of the current document:
 * const proposals = await runAllHooks();
 * // proposals is AIHookResult[]
 *
 * // When the user accepts a proposal:
 * acceptSuggestion({ suggestionId: 'id', proposal, affectedComponentIds: [...] });
 * ```
 *
 * @doc.type module
 * @doc.purpose Builder AI hooks integration — registry orchestration + platform events
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useCallback, useRef } from 'react';
import type {
  AIHookRegistry,
  AIHookContext,
  AIHookResult,
  AIHookProposal,
} from '@ghatana/ui-builder';
import { isAIHookProposal } from '@ghatana/ui-builder';
import type { BuilderDocument } from '@ghatana/ui-builder';
import {
  BuilderEvents,
  createAIVisibilityContract,
} from '@ghatana/platform-events';
import type {
  BuilderAISuggestionPayload,
  BuilderAIActionPayload,
} from '@ghatana/platform-events';

// ============================================================================
// Types
// ============================================================================

/** Callback signature for emitting platform events from the hook. */
export type BuilderAIEmitFn = (
  eventName: string,
  payload: BuilderAISuggestionPayload | BuilderAIActionPayload,
) => void;

/** Options for useBuilderAI. */
export interface UseBuilderAIOptions {
  /** The AI hook registry (from `createDefaultAIHookRegistry` or custom). */
  readonly registry: AIHookRegistry;
  /** The current builder document to analyze. */
  readonly document: BuilderDocument;
  /** Platform event emitter — receives canonical builder AI events. */
  readonly emit: BuilderAIEmitFn;
  /** Optional correlation ID for tracing. Auto-generated per invocation if omitted. */
  readonly correlationId?: string;
}

/** Return value of useBuilderAI. */
export interface UseBuilderAIResult {
  /**
   * Run all registered hooks against the current document and return the
   * proposals. Automatically emits a `builder.ai.suggestion.shown` event
   * for each proposal result.
   */
  runAllHooks: (
    context?: Partial<Pick<AIHookContext, 'rootNodeId'>>,
  ) => Promise<readonly AIHookResult[]>;
  /**
   * Accept a proposal. Emits `builder.ai.suggestion.accepted` and
   * `builder.ai.action.applied`.
   */
  acceptSuggestion: (options: AcceptSuggestionOptions) => void;
  /**
   * Reject a proposal. Emits `builder.ai.suggestion.rejected`.
   */
  rejectSuggestion: (options: RejectSuggestionOptions) => void;
}

/** Options for accepting a suggestion. */
export interface AcceptSuggestionOptions {
  readonly suggestionId: string;
  readonly proposal: AIHookProposal;
}

/** Options for rejecting a suggestion. */
export interface RejectSuggestionOptions {
  readonly suggestionId: string;
  readonly proposal: AIHookProposal;
}

// ============================================================================
// Hook
// ============================================================================

/**
 * React hook that wires the `AIHookRegistry` to the YAPPC builder and emits
 * canonical platform events for AI suggestion lifecycle events.
 *
 * All functions are stable references (useCallback + useRef pattern).
 */
export function useBuilderAI(options: UseBuilderAIOptions): UseBuilderAIResult {
  // Keep latest options accessible inside stable callbacks without re-creating
  // the callbacks on every render.
  const optionsRef = useRef(options);
  optionsRef.current = options;

  const runAllHooks = useCallback(
    async (
      contextOverrides: Partial<Pick<AIHookContext, 'rootNodeId'>> = {},
    ): Promise<readonly AIHookResult[]> => {
      const { registry, document, emit, correlationId } = optionsRef.current;

      const context: AIHookContext = {
        document,
        correlationId,
        ...contextOverrides,
      };

      const results = await registry.invokeAll(context);

      // Emit a suggestion.shown event for each proposal result.
      for (const result of results) {
        if (!isAIHookProposal(result)) continue;

        const suggestionId = `${result.hookKind}-${Date.now()}`;
        const affectedComponentIds = result.nodeProposals.map((p) => p.nodeId);
        const visibilityContract = createAIVisibilityContract(
          result.summary,
          (correlationId ?? suggestionId) as `corr-${string}`,
          {
            operationState: 'completed',
            confidenceBand: { low: result.confidence, high: result.confidence },
            rationale: result.summary,
            approvalState: 'PENDING',
            reviewRequired: true,
          },
        );

        const payload: BuilderAISuggestionPayload = {
          suggestionId,
          kind: mapHookKindToSuggestionKind(result.hookKind),
          affectedComponentIds,
          visibilityContract,
        };

        emit(BuilderEvents.AI_SUGGESTION_SHOWN, payload);
      }

      return results;
    },
    [],
  );

  const acceptSuggestion = useCallback(
    (opts: AcceptSuggestionOptions): void => {
      const { emit, correlationId } = optionsRef.current;
      const { suggestionId, proposal } = opts;
      const affectedComponentIds = proposal.nodeProposals.map((p) => p.nodeId);
      const visibilityContract = createAIVisibilityContract(
        proposal.summary,
        (correlationId ?? suggestionId) as `corr-${string}`,
        {
          operationState: 'completed',
          confidenceBand: {
            low: proposal.confidence,
            high: proposal.confidence,
          },
          rationale: proposal.summary,
          approvalState: 'APPROVED',
          reviewRequired: false,
          appliedChanges: proposal.nodeProposals.map((p) => ({
            region: p.nodeId,
            summary: p.description,
            kind: 'update' as const,
          })),
        },
      );

      const suggestionPayload: BuilderAISuggestionPayload = {
        suggestionId,
        kind: mapHookKindToSuggestionKind(proposal.hookKind),
        affectedComponentIds,
        visibilityContract,
      };
      emit(BuilderEvents.AI_SUGGESTION_ACCEPTED, suggestionPayload);

      const actionPayload: BuilderAIActionPayload = {
        actionId: `action-${suggestionId}`,
        actionType: proposal.hookKind,
        affectedComponentIds,
        visibilityContract: {
          ...visibilityContract,
          operationState: 'completed',
        },
      };
      emit(BuilderEvents.AI_ACTION_APPLIED, actionPayload);
    },
    [],
  );

  const rejectSuggestion = useCallback(
    (opts: RejectSuggestionOptions): void => {
      const { emit, correlationId } = optionsRef.current;
      const { suggestionId, proposal } = opts;
      const affectedComponentIds = proposal.nodeProposals.map((p) => p.nodeId);
      const visibilityContract = createAIVisibilityContract(
        proposal.summary,
        (correlationId ?? suggestionId) as `corr-${string}`,
        {
          operationState: 'completed',
          confidenceBand: {
            low: proposal.confidence,
            high: proposal.confidence,
          },
          rationale: proposal.summary,
          approvalState: 'REJECTED',
          reviewRequired: false,
        },
      );

      const payload: BuilderAISuggestionPayload = {
        suggestionId,
        kind: mapHookKindToSuggestionKind(proposal.hookKind),
        affectedComponentIds,
        visibilityContract,
      };
      emit(BuilderEvents.AI_SUGGESTION_REJECTED, payload);
    },
    [],
  );

  return { runAllHooks, acceptSuggestion, rejectSuggestion };
}

// ============================================================================
// Helpers
// ============================================================================

type SuggestionKind = BuilderAISuggestionPayload['kind'];

/** Maps an AIHookKind to the BuilderAISuggestionPayload suggestion kind. */
function mapHookKindToSuggestionKind(hookKind: string): SuggestionKind {
  switch (hookKind) {
    case 'auto-layout-cleanup':
      return 'layout';
    case 'accessibility-fix':
      return 'component';
    case 'token-normalization':
      return 'style';
    case 'action-wiring':
      return 'binding';
    default:
      return 'component';
  }
}
