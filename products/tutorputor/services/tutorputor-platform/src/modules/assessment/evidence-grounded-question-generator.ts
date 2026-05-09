/**
 * Evidence-Grounded Question Generator
 *
 * Implements fail-closed behavior for question generation by requiring
 * questions to be grounded in content evidence. If insufficient evidence
 * is found, generation fails rather than producing hallucinated content.
 *
 * @doc.type module
 * @doc.purpose Ground question generation in evidence with fail-closed behavior
 * @doc.layer product
 * @doc.pattern Service
 */

import type { Logger } from 'pino';
import type { TutorPrismaClient } from '@tutorputor/core/db';
import type { SemanticEvidenceSearchService } from '../knowledge-base/semantic-evidence-search';
import { z } from 'zod';

// ============================================================================
// Types
// ============================================================================

export interface EvidenceGroundingConfig {
  minimumEvidenceCount: number;
  similarityThreshold: number;
  requireDirectEvidence: boolean;
  allowPartialGrounding: boolean;
}

export interface QuestionGenerationRequest {
  topic: string;
  domain: string;
  gradeBand: string;
  learningObjectives: string[];
  claims?: string[];
  itemCount: number;
  difficulty: 'beginner' | 'intermediate' | 'advanced' | 'mixed';
}

export interface QuestionGenerationContext {
  evidence: EvidenceGroundingResult;
  isGrounded: boolean;
  groundingReason: string;
  canProceed: boolean;
}

export interface EvidenceGroundingResult {
  hasSufficientEvidence: boolean;
  evidenceCount: number;
  evidenceItems: Array<{
    evidenceId: string;
    text: string;
    similarityScore: number;
    claimRef: string;
  }>;
  confidenceScore: number;
  gaps: string[];
}

export interface GroundedQuestion {
  id: string;
  question: string;
  type: 'multiple_choice' | 'short_answer' | 'true_false';
  options: string[];
  correctAnswer: string;
  explanation: string;
  evidenceReferences: string[];
  groundingScore: number;
  difficulty: 'beginner' | 'intermediate' | 'advanced';
}

// ============================================================================
// Zod Schemas
// ============================================================================

const QuestionGenerationRequestSchema = z.object({
  topic: z.string().min(1),
  domain: z.string().min(1),
  gradeBand: z.string().min(1),
  learningObjectives: z.array(z.string()).min(1),
  claims: z.array(z.string()).optional(),
  itemCount: z.number().min(1).max(50),
  difficulty: z.enum(['beginner', 'intermediate', 'advanced', 'mixed']),
}).strict();

// ============================================================================
// Evidence-Grounded Question Generator
// ============================================================================

export class EvidenceGroundedQuestionGenerator {
  private config: EvidenceGroundingConfig;

  constructor(
    private readonly prisma: TutorPrismaClient,
    private readonly logger: Logger,
    private readonly evidenceSearchService: SemanticEvidenceSearchService,
    config?: Partial<EvidenceGroundingConfig>
  ) {
    this.config = {
      minimumEvidenceCount: 3,
      similarityThreshold: 0.75,
      requireDirectEvidence: true,
      allowPartialGrounding: false,
      ...config,
    };
  }

