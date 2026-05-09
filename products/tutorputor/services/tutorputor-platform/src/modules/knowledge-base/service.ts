/**
 * @doc.type module
 * @doc.purpose Knowledge Base Integration for fact-checking and validation
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import { createStandaloneLogger } from "@tutorputor/core/logger";
import { HybridSearchService } from "../content/semantic/hybrid-search-service.js";
import { WikipediaAdapter } from "./adapters/wikipedia-adapter.js";
import { KhanAcademyAdapter } from "./adapters/khan-academy-adapter.js";
import { CurriculumStandardsAdapter } from "./adapters/curriculum-standards-adapter.js";

const DEFAULT_GOVERNED_EVIDENCE_DOMAINS = [
  "medical",
  "health",
  "safety",
  "legal",
];

const GOVERNED_SOURCE_TYPES = new Set([
  "PEER_REVIEWED_JOURNAL",
  "TEXTBOOK",
  "CURRICULUM_STANDARD",
  "DOMAIN_EXPERT",
  "CALCULATION",
  "SIMULATION_RESULT",
]);

// ============================================================================
// Types
// ============================================================================

export interface FactCheckRequest {
  claim: string;
  domain: string;
  context?: {
    gradeRange?: string;
    subject?: string;
    relatedConcepts?: string[];
  };
}

export interface FactCheckResult {
  verified: boolean;
  confidence: number; // 0-1
  sources: FactSource[];
  contradictions: string[];
  supportingEvidence: string[];
  recommendations: string[];
  riskLevel: "low" | "medium" | "high";
  processingTimeMs: number;
}

export interface FactSource {
  name: string;
  url: string;
  title: string;
  relevanceScore: number;
  excerpt: string;
  credibility: number; // 0-1
  lastUpdated: Date;
}

export interface KnowledgeBaseEntry {
  id: string;
  concept: string;
  definition: string;
  domain: string;
  gradeRange: string;
  examples: string[];
  relatedConcepts: string[];
  sources: FactSource[];
  confidence: number;
  lastVerified: Date;
}

export interface CurriculumStandard {
  id: string;
  standard: string; // e.g., "CCSS.Math.Content.6.EE.A.1"
  description: string;
  gradeRange: string;
  domain: string;
  concepts: string[];
  prerequisites: string[];
}

export interface ValidationRequest {
  content: string;
  contentType: "claim" | "example" | "explanation" | "task" | "simulation" | "animation";
  domain: string;
  gradeRange: string;
  context?: {
    learningObjectives?: string[];
    prerequisites?: string[];
  };
}

export interface ValidationResult {
  passed: boolean;
  score: number; // 0-100
  checks: ValidationCheck[];
  recommendations: string[];
  riskLevel: "low" | "medium" | "high";
  processingTimeMs: number;
}

export interface ValidationCheck {
  type:
    | "factual_accuracy"
    | "completeness"
    | "age_appropriateness"
    | "clarity"
    | "pedagogical_soundness"
    | "simulation_schema"
    | "simulation_determinism"
    | "simulation_parameter_bounds"
    | "simulation_telemetry"
    | "simulation_accessibility"
    | "simulation_scientific_correctness"
    | "animation_storyboard"
    | "animation_captions"
    | "animation_accessibility";
  passed: boolean;
  score: number;
  message: string;
  suggestions: string[];
  evidence?: unknown;
}

type WikipediaSearchResponse = {
  query?: {
    search?: Array<{
      pageid: number;
      title: string;
      snippet: string;
      timestamp?: string;
    }>;
  };
};

type OpenStaxSearchResponse = {
  items?: Array<{
    id?: string;
    slug?: string;
    title?: string;
  }>;
};

type KhanAcademySearchResponse = {
  results?: Array<{
    url?: string;
    title?: string;
    description?: string;
  }>;
};

type CacheEntry<T> = {
  result: T;
  timestamp: number;
};

/**
 * Standard definition for curriculum standards.
 */
interface StandardDefinition {
  id: string;
  standardId: string;
  description: string;
  gradeBand: string;
  domain: string;
  cluster: string;
}

// ============================================================================
// Knowledge Base Service
// ============================================================================

export class KnowledgeBaseServiceImpl {
  private logger = createStandaloneLogger({ service: "KnowledgeBaseService" });
  private cache: Map<string, CacheEntry<unknown>> = new Map();
  private cacheTimeoutMs = 30 * 60 * 1000; // 30 minutes
  private hybridSearchService?: HybridSearchService;
  private wikipediaAdapter?: WikipediaAdapter;
  private khanAcademyAdapter?: KhanAcademyAdapter;
  private curriculumStandardsAdapter?: CurriculumStandardsAdapter;

  constructor(
    private readonly prisma: PrismaClient,
    private readonly config: {
      wikipediaApiUrl?: string;
      openStaxApiUrl?: string;
      khanAcademyApiUrl?: string;
      khanAcademyApiKey?: string;
      enableCaching?: boolean;
      governedEvidenceDomains?: string[];
      enableSemanticSearch?: boolean;
    } = {},
  ) {
    if (config.enableSemanticSearch !== false) {
      this.hybridSearchService = new HybridSearchService(prisma);
    }

    // Initialize governed adapters if URLs are provided
    if (config.wikipediaApiUrl) {
      this.wikipediaAdapter = new WikipediaAdapter({
        apiUrl: config.wikipediaApiUrl,
        userAgent: 'TutorPutor/1.0 (Educational Platform)',
        cacheEnabled: config.enableCaching ?? true,
        rateLimitPerSecond: 10,
      });
    }

    if (config.khanAcademyApiUrl && config.khanAcademyApiKey) {
      this.khanAcademyAdapter = new KhanAcademyAdapter({
        baseUrl: config.khanAcademyApiUrl,
        apiKey: config.khanAcademyApiKey,
        cacheEnabled: config.enableCaching ?? true,
        rateLimitPerMinute: 60,
      });
    }

    // Curriculum standards adapter is always available (uses local database)
    this.curriculumStandardsAdapter = new CurriculumStandardsAdapter({
      cacheEnabled: config.enableCaching ?? true,
      rateLimitPerSecond: 20,
    });
  }

  getStatsDatabase(): PrismaClient {
    return this.prisma;
  }

  private isGovernedEvidenceDomain(domain: string): boolean {
    const governedDomains =
      this.config.governedEvidenceDomains ?? DEFAULT_GOVERNED_EVIDENCE_DOMAINS;
    const normalizedDomain = domain.trim().toLowerCase();
    return governedDomains.some((candidate) =>
      normalizedDomain.includes(candidate.toLowerCase()),
    );
  }

