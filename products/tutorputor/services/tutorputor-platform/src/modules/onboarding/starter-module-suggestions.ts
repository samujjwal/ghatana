/**
 * Starter Module Suggestion Service
 *
 * AI-powered algorithm to suggest starter modules for new users
 * based on their interests, tenant domain, and peer behavior.
 *
 * @doc.type service
 * @doc.purpose Suggest personalized starter modules for new users
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type { Logger } from "pino";
import type { TenantDomain } from "../tenant/domain-analyzer";

export interface StarterModuleSuggestion {
  assetId: string;
  title: string;
  description: string;
  difficulty: string;
  estimatedMinutes: number;
  matchScore: number; // 0-1
  matchReasons: string[];
  tags: string[];
  imageUrl?: string;
}

export interface UserInterest {
  category: string;
  topics: string[];
  level: "beginner" | "intermediate" | "advanced";
}

export interface SuggestionOptions {
  tenantId: string;
  userId: string;
  domain?: TenantDomain;
  interests?: UserInterest[];
  excludeAssetIds?: string[];
  limit?: number;
}

/**
 * Service for suggesting starter modules to new users
 */
export class StarterModuleSuggestionService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: Logger,
  ) {}

  /**
   * Get personalized starter module suggestions
   */
  async getSuggestions(options: SuggestionOptions): Promise<StarterModuleSuggestion[]> {
    const {
      tenantId,
      userId,
      domain,
      interests,
      excludeAssetIds = [],
      limit = 5,
    } = options;

    // Get domain-appropriate modules
    const domainModules = await this.getDomainSpecificModules(tenantId, domain, excludeAssetIds);

    // Get popular modules as fallback
    const popularModules = await this.getPopularModules(tenantId, excludeAssetIds, 10);

    // Get peer-suggested modules
    const peerModules = await this.getPeerSuggestedModules(tenantId, userId, excludeAssetIds);

    // Combine and score all candidates
    const allCandidates = this.combineAndDeduplicate([
      ...domainModules.map((m) => ({ ...m, source: "domain" })),
      ...popularModules.map((m) => ({ ...m, source: "popular" })),
      ...peerModules.map((m) => ({ ...m, source: "peer" })),
    ]);

    // Score and rank based on interests
    const scored = this.scoreModules(allCandidates, interests, domain);

    // Sort by score and return top results
    const topResults = scored
      .sort((a, b) => b.matchScore - a.matchScore)
      .slice(0, limit);

    this.logger.info(
      {
        tenantId,
        userId,
        suggestionCount: topResults.length,
        domain,
      },
      "Starter module suggestions generated",
    );

    return topResults;
  }

  /**
   * Get modules appropriate for tenant domain
   */
  private async getDomainSpecificModules(
    tenantId: string,
    domain: TenantDomain | undefined,
    excludeIds: string[],
  ): Promise<Array<{ assetId: string; title: string; description: string; difficulty: string; estimatedMinutes: number; tags: string[]; domain: string | null }>> {
    // Map domain to recommended subjects
    const domainSubjects: Record<TenantDomain, string[]> = {
      K12_ELEMENTARY: ["BASIC_MATH", "READING", "SCIENCE_BASICS"],
      K12_MIDDLE: ["MATHEMATICS", "SCIENCE", "SOCIAL_STUDIES"],
      K12_HIGH: ["ADVANCED_MATH", "PHYSICS", "CHEMISTRY", "BIOLOGY", "LITERATURE"],
      HIGHER_ED: ["COMPUTER_SCIENCE", "ENGINEERING", "BUSINESS", "MEDICINE", "LAW"],
      CORPORATE: ["PROFESSIONAL_DEVELOPMENT", "LEADERSHIP", "TECHNICAL_SKILLS", "COMPLIANCE"],
      PROFESSIONAL_TRAINING: ["CERTIFICATION", "EXAM_PREP", "INDUSTRY_STANDARDS"],
      LIFELONG_LEARNING: ["PERSONAL_DEVELOPMENT", "HOBBIES", "CREATIVE_ARTS"],
      MIXED: ["INTRODUCTION", "PLATFORM_TUTORIAL", "GENERAL_SKILLS"],
      UNKNOWN: ["GETTING_STARTED", "PLATFORM_INTRO", "BASIC_SKILLS"],
    };

    const subjects = domain ? domainSubjects[domain] ?? [] : [];

    // Build where clause with proper type handling
    const whereClause: Record<string, unknown> = {
      tenantId,
      status: "PUBLISHED",
    };

    if (subjects.length > 0) {
      whereClause.domain = { in: subjects };
    }

    if (excludeIds.length > 0) {
      whereClause.id = { notIn: excludeIds };
    }

    const modules = await this.prisma.contentAsset.findMany({
      where: whereClause,
      select: {
        id: true,
        title: true,
        searchableText: true,
        difficultyLevel: true,
        domain: true,
        tags: true,
      },
      orderBy: { createdAt: "desc" },
      take: 20,
    });

    return modules.map((m) => ({
      assetId: m.id,
      title: m.title,
      description: m.searchableText?.substring(0, 200) ?? "",
      difficulty: m.difficultyLevel ?? "intermediate",
      estimatedMinutes: 30,
      tags: (m.tags as string[]) ?? [],
      domain: m.domain,
    }));
  }

  /**
   * Get popular modules in tenant
   */
  private async getPopularModules(
    tenantId: string,
    excludeIds: string[],
    limit: number,
  ): Promise<Array<{ assetId: string; title: string; description: string; difficulty: string; estimatedMinutes: number; tags: string[]; enrollmentCount: number }>> {
    // Get enrollment counts per module
    const enrollments = await this.prisma.enrollment.groupBy({
      by: ["moduleId"],
      where: {
        tenantId,
        ...(excludeIds.length > 0 && { moduleId: { notIn: excludeIds } }),
      },
      _count: { id: true },
      orderBy: { _count: { id: "desc" } },
      take: limit,
    });

    if (enrollments.length === 0) {
      return [];
    }

    // Get asset details
    const moduleIds = enrollments.map((e) => e.moduleId);
    const assets = await this.prisma.contentAsset.findMany({
      where: { id: { in: moduleIds } },
      select: {
        id: true,
        title: true,
        searchableText: true,
        difficultyLevel: true,
        tags: true,
      },
    });

    const assetMap = new Map(assets.map((a) => [a.id, a]));

    return enrollments.map((e) => {
      const asset = assetMap.get(e.moduleId);
      return {
        assetId: e.moduleId,
        title: asset?.title ?? "",
        description: (asset?.searchableText as string)?.substring(0, 200) ?? "",
        difficulty: (asset?.difficultyLevel as string) ?? "intermediate",
        estimatedMinutes: 30,
        tags: (asset?.tags as string[]) ?? [],
        enrollmentCount: e._count.id ?? 0,
      };
    });
  }

  /**
   * Get modules suggested by similar peers
   */
  private async getPeerSuggestedModules(
    tenantId: string,
    userId: string,
    excludeIds: string[],
  ): Promise<Array<{ assetId: string; title: string; description: string; difficulty: string; estimatedMinutes: number; tags: string[]; peerScore: number }>> {
    // Find users who enrolled in similar content
    const userEnrollments = await this.prisma.enrollment.findMany({
      where: { tenantId, userId },
      select: { moduleId: true },
    });

    if (userEnrollments.length === 0) {
      return [];
    }

    const userModuleIds = userEnrollments.map((e) => e.moduleId);

    // Find peers with similar enrollments
    const peerEnrollments = await this.prisma.enrollment.findMany({
      where: {
        tenantId,
        moduleId: { in: userModuleIds },
        userId: { not: userId },
      },
      select: { userId: true },
      distinct: ["userId"],
      take: 20,
    });

    const peerIds = peerEnrollments.map((e) => e.userId);

    if (peerIds.length === 0) {
      return [];
    }

    // Get what these peers enrolled in
    const peerModuleEnrollments = await this.prisma.enrollment.groupBy({
      by: ["moduleId"],
      where: {
        tenantId,
        userId: { in: peerIds },
        moduleId: { notIn: [...excludeIds, ...userModuleIds] },
      },
      _count: { id: true },
      orderBy: { _count: { id: "desc" } },
      take: 10,
    });

    if (peerModuleEnrollments.length === 0) {
      return [];
    }

    // Get asset details
    const moduleIds = peerModuleEnrollments.map((e) => e.moduleId);
    const assets = await this.prisma.contentAsset.findMany({
      where: { id: { in: moduleIds } },
      select: {
        id: true,
        title: true,
        searchableText: true,
        difficultyLevel: true,
        tags: true,
      },
    });

    const assetMap = new Map(assets.map((a) => [a.id, a]));
    const peerScoreMap = new Map(peerModuleEnrollments.map((e) => [e.moduleId, e._count.id ?? 0]));

    return peerModuleEnrollments.map((e) => {
      const asset = assetMap.get(e.moduleId);
      return {
        assetId: e.moduleId,
        title: asset?.title ?? "",
        description: (asset?.searchableText as string)?.substring(0, 200) ?? "",
        difficulty: (asset?.difficultyLevel as string) ?? "intermediate",
        estimatedMinutes: 30,
        tags: (asset?.tags as string[]) ?? [],
        peerScore: peerScoreMap.get(e.moduleId) ?? 0,
      };
    });
  }

  /**
   * Combine and deduplicate module candidates
   */
  private combineAndDeduplicate<T extends { assetId: string; source: string }>(
    candidates: T[],
  ): Array<Omit<T, "source"> & { sources: string[] }> {
    const map = new Map<string, Omit<T, "source"> & { sources: string[] }>();

    for (const candidate of candidates) {
      const { source, ...rest } = candidate;

      if (map.has(candidate.assetId)) {
        const existing = map.get(candidate.assetId)!;
        if (!existing.sources.includes(source)) {
          existing.sources.push(source);
        }
      } else {
        map.set(candidate.assetId, { ...rest, sources: [source] });
      }
    }

    return Array.from(map.values());
  }

  /**
   * Score modules based on interests and domain
   */
  private scoreModules(
    modules: Array<{
      assetId: string;
      title: string;
      description: string;
      difficulty: string;
      estimatedMinutes: number;
      tags: string[];
      sources: string[];
      enrollmentCount?: number;
      peerScore?: number;
    }>,
    interests: UserInterest[] | undefined,
    domain: TenantDomain | undefined,
  ): StarterModuleSuggestion[] {
    return modules.map((m) => {
      const matchReasons: string[] = [];
      let matchScore = 0;

      // Source-based scoring
      if (m.sources.includes("domain")) {
        matchScore += 0.3;
        matchReasons.push("Recommended for your organization type");
      }
      if (m.sources.includes("popular")) {
        matchScore += 0.25;
        matchReasons.push(`Popular with ${m.enrollmentCount ?? "many"} learners`);
      }
      if (m.sources.includes("peer")) {
        matchScore += 0.35;
        matchReasons.push("Similar learners are taking this");
      }

      // Interest-based scoring
      if (interests) {
        const interestScore = this.calculateInterestScore(m, interests);
        matchScore += interestScore.score;
        if (interestScore.reasons.length > 0) {
          matchReasons.push(...interestScore.reasons);
        }
      }

      // Difficulty adjustment for beginner-friendly content
      if (m.difficulty === "beginner" || m.difficulty === "elementary") {
        matchScore += 0.1;
        matchReasons.push("Great for beginners");
      }

      // Time commitment preference (shorter is better for starters)
      if (m.estimatedMinutes <= 15) {
        matchScore += 0.1;
      } else if (m.estimatedMinutes <= 30) {
        matchScore += 0.05;
      }

      return {
        assetId: m.assetId,
        title: m.title,
        description: m.description,
        difficulty: m.difficulty,
        estimatedMinutes: m.estimatedMinutes,
        tags: m.tags,
        matchScore: Math.min(1, Math.max(0, matchScore)),
        matchReasons: matchReasons.slice(0, 3), // Limit to 3 reasons
      };
    });
  }

  /**
   * Calculate interest match score
   */
  private calculateInterestScore(
    module: { tags: string[]; title: string; description: string },
    interests: UserInterest[],
  ): { score: number; reasons: string[] } {
    let score = 0;
    const reasons: string[] = [];

    const moduleText = `${module.title} ${module.description} ${module.tags.join(" ")}`.toLowerCase();

    for (const interest of interests) {
      // Check category match
      if (moduleText.includes(interest.category.toLowerCase())) {
        score += 0.2;
        reasons.push(`Matches your interest in ${interest.category}`);
      }

      // Check topic matches
      for (const topic of interest.topics) {
        if (moduleText.includes(topic.toLowerCase())) {
          score += 0.15;
          if (!reasons.some((r) => r.includes(interest.category))) {
            reasons.push(`Related to ${topic}`);
          }
        }
      }
    }

    return { score: Math.min(0.5, score), reasons: reasons.slice(0, 2) };
  }
}
