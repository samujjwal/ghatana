/**
 * Template Marketplace Infrastructure
 *
 * Provides a comprehensive marketplace system for community contributions,
 * template sharing, rating, and automated quality assessment.
 *
 * @doc.type module
 * @doc.purpose Template marketplace infrastructure for community contributions
 * @doc.layer product
 * @doc.pattern Marketplace
 */

import type { SimulationDomain } from "@ghatana/tutorputor-contracts/v1/simulation/types";
import {
  templateLibrary,
  type EnhancedTemplateMetadata,
  type TemplateManifestBlueprint,
} from "../templates/enhanced-template-library";

/**
 * Marketplace template with additional marketplace metadata
 */
export interface MarketplaceTemplate extends EnhancedTemplateMetadata {
  /** Manifest blueprint (inherited from template library entry) */
  manifest?: TemplateManifestBlueprint;
  /** Marketplace-specific metadata */
  marketplace: {
    /** Template ID in marketplace */
    marketplaceId: string;
    /** Author information */
    author: {
      id: string;
      name: string;
      email: string;
      organization?: string;
      bio?: string;
      website?: string;
      reputation: number;
    };
    /** Community metrics */
    community: {
      downloads: number;
      ratings: number;
      averageRating: number;
      reviews: TemplateReview[];
      forks: number;
      stars: number;
      contributors: Contributor[];
    };
    /** Licensing */
    licensing: {
      license: string;
      commercialUse: boolean;
      attributionRequired: boolean;
      derivativeWorks: boolean;
    };
    /** Version history */
    versions: TemplateVersion[];
    /** Tags and categories */
    tags: string[];
    categories: string[];
    /** Submission metadata */
    submission: {
      submittedAt: string;
      submittedBy: string;
      reviewedAt?: string;
      reviewedBy?: string;
      status: "pending" | "approved" | "rejected" | "archived";
      rejectionReason?: string;
      /** Optional reviewer notes (used for approval feedback) */
      reviewNotes?: string;
    };
  };
}

/**
 * Template review
 */
export interface TemplateReview {
  id: string;
  templateId: string;
  reviewerId: string;
  reviewerName: string;
  rating: number; // 1-5
  title: string;
  content: string;
  createdAt: string;
  helpful: number;
  verified: boolean;
}

/**
 * Template contributor
 */
export interface Contributor {
  id: string;
  name: string;
  role: string;
  contributions: string[];
  joinedAt: string;
}

/**
 * Template version
 */
export interface TemplateVersion {
  version: string;
  changelog: string;
  releasedAt: string;
  releasedBy: string;
  downloadCount: number;
  breakingChanges: boolean;
}

/**
 * Marketplace configuration
 */
export interface MarketplaceConfig {
  /** Enable community contributions */
  enableCommunityContributions: boolean;
  /** Review process required */
  reviewRequired: boolean;
  /** Auto-approval criteria */
  autoApprovalCriteria: AutoApprovalCriteria;
  /** Quality thresholds */
  qualityThresholds: {
    minRating: number;
    minDownloads: number;
    maxReports: number;
  };
  /** Moderation settings */
  moderation: {
    requireVerification: boolean;
    autoFlagSuspicious: boolean;
    reportThreshold: number;
  };
}

/**
 * Auto-approval criteria
 */
export interface AutoApprovalCriteria {
  /** Minimum confidence score */
  minConfidenceScore: number;
  /** Required quality metrics */
  requiredQualityMetrics: string[];
  /** Blacklisted keywords */
  blacklistedKeywords: string[];
  /** Required template structure */
  requiredStructure: string[];
}

/**
 * Template marketplace service
 */
export class TemplateMarketplace {
  private config: MarketplaceConfig;
  private templates: Map<string, MarketplaceTemplate> = new Map();
  private pendingSubmissions: Map<string, MarketplaceTemplate> = new Map();
  private userReputations: Map<string, UserReputation> = new Map();
  private qualityAssessor: TemplateQualityAssessor;

  constructor(config: MarketplaceConfig) {
    this.config = config;
    this.qualityAssessor = new TemplateQualityAssessor();
    this.initializeMarketplace();
  }

