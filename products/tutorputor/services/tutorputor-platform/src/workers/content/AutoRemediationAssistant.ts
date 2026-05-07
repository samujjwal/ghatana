/**
 * Auto-Remediation Assistant for Claim-Task-Artifact Completion
 *
 * Monitors claim and artifact generation jobs, detects failures,
 * and applies remediation strategies including retries with adjusted
 * parameters, fallback to defaults, and escalation to human review.
 *
 * @doc.type class
 * @doc.purpose Auto-remediation for content generation failures
 * @doc.layer backend-worker
 * @doc.pattern RemediationAssistant
 */

import { Job, Queue } from 'bullmq';
import { PrismaClient } from '@tutorputor/core/db';
import { Logger } from 'pino';
import * as crypto from 'crypto';

/**
 * Remediation strategy types.
 */
export type RemediationStrategy =
  | 'retry_with_defaults'
  | 'reduce_complexity'
  | 'use_deterministic_fallback'
  | 'escalate_to_human'
  | 'skip_and_continue';

/**
 * Failure classification.
 */
export interface FailureClassification {
  type: 'timeout' | 'validation_error' | 'ai_service_unavailable' | 'rate_limit' | 'content_policy' | 'unknown';
  severity: 'low' | 'medium' | 'high' | 'critical';
  retryable: boolean;
  suggestedStrategy: RemediationStrategy;
}

/**
 * Remediation action result.
 */
export interface RemediationActionResult {
  strategy: RemediationStrategy;
  success: boolean;
  message: string;
  escalated?: boolean;
  escalationReason?: string;
}

/**
 * Auto-remediation assistant configuration.
 */
export interface AutoRemediationConfig {
  maxRetries: number;
  maxEscalationsPerDay: number;
  enableDeterministicFallback: boolean;
  enableComplexityReduction: boolean;
}

interface RemediationJobData {
  maxClaims?: number;
  count?: number;
  experienceId?: string;
  tenantId?: string;
  domain?: string;
  gradeLevel?: string;
  topic?: string;
  claimRef?: string;
  claimText?: string;
}

function getRemediationJobData(job: Job): RemediationJobData {
  return job.data && typeof job.data === 'object'
    ? (job.data as RemediationJobData)
    : {};
}

function requireJobString(
  data: RemediationJobData,
  key: keyof RemediationJobData,
): string {
  const value = data[key];
  if (typeof value !== 'string' || value.length === 0) {
    throw new Error(`Missing required remediation job field: ${key}`);
  }
  return value;
}

/**
 * Auto-remediation assistant for content generation failures.
 */
export class AutoRemediationAssistant {
  private escalationCount: number = 0;
  private lastEscalationDate: Date | null = null;

  constructor(
    private prisma: PrismaClient,
    private queue: Queue,
    private logger: Logger,
    private config: AutoRemediationConfig = {
      maxRetries: 3,
      maxEscalationsPerDay: 10,
      enableDeterministicFallback: true,
      enableComplexityReduction: true,
    }
  ) {}

  /**
   * Classify a failure to determine remediation strategy.
   */
  classifyFailure(error: unknown, jobType: string): FailureClassification {
    const errorMessage = error instanceof Error ? error.message : String(error);

    // AI service unavailable
    if (errorMessage.includes('ECONNREFUSED') || errorMessage.includes('service unavailable')) {
      return {
        type: 'ai_service_unavailable',
        severity: 'high',
        retryable: true,
        suggestedStrategy: 'retry_with_defaults',
      };
    }

    // Timeout
    if (errorMessage.includes('timeout') || errorMessage.includes('ETIMEDOUT')) {
      return {
        type: 'timeout',
        severity: 'medium',
        retryable: true,
        suggestedStrategy: 'reduce_complexity',
      };
    }

    // Rate limit
    if (errorMessage.includes('rate limit') || errorMessage.includes('429')) {
      return {
        type: 'rate_limit',
        severity: 'medium',
        retryable: true,
        suggestedStrategy: 'retry_with_defaults',
      };
    }

    // Validation error
    if (errorMessage.includes('validation') || errorMessage.includes('invalid')) {
      return {
        type: 'validation_error',
        severity: 'low',
        retryable: false,
        suggestedStrategy: 'use_deterministic_fallback',
      };
    }

    // Content policy violation
    if (errorMessage.includes('policy') || errorMessage.includes('content policy')) {
      return {
        type: 'content_policy',
        severity: 'high',
        retryable: false,
        suggestedStrategy: 'escalate_to_human',
      };
    }

    // Unknown error - escalate if critical
    return {
      type: 'unknown',
      severity: 'high',
      retryable: false,
      suggestedStrategy: 'escalate_to_human',
    };
  }