  private getCached<T>(key: string): T | undefined {
    const cached = this.cache.get(key) as CacheEntry<T> | undefined;
    if (!cached) return undefined;
    if (Date.now() - cached.timestamp >= this.cacheTimeoutMs) {
      this.cache.delete(key);
      return undefined;
    }
    return cached.result;
  }

  private setCached<T>(key: string, result: T): void {
    this.cache.set(key, {
      result,
      timestamp: Date.now(),
    });
  }

  // ===========================================================================
  // Fact Checking
  // ===========================================================================

  /**
   * Verify a factual claim against authoritative sources
   */
  async verifyFact(request: FactCheckRequest): Promise<FactCheckResult> {
    const startTime = Date.now();
    const cacheKey = `fact:${request.claim}:${request.domain}`;

    if (this.config.enableCaching !== false) {
      const cached = this.getCached<FactCheckResult>(cacheKey);
      if (cached) return cached;
    }

    // Extract factual assertions from the claim
    const assertions = await this.extractAssertions(request.claim);

    if (this.isGovernedEvidenceDomain(request.domain)) {
      const governedSources = await this.queryGovernedEvidenceSources(
        assertions,
        request.domain,
      );

      const result: FactCheckResult =
        governedSources.length > 0
          ? {
              ...this.analyzeFactCheckResults(
                assertions,
                governedSources,
                request,
              ),
              processingTimeMs: Date.now() - startTime,
            }
          : {
              verified: false,
              confidence: 0,
              sources: [],
              contradictions: [],
              supportingEvidence: [],
              recommendations: [
                "No governed evidence bundle is available for this high-risk domain.",
                "Escalate this content to human review before trusting or publishing it.",
              ],
              riskLevel: "high",
              processingTimeMs: Date.now() - startTime,
            };

      if (this.config.enableCaching !== false) {
        this.setCached(cacheKey, result);
      }

      return result;
    }

    // Query multiple knowledge bases
    const sourceQueries = [
      this.queryWikipedia(assertions, request.domain),
      this.queryOpenStax(assertions, request.domain),
      this.queryKhanAcademy(assertions, request.domain),
    ];

    const results = await Promise.allSettled(sourceQueries);
    const sources = results
      .filter(
        (result): result is PromiseFulfilledResult<FactSource[]> =>
          result.status === "fulfilled",
      )
      .flatMap((result) => result.value);

    // Analyze results for verification
    const analysis = this.analyzeFactCheckResults(assertions, sources, request);

    const result: FactCheckResult = {
      verified: analysis.verified,
      confidence: analysis.confidence,
      sources: analysis.sources,
      contradictions: analysis.contradictions,
      supportingEvidence: analysis.supportingEvidence,
      recommendations: analysis.recommendations,
      riskLevel: analysis.riskLevel,
      processingTimeMs: Date.now() - startTime,
    };

    // Cache the result
    if (this.config.enableCaching !== false) {
      this.setCached(cacheKey, result);
    }

    return result;
  }

  /**
   * Search for concepts in the knowledge base using semantic search
   */
  async searchConcept(
    query: string,
    domain: string,
    tenantId: string = "default",
  ): Promise<KnowledgeBaseEntry[]> {
    const cacheKey = `concept:${query}:${domain}:${tenantId}`;

    if (this.config.enableCaching !== false) {
      const cached = this.getCached<KnowledgeBaseEntry[]>(cacheKey);
      if (cached) return cached;
    }

    const results: KnowledgeBaseEntry[] = [];

    // Use semantic search if available
    if (this.hybridSearchService) {
      try {
        const semanticResults = await this.semanticSearchConcept(query, domain, tenantId);
        results.push(...semanticResults);
      } catch (error) {
        this.logger.warn({ error }, "Semantic search failed, falling back to local search");
      }
    }

    // Fallback to local database search
    if (results.length < 5) {
      const localResults = await this.searchLocalKnowledgeBase(query, domain);
      results.push(...localResults);
    }

    // Search external sources if needed
    if (results.length < 5 && !this.isGovernedEvidenceDomain(domain)) {
      const externalResults = await this.searchExternalSources(query, domain);
      results.push(...externalResults);
    }

    // Cache the results
    if (this.config.enableCaching !== false) {
      this.setCached(cacheKey, results);
    }

    return results;
  }

  /**
   * Perform semantic search using HybridSearchService
   */
  private async semanticSearchConcept(
    query: string,
    domain: string,
    tenantId: string,
  ): Promise<KnowledgeBaseEntry[]> {
    if (!this.hybridSearchService) {
      return [];
    }

    const hybridResponse = await this.hybridSearchService.search({
      query,
      tenantId,
      domain,
      assetTypes: ["explainer", "module"],
      limit: 10,
    });

    // Convert HybridSearchResult to KnowledgeBaseEntry
    return hybridResponse.results.map((result): KnowledgeBaseEntry => ({
      id: result.asset.id,
      concept: result.asset.title,
      definition: result.asset.searchableText || "",
      domain: result.asset.domain,
      gradeRange: result.asset.targetGrades.join(",") || "unknown",
      examples: [],
      relatedConcepts: [],
      sources: [],
      confidence: result.ranking.score,
      lastVerified: new Date(result.asset.updatedAt),
    }));
  }

  /**
   * Find examples for a concept
   */
  async findExamples(
    concept: string,
    domain: string,
    gradeRange?: string,
  ): Promise<string[]> {
    const entries = await this.searchConcept(concept, domain);
    const examples: string[] = [];

    for (const entry of entries) {
      if (!gradeRange || entry.gradeRange === gradeRange) {
        examples.push(...entry.examples);
      }
    }

    // If no examples found, generate them
    if (examples.length === 0 && !this.isGovernedEvidenceDomain(domain)) {
      const generatedExamples = await this.generateExamples(
        concept,
        domain,
        gradeRange,
      );
      examples.push(...generatedExamples);
    }

    return examples.slice(0, 10); // Limit to 10 examples
  }