  /**
   * Submit template to marketplace
   */
  async submitTemplate(
    template: Omit<MarketplaceTemplate, "marketplace">,
    authorInfo: MarketplaceTemplate["marketplace"]["author"],
  ): Promise<string> {
    const marketplaceTemplate: MarketplaceTemplate = {
      ...template,
      marketplace: {
        marketplaceId: this.generateMarketplaceId(),
        author: authorInfo,
        community: {
          downloads: 0,
          ratings: 0,
          averageRating: 0,
          reviews: [],
          forks: 0,
          stars: 0,
          contributors: [],
        },
        licensing: {
          license: "CC BY-SA 4.0",
          commercialUse: true,
          attributionRequired: true,
          derivativeWorks: true,
        },
        versions: [
          {
            version: template.version,
            changelog: "Initial release",
            releasedAt: new Date().toISOString(),
            releasedBy: authorInfo.id,
            downloadCount: 0,
            breakingChanges: false,
          },
        ],
        tags: template.tags,
        categories: this.categorizeTemplate(template),
        submission: {
          submittedAt: new Date().toISOString(),
          submittedBy: authorInfo.id,
          status: "pending",
        },
      },
    };

    // Quality assessment
    const qualityResult =
      await this.qualityAssessor.assessTemplate(marketplaceTemplate);

    // Auto-approval if criteria met
    if (this.meetsAutoApprovalCriteria(marketplaceTemplate, qualityResult)) {
      marketplaceTemplate.marketplace.submission.status = "approved";
      marketplaceTemplate.marketplace.submission.reviewedAt =
        new Date().toISOString();
      marketplaceTemplate.marketplace.submission.reviewedBy = "auto-approval";
      this.templates.set(
        marketplaceTemplate.marketplace.marketplaceId,
        marketplaceTemplate,
      );
    } else {
      this.pendingSubmissions.set(
        marketplaceTemplate.marketplace.marketplaceId,
        marketplaceTemplate,
      );
    }

    return marketplaceTemplate.marketplace.marketplaceId;
  }

  /**
   * Search marketplace templates
   */
  searchTemplates(criteria: MarketplaceSearchCriteria): MarketplaceTemplate[] {
    let results = Array.from(this.templates.values());

    // Filter by domain
    if (criteria.domain) {
      results = results.filter(
        (template) => template.domain === criteria.domain,
      );
    }

    // Filter by categories
    if (criteria.categories && criteria.categories.length > 0) {
      results = results.filter((template) =>
        criteria.categories!.some((category) =>
          template.marketplace.categories.includes(category),
        ),
      );
    }

    // Filter by tags
    if (criteria.tags && criteria.tags.length > 0) {
      results = results.filter((template) =>
        criteria.tags!.some((tag) => template.marketplace.tags.includes(tag)),
      );
    }

    // Filter by rating
    if (criteria.minRating) {
      results = results.filter(
        (template) =>
          template.marketplace.community.averageRating >= criteria.minRating!,
      );
    }

    // Filter by license
    if (criteria.license) {
      results = results.filter(
        (template) =>
          template.marketplace.licensing.license === criteria.license,
      );
    }

    // Filter by author
    if (criteria.authorId) {
      results = results.filter(
        (template) => template.marketplace.author.id === criteria.authorId,
      );
    }

    // Sort results
    results = this.sortResults(results, criteria.sortBy);

    // Apply pagination
    if (criteria.offset || criteria.limit) {
      const offset = criteria.offset || 0;
      const limit = criteria.limit || 20;
      results = results.slice(offset, offset + limit);
    }

    return results;
  }

  /**
   * Get template by marketplace ID
   */
  getTemplate(marketplaceId: string): MarketplaceTemplate | undefined {
    return this.templates.get(marketplaceId);
  }

