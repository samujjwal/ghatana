/**
 * Tenant Domain Analyzer
 *
 * Analyzes tenant data to infer their educational domain and characteristics.
 * Determines if tenant is K-12, Higher Education, Corporate Training, etc.
 *
 * @doc.type service
 * @doc.purpose Analyze tenant to infer their educational domain
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type { Logger } from "pino";

export type TenantDomain =
  | "K12_ELEMENTARY"
  | "K12_MIDDLE"
  | "K12_HIGH"
  | "HIGHER_ED"
  | "CORPORATE"
  | "PROFESSIONAL_TRAINING"
  | "LIFELONG_LEARNING"
  | "MIXED"
  | "UNKNOWN";

export interface DomainAnalysis {
  domain: TenantDomain;
  confidence: number;
  indicators: DomainIndicator[];
  recommendedFeatures: string[];
  suggestedModules: string[];
}

export interface DomainIndicator {
  type: "user_email_domain" | "content_keywords" | "enrollment_patterns" | "age_demographics" | "usage_patterns";
  value: string;
  weight: number; // 0-1
}

export interface TenantContext {
  tenantId: string;
  name: string;
  userCount: number;
  emailDomains: string[];
  topSubjects: string[];
  averageUserAge?: number;
  contentCount: number;
}

// Domain keywords for classification
const DOMAIN_KEYWORDS: Record<TenantDomain, string[]> = {
  K12_ELEMENTARY: ["elementary", "primary", "grade 1", "grade 2", "grade 3", "grade 4", "grade 5", "k-5"],
  K12_MIDDLE: ["middle school", "junior high", "grade 6", "grade 7", "grade 8", "k-8"],
  K12_HIGH: ["high school", "secondary", "grade 9", "grade 10", "grade 11", "grade 12", "k-12"],
  HIGHER_ED: ["university", "college", "undergraduate", "graduate", "phd", "professor", "campus", "academic"],
  CORPORATE: ["training", "employee", "workforce", "corporate", "compliance", "professional development"],
  PROFESSIONAL_TRAINING: ["certification", "exam prep", "professional", "continuing education", "license"],
  LIFELONG_LEARNING: ["adult learning", "hobby", "personal development", "self-improvement"],
  MIXED: [],
  UNKNOWN: [],
};

// Corporate email domains
const CORPORATE_DOMAINS = [
  "corp.", "company", "inc.", "llc", "ltd", "enterprise", "business", "org",
  "consulting", "solutions", "tech", "systems", "group", "services", "industries",
];

// Educational email domains
const EDUCATION_DOMAINS = [".edu", "school", "academy", "institute", "college", "university"];

/**
 * Service for analyzing and inferring tenant domain
 */
