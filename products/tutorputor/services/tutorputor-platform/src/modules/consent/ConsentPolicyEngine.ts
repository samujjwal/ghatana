/**
 * Consent Policy Engine
 *
 * Manages granular consent categories for AI features.
 * Enforces consent policies for AI tutor, AI grading, and other AI-powered features.
 *
 * @doc.type class
 * @doc.purpose Granular consent policy enforcement for AI features
 * @doc.layer platform
 * @doc.pattern Service
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";

const logger = createStandaloneLogger({ component: "ConsentPolicyEngine" });

// ============================================================================
// Consent Categories
// ============================================================================

export enum ConsentCategory {
  AI_TUTOR = "ai_tutor",
  AI_GRADING = "ai_grading",
  AI_CONTENT_GENERATION = "ai_content_generation",
  AI_SIMULATION_GUIDANCE = "ai_simulation_guidance",
  AI_RECOMMENDATIONS = "ai_recommendations",
  AI_ANALYTICS = "ai_analytics",
  AI_VOICE_INTERACTION = "ai_voice_interaction",
  DATA_PROCESSING = "data_processing",
  TELEMTRY_COLLECTION = "telemetry_collection",
  PERSONALIZATION = "personalization",
}

export enum ConsentStatus {
  GRANTED = "granted",
  DENIED = "denied",
  PENDING = "pending",
  REVOKED = "revoked",
}

// ============================================================================
// Consent Types
// ============================================================================

export interface ConsentRecord {
  userId: string;
  tenantId: string;
  category: ConsentCategory;
  status: ConsentStatus;
  grantedAt?: Date;
  revokedAt?: Date;
  version: string;
  metadata: Record<string, unknown> | undefined;
}

export interface ConsentPolicy {
  category: ConsentCategory;
  required: boolean;
  description: string;
  version: string;
  requiresExplicitConsent: boolean;
  canBeRevoked: boolean;
  revocationEffect: "immediate" | "session_end" | "never";
}

export interface ConsentCheckResult {
  allowed: boolean;
  status: ConsentStatus;
  reason?: string;
  policy: ConsentPolicy;
}

// ============================================================================
// Consent Policy Engine
// ============================================================================

export class ConsentPolicyEngine {
  private static instance: ConsentPolicyEngine;
  private policies: Map<ConsentCategory, ConsentPolicy>;
  private userConsents: Map<string, Map<ConsentCategory, ConsentRecord>>;

  private constructor() {
    this.policies = new Map();
    this.userConsents = new Map();
    this.initializePolicies();
  }

  static getInstance(): ConsentPolicyEngine {
    if (!ConsentPolicyEngine.instance) {
      ConsentPolicyEngine.instance = new ConsentPolicyEngine();
    }
    return ConsentPolicyEngine.instance;
  }

  private initializePolicies(): void {
    // AI Tutor consent policy
    this.policies.set(ConsentCategory.AI_TUTOR, {
      category: ConsentCategory.AI_TUTOR,
      required: false,
      description: "AI-powered tutoring assistance",
      version: "1.0.0",
      requiresExplicitConsent: true,
      canBeRevoked: true,
      revocationEffect: "immediate",
    });

    // AI Grading consent policy
    this.policies.set(ConsentCategory.AI_GRADING, {
      category: ConsentCategory.AI_GRADING,
      required: false,
      description: "AI-powered assessment grading",
      version: "1.0.0",
      requiresExplicitConsent: true,
      canBeRevoked: true,
      revocationEffect: "session_end",
    });

    // AI Content Generation consent policy
    this.policies.set(ConsentCategory.AI_CONTENT_GENERATION, {
      category: ConsentCategory.AI_CONTENT_GENERATION,
      required: false,
      description: "AI-generated educational content",
      version: "1.0.0",
      requiresExplicitConsent: true,
      canBeRevoked: true,
      revocationEffect: "immediate",
    });

    // AI Simulation Guidance consent policy
    this.policies.set(ConsentCategory.AI_SIMULATION_GUIDANCE, {
      category: ConsentCategory.AI_SIMULATION_GUIDANCE,
      required: false,
      description: "AI guidance during simulations",
      version: "1.0.0",
      requiresExplicitConsent: true,
      canBeRevoked: true,
      revocationEffect: "immediate",
    });

    // AI Recommendations consent policy
    this.policies.set(ConsentCategory.AI_RECOMMENDATIONS, {
      category: ConsentCategory.AI_RECOMMENDATIONS,
      required: false,
      description: "Personalized AI recommendations",
      version: "1.0.0",
      requiresExplicitConsent: false,
      canBeRevoked: true,
      revocationEffect: "immediate",
    });

    // AI Analytics consent policy
    this.policies.set(ConsentCategory.AI_ANALYTICS, {
      category: ConsentCategory.AI_ANALYTICS,
      required: false,
      description: "AI-powered learning analytics",
      version: "1.0.0",
      requiresExplicitConsent: false,
      canBeRevoked: true,
      revocationEffect: "session_end",
    });

    // AI Voice Interaction consent policy
    this.policies.set(ConsentCategory.AI_VOICE_INTERACTION, {
      category: ConsentCategory.AI_VOICE_INTERACTION,
      required: false,
      description: "Voice interaction with AI",
      version: "1.0.0",
      requiresExplicitConsent: true,
      canBeRevoked: true,
      revocationEffect: "immediate",
    });

    // Data Processing consent policy
    this.policies.set(ConsentCategory.DATA_PROCESSING, {
      category: ConsentCategory.DATA_PROCESSING,
      required: true,
      description: "Essential data processing",
      version: "1.0.0",
      requiresExplicitConsent: false,
      canBeRevoked: false,
      revocationEffect: "never",
    });

    // Telemetry Collection consent policy
    this.policies.set(ConsentCategory.TELEMTRY_COLLECTION, {
      category: ConsentCategory.TELEMTRY_COLLECTION,
      required: false,
      description: "Telemetry data collection",
      version: "1.0.0",
      requiresExplicitConsent: false,
      canBeRevoked: true,
      revocationEffect: "session_end",
    });

    // Personalization consent policy
    this.policies.set(ConsentCategory.PERSONALIZATION, {
      category: ConsentCategory.PERSONALIZATION,
      required: false,
      description: "Personalized learning experience",
      version: "1.0.0",
      requiresExplicitConsent: false,
      canBeRevoked: true,
      revocationEffect: "immediate",
    });

    logger.info({
      message: "Consent policies initialized",
      policyCount: this.policies.size,
    });
  }

  /**
   * Check if a user has consent for a specific category
   */
  checkConsent(
    userId: string,
    tenantId: string,
    category: ConsentCategory,
  ): ConsentCheckResult {
    const policy = this.policies.get(category);
    if (!policy) {
      return {
        allowed: false,
        status: ConsentStatus.DENIED,
        reason: "Unknown consent category",
        policy: {
          category,
          required: false,
          description: "Unknown category",
          version: "1.0.0",
          requiresExplicitConsent: false,
          canBeRevoked: true,
          revocationEffect: "immediate",
        },
      };
    }

    // Required policies are always allowed (essential)
    if (policy.required) {
      return {
        allowed: true,
        status: ConsentStatus.GRANTED,
        reason: "Required consent category",
        policy,
      };
    }

    // Check user's consent record
    const userKey = `${tenantId}:${userId}`;
    const userConsents = this.userConsents.get(userKey);
    const consent = userConsents?.get(category);

    if (!consent) {
      return {
        allowed: false,
        status: ConsentStatus.PENDING,
        reason: "No consent record found",
        policy,
      };
    }

    if (consent.status === ConsentStatus.GRANTED) {
      return {
        allowed: true,
        status: ConsentStatus.GRANTED,
        policy,
      };
    }

    if (consent.status === ConsentStatus.DENIED || consent.status === ConsentStatus.REVOKED) {
      return {
        allowed: false,
        status: consent.status,
        reason: consent.status === ConsentStatus.REVOKED ? "Consent was revoked" : "Consent was denied",
        policy,
      };
    }

    return {
      allowed: false,
      status: consent.status,
      reason: "Consent is pending",
      policy,
    };
  }

  /**
   * Grant consent for a specific category
   */
  grantConsent(
    userId: string,
    tenantId: string,
    category: ConsentCategory,
    metadata?: Record<string, unknown>,
  ): ConsentRecord {
    const policy = this.policies.get(category);
    if (!policy) {
      throw new Error(`Unknown consent category: ${category}`);
    }

    const userKey = `${tenantId}:${userId}`;
    if (!this.userConsents.has(userKey)) {
      this.userConsents.set(userKey, new Map());
    }

    const consent: ConsentRecord = {
      userId,
      tenantId,
      category,
      status: ConsentStatus.GRANTED,
      grantedAt: new Date(),
      version: policy.version,
      metadata,
    };

    this.userConsents.get(userKey)!.set(category, consent);

    logger.info({
      message: "Consent granted",
      userId,
      tenantId,
      category,
      version: policy.version,
    });

    return consent;
  }

  /**
   * Revoke consent for a specific category
   */
  revokeConsent(
    userId: string,
    tenantId: string,
    category: ConsentCategory,
  ): ConsentRecord | null {
    const policy = this.policies.get(category);
    if (!policy) {
      throw new Error(`Unknown consent category: ${category}`);
    }

    if (!policy.canBeRevoked) {
      throw new Error(`Consent for ${category} cannot be revoked`);
    }

    const userKey = `${tenantId}:${userId}`;
    const userConsents = this.userConsents.get(userKey);
    const consent = userConsents?.get(category);

    if (!consent) {
      return null;
    }

    consent.status = ConsentStatus.REVOKED;
    consent.revokedAt = new Date();

    logger.info({
      message: "Consent revoked",
      userId,
      tenantId,
      category,
      revocationEffect: policy.revocationEffect,
    });

    return consent;
  }

  /**
   * Get all consent records for a user
   */
  getUserConsents(userId: string, tenantId: string): ConsentRecord[] {
    const userKey = `${tenantId}:${userId}`;
    const userConsents = this.userConsents.get(userKey);
    if (!userConsents) {
      return [];
    }
    return Array.from(userConsents.values());
  }

  /**
   * Get consent policy for a category
   */
  getPolicy(category: ConsentCategory): ConsentPolicy | undefined {
    return this.policies.get(category);
  }

  /**
   * Get all consent policies
   */
  getAllPolicies(): ConsentPolicy[] {
    return Array.from(this.policies.values());
  }

  /**
   * Grant consent for multiple categories at once
   */
  grantMultipleConsents(
    userId: string,
    tenantId: string,
    categories: ConsentCategory[],
    metadata?: Record<string, unknown>,
  ): ConsentRecord[] {
    return categories.map((category) =>
      this.grantConsent(userId, tenantId, category, metadata)
    );
  }

  /**
   * Check consent for multiple categories
   */
  checkMultipleConsents(
    userId: string,
    tenantId: string,
    categories: ConsentCategory[],
  ): Map<ConsentCategory, ConsentCheckResult> {
    const results = new Map<ConsentCategory, ConsentCheckResult>();
    for (const category of categories) {
      results.set(category, this.checkConsent(userId, tenantId, category));
    }
    return results;
  }

  /**
   * Check if all required consents are granted
   */
  hasAllRequiredConsents(userId: string, tenantId: string): boolean {
    for (const [category, policy] of this.policies.entries()) {
      if (policy.required) {
        continue; // Required consents are always allowed
      }
      const check = this.checkConsent(userId, tenantId, category);
      if (!check.allowed) {
        return false;
      }
    }
    return true;
  }
}

// Singleton instance
export function getConsentPolicyEngine(): ConsentPolicyEngine {
  return ConsentPolicyEngine.getInstance();
}
