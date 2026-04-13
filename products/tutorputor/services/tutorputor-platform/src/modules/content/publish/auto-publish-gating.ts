/**
 * Auto-Publish Gating Service
 *
 * Task 4.8: Implement Safe Auto-Publish Gating
 *
 * @doc.type module
 * @doc.purpose Safe auto-publish with guardrails
 * @doc.layer service
 * @doc.pattern GatingService
 */

import type { Logger } from 'pino';
import type { PrismaClient } from '@tutorputor/core/db';
import { FActScoreEvaluator, type FActScoreResult } from '../evaluation/factscore-evaluator';
import type { EvidenceBundle } from '../../knowledge-base/evidence-bundle';

/**
 * Auto-publish decision.
 */
export interface AutoPublishDecision {
  canAutoPublish: boolean;
  reasons: string[];
  confidence: number;
  requiresHumanReview: boolean;
  reviewReasons?: string[];
}

/**
 * Quality thresholds for auto-publish.
 */
export interface PublishThresholds {
  factScoreMin: number;
  evidenceCoverageMin: number;
  bundleConfidenceMin: number;
  noContradictions: boolean;
  notNovelDomain: boolean;
  notPolicySensitive: boolean;
}

/**
 * Service for gating auto-publish decisions.
 */
export class AutoPublishGatingService {
  private readonly defaultThresholds: PublishThresholds = {
    factScoreMin: 0.95,
    evidenceCoverageMin: 0.90,
    bundleConfidenceMin: 0.85,
    noContradictions: true,
    notNovelDomain: true,
    notPolicySensitive: true,
  };

  private readonly factScoreEvaluator: FActScoreEvaluator;

  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: Logger,
    private readonly thresholds?: Partial<PublishThresholds>
  ) {
    this.factScoreEvaluator = new FActScoreEvaluator(logger);
  }

  /**
   * Evaluate if an artifact can be auto-published.
   */
  async evaluateForAutoPublish(
    artifactId: string,
    artifactType: 'example' | 'animation' | 'simulation',
    content: string,
    evidenceBundle: EvidenceBundle,
    domain: string
  ): Promise<AutoPublishDecision> {
    const thresholds = { ...this.defaultThresholds, ...this.thresholds };
    const reasons: string[] = [];
    const reviewReasons: string[] = [];

    // Check FActScore
    const factScoreResult = await this.factScoreEvaluator.evaluate(content, evidenceBundle);
    if (factScoreResult.precision < thresholds.factScoreMin) {
      reasons.push(`FActScore ${factScoreResult.precision} below threshold ${thresholds.factScoreMin}`);
      reviewReasons.push('Fact verification failed');
    }

    // Check evidence coverage
    if (evidenceBundle.coverageScore < thresholds.evidenceCoverageMin) {
      reasons.push(`Evidence coverage ${evidenceBundle.coverageScore} below threshold ${thresholds.evidenceCoverageMin}`);
      reviewReasons.push('Insufficient evidence coverage');
    }

    // Check bundle confidence
    if (evidenceBundle.bundleConfidence < thresholds.bundleConfidenceMin) {
      reasons.push(`Bundle confidence ${evidenceBundle.bundleConfidence} below threshold ${thresholds.bundleConfidenceMin}`);
      reviewReasons.push('Low evidence confidence');
    }

    // Check contradictions
    if (thresholds.noContradictions && evidenceBundle.contradictionDetected) {
      reasons.push('Contradictions detected in evidence bundle');
      reviewReasons.push('Evidence contradictions require resolution');
    }

    // Check domain novelty
    if (thresholds.notNovelDomain && await this.isNovelDomainPattern(domain, artifactType)) {
      reasons.push('Novel domain pattern detected');
      reviewReasons.push('Novel content requires expert review');
    }

    // Check policy sensitivity
    if (thresholds.notPolicySensitive && this.isPolicySensitive(domain)) {
      reasons.push('Policy-sensitive domain detected');
      reviewReasons.push('Policy-sensitive content requires review');
    }

    // Calculate confidence
    const confidence = this.calculateConfidence(factScoreResult, evidenceBundle, reasons.length);

    // Determine if can auto-publish
    const canAutoPublish = reasons.length === 0 && confidence >= 0.95;

    this.logger.info({
      artifactId,
      canAutoPublish,
      reasonCount: reasons.length,
      confidence,
    }, 'Auto-publish evaluation complete');

    // Audit log
    await this.logDecision(artifactId, canAutoPublish, reasons, confidence);

    return {
      canAutoPublish,
      reasons,
      confidence,
      requiresHumanReview: reviewReasons.length > 0,
      reviewReasons: reviewReasons.length > 0 ? reviewReasons : undefined,
    };
  }

  /**
   * Check if domain pattern is novel.
   */
  private async isNovelDomainPattern(domain: string, artifactType: string): Promise<boolean> {
    // Check if this domain has published artifacts
    const count = await this.prisma.claimExample.count({
      where: {
        claim: {
          experience: {
            domain: domain.toUpperCase() as any,
          },
        },
        validationStatus: 'valid',
      },
    });

    // Less than 5 published artifacts = novel
    return count < 5;
  }

  /**
   * Check if domain is policy-sensitive.
   */
  private isPolicySensitive(domain: string): boolean {
    const sensitiveDomains = ['health', 'safety', 'legal', 'medical'];
    return sensitiveDomains.some(sd => domain.toLowerCase().includes(sd));
  }

  /**
   * Calculate overall confidence score.
   */
  private calculateConfidence(
    factScore: FActScoreResult,
    evidenceBundle: EvidenceBundle,
    violationCount: number
  ): number {
    let confidence = 1.0;

    // Deduct for fact score
    confidence -= (1 - factScore.precision) * 0.3;

    // Deduct for evidence coverage
    confidence -= (1 - evidenceBundle.coverageScore) * 0.2;

    // Deduct for bundle confidence
    confidence -= (1 - evidenceBundle.bundleConfidence) * 0.2;

    // Deduct for violations
    confidence -= violationCount * 0.1;

    return Math.max(0, Math.min(1, confidence));
  }

  /**
   * Log auto-publish decision for audit.
   */
  private async logDecision(
    artifactId: string,
    canAutoPublish: boolean,
    reasons: string[],
    confidence: number
  ): Promise<void> {
    // In production, write to audit log table
    this.logger.info({
      artifactId,
      canAutoPublish,
      reasons,
      confidence,
      timestamp: new Date().toISOString(),
    }, 'Auto-publish decision logged');
  }

  /**
   * Get current thresholds.
   */
  getThresholds(): PublishThresholds {
    return { ...this.defaultThresholds, ...this.thresholds };
  }

  /**
   * Update thresholds (requires admin).
   */
  updateThresholds(newThresholds: Partial<PublishThresholds>): void {
    Object.assign(this.thresholds || {}, newThresholds);
    this.logger.info({ newThresholds }, 'Publish thresholds updated');
  }
}
