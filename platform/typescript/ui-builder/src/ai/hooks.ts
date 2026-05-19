/**
 * @fileoverview Builder AI hooks — pluggable AI operations for UI Builder documents.
 *
 * Each hook receives a BuilderDocument (read-only), returns a proposal containing:
 * - The operations to apply (as immer-compatible patches or builder operation calls)
 * - An AIActionLineage record for traceability
 * - An optional set of before/after diffs for review UX
 *
 * Hooks are registered in an `AIHookRegistry` and invoked by the builder runtime
 * or product-level orchestration (e.g., yappc-ai).  Hooks MUST NOT mutate the
 * document directly — they return a proposal that the caller applies.
 *
 * Available hook types:
 * - missing-prop-repair: Fill in missing required props using contract defaults
 * - token-normalization: Replace raw values with design token references
 * - auto-layout-cleanup: Normalize inconsistent padding, gap, and alignment
 * - accessibility-fix: Add missing alt text, aria-labels, roles, etc.
 * - responsive-adjustment: Add or fix responsive variants for common breakpoints
 * - property-completion: Complete partially-filled props using contract guidance
 * - action-wiring: Suggest event→action bindings based on contract patterns
 */

import type { BuilderDocument } from '../core/builder-document.js';
import type { ComponentInstance, NodeId } from '../core/types.js';
import type { AIActionLineage, AIHookKind } from './lineage.js';
import type { ComponentContract } from '@ghatana/ds-schema';

// ============================================================================
// Hook Result
// ============================================================================

/** A single proposed change to a specific node's props. */
export interface AINodeProposal {
  /** Node to modify. */
  readonly nodeId: NodeId;
  /** Partial props to merge (undefined values are ignored). */
  readonly propsUpdate: Record<string, unknown>;
  /** Human-readable description of what this change does. */
  readonly description: string;
}

/** A complete proposal returned by a hook. */
export interface AIHookProposal {
  /** Hook that produced this proposal. */
  readonly hookKind: AIHookKind;
  /** Overall confidence [0.0, 1.0]. */
  readonly confidence: number;
  /** Human-readable summary of all proposed changes. */
  readonly summary: string;
  /** Individual node-level changes. */
  readonly nodeProposals: readonly AINodeProposal[];
  /** Lineage record for this proposal (one per hook invocation). */
  readonly lineage: AIActionLineage;
  /** True when policy requires a human to review before applying the proposal. */
  readonly reviewRequired?: boolean;
  /** Policy markers explaining why review or gating was applied. */
  readonly policyTriggers?: readonly string[];
}

/** Returned when a hook has no suggestions. */
export interface AIHookNoOp {
  readonly hookKind: AIHookKind;
  readonly reason: string;
}

export type AIHookResult = AIHookProposal | AIHookNoOp;

/** Type guard: narrows to a proposal (has changes). */
export function isAIHookProposal(result: AIHookResult): result is AIHookProposal {
  return 'nodeProposals' in result;
}

function getNodeById(document: BuilderDocument, nodeId: NodeId): ComponentInstance | undefined {
  const nodes = document.nodes as unknown;
  if (nodes instanceof Map) {
    return nodes.get(nodeId) as ComponentInstance | undefined;
  }

  if (nodes && typeof nodes === 'object') {
    return (nodes as Record<string, ComponentInstance>)[nodeId as string];
  }

  return undefined;
}

function getContractByName(context: AIHookContext, contractName: string): ComponentContract | undefined {
  const documentContracts = (context.document as BuilderDocument & {
    designSystem?: { componentContracts?: readonly ComponentContract[] };
  }).designSystem?.componentContracts;
  return [...(context.contracts ?? []), ...(documentContracts ?? [])]
    .find((contract: ComponentContract) => contract.name === contractName);
}

