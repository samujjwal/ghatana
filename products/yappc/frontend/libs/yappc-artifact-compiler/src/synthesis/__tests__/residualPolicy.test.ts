import { describe, expect, it } from 'vitest';
import { residualPolicy, ResidualClassification } from '../residualPolicy';
import type { ResidualIsland } from '../../residual/types';

describe('ResidualPolicy', () => {
  it('classifies unsafe patterns as UNSAFE', () => {
    const unsafeIsland: ResidualIsland = {
      id: 'unsafe-island',
      kind: 'code',
      sourceLocation: { filePath: 'unsafe.ts', startLine: 1, startColumn: 1, endLine: 10, endColumn: 1 },
      confidence: 0.5,
      regenerationStrategy: 'require-manual-impl',
    } as ResidualIsland;

    const result = residualPolicy.classify(unsafeIsland);
    expect(result.classification).toBe(ResidualClassification.UNSAFE);
    expect(result.suggestedAction).toBe('block');
  });

  it('classifies verbatim-safe patterns as PRESERVE_VERBATIM', () => {
    const commentIsland: ResidualIsland = {
      id: 'comment-island',
      kind: 'code',
      sourceLocation: { filePath: 'file.ts', startLine: 1, startColumn: 1, endLine: 5, endColumn: 1 },
      confidence: 1.0,
      regenerationStrategy: 'verbatim-preserve',
    } as ResidualIsland;

    const result = residualPolicy.classify(commentIsland);
    expect(result.classification).toBe(ResidualClassification.PRESERVE_VERBATIM);
    expect(result.suggestedAction).toBe('preserve');
  });

  it('classifies complex patterns as REQUIRES_REVIEW', () => {
    const complexIsland: ResidualIsland = {
      id: 'complex-island',
      kind: 'logic',
      sourceLocation: { filePath: 'complex.ts', startLine: 1, startColumn: 1, endLine: 20, endColumn: 1 },
      confidence: 0.6,
      regenerationStrategy: 'require-manual-impl',
    } as ResidualIsland;

    const result = residualPolicy.classify(complexIsland);
    expect(result.classification).toBe(ResidualClassification.REQUIRES_REVIEW);
    expect(result.suggestedAction).toBe('review');
  });

  it('classifies unknown patterns as UNKNOWN', () => {
    const unknownIsland: ResidualIsland = {
      id: 'unknown-island',
      kind: 'code',
      sourceLocation: { filePath: 'unknown.ts', startLine: 1, startColumn: 1, endLine: 10, endColumn: 1 },
      confidence: 0.5,
      regenerationStrategy: 'require-manual-impl',
    } as ResidualIsland;

    const result = residualPolicy.classify(unknownIsland);
    expect(result.classification).toBe(ResidualClassification.UNKNOWN);
    expect(result.suggestedAction).toBe('review');
  });

  it('determines regeneration strategy based on classification', () => {
    const island: ResidualIsland = {
      id: 'test-island',
      kind: 'code',
      sourceLocation: { filePath: 'test.ts', startLine: 1, startColumn: 1, endLine: 5, endColumn: 1 },
      confidence: 1.0,
      regenerationStrategy: 'verbatim-preserve',
    } as ResidualIsland;

    const strategy = residualPolicy.determineRegenerationStrategy(island);
    expect(strategy).toBe('verbatim-preserve');
  });

  it('batch classifies multiple islands', () => {
    const islands: ResidualIsland[] = [
      {
        id: 'safe-island',
        kind: 'code',
        sourceLocation: { filePath: 'safe.ts', startLine: 1, startColumn: 1, endLine: 5, endColumn: 1 },
        confidence: 1.0,
        regenerationStrategy: 'verbatim-preserve',
      } as ResidualIsland,
      {
        id: 'unsafe-island',
        kind: 'code',
        sourceLocation: { filePath: 'unsafe.ts', startLine: 1, startColumn: 1, endLine: 10, endColumn: 1 },
        confidence: 0.5,
        regenerationStrategy: 'require-manual-impl',
      } as ResidualIsland,
    ];

    const results = residualPolicy.batchClassify(islands);
    expect(results.size).toBe(2);
    expect(results.get('safe-island')?.classification).toBe(ResidualClassification.PRESERVE_VERBATIM);
    expect(results.get('unsafe-island')?.classification).toBe(ResidualClassification.UNSAFE);
  });

  it('filters islands by classification', () => {
    const islands: ResidualIsland[] = [
      {
        id: 'safe-island',
        kind: 'code',
        sourceLocation: { filePath: 'safe.ts', startLine: 1, startColumn: 1, endLine: 5, endColumn: 1 },
        confidence: 1.0,
        regenerationStrategy: 'verbatim-preserve',
      } as ResidualIsland,
      {
        id: 'review-island',
        kind: 'logic',
        sourceLocation: { filePath: 'review.ts', startLine: 1, startColumn: 1, endLine: 20, endColumn: 1 },
        confidence: 0.6,
        regenerationStrategy: 'require-manual-impl',
      } as ResidualIsland,
      {
        id: 'unsafe-island',
        kind: 'code',
        sourceLocation: { filePath: 'unsafe.ts', startLine: 1, startColumn: 1, endLine: 10, endColumn: 1 },
        confidence: 0.5,
        regenerationStrategy: 'require-manual-impl',
      } as ResidualIsland,
    ];

    const verbatimIslands = residualPolicy.filterByClassification(islands, ResidualClassification.PRESERVE_VERBATIM);
    expect(verbatimIslands).toHaveLength(1);
    expect(verbatimIslands[0]?.id).toBe('safe-island');
  });

  it('provides statistics on classifications', () => {
    const islands: ResidualIsland[] = [
      {
        id: 'safe-island',
        kind: 'code',
        sourceLocation: { filePath: 'safe.ts', startLine: 1, startColumn: 1, endLine: 5, endColumn: 1 },
        confidence: 1.0,
        regenerationStrategy: 'verbatim-preserve',
      } as ResidualIsland,
      {
        id: 'review-island',
        kind: 'logic',
        sourceLocation: { filePath: 'review.ts', startLine: 1, startColumn: 1, endLine: 20, endColumn: 1 },
        confidence: 0.6,
        regenerationStrategy: 'require-manual-impl',
      } as ResidualIsland,
      {
        id: 'unsafe-island',
        kind: 'code',
        sourceLocation: { filePath: 'unsafe.ts', startLine: 1, startColumn: 1, endLine: 10, endColumn: 1 },
        confidence: 0.5,
        regenerationStrategy: 'require-manual-impl',
      } as ResidualIsland,
    ];

    const stats = residualPolicy.getStatistics(islands);
    expect(stats.total).toBe(3);
    expect(stats.preserveVerbatim).toBe(1);
    expect(stats.requiresReview).toBe(1);
    expect(stats.unsafe).toBe(1);
  });
});
