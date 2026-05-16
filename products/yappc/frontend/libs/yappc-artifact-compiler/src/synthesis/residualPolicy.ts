/**
 * @fileoverview Residual Policy — determines how to handle code that cannot be safely compiled.
 *
 * This module provides policies for classifying and managing residual islands
 * (code patterns that cannot be safely extracted or synthesized back to code).
 */

import type { ResidualIsland } from '../residual/types';

// ============================================================================
// Residual Classification
// ============================================================================

export enum ResidualClassification {
  /** Safe to preserve verbatim (comments, formatting, whitespace) */
  PRESERVE_VERBATIM = 'preserve-verbatim',
  /** Contains logic that should be manually reviewed before compile-back */
  REQUIRES_REVIEW = 'requires-review',
  /** Contains unsafe patterns that must be blocked */
  UNSAFE = 'unsafe',
  /** Unknown pattern - default to review */
  UNKNOWN = 'unknown',
}

// ============================================================================
// Residual Policy
// ============================================================================

export interface ResidualPolicyResult {
  classification: ResidualClassification;
  confidence: number;
  reason: string;
  suggestedAction: 'preserve' | 'review' | 'block' | 'attempt-extraction';
}

export class ResidualPolicy {
  /**
   * Classify a residual island based on its content and context.
   */
  classify(island: ResidualIsland): ResidualPolicyResult {
    const searchableText = this.describeIsland(island);

    // Check for known unsafe patterns
    if (this.isUnsafe(searchableText)) {
      return {
        classification: ResidualClassification.UNSAFE,
        confidence: 0.9,
        reason: 'Contains unsafe patterns that cannot be safely compiled',
        suggestedAction: 'block',
      };
    }

    // Check for verbatim-safe patterns (comments, formatting)
    if (this.isVerbatimSafe(searchableText)) {
      return {
        classification: ResidualClassification.PRESERVE_VERBATIM,
        confidence: 0.95,
        reason: 'Safe to preserve verbatim',
        suggestedAction: 'preserve',
      };
    }

    // Check for patterns requiring review
    if (this.requiresReview(island, searchableText)) {
      return {
        classification: ResidualClassification.REQUIRES_REVIEW,
        confidence: 0.8,
        reason: 'Contains complex logic requiring manual review',
        suggestedAction: 'review',
      };
    }

    // Default to review for unknown patterns
    return {
      classification: ResidualClassification.UNKNOWN,
      confidence: 0.5,
      reason: 'Unknown pattern - requires review',
      suggestedAction: 'review',
    };
  }

  /**
   * Check if a residual island contains unsafe patterns.
   */
  private isUnsafe(searchableText: string): boolean {
    const unsafePatterns = [
      'unsafe',
      'eval(',
      'new Function(',
      'innerHTML',
      'dangerouslySetInnerHTML',
      'document.write',
      'exec(',
      'spawn(',
      'child_process',
    ];

    for (const pattern of unsafePatterns) {
      if (searchableText.includes(pattern)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Check if a residual island requires manual review.
   */
  private requiresReview(island: ResidualIsland, searchableText: string): boolean {
    const complexPatterns = [
      'try {',
      'catch (',
      'finally {',
      'Promise.all',
      'async/await',
      'generator',
      'iterator',
      'proxy',
      'reflect',
    ];

    for (const pattern of complexPatterns) {
      if (searchableText.includes(pattern)) {
        return true;
      }
    }

    return island.confidence >= 0.55 && island.confidence < 0.7;
  }

  /**
   * Check if a residual island is safe to preserve verbatim.
   */
  private isVerbatimSafe(searchableText: string): boolean {
    const verbatimSafeKinds = [
      'comment',
      'whitespace',
      'formatting',
      'documentation',
      'verbatim-preserve',
    ];

    return verbatimSafeKinds.some(kind => searchableText.includes(kind));
  }

  private describeIsland(island: ResidualIsland): string {
    return [
      island.id,
      island.kind,
      island.originalSource ?? '',
      island.normalizedSummary ?? '',
      island.reasonUnmodeled ?? '',
      island.reviewReason ?? '',
      island.sourceLocation.filePath,
      island.regenerationStrategy,
    ].join(' ').toLowerCase();
  }

  /**
   * Determine regeneration strategy for a residual island.
   */
  determineRegenerationStrategy(island: ResidualIsland): string {
    const policy = this.classify(island);

    switch (policy.suggestedAction) {
      case 'preserve':
        return island.regenerationStrategy || 'verbatim-preserve';
      case 'review':
        return island.regenerationStrategy || 'manual-review';
      case 'block':
        return 'block';
      case 'attempt-extraction':
        return 'attempt-extraction';
      default:
        return 'manual-review';
    }
  }

  /**
   * Batch classify multiple residual islands.
   */
  batchClassify(islands: readonly ResidualIsland[]): Map<string, ResidualPolicyResult> {
    const results = new Map<string, ResidualPolicyResult>();
    for (const island of islands) {
      results.set(island.id, this.classify(island));
    }
    return results;
  }

  /**
   * Filter islands by classification.
   */
  filterByClassification(
    islands: readonly ResidualIsland[],
    classification: ResidualClassification,
  ): ResidualIsland[] {
    return islands.filter(island => {
      const result = this.classify(island);
      return result.classification === classification;
    });
  }

  /**
   * Get statistics on residual classifications.
   */
  getStatistics(islands: readonly ResidualIsland[]): {
    total: number;
    preserveVerbatim: number;
    requiresReview: number;
    unsafe: number;
    unknown: number;
  } {
    const stats = {
      total: islands.length,
      preserveVerbatim: 0,
      requiresReview: 0,
      unsafe: 0,
      unknown: 0,
    };

    for (const island of islands) {
      const result = this.classify(island);
      switch (result.classification) {
        case ResidualClassification.PRESERVE_VERBATIM:
          stats.preserveVerbatim++;
          break;
        case ResidualClassification.REQUIRES_REVIEW:
          stats.requiresReview++;
          break;
        case ResidualClassification.UNSAFE:
          stats.unsafe++;
          break;
        case ResidualClassification.UNKNOWN:
          stats.unknown++;
          break;
      }
    }

    return stats;
  }
}

// ============================================================================
// Singleton instance
// ============================================================================

export const residualPolicy = new ResidualPolicy();