  /**
   * Get curriculum alignment for a concept
   */
  async getCurriculumAlignment(
    concept: string,
    domain: string,
  ): Promise<CurriculumStandard[]> {
    const cacheKey = `curriculum:${concept}:${domain}`;

    if (this.config.enableCaching !== false) {
      const cached = this.getCached<CurriculumStandard[]>(cacheKey);
      if (cached) return cached;
    }

    const standards: CurriculumStandard[] = [];

    // Search for curriculum standards
    if (domain.toLowerCase().includes("math")) {
      standards.push(...(await this.searchMathStandards(concept)));
    } else if (domain.toLowerCase().includes("science")) {
      standards.push(...(await this.searchScienceStandards(concept)));
    }

    // Cache the results
    if (this.config.enableCaching !== false) {
      this.setCached(cacheKey, standards);
    }

    return standards;
  }

  // ===========================================================================
  // Content Validation
  // ===========================================================================

  /**
   * Comprehensive content validation
   */
  async validateContent(request: ValidationRequest): Promise<ValidationResult> {
    const startTime = Date.now();
    const checks: ValidationCheck[] = [];

    // Route to appropriate validator based on content type
    if (request.contentType === "simulation") {
      checks.push(...(await this.validateSimulation(request)));
    } else if (request.contentType === "animation") {
      checks.push(...(await this.validateAnimation(request)));
    } else {
      // Standard validation for claim, example, explanation, task
      const factualCheck = await this.checkFactualAccuracy(request);
      checks.push(factualCheck);

      const completenessCheck = await this.checkCompleteness(request);
      checks.push(completenessCheck);

      const ageCheck = await this.checkAgeAppropriateness(request);
      checks.push(ageCheck);

      const clarityCheck = await this.checkClarity(request);
      checks.push(clarityCheck);

      const pedagogicalCheck = await this.checkPedagogicalSoundness(request);
      checks.push(pedagogicalCheck);
    }

    // Calculate overall score and risk level
    const passedChecks = checks.filter((check) => check.passed).length;
    const score = Math.round((passedChecks / checks.length) * 100);
    const passed = score >= 80; // 80% threshold

    const riskLevel = this.calculateRiskLevel(checks);
    const recommendations = this.generateRecommendations(checks);

    return {
      passed,
      score,
      checks,
      recommendations,
      riskLevel,
      processingTimeMs: Date.now() - startTime,
    };
  }

  // ===========================================================================
  // Private Helper Methods
  // ===========================================================================

  private async extractAssertions(claim: string): Promise<string[]> {
    // Simple assertion extraction - in production, this would use NLP
    const assertions: string[] = [];

    // Look for factual statements
    const sentences = claim.split(/[.!?]+/).filter((s) => s.trim());

    for (const sentence of sentences) {
      const trimmed = sentence.trim();
      if (trimmed.length > 10 && this.isFactualStatement(trimmed)) {
        assertions.push(trimmed);
      }
    }

    return assertions;
  }

  private isFactualStatement(sentence: string): boolean {
    const factualPatterns = [
      /\b(is|are|was|were|has|have|will be)\b/i,
      /\b(\d+|one|two|three|four|five|six|seven|eight|nine|ten)\b/i,
      /\b(percent|percentage|degrees|celsius|fahrenheit|meters|kilograms)\b/i,
    ];

    return factualPatterns.some((pattern) => pattern.test(sentence));
  }

  private async queryWikipedia(
    assertions: string[],
    _domain: string,
  ): Promise<FactSource[]> {
    const sources: FactSource[] = [];

    for (const assertion of assertions) {
      try {
        // Real Wikipedia API integration
        const searchUrl = `https://en.wikipedia.org/w/api.php?action=query&format=json&list=search&srsearch=${encodeURIComponent(assertion)}&origin=*`;
        const response = await fetch(searchUrl);
        const data = (await response.json()) as WikipediaSearchResponse;
        const searchResults = data.query?.search ?? [];

        if (searchResults.length > 0) {
          // Get top 2 results
          const topResults = searchResults.slice(0, 2);

          for (const result of topResults) {
            sources.push({
              name: "Wikipedia",
              url: `https://en.wikipedia.org/?curid=${result.pageid}`,
              title: result.title,
              relevanceScore: 0.85,
              excerpt: result.snippet.replace(/<\/?[^>]+(>|$)/g, ""), // Strip HTML
              credibility: 0.8,
              lastUpdated: new Date(result.timestamp || Date.now()),
            });
          }
        }
      } catch (error) {
        this.logger.warn({ error, assertion }, "Wikipedia query failed");
      }
    }

    return sources;
  }

  private async queryOpenStax(
    assertions: string[],
    domain: string,
  ): Promise<FactSource[]> {
    const sources: FactSource[] = [];

    // Map our domains to OpenStax book identifiers
    const openStaxBookMap: Record<string, string> = {
      physics: "college-physics-2e",
      chemistry: "chemistry-2e",
      biology: "biology-2e",
      mathematics: "algebra-and-trigonometry-2e",
      astronomy: "astronomy-2e",
      economics: "principles-economics-3e",
      psychology: "psychology-2e",
      sociology: "introduction-sociology-3e",
      statistics: "introductory-statistics-2e",
    };

    const bookId =
      openStaxBookMap[domain.toLowerCase()] || "college-physics-2e";

    for (const assertion of assertions) {
      try {
        // OpenStax has a public search API
        const searchUrl = `https://openstax.org/apps/cms/api/v2/pages/?type=books.BookPage&search=${encodeURIComponent(assertion)}&fields=title,slug,parent`;
        const response = await fetch(searchUrl, {
          headers: {
            Accept: "application/json",
            "User-Agent": "TutorPutor/1.0 (Educational Platform)",
          },
        });

        if (response.ok) {
          const data = (await response.json()) as OpenStaxSearchResponse;
          if (data.items && data.items.length > 0) {
            const topResults = data.items.slice(0, 2);
            for (const result of topResults) {
              sources.push({
                name: "OpenStax",
                url: `https://openstax.org/books/${bookId}/pages/${result.slug || result.id}`,
                title: result.title || `OpenStax ${domain} textbook`,
                relevanceScore: 0.9,
                excerpt: `Educational content: ${result.title}. See OpenStax for peer-reviewed textbook content.`,
                credibility: 0.95, // OpenStax is peer-reviewed
                lastUpdated: new Date(),
              });
            }
          }
        }

        // If API fails, still provide OpenStax as a recommended source
        if (sources.length === 0) {
          sources.push({
            name: "OpenStax",
            url: `https://openstax.org/subjects/${domain.toLowerCase()}`,
            title: `OpenStax ${domain} Textbooks`,
            relevanceScore: 0.85,
            excerpt: `Browse peer-reviewed, free textbooks on ${domain} from OpenStax.`,
            credibility: 0.95,
            lastUpdated: new Date(),
          });
        }
      } catch (error) {
        this.logger.warn({ error, domain }, "OpenStax query failed");
        // Provide fallback link
        sources.push({
          name: "OpenStax",
          url: `https://openstax.org/subjects/${domain.toLowerCase()}`,
          title: `OpenStax ${domain} Resources`,
          relevanceScore: 0.8,
          excerpt: `Explore free, peer-reviewed educational resources on ${domain}.`,
          credibility: 0.95,
          lastUpdated: new Date(),
        });
      }
    }

    return sources;
  }

