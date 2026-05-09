/**
 * Curriculum Standards Evidence Source Adapter
 *
 * Governed adapter for curriculum standards (CCSS, NGSS, etc.) with
 * rate limiting, caching, and proper error handling.
 *
 * @doc.type module
 * @doc.purpose Adapter for curriculum standards knowledge source
 * @doc.layer adapter
 * @doc.pattern Adapter
 */

import type {
  EvidenceSourceAdapter,
  EvidenceSearchResult,
  RetrievedContent,
} from './evidence-source-adapter';
import { EvidenceSourceError } from './evidence-source-adapter';
import type { EvidenceSourceType } from '../evidence-bundle';

/**
 * Configuration for curriculum standards adapter.
 */
export interface CurriculumStandardsConfig {
  cacheEnabled: boolean;
  rateLimitPerSecond: number;
}

/**
 * Standard definition.
 */
export interface StandardDefinition {
  id: string;
  standardId: string; // e.g., "CCSS.Math.Content.6.EE.A.1"
  description: string;
  gradeBand: string;
  domain: string;
  cluster: string;
}

/**
 * Mock standards database - in production, this would be a real database
 * or API integration with standards repositories.
 */
const STANDARDS_DATABASE: StandardDefinition[] = [
  {
    id: 'std-1',
    standardId: 'CCSS.Math.Content.6.EE.A.1',
    description: 'Write and evaluate numerical expressions involving whole-number exponents.',
    gradeBand: '6',
    domain: 'mathematics',
    cluster: 'Expressions and Equations',
  },
  {
    id: 'std-2',
    standardId: 'CCSS.Math.Content.6.EE.A.2',
    description: 'Write, read, and evaluate expressions in which letters stand for numbers.',
    gradeBand: '6',
    domain: 'mathematics',
    cluster: 'Expressions and Equations',
  },
  {
    id: 'std-3',
    standardId: 'NGSS.HS-PS1-1',
    description: 'Use the periodic table as a model to predict the relative properties of elements based on the patterns of electrons in the outermost energy level of atoms.',
    gradeBand: '9-12',
    domain: 'physics',
    cluster: 'Structure and Properties of Matter',
  },
  {
    id: 'std-4',
    standardId: 'NGSS.HS-LS1-1',
    description: 'Construct an explanation based on evidence for how the structure of DNA determines the structure of proteins, which carry out the essential functions of life through systems of specialized cells.',
    gradeBand: '9-12',
    domain: 'biology',
    cluster: 'Structure and Function',
  },
];

/**
 * Adapter for curriculum standards with governance controls.
 */
export class CurriculumStandardsAdapter implements EvidenceSourceAdapter {
  private requestTimestamps: number[] = [];
  private cache = new Map<string, RetrievedContent>();

  constructor(private readonly config: CurriculumStandardsConfig) {}

  getSourceType(): EvidenceSourceType {
    return 'CURRICULUM_STANDARD';
  }

  async search(
    query: string,
    domain: string,
    options?: { maxResults?: number; gradeBand?: string }
  ): Promise<EvidenceSearchResult[]> {
    try {
      await this.enforceRateLimit();

      const maxResults = options?.maxResults ?? 5;
      const gradeBand = options?.gradeBand;

      // Search standards database
      const results: EvidenceSearchResult[] = [];
      const queryLower = query.toLowerCase();
      const domainLower = domain.toLowerCase();

      for (const standard of STANDARDS_DATABASE) {
        // Filter by domain and grade band if specified
        if (domainLower && standard.domain.toLowerCase() !== domainLower) {
          continue;
        }

        if (gradeBand && !this.isGradeBandMatch(standard.gradeBand, gradeBand)) {
          continue;
        }

        // Check relevance
        const descriptionLower = standard.description.toLowerCase();
        const standardIdLower = standard.standardId.toLowerCase();

        if (
          descriptionLower.includes(queryLower) ||
          standardIdLower.includes(queryLower) ||
          queryLower.includes(standard.domain.toLowerCase())
        ) {
          results.push({
            sourceUrl: `https://www.nextgenscience.org/${standard.standardId}`,
            title: `${standard.standardId}: ${standard.description.substring(0, 50)}...`,
            publisher: this.getPublisher(standard.standardId),
            excerpt: standard.description,
            relevanceScore: this.calculateRelevance(standard, queryLower),
          });
        }

        if (results.length >= maxResults) {
          break;
        }
      }

      return results;
    } catch (error) {
      throw new EvidenceSourceError(
        error instanceof Error ? error.message : 'Unknown error',
        'CURRICULUM_STANDARD',
        'search',
        error instanceof Error ? error : undefined
      );
    }
  }

