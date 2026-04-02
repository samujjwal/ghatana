import { describe, it, expect, beforeEach } from 'vitest';
import { ConflictResolutionEngine } from '../crdt/conflict-resolution/index.js';
import type {
  Conflict,
  ConflictResolutionEngineConfig,
} from '../crdt/conflict-resolution/types.js';
import type { CRDTOperation, VectorClock } from '../crdt/core/types.js';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeVectorClock(replicaId: string, value = 1): VectorClock {
  return {
    id: `vc-${replicaId}`,
    values: new Map([[replicaId, value]]),
    timestamp: Date.now(),
  };
}

function makeOp(
  params: Partial<CRDTOperation> & { targetId: string; type: CRDTOperation['type'] }
): CRDTOperation {
  return {
    id: `op-${Math.random()}`,
    replicaId: 'replica-a',
    vectorClock: makeVectorClock('replica-a'),
    data: { text: 'hello' },
    timestamp: Date.now(),
    parents: [],
    ...params,
  };
}

function makeConflict(
  opAOverrides: Partial<CRDTOperation> = {},
  opBOverrides: Partial<CRDTOperation> = {},
  type: Conflict['type'] = 'concurrent-update'
): Conflict {
  const opA = makeOp({ targetId: 'target-1', type: 'update', ...opAOverrides });
  const opB = makeOp({
    targetId: 'target-1',
    type: 'update',
    replicaId: 'replica-b',
    vectorClock: makeVectorClock('replica-b'),
    ...opBOverrides,
  });

  return {
    id: `conflict-${Math.random()}`,
    type,
    targetId: 'target-1',
    operationA: opA,
    operationB: opB,
    severity: 'medium',
    timestamp: Date.now(),
  };
}

const BASE_CONFIG: ConflictResolutionEngineConfig = {
  id: 'engine-1',
  defaultStrategy: 'last-write-wins',
  strategies: [{ strategy: 'last-write-wins', priority: 1 }],
  detectorConfig: {
    detectConcurrentUpdates: true,
    detectConcurrentDeletes: true,
    detectMoveConflicts: true,
    detectOrderingConflicts: false,
    autoResolveSeverityThreshold: 'medium',
  },
  enableAutoResolution: true,
  maxResolutionAttempts: 3,
  keepHistory: true,
  maxHistorySize: 100,
};

// ---------------------------------------------------------------------------
// ConflictResolutionEngine — construction
// ---------------------------------------------------------------------------

describe('ConflictResolutionEngine construction', () => {
  it('instantiates without throwing', () => {
    expect(() => new ConflictResolutionEngine(BASE_CONFIG)).not.toThrow();
  });
});

// ---------------------------------------------------------------------------
// detectConflicts()
// ---------------------------------------------------------------------------

