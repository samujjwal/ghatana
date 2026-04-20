/**
 * Personalization A/B Testing Integration Service
 *
 * Bridges personalization decisions with the A/B testing framework.
 * Enables experimentation on different personalization strategies.
 *
 * @doc.type class
 * @doc.purpose Integrate personalization with A/B testing for strategy optimization
 * @doc.layer product
 * @doc.pattern Experimentation Integration
 */

import { createStandaloneLogger } from '@tutorputor/core/logger';
import type { ABTestingService } from '../content/experiments/ab-testing/service.js';

const logger = createStandaloneLogger({ component: 'PersonalizationABTestingService' });

export type PersonalizationStrategy =
  | 'adaptive_difficulty'
  | 'learning_style_based'
  | 'pathway_optimized'
  | 'collaborative_filtering'
  | 'hybrid';

export interface CreatePersonalizationExperimentInput {
  tenantId: string;
  strategy: PersonalizationStrategy;
  controlStrategy: PersonalizationStrategy;
  treatmentStrategy: PersonalizationStrategy;
  notes?: string;
  priority?: number;
}

export interface RecordPersonalizationOutcomeInput {
  sessionId?: string;
  userId: string;
  assetId?: string;
  engagementScore: number;
  completionRate: number;
  masteryScore?: number;
  feedbackScore?: number;
  timeSpentMinutes: number;
  metadata?: Record<string, unknown>;
}

export class PersonalizationABTestingService {
  constructor(
    private readonly abTestingService: ABTestingService,
  ) {}

  /**
   * Create an experiment for comparing personalization strategies
   */
  async createPersonalizationExperiment(
    input: CreatePersonalizationExperimentInput,
  ) {
    logger.info({
      message: 'Creating personalization experiment',
      strategy: input.strategy,
      controlStrategy: input.controlStrategy,
      treatmentStrategy: input.treatmentStrategy,
    });

    // Create a generic experience ID for the experiment
    const experienceId = `personalization_${input.strategy}_${Date.now()}`;

    return await this.abTestingService.createExperienceExperiment(
      input.tenantId,
      {
        experienceId,
        controlVersion: 1,
        treatmentVersion: 2,
        notes: `${input.notes ?? ''} | Strategy: ${input.strategy} | Control: ${input.controlStrategy} | Treatment: ${input.treatmentStrategy}`,
        priority: input.priority ?? 0,
      },
    );
  }

  /**
   * Assign a personalization variant to a user
   */
  async assignPersonalizationVariant(
    tenantId: string,
    experimentId: string,
    userId: string,
  ): Promise<{ variant: 'control' | 'treatment'; strategy: PersonalizationStrategy }> {
    const variant = await this.abTestingService.assignVariant(
      tenantId,
      experimentId,
      userId,
    );

    // Map variant to strategy (simplified - in production, fetch from experiment metadata)
    const strategy = variant === 'control' ? 'adaptive_difficulty' as PersonalizationStrategy : 'learning_style_based' as PersonalizationStrategy;

    logger.info({
      message: 'Assigned personalization variant',
      userId,
      variant,
      strategy,
    });

    return { variant, strategy };
  }

  /**
   * Record personalization outcome metrics
   */
  async recordPersonalizationOutcome(
    tenantId: string,
    experimentId: string,
    userId: string,
    input: RecordPersonalizationOutcomeInput,
  ) {
    logger.info({
      message: 'Recording personalization outcome',
      userId,
      engagementScore: input.engagementScore,
      completionRate: input.completionRate,
    });

    // Calculate composite metric value (weighted combination of engagement and completion)
    const metricValue = (input.engagementScore * 0.6) + (input.completionRate * 0.4);

    return await this.abTestingService.recordObservation(
      tenantId,
      experimentId,
      userId,
      {
        metricValue,
        completed: input.completionRate >= 0.8,
        ...(input.sessionId ? { sessionId: input.sessionId } : {}),
        ...(input.assetId ? { assetId: input.assetId } : {}),
        ...(input.masteryScore !== undefined
          ? { masteryScore: input.masteryScore }
          : {}),
        ...(input.feedbackScore !== undefined
          ? { feedbackScore: input.feedbackScore }
          : {}),
        metadata: {
          ...input.metadata,
          engagementScore: input.engagementScore,
          completionRate: input.completionRate,
          timeSpentMinutes: input.timeSpentMinutes,
        },
      },
    );
  }

  /**
   * Get personalization experiment results
   */
  async getPersonalizationExperimentResults(
    tenantId: string,
    experimentId: string,
  ) {
    return await this.abTestingService.calculateResults(
      tenantId,
      experimentId,
    );
  }

  /**
   * Evaluate active personalization experiments
   */
  async evaluateActivePersonalizationExperiments(
    tenantId: string,
    options: {
      minSampleSize?: number;
      autoPromote?: boolean;
      maxPValue?: number;
      minRelativeImprovement?: number;
    } = {},
  ) {
    const results = await this.abTestingService.evaluateActiveExperiments(
      tenantId,
      options,
    );

    logger.info({
      message: 'Evaluated personalization experiments',
      evaluated: results.evaluated,
      promoted: results.promoted,
    });

    return results;
  }

  /**
   * Promote winning personalization strategy
   */
  async promoteWinningStrategy(
    tenantId: string,
    experimentId: string,
  ) {
    logger.info({
      message: 'Promoting winning personalization strategy',
      experimentId,
    });

    return await this.abTestingService.promoteWinner(
      tenantId,
      experimentId,
    );
  }
}
