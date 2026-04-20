/**
 * Evidence Bundle domain model for claim-level evidence collection.
 *
 * Implements Task 1.2: Create EvidenceBundle Aggregate
 *
 * @doc.type module
 * @doc.purpose Domain model for evidence bundle aggregate
 * @doc.layer domain
 * @doc.pattern AggregateRoot
 */

import type { Prisma } from '@tutorputor/core/db';

// =============================================================================
// Evidence Types (aligned with schema.prisma)
// =============================================================================

export type EvidenceSourceType =
  | 'OPENSTAX'
  | 'KHAN_ACADEMY'
  | 'WIKIPEDIA'
  | 'PEER_REVIEWED_JOURNAL'
  | 'TEXTBOOK'
  | 'CURRICULUM_STANDARD'
  | 'DOMAIN_EXPERT'
  | 'SIMULATION_RESULT'
  | 'CALCULATION';

export type SupportKind = 'SUPPORTS' | 'CONTRADICTS' | 'NEUTRAL' | 'PARTIALLY_SUPPORTS';

export type EvidenceFreshnessStatus = 'CURRENT' | 'STALE' | 'EXPIRED' | 'UNKNOWN';

export type EvidenceVerificationState = 'UNVERIFIED' | 'VERIFIED' | 'DISPUTED' | 'FAILED_VERIFICATION';

// =============================================================================
// Domain Types
// =============================================================================

/**
 * A single piece of evidence supporting a claim.
 */
export interface LearningEvidence {
  id: string;
  evidenceRef: string;
  claimRef: string;

  // Source identity
  sourceType: EvidenceSourceType;
  sourceUrl?: string;
  sourceTitle: string;
  sourcePublisher?: string;
  sourcePublicationDate?: Date;

  // Content
  excerpt?: string;
  structuredFact?: Record<string, unknown>;

  // Provenance
  supportKind: SupportKind;
  credibilityScore?: number;
  retrievedAt: Date;
  freshnessStatus: EvidenceFreshnessStatus;

  // Verification
  verificationState: EvidenceVerificationState;
  contradictionNotes?: string;
}

/**
 * Contradiction report generated when conflicts are detected.
 */
export interface ContradictionReport {
  hasContradictions: boolean;
  contradictions: Array<{
    evidenceARef: string;
    evidenceBRef: string;
    description: string;
    severity: 'HIGH' | 'MEDIUM' | 'LOW';
  }>;
}

/**
 * Coverage gap identified during analysis.
 */
export interface CoverageGap {
  aspect: string;
  severity: 'CRITICAL' | 'MAJOR' | 'MINOR';
  suggestedSourceTypes: EvidenceSourceType[];
}

/**
 * EvidenceBundle is the aggregate root containing all evidence for a claim.
 */
export interface EvidenceBundle {
  bundleId: string;
  claimRef: string;
  domain: string;
  gradeBand: string;

  evidences: LearningEvidence[];

  // Aggregated metrics
  bundleConfidence: number; // 0.0 - 1.0, aggregated from individual evidences
  coverageScore: number; // % of claim aspects covered by evidence
  coverageGaps: CoverageGap[];

  contradictionDetected: boolean;
  contradictionReport?: ContradictionReport;

  freshnessOverall: EvidenceFreshnessStatus;

  // Source distribution summary
  sourceDistribution: Record<EvidenceSourceType, number>;

  generatedAt: Date;
  regeneratedAt?: Date;
}

// =============================================================================
// Evidence Bundle Builder
// =============================================================================

/**
 * Builder for constructing EvidenceBundle aggregates.
 *
 * Usage:
 * ```typescript
 * const bundle = new EvidenceBundleBuilder('C1', 'physics', 'GRADE_9_12')
 *   .addEvidence(evidence1)
 *   .addEvidence(evidence2)
 *   .calculateCoverage()
 *   .detectContradictions()
 *   .build();
 * ```
 */
export class EvidenceBundleBuilder {
  private evidences: LearningEvidence[] = [];
  private coverageGaps: CoverageGap[] = [];
  private contradictionReport?: ContradictionReport;
  private coverageScore = 0;
  private bundleConfidence = 0;

  constructor(
    private readonly claimRef: string,
    private readonly domain: string,
    private readonly gradeBand: string,
    private readonly bundleId = `bundle-${claimRef}-${Date.now()}`
  ) {}

  /**
   * Add evidence to the bundle.
   */
  addEvidence(evidence: LearningEvidence): EvidenceBundleBuilder {
    this.evidences.push(evidence);
    return this;
  }

  /**
   * Add multiple evidences at once.
   */
  addEvidences(evidences: LearningEvidence[]): EvidenceBundleBuilder {
    this.evidences.push(...evidences);
    return this;
  }

