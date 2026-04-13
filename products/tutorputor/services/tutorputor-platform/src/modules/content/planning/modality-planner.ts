/**
 * Modality Planner Service
 *
 * Task 3.3: Implement Modality Planner Service
 *
 * @doc.type module
 * @doc.purpose Unified planning for all content modalities
 * @doc.layer service
 * @doc.pattern Planner
 */

import type { Logger } from 'pino';
import type { PrismaClient } from '@tutorputor/core/db';
import type { EvidenceBundle } from '../../knowledge-base/evidence-bundle';

/**
 * Content needs plan for a claim.
 */
export interface ModalityPlan {
  claimRef: string;
  domain: string;
  gradeBand: string;

  examples: ExampleNeeds;
  animations: AnimationNeeds;
  simulations: SimulationNeeds;
  assessments: AssessmentNeeds;

  pedagogicalIntent: string;
  misconceptionCoverage: string[];
  gradeAdaptation: GradeAdaptationPlan;

  requiredValidation: ValidationRequirement[];
  autoPublishEligible: boolean;
}

export interface ExampleNeeds {
  required: boolean;
  count: number;
  families: Array<'real-world' | 'analogy' | 'worked-solution' | 'counterexample' | 'case-study'>;
  difficultyRange: [number, number];
}

export interface AnimationNeeds {
  required: boolean;
  count: number;
  typeHints: string[];
  pacingPreference: 'slow' | 'medium' | 'fast';
}

export interface SimulationNeeds {
  required: boolean;
  count: number;
  kernelDomains: string[];
  variantSupport: boolean;
}

export interface AssessmentNeeds {
  required: boolean;
  questionCount: number;
  types: Array<'multiple-choice' | 'open-ended' | 'interactive'>;
}

export interface GradeAdaptationPlan {
  scaffoldLevel: 'high' | 'medium' | 'low';
  languageComplexity: 'simple' | 'moderate' | 'advanced';
  visualAids: boolean;
}

export interface ValidationRequirement {
  type: 'peer-review' | 'expert-review' | 'automated';
  threshold: number;
}

/**
 * Service for planning content modalities.
 */
