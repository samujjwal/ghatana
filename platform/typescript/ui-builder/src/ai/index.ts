/**
 * @fileoverview AI module barrel for @ghatana/ui-builder.
 */

export type {
  AIHookKind,
  AIReviewState,
  AIActionLineage,
} from './lineage.js';

export {
  AIActionLineageTracker,
  createLineageEntry,
} from './lineage.js';

export type {
  AINodeProposal,
  AIHookProposal,
  AIHookNoOp,
  AIHookResult,
  AIHookContext,
  AIHookFn,
} from './hooks.js';

export {
  isAIHookProposal,
  AIHookRegistry,
  createNoOpHook,
  createDefaultAIHookRegistry,
} from './hooks.js';
