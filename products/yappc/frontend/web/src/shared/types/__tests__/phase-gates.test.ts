/**
 * Phase Gates — Unit Tests
 *
 * Tests for the PHASE_GATES catalog, PHASE_GATES_BY_ID lookup,
 * getGateForTransition, validateGate, and validatePhaseTransition.
 *
 * @doc.type test
 * @doc.purpose Verify phase gate definitions and validation logic
 * @doc.layer product
 * @doc.pattern Type Tests
 */

import { describe, it, expect } from 'vitest';

import {
  PHASE_GATES,
  PHASE_GATES_BY_ID,
  getGateForTransition,
  validateGate,
  validatePhaseTransition,
  type GateContext,
  type ItemSummary,
  type PhaseGate,
} from '../phase-gates';
import {
  type LifecycleArtifactKind,
  getArtifactsForPhase,
} from '../lifecycle-artifacts';
import { LifecyclePhase } from '@/types/lifecycle';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Build a minimal passing context where all required artifacts are 'complete'. */
function buildPassingContext(
  fromPhase: LifecyclePhase,
  toPhase: LifecyclePhase,
  status: ItemSummary['status'] = 'complete'
): GateContext {
  const requiredKinds = getArtifactsForPhase(fromPhase);
  const lifecycleArtifactItemsByKind: Partial<
    Record<LifecycleArtifactKind, ItemSummary>
  > = {};

  for (const kind of requiredKinds) {
    lifecycleArtifactItemsByKind[kind] = {
      id: `item-${kind}`,
      title: kind,
      artifactKind: kind,
      status,
      lastUpdated: '2025-01-01T00:00:00Z',
    };
  }

  return {
    projectId: 'proj-test',
    currentPhase: fromPhase,
    targetPhase: toPhase,
    lifecycleArtifactItemsByKind,
  };
}

/** Build a context with no artifacts at all. */
function buildEmptyContext(
  fromPhase: LifecyclePhase,
  toPhase: LifecyclePhase
): GateContext {
  return {
    projectId: 'proj-test',
    currentPhase: fromPhase,
    targetPhase: toPhase,
    lifecycleArtifactItemsByKind: {},
  };
}

// ---------------------------------------------------------------------------
// PHASE_GATES catalog
// ---------------------------------------------------------------------------

describe('PHASE_GATES', () => {
  const EXPECTED_GATE_COUNT = 6;

  it('defines exactly 6 consecutive gates', () => {
    expect(PHASE_GATES).toHaveLength(EXPECTED_GATE_COUNT);
  });

  it('covers every consecutive phase transition', () => {
    const phaseOrder = [
      LifecyclePhase.INTENT,
      LifecyclePhase.SHAPE,
      LifecyclePhase.VALIDATE,
      LifecyclePhase.GENERATE,
      LifecyclePhase.RUN,
      LifecyclePhase.OBSERVE,
      LifecyclePhase.IMPROVE,
    ];

    for (let i = 0; i < phaseOrder.length - 1; i++) {
      const from = phaseOrder[i]!;
      const to = phaseOrder[i + 1]!;
      const gate = PHASE_GATES.find(
        (g) => g.fromPhase === from && g.toPhase === to
      );
      expect(gate, `Expected gate for ${from} → ${to}`).toBeDefined();
    }
  });

  it('every gate has a non-empty id, name, and description', () => {
    for (const gate of PHASE_GATES) {
      expect(gate.id).toBeTruthy();
      expect(gate.name).toBeTruthy();
      expect(gate.description).toBeTruthy();
    }
  });

  it('all gates allow bypass', () => {
    for (const gate of PHASE_GATES) {
      expect(gate.canBypass).toBe(true);
    }
  });

  it('every gate has at least one required artifact kind', () => {
    for (const gate of PHASE_GATES) {
      expect(gate.requiredArtifactKinds.length).toBeGreaterThan(0);
    }
  });

  it.each([
    [LifecyclePhase.INTENT, LifecyclePhase.SHAPE, 3] as const,
    [LifecyclePhase.SHAPE, LifecyclePhase.VALIDATE, 3] as const,
    [LifecyclePhase.VALIDATE, LifecyclePhase.GENERATE, 3] as const,
    [LifecyclePhase.GENERATE, LifecyclePhase.RUN, 2] as const,
    [LifecyclePhase.RUN, LifecyclePhase.OBSERVE, 2] as const,
    [LifecyclePhase.OBSERVE, LifecyclePhase.IMPROVE, 2] as const,
  ])(
    'gate %s→%s requires %i artifacts from the catalog',
    (from, to, expectedCount) => {
      const gate = PHASE_GATES.find(
        (g) => g.fromPhase === from && g.toPhase === to
      );
      expect(gate).toBeDefined();
      expect(gate!.requiredArtifactKinds).toHaveLength(expectedCount);
    }
  );

  it('gate artifact kinds match getArtifactsForPhase for the from-phase', () => {
    for (const gate of PHASE_GATES) {
      const catalogArtifacts = getArtifactsForPhase(gate.fromPhase);
      expect(gate.requiredArtifactKinds).toEqual(catalogArtifacts);
    }
  });

  it('all gate IDs are unique', () => {
    const ids = PHASE_GATES.map((g) => g.id);
    expect(new Set(ids).size).toBe(ids.length);
  });
});

