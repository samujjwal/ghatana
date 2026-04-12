/**
 * Evidence Freshness Checker Job
 *
 * Task 1.6: Implement Freshness and Contradiction Checks
 *
 * @doc.type module
 * @doc.purpose Background job for evidence health monitoring
 * @doc.layer job
 * @doc.pattern BackgroundJob
 */

import type { Logger } from 'pino';
import type { PrismaClient } from '@tutorputor/core/db';
import type { EvidenceSourceAdapter } from '../adapters/evidence-source-adapter';

/**
 * Configuration for freshness checker.
 */
export interface FreshnessCheckerConfig {
  /** Run check every X hours */
  checkIntervalHours: number;
  /** Evidence older than this (days) is considered stale */
  staleThresholdDays: number;
  /** Evidence older than this (days) is considered expired */
  expireThresholdDays: number;
  /** Minimum credibility score to trigger re-retrieval */
  minCredibilityForRefresh: number;
}

/**
 * Result of a freshness check.
 */
export interface FreshnessCheckResult {
  evidenceId: string;
  evidenceRef: string;
  oldStatus: string;
  newStatus: 'CURRENT' | 'STALE' | 'EXPIRED' | 'UNKNOWN';
  action: 'refreshed' | 'flagged' | 'no_change' | 'error';
  error?: string;
}

/**
 * Service for checking evidence freshness.
 */
export class EvidenceFreshnessChecker {
  private readonly defaultConfig: FreshnessCheckerConfig = {
    checkIntervalHours: 24,
    staleThresholdDays: 30,
    expireThresholdDays: 365,
    minCredibilityForRefresh: 0.7,
  };

  constructor(
    private readonly prisma: PrismaClient,
    private readonly adapters: Map<string, EvidenceSourceAdapter>,
    private readonly logger: Logger,
    private readonly config?: Partial<FreshnessCheckerConfig>
  ) {}

  /**
   * Run freshness check on all evidence.
   * This should be called by a scheduled job (e.g., cron).
   */
  async runCheck(): Promise<FreshnessCheckResult[]> {
    const config = { ...this.defaultConfig, ...this.config };
    const results: FreshnessCheckResult[] = [];

    this.logger.info('Starting evidence freshness check');

    // Get all evidence that needs checking
    const evidences = await this.prisma.learningEvidence.findMany({
      where: {
        OR: [
          { freshnessStatus: { in: ['CURRENT', 'UNKNOWN'] } },
          { retrievedAt: { lt: new Date(Date.now() - config.staleThresholdDays * 24 * 60 * 60 * 1000) } },
        ],
      },
      take: 100, // Batch size
    });

    this.logger.info({ count: evidences.length }, 'Found evidence to check');

    for (const evidence of evidences) {
      try {
        const result = await this.checkSingleEvidence(evidence, config);
        results.push(result);
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Unknown error';
        this.logger.error({ err: error, evidenceId: evidence.id }, 'Freshness check failed');
        results.push({
          evidenceId: evidence.id,
          evidenceRef: evidence.evidenceRef,
          oldStatus: evidence.freshnessStatus,
          newStatus: 'UNKNOWN',
          action: 'error',
          error: message,
        });
      }
    }

    // Emit metrics
    const refreshed = results.filter(r => r.action === 'refreshed').length;
    const flagged = results.filter(r => r.action === 'flagged').length;
    const errors = results.filter(r => r.action === 'error').length;

    this.logger.info(
      { total: results.length, refreshed, flagged, errors },
      'Freshness check complete'
    );

    return results;
  }

  /**
   * Check freshness of a single evidence item.
   */
  private async checkSingleEvidence(
    evidence: { id: string; evidenceRef: string; sourceType: string; sourceUrl: string | null; freshnessStatus: string; retrievedAt: Date; credibilityScore: number | null },
    config: FreshnessCheckerConfig
  ): Promise<FreshnessCheckResult> {
    const ageMs = Date.now() - evidence.retrievedAt.getTime();
    const ageDays = ageMs / (24 * 60 * 60 * 1000);

    let newStatus: 'CURRENT' | 'STALE' | 'EXPIRED' | 'UNKNOWN';
    let action: 'refreshed' | 'flagged' | 'no_change' = 'no_change';

    // Determine status based on age
    if (ageDays > config.expireThresholdDays) {
      newStatus = 'EXPIRED';
    } else if (ageDays > config.staleThresholdDays) {
      newStatus = 'STALE';
    } else {
      newStatus = 'CURRENT';
    }

    // Attempt to refresh if stale and has high credibility
    if (newStatus === 'STALE' &&
        (evidence.credibilityScore ?? 0) >= config.minCredibilityForRefresh &&
        evidence.sourceUrl) {
      const adapter = this.adapters.get(evidence.sourceType);
      if (adapter) {
        try {
          const content = await adapter.retrieveContent(evidence.sourceUrl);
          if (content) {
            // Update evidence with fresh content
            await this.prisma.learningEvidence.update({
              where: { id: evidence.id },
              data: {
                excerpt: content.content.substring(0, 500),
                retrievedAt: new Date(),
                freshnessStatus: 'CURRENT',
              },
            });
            newStatus = 'CURRENT';
            action = 'refreshed';
          }
        } catch (error) {
          this.logger.warn(
            { err: error, evidenceId: evidence.id },
            'Failed to refresh evidence'
          );
        }
      }
    }

    // Update status if changed and not refreshed
    if (action !== 'refreshed' && newStatus !== evidence.freshnessStatus) {
      await this.prisma.learningEvidence.update({
        where: { id: evidence.id },
        data: { freshnessStatus: newStatus },
      });
      action = 'flagged';
    }

    return {
      evidenceId: evidence.id,
      evidenceRef: evidence.evidenceRef,
      oldStatus: evidence.freshnessStatus,
      newStatus,
      action,
    };
  }

  /**
   * Get statistics on evidence freshness.
   */
  async getFreshnessStats(): Promise<{
    total: number;
    current: number;
    stale: number;
    expired: number;
    unknown: number;
  }> {
    const [total, current, stale, expired, unknown] = await Promise.all([
      this.prisma.learningEvidence.count(),
      this.prisma.learningEvidence.count({ where: { freshnessStatus: 'CURRENT' } }),
      this.prisma.learningEvidence.count({ where: { freshnessStatus: 'STALE' } }),
      this.prisma.learningEvidence.count({ where: { freshnessStatus: 'EXPIRED' } }),
      this.prisma.learningEvidence.count({ where: { freshnessStatus: 'UNKNOWN' } }),
    ]);

    return { total, current, stale, expired, unknown };
  }
}