  /**
   * Attempt to remediate a failed job.
   */
  async remediateFailedJob(
    job: Job,
    error: unknown,
    jobType: 'claim' | 'example' | 'simulation' | 'animation' | 'assessment'
  ): Promise<RemediationActionResult> {
    const classification = this.classifyFailure(error, jobType);

    this.logger.info(
      {
        jobId: job.id,
        jobType,
        classification,
      },
      'Attempting remediation'
    );

    // Check escalation limits
    if (classification.suggestedStrategy === 'escalate_to_human') {
      if (!this.canEscalate()) {
        this.logger.warn(
          { jobId: job.id },
          'Escalation limit reached, skipping and continuing'
        );
        return {
          strategy: 'skip_and_continue',
          success: false,
          message: 'Escalation limit reached, job skipped',
        };
      }
    }

    // Apply remediation strategy
    switch (classification.suggestedStrategy) {
      case 'retry_with_defaults':
        return await this.retryWithDefaults(job, jobType);

      case 'reduce_complexity':
        return await this.reduceComplexity(job, jobType);

      case 'use_deterministic_fallback':
        return await this.useDeterministicFallback(job, jobType);

      case 'escalate_to_human':
        return await this.escalateToHuman(job, jobType, error, classification);

      case 'skip_and_continue':
        return {
          strategy: 'skip_and_continue',
          success: false,
          message: 'Job skipped per policy',
        };

      default:
        return {
          strategy: 'escalate_to_human',
          success: false,
          message: 'Unknown strategy, escalating',
          escalated: true,
          escalationReason: 'Unknown remediation strategy',
        };
    }
  }

  /**
   * Retry job with default parameters.
   */
  private async retryWithDefaults(
    job: Job,
    jobType: string
  ): Promise<RemediationActionResult> {
    try {
      const retryCount = job.attemptsMade || 0;

      if (retryCount >= this.config.maxRetries) {
        this.logger.warn(
          { jobId: job.id, retryCount },
          'Max retries reached, escalating'
        );
        return await this.escalateToHuman(
          job,
          jobType,
          new Error('Max retries exceeded'),
          { type: 'unknown', severity: 'high', retryable: false, suggestedStrategy: 'escalate_to_human' }
        );
      }

      // Re-queue with exponential backoff
      const delayMs = Math.pow(2, retryCount) * 1000; // 1s, 2s, 4s, etc.

      await job.moveToDelayed(Date.now() + delayMs);

      this.logger.info(
        { jobId: job.id, delayMs, retryCount },
        'Job re-queued with delay'
      );

      return {
        strategy: 'retry_with_defaults',
        success: true,
        message: `Job re-queued with ${delayMs}ms delay`,
      };
    } catch (error) {
      this.logger.error({ err: error, jobId: job.id }, 'Failed to retry job');
      return {
        strategy: 'retry_with_defaults',
        success: false,
        message: 'Failed to re-queue job',
      };
    }
  }