  async retrieveContent(url: string): Promise<RetrievedContent | null> {
    try {
      // Check cache first
      if (this.config.cacheEnabled) {
        const cached = this.cache.get(url);
        if (cached) return cached;
      }

      await this.enforceRateLimit();

      // Extract standard ID from URL
      const standardIdMatch = url.match(/([^/]+)$/);
      if (!standardIdMatch) {
        return null;
      }

      const standardId = standardIdMatch[1];
      const standard = STANDARDS_DATABASE.find(s => s.standardId === standardId);

      if (!standard) {
        return null;
      }

      const content: RetrievedContent = {
        url,
        title: `${standard.standardId}: ${standard.description}`,
        publisher: this.getPublisher(standard.standardId),
        content: JSON.stringify(standard, null, 2),
        qualityIndicators: {
          isFeatured: true,
          citationCount: 0,
        },
      };

      if (this.config.cacheEnabled) {
        this.cache.set(url, content);
      }

      return content;
    } catch (error) {
      throw new EvidenceSourceError(
        error instanceof Error ? error.message : 'Unknown error',
        'CURRICULUM_STANDARD',
        'retrieve',
        error instanceof Error ? error : undefined
      );
    }
  }

  async healthCheck(): Promise<boolean> {
    // Standards database is always available
    return true;
  }

  /**
   * Enforce rate limiting.
   */
  private async enforceRateLimit(): Promise<void> {
    const now = Date.now();
    const oneSecondAgo = now - 1000;

    // Remove timestamps older than 1 second
    this.requestTimestamps = this.requestTimestamps.filter(t => t > oneSecondAgo);

    // If at limit, wait
    if (this.requestTimestamps.length >= this.config.rateLimitPerSecond) {
      const oldestTimestamp = this.requestTimestamps[0] ?? now;
      const waitTime = 1000 - (now - oldestTimestamp);
      if (waitTime > 0) {
        await new Promise(resolve => setTimeout(resolve, waitTime));
      }
    }

    this.requestTimestamps.push(now);
  }

  /**
   * Check if grade band matches.
   */
  private isGradeBandMatch(standardGradeBand: string, requestedGradeBand: string): boolean {
    const standardGrades = this.parseGradeBand(standardGradeBand);
    const requestedGrades = this.parseGradeBand(requestedGradeBand);

    // Check for overlap
    return standardGrades.some(g => requestedGrades.includes(g));
  }

  /**
   * Parse grade band string into array of grades.
   */
  private parseGradeBand(gradeBand: string): string[] {
    if (gradeBand.includes('-')) {
      const parts = gradeBand.split('-');
      const start = Number(parts[0]);
      const end = Number(parts[1]);
      const grades: string[] = [];
      for (let i = start; i <= end; i++) {
        grades.push(String(i));
      }
      return grades;
    }
    return [gradeBand];
  }

  /**
   * Get publisher based on standard ID.
   */
  private getPublisher(standardId: string): string {
    if (standardId.startsWith('CCSS')) {
      return 'Common Core State Standards Initiative';
    }
    if (standardId.startsWith('NGSS')) {
      return 'Next Generation Science Standards';
    }
    return 'Curriculum Standards Repository';
  }

  /**
   * Calculate relevance score.
   */
  private calculateRelevance(standard: StandardDefinition, queryLower: string): number {
    let score = 0.5; // Base score

    const descriptionLower = standard.description.toLowerCase();
    const standardIdLower = standard.standardId.toLowerCase();

    // Boost for exact match in description
    if (descriptionLower.includes(queryLower)) {
      score += 0.3;
    }

    // Boost for match in standard ID
    if (standardIdLower.includes(queryLower)) {
      score += 0.2;
    }

    return Math.min(score, 1.0);
  }
}
