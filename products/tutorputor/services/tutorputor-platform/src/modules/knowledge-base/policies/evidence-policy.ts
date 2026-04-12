/**
 * Evidence Policy System
 *
 * Task 1.7: Define Domain-Specific Evidence Policies
 *
 * @doc.type module
 * @doc.purpose Policy configuration for evidence requirements
 * @doc.layer policy
 * @doc.pattern PolicyEngine
 */

import type { EvidenceSourceType, EvidenceBundle } from '../evidence-bundle';

/**
 * Evidence policy for a domain/grade.
 */
export interface EvidencePolicy {
  /** Domain this policy applies to */
  domain: string;
  /** Optional grade band filter */
  gradeBand?: string;
  /** Minimum number of evidence items required */
  minimumEvidenceCount: number;
  /** Required source types */
  requiredSourceTypes: EvidenceSourceType[];
  /** Minimum credibility score (0-1) */
  minimumCredibilityScore: number;
  /** Freshness requirement */
  freshnessRequirement: 'CURRENT' | 'STALE' | 'EXPIRED' | 'UNKNOWN';
  /** Whether Wikipedia is allowed */
  allowWikipedia: boolean;
  /** Whether curriculum alignment is required */
  curriculumAlignmentRequired: boolean;
  /** Minimum coverage score (0-1) */
  minimumCoverageScore: number;
  /** Whether contradictions are allowed */
  allowContradictions: boolean;
}

/**
 * Result of policy evaluation.
 */
export interface PolicyEvaluationResult {
  passed: boolean;
  score: number; // 0-1
  violations: PolicyViolation[];
  recommendations: string[];
}

/**
 * Policy violation.
 */
export interface PolicyViolation {
  type: 'count' | 'source' | 'credibility' | 'freshness' | 'coverage' | 'contradiction';
  severity: 'CRITICAL' | 'MAJOR' | 'MINOR';
  message: string;
  details?: Record<string, unknown>;
}

/**
 * Default policies for pilot domains.
 */
export const DEFAULT_POLICIES: EvidencePolicy[] = [
  // STEM - Physics
  {
    domain: 'physics',
    minimumEvidenceCount: 3,
    requiredSourceTypes: ['OPENSTAX', 'KHAN_ACADEMY'],
    minimumCredibilityScore: 0.8,
    freshnessRequirement: 'CURRENT',
    allowWikipedia: false,
    curriculumAlignmentRequired: true,
    minimumCoverageScore: 0.7,
    allowContradictions: false,
  },
  // Math - Algebra
  {
    domain: 'algebra',
    minimumEvidenceCount: 3,
    requiredSourceTypes: ['KHAN_ACADEMY', 'OPENSTAX', 'CURRICULUM_STANDARD'],
    minimumCredibilityScore: 0.8,
    freshnessRequirement: 'CURRENT',
    allowWikipedia: false,
    curriculumAlignmentRequired: true,
    minimumCoverageScore: 0.7,
    allowContradictions: false,
  },
  // General Science
  {
    domain: 'science',
    minimumEvidenceCount: 3,
    requiredSourceTypes: ['OPENSTAX', 'KHAN_ACADEMY'],
    minimumCredibilityScore: 0.7,
    freshnessRequirement: 'STALE',
    allowWikipedia: true,
    curriculumAlignmentRequired: true,
    minimumCoverageScore: 0.6,
    allowContradictions: false,
  },
  // Default fallback
  {
    domain: 'default',
    minimumEvidenceCount: 2,
    requiredSourceTypes: ['KHAN_ACADEMY', 'OPENSTAX'],
    minimumCredibilityScore: 0.6,
    freshnessRequirement: 'STALE',
    allowWikipedia: true,
    curriculumAlignmentRequired: false,
    minimumCoverageScore: 0.5,
    allowContradictions: true,
  },
];

/**
 * Service for evaluating evidence policies.
 */
export class EvidencePolicyEvaluator {
  private policies: Map<string, EvidencePolicy> = new Map();

  constructor(policies: EvidencePolicy[] = DEFAULT_POLICIES) {
    for (const policy of policies) {
      this.policies.set(policy.domain.toLowerCase(), policy);
    }
  }