  /**
   * Reduce complexity and retry.
   */
  private async reduceComplexity(
    job: Job,
    jobType: string
  ): Promise<RemediationActionResult> {
    if (!this.config.enableComplexityReduction) {
      return await this.useDeterministicFallback(job, jobType);
    }

    try {
      const data = getRemediationJobData(job);

      // Reduce max claims/examples count
      if (data.maxClaims && data.maxClaims > 5) {
        data.maxClaims = Math.max(3, Math.floor(data.maxClaims / 2));
        this.logger.info(
          { jobId: job.id, newMaxClaims: data.maxClaims },
          'Reduced max claims for retry'
        );
      }

      if (data.count && data.count > 3) {
        data.count = Math.max(2, Math.floor(data.count / 2));
        this.logger.info(
          { jobId: job.id, newCount: data.count },
          'Reduced item count for retry'
        );
      }

      // Re-queue with reduced complexity
      await job.moveToDelayed(Date.now() + 2000, job.id);

      return {
        strategy: 'reduce_complexity',
        success: true,
        message: 'Job re-queued with reduced complexity',
      };
    } catch (error) {
      this.logger.error({ err: error, jobId: job.id }, 'Failed to reduce complexity');
      return await this.useDeterministicFallback(job, jobType);
    }
  }

  /**
   * Use deterministic fallback content.
   */
  private async useDeterministicFallback(
    job: Job,
    jobType: string
  ): Promise<RemediationActionResult> {
    if (!this.config.enableDeterministicFallback) {
      return await this.escalateToHuman(
        job,
        jobType,
        new Error('Deterministic fallback disabled'),
        { type: 'unknown', severity: 'high', retryable: false, suggestedStrategy: 'escalate_to_human' }
      );
    }

    try {
      const data = getRemediationJobData(job);
      const experienceId = requireJobString(data, 'experienceId');
      const tenantId = requireJobString(data, 'tenantId');
      const domain = requireJobString(data, 'domain');
      const gradeLevel = requireJobString(data, 'gradeLevel');

      this.logger.info(
        { jobId: job.id, jobType, experienceId },
        'Using deterministic fallback'
      );

      // Generate deterministic content based on domain
      if (jobType === 'claim') {
        await this.generateDeterministicClaims(
          experienceId,
          tenantId,
          domain,
          gradeLevel,
          requireJobString(data, 'topic'),
        );
      } else if (jobType === 'example') {
        await this.generateDeterministicExample(
          experienceId,
          tenantId,
          domain,
          gradeLevel,
          requireJobString(data, 'claimRef'),
          requireJobString(data, 'claimText'),
        );
      }

      return {
        strategy: 'use_deterministic_fallback',
        success: true,
        message: 'Deterministic fallback content generated',
      };
    } catch (error) {
      this.logger.error({ err: error, jobId: job.id }, 'Failed to generate deterministic fallback');
      return await this.escalateToHuman(
        job,
        jobType,
        error,
        { type: 'unknown', severity: 'high', retryable: false, suggestedStrategy: 'escalate_to_human' }
      );
    }
  }

  /**
   * Escalate to human review.
   */
  private async escalateToHuman(
    job: Job,
    jobType: string,
    error: unknown,
    classification: FailureClassification
  ): Promise<RemediationActionResult> {
    try {
      const data = getRemediationJobData(job);
      const experienceId = requireJobString(data, 'experienceId');
      const tenantId = requireJobString(data, 'tenantId');

      // Record escalation in database using existing model
      // Log escalation for now - can be extended with dedicated model later
      await this.prisma.auditLog.create({
        data: {
          id: crypto.randomUUID(),
          action: 'CONTENT_GENERATION_ESCALATION',
          resourceType: 'LearningExperience',
          resourceId: experienceId,
          tenantId,
          actorId: 'system',
          metadata: JSON.stringify({
            jobType,
            jobId: job.id as string,
            errorType: classification.type,
            severity: classification.severity,
            errorMessage: error instanceof Error ? error.message : String(error),
          }),
          timestamp: new Date(),
        },
      });

      this.escalationCount++;
      this.lastEscalationDate = new Date();

      this.logger.warn(
        {
          jobId: job.id,
          jobType,
          experienceId,
          classification,
          escalationCount: this.escalationCount,
        },
        'Job escalated to human review'
      );

      return {
        strategy: 'escalate_to_human',
        success: false,
        message: 'Job escalated to human review',
        escalated: true,
        escalationReason: classification.type,
      };
    } catch (dbError) {
      this.logger.error(
        { err: dbError, jobId: job.id },
        'Failed to record escalation in database'
      );

      return {
        strategy: 'escalate_to_human',
        success: false,
        message: 'Escalation failed (database error)',
        escalated: true,
        escalationReason: 'Database error during escalation',
      };
    }
  }