  /**
   * Rate template
   */
  async rateTemplate(
    marketplaceId: string,
    userId: string,
    rating: number,
    review?: string,
  ): Promise<void> {
    const template = this.templates.get(marketplaceId);
    if (!template) {
      throw new Error("Template not found");
    }

    // Check if user already rated
    const existingReview = template.marketplace.community.reviews.find(
      (r) => r.reviewerId === userId,
    );
    if (existingReview) {
      throw new Error("User has already rated this template");
    }

    // Create new review
    const newReview: TemplateReview = {
      id: this.generateReviewId(),
      templateId: marketplaceId,
      reviewerId: userId,
      reviewerName: this.getUserName(userId),
      rating,
      title: review ? `Rating ${rating} stars` : "",
      content: review || "",
      createdAt: new Date().toISOString(),
      helpful: 0,
      verified: this.isVerifiedUser(userId),
    };

    // Add review
    template.marketplace.community.reviews.push(newReview);
    template.marketplace.community.ratings++;

    // Update average rating
    const totalRating = template.marketplace.community.reviews.reduce(
      (sum, r) => sum + r.rating,
      0,
    );
    template.marketplace.community.averageRating =
      totalRating / template.marketplace.community.reviews.length;

    // Update author reputation
    this.updateAuthorReputation(template.marketplace.author.id, rating);
  }

  /**
   * Download template
   */
  async downloadTemplate(
    marketplaceId: string,
    userId: string,
  ): Promise<MarketplaceTemplate> {
    const template = this.templates.get(marketplaceId);
    if (!template) {
      throw new Error("Template not found");
    }

    // Increment download count
    template.marketplace.community.downloads++;

    // Update version download count
    const currentVersion =
      template.marketplace.versions[template.marketplace.versions.length - 1];
    currentVersion.downloadCount++;

    // Track user download history
    this.trackUserDownload(userId, marketplaceId);

    return template;
  }

  /**
   * Fork template
   */
  async forkTemplate(
    marketplaceId: string,
    userId: string,
    modifications: Partial<MarketplaceTemplate>,
  ): Promise<string> {
    const originalTemplate = this.templates.get(marketplaceId);
    if (!originalTemplate) {
      throw new Error("Template not found");
    }

    // Create fork
    const forkedTemplate: MarketplaceTemplate = {
      ...originalTemplate,
      ...modifications,
      id: this.generateTemplateId(),
      marketplace: {
        ...originalTemplate.marketplace,
        marketplaceId: this.generateMarketplaceId(),
        author: {
          ...originalTemplate.marketplace.author,
          id: userId,
          name: this.getUserName(userId),
        },
        community: {
          ...originalTemplate.marketplace.community,
          downloads: 0,
          ratings: 0,
          averageRating: 0,
          reviews: [],
          forks: originalTemplate.marketplace.community.forks + 1,
          stars: 0,
        },
        submission: {
          submittedAt: new Date().toISOString(),
          submittedBy: userId,
          status: "pending",
        },
      },
    };

    // Add fork relationship
    forkedTemplate.marketplace.tags.push("fork");
    forkedTemplate.marketplace.tags.push(
      `fork-of:${originalTemplate.marketplace.marketplaceId}`,
    );

    // Submit fork for review
    return await this.submitTemplate(
      forkedTemplate,
      forkedTemplate.marketplace.author,
    );
  }

  /**
   * Report template
   */
  async reportTemplate(
    marketplaceId: string,
    userId: string,
    reason: string,
    description: string,
  ): Promise<void> {
    const template = this.templates.get(marketplaceId);
    if (!template) {
      throw new Error("Template not found");
    }

    // Create report
    const report = {
      id: this.generateReportId(),
      templateId: marketplaceId,
      reporterId: userId,
      reason,
      description,
      createdAt: new Date().toISOString(),
      status: "pending" as const,
    };

    // Auto-flag if suspicious
    if (
      this.config.moderation.autoFlagSuspicious &&
      this.isSuspiciousReport(report)
    ) {
      template.marketplace.submission.status = "archived";
    }

    // Check report threshold
    const reportCount = this.getReportCount(marketplaceId);
    if (reportCount >= this.config.moderation.reportThreshold) {
      template.marketplace.submission.status = "archived";
    }
  }

  /**
   * Get pending submissions for review
   */
  getPendingSubmissions(): MarketplaceTemplate[] {
    return Array.from(this.pendingSubmissions.values());
  }

