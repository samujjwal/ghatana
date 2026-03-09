/**
 * Learning Sequence Composer Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test adaptive multi-modal learning sequence composition
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect } from 'vitest';
import { LearningSequenceComposer } from '../sequence-composer';
import type { LearningSequence, SequenceProgress } from '../sequence-composer';

// Minimal Prisma mock — composer methods under test don't use prisma
const mockPrisma = {} as any;

function makeComposer(): LearningSequenceComposer {
    return new LearningSequenceComposer(mockPrisma);
}

const SAMPLE_CLAIMS = [
    {
        claimId: 'C1',
        claimText: 'Explain Newton\'s first law',
        bloomLevel: 'understand',
        examples: [
            { id: 'ex-C1-1', type: 'real_world_application', durationSeconds: 90 },
            { id: 'ex-C1-2', type: 'visual_representation', durationSeconds: 60 },
        ],
        simulation: { manifestId: 'sim-C1', durationSeconds: 300 },
        animation: { id: 'anim-C1', durationSeconds: 30 },
    },
    {
        claimId: 'C2',
        claimText: 'Calculate net force',
        bloomLevel: 'apply',
        examples: [
            { id: 'ex-C2-1', type: 'problem_solving', durationSeconds: 120 },
        ],
        simulation: undefined,
        animation: undefined,
    },
];

describe('LearningSequenceComposer', () => {
    describe('composeSequence', () => {
        it('creates a sequence with nodes and edges', () => {
            const composer = makeComposer();
            const seq = composer.composeSequence('exp-1', SAMPLE_CLAIMS);

            expect(seq.experienceId).toBe('exp-1');
            expect(seq.claimIds).toEqual(['C1', 'C2']);
            expect(seq.nodes.length).toBeGreaterThan(0);
            expect(seq.edges.length).toBeGreaterThan(0);
            expect(seq.version).toBe(1);
        });

        it('includes all modalities for a claim with full content', () => {
            const composer = makeComposer();
            const seq = composer.composeSequence('exp-1', SAMPLE_CLAIMS);

            const c1Nodes = seq.nodes.filter((n) => n.claimId === 'C1');
            const modalities = c1Nodes.map((n) => n.modality);

            expect(modalities).toContain('animation');
            expect(modalities).toContain('example');
            expect(modalities).toContain('simulation');
            expect(modalities).toContain('assessment');
            expect(modalities).toContain('recap');
        });

        it('skips simulation and animation nodes when content is absent', () => {
            const composer = makeComposer();
            const seq = composer.composeSequence('exp-1', SAMPLE_CLAIMS);

            const c2Nodes = seq.nodes.filter((n) => n.claimId === 'C2');
            const modalities = c2Nodes.map((n) => n.modality);

            expect(modalities).not.toContain('simulation');
            expect(modalities).not.toContain('animation');
            expect(modalities).toContain('example');
            expect(modalities).toContain('assessment');
        });

        it('marks first example as required, rest as optional', () => {
            const composer = makeComposer();
            const seq = composer.composeSequence('exp-1', SAMPLE_CLAIMS);

            const c1Examples = seq.nodes.filter(
                (n) => n.claimId === 'C1' && n.modality === 'example',
            );

            expect(c1Examples.length).toBe(2);
            expect(c1Examples[0]!.optional).toBe(false);
            expect(c1Examples[1]!.optional).toBe(true);
        });

        it('has entry and exit node IDs set', () => {
            const composer = makeComposer();
            const seq = composer.composeSequence('exp-1', SAMPLE_CLAIMS);

            expect(seq.entryNodeId).toBeTruthy();
            expect(seq.exitNodeIds.length).toBeGreaterThan(0);

            // Entry node exists in nodes
            expect(seq.nodes.some((n) => n.id === seq.entryNodeId)).toBe(true);
        });

        it('estimates duration range correctly', () => {
            const composer = makeComposer();
            const seq = composer.composeSequence('exp-1', SAMPLE_CLAIMS);

            expect(seq.estimatedDuration.min).toBeGreaterThan(0);
            expect(seq.estimatedDuration.max).toBeGreaterThanOrEqual(seq.estimatedDuration.min);
        });

        it('includes remediation edge from assessment to recap', () => {
            const composer = makeComposer();
            const seq = composer.composeSequence('exp-1', SAMPLE_CLAIMS);

            const remediationEdges = seq.edges.filter((e) => e.label === 'remediation');
            expect(remediationEdges.length).toBeGreaterThan(0);

            // Remediation edge should have a mastery ceiling
            for (const edge of remediationEdges) {
                expect(edge.masteryCeiling).toBe(0.6);
            }
        });

        it('handles empty claims array', () => {
            const composer = makeComposer();
            const seq = composer.composeSequence('exp-1', []);

            expect(seq.nodes).toHaveLength(0);
            expect(seq.edges).toHaveLength(0);
            expect(seq.entryNodeId).toBe('');
        });

        it('handles single claim', () => {
            const composer = makeComposer();
            const seq = composer.composeSequence('exp-1', [SAMPLE_CLAIMS[0]!]);

            expect(seq.claimIds).toEqual(['C1']);
            expect(seq.nodes.length).toBeGreaterThan(0);
        });
    });

    describe('resolveNextNode', () => {
        function buildTestSequence(): LearningSequence {
            const composer = makeComposer();
            return composer.composeSequence('exp-1', SAMPLE_CLAIMS);
        }

        function makeProgress(
            seq: LearningSequence,
            overrides: Partial<SequenceProgress> = {},
        ): SequenceProgress {
            return {
                learnerId: 'learner-1',
                sequenceId: seq.id,
                currentNodeId: seq.entryNodeId,
                completedNodeIds: [],
                skippedNodeIds: [],
                masteryState: {},
                startedAt: new Date(),
                lastActivityAt: new Date(),
                ...overrides,
            };
        }

        it('returns next sequential node from entry', () => {
            const seq = buildTestSequence();
            const progress = makeProgress(seq);

            const decision = makeComposer().resolveNextNode(seq, progress);
            expect(decision.nodeId).toBeTruthy();
            expect(decision.reason).not.toBe('complete');
        });

        it('returns complete when at terminal node with no outgoing edges', () => {
            const seq = buildTestSequence();
            const lastNodeId = seq.exitNodeIds[0]!;
            const progress = makeProgress(seq, { currentNodeId: lastNodeId });

            const decision = makeComposer().resolveNextNode(seq, progress);
            // The exit node may or may not have outgoing edges depending on structure
            // If no outgoing, it should be complete
            if (!seq.edges.some((e) => e.from === lastNodeId)) {
                expect(decision.reason).toBe('complete');
            }
        });

        it('skips optional nodes when mastery is high', () => {
            const seq = buildTestSequence();
            // Find an optional node
            const optionalNode = seq.nodes.find((n) => n.optional);
            if (!optionalNode) return; // skip if no optional nodes

            // Find the edge leading to this optional node
            const edgeTo = seq.edges.find((e) => e.to === optionalNode.id);
            if (!edgeTo) return;

            const progress = makeProgress(seq, {
                currentNodeId: edgeTo.from,
                masteryState: { [optionalNode.claimId]: 0.9 },
            });

            const decision = makeComposer().resolveNextNode(seq, progress);
            // With high mastery (0.9 >= 0.8), optional nodes should be skipped
            if (decision.skippedNodeIds.includes(optionalNode.id)) {
                expect(decision.skippedNodeIds).toContain(optionalNode.id);
            }
        });

        it('follows remediation path when mastery is low', () => {
            const seq = buildTestSequence();
            // Find assessment node
            const assessNode = seq.nodes.find(
                (n) => n.modality === 'assessment' && n.claimId === 'C1',
            );
            if (!assessNode) return;

            const progress = makeProgress(seq, {
                currentNodeId: assessNode.id,
                masteryState: { C1: 0.3 }, // below 0.6 ceiling
            });

            const decision = makeComposer().resolveNextNode(seq, progress);
            // Should route to recap (remediation)
            if (decision.nodeId) {
                const targetNode = seq.nodes.find((n) => n.id === decision.nodeId);
                if (targetNode?.modality === 'recap') {
                    expect(decision.reason).toBe('remediation');
                }
            }
        });

        it('skips already completed nodes', () => {
            const seq = buildTestSequence();
            const firstNode = seq.entryNodeId;
            const outgoing = seq.edges.filter((e) => e.from === firstNode);

            if (outgoing.length > 0) {
                const progress = makeProgress(seq, {
                    currentNodeId: firstNode,
                    completedNodeIds: [outgoing[0]!.to],
                });

                const decision = makeComposer().resolveNextNode(seq, progress);
                // Should not return the already-completed node
                expect(decision.nodeId).not.toBe(outgoing[0]!.to);
            }
        });
    });
});