  /**
   * Check if escalation is allowed based on limits.
   */
  private canEscalate(): boolean {
    // Reset counter if it's a new day
    if (this.lastEscalationDate) {
      const now = new Date();
      const isSameDay =
        now.getDate() === this.lastEscalationDate.getDate() &&
        now.getMonth() === this.lastEscalationDate.getMonth() &&
        now.getFullYear() === this.lastEscalationDate.getFullYear();

      if (!isSameDay) {
        this.escalationCount = 0;
        this.lastEscalationDate = null;
      }
    }

    return this.escalationCount < this.config.maxEscalationsPerDay;
  }

  /**
   * Generate deterministic claims as fallback.
   */
  private async generateDeterministicClaims(
    experienceId: string,
    tenantId: string,
    domain: string,
    gradeLevel: string,
    topic: string
  ): Promise<void> {
    const defaultClaims = this.getDefaultClaimsForDomain(domain, topic, 3);

    for (const claim of defaultClaims) {
      await this.prisma.learningClaim.create({
        data: {
          experienceId,
          claimRef: claim.claimRef,
          text: claim.text,
          bloomLevel: claim.bloomLevel,
          orderIndex: claim.orderIndex,
          contentNeeds: claim.contentNeeds,
        },
      });
    }
  }

  /**
   * Generate deterministic example as fallback.
   * Note: Simplified to log only since ArtifactManifest requires assetId.
   */
  private async generateDeterministicExample(
    experienceId: string,
    tenantId: string,
    domain: string,
    gradeLevel: string,
    claimRef: string,
    claimText: string
  ): Promise<void> {
    const example = this.getDefaultExampleForDomain(domain, claimRef, claimText);

    // Log the deterministic example for manual review
    this.logger.info(
      { experienceId, claimRef, domain, example },
      'Deterministic example generated (logged for manual review)'
    );

    // In a full implementation, this would create a ContentAsset first,
    // then link it to an ArtifactManifest. For now, we just log it.
  }

  /**
   * Get default claims for a domain.
   */
  private getDefaultClaimsForDomain(domain: string, topic: string, count: number) {
    return Array.from({ length: count }, (_, i) => ({
      claimRef: `fallback-${domain}-${i + 1}`,
      text: `Understanding ${topic} in ${domain} (claim ${i + 1})`,
      bloomLevel: 'UNDERSTAND' as const,
      orderIndex: i + 1,
      contentNeeds: {
        examples: { required: true, types: ['REAL_WORLD'], count: 2, necessity: 0.8 },
        simulation: { required: false, necessity: 0.3 },
        animation: { required: false, necessity: 0.2 },
      },
    }));
  }

  /**
   * Get default example for a domain.
   */
  private getDefaultExampleForDomain(domain: string, claimRef: string, claimText: string) {
    return {
      schemaVersion: '1.0.0',
      exampleType: 'REAL_WORLD',
      domain,
      difficulty: 'beginner' as const,
      problemStatement: claimText,
      steps: [
        {
          stepNumber: 1,
          label: 'Understand the problem',
          content: 'First, read and understand what is being asked.',
        },
        {
          stepNumber: 2,
          label: 'Identify key information',
          content: 'Extract the important information from the problem.',
        },
        {
          stepNumber: 3,
          label: 'Apply the solution',
          content: 'Use the appropriate method to solve the problem.',
        },
      ],
      answer: 'Solution completed using standard methods',
      scaffolding: 'medium' as const,
    };
  }

  /**
   * Get remediation statistics.
   */
  getStats() {
    return {
      escalationCount: this.escalationCount,
      lastEscalationDate: this.lastEscalationDate,
      canEscalate: this.canEscalate(),
    };
  }
}
