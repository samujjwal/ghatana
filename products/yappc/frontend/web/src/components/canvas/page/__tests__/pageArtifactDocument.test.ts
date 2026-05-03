import { describe, expect, it } from 'vitest';

import {
  createAIChangeRecord,
  AIActionLineageTracker,
  createPageArtifactDocument,
} from '../pageArtifactDocument';

// ---------------------------------------------------------------------------
// AIChangeRecord
// ---------------------------------------------------------------------------

describe('createAIChangeRecord', () => {
  it('creates a record with the correct artifact and document IDs', () => {
    const record = createAIChangeRecord(
      'art-1',
      'doc-1',
      'accessibility-fix',
      'Added missing alt text',
      0.95,
      ['node-a'],
    );

    expect(record.artifactId).toBe('art-1');
    expect(record.documentId).toBe('doc-1');
    expect(record.lineage.hookKind).toBe('accessibility-fix');
    expect(record.lineage.reason).toBe('Added missing alt text');
    expect(record.lineage.confidence).toBe(0.95);
    expect(record.lineage.affectedNodeIds).toEqual(['node-a']);
    expect(record.lineage.reviewState).toBe('pending');
    expect(record.lineage.reversible).toBe(true);
  });

  it('generates a unique actionId for each record', () => {
    const r1 = createAIChangeRecord('art-1', 'doc-1', 'auto-layout-cleanup', 'Layout', 0.9, []);
    const r2 = createAIChangeRecord('art-1', 'doc-1', 'auto-layout-cleanup', 'Layout', 0.9, []);
    expect(r1.lineage.actionId).not.toBe(r2.lineage.actionId);
  });

  it('applies custom options (reversible, reviewState, evidence)', () => {
    const record = createAIChangeRecord(
      'art-2',
      'doc-2',
      'token-normalization',
      'Normalised colour token',
      0.8,
      ['node-b'],
      { reversible: false, reviewState: 'auto-accepted', evidence: ['contract:Button/color'] },
    );

    expect(record.lineage.reversible).toBe(false);
    expect(record.lineage.reviewState).toBe('auto-accepted');
    expect(record.lineage.evidence).toEqual(['contract:Button/color']);
  });
});

// ---------------------------------------------------------------------------
// AIActionLineageTracker
// ---------------------------------------------------------------------------

describe('AIActionLineageTracker', () => {
  it('records and retrieves entries', () => {
    const tracker = new AIActionLineageTracker();
    const record = createAIChangeRecord('a', 'b', 'missing-prop-repair', 'R', 0.7, ['n1']);

    tracker.record(record.lineage);

    expect(tracker.getAll()).toHaveLength(1);
    expect(tracker.getAll()[0]?.actionId).toBe(record.lineage.actionId);
  });

  it('filters pending entries correctly', () => {
    const tracker = new AIActionLineageTracker();
    const r1 = createAIChangeRecord('a', 'b', 'accessibility-fix', 'R', 0.9, []);
    const r2 = createAIChangeRecord('a', 'b', 'token-normalization', 'R2', 0.8, []);

    tracker.record(r1.lineage);
    tracker.record(r2.lineage);
    tracker.setReviewState(r1.lineage.actionId, 'accepted');

    expect(tracker.getPending()).toHaveLength(1);
    expect(tracker.getPending()[0]?.actionId).toBe(r2.lineage.actionId);
  });

  it('setReviewState returns false for unknown actionIds', () => {
    const tracker = new AIActionLineageTracker();
    expect(tracker.setReviewState('nonexistent', 'accepted')).toBe(false);
  });

  it('getByNode returns only entries affecting that node', () => {
    const tracker = new AIActionLineageTracker();
    const r1 = createAIChangeRecord('a', 'b', 'accessibility-fix', 'A', 0.9, ['node-x']);
    const r2 = createAIChangeRecord('a', 'b', 'token-normalization', 'B', 0.8, ['node-y']);

    tracker.record(r1.lineage);
    tracker.record(r2.lineage);

    const byX = tracker.getByNode('node-x');
    expect(byX).toHaveLength(1);
    expect(byX[0]?.actionId).toBe(r1.lineage.actionId);
  });

  it('clear removes all entries', () => {
    const tracker = new AIActionLineageTracker();
    tracker.record(createAIChangeRecord('a', 'b', 'accessibility-fix', 'R', 0.9, []).lineage);
    tracker.clear();
    expect(tracker.getAll()).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// createPageArtifactDocument
// ---------------------------------------------------------------------------

describe('createPageArtifactDocument', () => {
  it('creates a document with default source and sync status', () => {
    const doc = createPageArtifactDocument({ artifactId: 'art-1', name: 'Test Page', createdBy: 'dev' });
    expect(doc.source).toBe('created-in-builder');
    expect(doc.syncStatus).toBe('dirty');
    // trustLevel and dataClassification mirror the BuilderDocument metadata defaults
    expect(doc.trustLevel).toBeDefined();
    expect(doc.dataClassification).toBeDefined();
  });

  it('accepts an explicit source', () => {
    const doc = createPageArtifactDocument({
      artifactId: 'art-2',
      name: 'Decompiled Page',
      createdBy: 'system',
      source: 'decompiled',
    });
    expect(doc.source).toBe('decompiled');
  });
});