export class ModalityPlanner {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: Logger
  ) {}

  /**
   * Plan modalities for a claim based on evidence and domain.
   */
  async planForClaim(
    claimRef: string,
    evidenceBundle: EvidenceBundle,
    domain: string,
    gradeBand: string
  ): Promise<ModalityPlan> {
    this.logger.info({ claimRef, domain }, 'Planning modalities');

    const claim = await this.prisma.learningClaim.findFirst({
      where: { claimRef },
    });

    if (!claim) {
      throw new Error(`Claim not found: ${claimRef}`);
    }

    // Determine needs based on domain and claim type
    const needs = this.analyzeNeeds(domain, claim.text, gradeBand, evidenceBundle);

    // Check auto-publish eligibility
    const autoPublishEligible = this.checkAutoPublishEligibility(
      evidenceBundle,
      needs
    );

    return {
      claimRef,
      domain,
      gradeBand,
      ...needs,
      pedagogicalIntent: this.generatePedagogicalIntent(domain, claim.text),
      misconceptionCoverage: this.identifyMisconceptions(domain, claim.text),
      gradeAdaptation: this.planGradeAdaptation(gradeBand),
      requiredValidation: this.determineValidationNeeds(domain, autoPublishEligible),
      autoPublishEligible,
    };
  }

  /**
   * Analyze content needs based on domain and evidence.
   */
  private analyzeNeeds(
    domain: string,
    claimText: string,
    gradeBand: string,
    evidenceBundle: EvidenceBundle
  ): { examples: ExampleNeeds; animations: AnimationNeeds; simulations: SimulationNeeds; assessments: AssessmentNeeds } {
    const lowerDomain = domain.toLowerCase();

    // Domain-based heuristics
    const domainNeeds: Record<string, { examples: number; animations: number; simulations: number }> = {
      'physics': { examples: 3, animations: 2, simulations: 1 },
      'chemistry': { examples: 2, animations: 2, simulations: 2 },
      'biology': { examples: 3, animations: 1, simulations: 1 },
      'algebra': { examples: 4, animations: 1, simulations: 0 },
      'calculus': { examples: 3, animations: 2, simulations: 0 },
    };

    const needs = domainNeeds[lowerDomain] ?? { examples: 2, animations: 1, simulations: 0 };

    // Adjust based on grade band
    const isLowerGrade = ['K-2', '3-5', 'K-5', '6-8'].some(g => gradeBand.includes(g));
    if (isLowerGrade) {
      needs.animations += 1; // More visuals for younger students
    }

    return {
      examples: {
        required: needs.examples > 0,
        count: needs.examples,
        families: this.selectExampleFamilies(lowerDomain),
        difficultyRange: [0.3, 0.8],
      },
      animations: {
        required: needs.animations > 0,
        count: needs.animations,
        typeHints: this.selectAnimationTypes(lowerDomain),
        pacingPreference: isLowerGrade ? 'slow' : 'medium',
      },
      simulations: {
        required: needs.simulations > 0,
        count: needs.simulations,
        kernelDomains: [lowerDomain],
        variantSupport: true,
      },
      assessments: {
        required: true,
        questionCount: 3,
        types: ['multiple-choice', 'open-ended'],
      },
    };
  }

  /**
   * Select appropriate example families for a domain.
   */
  private selectExampleFamilies(domain: string): Array<'real-world' | 'analogy' | 'worked-solution' | 'counterexample' | 'case-study'> {
    const families: Record<string, Array<'real-world' | 'analogy' | 'worked-solution' | 'counterexample' | 'case-study'>> = {
      'physics': ['real-world', 'worked-solution', 'analogy'],
      'chemistry': ['real-world', 'worked-solution'],
      'biology': ['real-world', 'case-study'],
      'algebra': ['worked-solution', 'counterexample'],
      'calculus': ['worked-solution', 'real-world'],
    };

    return families[domain] ?? ['worked-solution'];
  }

  /**
   * Select appropriate animation types for a domain.
   */
  private selectAnimationTypes(domain: string): string[] {
    const types: Record<string, string[]> = {
      'physics': ['2d-motion', 'force-diagram', 'timeline'],
      'chemistry': ['molecular', 'reaction-timeline'],
      'biology': ['process', 'lifecycle'],
      'algebra': ['step-by-step', 'transformation'],
    };

    return types[domain] ?? ['2d'];
  }

  /**
   * Generate pedagogical intent statement.
   */
  private generatePedagogicalIntent(domain: string, claimText: string): string {
    return `Help learners understand ${claimText} through multiple representations and interactive experiences suitable for ${domain}.`;
  }

  /**
   * Identify common misconceptions for a domain.
   */
  private identifyMisconceptions(domain: string, claimText: string): string[] {
    const misconceptions: Record<string, string[]> = {
      'physics': ['Force requires motion', 'Heavier objects fall faster'],
      'algebra': ['Distributing incorrectly', 'Sign errors'],
      'chemistry': ['Chemical changes vs physical changes'],
    };

    return misconceptions[domain.toLowerCase()] ?? ['Overgeneralization'];
  }

  /**
   * Plan grade-specific adaptations.
   */
  private planGradeAdaptation(gradeBand: string): GradeAdaptationPlan {
    const isLowerGrade = ['K-2', '3-5', 'K-5'].some(g => gradeBand.includes(g));
    const isMiddleGrade = ['6-8', 'middle'].some(g => gradeBand.includes(g));

    return {
      scaffoldLevel: isLowerGrade ? 'high' : isMiddleGrade ? 'medium' : 'low',
      languageComplexity: isLowerGrade ? 'simple' : isMiddleGrade ? 'moderate' : 'advanced',
      visualAids: isLowerGrade || isMiddleGrade,
    };
  }

  /**
   * Determine validation requirements.
   */
  private determineValidationNeeds(domain: string, autoPublishEligible: boolean): ValidationRequirement[] {
    const requirements: ValidationRequirement[] = [
      { type: 'automated', threshold: 0.8 },
    ];

    // Sensitive domains require peer review
    if (['physics', 'chemistry', 'biology'].includes(domain.toLowerCase())) {
      requirements.push({ type: 'expert-review', threshold: 0.9 });
    }

    return requirements;
  }

  /**
   * Check if claim can be auto-published.
   */
  private checkAutoPublishEligibility(
    evidenceBundle: EvidenceBundle,
    needs: { examples: ExampleNeeds; animations: AnimationNeeds; simulations: SimulationNeeds; assessments: AssessmentNeeds }
  ): boolean {
    // Require high confidence from evidence
    if (evidenceBundle.bundleConfidence < 0.8) return false;

    // No contradictions
    if (evidenceBundle.contradictionDetected) return false;

    // Sufficient coverage
    if (evidenceBundle.coverageScore < 0.7) return false;

    return true;
  }
}