  /**
   * Calculate coverage score based on how well evidence covers the claim.
   * This is a heuristic that can be enhanced with LLM-based analysis.
   */
  calculateCoverage(): EvidenceBundleBuilder {
    if (this.evidences.length === 0) {
      this.coverageScore = 0;
      this.coverageGaps = [{
        aspect: 'no_evidence',
        severity: 'CRITICAL',
        suggestedSourceTypes: this.getDefaultSourcesForDomain(),
      }];
      return this;
    }

    // Base coverage: each piece of evidence contributes to coverage
    const baseCoverage = Math.min(1.0, this.evidences.length / 3); // 3+ evidences = full base coverage

    // Quality multiplier: average credibility score
    const qualityScores = this.evidences
      .map(e => e.credibilityScore ?? 0.5)
      .filter(s => s > 0);
    const avgQuality = qualityScores.length > 0
      ? qualityScores.reduce((a, b) => a + b, 0) / qualityScores.length
      : 0.5;

    this.coverageScore = Math.round(baseCoverage * avgQuality * 100) / 100;

    // Identify gaps based on source diversity
    const sourceTypes = new Set(this.evidences.map(e => e.sourceType));
    const requiredSources = this.getDefaultSourcesForDomain();
    const missingSources = requiredSources.filter(s => !sourceTypes.has(s));

    if (missingSources.length > 0 && this.coverageScore < 0.8) {
      this.coverageGaps.push({
        aspect: 'source_diversity',
        severity: this.coverageScore < 0.5 ? 'MAJOR' : 'MINOR',
        suggestedSourceTypes: missingSources,
      });
    }

    // Check for freshness gaps
    const staleCount = this.evidences.filter(e => e.freshnessStatus === 'STALE').length;
    if (staleCount > 0) {
      this.coverageGaps.push({
        aspect: 'freshness',
        severity: staleCount > this.evidences.length / 2 ? 'MAJOR' : 'MINOR',
        suggestedSourceTypes: [],
      });
    }

    return this;
  }

  /**
   * Detect contradictions between evidence items.
   * Uses a simple heuristic; can be enhanced with semantic similarity.
   */
  detectContradictions(): EvidenceBundleBuilder {
    const contradictions: ContradictionReport['contradictions'] = [];

    for (let i = 0; i < this.evidences.length; i++) {
      for (let j = i + 1; j < this.evidences.length; j++) {
        const a = this.evidences[i];
        const b = this.evidences[j];
        if (!a || !b) {
          continue;
        }

        // Direct contradiction in support kind
        if (a.supportKind === 'SUPPORTS' && b.supportKind === 'CONTRADICTS') {
          contradictions.push({
            evidenceARef: a.evidenceRef,
            evidenceBRef: b.evidenceRef,
            description: `Contradiction: ${a.sourceTitle} supports while ${b.sourceTitle} contradicts`,
            severity: 'HIGH',
          });
        }

        // Already marked with contradiction notes
        if (a.contradictionNotes || b.contradictionNotes) {
          contradictions.push({
            evidenceARef: a.evidenceRef,
            evidenceBRef: b.evidenceRef,
            description: a.contradictionNotes || b.contradictionNotes || 'Known contradiction',
            severity: 'MEDIUM',
          });
        }
      }
    }

    this.contradictionReport = {
      hasContradictions: contradictions.length > 0,
      contradictions,
    };

    return this;
  }

  /**
   * Calculate overall bundle confidence from individual evidence scores.
   */
  calculateConfidence(): EvidenceBundleBuilder {
    if (this.evidences.length === 0) {
      this.bundleConfidence = 0;
      return this;
    }

    // Weight factors
    const credibilityWeight = 0.4;
    const freshnessWeight = 0.3;
    const verificationWeight = 0.3;

    const scores = this.evidences.map(e => {
      const credibility = e.credibilityScore ?? 0.5;

      const freshnessScores: Record<EvidenceFreshnessStatus, number> = {
        'CURRENT': 1.0,
        'UNKNOWN': 0.7,
        'STALE': 0.4,
        'EXPIRED': 0.1,
      };
      const freshness = freshnessScores[e.freshnessStatus];

      const verificationScores: Record<EvidenceVerificationState, number> = {
        'VERIFIED': 1.0,
        'UNVERIFIED': 0.6,
        'DISPUTED': 0.3,
        'FAILED_VERIFICATION': 0.1,
      };
      const verification = verificationScores[e.verificationState];

      return (credibility * credibilityWeight) +
             (freshness * freshnessWeight) +
             (verification * verificationWeight);
    });

    // Average across all evidences
    this.bundleConfidence = Math.round(
      (scores.reduce((a, b) => a + b, 0) / scores.length) * 100
    ) / 100;

    return this;
  }

