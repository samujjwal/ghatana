/**
 * Evidence Bundle Generator Service
 *
 * Task 1.4: Build Evidence Bundle Generator Service
 *
 * @doc.type module
 * @doc.purpose Service that generates complete evidence bundles for claims
 * @doc.layer service
 * @doc.pattern Service
 */

import type { Logger } from 'pino';
import {
  EvidenceBundle,
  EvidenceBundleBuilder,
  LearningEvidence,
  type EvidenceSourceType,
  type SupportKind,
} from './evidence-bundle';
import type { EvidenceSourceAdapter, EvidenceSearchResult } from './adapters/evidence-source-adapter';
import type { PrismaClient } from '@tutorputor/core/db';

/**
 * Options for bundle generation.
 */
export interface BundleGenerationOptions {
  /** Minimum number of evidence items to collect */
  minimumEvidenceCount?: number;
  /** Maximum number of evidence items to collect */
  maximumEvidenceCount?: number;
  /** Required source types for the bundle */
  requiredSourceTypes?: EvidenceSourceType[];
  /** Timeout in milliseconds for the entire generation process */
  timeoutMs?: number;
}

/**
 * Result of bundle generation.
 */
export interface BundleGenerationResult {
  bundle: EvidenceBundle;
  success: boolean;
  errors: string[];
  sourcesQueried: EvidenceSourceType[];
  totalResultsFound: number;
}

/**
 * Service for generating evidence bundles for learning claims.
 */
export class EvidenceBundleGenerator {
  private readonly adapters: Map<EvidenceSourceType, EvidenceSourceAdapter>;
  private readonly defaultOptions: BundleGenerationOptions = {
    minimumEvidenceCount: 3,
    maximumEvidenceCount: 10,
    timeoutMs: 30000,
  };

  constructor(
    adapters: EvidenceSourceAdapter[],
    private readonly prisma: PrismaClient,
    private readonly logger: Logger
  ) {
    this.adapters = new Map();
    for (const adapter of adapters) {
      this.adapters.set(adapter.getSourceType(), adapter);
    }
  }

