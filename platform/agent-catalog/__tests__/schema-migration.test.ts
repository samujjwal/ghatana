/**
 * Unit tests for schema-migration.ts canonicalizeLearningBlock function.
 *
 * Verifies that the canonicalization and validation rules for agent learning
 * blocks are correctly enforced.
 */
import { describe, it, expect } from 'vitest';
import { canonicalizeLearningBlock } from '../schema-migration.js';
import type { LearningBlock } from '../schema-migration.js';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function runCheck(
  learning: LearningBlock,
): { errors: string[]; changed: boolean } {
  const errors: string[] = [];
  const changed = canonicalizeLearningBlock(learning, false, errors, 'agent:test');
  return { errors, changed };
}

function runFix(
  learning: LearningBlock,
): { errors: string[]; changed: boolean; block: LearningBlock } {
  const errors: string[] = [];
  const block: LearningBlock = { ...learning };
  const changed = canonicalizeLearningBlock(block, true, errors, 'agent:test');
  return { errors, changed, block };
}

// ---------------------------------------------------------------------------
// Learning level normalization
// ---------------------------------------------------------------------------

describe('canonicalizeLearningBlock — level normalization', () => {
  it('normalizes lowercase l3 to L3 in fix mode', () => {
    const { changed, block, errors } = runFix({ learningLevel: 'l3', promotionRequired: true, provenanceRequired: true, evaluationRefs: ['eval/v1.yaml'] });

    expect(changed).toBe(true);
    expect(block.learningLevel).toBe('L3');
    expect(errors).toHaveLength(0);
  });

  it('reports an error for non-canonical casing in check mode', () => {
    const { changed, errors } = runCheck({
      learningLevel: 'l2',
      provenanceRequired: true,
    });

    expect(changed).toBe(false);
    expect(errors).toEqual(
      expect.arrayContaining([
        expect.stringContaining("must be uppercase canonical form 'L2'"),
      ]),
    );
  });

  it('passes without mutation when level is already canonical', () => {
    const { changed, errors } = runCheck({
      learningLevel: 'L1',
      provenanceRequired: true,
    });

    expect(changed).toBe(false);
    expect(errors).toHaveLength(0);
  });

  it('reports an error for an entirely invalid level', () => {
    const { errors } = runCheck({ learningLevel: 'L9' });

    expect(errors).toEqual(
      expect.arrayContaining([
        expect.stringContaining("is not a valid learning level"),
      ]),
    );
  });

  it('returns false without error when learningLevel is absent', () => {
    const { changed, errors } = runCheck({});

    expect(changed).toBe(false);
    expect(errors).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// L2+ provenanceRequired invariant
// ---------------------------------------------------------------------------

describe('canonicalizeLearningBlock — provenanceRequired', () => {
  it('fails when L2 agent does not set provenanceRequired', () => {
    const { errors } = runCheck({
      learningLevel: 'L2',
      provenanceRequired: false,
    });

    expect(errors).toEqual(
      expect.arrayContaining([
        expect.stringContaining('L2+ agents must set provenanceRequired: true'),
      ]),
    );
  });

  it('fails when L3 agent does not set provenanceRequired', () => {
    const { errors } = runCheck({
      learningLevel: 'L3',
      provenanceRequired: false,
      promotionRequired: true,
      evaluationRefs: ['eval/v1.yaml'],
    });

    expect(errors).toEqual(
      expect.arrayContaining([
        expect.stringContaining('L2+ agents must set provenanceRequired: true'),
      ]),
    );
  });

  it('passes when L2 agent sets provenanceRequired: true', () => {
    const { errors } = runCheck({
      learningLevel: 'L2',
      provenanceRequired: true,
    });

    // Only provenanceRequired failure absent; no other L2 constraint
    expect(errors.filter(e => e.includes('provenanceRequired'))).toHaveLength(0);
  });

  it('does not require provenanceRequired at L1', () => {
    const { errors } = runCheck({
      learningLevel: 'L1',
    });

    expect(errors.filter(e => e.includes('provenanceRequired'))).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// L3+ promotionRequired invariant
// ---------------------------------------------------------------------------

describe('canonicalizeLearningBlock — promotionRequired', () => {
  it('fails when L3 agent does not set promotionRequired', () => {
    const { errors } = runCheck({
      learningLevel: 'L3',
      provenanceRequired: true,
      promotionRequired: false,
      evaluationRefs: ['eval/v1.yaml'],
    });

    expect(errors).toEqual(
      expect.arrayContaining([
        expect.stringContaining('L3+ agents must set promotionRequired: true'),
      ]),
    );
  });

  it('fails when L5 agent does not set promotionRequired', () => {
    const { errors } = runCheck({
      learningLevel: 'L5',
      provenanceRequired: true,
      promotionRequired: false,
      evaluationRefs: ['eval/v1.yaml'],
    });

    expect(errors).toEqual(
      expect.arrayContaining([
        expect.stringContaining('L3+ agents must set promotionRequired: true'),
      ]),
    );
  });

  it('passes when L3 agent sets both provenanceRequired and promotionRequired', () => {
    const { errors } = runCheck({
      learningLevel: 'L3',
      provenanceRequired: true,
      promotionRequired: true,
      evaluationRefs: ['eval/v1.yaml'],
    });

    expect(errors).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// L3+ evaluationRefs invariant
// ---------------------------------------------------------------------------

describe('canonicalizeLearningBlock — evaluationRefs', () => {
  it('fails when L3 agent has no evaluationRefs', () => {
    const { errors } = runCheck({
      learningLevel: 'L3',
      provenanceRequired: true,
      promotionRequired: true,
    });

    expect(errors).toEqual(
      expect.arrayContaining([
        expect.stringContaining('L3+ agents must provide at least one evaluationRefs entry'),
      ]),
    );
  });

  it('fails when L3 agent has an empty evaluationRefs array', () => {
    const { errors } = runCheck({
      learningLevel: 'L3',
      provenanceRequired: true,
      promotionRequired: true,
      evaluationRefs: [],
    });

    expect(errors).toEqual(
      expect.arrayContaining([
        expect.stringContaining('at least one evaluationRefs entry'),
      ]),
    );
  });

  it('passes when L3 agent has at least one evaluationRefs entry', () => {
    const { errors } = runCheck({
      learningLevel: 'L3',
      provenanceRequired: true,
      promotionRequired: true,
      evaluationRefs: ['evaluation-packs/skill-v1.yaml'],
    });

    expect(errors).toHaveLength(0);
  });

  it('does not require evaluationRefs at L2', () => {
    const { errors } = runCheck({
      learningLevel: 'L2',
      provenanceRequired: true,
    });

    expect(errors.filter(e => e.includes('evaluationRefs'))).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// adaptationTargets — valid target enforcement
// ---------------------------------------------------------------------------

describe('canonicalizeLearningBlock — adaptationTargets', () => {
  it('fails when an adaptation target is not a valid LearningTarget enum name', () => {
    const { errors } = runCheck({
      learningLevel: 'L2',
      provenanceRequired: true,
      adaptationTargets: ['INVALID_TARGET'],
    });

    expect(errors).toEqual(
      expect.arrayContaining([
        expect.stringContaining("'INVALID_TARGET' is not a valid LearningTarget"),
      ]),
    );
  });

  it('passes with all valid adaptation targets', () => {
    const { errors } = runCheck({
      learningLevel: 'L2',
      provenanceRequired: true,
      adaptationTargets: ['EPISODIC_MEMORY', 'SEMANTIC_FACT', 'RETRIEVAL_POLICY'],
    });

    expect(errors.filter(e => e.includes('adaptationTargets'))).toHaveLength(0);
  });

  it('fails when adaptationTargets is not an array', () => {
    const { errors } = runCheck({
      learningLevel: 'L2',
      provenanceRequired: true,
      adaptationTargets: 'EPISODIC_MEMORY' as unknown as string[],
    });

    expect(errors).toEqual(
      expect.arrayContaining([
        expect.stringContaining('must be an array'),
      ]),
    );
  });
});

// ---------------------------------------------------------------------------
// masteryBindings — required field enforcement
// ---------------------------------------------------------------------------

describe('canonicalizeLearningBlock — masteryBindings', () => {
  it('fails when single masteryBinding map is missing namespace', () => {
    const { errors } = runCheck({
      learningLevel: 'L2',
      provenanceRequired: true,
      masteryBindings: {
        registryRef: 'mastery-policies/skill-v1.yaml',
        // namespace intentionally absent
      } as unknown as LearningBlock['masteryBindings'],
    });

    expect(errors).toEqual(
      expect.arrayContaining([
        expect.stringContaining("must include 'namespace'"),
      ]),
    );
  });

  it('fails when single masteryBinding map is missing registryRef', () => {
    const { errors } = runCheck({
      learningLevel: 'L2',
      provenanceRequired: true,
      masteryBindings: {
        namespace: 'com.ghatana.agents',
        // registryRef intentionally absent
      } as unknown as LearningBlock['masteryBindings'],
    });

    expect(errors).toEqual(
      expect.arrayContaining([
        expect.stringContaining("must include 'registryRef'"),
      ]),
    );
  });

  it('passes when single masteryBinding has both namespace and registryRef', () => {
    const { errors } = runCheck({
      learningLevel: 'L2',
      provenanceRequired: true,
      masteryBindings: {
        namespace: 'com.ghatana.agents',
        registryRef: 'mastery-policies/skill-v1.yaml',
      } as unknown as LearningBlock['masteryBindings'],
    });

    expect(errors.filter(e => e.includes('masteryBindings'))).toHaveLength(0);
  });

  it('fails when an array masteryBinding entry is missing namespace', () => {
    const { errors } = runCheck({
      learningLevel: 'L2',
      provenanceRequired: true,
      masteryBindings: [
        { registryRef: 'mastery-policies/skill-v1.yaml' }, // missing namespace
      ] as unknown as LearningBlock['masteryBindings'],
    });

    expect(errors).toEqual(
      expect.arrayContaining([
        expect.stringContaining("must include 'namespace'"),
      ]),
    );
  });

  it('passes when all array masteryBinding entries have namespace and registryRef', () => {
    const { errors } = runCheck({
      learningLevel: 'L2',
      provenanceRequired: true,
      masteryBindings: [
        { namespace: 'com.ghatana.agents', registryRef: 'mastery-policies/skill-v1.yaml' },
        { namespace: 'com.ghatana.agents', registryRef: 'mastery-policies/policy-v2.yaml' },
      ] as unknown as LearningBlock['masteryBindings'],
    });

    expect(errors.filter(e => e.includes('masteryBindings'))).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// MASTERY_STATE target governance
// ---------------------------------------------------------------------------

describe('canonicalizeLearningBlock — MASTERY_STATE target governance', () => {
  it('fails when MASTERY_STATE is used at a level below L5', () => {
    const { errors } = runCheck({
      learningLevel: 'L4',
      provenanceRequired: true,
      promotionRequired: true,
      evaluationRefs: ['eval/v1.yaml'],
      adaptationTargets: ['MASTERY_STATE'],
    });

    expect(errors).toEqual(
      expect.arrayContaining([
        expect.stringContaining('MASTERY_STATE target is only permitted at learningLevel L5'),
      ]),
    );
  });

  it('fails when MASTERY_STATE is used at L5 without governanceWorkflow: true', () => {
    const { errors } = runCheck({
      learningLevel: 'L5',
      provenanceRequired: true,
      promotionRequired: true,
      evaluationRefs: ['eval/v1.yaml'],
      adaptationTargets: ['MASTERY_STATE'],
      governanceWorkflow: false,
    });

    expect(errors).toEqual(
      expect.arrayContaining([
        expect.stringContaining('requires governanceWorkflow: true'),
      ]),
    );
  });

  it('passes when MASTERY_STATE is used at L5 with governanceWorkflow: true', () => {
    const { errors } = runCheck({
      learningLevel: 'L5',
      provenanceRequired: true,
      promotionRequired: true,
      evaluationRefs: ['eval/v1.yaml'],
      adaptationTargets: ['MASTERY_STATE'],
      governanceWorkflow: true,
    });

    expect(errors).toHaveLength(0);
  });
});