  /**
   * Build the final EvidenceBundle.
   */
  build(): EvidenceBundle {
    // Ensure calculations have been run
    if (this.coverageScore === 0 && this.evidences.length > 0) {
      this.calculateCoverage();
    }
    if (!this.contradictionReport) {
      this.detectContradictions();
    }
    if (this.bundleConfidence === 0 && this.evidences.length > 0) {
      this.calculateConfidence();
    }

    // Calculate source distribution
    const sourceDistribution: Record<string, number> = {};
    for (const evidence of this.evidences) {
      sourceDistribution[evidence.sourceType] =
        (sourceDistribution[evidence.sourceType] || 0) + 1;
    }

    // Determine overall freshness (worst status)
    const freshnessPriority: EvidenceFreshnessStatus[] = ['EXPIRED', 'STALE', 'UNKNOWN', 'CURRENT'];
    const freshnessOverall = freshnessPriority.find(
      status => this.evidences.some(e => e.freshnessStatus === status)
    ) || 'CURRENT';

    const bundle: EvidenceBundle = {
      bundleId: this.bundleId,
      claimRef: this.claimRef,
      domain: this.domain,
      gradeBand: this.gradeBand,
      evidences: [...this.evidences],
      bundleConfidence: this.bundleConfidence,
      coverageScore: this.coverageScore,
      coverageGaps: [...this.coverageGaps],
      contradictionDetected: this.contradictionReport?.hasContradictions ?? false,
      freshnessOverall,
      sourceDistribution: sourceDistribution as Record<EvidenceSourceType, number>,
      generatedAt: new Date(),
    };

    if (this.contradictionReport) {
      bundle.contradictionReport = this.contradictionReport;
    }

    return bundle;
  }

  /**
   * Get default source types for the domain.
   */
  private getDefaultSourcesForDomain(): EvidenceSourceType[] {
    const domainSources: Record<string, EvidenceSourceType[]> = {
      'physics': ['OPENSTAX', 'KHAN_ACADEMY', 'PEER_REVIEWED_JOURNAL'],
      'algebra': ['KHAN_ACADEMY', 'OPENSTAX', 'CURRICULUM_STANDARD'],
      'math': ['KHAN_ACADEMY', 'OPENSTAX', 'CURRICULUM_STANDARD'],
      'science': ['OPENSTAX', 'WIKIPEDIA', 'PEER_REVIEWED_JOURNAL'],
      'default': ['KHAN_ACADEMY', 'OPENSTAX'],
    };

    const lowerDomain = this.domain.toLowerCase();
    return domainSources[lowerDomain] ?? ['KHAN_ACADEMY', 'OPENSTAX'];
  }
}

// =============================================================================
// Factory Functions
// =============================================================================

/**
 * Create an empty bundle for a claim.
 */
export function createEmptyBundle(
  claimRef: string,
  domain: string,
  gradeBand: string
): EvidenceBundle {
  return {
    bundleId: `bundle-${claimRef}-${Date.now()}`,
    claimRef,
    domain,
    gradeBand,
    evidences: [],
    bundleConfidence: 0,
    coverageScore: 0,
    coverageGaps: [{
      aspect: 'no_evidence',
      severity: 'CRITICAL',
      suggestedSourceTypes: ['OPENSTAX', 'KHAN_ACADEMY'],
    }],
    contradictionDetected: false,
    freshnessOverall: 'UNKNOWN',
    sourceDistribution: {} as Record<EvidenceSourceType, number>,
    generatedAt: new Date(),
  };
}

/**
 * Convert Prisma LearningEvidence to domain type.
 */
export function fromPrismaEvidence(
  prisma: Prisma.LearningEvidenceGetPayload<Record<string, never>>
): LearningEvidence {
  return {
    id: prisma.id,
    evidenceRef: prisma.evidenceRef,
    claimRef: prisma.claimRef,
    sourceType: prisma.sourceType as EvidenceSourceType,
    sourceTitle: prisma.sourceTitle,
    supportKind: prisma.supportKind as SupportKind,
    retrievedAt: prisma.retrievedAt,
    freshnessStatus: prisma.freshnessStatus as EvidenceFreshnessStatus,
    verificationState: prisma.verificationState as EvidenceVerificationState,
    ...(prisma.sourceUrl ? { sourceUrl: prisma.sourceUrl } : {}),
    ...(prisma.sourcePublisher ? { sourcePublisher: prisma.sourcePublisher } : {}),
    ...(prisma.sourcePublicationDate
      ? { sourcePublicationDate: prisma.sourcePublicationDate }
      : {}),
    ...(prisma.excerpt ? { excerpt: prisma.excerpt } : {}),
    ...(prisma.structuredFact
      ? { structuredFact: prisma.structuredFact as Record<string, unknown> }
      : {}),
    ...(prisma.credibilityScore != null
      ? { credibilityScore: prisma.credibilityScore }
      : {}),
    ...(prisma.contradictionNotes
      ? { contradictionNotes: prisma.contradictionNotes }
      : {}),
  };
}