  /**
   * Check if there is sufficient evidence to support question generation.
   * This implements the fail-closed behavior - if evidence is insufficient,
   * generation cannot proceed.
   */
  async checkEvidenceGrounding(
    request: QuestionGenerationRequest
  ): Promise<QuestionGenerationContext> {
    const validatedRequest = QuestionGenerationRequestSchema.parse(request) as QuestionGenerationRequest;

    this.logger.info(
      { topic: validatedRequest.topic, domain: validatedRequest.domain },
      'Checking evidence grounding for question generation'
    );

    // Search for evidence related to the topic and claims
    const evidenceResults: Array<{
      evidenceId: string;
      text: string;
      similarityScore: number;
      claimRef: string;
    }> = [];

    // Search for evidence on the topic
    const topicEvidence = await this.evidenceSearchService.searchEvidence(
      validatedRequest.topic,
      {
        claimRef: '', // Search across all claims
        domain: validatedRequest.domain,
        gradeBand: validatedRequest.gradeBand,
        maxResults: 10,
        threshold: this.config.similarityThreshold,
      }
    );
    evidenceResults.push(...topicEvidence);

    // Search for evidence on claims if provided
    if (validatedRequest.claims && validatedRequest.claims.length > 0) {
      for (const claim of validatedRequest.claims) {
        const claimEvidence = await this.evidenceSearchService.searchEvidence(claim, {
          claimRef: claim,
          domain: validatedRequest.domain,
          gradeBand: validatedRequest.gradeBand,
          maxResults: 5,
          threshold: this.config.similarityThreshold,
        });
        evidenceResults.push(...claimEvidence);
      }
    }

    // Deduplicate evidence
    const uniqueEvidence = new Map<string, typeof evidenceResults[0]>();
    for (const evidence of evidenceResults) {
      if (!uniqueEvidence.has(evidence.evidenceId)) {
        uniqueEvidence.set(evidence.evidenceId, evidence);
      }
    }

    const uniqueEvidenceArray = Array.from(uniqueEvidence.values());

    // Calculate grounding metrics
    const hasSufficientEvidence = uniqueEvidenceArray.length >= this.config.minimumEvidenceCount;
    const confidenceScore = this.calculateGroundingConfidence(uniqueEvidenceArray);
    const gaps = this.identifyEvidenceGaps(validatedRequest, uniqueEvidenceArray);

    const result: EvidenceGroundingResult = {
      hasSufficientEvidence,
      evidenceCount: uniqueEvidenceArray.length,
      evidenceItems: uniqueEvidenceArray,
      confidenceScore,
      gaps,
    };

    // Determine if generation can proceed
    const canProceed = this.config.allowPartialGrounding
      ? uniqueEvidenceArray.length > 0
      : hasSufficientEvidence;

    const groundingReason = canProceed
      ? `Sufficient evidence found (${uniqueEvidenceArray.length} items, confidence: ${confidenceScore.toFixed(2)})`
      : `Insufficient evidence: found ${uniqueEvidenceArray.length} items, require ${this.config.minimumEvidenceCount}`;

    this.logger.info(
      {
        hasSufficientEvidence,
        evidenceCount: uniqueEvidenceArray.length,
        confidenceScore,
        canProceed,
      },
      'Evidence grounding check completed'
    );

    return {
      evidence: result,
      isGrounded: hasSufficientEvidence,
      groundingReason,
      canProceed,
    };
  }

  /**
   * Generate questions grounded in evidence.
   * This method will fail if evidence grounding check fails (fail-closed).
   */
  async generateGroundedQuestions(
    request: QuestionGenerationRequest
  ): Promise<GroundedQuestion[]> {
    // First check evidence grounding
    const context = await this.checkEvidenceGrounding(request);

    if (!context.canProceed) {
      throw new Error(
        `Cannot generate questions: ${context.groundingReason}. ` +
        `This is a fail-closed safety measure to prevent hallucinated content.`
      );
    }

    this.logger.info(
      { topic: request.topic, evidenceCount: context.evidence.evidenceCount },
      'Generating evidence-grounded questions'
    );

    // Generate questions using the evidence
    const questions: GroundedQuestion[] = [];

    for (let i = 0; i < request.itemCount; i++) {
      const question = await this.generateSingleQuestion(request, context.evidence);
      questions.push(question);
    }

    this.logger.info(
      { questionCount: questions.length },
      'Evidence-grounded questions generated successfully'
    );

    return questions;
  }