// ---------------------------------------------------------------------------
// PHASE_GATES_BY_ID
// ---------------------------------------------------------------------------

describe('PHASE_GATES_BY_ID', () => {
  it('contains an entry for every gate', () => {
    for (const gate of PHASE_GATES) {
      expect(PHASE_GATES_BY_ID[gate.id]).toBeDefined();
    }
  });

  it('maps each ID back to the correct gate', () => {
    for (const gate of PHASE_GATES) {
      expect(PHASE_GATES_BY_ID[gate.id]).toBe(gate);
    }
  });

  it('returns undefined for unknown IDs', () => {
    expect(PHASE_GATES_BY_ID['gate:nonexistent']).toBeUndefined();
  });
});

// ---------------------------------------------------------------------------
// getGateForTransition
// ---------------------------------------------------------------------------

describe('getGateForTransition', () => {
  it('returns the correct gate for every consecutive pair', () => {
    for (const gate of PHASE_GATES) {
      const found = getGateForTransition(gate.fromPhase, gate.toPhase);
      expect(found).toBe(gate);
    }
  });

  it('returns undefined for the same phase', () => {
    expect(
      getGateForTransition(LifecyclePhase.INTENT, LifecyclePhase.INTENT)
    ).toBeUndefined();
  });

  it('returns undefined for non-adjacent phases (INTENT → VALIDATE)', () => {
    expect(
      getGateForTransition(LifecyclePhase.INTENT, LifecyclePhase.VALIDATE)
    ).toBeUndefined();
  });

  it('returns undefined for reversed direction (SHAPE → INTENT)', () => {
    expect(
      getGateForTransition(LifecyclePhase.SHAPE, LifecyclePhase.INTENT)
    ).toBeUndefined();
  });
});

// ---------------------------------------------------------------------------
// validateGate
// ---------------------------------------------------------------------------

describe('validateGate', () => {
  const intentToShapeGate: PhaseGate = PHASE_GATES.find(
    (g) =>
      g.fromPhase === LifecyclePhase.INTENT &&
      g.toPhase === LifecyclePhase.SHAPE
  )!;

  it('returns status "passed" when all required artifacts are complete', () => {
    const context = buildPassingContext(
      LifecyclePhase.INTENT,
      LifecyclePhase.SHAPE,
      'complete'
    );
    const result = validateGate(intentToShapeGate, context);

    expect(result.status).toBe('passed');
    expect(result.gateId).toBe(intentToShapeGate.id);
    expect(result.blockedReason).toBeUndefined();
    expect(result.validationResults.every((r) => r.valid)).toBe(true);
  });

  it('returns status "passed" when all required artifacts are validated', () => {
    const context = buildPassingContext(
      LifecyclePhase.INTENT,
      LifecyclePhase.SHAPE,
      'validated'
    );
    const result = validateGate(intentToShapeGate, context);

    expect(result.status).toBe('passed');
  });

  it('returns status "blocked" when an artifact is in draft', () => {
    const context = buildPassingContext(
      LifecyclePhase.INTENT,
      LifecyclePhase.SHAPE,
      'complete'
    );
    // Override the first artifact to "draft"
    const firstKind = intentToShapeGate.requiredArtifactKinds[0]!;
    context.lifecycleArtifactItemsByKind[firstKind] = {
      id: 'draft-item',
      title: firstKind,
      artifactKind: firstKind,
      status: 'draft',
      lastUpdated: '2025-01-01T00:00:00Z',
    };

    const result = validateGate(intentToShapeGate, context);

    expect(result.status).toBe('blocked');
    expect(result.blockedReason).toBeTruthy();
    expect(result.validationResults.some((r) => !r.valid)).toBe(true);
  });

  it('returns status "blocked" when an artifact is missing', () => {
    const context = buildEmptyContext(
      LifecyclePhase.INTENT,
      LifecyclePhase.SHAPE
    );
    const result = validateGate(intentToShapeGate, context);

    expect(result.status).toBe('blocked');
    expect(result.blockedReason).toBeTruthy();
  });

  it('produces one ValidationResult per required artifact', () => {
    const context = buildPassingContext(
      LifecyclePhase.INTENT,
      LifecyclePhase.SHAPE,
      'complete'
    );
    const result = validateGate(intentToShapeGate, context);

    expect(result.validationResults).toHaveLength(
      intentToShapeGate.requiredArtifactKinds.length
    );
  });

  it('validation result ruleId includes artifact kind', () => {
    const context = buildPassingContext(
      LifecyclePhase.INTENT,
      LifecyclePhase.SHAPE,
      'complete'
    );
    const result = validateGate(intentToShapeGate, context);

    for (const vr of result.validationResults) {
      expect(vr.ruleId).toMatch(/^artifact:/);
    }
  });

  it('passes for each of the 6 distinct gates when all artifacts are complete', () => {
    for (const gate of PHASE_GATES) {
      const context = buildPassingContext(
        gate.fromPhase,
        gate.toPhase,
        'complete'
      );
      const result = validateGate(gate, context);
      expect(result.status).toBe('passed');
    }
  });

  it('blocks for each of the 6 gates when no artifacts exist', () => {
    for (const gate of PHASE_GATES) {
      const context = buildEmptyContext(gate.fromPhase, gate.toPhase);
      const result = validateGate(gate, context);
      expect(result.status).toBe('blocked');
    }
  });

  it('partial completion — one missing artifact causes blocked', () => {
    const gate = PHASE_GATES.find(
      (g) =>
        g.fromPhase === LifecyclePhase.VALIDATE &&
        g.toPhase === LifecyclePhase.GENERATE
    )!;
    // Fill all but the last artifact
    const context = buildPassingContext(
      LifecyclePhase.VALIDATE,
      LifecyclePhase.GENERATE,
      'complete'
    );
    const lastKind =
      gate.requiredArtifactKinds[gate.requiredArtifactKinds.length - 1]!;
    // eslint-disable-next-line @typescript-eslint/no-dynamic-delete
    delete context.lifecycleArtifactItemsByKind[lastKind];

    const result = validateGate(gate, context);
    expect(result.status).toBe('blocked');
  });
});

