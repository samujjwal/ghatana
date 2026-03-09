/**
 * Learning Sequence Composer
 *
 * Composes multi-modal content (examples, simulations, animations) into
 * ordered learning sequences with adaptive branching based on real-time
 * mastery signals from BKT/IRT models.
 *
 * A learning sequence is a DAG of content nodes where edges carry mastery
 * thresholds. The composer selects the next node based on the learner's
 * current mastery state, enabling personalised pathways through content.
 *
 * @doc.type class
 * @doc.purpose Compose adaptive multi-modal learning sequences
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from '@ghatana/tutorputor-db';

// ============================================================================
// Types
// ============================================================================

/**
 * Modality of a content node within a sequence.
 */
export type ContentModality =
    | 'example'
    | 'simulation'
    | 'animation'
    | 'assessment'
    | 'explanation_prompt'
    | 'recap';

/**
 * A single node in the learning sequence DAG.
 */
export interface SequenceNode {
    /** Unique node ID */
    id: string;
    /** Claim this node targets */
    claimId: string;
    /** Content modality */
    modality: ContentModality;
    /** Reference to the actual content (example ID, sim manifest ID, etc.) */
    contentRef: string;
    /** Estimated duration in seconds */
    durationSeconds: number;
    /** Whether this node is optional (can be skipped if mastery is high) */
    optional: boolean;
    /** Metadata for rendering */
    meta: Record<string, unknown>;
}

/**
 * An edge in the sequence DAG — defines transition conditions.
 */
export interface SequenceEdge {
    /** Source node ID */
    from: string;
    /** Target node ID */
    to: string;
    /** Minimum mastery required to traverse (0-1). null = unconditional. */
    masteryThreshold: number | null;
    /** Maximum mastery — if exceeded, skip target (go to next). null = no ceiling. */
    masteryCeiling: number | null;
    /** Edge priority when multiple edges are valid (higher = preferred) */
    priority: number;
    /** Label for debugging / analytics */
    label?: string;
}

/**
 * A complete adaptive learning sequence.
 */
export interface LearningSequence {
    /** Sequence ID */
    id: string;
    /** Experience this sequence belongs to */
    experienceId: string;
    /** Claim IDs covered */
    claimIds: string[];
    /** All nodes */
    nodes: SequenceNode[];
    /** All edges */
    edges: SequenceEdge[];
    /** Entry node ID */
    entryNodeId: string;
    /** Terminal node IDs */
    exitNodeIds: string[];
    /** Estimated total duration range (min, max) in seconds */
    estimatedDuration: { min: number; max: number };
    /** Version for cache invalidation */
    version: number;
}

/**
 * Runtime state for a learner traversing a sequence.
 */
export interface SequenceProgress {
    learnerId: string;
    sequenceId: string;
    currentNodeId: string;
    completedNodeIds: string[];
    skippedNodeIds: string[];
    /** Real-time mastery per claim (0-1) */
    masteryState: Record<string, number>;
    startedAt: Date;
    lastActivityAt: Date;
}

/**
 * Decision about which node to present next.
 */
export interface NextNodeDecision {
    nodeId: string | null;
    reason: 'next_in_sequence' | 'branch_on_mastery' | 'skip_mastered' | 'remediation' | 'complete';
    skippedNodeIds: string[];
}

// ============================================================================
// Composer
// ============================================================================

export class LearningSequenceComposer {
    constructor(private readonly prisma: PrismaClient) {}

    /**
     * Build a learning sequence for a set of claims with their generated content.
     * Arranges content in pedagogical order: example → simulation → assessment → recap,
     * with branching for remediation if mastery drops.
     */
    composeSequence(
        experienceId: string,
        claims: Array<{
            claimId: string;
            claimText: string;
            bloomLevel: string;
            examples: Array<{ id: string; type: string; durationSeconds?: number }>;
            simulation?: { manifestId: string; durationSeconds?: number };
            animation?: { id: string; durationSeconds?: number };
        }>,
    ): LearningSequence {
        const nodes: SequenceNode[] = [];
        const edges: SequenceEdge[] = [];
        const claimIds = claims.map((c) => c.claimId);

        let prevNodeId: string | null = null;

        for (const claim of claims) {
            const claimNodes = this.buildClaimSubgraph(claim, nodes, edges);

            // Link previous claim's exit to this claim's entry
            if (prevNodeId && claimNodes.entryId) {
                edges.push({
                    from: prevNodeId,
                    to: claimNodes.entryId,
                    masteryThreshold: null,
                    masteryCeiling: null,
                    priority: 10,
                    label: 'next_claim',
                });
            }

            prevNodeId = claimNodes.exitId;
        }

        const entryNodeId = nodes.length > 0 ? nodes[0]!.id : '';
        const exitNodeIds = prevNodeId ? [prevNodeId] : [];

        // Estimate durations
        const totalDuration = nodes.reduce((sum, n) => sum + n.durationSeconds, 0);
        const optionalDuration = nodes
            .filter((n) => n.optional)
            .reduce((sum, n) => sum + n.durationSeconds, 0);

        return {
            id: `seq-${experienceId}-${Date.now()}`,
            experienceId,
            claimIds,
            nodes,
            edges,
            entryNodeId,
            exitNodeIds,
            estimatedDuration: {
                min: totalDuration - optionalDuration,
                max: totalDuration,
            },
            version: 1,
        };
    }