  /**
   * Generate an evidence bundle for a claim.
   *
   * Flow:
   * 1. Extract key assertions from claim text (heuristic)
   * 2. Query multiple evidence sources in parallel
   * 3. Score and rank evidence
   * 4. Detect contradictions
   * 5. Package into EvidenceBundle
   */
  async generateForClaim(
    claimRef: string,
    claimText: string,
    domain: string,
    gradeBand: string,
    options?: BundleGenerationOptions
  ): Promise<BundleGenerationResult> {
    const opts = { ...this.defaultOptions, ...options };
    const errors: string[] = [];
    const sourcesQueried: EvidenceSourceType[] = [];
    let totalResultsFound = 0;

    this.logger.info(
      { claimRef, domain, gradeBand },
      'Starting evidence bundle generation'
    );

    try {
      // Step 1: Extract key assertions (heuristic for now)
      const searchQueries = this.extractSearchQueries(claimText, domain);

      // Step 2: Query sources in parallel with timeout
      const searchPromises = Array.from(this.adapters.values()).map(async (adapter) => {
        const sourceType = adapter.getSourceType();
        sourcesQueried.push(sourceType);

        try {
          const results = await Promise.race([
            this.queryAdapter(adapter, searchQueries, domain, gradeBand),
            this.createTimeoutPromise<EvidenceSearchResult[]>(opts.timeoutMs!, []),
          ]);

          return { sourceType, results, error: null };
        } catch (error) {
          const message = error instanceof Error ? error.message : 'Unknown error';
          errors.push(`[${sourceType}] ${message}`);
          return { sourceType, results: [] as EvidenceSearchResult[], error: message };
        }
      });

      const searchResults = await Promise.all(searchPromises);

      // Step 3: Collect and rank evidence
      const allEvidence: LearningEvidence[] = [];
      let evidenceCounter = 1;

      for (const { sourceType, results } of searchResults) {
        totalResultsFound += results.length;

        for (const result of results.slice(0, 3)) { // Top 3 per source
          try {
            const adapter = this.adapters.get(sourceType);
            if (!adapter) continue;

            const content = await adapter.retrieveContent(result.sourceUrl);
            if (!content) continue;

            const evidence: LearningEvidence = {
              id: `ev-${Date.now()}-${evidenceCounter}`,
              evidenceRef: `E${evidenceCounter}`,
              claimRef,
              sourceType,
              sourceUrl: result.sourceUrl,
              sourceTitle: content.title,
              excerpt: content.content.substring(0, 500), // First 500 chars
              supportKind: this.detectSupportKind(content.content, claimText),
              credibilityScore: this.calculateCredibilityScore(content, sourceType),
              retrievedAt: new Date(),
              freshnessStatus: 'CURRENT',
              verificationState: 'UNVERIFIED',
              ...(content.publisher ? { sourcePublisher: content.publisher } : {}),
              ...(content.publicationDate
                ? { sourcePublicationDate: content.publicationDate }
                : {}),
              ...(this.extractStructuredFact(content.content, claimText)
                ? {
                    structuredFact: this.extractStructuredFact(
                      content.content,
                      claimText,
                    ) as Record<string, unknown>,
                  }
                : {}),
            };

            allEvidence.push(evidence);
            evidenceCounter++;

            if (allEvidence.length >= (opts.maximumEvidenceCount ?? 10)) {
              break;
            }
          } catch (error) {
            const message = error instanceof Error ? error.message : 'Unknown error';
            errors.push(`[${sourceType}] Failed to retrieve ${result.sourceUrl}: ${message}`);
          }
        }

        if (allEvidence.length >= (opts.maximumEvidenceCount ?? 10)) {
          break;
        }
      }

      // Step 4: Build bundle with builder pattern
      const builder = new EvidenceBundleBuilder(claimRef, domain, gradeBand);
      builder.addEvidences(allEvidence);
      builder.calculateCoverage();
      builder.detectContradictions();
      builder.calculateConfidence();

      const bundle = builder.build();

      // Step 5: Persist to database
      await this.persistBundle(bundle, claimRef);

      this.logger.info(
        {
          claimRef,
          evidenceCount: bundle.evidences.length,
          coverageScore: bundle.coverageScore,
          confidence: bundle.bundleConfidence,
        },
        'Evidence bundle generation complete'
      );

      return {
        bundle,
        success: bundle.evidences.length >= (opts.minimumEvidenceCount ?? 3),
        errors,
        sourcesQueried,
        totalResultsFound,
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error';
      this.logger.error({ err: error, claimRef }, 'Evidence bundle generation failed');

      return {
        bundle: this.createEmptyBundle(claimRef, domain, gradeBand),
        success: false,
        errors: [...errors, message],
        sourcesQueried,
        totalResultsFound,
      };
    }
  }

  /**
   * Extract structured facts from evidence content.
   * Uses heuristics to identify factual statements, entities, and relationships.
   */
  private extractStructuredFact(content: string, claimText: string): {
    mainStatement: string;
    entities: string[];
    relationships: string[];
    confidence: number;
  } | null {
    if (!content || content.length < 50) {
      return null;
    }

    // Extract sentences from content
    const sentences = content
      .split(/[.!?]+/)
      .map((s) => s.trim())
      .filter((s) => s.length > 20);

    if (sentences.length === 0) {
      return null;
    }

    // Find the most relevant sentence (simple heuristic: longest sentence with claim keywords)
    const claimKeywords = claimText.toLowerCase().split(/\s+/).filter((w) => w.length > 3);
    const firstSentence = sentences[0];
    if (!firstSentence) {
      return null;
    }

    let bestSentence = firstSentence;
    let bestScore = 0;

    for (const sentence of sentences) {
      const lowerSentence = sentence.toLowerCase();
      const keywordMatches = claimKeywords.filter((kw) => lowerSentence.includes(kw)).length;
      const lengthScore = sentence.length / content.length;
      const score = keywordMatches * 2 + lengthScore;

      if (score > bestScore) {
        bestScore = score;
        bestSentence = sentence;
      }
    }

    // Extract entities (capitalized words, numbers, units)
    const entityPattern = /\b[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*\b|\b\d+(?:\.\d+)?\s*(?:kg|m|s|°C|°F|N|J|W|Hz|Pa|mol|L|g|cm|km|mph)\b/g;
    const entities = [...new Set(bestSentence.match(entityPattern) || [])];

    // Extract relationships (verbs and prepositions)
    const relationshipPattern = /\b(?:is|are|was|were|has|have|had|causes|caused|results in|resulted in|leads to|led to|depends on|affects|affected by|relates to|related to)\b/gi;
    const relationships = [...new Set(bestSentence.match(relationshipPattern) || [])];

    // Calculate confidence based on entity count and relationship presence
    const confidence = Math.min(1.0, (entities.length * 0.3) + (relationships.length * 0.2) + 0.3);

    return {
      mainStatement: bestSentence,
      entities,
      relationships,
      confidence,
    };
  }

  /**
   * Detect support kind for evidence relative to claim.
   * Analyzes semantic similarity and sentiment to determine if evidence supports, contradicts, or is neutral.
   */
  private detectSupportKind(evidenceContent: string, claimText: string): 'SUPPORTS' | 'CONTRADICTS' | 'NEUTRAL' {
    const lowerEvidence = evidenceContent.toLowerCase();
    const lowerClaim = claimText.toLowerCase();

    // Check for explicit contradiction indicators
    const contradictionIndicators = [
      'not', 'never', 'cannot', 'impossible', 'false', 'incorrect', 'wrong',
      'disprove', 'contradict', 'opposite', 'contrary', 'however', 'although',
      'despite', 'unlike', 'differs from', 'different from'
    ];

    const hasContradiction = contradictionIndicators.some((indicator) =>
      lowerEvidence.includes(indicator) && lowerClaim.split(/\s+/).some((word) =>
        lowerEvidence.includes(word) && word.length > 3
      )
    );

    if (hasContradiction) {
      return 'CONTRADICTS';
    }

    // Check for explicit support indicators
    const supportIndicators = [
      'confirms', 'demonstrates', 'shows', 'proves', 'establishes', 'validates',
      'supports', 'agrees with', 'consistent with', 'according to', 'as stated',
      'evidence shows', 'research indicates', 'studies show', 'data shows'
    ];

    const hasSupport = supportIndicators.some((indicator) => lowerEvidence.includes(indicator));

    if (hasSupport) {
      return 'SUPPORTS';
    }

    // Calculate keyword overlap as fallback
    const claimWords = new Set(lowerClaim.split(/\s+/).filter((w) => w.length > 3));
    const evidenceWords = new Set(lowerEvidence.split(/\s+/).filter((w) => w.length > 3));

    let overlap = 0;
    for (const word of claimWords) {
      if (evidenceWords.has(word)) {
        overlap++;
      }
    }

    const overlapRatio = overlap / Math.max(claimWords.size, 1);

    // If significant overlap (>30%), treat as support
    if (overlapRatio > 0.3) {
      return 'SUPPORTS';
    }

    // Default to neutral
    return 'NEUTRAL';
  }

  /**
   * Extract search queries from claim text.
   */
  private extractSearchQueries(claimText: string, domain: string): string[] {
    // Simple heuristic: use claim text as primary query
    // Could be enhanced with LLM-based keyword extraction
    const queries = [claimText];

    // Add domain-specific context
    if (domain.toLowerCase() === 'physics') {
      queries.push(`${claimText} physics principles`);
    } else if (domain.toLowerCase() === 'math' || domain.toLowerCase() === 'algebra') {
      queries.push(`${claimText} mathematical proof`);
    }

    return queries.slice(0, 3); // Limit to 3 queries
  }

  /**
   * Query an adapter with multiple search queries.
   */
  private async queryAdapter(
    adapter: EvidenceSourceAdapter,
    queries: string[],
    domain: string,
    gradeBand: string
  ): Promise<EvidenceSearchResult[]> {
    const allResults: EvidenceSearchResult[] = [];

    for (const query of queries) {
      const results = await adapter.search(query, domain, {
        maxResults: 5,
        gradeBand,
      });
      allResults.push(...results);
    }

    // Deduplicate by URL and sort by relevance
    const unique = new Map<string, EvidenceSearchResult>();
    for (const result of allResults) {
      if (!unique.has(result.sourceUrl) ||
          unique.get(result.sourceUrl)!.relevanceScore < result.relevanceScore) {
        unique.set(result.sourceUrl, result);
      }
    }

    return Array.from(unique.values())
      .sort((a, b) => b.relevanceScore - a.relevanceScore);
  }

  /**
   * Calculate credibility score based on source and content quality.
   */
  private calculateCredibilityScore(
    content: { qualityIndicators?: { isFeatured?: boolean; citationCount?: number } },
    sourceType: EvidenceSourceType
  ): number {
    let score = 0.5; // Base score

    // Source type weighting
    const sourceWeights: Record<EvidenceSourceType, number> = {
      'PEER_REVIEWED_JOURNAL': 0.95,
      'OPENSTAX': 0.9,
      'TEXTBOOK': 0.85,
      'KHAN_ACADEMY': 0.8,
      'CURRICULUM_STANDARD': 0.8,
      'DOMAIN_EXPERT': 0.75,
      'SIMULATION_RESULT': 0.7,
      'CALCULATION': 0.7,
      'WIKIPEDIA': 0.5,
    };

    score = sourceWeights[sourceType] ?? 0.5;

    // Quality indicators
    if (content.qualityIndicators?.isFeatured) {
      score += 0.05;
    }
    if ((content.qualityIndicators?.citationCount ?? 0) > 10) {
      score += 0.05;
    }

    return Math.min(1.0, score);
  }

  /**
   * Persist bundle to database.
   */
  private async persistBundle(bundle: EvidenceBundle, claimRef: string): Promise<void> {
    // Get experienceId from claim
    const claim = await this.prisma.learningClaim.findFirst({
      where: { claimRef },
      select: { experienceId: true },
    });

    if (!claim) {
      throw new Error(`Claim not found: ${claimRef}`);
    }

    const experienceId = claim.experienceId;

    // Save evidences
    for (const evidence of bundle.evidences) {
      const persistencePayload = {
        sourceType: evidence.sourceType,
        sourceTitle: evidence.sourceTitle,
        supportKind: evidence.supportKind,
        retrievedAt: evidence.retrievedAt,
        freshnessStatus: evidence.freshnessStatus,
        verificationState: evidence.verificationState,
        ...(evidence.sourceUrl ? { sourceUrl: evidence.sourceUrl } : {}),
        ...(evidence.sourcePublisher
          ? { sourcePublisher: evidence.sourcePublisher }
          : {}),
        ...(evidence.sourcePublicationDate
          ? { sourcePublicationDate: evidence.sourcePublicationDate }
          : {}),
        ...(evidence.excerpt ? { excerpt: evidence.excerpt } : {}),
        ...(evidence.structuredFact
          ? { structuredFact: evidence.structuredFact as Prisma.InputJsonValue }
          : {}),
        ...(evidence.credibilityScore != null
          ? { credibilityScore: evidence.credibilityScore }
          : {}),
        ...(evidence.contradictionNotes
          ? { contradictionNotes: evidence.contradictionNotes }
          : {}),
      };

      await this.prisma.learningEvidence.upsert({
        where: {
          experienceId_evidenceRef: {
            experienceId,
            evidenceRef: evidence.evidenceRef,
          },
        },
        create: {
          experienceId,
          evidenceRef: evidence.evidenceRef,
          claimRef: evidence.claimRef,
          ...persistencePayload,
        },
        update: persistencePayload,
      });
    }

    // Save or update bundle metadata
    await this.prisma.evidenceBundleMetadata.upsert({
      where: { claimRef },
      create: {
        claimRef,
        experienceId,
        bundleConfidence: bundle.bundleConfidence,
        coverageScore: bundle.coverageScore,
        contradictionDetected: bundle.contradictionDetected,
        freshnessOverall: bundle.freshnessOverall,
        evidenceCount: bundle.evidences.length,
        primarySourceTypes: Array.from(new Set(bundle.evidences.map(e => e.sourceType))),
        bundleCache: bundle as unknown as Prisma.InputJsonValue,
      },
      update: {
        bundleConfidence: bundle.bundleConfidence,
        coverageScore: bundle.coverageScore,
        contradictionDetected: bundle.contradictionDetected,
        freshnessOverall: bundle.freshnessOverall,
        evidenceCount: bundle.evidences.length,
        primarySourceTypes: Array.from(new Set(bundle.evidences.map(e => e.sourceType))),
        regeneratedAt: new Date(),
        bundleCache: bundle as unknown as Prisma.InputJsonValue,
      },
    });
  }

  /**
   * Create an empty bundle for error cases.
   */
  private createEmptyBundle(claimRef: string, domain: string, gradeBand: string): EvidenceBundle {
    return {
      bundleId: `bundle-${claimRef}-empty`,
      claimRef,
      domain,
      gradeBand,
      evidences: [],
      bundleConfidence: 0,
      coverageScore: 0,
      coverageGaps: [{
        aspect: 'generation_failed',
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
   * Create a timeout promise.
   */
  private createTimeoutPromise<T>(ms: number, defaultValue: T): Promise<T> {
    return new Promise((resolve) => {
      setTimeout(() => resolve(defaultValue), ms);
    });
  }
}

// Import Prisma type
import type { Prisma } from '@tutorputor/core/db';
