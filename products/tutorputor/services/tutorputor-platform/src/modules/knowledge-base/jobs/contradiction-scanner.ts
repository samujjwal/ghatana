/**
 * Contradiction Scanner Job
 *
 * Task 1.6: Implement Freshness and Contradiction Checks
 *
 * @doc.type module
 * @doc.purpose Periodic scan for contradictory evidence
 * @doc.layer job
 * @doc.pattern BackgroundJob
 */

import type { Logger } from 'pino';
import type { PrismaClient } from '@tutorputor/core/db';

/**
 * Contradiction detection result.
 */
export interface ContradictionResult {
  bundleId: string;
  claimRef: string;
  contradictionsFound: number;
  newContradictions: Array<{
    evidenceARef: string;
    evidenceBRef: string;
    description: string;
    severity: 'HIGH' | 'MEDIUM' | 'LOW';
  }>;
  action: 'alert' | 'no_change';
}

/**
 * Service for scanning evidence bundles for contradictions.
 */
export class ContradictionScanner {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: Logger
  ) {}

  /**
   * Run contradiction scan on all evidence bundles.
   */
  async runScan(): Promise<ContradictionResult[]> {
    const results: ContradictionResult[] = [];

    this.logger.info('Starting contradiction scan');

    // Get all bundles with multiple evidences
    const bundles = await this.prisma.evidenceBundleMetadata.findMany({
      where: { evidenceCount: { gt: 1 } },
      take: 50, // Batch size
    });

    for (const bundle of bundles) {
      try {
        const result = await this.scanBundle(bundle.claimRef);
        results.push(result);
      } catch (error) {
        this.logger.error({ err: error, claimRef: bundle.claimRef }, 'Bundle scan failed');
      }
    }

    const withContradictions = results.filter(r => r.contradictionsFound > 0).length;
    this.logger.info(
      { total: results.length, withContradictions },
      'Contradiction scan complete'
    );

    return results;
  }

  /**
   * Scan a single evidence bundle for contradictions.
   */
  private async scanBundle(claimRef: string): Promise<ContradictionResult> {
    // Get all evidence for this claim
    const evidences = await this.prisma.learningEvidence.findMany({
      where: { claimRef },
    });

    if (evidences.length < 2) {
      return {
        bundleId: `bundle-${claimRef}`,
        claimRef,
        contradictionsFound: 0,
        newContradictions: [],
        action: 'no_change',
      };
    }

    const newContradictions: Array<{ evidenceARef: string; evidenceBRef: string; description: string; severity: 'HIGH' | 'MEDIUM' | 'LOW' }> = [];

    // Check all pairs for contradictions
    for (let i = 0; i < evidences.length; i++) {
      for (let j = i + 1; j < evidences.length; j++) {
        const a = evidences[i];
        const b = evidences[j];
        if (!a || !b) {
          continue;
        }

        // Check for direct support/opposition contradiction
        if (a.supportKind === 'SUPPORTS' && b.supportKind === 'CONTRADICTS') {
          newContradictions.push({
            evidenceARef: a.evidenceRef,
            evidenceBRef: b.evidenceRef,
            description: `Direct contradiction: ${a.sourceTitle} supports while ${b.sourceTitle} contradicts`,
            severity: 'HIGH',
          });
        }

        // Check for existing contradiction notes
        if (a.contradictionNotes?.includes(b.evidenceRef) ||
            b.contradictionNotes?.includes(a.evidenceRef)) {
          newContradictions.push({
            evidenceARef: a.evidenceRef,
            evidenceBRef: b.evidenceRef,
            description: a.contradictionNotes || b.contradictionNotes || 'Known contradiction',
            severity: 'MEDIUM',
          });
        }

        // Semantic similarity check (simplified)
        // In a real implementation, this would use embeddings or LLM
        const similarity = this.calculateSimilarity(a.excerpt, b.excerpt);
        if (similarity > 0.7 && a.supportKind !== b.supportKind) {
          newContradictions.push({
            evidenceARef: a.evidenceRef,
            evidenceBRef: b.evidenceRef,
            description: `Semantic contradiction (similarity: ${similarity.toFixed(2)})`,
            severity: 'MEDIUM',
          });
        }
      }
    }

    // Update bundle if contradictions found
    if (newContradictions.length > 0) {
      await this.prisma.evidenceBundleMetadata.update({
        where: { claimRef },
        data: { contradictionDetected: true },
      });

      // Update individual evidence with contradiction notes
      for (const contradiction of newContradictions) {
        await this.prisma.learningEvidence.updateMany({
          where: { evidenceRef: contradiction.evidenceARef },
          data: {
            contradictionNotes: `Contradicts ${contradiction.evidenceBRef}: ${contradiction.description}`,
            verificationState: 'DISPUTED',
          },
        });
      }
    }

    return {
      bundleId: `bundle-${claimRef}`,
      claimRef,
      contradictionsFound: newContradictions.length,
      newContradictions,
      action: newContradictions.length > 0 ? 'alert' : 'no_change',
    };
  }

  /**
   * Calculate simple text similarity (Jaccard index on words).
   */
  private calculateSimilarity(textA?: string | null, textB?: string | null): number {
    if (!textA || !textB) return 0;

    const wordsA = new Set(textA.toLowerCase().split(/\s+/));
    const wordsB = new Set(textB.toLowerCase().split(/\s+/));

    const intersection = new Set([...wordsA].filter(x => wordsB.has(x)));
    const union = new Set([...wordsA, ...wordsB]);

    return intersection.size / union.size;
  }

  /**
   * Get high-priority contradictions requiring human review.
   */
  async getHighPriorityContradictions(): Promise<Array<{
    claimRef: string;
    evidenceARef: string;
    evidenceBRef: string;
    description: string;
  }>> {
    const evidences = await this.prisma.learningEvidence.findMany({
      where: {
        verificationState: 'DISPUTED',
        contradictionNotes: { not: null },
      },
      take: 20,
    });

    return evidences.map(e => ({
      claimRef: e.claimRef,
      evidenceARef: e.evidenceRef,
      evidenceBRef: e.contradictionNotes?.split(':')[0]?.replace('Contradicts ', '') || 'unknown',
      description: e.contradictionNotes || '',
    }));
  }
}
