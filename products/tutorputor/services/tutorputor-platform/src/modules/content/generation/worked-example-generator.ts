/**
 * Worked Example Generator
 *
 * Task 2.6: Implement Worked Example Generator
 *
 * @doc.type module
 * @doc.purpose Service for generating worked example manifests from claims and evidence
 * @doc.layer service
 * @doc.pattern GeneratorService
 */

import type { Logger } from 'pino';
import type { PrismaClient } from '@tutorputor/core/db';
import type {
  WorkedExampleManifest,
  ExampleFamily,
} from '../../../../../../contracts/v1/artifact-manifests/worked-example-manifest';
import type { EvidenceBundle } from '../../knowledge-base/evidence-bundle';
import { ManifestValidator } from '../manifest-validator';

function toManifestValue(value: unknown): string | number | boolean {
  if (
    typeof value === 'string' ||
    typeof value === 'number' ||
    typeof value === 'boolean'
  ) {
    return value;
  }
  return value == null ? '' : JSON.stringify(value);
}

/**
 * Options for worked example generation.
 */
export interface WorkedExampleGenerationOptions {
  exampleFamily?: ExampleFamily;
  targetDifficulty?: number; // 0-1
  maxSteps?: number;
  includeMisconceptions?: boolean;
  includeTransferPrompts?: boolean;
}

/**
 * Result of worked example generation.
 */
export interface WorkedExampleGenerationResult {
  manifest: WorkedExampleManifest;
  success: boolean;
  errors: string[];
  warnings: string[];
}

/**
 * Service for generating worked example manifests.
 */