  /**
   * Approve pending submission
   */
  async approveSubmission(
    marketplaceId: string,
    reviewerId: string,
    feedback?: string,
  ): Promise<void> {
    const template = this.pendingSubmissions.get(marketplaceId);
    if (!template) {
      throw new Error("Pending submission not found");
    }

    template.marketplace.submission.status = "approved";
    template.marketplace.submission.reviewedAt = new Date().toISOString();
    template.marketplace.submission.reviewedBy = reviewerId;

    if (feedback) {
      template.marketplace.submission.reviewNotes = feedback;
    }

    // Move to approved templates
    this.templates.set(marketplaceId, template);
    this.pendingSubmissions.delete(marketplaceId);

    // Update author reputation
    this.updateAuthorReputation(template.marketplace.author.id, 5);
  }

  /**
   * Reject pending submission
   */
  async rejectSubmission(
    marketplaceId: string,
    reviewerId: string,
    reason: string,
  ): Promise<void> {
    const template = this.pendingSubmissions.get(marketplaceId);
    if (!template) {
      throw new Error("Pending submission not found");
    }

    template.marketplace.submission.status = "rejected";
    template.marketplace.submission.reviewedAt = new Date().toISOString();
    template.marketplace.submission.reviewedBy = reviewerId;
    template.marketplace.submission.rejectionReason = reason;

    // Remove from pending
    this.pendingSubmissions.delete(marketplaceId);

    // Update author reputation
    this.updateAuthorReputation(template.marketplace.author.id, -2);
  }

  /**
   * Get marketplace statistics
   */
  getMarketplaceStatistics(): MarketplaceStatistics {
    const templates = Array.from(this.templates.values());
    const pending = Array.from(this.pendingSubmissions.values());

    return {
      totalTemplates: templates.length,
      pendingSubmissions: pending.length,
      totalDownloads: templates.reduce(
        (sum, t) => sum + t.marketplace.community.downloads,
        0,
      ),
      totalRatings: templates.reduce(
        (sum, t) => sum + t.marketplace.community.ratings,
        0,
      ),
      averageRating:
        templates.length > 0
          ? templates.reduce(
              (sum, t) => sum + t.marketplace.community.averageRating,
              0,
            ) / templates.length
          : 0,
      topAuthors: this.getTopAuthors(templates),
      popularCategories: this.getPopularCategories(templates),
      recentActivity: this.getRecentActivity(templates),
    };
  }

  /**
   * Initialize marketplace
   */
  private initializeMarketplace(): void {
    // Add featured templates from the library
    const featuredTemplates = templateLibrary.getTemplates();
    featuredTemplates.forEach((template) => {
      if (template.quality.confidenceScore > 0.8) {
        this.addFeaturedTemplate(template);
      }
    });
  }

  /**
   * Add featured template from library
   */
  private addFeaturedTemplate(
    template: EnhancedTemplateMetadata & {
      manifest: TemplateManifestBlueprint;
    },
  ): void {
    const marketplaceTemplate: MarketplaceTemplate = {
      ...template,
      marketplace: {
        marketplaceId: this.generateMarketplaceId(),
        author: {
          id: "tutorputor-team",
          name: "TutorPutor Team",
          email: "team@tutorputor.com",
          organization: "TutorPutor",
          reputation: 100,
        },
        community: {
          downloads: template.quality.usageCount,
          ratings: Math.floor(template.quality.usageCount * 0.1), // Estimate ratings
          averageRating: template.quality.averageRating,
          reviews: [],
          forks: 0,
          stars: Math.floor(template.quality.usageCount * 0.05),
          contributors: [],
        },
        licensing: {
          license: "CC BY-SA 4.0",
          commercialUse: true,
          attributionRequired: true,
          derivativeWorks: true,
        },
        versions: [
          {
            version: template.version,
            changelog: "Initial release",
            releasedAt: new Date().toISOString(),
            releasedBy: "tutorputor-team",
            downloadCount: template.quality.usageCount,
            breakingChanges: false,
          },
        ],
        tags: template.tags,
        categories: this.categorizeTemplate(template),
        submission: {
          submittedAt:
            template.governance.reviewedAt || new Date().toISOString(),
          submittedBy: "tutorputor-team",
          reviewedAt: template.governance.reviewedAt,
          reviewedBy: template.governance.reviewedBy,
          status: "approved",
        },
      },
    };

    this.templates.set(
      marketplaceTemplate.marketplace.marketplaceId,
      marketplaceTemplate,
    );
  }

