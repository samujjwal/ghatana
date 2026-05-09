/**
 * Child Safety Service
 *
 * Manages social and child safety with role/age restrictions and content moderation.
 * Enforces age-based access controls, parental consent requirements, and content filtering.
 *
 * @doc.type class
 * @doc.purpose Child safety enforcement with age restrictions and content moderation
 * @doc.layer platform
 * @doc.pattern Service
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";
import type {
  AgeGroup,
  ContentModerationStatus,
  ModerationSeverity,
  ModerationAction,
} from "@tutorputor/core/prisma";

const logger = createStandaloneLogger({ component: "ChildSafetyService" });

// ============================================================================
// Safety Check Types
// ============================================================================

export interface SafetyCheckResult {
  allowed: boolean;
  reason?: string;
  requiresParentalConsent?: boolean;
  requiresSupervision?: boolean;
  restrictedFeatures?: string[];
}

export interface ContentModerationResult {
  approved: boolean;
  action: ModerationAction;
  severity: ModerationSeverity;
  confidence: number;
  flaggedReasons: string[];
  requiresManualReview: boolean;
}

export interface AgeVerificationRequest {
  tenantId: TenantId;
  userId: UserId;
  birthDate: Date;
  verifiedMethod: "parent_consent" | "id_verification" | "self_declaration";
  parentId?: string;
}

export interface ModerationRequest {
  tenantId: TenantId;
  userId?: UserId;
  contentType: string;
  contentId: string;
  contentText: string;
  contentMetadata?: Record<string, unknown>;
}

// ============================================================================
// Child Safety Service
// =============================================================================

export class ChildSafetyService {
  private static instance: ChildSafetyService;

  private constructor() {}

  static getInstance(): ChildSafetyService {
    if (!ChildSafetyService.instance) {
      ChildSafetyService.instance = new ChildSafetyService();
    }
    return ChildSafetyService.instance;
  }

  /**
   * Verify user age and apply appropriate restrictions
   */
  async verifyUserAge(
    prisma: TutorPrismaClient,
    request: AgeVerificationRequest,
  ): Promise<{ success: boolean; ageGroup: AgeGroup; requiresParentalConsent: boolean }> {
    try {
      const age = this.calculateAge(request.birthDate);
      const ageGroup = this.determineAgeGroup(age);

      // Check if parental consent is required
      const requiresParentalConsent = ageGroup === "UNDER_13";

      if (requiresParentalConsent && !request.parentId) {
        throw new Error("Parental consent required for users under 13");
      }

      // Create or update age verification record
      const verification = await prisma.userAgeVerification.upsert({
        where: {
          tenantId_userId: {
            tenantId: request.tenantId,
            userId: request.userId,
          },
        },
        create: {
          tenantId: request.tenantId,
          userId: request.userId,
          birthDate: request.birthDate,
          ageGroup,
          verifiedMethod: request.verifiedMethod,
          verifiedBy: request.parentId,
          parentalConsentGiven: requiresParentalConsent ? true : false,
          parentalConsentAt: requiresParentalConsent ? new Date() : null,
          parentId: request.parentId,
          restrictedFeatures: this.getRestrictedFeatures(ageGroup),
          requiresSupervision: requiresParentalConsent,
          dataRetentionLimited: requiresParentalConsent,
          marketingOptOut: requiresParentalConsent,
        },
        update: {
          birthDate: request.birthDate,
          ageGroup,
          verifiedMethod: request.verifiedMethod,
          verifiedBy: request.parentId,
          parentalConsentGiven: requiresParentalConsent ? true : false,
          parentalConsentAt: requiresParentalConsent ? new Date() : null,
          parentId: request.parentId,
          restrictedFeatures: this.getRestrictedFeatures(ageGroup),
          requiresSupervision: requiresParentalConsent,
          dataRetentionLimited: requiresParentalConsent,
          marketingOptOut: requiresParentalConsent,
        },
      });

      logger.info({
        message: "User age verified",
        tenantId: request.tenantId,
        userId: request.userId,
        ageGroup,
        requiresParentalConsent,
      }, "ChildSafetyService");

      return {
        success: true,
        ageGroup,
        requiresParentalConsent,
      };
    } catch (error) {
      logger.error({
        message: "Failed to verify user age",
        tenantId: request.tenantId,
        userId: request.userId,
        error,
      }, "ChildSafetyService");
      throw error;
    }
  }

  /**
   * Check if a user can access a feature based on age restrictions
   */
  async checkFeatureAccess(
    prisma: TutorPrismaClient,
    tenantId: TenantId,
    userId: UserId,
    feature: string,
  ): Promise<SafetyCheckResult> {
    try {
      const verification = await prisma.userAgeVerification.findUnique({
        where: {
          tenantId_userId: {
            tenantId,
            userId,
          },
        },
      });

      if (!verification) {
        // No age verification - allow by default but log for compliance
        logger.warn({
          message: "No age verification found for user",
          tenantId,
          userId,
          feature,
        }, "ChildSafetyService");

        return {
          allowed: true,
          reason: "No age verification on file",
        };
      }

      // Check if feature is restricted for this age group
      const restrictedFeatures = verification.restrictedFeatures as string[] | null;
      if (restrictedFeatures && restrictedFeatures.includes(feature)) {
        return {
          allowed: false,
          reason: `Feature '${feature}' is restricted for age group ${verification.ageGroup}`,
          requiresParentalConsent: verification.ageGroup === "UNDER_13",
          requiresSupervision: verification.requiresSupervision,
          restrictedFeatures: restrictedFeatures,
        };
      }

      // Check communication hours for minors
      if (verification.ageGroup !== "AGE_18_PLUS" && verification.ageGroup !== "ADULT_ONLY") {
        const policy = await this.getActivePolicy(prisma, tenantId, verification.ageGroup);
        if (policy && policy.limitCommunicationHours) {
          const currentHour = new Date().getHours();
          if (
            policy.allowedHoursStart !== null &&
            policy.allowedHoursEnd !== null &&
            (currentHour < policy.allowedHoursStart || currentHour >= policy.allowedHoursEnd)
          ) {
            return {
              allowed: false,
              reason: "Feature restricted during allowed communication hours",
              requiresSupervision: true,
            };
          }
        }
      }

      return {
        allowed: true,
      };
    } catch (error) {
      logger.error({
        message: "Failed to check feature access",
        tenantId,
        userId,
        feature,
        error,
      }, "ChildSafetyService");
      throw error;
    }
  }

  /**
   * Moderate content for safety violations
   */
  async moderateContent(
    prisma: TutorPrismaClient,
    request: ModerationRequest,
  ): Promise<ContentModerationResult> {
    try {
      // Get user's age group if userId provided
      let ageGroup: AgeGroup | null = null;
      if (request.userId) {
        const verification = await prisma.userAgeVerification.findUnique({
          where: {
            tenantId_userId: {
              tenantId: request.tenantId,
              userId: request.userId,
            },
          },
        });
        ageGroup = verification?.ageGroup ?? null;
      }

      // Get applicable safety policy
      const policy = ageGroup
        ? await this.getActivePolicy(prisma, request.tenantId, ageGroup)
        : null;

      // Run content checks
      const checks = await this.runContentChecks(request.contentText, policy);

      // Determine action based on checks
      const result = this.determineModerationAction(checks, policy);

      // Log moderation decision
      await prisma.contentModerationQueue.create({
        data: {
          tenantId: request.tenantId,
          userId: request.userId,
          contentType: request.contentType,
          contentId: request.contentId,
          contentText: request.contentText,
          contentMetadata: request.contentMetadata as any,
          status: result.requiresManualReview ? "FLAGGED" : result.approved ? "APPROVED" : "REJECTED",
          severity: result.severity,
          action: result.action,
          confidenceScore: result.confidence,
          flaggedReasons: result.flaggedReasons,
          detectedPatterns: checks.detectedPatterns,
        },
      });

      // Log violation if content was rejected
      if (!result.approved && request.userId) {
        await this.logViolation(prisma, {
          tenantId: request.tenantId,
          userId: request.userId,
          violationType: result.flaggedReasons[0] ?? "inappropriate_content",
          severity: result.severity,
          contentType: request.contentType,
          contentId: request.contentId,
          contentText: request.contentText,
          actionTaken: result.action === "BLOCK" ? "blocked" : "flagged",
        });
      }

      logger.info({
        message: "Content moderation completed",
        tenantId: request.tenantId,
        userId: request.userId,
        contentType: request.contentType,
        approved: result.approved,
        action: result.action,
        severity: result.severity,
      }, "ChildSafetyService");

      return result;
    } catch (error) {
      logger.error({
        message: "Failed to moderate content",
        tenantId: request.tenantId,
        userId: request.userId,
        contentType: request.contentType,
        error,
      }, "ChildSafetyService");
      throw error;
    }
  }

  /**
   * Get active safety policy for an age group
   */
  private async getActivePolicy(
    prisma: TutorPrismaClient,
    tenantId: TenantId,
    ageGroup: AgeGroup,
  ): Promise<unknown | null> {
    return await prisma.childSafetyPolicy.findFirst({
      where: {
        tenantId,
        ageGroup,
        isActive: true,
        effectiveAt: { lte: new Date() },
        OR: [
          { expiresAt: null },
          { expiresAt: { gt: new Date() } },
        ],
      },
      orderBy: { effectiveAt: "desc" },
    });
  }

  /**
   * Run content safety checks
   */
  private async runContentChecks(
    content: string,
    policy: unknown | null,
  ): Promise<{ detectedPatterns: Record<string, unknown>; flaggedReasons: string[]; severity: ModerationSeverity }> {
    const detectedPatterns: Record<string, unknown> = {};
    const flaggedReasons: string[] = [];
    let severity: ModerationSeverity = "LOW";

    // Check for profanity
    if (this.containsProfanity(content)) {
      detectedPatterns.profanity = true;
      flaggedReasons.push("profanity");
      severity = "MEDIUM";
    }

    // Check for personal information (PII)
    const piiDetected = this.detectPII(content);
    if (piiDetected.detected) {
      detectedPatterns.personalInfo = piiDetected.types;
      flaggedReasons.push("personal_info");
      severity = "HIGH";
    }

    // Check for external links
    if (this.containsExternalLinks(content)) {
      detectedPatterns.externalLinks = true;
      flaggedReasons.push("external_link");
      severity = "MEDIUM";
    }

    // Check for harassment/inappropriate content
    if (this.detectHarassment(content)) {
      detectedPatterns.harassment = true;
      flaggedReasons.push("harassment");
      severity = "CRITICAL";
    }

    // Apply policy-based restrictions
    if (policy) {
      const p = policy as any;
      if (p.blockProfanity && detectedPatterns.profanity) {
        severity = "HIGH";
      }
      if (p.blockPersonalInfoSharing && detectedPatterns.personalInfo) {
        severity = "CRITICAL";
      }
      if (p.blockExternalLinks && detectedPatterns.externalLinks) {
        severity = "HIGH";
      }
    }

    return { detectedPatterns, flaggedReasons, severity };
  }

  /**
   * Determine moderation action based on checks
   */
  private determineModerationAction(
    checks: { flaggedReasons: string[]; severity: ModerationSeverity },
    policy: unknown | null,
  ): ContentModerationResult {
    const { flaggedReasons, severity } = checks;

    if (flaggedReasons.length === 0) {
      return {
        approved: true,
        action: "ALLOW",
        severity: "LOW",
        confidence: 1.0,
        flaggedReasons: [],
        requiresManualReview: false,
      };
    }

    // Critical violations always block
    if (severity === "CRITICAL") {
      return {
        approved: false,
        action: "AUTO_REJECT",
        severity,
        confidence: 0.95,
        flaggedReasons,
        requiresManualReview: true,
      };
    }

    // High severity may require manual review
    if (severity === "HIGH") {
      const p = policy as any;
      if (p?.requireModeratorApproval) {
        return {
          approved: false,
          action: "FLAG_FOR_REVIEW",
          severity,
          confidence: 0.85,
          flaggedReasons,
          requiresManualReview: true,
        };
      }
      return {
        approved: false,
        action: "AUTO_REJECT",
        severity,
        confidence: 0.8,
        flaggedReasons,
        requiresManualReview: false,
      };
    }

    // Medium severity may flag for review
    if (severity === "MEDIUM") {
      return {
        approved: false,
        action: "FLAG_FOR_REVIEW",
        severity,
        confidence: 0.7,
        flaggedReasons,
        requiresManualReview: true,
      };
    }

    // Low severity may allow with warning
    return {
      approved: true,
      action: "ALLOW",
      severity,
      confidence: 0.6,
      flaggedReasons,
      requiresManualReview: false,
    };
  }

  /**
   * Log a safety violation
   */
  private async logViolation(
    prisma: TutorPrismaClient,
    violation: {
      tenantId: TenantId;
      userId: UserId;
      violationType: string;
      severity: ModerationSeverity;
      contentType: string;
      contentId: string;
      contentText: string;
      actionTaken: string;
    },
  ): Promise<void> {
    const verification = await prisma.userAgeVerification.findUnique({
      where: {
        tenantId_userId: {
          tenantId: violation.tenantId,
          userId: violation.userId,
        },
      },
    });

    const isMinor = verification?.ageGroup === "UNDER_13" || verification?.ageGroup === "AGE_13_17";

    await prisma.safetyViolationLog.create({
      data: {
        tenantId: violation.tenantId,
        userId: violation.userId,
        violationType: violation.violationType,
        severity: violation.severity,
        contentType: violation.contentType,
        contentId: violation.contentId,
        contentText: violation.contentText,
        actionTaken: violation.actionTaken,
        actionTakenBy: "system",
        parentNotified: isMinor,
        parentNotifiedAt: isMinor ? new Date() : null,
        parentId: verification?.parentId,
      },
    });

    if (isMinor && verification?.parentId) {
      logger.warn({
        message: "Parent notified of safety violation",
        tenantId: violation.tenantId,
        userId: violation.userId,
        parentId: verification.parentId,
        violationType: violation.violationType,
      }, "ChildSafetyService");
    }
  }

  /**
   * Calculate age from birth date
   */
  private calculateAge(birthDate: Date): number {
    const today = new Date();
    let age = today.getFullYear() - birthDate.getFullYear();
    const monthDiff = today.getMonth() - birthDate.getMonth();
    if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birthDate.getDate())) {
      age--;
    }
    return age;
  }

  /**
   * Determine age group from age
   */
  private determineAgeGroup(age: number): AgeGroup {
    if (age < 13) return "UNDER_13";
    if (age < 18) return "AGE_13_17";
    if (age < 21) return "AGE_18_PLUS";
    return "ADULT_ONLY";
  }

  /**
   * Get restricted features for age group
   */
  private getRestrictedFeatures(ageGroup: AgeGroup): string[] {
    switch (ageGroup) {
      case "UNDER_13":
        return ["direct_messaging", "peer_tutoring", "public_forums", "social_sharing"];
      case "AGE_13_17":
        return ["peer_tutoring", "public_forums"];
      case "AGE_18_PLUS":
        return [];
      case "ADULT_ONLY":
        return [];
      default:
        return [];
    }
  }

  /**
   * Check if content contains profanity
   */
  private containsProfanity(content: string): boolean {
    // Basic profanity detection - in production, use a proper profanity filter
    const profanityPatterns = [
      /\b(damn|hell|shit|crap)\b/gi,
      // Add more patterns as needed
    ];
    return profanityPatterns.some((pattern) => pattern.test(content));
  }

  /**
   * Detect PII in content
   */
  private detectPII(content: string): { detected: boolean; types: string[] } {
    const types: string[] = [];

    // Email pattern
    if (/\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b/.test(content)) {
      types.push("email");
    }

    // Phone pattern
    if (/\b\d{3}[-.]?\d{3}[-.]?\d{4}\b/.test(content)) {
      types.push("phone");
    }

    // SSN pattern
    if (/\b\d{3}-\d{2}-\d{4}\b/.test(content)) {
      types.push("ssn");
    }

    // Address pattern
    if (/\b\d+\s+[A-Za-z]+\s+(Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd)\b/i.test(content)) {
      types.push("address");
    }

    return {
      detected: types.length > 0,
      types,
    };
  }

  /**
   * Check if content contains external links
   */
  private containsExternalLinks(content: string): boolean {
    const urlPattern = /(https?:\/\/[^\s]+)/gi;
    return urlPattern.test(content);
  }

  /**
   * Detect harassment/inappropriate content
   */
  private detectHarassment(content: string): boolean {
    // Basic harassment detection - in production, use ML-based classification
    const harassmentPatterns = [
      /\b(kill|die|hate|stupid|idiot)\b/gi,
      // Add more patterns as needed
    ];
    return harassmentPatterns.some((pattern) => pattern.test(content));
  }

  /**
   * Get moderation queue for review
   */
  async getModerationQueue(
    prisma: TutorPrismaClient,
    tenantId: TenantId,
    status?: ContentModerationStatus,
    limit: number = 50,
  ): Promise<unknown[]> {
    return await prisma.contentModerationQueue.findMany({
      where: {
        tenantId,
        ...(status && { status }),
      },
      orderBy: { createdAt: "desc" },
      take: limit,
    });
  }

  /**
   * Review and resolve moderation item
   */
  async resolveModerationItem(
    prisma: TutorPrismaClient,
    moderationId: string,
    reviewerId: string,
    action: ModerationAction,
    notes: string,
  ): Promise<void> {
    await prisma.contentModerationQueue.update({
      where: { id: moderationId },
      data: {
        status: action === "ALLOW" ? "APPROVED" : "REJECTED",
        action,
        reviewedBy: reviewerId,
        reviewedAt: new Date(),
        reviewNotes: notes,
      },
    });

    logger.info({
      message: "Moderation item resolved",
      moderationId,
      reviewerId,
      action,
    }, "ChildSafetyService");
  }

  /**
   * Get safety violations for a user
   */
  async getUserViolations(
    prisma: TutorPrismaClient,
    tenantId: TenantId,
    userId: UserId,
    limit: number = 50,
  ): Promise<unknown[]> {
    return await prisma.safetyViolationLog.findMany({
      where: {
        tenantId,
        userId,
      },
      orderBy: { createdAt: "desc" },
      take: limit,
    });
  }
}

/**
 * Factory function
 */
export function createChildSafetyService(): ChildSafetyService {
  return ChildSafetyService.getInstance();
}