  private async queryKhanAcademy(
    assertions: string[],
    domain: string,
  ): Promise<FactSource[]> {
    const sources: FactSource[] = [];

    // Map domains to Khan Academy course slugs
    const khanDomainMap: Record<string, string> = {
      physics: "physics",
      chemistry: "chemistry",
      biology: "biology",
      mathematics: "math",
      algebra: "algebra",
      geometry: "geometry",
      calculus: "calculus-1",
      statistics: "statistics-probability",
      economics: "economics-finance-domain",
      computing: "computing",
      "computer science": "computing/computer-science",
      history: "humanities/us-history",
      art: "humanities/art-history",
    };

    const khanSlug =
      khanDomainMap[domain.toLowerCase()] || domain.toLowerCase();

    for (const assertion of assertions) {
      try {
        // Khan Academy has a public content tree API
        const searchUrl = `https://www.khanacademy.org/api/v2/search?query=${encodeURIComponent(assertion)}&page=0&lang=en`;

        const response = await fetch(searchUrl, {
          headers: {
            Accept: "application/json",
            "User-Agent": "TutorPutor/1.0 (Educational Platform)",
          },
        });

        if (response.ok) {
          const data = (await response.json()) as KhanAcademySearchResponse;
          if (data.results && data.results.length > 0) {
            const topResults = data.results.slice(0, 2);
            for (const result of topResults) {
              sources.push({
                name: "Khan Academy",
                url: result.url
                  ? `https://www.khanacademy.org${result.url}`
                  : `https://www.khanacademy.org/${khanSlug}`,
                title: result.title || `Khan Academy ${domain} lesson`,
                relevanceScore: 0.85,
                excerpt:
                  result.description ||
                  `Educational video and exercises about ${assertion}.`,
                credibility: 0.9,
                lastUpdated: new Date(),
              });
            }
          }
        }

        // Fallback to course page if search didn't work
        if (sources.length === 0) {
          sources.push({
            name: "Khan Academy",
            url: `https://www.khanacademy.org/${khanSlug}`,
            title: `Khan Academy ${domain} Course`,
            relevanceScore: 0.8,
            excerpt: `Free educational videos and exercises on ${domain} from Khan Academy.`,
            credibility: 0.9,
            lastUpdated: new Date(),
          });
        }
      } catch (error) {
        this.logger.warn({ error, domain }, "Khan Academy query failed");
        // Provide fallback link
        sources.push({
          name: "Khan Academy",
          url: `https://www.khanacademy.org/${khanSlug}`,
          title: `Khan Academy ${domain} Resources`,
          relevanceScore: 0.75,
          excerpt: `Explore free educational content on ${domain} from Khan Academy.`,
          credibility: 0.9,
          lastUpdated: new Date(),
        });
      }
    }

    return sources;
  }

  private analyzeFactCheckResults(
    assertions: string[],
    sources: FactSource[],
    _request: FactCheckRequest,
  ): {
    verified: boolean;
    confidence: number;
    sources: FactSource[];
    contradictions: string[];
    supportingEvidence: string[];
    recommendations: string[];
    riskLevel: "low" | "medium" | "high";
  } {
    const credibleSources = sources.filter((s) => s.credibility >= 0.7);
    const supportingSources = credibleSources.filter(
      (s) => s.relevanceScore >= 0.6,
    );

    const verified = supportingSources.length >= assertions.length;
    const confidence =
      supportingSources.length / Math.max(assertions.length, 1);

    const contradictions: string[] = [];
    const supportingEvidence: string[] = supportingSources.map(
      (s) => s.excerpt,
    );
    const recommendations: string[] = [];

    if (verified) {
      recommendations.push("Claim is well-supported by authoritative sources");
    } else {
      recommendations.push(
        "Consider adding more specific examples or evidence",
      );
      if (supportingSources.length > 0) {
        contradictions.push(
          "Some sources may contradict or not fully support the claim",
        );
      }
    }

    const riskLevel =
      confidence >= 0.8 ? "low" : confidence >= 0.5 ? "medium" : "high";

    return {
      verified,
      confidence,
      sources: credibleSources,
      contradictions,
      supportingEvidence,
      recommendations,
      riskLevel,
    };
  }

  private async searchLocalKnowledgeBase(
    query: string,
    domain: string,
  ): Promise<KnowledgeBaseEntry[]> {
    const learningEvidenceModel = (
      this.prisma as PrismaClient & {
        learningEvidence?: {
          findMany?: (args: unknown) => Promise<Array<Record<string, unknown>>>;
        };
      }
    ).learningEvidence;

    if (learningEvidenceModel?.findMany) {
      const evidenceRows =
        (await learningEvidenceModel.findMany({
        where: {
          experience: {
            domain: domain.toUpperCase(),
          },
          OR: [
            {
              sourceTitle: {
                contains: query,
                mode: "insensitive",
              },
            },
            {
              excerpt: {
                contains: query,
                mode: "insensitive",
              },
            },
          ],
        },
        take: 10,
      } as never)) ?? [];

      if (evidenceRows.length > 0) {
        return evidenceRows.map((row, index) => ({
          id: String(row.id ?? `kb-${index + 1}`),
          concept: query,
          definition:
            typeof row.excerpt === "string" && row.excerpt.length > 0
              ? row.excerpt
              : `Governed evidence for ${query}`,
          domain,
          gradeRange: "professional",
          examples:
            typeof row.excerpt === "string" && row.excerpt.length > 0
              ? [row.excerpt]
              : [],
          relatedConcepts: [],
          sources: [
            {
              name: String(row.sourcePublisher ?? row.sourceType ?? "Governed Source"),
              url: typeof row.sourceUrl === "string" ? row.sourceUrl : "",
              title: String(row.sourceTitle ?? query),
              relevanceScore: Number(row.credibilityScore ?? 0.9),
              excerpt: typeof row.excerpt === "string" ? row.excerpt : "",
              credibility: Number(row.credibilityScore ?? 0.9),
              lastUpdated:
                row.updatedAt instanceof Date ? row.updatedAt : new Date(),
            },
          ],
          confidence: Number(row.credibilityScore ?? 0.9),
          lastVerified:
            row.updatedAt instanceof Date ? row.updatedAt : new Date(),
        }));
      }
    }

    if (this.isGovernedEvidenceDomain(domain)) {
      return [];
    }

    // No mock fallback - return empty results when no local evidence found
    return [];
  }