  /**
   * Check if template meets auto-approval criteria
   */
  private meetsAutoApprovalCriteria(
    template: MarketplaceTemplate,
    qualityResult: QualityAssessmentResult,
  ): boolean {
    if (!this.config.reviewRequired) return true;

    const criteria = this.config.autoApprovalCriteria;

    // Check confidence score
    if (template.quality.confidenceScore < criteria.minConfidenceScore) {
      return false;
    }

    // Check quality metrics
    for (const metric of criteria.requiredQualityMetrics) {
      if (
        !(metric in qualityResult.metrics) ||
        qualityResult.metrics[metric] < 0.8
      ) {
        return false;
      }
    }

    // Check for blacklisted keywords
    const combinedText =
      `${template.name} ${template.description} ${template.tags.join(" ")}`.toLowerCase();
    for (const keyword of criteria.blacklistedKeywords) {
      if (combinedText.includes(keyword.toLowerCase())) {
        return false;
      }
    }

    // Check required structure
    for (const structure of criteria.requiredStructure) {
      if (!(structure in template)) {
        return false;
      }
    }

    return true;
  }

  /**
   * Categorize template
   */
  private categorizeTemplate(template: EnhancedTemplateMetadata): string[] {
    const categories: string[] = [];

    // Domain-based categories
    categories.push(template.domain.toLowerCase());

    // Difficulty-based categories
    categories.push(template.difficulty);

    // Content type categories
    if (template.contentTypes.simulation) categories.push("simulation");
    if (template.contentTypes.examples) categories.push("examples");
    if (template.contentTypes.animation) categories.push("animation");

    // Grade level categories
    template.gradeLevels.forEach((grade) => {
      if (grade.includes("K") || grade.includes("1") || grade.includes("2")) {
        categories.push("elementary");
      } else if (
        grade.includes("3") ||
        grade.includes("4") ||
        grade.includes("5")
      ) {
        categories.push("middle-school");
      } else if (
        grade.includes("6") ||
        grade.includes("7") ||
        grade.includes("8")
      ) {
        categories.push("high-school");
      } else {
        categories.push("advanced");
      }
    });

    return [...new Set(categories)]; // Remove duplicates
  }

  /**
   * Sort search results
   */
  private sortResults(
    templates: MarketplaceTemplate[],
    sortBy?: string,
  ): MarketplaceTemplate[] {
    switch (sortBy) {
      case "rating":
        return templates.sort(
          (a, b) =>
            b.marketplace.community.averageRating -
            a.marketplace.community.averageRating,
        );
      case "downloads":
        return templates.sort(
          (a, b) =>
            b.marketplace.community.downloads -
            a.marketplace.community.downloads,
        );
      case "recent":
        return templates.sort(
          (a, b) =>
            new Date(b.marketplace.submission.submittedAt).getTime() -
            new Date(a.marketplace.submission.submittedAt).getTime(),
        );
      case "name":
        return templates.sort((a, b) => a.name.localeCompare(b.name));
      default:
        return templates.sort(
          (a, b) => b.quality.confidenceScore - a.quality.confidenceScore,
        );
    }
  }

  /**
   * Update author reputation
   */
  private updateAuthorReputation(authorId: string, ratingChange: number): void {
    const reputation = this.userReputations.get(authorId) || {
      userId: authorId,
      reputation: 50,
      contributions: 0,
      averageRating: 0,
      joinedAt: new Date().toISOString(),
    };

    reputation.reputation = Math.max(
      0,
      Math.min(100, reputation.reputation + ratingChange),
    );
    reputation.contributions++;

    this.userReputations.set(authorId, reputation);
  }

