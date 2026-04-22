import { describe, it, expect, vi } from 'vitest';
import {
  AIActionLineageTracker,
  createLineageEntry,
} from '../../ai/lineage.js';
import type { NodeId } from '../../core/types.js';

const nodeA = 'node-a' as NodeId;
const nodeB = 'node-b' as NodeId;

describe('createLineageEntry', () => {
  it('creates an entry with required fields', () => {
    const entry = createLineageEntry('missing-prop-repair', 'Fixed missing label', 0.9, [nodeA]);
    expect(entry.hookKind).toBe('missing-prop-repair');
    expect(entry.reason).toBe('Fixed missing label');
    expect(entry.confidence).toBe(0.9);
    expect(entry.affectedNodeIds).toContain(nodeA);
    expect(entry.reversible).toBe(true); // default
    expect(entry.reviewState).toBe('pending'); // default
    expect(entry.actionId).toMatch(/^ai-/);
    expect(entry.appliedAt).toBeTruthy();
  });

  it('accepts reversible and reviewState overrides', () => {
    const entry = createLineageEntry('accessibility-fix', 'Added alt text', 0.85, [nodeB], {
      reversible: false,
      reviewState: 'auto-accepted',
    });
    expect(entry.reversible).toBe(false);
    expect(entry.reviewState).toBe('auto-accepted');
  });

  it('accepts correlationId and evidence', () => {
    const entry = createLineageEntry('token-normalization', 'Replaced raw hex', 0.7, [nodeA], {
      correlationId: 'corr-xyz',
      evidence: ['Design token: --color-primary'],
    });
    expect(entry.correlationId).toBe('corr-xyz');
    expect(entry.evidence).toContain('Design token: --color-primary');
  });

  it('produces unique actionIds on consecutive calls', () => {
    const a = createLineageEntry('auto-layout-cleanup', 'Normalized gaps', 0.6, []);
    const b = createLineageEntry('auto-layout-cleanup', 'Normalized gaps', 0.6, []);
    expect(a.actionId).not.toBe(b.actionId);
  });
});

describe('AIActionLineageTracker', () => {
  it('records and retrieves entries', () => {
    const tracker = new AIActionLineageTracker();
    const entry = createLineageEntry('property-completion', 'Completed size prop', 0.8, [nodeA]);
    tracker.record(entry);
    expect(tracker.getAll()).toHaveLength(1);
    expect(tracker.getAll()[0]).toBe(entry);
  });

  it('getByNode returns only entries affecting that node', () => {
    const tracker = new AIActionLineageTracker();
    const e1 = createLineageEntry('accessibility-fix', 'Alt text for A', 0.9, [nodeA]);
    const e2 = createLineageEntry('accessibility-fix', 'Alt text for B', 0.9, [nodeB]);
    tracker.record(e1);
    tracker.record(e2);
    const forA = tracker.getByNode(nodeA);
    expect(forA).toHaveLength(1);
    expect(forA[0]).toBe(e1);
  });

  it('setReviewState transitions the state and returns true', () => {
    const tracker = new AIActionLineageTracker();
    const entry = createLineageEntry('action-wiring', 'Wired onClick', 0.7, [nodeA]);
    tracker.record(entry);
    const ok = tracker.setReviewState(entry.actionId, 'accepted');
    expect(ok).toBe(true);
    expect(tracker.getAll()[0]?.reviewState).toBe('accepted');
  });

  it('setReviewState returns false for unknown actionId', () => {
    const tracker = new AIActionLineageTracker();
    const ok = tracker.setReviewState('nonexistent', 'accepted');
    expect(ok).toBe(false);
  });

  it('getPending returns only pending entries', () => {
    const tracker = new AIActionLineageTracker();
    const e1 = createLineageEntry('missing-prop-repair', 'Repair A', 0.9, [nodeA]);
    const e2 = createLineageEntry('missing-prop-repair', 'Repair B', 0.9, [nodeB]);
    tracker.record(e1);
    tracker.record(e2);
    tracker.setReviewState(e1.actionId, 'accepted');
    const pending = tracker.getPending();
    expect(pending).toHaveLength(1);
    expect(pending[0]).toBe(e2);
  });

  it('getReversible returns only reversible entries', () => {
    const tracker = new AIActionLineageTracker();
    const e1 = createLineageEntry('accessibility-fix', 'Reversible', 0.9, [nodeA], { reversible: true });
    const e2 = createLineageEntry('accessibility-fix', 'Not reversible', 0.9, [nodeB], { reversible: false });
    tracker.record(e1);
    tracker.record(e2);
    const reversible = tracker.getReversible();
    expect(reversible).toHaveLength(1);
    expect(reversible[0]).toBe(e1);
  });

  it('clear removes all entries', () => {
    const tracker = new AIActionLineageTracker();
    tracker.record(createLineageEntry('token-normalization', 'Fix', 0.5, [nodeA]));
    tracker.clear();
    expect(tracker.getAll()).toHaveLength(0);
  });
});