  /**
   * Evaluate a bundle against policy.
   */
  evaluate(bundle: EvidenceBundle, domain: string): PolicyEvaluationResult {
    const policy = this.getPolicy(domain);
    const violations: PolicyViolation[] = [];
    const recommendations: string[] = [];

    // Check evidence count
    if (bundle.evidences.length < policy.minimumEvidenceCount) {
      violations.push({
        type: 'count',
        severity: 'CRITICAL',
        message: `Insufficient evidence: ${bundle.evidences.length} < ${policy.minimumEvidenceCount}`,
        details: { actual: bundle.evidences.length, required: policy.minimumEvidenceCount },
      });
      recommendations.push(`Add ${policy.minimumEvidenceCount - bundle.evidences.length} more evidence sources`);
    }

    // Check required source types
    const sourceTypes = new Set(bundle.evidences.map(e => e.sourceType));
    const missingSources = policy.requiredSourceTypes.filter(
      st => !sourceTypes.has(st)
    );
    if (missingSources.length > 0) {
      violations.push({
        type: 'source',
        severity: 'MAJOR',
        message: `Missing required source types: ${missingSources.join(', ')}`,
        details: { missing: missingSources },
      });
      recommendations.push(`Search for evidence from: ${missingSources.join(', ')}`);
    }

    // Check credibility scores
    const lowCredibility = bundle.evidences.filter(
      e => (e.credibilityScore ?? 0) < policy.minimumCredibilityScore
    );
    if (lowCredibility.length > 0) {
      violations.push({
        type: 'credibility',
        severity: 'MINOR',
        message: `${lowCredibility.length} evidence items below credibility threshold`,
        details: { threshold: policy.minimumCredibilityScore, items: lowCredibility.map(e => e.evidenceRef) },
      });
      recommendations.push('Replace low-credibility sources with peer-reviewed content');
    }

    // Check freshness
    const freshnessPriority = ['EXPIRED', 'STALE', 'UNKNOWN', 'CURRENT'];
    const currentIndex = freshnessPriority.indexOf(policy.freshnessRequirement);
    const bundleIndex = freshnessPriority.indexOf(bundle.freshnessOverall);
    if (bundleIndex < currentIndex) {
      violations.push({
        type: 'freshness',
        severity: 'MAJOR',
        message: `Bundle freshness ${bundle.freshnessOverall} below requirement ${policy.freshnessRequirement}`,
        details: { actual: bundle.freshnessOverall, required: policy.freshnessRequirement },
      });
      recommendations.push('Run freshness check to update stale evidence');
    }

    // Check coverage
    if (bundle.coverageScore < policy.minimumCoverageScore) {
      violations.push({
        type: 'coverage',
        severity: 'MAJOR',
        message: `Coverage score ${bundle.coverageScore} below threshold ${policy.minimumCoverageScore}`,
        details: { actual: bundle.coverageScore, required: policy.minimumCoverageScore },
      });
      recommendations.push('Add evidence covering uncovered aspects of the claim');
    }

    // Check contradictions
    if (bundle.contradictionDetected && !policy.allowContradictions) {
      violations.push({
        type: 'contradiction',
        severity: 'CRITICAL',
        message: 'Contradictions detected in evidence bundle',
        details: { contradictions: bundle.contradictionReport },
      });
      recommendations.push('Resolve contradictions through human review');
    }

    // Calculate overall score
    const score = this.calculateScore(bundle, policy, violations.length);

    return {
      passed: violations.length === 0 && score >= 0.8,
      score,
      violations,
      recommendations,
    };
  }

  /**
   * Get policy for a domain.
   */
  private getPolicy(domain: string): EvidencePolicy {
    const lowerDomain = domain.toLowerCase();
    return this.policies.get(lowerDomain) ?? this.policies.get('default')!;
  }

  /**
   * Calculate overall policy score.
   */
  private calculateScore(bundle: EvidenceBundle, policy: EvidencePolicy, violationCount: number): number {
    let score = 1.0;

    // Deduct for violations
    score -= violationCount * 0.2;

    // Bonus for exceeding minimums
    if (bundle.evidences.length > policy.minimumEvidenceCount) {
      score += 0.05;
    }
    if (bundle.coverageScore > policy.minimumCoverageScore) {
      score += 0.05;
    }
    if (bundle.bundleConfidence > 0.8) {
      score += 0.05;
    }

    return Math.max(0, Math.min(1, score));
  }

  /**
   * Add or update a policy.
   */
  setPolicy(policy: EvidencePolicy): void {
    this.policies.set(policy.domain.toLowerCase(), policy);
  }

  /**
   * Get all registered policies.
   */
  getAllPolicies(): EvidencePolicy[] {
    return Array.from(this.policies.values());
  }
}