  // Helper methods (simplified implementations)
  private generateMarketplaceId(): string {
    return `mp_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }
  private generateReviewId(): string {
    return `rev_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }
  private generateReportId(): string {
    return `rep_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }
  private generateTemplateId(): string {
    return `tpl_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }
  private getUserName(userId: string): string {
    return `User_${userId.substr(0, 8)}`;
  }
  private isVerifiedUser(userId: string): boolean {
    return (this.userReputations.get(userId)?.reputation ?? 0) > 70;
  }
  private trackUserDownload(userId: string, templateId: string): void {
    /* Track download */
  }
  private isSuspiciousReport(report: any): boolean {
    return false;
  }
  private getReportCount(templateId: string): number {
    return 0;
  }
  private getTopAuthors(templates: MarketplaceTemplate[]): any[] {
    return [];
  }
  private getPopularCategories(templates: MarketplaceTemplate[]): any[] {
    return [];
  }
  private getRecentActivity(templates: MarketplaceTemplate[]): any[] {
    return [];
  }
}

/**
 * Marketplace search criteria
 */
export interface MarketplaceSearchCriteria {
  domain?: SimulationDomain;
  categories?: string[];
  tags?: string[];
  minRating?: number;
  license?: string;
  authorId?: string;
  sortBy?: "rating" | "downloads" | "recent" | "name" | "quality";
  offset?: number;
  limit?: number;
}

/**
 * Marketplace statistics
 */
export interface MarketplaceStatistics {
  totalTemplates: number;
  pendingSubmissions: number;
  totalDownloads: number;
  totalRatings: number;
  averageRating: number;
  topAuthors: any[];
  popularCategories: any[];
  recentActivity: any[];
}

/**
 * User reputation
 */
export interface UserReputation {
  userId: string;
  reputation: number;
  contributions: number;
  averageRating: number;
  joinedAt: string;
}

/**
 * Template quality assessor
 */
export class TemplateQualityAssessor {
  async assessTemplate(
    template: MarketplaceTemplate,
  ): Promise<QualityAssessmentResult> {
    // Simplified quality assessment
    return {
      overallScore: template.quality.confidenceScore,
      metrics: {
        completeness: this.assessCompleteness(template),
        educationalValue: this.assessEducationalValue(template),
        technicalQuality: this.assessTechnicalQuality(template),
        accessibility: this.assessAccessibility(template),
      },
      issues: [],
      recommendations: [],
    };
  }

  private assessCompleteness(template: MarketplaceTemplate): number {
    let score = 0;
    if (template.name) score += 0.2;
    if (template.description) score += 0.2;
    if (template.learningObjectives.length > 0) score += 0.2;
    if (template.manifest?.initialEntities) score += 0.2;
    if (template.manifest?.steps) score += 0.2;
    return score;
  }

  private assessEducationalValue(template: MarketplaceTemplate): number {
    return template.quality.confidenceScore;
  }

  private assessTechnicalQuality(template: MarketplaceTemplate): number {
    return template.quality.successRate / 100;
  }

  private assessAccessibility(template: MarketplaceTemplate): number {
    return template.manifest?.accessibility ? 0.8 : 0.4;
  }
}

/**
 * Quality assessment result
 */
export interface QualityAssessmentResult {
  overallScore: number;
  metrics: Record<string, number>;
  issues: string[];
  recommendations: string[];
}

/**
 * Default marketplace configuration
 */
export const DEFAULT_MARKETPLACE_CONFIG: MarketplaceConfig = {
  enableCommunityContributions: true,
  reviewRequired: true,
  autoApprovalCriteria: {
    minConfidenceScore: 0.8,
    requiredQualityMetrics: [
      "completeness",
      "educationalValue",
      "technicalQuality",
    ],
    blacklistedKeywords: ["spam", "advertisement", "inappropriate"],
    requiredStructure: ["name", "description", "domain", "manifest"],
  },
  qualityThresholds: {
    minRating: 3.0,
    minDownloads: 10,
    maxReports: 5,
  },
  moderation: {
    requireVerification: false,
    autoFlagSuspicious: true,
    reportThreshold: 3,
  },
};