export class WorkedExampleGenerator {
  private readonly validator: ManifestValidator;

  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: Logger
  ) {
    this.validator = new ManifestValidator(logger);
  }

  /**
   * Generate a worked example manifest for a claim.
   */
  async generate(
    claimRef: string,
    evidenceBundle: EvidenceBundle,
    domain: string,
    gradeBand: string,
    options?: WorkedExampleGenerationOptions
  ): Promise<WorkedExampleGenerationResult> {
    const errors: string[] = [];
    const warnings: string[] = [];

    try {
      // Get claim details
      const claim = await this.prisma.learningClaim.findFirst({
        where: { claimRef },
        include: { evidences: true },
      });

      if (!claim) {
        return {
          manifest: this.createEmptyManifest(claimRef),
          success: false,
          errors: [`Claim not found: ${claimRef}`],
          warnings: [],
        };
      }

      // Determine example family
      const exampleFamily = options?.exampleFamily ?? this.inferExampleFamily(domain, claim.text);

      // Generate learner goal
      const learnerGoal = this.generateLearnerGoal(claim.text, exampleFamily);

      // Extract givens from evidence
      const givens = this.extractGivens(evidenceBundle);

      // Generate reasoning steps
      const reasoningSteps = this.generateReasoningSteps(
        claim.text,
        evidenceBundle,
        options?.maxSteps ?? 5
      );

      // Generate explanation steps
      const explanationSteps = this.generateExplanationSteps(reasoningSteps);

      // Generate misconception checkpoints
      const misconceptionCheckpoints = options?.includeMisconceptions !== false
        ? this.generateMisconceptionCheckpoints(domain, claim.text)
        : [];

      // Generate transfer prompts
      const transferPrompts = options?.includeTransferPrompts !== false
        ? this.generateTransferPrompts(claim.text, domain)
        : [];

      // Build manifest
      const manifest: WorkedExampleManifest = {
        schemaVersion: '1.0.0',
        manifestType: 'WorkedExample',
        claimRef,
        evidenceRefs: evidenceBundle.evidences.map(e => e.evidenceRef),
        domain,
        gradeBand,
        pedagogicalIntent: this.generatePedagogicalIntent(exampleFamily),
        exampleFamily,
        learnerGoal,
        givens,
        reasoningSteps,
        explanationSteps,
        misconceptionCheckpoints,
        transferPrompts,
        adaptationRules: this.generateGradeAdaptations(gradeBand),
        difficultyEstimate: options?.targetDifficulty ?? 0.5,
        estimatedTimeMinutes: this.estimateTime(reasoningSteps.length),
        prerequisites: this.extractPrerequisites(claim.text),
        evaluationHints: this.generateEvaluationHints(reasoningSteps),
        createdAt: new Date().toISOString(),
        generatedBy: 'WorkedExampleGenerator',
        validationStatus: 'pending',
      };

      // Validate
      const validation = this.validator.validateWorkedExample(manifest);
      warnings.push(...validation.warnings.map(w => w.message));

      this.logger.info({ claimRef, exampleFamily }, 'Worked example generated');

      return {
        manifest,
        success: true,
        errors,
        warnings,
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      this.logger.error({ err: error, claimRef }, 'Worked example generation failed');

      return {
        manifest: this.createEmptyManifest(claimRef),
        success: false,
        errors: [...errors, message],
        warnings,
      };
    }
  }

  /**
   * Infer the appropriate example family for a domain and claim.
   */
  private inferExampleFamily(domain: string, claimText: string): ExampleFamily {
    const lowerDomain = domain.toLowerCase();
    const lowerClaim = claimText.toLowerCase();

    // Math domains tend toward worked solutions
    if (['math', 'algebra', 'calculus', 'geometry'].includes(lowerDomain)) {
      return 'worked-solution';
    }

    // Physics and chemistry benefit from real-world examples
    if (['physics', 'chemistry'].includes(lowerDomain)) {
      if (lowerClaim.includes('formula') || lowerClaim.includes('equation')) {
        return 'worked-solution';
      }
      return 'real-world';
    }

    // Biology and earth science often use analogies
    if (['biology', 'earth science'].includes(lowerDomain)) {
      if (lowerClaim.includes('like') || lowerClaim.includes('similar to')) {
        return 'analogy';
      }
      return 'real-world';
    }

    // Default to worked solution
    return 'worked-solution';
  }

  /**
   * Generate a learner goal statement.
   */
  private generateLearnerGoal(claimText: string, exampleFamily: ExampleFamily): string {
    const templates: Record<ExampleFamily, string> = {
      'real-world': `Apply the concept that "${claimText}" to real-world situations`,
      'analogy': `Understand "${claimText}" through familiar comparisons`,
      'worked-solution': `Solve problems involving "${claimText}" step by step`,
      'counterexample': `Understand why "${claimText}" by examining edge cases`,
      'case-study': `Analyze how "${claimText}" applies in specific contexts`,
    };

    const fallbackGoal = `Solve problems involving "${claimText}" step by step`;
    return templates[exampleFamily] ?? fallbackGoal;
  }

  /**
   * Extract givens from evidence bundle.
   */
  private extractGivens(bundle: EvidenceBundle): Array<{ id: string; description: string; value: string | number | boolean; unit?: string }> {
    return bundle.evidences.slice(0, 3).map((e, i) => ({
      id: `given-${i + 1}`,
      description: e.excerpt?.substring(0, 100) ?? `Evidence ${e.evidenceRef}`,
      value: toManifestValue(e.structuredFact?.value ?? e.sourceTitle ?? ''),
    }));
  }

  /**
   * Generate reasoning steps.
   */
  private generateReasoningSteps(claimText: string, bundle: EvidenceBundle, maxSteps: number): Array<{ stepNumber: number; description: string; checkpoint: boolean; hint?: string }> {
    const steps: Array<{ stepNumber: number; description: string; checkpoint: boolean; hint?: string }> = [];

    // Step 1: Understand the problem
    steps.push({
      stepNumber: 1,
      description: `Identify what we need to understand about: ${claimText}`,
      checkpoint: true,
      hint: 'Read the claim carefully and identify key terms',
    });

    // Step 2: Gather information from evidence
    if (bundle.evidences.length > 0) {
      steps.push({
        stepNumber: 2,
        description: `Review the evidence: ${bundle.evidences[0]?.excerpt?.substring(0, 80) ?? 'Available evidence'}...`,
        checkpoint: false,
        hint: 'Note the key facts and their sources',
      });
    }

    // Step 3: Apply reasoning
    steps.push({
      stepNumber: steps.length + 1,
      description: 'Connect the evidence to the claim through logical reasoning',
      checkpoint: true,
      hint: 'How does the evidence support the claim?',
    });

    // Step 4: Draw conclusion
    steps.push({
      stepNumber: steps.length + 1,
      description: `Conclude that ${claimText} is supported by the evidence`,
      checkpoint: true,
    });

    return steps.slice(0, maxSteps);
  }

  /**
   * Generate explanation steps.
   */
  private generateExplanationSteps(reasoningSteps: Array<{ stepNumber: number; description: string }>): Array<{ stepNumber: number; content: string; emphasizes?: string[] }> {
    return reasoningSteps.map(step => ({
      stepNumber: step.stepNumber,
      content: `Explanation for step ${step.stepNumber}: ${step.description}. This step helps build understanding by breaking down the problem into manageable parts.`,
      emphasizes: ['key concept', 'reasoning'],
    }));
  }

  /**
   * Generate misconception checkpoints.
   */
  private generateMisconceptionCheckpoints(domain: string, claimText: string): Array<{ id: string; commonError: string; warningSign: string; correctiveGuidance: string; relatedStepNumber?: number }> {
    const domainMisconceptions: Record<string, Array<{ error: string; warning: string; guidance: string }>> = {
      'physics': [
        { error: 'Confusing velocity and acceleration', warning: 'Using v and a interchangeably', guidance: 'Velocity is rate of position change; acceleration is rate of velocity change' },
        { error: 'Forces require motion', warning: 'Thinking objects at rest have no forces', guidance: 'Forces can balance to produce equilibrium without motion' },
      ],
      'algebra': [
        { error: 'Distributing incorrectly', warning: 'a(b+c) = ab + c', guidance: 'Multiply every term inside parentheses by the factor outside' },
        { error: 'Sign errors with negatives', warning: '(-a)² = -a²', guidance: 'Square the entire quantity; (-a)² = a²' },
      ],
    };

    const misconceptions = domainMisconceptions[domain.toLowerCase()] ?? [
      { error: 'Overgeneralizing the concept', warning: 'Applying claim to all cases', guidance: 'Consider the specific conditions where this applies' },
    ];

    return misconceptions.slice(0, 2).map((m, i) => ({
      id: `misconception-${i + 1}`,
      commonError: m.error,
      warningSign: m.warning,
      correctiveGuidance: m.guidance,
      relatedStepNumber: i + 1,
    }));
  }

  /**
   * Generate transfer prompts.
   */
  private generateTransferPrompts(claimText: string, domain: string): Array<{ id: string; prompt: string; expectedAnswer?: string; hints?: string[] }> {
    return [
      {
        id: 'transfer-1',
        prompt: `In a different context from ${domain}, how might the principle "${claimText}" apply?`,
        expectedAnswer: 'Application to a new domain',
        hints: ['Think about similar situations', 'Identify the core principle'],
      },
      {
        id: 'transfer-2',
        prompt: 'What would happen if the conditions in this example changed slightly?',
        expectedAnswer: 'Analysis of variation',
        hints: ['Consider edge cases', 'Test boundary conditions'],
      },
    ];
  }

  /**
   * Generate grade adaptation rules.
   */
  private generateGradeAdaptations(gradeBand: string): Array<{ gradeBand: string; modifications: { simplifyLanguage?: boolean; addScaffolding?: boolean; reduceSteps?: boolean; addVisualAids?: boolean } }> {
    return [
      {
        gradeBand: 'K-5',
        modifications: { simplifyLanguage: true, addScaffolding: true, addVisualAids: true },
      },
      {
        gradeBand: '6-8',
        modifications: { addScaffolding: true, reduceSteps: false },
      },
      {
        gradeBand: '9-12',
        modifications: { simplifyLanguage: false, addScaffolding: false },
      },
    ];
  }

  /**
   * Estimate time based on step count.
   */
  private estimateTime(stepCount: number): number {
    return Math.max(3, stepCount * 2);
  }

  /**
   * Extract prerequisites from claim text.
   */
  private extractPrerequisites(claimText: string): string[] {
    // Simple heuristic: extract key terms as prerequisites
    const commonPrereqs = ['basic understanding', 'foundational concepts'];
    return commonPrereqs;
  }

  /**
   * Generate evaluation hints.
   */
  private generateEvaluationHints(reasoningSteps: Array<{ stepNumber: number; description: string }>): { correctIndicators: string[]; misconceptionIndicators: string[]; followUpQuestions?: string[] } {
    return {
      correctIndicators: reasoningSteps.map(s => `Completed step ${s.stepNumber} correctly`),
      misconceptionIndicators: ['Skipping steps', 'Incorrect reasoning', 'Missing key concepts'],
      followUpQuestions: ['Can you explain your reasoning?', 'What would happen if we changed this?'],
    };
  }

  /**
   * Generate pedagogical intent.
   */
  private generatePedagogicalIntent(exampleFamily: ExampleFamily): string {
    const intents: Record<ExampleFamily, string> = {
      'real-world': 'Connect abstract concepts to concrete applications',
      'analogy': 'Build understanding through familiar comparisons',
      'worked-solution': 'Demonstrate problem-solving process explicitly',
      'counterexample': 'Clarify boundaries of concepts through contrast',
      'case-study': 'Deepen understanding through contextual analysis',
    };

    return intents[exampleFamily] ?? 'Demonstrate problem-solving process explicitly';
  }

  /**
   * Create an empty manifest for error cases.
   */
  private createEmptyManifest(claimRef: string): WorkedExampleManifest {
    return {
      schemaVersion: '1.0.0',
      manifestType: 'WorkedExample',
      claimRef,
      evidenceRefs: [],
      domain: '',
      gradeBand: '',
      pedagogicalIntent: '',
      exampleFamily: 'worked-solution',
      learnerGoal: '',
      givens: [],
      reasoningSteps: [],
      explanationSteps: [],
      misconceptionCheckpoints: [],
      transferPrompts: [],
      adaptationRules: [],
      difficultyEstimate: 0.5,
      estimatedTimeMinutes: 5,
      prerequisites: [],
      evaluationHints: { correctIndicators: [], misconceptionIndicators: [] },
    };
  }
}