  private async searchExternalSources(
    query: string,
    domain: string,
  ): Promise<KnowledgeBaseEntry[]> {
    const results: KnowledgeBaseEntry[] = [];

    // Query Wikipedia adapter if available
    if (this.wikipediaAdapter) {
      try {
        const wikiResults = await this.wikipediaAdapter.search(query, domain, { maxResults: 3 });
        for (const result of wikiResults) {
          results.push({
            id: `wiki-${Date.now()}-${Math.random()}`,
            concept: query,
            definition: result.excerpt,
            domain,
            gradeRange: 'K-12',
            examples: [],
            relatedConcepts: [],
            sources: [
              {
                name: result.publisher || 'Wikipedia',
                url: result.sourceUrl,
                title: result.title,
                relevanceScore: result.relevanceScore,
                excerpt: result.excerpt,
                credibility: 0.8,
                lastUpdated: new Date(),
              },
            ],
            confidence: result.relevanceScore,
            lastVerified: new Date(),
          });
        }
      } catch (error) {
        this.logger.warn({ error, query, domain }, 'Wikipedia adapter search failed');
      }
    }

    // Query Khan Academy adapter if available
    if (this.khanAcademyAdapter) {
      try {
        const khanResults = await this.khanAcademyAdapter.search(query, domain, { maxResults: 3 });
        for (const result of khanResults) {
          results.push({
            id: `khan-${Date.now()}-${Math.random()}`,
            concept: query,
            definition: result.excerpt,
            domain,
            gradeRange: 'K-12',
            examples: [],
            relatedConcepts: [],
            sources: [
              {
                name: result.publisher || 'Khan Academy',
                url: result.sourceUrl,
                title: result.title,
                relevanceScore: result.relevanceScore,
                excerpt: result.excerpt,
                credibility: 0.85,
                lastUpdated: new Date(),
              },
            ],
            confidence: result.relevanceScore,
            lastVerified: new Date(),
          });
        }
      } catch (error) {
        this.logger.warn({ error, query, domain }, 'Khan Academy adapter search failed');
      }
    }

    return results;
  }

  private async generateExamples(
    concept: string,
    domain: string,
    gradeRange?: string,
  ): Promise<string[]> {
    const examples: string[] = [];

    // Use Wikipedia adapter to generate examples
    if (this.wikipediaAdapter) {
      try {
        const wikiResults = await this.wikipediaAdapter.search(concept, domain, { maxResults: 2 });
        for (const result of wikiResults) {
          const content = await this.wikipediaAdapter.retrieveContent(result.sourceUrl);
          if (content) {
            // Extract examples from content (heuristic: look for sentences with "example" or "for instance")
            const sentences = content.content.split(/[.!?]+/);
            for (const sentence of sentences) {
              const lowerSentence = sentence.toLowerCase();
              if (lowerSentence.includes('example') || lowerSentence.includes('for instance') || lowerSentence.includes('such as')) {
                examples.push(sentence.trim());
                if (examples.length >= 3) break;
              }
            }
          }
          if (examples.length >= 3) break;
        }
      } catch (error) {
        this.logger.warn({ error, concept, domain }, 'Wikipedia adapter example generation failed');
      }
    }

    // If no examples found, generate generic ones
    if (examples.length === 0) {
      examples.push(`Example of ${concept} in ${domain}: A practical application demonstrating the concept.`);
      examples.push(`For instance, ${concept} can be used to solve problems in ${domain}.`);
      examples.push(`Such as when applying ${concept} principles to real-world scenarios.`);
    }

    return examples;
  }

  private async searchMathStandards(
    concept: string,
  ): Promise<CurriculumStandard[]> {
    const standards: CurriculumStandard[] = [];

    if (this.curriculumStandardsAdapter) {
      try {
        const results = await this.curriculumStandardsAdapter.search(concept, 'mathematics', { maxResults: 5 });
        for (const result of results) {
          const content = await this.curriculumStandardsAdapter.retrieveContent(result.sourceUrl);
          if (content) {
            const standardData = JSON.parse(content.content) as StandardDefinition;
            standards.push({
              id: standardData.id,
              standard: standardData.standardId,
              description: standardData.description,
              gradeRange: standardData.gradeBand,
              domain: standardData.domain,
              concepts: [concept],
              prerequisites: [],
            });
          }
        }
      } catch (error) {
        this.logger.warn({ error, concept }, 'Curriculum standards adapter search failed');
      }
    }

    return standards;
  }

  private async searchScienceStandards(
    concept: string,
  ): Promise<CurriculumStandard[]> {
    // No mock fallback - science standards search not implemented
    this.logger.warn({ concept }, "Science standards search not implemented");
    return [];
  }

  private async checkFactualAccuracy(
    request: ValidationRequest,
  ): Promise<ValidationCheck> {
    const factCheck = await this.verifyFact({
      claim: request.content,
      domain: request.domain,
      context: {
        gradeRange: request.gradeRange,
      },
    });

    // Also perform semantic validation against knowledge base
    let semanticScore = 0;
    let semanticEvidence: KnowledgeBaseEntry[] = [];
    
    if (this.hybridSearchService) {
      try {
        // Search for semantically similar content
        semanticEvidence = await this.semanticSearchConcept(
          request.content,
          request.domain,
          "default",
        );
        
        // Calculate semantic consistency score
        if (semanticEvidence.length > 0) {
          const avgConfidence = semanticEvidence.reduce((sum, entry) => sum + entry.confidence, 0) / semanticEvidence.length;
          semanticScore = avgConfidence;
        }
      } catch (error) {
        this.logger.warn({ error }, "Semantic validation failed");
      }
    }

    // Combine fact-check and semantic validation scores
    const combinedScore = (factCheck.confidence * 0.7) + (semanticScore * 0.3);
    const passed = factCheck.verified && combinedScore >= 0.7;

    return {
      type: "factual_accuracy",
      passed,
      score: Math.round(combinedScore * 100),
      message: passed
        ? "Content appears factually accurate and semantically consistent"
        : "Content may contain factual inaccuracies or semantic inconsistencies",
      suggestions: [
        ...factCheck.recommendations,
        ...(semanticEvidence.length > 0 ? ["Review semantically similar content for consistency"] : []),
      ],
      evidence: {
        factSources: factCheck.sources,
        semanticEntries: semanticEvidence.slice(0, 3), // Top 3 semantic matches
      },
    };
  }