  /**
   * Generate a single question grounded in evidence.
   */
  private async generateSingleQuestion(
    request: QuestionGenerationRequest,
    evidence: EvidenceGroundingResult
  ): Promise<GroundedQuestion> {
    // Select relevant evidence for this question
    const relevantEvidence = this.selectRelevantEvidence(evidence, request);

    // Generate question based on evidence (this would call an AI service in production)
    // For now, implement a template-based approach
    const templateQuestion = this.generateTemplateQuestion(request, relevantEvidence);

    // Calculate grounding score
    const groundingScore = this.calculateQuestionGroundingScore(templateQuestion, relevantEvidence);

    return {
      id: `q-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      question: templateQuestion.question,
      type: templateQuestion.type,
      options: templateQuestion.options,
      correctAnswer: templateQuestion.correctAnswer,
      explanation: templateQuestion.explanation,
      evidenceReferences: relevantEvidence.map(e => e.evidenceId),
      groundingScore,
      difficulty: request.difficulty === 'mixed' ? this.selectDifficulty() : request.difficulty,
    };
  }

  /**
   * Select the most relevant evidence for a question.
   */
  private selectRelevantEvidence(
    evidence: { evidenceItems: Array<{ evidenceId: string; text: string; similarityScore: number; claimRef: string }> },
    request: QuestionGenerationRequest
  ): Array<{ evidenceId: string; text: string; similarityScore: number; claimRef: string }> {
    // Sort by similarity score and take top items
    const sorted = [...evidence.evidenceItems].sort((a, b) => b.similarityScore - a.similarityScore);
    return sorted.slice(0, 3);
  }

  /**
   * Generate a template-based question from evidence.
   * In production, this would use an AI service.
   */
  private generateTemplateQuestion(
    request: QuestionGenerationRequest,
    evidence: Array<{ evidenceId: string; text: string; similarityScore: number; claimRef: string }>
  ): {
    question: string;
    type: 'multiple_choice' | 'short_answer' | 'true_false';
    options: string[];
    correctAnswer: string;
    explanation: string;
  } {
    const primaryEvidence = evidence[0];
    if (!primaryEvidence) {
      throw new Error('No evidence available for question generation');
    }

    const topic = request.topic;

    // Generate a multiple-choice question based on evidence
    return {
      question: `Based on the evidence about ${topic}, which of the following is true?`,
      type: 'multiple_choice',
      options: [
        primaryEvidence.text.substring(0, 100) + '...',
        `A common misconception about ${topic}`,
        `An unrelated fact about ${request.domain}`,
        `None of the above`,
      ],
      correctAnswer: primaryEvidence.text.substring(0, 100) + '...',
      explanation: `This is supported by evidence: ${primaryEvidence.text.substring(0, 200)}...`,
    };
  }

  /**
   * Calculate grounding score for a question.
   */
  private calculateQuestionGroundingScore(
    question: { question: string; type: string; options: string[]; correctAnswer: string; explanation: string },
    evidence: Array<{ similarityScore: number }>
  ): number {
    // Calculate based on evidence count and similarity scores
    const avgSimilarity = evidence.reduce((sum: number, e: { similarityScore: number }) => sum + e.similarityScore, 0) / evidence.length;
    const evidenceCoverage = Math.min(evidence.length / 3, 1); // Cap at 3 evidence items
    return (avgSimilarity * 0.7) + (evidenceCoverage * 0.3);
  }

  /**
   * Calculate grounding confidence based on evidence.
   */
  private calculateGroundingConfidence(evidence: Array<{ similarityScore: number }>): number {
    if (evidence.length === 0) return 0;

    const avgSimilarity = evidence.reduce((sum, e) => sum + e.similarityScore, 0) / evidence.length;
    const countBonus = Math.min(evidence.length / this.config.minimumEvidenceCount, 1) * 0.2;

    return Math.min(avgSimilarity + countBonus, 1);
  }

  /**
   * Identify gaps in evidence coverage.
   */
  private identifyEvidenceGaps(
    request: QuestionGenerationRequest,
    evidence: Array<{ text: string }>
  ): string[] {
    const gaps: string[] = [];
    const allText = evidence.map(e => e.text.toLowerCase()).join(' ');

    // Check for coverage of learning objectives
    for (const objective of request.learningObjectives) {
      const objectiveLower = objective.toLowerCase();
      if (!allText.includes(objectiveLower)) {
        gaps.push(`Insufficient evidence for learning objective: ${objective}`);
      }
    }

    // Check for coverage of claims
    if (request.claims) {
      for (const claim of request.claims) {
        const claimLower = claim.toLowerCase();
        if (!allText.includes(claimLower)) {
          gaps.push(`Insufficient evidence for claim: ${claim}`);
        }
      }
    }

    return gaps;
  }

  /**
   * Select a difficulty level randomly.
   */
  private selectDifficulty(): 'beginner' | 'intermediate' | 'advanced' {
    const difficulties: Array<'beginner' | 'intermediate' | 'advanced'> = ['beginner', 'intermediate', 'advanced'];
    const index = Math.floor(Math.random() * difficulties.length);
    return difficulties[index] ?? 'intermediate';
  }

  /**
   * Get the current configuration.
   */
  getConfig(): EvidenceGroundingConfig {
    return { ...this.config };
  }

  /**
   * Update the configuration.
   */
  updateConfig(config: Partial<EvidenceGroundingConfig>): void {
    this.config = { ...this.config, ...config };
    this.logger.info({ config: this.config }, 'Evidence grounding configuration updated');
  }
}
