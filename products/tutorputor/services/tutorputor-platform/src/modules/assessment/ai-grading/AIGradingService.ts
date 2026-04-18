/**
 * AI Grading Service
 *
 * AI-based grading for open-ended assessment responses.
 * Uses semantic similarity and rubric-based evaluation.
 *
 * @doc.type class
 * @doc.purpose AI-powered grading for open-ended assessment questions
 * @doc.layer product
 * @doc.pattern AI Service
 */

import { createStandaloneLogger } from '@tutorputor/core/logger';
import { aiClient } from '../../../clients/ai-client';

const logger = createStandaloneLogger({ component: 'AIGradingService' });

export interface AIGradingRequest {
  tenantId: string;
  assessmentId: string;
  itemId: string;
  questionPrompt: string;
  studentResponse: string;
  rubric?: {
    criteria: Array<{
      name: string;
      description: string;
      maxPoints: number;
    }>;
  };
  modelAnswer?: string;
  context?: {
    domain?: string;
    difficulty?: string;
    learningObjectives?: string[];
  };
}

export interface AIGradingResult {
  itemId: string;
  scorePercent: number;
  earnedPoints: number;
  maxPoints: number;
  confidence: number;
  needsReview: boolean;
  feedback: {
    strengths: string[];
    improvements: string[];
    comments: string;
    rubricScores?: Array<{
      criterion: string;
      score: number;
      maxPoints: number;
      feedback: string;
    }>;
  };
  metadata: {
    modelUsed: string;
    processingTimeMs: number;
    timestamp: string;
  };
}

export class AIGradingService {
  private readonly CONFIDENCE_THRESHOLD = 0.7;

  /**
   * Grade an open-ended response using AI
   */
  async gradeOpenEndedResponse(request: AIGradingRequest): Promise<AIGradingResult> {
    const startTime = Date.now();

    logger.info({
      message: 'Grading open-ended response with AI',
      itemId: request.itemId,
      tenantId: request.tenantId,
    });

    try {
      // Call AI client for grading
      const aiResponse = await aiClient.gradeResponse({
        question: request.questionPrompt,
        response: request.studentResponse,
        rubric: request.rubric,
        modelAnswer: request.modelAnswer,
        context: request.context,
      });

      const processingTimeMs = Date.now() - startTime;

      // Process AI response
      const result: AIGradingResult = {
        itemId: request.itemId,
        scorePercent: aiResponse.scorePercent,
        earnedPoints: aiResponse.earnedPoints,
        maxPoints: aiResponse.maxPoints,
        confidence: aiResponse.confidence,
        needsReview: aiResponse.confidence < this.CONFIDENCE_THRESHOLD,
        feedback: {
          strengths: aiResponse.strengths || [],
          improvements: aiResponse.improvements || [],
          comments: aiResponse.comments || '',
          rubricScores: aiResponse.rubricScores,
        },
        metadata: {
          modelUsed: aiResponse.model || 'tutorputor-grading-v1',
          processingTimeMs,
          timestamp: new Date().toISOString(),
        },
      };

      logger.info({
        message: 'AI grading completed',
        itemId: request.itemId,
        scorePercent: result.scorePercent,
        confidence: result.confidence,
        needsReview: result.needsReview,
        processingTimeMs,
      });

      return result;
    } catch (error) {
      logger.error({
        message: 'AI grading failed, falling back to teacher review',
        itemId: request.itemId,
        error: error instanceof Error ? error.message : String(error),
      });

      // Fallback to teacher review on error
      return {
        itemId: request.itemId,
        scorePercent: 0,
        earnedPoints: 0,
        maxPoints: request.rubric?.criteria.reduce((sum, c) => sum + c.maxPoints, 0) || 10,
        confidence: 0,
        needsReview: true,
        feedback: {
          strengths: [],
          improvements: [],
          comments: 'AI grading unavailable. Requires teacher review.',
        },
        metadata: {
          modelUsed: 'fallback',
          processingTimeMs: Date.now() - startTime,
          timestamp: new Date().toISOString(),
        },
      };
    }
  }

  /**
   * Batch grade multiple responses
   */
  async gradeBatch(requests: AIGradingRequest[]): Promise<AIGradingResult[]> {
    logger.info({
      message: 'Batch grading responses',
      count: requests.length,
    });

    const results = await Promise.all(
      requests.map((req) => this.gradeOpenEndedResponse(req)),
    );

    const needsReviewCount = results.filter((r) => r.needsReview).length;
    const avgConfidence =
      results.reduce((sum, r) => sum + r.confidence, 0) / results.length;

    logger.info({
      message: 'Batch grading completed',
      total: results.length,
      needsReview: needsReviewCount,
      avgConfidence,
    });

    return results;
  }

  /**
   * Get grading statistics for monitoring
   */
  getGradingStats(results: AIGradingResult[]): {
    total: number;
    avgScorePercent: number;
    avgConfidence: number;
    needsReviewCount: number;
    avgProcessingTimeMs: number;
  } {
    if (results.length === 0) {
      return {
        total: 0,
        avgScorePercent: 0,
        avgConfidence: 0,
        needsReviewCount: 0,
        avgProcessingTimeMs: 0,
      };
    }

    return {
      total: results.length,
      avgScorePercent:
        results.reduce((sum, r) => sum + r.scorePercent, 0) / results.length,
      avgConfidence:
        results.reduce((sum, r) => sum + r.confidence, 0) / results.length,
      needsReviewCount: results.filter((r) => r.needsReview).length,
      avgProcessingTimeMs:
        results.reduce((sum, r) => sum + r.metadata.processingTimeMs, 0) /
        results.length,
    };
  }
}