export class TenantDomainAnalyzer {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: Logger,
  ) {}

  /**
   * Analyze tenant and return domain classification
   */
  async analyzeTenant(tenantId: string): Promise<DomainAnalysis> {
    // Gather tenant context
    const context = await this.gatherTenantContext(tenantId);

    // Run analysis
    const indicators = await this.identifyIndicators(context);
    const domain = this.classifyDomain(indicators);
    const confidence = this.calculateConfidence(indicators);

    // Generate recommendations
    const recommendedFeatures = this.getRecommendedFeatures(domain);
    const suggestedModules = this.getSuggestedStarterModules(domain);

    this.logger.info(
      {
        tenantId,
        domain,
        confidence,
        indicatorCount: indicators.length,
      },
      "Tenant domain analysis completed",
    );

    return {
      domain,
      confidence,
      indicators,
      recommendedFeatures,
      suggestedModules,
    };
  }

  /**
   * Quick domain detection based on limited data
   */
  async quickDetect(tenantId: string, tenantName: string, adminEmail: string): Promise<TenantDomain> {
    const indicators: DomainIndicator[] = [];

    // Check email domain
    const emailDomain = adminEmail.split("@")[1]?.toLowerCase() ?? "";

    if (EDUCATION_DOMAINS.some((d) => emailDomain.includes(d))) {
      indicators.push({
        type: "user_email_domain",
        value: "education_domain",
        weight: 0.7,
      });
    } else if (CORPORATE_DOMAINS.some((d) => emailDomain.includes(d))) {
      indicators.push({
        type: "user_email_domain",
        value: "corporate_domain",
        weight: 0.6,
      });
    }

    // Check tenant name
    const nameLower = tenantName.toLowerCase();
    for (const [domain, keywords] of Object.entries(DOMAIN_KEYWORDS)) {
      if (keywords.some((k) => nameLower.includes(k))) {
        indicators.push({
          type: "content_keywords",
          value: `${domain}_in_name`,
          weight: 0.5,
        });
        break;
      }
    }

    return this.classifyDomain(indicators);
  }

  /**
   * Gather comprehensive tenant context
   */
  private async gatherTenantContext(tenantId: string): Promise<TenantContext> {
    // Get user statistics
    const userStats = await this.prisma.user.groupBy({
      by: ["tenantId"],
      where: { tenantId },
      _count: { id: true },
    });

    const userCount = userStats[0]?._count.id ?? 0;

    // Get email domains from users
    const users = await this.prisma.user.findMany({
      where: { tenantId },
      select: { email: true },
      take: 100,
    });

    const emailDomains = [...new Set(users.map((u) => u.email.split("@")[1]).filter((d): d is string => typeof d === "string"))];

    // Get content subjects
    const content = await this.prisma.contentAsset.findMany({
      where: { tenantId },
      select: { domain: true },
      take: 50,
    });

    const domainCounts: Record<string, number> = {};
    for (const item of content) {
      if (item.domain) {
        domainCounts[item.domain] = (domainCounts[item.domain] ?? 0) + 1;
      }
    }

    const topSubjects = Object.entries(domainCounts)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5)
      .map(([domain]) => domain);

    return {
      tenantId,
      name: "", // Would need tenant table
      userCount,
      emailDomains,
      topSubjects,
      contentCount: content.length,
    };
  }

  /**
   * Identify domain indicators from context
   */
  private async identifyIndicators(context: TenantContext): Promise<DomainIndicator[]> {
    const indicators: DomainIndicator[] = [];

    // Analyze email domains
    for (const domain of context.emailDomains) {
      if (EDUCATION_DOMAINS.some((d) => domain.includes(d))) {
        indicators.push({
          type: "user_email_domain",
          value: domain,
          weight: 0.7,
        });
      } else if (CORPORATE_DOMAINS.some((d) => domain.includes(d))) {
        indicators.push({
          type: "user_email_domain",
          value: domain,
          weight: 0.6,
        });
      }
    }

    // Analyze content domains
    for (const subject of context.topSubjects) {
      const subjectLower = subject.toLowerCase();

      // Check for grade level indicators in subjects
      if (/grade\s*[1-5]/i.test(subjectLower)) {
        indicators.push({
          type: "content_keywords",
          value: "K12_ELEMENTARY",
          weight: 0.8,
        });
      } else if (/grade\s*[6-8]/i.test(subjectLower)) {
        indicators.push({
          type: "content_keywords",
          value: "K12_MIDDLE",
          weight: 0.8,
        });
      } else if (/grade\s*(9|10|11|12)/i.test(subjectLower) || /high\s*school/i.test(subjectLower)) {
        indicators.push({
          type: "content_keywords",
          value: "K12_HIGH",
          weight: 0.8,
        });
      }
    }

    // Analyze user count patterns
    if (context.userCount > 1000) {
      indicators.push({
        type: "usage_patterns",
        value: "large_organization",
        weight: 0.3,
      });
    } else if (context.userCount < 50) {
      indicators.push({
        type: "usage_patterns",
        value: "small_organization",
        weight: 0.2,
      });
    }

    return indicators;
  }

  /**
   * Classify domain based on indicators
   */
  private classifyDomain(indicators: DomainIndicator[]): TenantDomain {
    // Score each domain
    const scores: Record<string, number> = {};

    for (const indicator of indicators) {
      // Map indicator to domain
      let domain: TenantDomain | null = null;

      if (indicator.value.includes("edu") || indicator.value.includes("K12")) {
        domain = indicator.value.includes("ELEMENTARY")
          ? "K12_ELEMENTARY"
          : indicator.value.includes("MIDDLE")
            ? "K12_MIDDLE"
            : indicator.value.includes("HIGH")
              ? "K12_HIGH"
              : "HIGHER_ED";
      } else if (indicator.value.includes("corporate") || indicator.value.includes("training")) {
        domain = "CORPORATE";
      }

      if (domain) {
        scores[domain] = (scores[domain] ?? 0) + indicator.weight;
      }
    }

    // Find highest scoring domain
    let bestDomain: TenantDomain = "UNKNOWN";
    let bestScore = 0;

    for (const [domain, score] of Object.entries(scores)) {
      if (score > bestScore) {
        bestScore = score;
        bestDomain = domain as TenantDomain;
      }
    }

    // If scores are close, mark as mixed
    const sortedScores = Object.entries(scores).sort((a, b) => b[1] - a[1]);
    if (sortedScores.length >= 2 && sortedScores[0]![1] - sortedScores[1]![1] < 0.3) {
      return "MIXED";
    }

    return bestDomain;
  }

  /**
   * Calculate confidence based on indicator strength
   */
  private calculateConfidence(indicators: DomainIndicator[]): number {
    if (indicators.length === 0) return 0;

    const totalWeight = indicators.reduce((sum, i) => sum + i.weight, 0);
    const maxPossibleWeight = indicators.length * 0.8; // Assuming max weight per indicator is ~0.8

    return Math.min(1, totalWeight / maxPossibleWeight);
  }

  /**
   * Get recommended features for domain
   */
  private getRecommendedFeatures(domain: TenantDomain): string[] {
    const featureMap: Record<TenantDomain, string[]> = {
      K12_ELEMENTARY: ["gamification", "visual_learning", "parent_portal", "progress_tracking"],
      K12_MIDDLE: ["collaboration_tools", "project_based", "interactive_content", "assessments"],
      K12_HIGH: ["exam_prep", "advanced_analytics", "career_exploration", "study_groups"],
      HIGHER_ED: ["research_tools", "academic_integrity", "advanced_analytics", "peer_review"],
      CORPORATE: ["compliance_tracking", "skills_assessment", "certification_paths", "reporting"],
      PROFESSIONAL_TRAINING: ["certification_prep", "continuing_ed", "license_tracking", "assessments"],
      LIFELONG_LEARNING: ["flexible_pacing", "interest_discovery", "community_features"],
      MIXED: ["adaptive_learning", "content_curation", "multi_domain_support"],
      UNKNOWN: ["onboarding_wizard", "domain_detection", "general_features"],
    };

    return featureMap[domain] ?? featureMap.UNKNOWN;
  }

  /**
   * Get suggested starter modules for domain
   */
  private getSuggestedStarterModules(domain: TenantDomain): string[] {
    const moduleMap: Record<TenantDomain, string[]> = {
      K12_ELEMENTARY: ["introduction_to_learning", "basic_math", "reading_fundamentals"],
      K12_MIDDLE: ["study_skills", "intro_to_science", "digital_literacy"],
      K12_HIGH: ["college_prep", "advanced_writing", "critical_thinking"],
      HIGHER_ED: ["research_methods", "academic_writing", "time_management"],
      CORPORATE: ["professional_communication", "diversity_training", "cybersecurity_basics"],
      PROFESSIONAL_TRAINING: ["certification_overview", "industry_standards", "best_practices"],
      LIFELONG_LEARNING: ["learning_strategies", "goal_setting", "skill_discovery"],
      MIXED: ["platform_introduction", "getting_started", "content_exploration"],
      UNKNOWN: ["welcome_tutorial", "getting_started_guide", "feature_tour"],
    };

    return moduleMap[domain] ?? moduleMap.UNKNOWN;
  }
}