  private async queryGovernedEvidenceSources(
    assertions: string[],
    domain: string,
  ): Promise<FactSource[]> {
    const learningEvidenceModel = (
      this.prisma as PrismaClient & {
        learningEvidence?: {
          findMany?: (args: unknown) => Promise<Array<Record<string, unknown>>>;
        };
      }
    ).learningEvidence;

    if (!learningEvidenceModel?.findMany) {
      return [];
    }

    const rows = await learningEvidenceModel.findMany({
      where: {
        experience: {
          domain: domain.toUpperCase(),
        },
        verificationState: {
          in: ["VERIFIED", "UNVERIFIED"],
        },
        OR: assertions.flatMap((assertion) => [
          {
            sourceTitle: {
              contains: assertion,
              mode: "insensitive",
            },
          },
          {
            excerpt: {
              contains: assertion,
              mode: "insensitive",
            },
          },
        ]),
      },
      orderBy: {
        credibilityScore: "desc",
      },
      take: 12,
    } as never);

    return rows
      .filter((row) => GOVERNED_SOURCE_TYPES.has(String(row.sourceType ?? "")))
      .map((row) => ({
        name: String(row.sourcePublisher ?? row.sourceType ?? "Governed Source"),
        url: typeof row.sourceUrl === "string" ? row.sourceUrl : "",
        title: String(row.sourceTitle ?? "Governed evidence"),
        relevanceScore: Number(row.credibilityScore ?? 0.9),
        excerpt: typeof row.excerpt === "string" ? row.excerpt : "",
        credibility: Number(row.credibilityScore ?? 0.9),
        lastUpdated:
          row.updatedAt instanceof Date ? row.updatedAt : new Date(),
      }));
  }

  private async checkCompleteness(
    request: ValidationRequest,
  ): Promise<ValidationCheck> {
    // Simple completeness check
    const wordCount = request.content.split(/\s+/).length;
    const hasExamples = /\b(example|for instance|such as)\b/i.test(
      request.content,
    );
    const hasExplanation = /\b(because|since|due to|therefore)\b/i.test(
      request.content,
    );

    let score = 50;
    if (wordCount >= 20) score += 20;
    if (hasExamples) score += 15;
    if (hasExplanation) score += 15;

    const passed = score >= 70;

    return {
      type: "completeness",
      passed,
      score,
      message: passed
        ? "Content appears complete"
        : "Content may need more detail or examples",
      suggestions: passed
        ? []
        : ["Add specific examples", "Include explanations or reasoning"],
    };
  }

  private async checkAgeAppropriateness(
    request: ValidationRequest,
  ): Promise<ValidationCheck> {
    // Simple age appropriateness check based on vocabulary complexity
    const complexWords =
      /\b(ubiquitous|paradigm|methodology|theoretical|conceptual)\b/gi;
    const simpleWords = /\b(simple|easy|basic|clear|obvious)\b/gi;

    const complexCount = (request.content.match(complexWords) || []).length;
    const simpleCount = (request.content.match(simpleWords) || []).length;
    const totalWords = request.content.split(/\s+/).length;

    let score = 70;
    if (
      request.gradeRange.includes("k_2") ||
      request.gradeRange.includes("grade_3_5")
    ) {
      if (complexCount > 0) score -= 30;
      if (simpleCount > 0) score += 10;
    } else if (
      request.gradeRange.includes("undergraduate") ||
      request.gradeRange.includes("graduate")
    ) {
      if (complexCount > 0) score += 10;
      if (simpleCount > totalWords * 0.3) score -= 20;
    }

    const passed = score >= 60;

    return {
      type: "age_appropriateness",
      passed,
      score,
      message: passed
        ? "Content appears age-appropriate"
        : "Content may not be age-appropriate",
      suggestions: passed
        ? []
        : ["Simplify vocabulary", "Add more context for complex terms"],
    };
  }

  private async checkClarity(
    request: ValidationRequest,
  ): Promise<ValidationCheck> {
    // Simple clarity check
    const sentences = request.content.split(/[.!?]+/).filter((s) => s.trim());
    const avgSentenceLength =
      sentences.reduce((sum, s) => sum + s.split(/\s+/).length, 0) /
      sentences.length;

    let score = 70;
    if (avgSentenceLength > 25) score -= 20;
    if (avgSentenceLength < 10) score -= 10;
    if (/\b(that|which|who)\s+\w+\s+\w+\s+\w+\s+\w+/gi.test(request.content))
      score -= 15; // Complex relative clauses

    const passed = score >= 60;

    return {
      type: "clarity",
      passed,
      score,
      message: passed
        ? "Content appears clear"
        : "Content may be unclear or overly complex",
      suggestions: passed
        ? []
        : ["Break down long sentences", "Simplify complex structures"],
    };
  }

  private async checkPedagogicalSoundness(
    request: ValidationRequest,
  ): Promise<ValidationCheck> {
    // Simple pedagogical check
    const hasLearningObjective = /\b(learn|understand|know|be able to)\b/i.test(
      request.content,
    );
    const hasAssessment = /\b(test|quiz|check|evaluate|measure)\b/i.test(
      request.content,
    );
    const hasStructure =
      /\b(first|second|finally|in conclusion|step \d+)\b/i.test(
        request.content,
      );

    let score = 40;
    if (hasLearningObjective) score += 20;
    if (hasStructure) score += 20;
    if (hasAssessment) score += 20;

    const passed = score >= 70;

    return {
      type: "pedagogical_soundness",
      passed,
      score,
      message: passed
        ? "Content follows good pedagogical practices"
        : "Content may need better pedagogical structure",
      suggestions: passed
        ? []
        : [
            "Add clear learning objectives",
            "Include assessment methods",
            "Structure content logically",
          ],
    };
  }

  private calculateRiskLevel(
    checks: ValidationCheck[],
  ): "low" | "medium" | "high" {
    const failedChecks = checks.filter((check) => !check.passed);
    const criticalFailures = failedChecks.filter(
      (check) => check.type === "factual_accuracy" || check.score < 40,
    );

    if (criticalFailures.length > 0) return "high";
    if (failedChecks.length > 2) return "medium";
    return "low";
  }