    /**
     * Determine the next node for a learner based on their current progress and mastery.
     */
    resolveNextNode(
        sequence: LearningSequence,
        progress: SequenceProgress,
    ): NextNodeDecision {
        const currentNode = progress.currentNodeId;
        const skipped: string[] = [];

        // Get outgoing edges from current node, sorted by priority
        const outgoing = sequence.edges
            .filter((e) => e.from === currentNode)
            .sort((a, b) => b.priority - a.priority);

        if (outgoing.length === 0) {
            return { nodeId: null, reason: 'complete', skippedNodeIds: [] };
        }

        for (const edge of outgoing) {
            const targetNode = sequence.nodes.find((n) => n.id === edge.to);
            if (!targetNode) continue;

            // Already completed?
            if (progress.completedNodeIds.includes(edge.to)) continue;

            const claimMastery = progress.masteryState[targetNode.claimId] ?? 0;

            // Check mastery ceiling — skip if learner already exceeds it
            if (edge.masteryCeiling !== null && claimMastery > edge.masteryCeiling) {
                skipped.push(edge.to);
                continue;
            }

            // Check mastery threshold — need sufficient mastery to proceed
            if (edge.masteryThreshold !== null && claimMastery < edge.masteryThreshold) {
                // This edge requires more mastery — check if it's a skip-ahead edge
                // If so, fall through to the next edge which may be remediation
                continue;
            }

            // Check if optional node can be skipped
            if (targetNode.optional && claimMastery >= 0.8) {
                skipped.push(edge.to);
                continue;
            }

            // Determine reason
            let reason: NextNodeDecision['reason'] = 'next_in_sequence';
            if (edge.masteryThreshold !== null) {
                reason = 'branch_on_mastery';
            }
            if (skipped.length > 0) {
                reason = 'skip_mastered';
            }
            if (edge.label === 'remediation') {
                reason = 'remediation';
            }

            return { nodeId: edge.to, reason, skippedNodeIds: skipped };
        }

        // All outgoing paths exhausted
        return { nodeId: null, reason: 'complete', skippedNodeIds: skipped };
    }

    // =========================================================================
    // Private
    // =========================================================================

    private buildClaimSubgraph(
        claim: {
            claimId: string;
            claimText: string;
            bloomLevel: string;
            examples: Array<{ id: string; type: string; durationSeconds?: number }>;
            simulation?: { manifestId: string; durationSeconds?: number };
            animation?: { id: string; durationSeconds?: number };
        },
        nodes: SequenceNode[],
        edges: SequenceEdge[],
    ): { entryId: string; exitId: string } {
        const { claimId } = claim;
        const subNodes: string[] = [];

        // 1. Animation (intro visual) — optional
        if (claim.animation) {
            const id = `${claimId}:anim`;
            nodes.push({
                id,
                claimId,
                modality: 'animation',
                contentRef: claim.animation.id,
                durationSeconds: claim.animation.durationSeconds ?? 30,
                optional: true,
                meta: {},
            });
            subNodes.push(id);
        }

        // 2. Examples — first is required, rest optional
        for (let i = 0; i < claim.examples.length; i++) {
            const ex = claim.examples[i]!;
            const id = `${claimId}:ex:${i}`;
            nodes.push({
                id,
                claimId,
                modality: 'example',
                contentRef: ex.id,
                durationSeconds: ex.durationSeconds ?? 120,
                optional: i > 0,
                meta: { exampleType: ex.type },
            });
            subNodes.push(id);
        }

        // 3. Simulation — if available
        if (claim.simulation) {
            const id = `${claimId}:sim`;
            nodes.push({
                id,
                claimId,
                modality: 'simulation',
                contentRef: claim.simulation.manifestId,
                durationSeconds: claim.simulation.durationSeconds ?? 300,
                optional: false,
                meta: {},
            });
            subNodes.push(id);
        }

        // 4. Assessment check
        const assessId = `${claimId}:assess`;
        nodes.push({
            id: assessId,
            claimId,
            modality: 'assessment',
            contentRef: claimId,
            durationSeconds: 60,
            optional: false,
            meta: {},
        });
        subNodes.push(assessId);

        // 5. Recap — only if mastery is low after assessment
        const recapId = `${claimId}:recap`;
        nodes.push({
            id: recapId,
            claimId,
            modality: 'recap',
            contentRef: claimId,
            durationSeconds: 60,
            optional: true,
            meta: {},
        });
        subNodes.push(recapId);

        // Wire sequential edges
        for (let i = 1; i < subNodes.length - 1; i++) {
            edges.push({
                from: subNodes[i - 1]!,
                to: subNodes[i]!,
                masteryThreshold: null,
                masteryCeiling: null,
                priority: 10,
                label: 'sequential',
            });
        }

        // Assessment → recap (only if mastery < 0.6)
        edges.push({
            from: assessId,
            to: recapId,
            masteryThreshold: null,
            masteryCeiling: 0.6,
            priority: 5,
            label: 'remediation',
        });

        // Assessment → next (skip recap if mastery >= 0.6)
        // This edge will be the exit edge — the caller links it to the next claim
        // We use recap as exit if it exists, otherwise assessment
        const exitId = recapId;
        const entryId = subNodes[0]!;

        return { entryId, exitId };
    }
}

/**
 * Factory function.
 */
export function createLearningSequenceComposer(
    prisma: PrismaClient,
): LearningSequenceComposer {
    return new LearningSequenceComposer(prisma);
}