describe('ConflictResolutionEngine.detectConflicts()', () => {
  let engine: ConflictResolutionEngine;

  beforeEach(() => {
    engine = new ConflictResolutionEngine(BASE_CONFIG);
  });

  it('detects a concurrent-update conflict on same target', () => {
    const opA = makeOp({ targetId: 'doc-1', type: 'update' });
    const opB = makeOp({
      targetId: 'doc-1',
      type: 'update',
      replicaId: 'replica-b',
      vectorClock: makeVectorClock('replica-b'),
    });

    const conflicts = engine.detectConflicts(opA, opB);
    expect(conflicts.length).toBeGreaterThan(0);
    expect(conflicts[0]!.type).toBe('concurrent-update');
  });

  it('returns empty array when operations target different resources', () => {
    const opA = makeOp({ targetId: 'doc-1', type: 'update' });
    const opB = makeOp({ targetId: 'doc-2', type: 'update', replicaId: 'b' });

    const conflicts = engine.detectConflicts(opA, opB);
    expect(conflicts).toHaveLength(0);
  });

  it('detects concurrent-delete conflict', () => {
    const opA = makeOp({ targetId: 'x', type: 'delete' });
    const opB = makeOp({
      targetId: 'x',
      type: 'delete',
      replicaId: 'replica-b',
      vectorClock: makeVectorClock('replica-b'),
    });

    const conflicts = engine.detectConflicts(opA, opB);
    expect(conflicts.some(c => c.type === 'concurrent-delete')).toBe(true);
  });

  it('does not detect concurrent-delete when config disables it', () => {
    const cfg: ConflictResolutionEngineConfig = {
      ...BASE_CONFIG,
      detectorConfig: { ...BASE_CONFIG.detectorConfig, detectConcurrentDeletes: false },
    };
    const eng = new ConflictResolutionEngine(cfg);

    const opA = makeOp({ targetId: 'x', type: 'delete' });
    const opB = makeOp({ targetId: 'x', type: 'delete', replicaId: 'b', vectorClock: makeVectorClock('b') });

    const conflicts = eng.detectConflicts(opA, opB);
    expect(conflicts.some(c => c.type === 'concurrent-delete')).toBe(false);
  });

  it('detects update-delete conflict (update vs delete)', () => {
    const opA = makeOp({ targetId: 'x', type: 'update' });
    const opB = makeOp({ targetId: 'x', type: 'delete', replicaId: 'b', vectorClock: makeVectorClock('b') });

    const conflicts = engine.detectConflicts(opA, opB);
    expect(conflicts.length).toBeGreaterThan(0);
  });

  it('detects move conflict when detectMoveConflicts is enabled', () => {
    const opA = makeOp({ targetId: 'x', type: 'move' });
    const opB = makeOp({ targetId: 'x', type: 'move', replicaId: 'b', vectorClock: makeVectorClock('b') });

    const conflicts = engine.detectConflicts(opA, opB);
    expect(conflicts.some(c => c.type === 'move-conflict')).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// analyzeConflict()
// ---------------------------------------------------------------------------

describe('ConflictResolutionEngine.analyzeConflict()', () => {
  let engine: ConflictResolutionEngine;

  beforeEach(() => {
    engine = new ConflictResolutionEngine(BASE_CONFIG);
  });

  it('returns analysis with a description and suggestions', () => {
    const conflict = makeConflict();
    const analysis = engine.analyzeConflict(conflict);

    expect(analysis.id).toBeTruthy();
    expect(analysis.description).toBeTruthy();
    expect(analysis.suggestedResolutions.length).toBeGreaterThan(0);
  });

  it('includes last-write-wins suggestion with confidence > 0.8 (enables canAutoResolve)', () => {
    const conflict = makeConflict();
    const analysis = engine.analyzeConflict(conflict);

    const lwwSuggestion = analysis.suggestedResolutions.find(s => s.strategy === 'last-write-wins');
    expect(lwwSuggestion).toBeTruthy();
    expect(lwwSuggestion!.confidence).toBeGreaterThan(0.8);
    expect(analysis.canAutoResolve).toBe(true);
  });

  it('includes first-write-wins suggestion', () => {
    const conflict = makeConflict();
    const analysis = engine.analyzeConflict(conflict);

    expect(analysis.suggestedResolutions.some(s => s.strategy === 'first-write-wins')).toBe(true);
  });

  it('includes merge suggestion when both operations have object data', () => {
    const conflict = makeConflict(
      { data: { text: 'from A' } },
      { data: { text: 'from B' } }
    );
    const analysis = engine.analyzeConflict(conflict);

    expect(analysis.suggestedResolutions.some(s => s.strategy === 'merge')).toBe(true);
  });

  it('does NOT include merge suggestion when data is not an object', () => {
    const conflict = makeConflict({ data: 'plain string A' }, { data: 'plain string B' });
    const analysis = engine.analyzeConflict(conflict);

    expect(analysis.suggestedResolutions.some(s => s.strategy === 'merge')).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// resolveConflict()
// ---------------------------------------------------------------------------

describe('ConflictResolutionEngine.resolveConflict()', () => {
  let engine: ConflictResolutionEngine;

  beforeEach(() => {
    engine = new ConflictResolutionEngine(BASE_CONFIG);
  });

  it('resolves with last-write-wins — returns the newer operation data', () => {
    const earlier = Date.now() - 5000;
    const later = Date.now();

    const conflict = makeConflict(
      { data: 'version A', timestamp: earlier },
      { data: 'version B', timestamp: later }
    );

    const result = engine.resolveConflict(conflict, 'last-write-wins');

    expect(result.resolved).toBe(true);
    expect(result.resolvedValue).toBe('version B');
  });

  it('resolves with first-write-wins — returns the earlier operation data', () => {
    const earlier = Date.now() - 5000;
    const later = Date.now();

    const conflict = makeConflict(
      { data: 'version A', timestamp: earlier },
      { data: 'version B', timestamp: later }
    );

    const result = engine.resolveConflict(conflict, 'first-write-wins');

    expect(result.resolved).toBe(true);
    expect(result.resolvedValue).toBe('version A');
  });

  it('resolves with merge strategy — merges two objects', () => {
    const conflict = makeConflict(
      { data: { name: 'Alice', age: 30 } },
      { data: { email: 'alice@example.com', age: 31 } }
    );

    const result = engine.resolveConflict(conflict, 'merge');

    expect(result.resolved).toBe(true);
    const merged = result.resolvedValue as Record<string, unknown>;
    expect(merged['name']).toBe('Alice');
    expect(merged['email']).toBe('alice@example.com');
    // last key wins on overlap per spread order
    expect(merged['age']).toBe(31);
  });

  it('resolves with custom strategy and custom resolver', () => {
    const conflict = makeConflict(
      { data: 'from A' },
      { data: 'from B' }
    );

    const result = engine.resolveConflict(conflict, 'custom', () => 'custom-resolved');

    expect(result.resolved).toBe(true);
    expect(result.resolvedValue).toBe('custom-resolved');
  });

  it('fails when custom strategy is used without a resolver', () => {
    const conflict = makeConflict();
    const result = engine.resolveConflict(conflict, 'custom');
    expect(result.resolved).toBe(false);
    expect(result.error).toContain('Custom resolver required');
  });

  it('returns a result with posititive duration', () => {
    const conflict = makeConflict();
    const result = engine.resolveConflict(conflict, 'last-write-wins');
    expect(result.duration).toBeGreaterThanOrEqual(0);
  });

  it('returns conflictId matching the original conflict', () => {
    const conflict = makeConflict();
    const result = engine.resolveConflict(conflict, 'last-write-wins');
    expect(result.conflictId).toBe(conflict.id);
  });
});