function applyContractPolicy(result: AIHookResult, context: AIHookContext): AIHookResult {
  if (!isAIHookProposal(result)) {
    return result;
  }

  if (result.nodeProposals.length === 0) {
    return result;
  }

  const policyTriggers = new Set<string>();
  let reviewRequired = false;

  const filteredNodeProposals = result.nodeProposals.filter((proposal) => {
    const node = getNodeById(context.document, proposal.nodeId);
    if (!node) {
      return true;
    }

    const contract = getContractByName(context, node.contractName);
    if (!contract) {
      return true;
    }

    const aiPolicy = contract.aiPolicy;

    if (aiPolicy?.allowAutonomousConfiguration === false) {
      policyTriggers.add('ai.autonomous-configuration.disabled');
      return false;
    }

    if (aiPolicy?.permittedActions?.length && !aiPolicy.permittedActions.includes('set-prop')) {
      policyTriggers.add('ai.action.set-prop.not-permitted');
      return false;
    }

    const updatedPropNames = Object.keys(proposal.propsUpdate);

    if (aiPolicy?.reviewRequiredProps?.some((propName) => updatedPropNames.includes(propName))) {
      reviewRequired = true;
      policyTriggers.add('ai.prop.review-required');
    }

    if (contract.privacy?.mayRenderPii || contract.privacy?.regulatoryFrameworks.length) {
      reviewRequired = true;
      policyTriggers.add('security.privacy.review-required');
    }

    for (const propName of updatedPropNames) {
      const propContract = contract.props.find((prop) => prop.name === propName);
      if (!propContract) {
        continue;
      }

      if (propContract.secretBearing || propContract.reviewRequired) {
        reviewRequired = true;
        policyTriggers.add('security.prop.review-required');
      }

      if (
        propContract.dataClassification === 'restricted' ||
        propContract.dataClassification === 'confidential' ||
        propContract.dataClassification === 'pii' ||
        propContract.dataClassification === 'sensitive'
      ) {
        reviewRequired = true;
        policyTriggers.add('security.data-classification.review-required');
      }
    }

    return true;
  });

  if (!filteredNodeProposals.length) {
    return {
      hookKind: result.hookKind,
      reason: 'All proposals were blocked by AI/security policy constraints.',
    };
  }

  return {
    ...result,
    nodeProposals: filteredNodeProposals,
    reviewRequired: reviewRequired || result.reviewRequired,
    policyTriggers: [...policyTriggers],
  };
}

// ============================================================================
// Hook Function Signature
// ============================================================================

/** Context passed to every hook. */
export interface AIHookContext {
  /** The document to analyze (treat as read-only). */
  readonly document: BuilderDocument;
  /**
   * Optional node scope — if provided, the hook should limit its analysis to
   * the subtree rooted at this node.
   */
  readonly rootNodeId?: NodeId;
  /** Correlation ID for linking the proposal to a platform event. */
  readonly correlationId?: string;
  /** Component contracts available to policy checks. */
  readonly contracts?: readonly ComponentContract[];
}

/** A pluggable AI hook function. */
export type AIHookFn = (context: AIHookContext) => Promise<AIHookResult>;

// ============================================================================
// Hook Registry
// ============================================================================

/**
 * Registry for builder AI hooks.  Each hook kind maps to exactly one function.
 * Product code (e.g., yappc-ai) should register product-specific implementations;
 * shared platform code provides sensible stubs or no-op defaults.
 */
export class AIHookRegistry {
  private readonly hooks = new Map<AIHookKind, AIHookFn>();

  /** Register a hook for the given kind. Overwrites any existing registration. */
  register(kind: AIHookKind, fn: AIHookFn): void {
    this.hooks.set(kind, fn);
  }

  /** Returns true if a hook is registered for the given kind. */
  has(kind: AIHookKind): boolean {
    return this.hooks.has(kind);
  }

  /** Invoke a registered hook. Throws if the hook kind is not registered. */
  async invoke(kind: AIHookKind, context: AIHookContext): Promise<AIHookResult> {
    const fn = this.hooks.get(kind);
    if (!fn) {
      throw new Error(`No AI hook registered for kind '${kind}'.`);
    }
    const result = await fn(context);
    return applyContractPolicy(result, context);
  }

  /**
   * Invoke ALL registered hooks in parallel and return all results.
   * Hooks that throw are captured as no-op results to prevent partial failures
   * from blocking the rest.
   */
  async invokeAll(context: AIHookContext): Promise<readonly AIHookResult[]> {
    const entries = [...this.hooks.entries()];
    const results = await Promise.allSettled(
      entries.map(async ([, fn]) => fn(context)),
    );
    return results.map((r, i): AIHookResult => {
      if (r.status === 'fulfilled') return r.value;
      const kind = entries[i]?.[0] ?? ('missing-prop-repair' as AIHookKind);
      return { hookKind: kind, reason: `Hook threw: ${String(r.reason)}` };
    }).map((result) => applyContractPolicy(result, context));
  }

  /** Returns the registered hook kinds. */
  registeredKinds(): readonly AIHookKind[] {
    return [...this.hooks.keys()];
  }
}

// ============================================================================
// No-op stub implementations (platform defaults)
// ============================================================================

/** Creates a no-op stub for any hook kind that returns "nothing to do". */
export function createNoOpHook(kind: AIHookKind, reason = 'No implementation registered.'): AIHookFn {
  return async (): Promise<AIHookNoOp> => ({ hookKind: kind, reason });
}

/**
 * Creates a default AIHookRegistry with no-op stubs for all known hook kinds.
 * Product code should replace individual stubs with real implementations via
 * `registry.register(kind, realFn)`.
 */
export function createDefaultAIHookRegistry(): AIHookRegistry {
  const registry = new AIHookRegistry();
  const allKinds: readonly AIHookKind[] = [
    'missing-prop-repair',
    'token-normalization',
    'auto-layout-cleanup',
    'accessibility-fix',
    'responsive-adjustment',
    'property-completion',
    'action-wiring',
  ];
  for (const kind of allKinds) {
    registry.register(kind, createNoOpHook(kind));
  }
  return registry;
}