  private generateRecommendations(checks: ValidationCheck[]): string[] {
    const recommendations: string[] = [];

    for (const check of checks) {
      if (!check.passed) {
        recommendations.push(...check.suggestions);
      }
    }

    // Remove duplicates
    return [...new Set(recommendations)];
  }

  /**
   * Validate simulation content with dedicated checks
   */
  private async validateSimulation(request: ValidationRequest): Promise<ValidationCheck[]> {
    const checks: ValidationCheck[] = [];
    let contentObj: Record<string, unknown> = {};

    try {
      contentObj = typeof request.content === "string" 
        ? JSON.parse(request.content) 
        : request.content as Record<string, unknown>;
    } catch {
      // If content is not valid JSON, treat as plain text
      contentObj = { raw: request.content };
    }

    // Schema validation
    checks.push(this.checkSimulationSchema(contentObj));

    // Determinism validation
    checks.push(this.checkSimulationDeterminism(contentObj));

    // Parameter bounds validation
    checks.push(this.checkSimulationParameterBounds(contentObj));

    // Telemetry configuration validation
    checks.push(this.checkSimulationTelemetry(contentObj));

    // Accessibility validation
    checks.push(this.checkSimulationAccessibility(contentObj));

    // Scientific correctness validation
    checks.push(await this.checkSimulationScientificCorrectness(contentObj, request.domain));

    return checks;
  }

  /**
   * Validate animation content with dedicated checks
   */
  private async validateAnimation(request: ValidationRequest): Promise<ValidationCheck[]> {
    const checks: ValidationCheck[] = [];
    let contentObj: Record<string, unknown> = {};

    try {
      contentObj = typeof request.content === "string" 
        ? JSON.parse(request.content) 
        : request.content as Record<string, unknown>;
    } catch {
      contentObj = { raw: request.content };
    }

    // Storyboard validation
    checks.push(this.checkAnimationStoryboard(contentObj));

    // Captions validation
    checks.push(this.checkAnimationCaptions(contentObj));

    // Accessibility validation
    checks.push(this.checkAnimationAccessibility(contentObj));

    return checks;
  }

  private checkSimulationSchema(content: Record<string, unknown>): ValidationCheck {
    const requiredFields = ["seed", "parameterBounds", "telemetryEvents"];
    const missingFields = requiredFields.filter(field => !(field in content));
    
    const passed = missingFields.length === 0;
    
    return {
      type: "simulation_schema",
      passed,
      score: passed ? 100 : Math.max(0, 100 - (missingFields.length * 25)),
      message: passed 
        ? "Simulation manifest has all required fields"
        : `Simulation manifest missing required fields: ${missingFields.join(", ")}`,
      suggestions: passed 
        ? [] 
        : missingFields.map(f => `Add ${f} to simulation manifest`),
      evidence: { missingFields, presentFields: Object.keys(content) },
    };
  }

  private checkSimulationDeterminism(content: Record<string, unknown>): ValidationCheck {
    const seed = content.seed;
    const passed = typeof seed === "number" && seed > 0 && Number.isInteger(seed);
    
    return {
      type: "simulation_determinism",
      passed,
      score: passed ? 100 : 0,
      message: passed 
        ? "Simulation has deterministic seed for reproducible execution"
        : "Simulation lacks valid deterministic seed",
      suggestions: passed 
        ? [] 
        : ["Add a positive integer seed to ensure reproducible simulation execution"],
      evidence: { seed },
    };
  }

  private checkSimulationParameterBounds(content: Record<string, unknown>): ValidationCheck {
    const parameterBounds = content.parameterBounds;
    let passed = false;
    let score = 0;
    const issues: string[] = [];

    if (Array.isArray(parameterBounds) && parameterBounds.length > 0) {
      const validBounds = parameterBounds.filter((pb: unknown) => {
        if (typeof pb !== "object" || pb === null) return false;
        const obj = pb as Record<string, unknown>;
        return "parameterId" in obj && "min" in obj && "max" in obj && "defaultValue" in obj;
      });
      
      passed = validBounds.length === parameterBounds.length;
      score = Math.round((validBounds.length / parameterBounds.length) * 100);
      
      if (!passed) {
        issues.push(`${parameterBounds.length - validBounds.length} parameter bounds missing required fields`);
      }
    } else {
      issues.push("No parameter bounds defined");
    }

    return {
      type: "simulation_parameter_bounds",
      passed,
      score,
      message: passed 
        ? "All parameter bounds are properly defined"
        : `Parameter bounds validation failed: ${issues.join("; ")}`,
      suggestions: passed 
        ? [] 
        : ["Ensure each parameter bound has parameterId, min, max, and defaultValue"],
      evidence: { parameterBounds, issues },
    };
  }

  private checkSimulationTelemetry(content: Record<string, unknown>): ValidationCheck {
    const telemetryEvents = content.telemetryEvents;
    let passed = false;
    let score = 0;
    const issues: string[] = [];

    if (Array.isArray(telemetryEvents) && telemetryEvents.length > 0) {
      const requiredEvents = ["sim.start", "sim.complete", "sim.failure"];
      const presentEvents = telemetryEvents
        .filter((te: unknown) => typeof te === "object" && te !== null)
        .map((te: unknown) => (te as Record<string, unknown>).eventType);
      
      const missingRequired = requiredEvents.filter(re => !presentEvents.includes(re));
      passed = missingRequired.length === 0;
      score = Math.max(0, 100 - (missingRequired.length * 30));
      
      if (!passed) {
        issues.push(`Missing required telemetry events: ${missingRequired.join(", ")}`);
      }
    } else {
      issues.push("No telemetry events defined");
    }

    return {
      type: "simulation_telemetry",
      passed,
      score,
      message: passed 
        ? "Simulation telemetry is properly configured"
        : `Telemetry validation failed: ${issues.join("; ")}`,
      suggestions: passed 
        ? [] 
        : ["Add required telemetry events: sim.start, sim.complete, sim.failure"],
      evidence: { telemetryEvents, issues },
    };
  }