// ---------------------------------------------------------------------------
// validatePhaseTransition
// ---------------------------------------------------------------------------

describe('validatePhaseTransition', () => {
  it('allows same-phase "transition" (no-op)', () => {
    const context: GateContext = {
      projectId: 'p1',
      currentPhase: LifecyclePhase.INTENT,
      targetPhase: LifecyclePhase.INTENT,
      lifecycleArtifactItemsByKind: {},
    };
    const result = validatePhaseTransition(context);

    expect(result.canTransition).toBe(true);
    expect(result.gate).toBeUndefined();
    expect(result.gateStatus).toBeUndefined();
  });

  it('blocks when no gate exists between non-adjacent phases (INTENT → VALIDATE)', () => {
    const context: GateContext = {
      projectId: 'p1',
      currentPhase: LifecyclePhase.INTENT,
      targetPhase: LifecyclePhase.VALIDATE,
      lifecycleArtifactItemsByKind: {},
    };
    const result = validatePhaseTransition(context);

    expect(result.canTransition).toBe(false);
    expect(result.gateStatus?.status).toBe('blocked');
    expect(result.gate).toBeUndefined();
  });

  it('returns canTransition:true when all artifacts are complete', () => {
    const context = buildPassingContext(
      LifecyclePhase.INTENT,
      LifecyclePhase.SHAPE,
      'complete'
    );
    const result = validatePhaseTransition(context);

    expect(result.canTransition).toBe(true);
    expect(result.gate).toBeDefined();
    expect(result.gateStatus?.status).toBe('passed');
  });

  it('returns canTransition:false when artifacts are missing', () => {
    const context = buildEmptyContext(
      LifecyclePhase.SHAPE,
      LifecyclePhase.VALIDATE
    );
    const result = validatePhaseTransition(context);

    expect(result.canTransition).toBe(false);
    expect(result.gate).toBeDefined();
    expect(result.gateStatus?.status).toBe('blocked');
  });

  it('populates gate.canBypass on a blocked result', () => {
    const context = buildEmptyContext(
      LifecyclePhase.RUN,
      LifecyclePhase.OBSERVE
    );
    const result = validatePhaseTransition(context);

    expect(result.gate?.canBypass).toBe(true);
  });

  it('gate identity matches getGateForTransition', () => {
    const context = buildPassingContext(
      LifecyclePhase.GENERATE,
      LifecyclePhase.RUN,
      'validated'
    );
    const result = validatePhaseTransition(context);

    const expected = getGateForTransition(
      LifecyclePhase.GENERATE,
      LifecyclePhase.RUN
    );
    expect(result.gate).toBe(expected);
  });

  it('returns canTransition:false when any artifact is in draft', () => {
    const context = buildPassingContext(
      LifecyclePhase.OBSERVE,
      LifecyclePhase.IMPROVE,
      'draft'
    );
    const result = validatePhaseTransition(context);

    expect(result.canTransition).toBe(false);
  });
});