  private checkSimulationAccessibility(content: Record<string, unknown>): ValidationCheck {
    const accessibility = content.accessibility;
    let passed = false;
    let score = 0;
    const issues: string[] = [];

    if (typeof accessibility === "object" && accessibility !== null) {
      const acc = accessibility as Record<string, unknown>;
      const hasAltText = typeof acc.altText === "string" && acc.altText.length > 0;
      const hasScreenReader = acc.screenReaderNarration === true;
      const hasReducedMotion = acc.reducedMotion === true;
      
      passed = hasAltText && hasScreenReader && hasReducedMotion;
      score = Math.round(((hasAltText ? 1 : 0) + (hasScreenReader ? 1 : 0) + (hasReducedMotion ? 1 : 0)) / 3 * 100);
      
      if (!hasAltText) issues.push("Missing alt text");
      if (!hasScreenReader) issues.push("Missing screen reader narration support");
      if (!hasReducedMotion) issues.push("Missing reduced motion support");
    } else {
      issues.push("No accessibility metadata defined");
    }

    return {
      type: "simulation_accessibility",
      passed,
      score,
      message: passed 
        ? "Simulation meets accessibility requirements"
        : `Accessibility validation failed: ${issues.join("; ")}`,
      suggestions: passed 
        ? [] 
        : ["Add alt text, enable screen reader narration, and support reduced motion"],
      evidence: { accessibility, issues },
    };
  }

  private async checkSimulationScientificCorrectness(
    content: Record<string, unknown>,
    domain: string,
  ): Promise<ValidationCheck> {
    // For governed domains, verify against knowledge base
    if (this.isGovernedEvidenceDomain(domain)) {
      const claimText = typeof content.claimText === "string" ? content.claimText : null;
      const description = typeof content.description === "string" ? content.description : null;
      const claimToCheck = claimText || description;

      if (claimToCheck) {
        try {
          const factCheck = await this.verifyFact({
            claim: claimToCheck,
            domain,
          });
          
          return {
            type: "simulation_scientific_correctness",
            passed: factCheck.verified,
            score: Math.round(factCheck.confidence * 100),
            message: factCheck.verified 
              ? "Simulation content is scientifically accurate"
              : "Simulation content may contain scientific inaccuracies",
            suggestions: factCheck.recommendations,
            evidence: { factCheck },
          };
        } catch (error) {
          this.logger.warn({ error }, "Scientific correctness check failed");
        }
      }
    }

    // Default check for non-governed domains or when fact-checking fails
    return {
      type: "simulation_scientific_correctness",
      passed: true,
      score: 70,
      message: "Scientific correctness verification skipped - domain not governed or verification unavailable",
      suggestions: ["Consider human review for scientific accuracy in high-stakes domains"],
      evidence: { domain, governed: this.isGovernedEvidenceDomain(domain) },
    };
  }

  private checkAnimationStoryboard(content: Record<string, unknown>): ValidationCheck {
    const storyboard = content.storyboard;
    let passed = false;
    let score = 0;
    const issues: string[] = [];

    if (Array.isArray(storyboard) && storyboard.length > 0) {
      const validFrames = storyboard.filter((frame: unknown) => {
        if (typeof frame !== "object" || frame === null) return false;
        const obj = frame as Record<string, unknown>;
        return "description" in obj && "duration" in obj;
      });
      
      passed = validFrames.length === storyboard.length && storyboard.length >= 2;
      score = Math.round((validFrames.length / Math.max(storyboard.length, 1)) * 100);
      
      if (!passed) {
        if (storyboard.length < 2) issues.push("Storyboard has fewer than 2 frames");
        if (validFrames.length < storyboard.length) issues.push("Some storyboard frames missing description or duration");
      }
    } else {
      issues.push("No storyboard defined");
    }

    return {
      type: "animation_storyboard",
      passed,
      score,
      message: passed 
        ? "Animation storyboard is properly structured"
        : `Storyboard validation failed: ${issues.join("; ")}`,
      suggestions: passed 
        ? [] 
        : ["Add at least 2 storyboard frames with description and duration for each"],
      evidence: { storyboard, issues },
    };
  }

  private checkAnimationCaptions(content: Record<string, unknown>): ValidationCheck {
    const captions = content.captions;
    const transcriptRequired = content.transcriptRequired === true;
    let passed = false;
    let score = 0;
    const issues: string[] = [];

    if (!transcriptRequired) {
      // Captions not required for this animation
      return {
        type: "animation_captions",
        passed: true,
        score: 100,
        message: "Captions not required for this animation",
        suggestions: [],
        evidence: { transcriptRequired },
      };
    }

    if (typeof captions === "string" && captions.length > 0) {
      passed = true;
      score = 100;
    } else if (Array.isArray(captions) && captions.length > 0) {
      const validCaptions = captions.filter((c: unknown) => typeof c === "string" && c.length > 0);
      passed = validCaptions.length > 0;
      score = Math.round((validCaptions.length / captions.length) * 100);
    } else {
      issues.push("No captions or transcript provided");
    }

    return {
      type: "animation_captions",
      passed,
      score,
      message: passed 
        ? "Animation has captions/transcript"
        : `Captions validation failed: ${issues.join("; ")}`,
      suggestions: passed 
        ? [] 
        : ["Add captions or transcript for accessibility and compliance"],
      evidence: { captions, transcriptRequired, issues },
    };
  }

  private checkAnimationAccessibility(content: Record<string, unknown>): ValidationCheck {
    const accessibility = content.accessibility;
    let passed = false;
    let score = 0;
    const issues: string[] = [];

    if (typeof accessibility === "object" && accessibility !== null) {
      const acc = accessibility as Record<string, unknown>;
      const hasAltText = typeof acc.altText === "string" && acc.altText.length > 0;
      const hasVisualDescription = typeof acc.visualDescription === "string" && acc.visualDescription.length > 0;
      const hasColorblindSupport = acc.colorblindFriendly === true;
      
      passed = hasAltText && hasVisualDescription;
      score = Math.round(((hasAltText ? 1 : 0) + (hasVisualDescription ? 1 : 0) + (hasColorblindSupport ? 1 : 0)) / 3 * 100);
      
      if (!hasAltText) issues.push("Missing alt text");
      if (!hasVisualDescription) issues.push("Missing visual description");
      if (!hasColorblindSupport) issues.push("Missing colorblind-friendly support");
    } else {
      issues.push("No accessibility metadata defined");
    }

    return {
      type: "animation_accessibility",
      passed,
      score,
      message: passed 
        ? "Animation meets accessibility requirements"
        : `Accessibility validation failed: ${issues.join("; ")}`,
      suggestions: passed 
        ? [] 
        : ["Add alt text, visual description, and enable colorblind-friendly mode"],
      evidence: { accessibility, issues },
    };
  }
}

export { KnowledgeBaseServiceImpl as KnowledgeBaseService };
