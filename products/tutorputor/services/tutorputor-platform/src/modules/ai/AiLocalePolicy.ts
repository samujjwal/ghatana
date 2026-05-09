/**
 * AI Locale Policy
 *
 * Manages locale-aware AI response policies including language, units,
 * reading level, and cultural context for AI-generated content.
 *
 * @doc.type class
 * @doc.purpose Locale-aware AI response policy enforcement
 * @doc.layer platform
 * @doc.pattern Policy
 */

import { z } from "zod";
import { createStandaloneLogger } from "@tutorputor/core/logger";

const logger = createStandaloneLogger({ component: "AiLocalePolicy" });

// ============================================================================
// Locale Policy Types
// ============================================================================

export enum UnitSystem {
  METRIC = "metric",
  IMPERIAL = "imperial",
}

export enum ReadingLevel {
  ELEMENTARY = "elementary",
  MIDDLE = "middle",
  HIGH = "high",
  COLLEGE = "college",
  PROFESSIONAL = "professional",
}

export interface LocalePolicy {
  locale: string;
  language: string;
  unitSystem: UnitSystem;
  readingLevel: ReadingLevel;
  culturalContext: string[];
  dateFormat: string;
  numberFormat: string;
  currencyCode: string;
}

export interface LocalePolicyRequest {
  locale: string;
  userId?: string;
  tenantId?: string;
  context?: "tutor" | "assessment" | "content" | "simulation";
}

export interface LocalePolicyResponse {
  policy: LocalePolicy;
  applied: boolean;
  warnings: string[];
}

// ============================================================================
// Locale Policy Configuration
// ============================================================================

const DEFAULT_POLICIES: Record<string, Partial<LocalePolicy>> = {
  en: {
    locale: "en",
    language: "English",
    unitSystem: UnitSystem.IMPERIAL,
    readingLevel: ReadingLevel.MIDDLE,
    culturalContext: ["western", "us-centric"],
    dateFormat: "MM/DD/YYYY",
    numberFormat: "#,##0.###",
    currencyCode: "USD",
  },
  es: {
    locale: "es",
    language: "Spanish",
    unitSystem: UnitSystem.METRIC,
    readingLevel: ReadingLevel.MIDDLE,
    culturalContext: ["hispanic", "latin-american"],
    dateFormat: "DD/MM/YYYY",
    numberFormat: "#.##0,###",
    currencyCode: "EUR",
  },
  hi: {
    locale: "hi",
    language: "Hindi",
    unitSystem: UnitSystem.METRIC,
    readingLevel: ReadingLevel.MIDDLE,
    culturalContext: ["indian", "south-asian"],
    dateFormat: "DD/MM/YYYY",
    numberFormat: "#,##0.###",
    currencyCode: "INR",
  },
  zh: {
    locale: "zh",
    language: "Chinese",
    unitSystem: UnitSystem.METRIC,
    readingLevel: ReadingLevel.MIDDLE,
    culturalContext: ["east-asian", "chinese"],
    dateFormat: "YYYY/MM/DD",
    numberFormat: "#,##0.###",
    currencyCode: "CNY",
  },
};

// ============================================================================
// AI Locale Policy
// ============================================================================

export class AiLocalePolicy {
  private static instance: AiLocalePolicy;
  private customPolicies: Map<string, LocalePolicy>;
  private tenantOverrides: Map<string, Map<string, Partial<LocalePolicy>>>;

  private constructor() {
    this.customPolicies = new Map();
    this.tenantOverrides = new Map();
  }

  static getInstance(): AiLocalePolicy {
    if (!AiLocalePolicy.instance) {
      AiLocalePolicy.instance = new AiLocalePolicy();
    }
    return AiLocalePolicy.instance;
  }

  /**
   * Get locale policy for a request
   */
  getPolicy(request: LocalePolicyRequest): LocalePolicyResponse {
    const warnings: string[] = [];
    let policy: LocalePolicy;

    // Check tenant-specific overrides first
    if (request.tenantId && this.tenantOverrides.has(request.tenantId)) {
      const tenantPolicies = this.tenantOverrides.get(request.tenantId)!;
      const tenantPolicy = tenantPolicies.get(request.locale);
      if (tenantPolicy) {
        const basePolicy = DEFAULT_POLICIES[request.locale] || DEFAULT_POLICIES.en;
        if (basePolicy) {
          policy = this.mergePolicy(basePolicy, tenantPolicy);
          logger.info({
            message: "Using tenant-specific locale policy",
            tenantId: request.tenantId,
            locale: request.locale,
          }, "AiLocalePolicy");
          return { policy, applied: true, warnings };
        }
      }
    }

    // Check custom policies
    if (this.customPolicies.has(request.locale)) {
      policy = this.customPolicies.get(request.locale)!;
      logger.info({
        message: "Using custom locale policy",
        locale: request.locale,
      }, "AiLocalePolicy");
      return { policy, applied: true, warnings };
    }

    // Use default policy
    const defaultPolicy = DEFAULT_POLICIES[request.locale];
    if (defaultPolicy) {
      policy = this.completePolicy(defaultPolicy);
      logger.info({
        message: "Using default locale policy",
        locale: request.locale,
      }, "AiLocalePolicy");
      return { policy, applied: true, warnings };
    }

    // Fallback to English if locale not found
    warnings.push(`Locale '${request.locale}' not found, falling back to 'en'`);
    const fallbackPolicy = DEFAULT_POLICIES.en;
    if (fallbackPolicy) {
      policy = this.completePolicy(fallbackPolicy);
    } else {
      // Ultimate fallback with hardcoded defaults
      policy = this.completePolicy({});
    }
    logger.warn({
      message: "Locale not found, falling back to English",
      requestedLocale: request.locale,
    }, "AiLocalePolicy");

    return { policy, applied: false, warnings };
  }

  /**
   * Apply locale policy to AI request context
   */
  applyToContext(request: LocalePolicyRequest, context: Record<string, unknown>): Record<string, unknown> {
    const { policy } = this.getPolicy(request);

    return {
      ...context,
      locale: policy.locale,
      language: policy.language,
      unitSystem: policy.unitSystem,
      readingLevel: policy.readingLevel,
      culturalContext: policy.culturalContext,
      dateFormat: policy.dateFormat,
      numberFormat: policy.numberFormat,
      currencyCode: policy.currencyCode,
    };
  }

  /**
   * Register a custom locale policy
   */
  registerCustomPolicy(locale: string, policy: Partial<LocalePolicy>): void {
    const completePolicy = this.completePolicy(policy);
    this.customPolicies.set(locale, completePolicy);
    logger.info({
      message: "Custom locale policy registered",
      locale,
    }, "AiLocalePolicy");
  }

  /**
   * Register a tenant-specific override
   */
  registerTenantOverride(tenantId: string, locale: string, override: Partial<LocalePolicy>): void {
    if (!this.tenantOverrides.has(tenantId)) {
      this.tenantOverrides.set(tenantId, new Map());
    }
    this.tenantOverrides.get(tenantId)!.set(locale, override);
    logger.info({
      message: "Tenant locale policy override registered",
      tenantId,
      locale,
    }, "AiLocalePolicy");
  }

  /**
   * Validate content against locale policy
   */
  validateContent(content: string, policy: LocalePolicy): { valid: boolean; issues: string[] } {
    const issues: string[] = [];

    // Check for unit consistency
    if (policy.unitSystem === UnitSystem.METRIC) {
      const imperialUnits = ["feet", "foot", "mile", "inch", "pound", "gallon", "fahrenheit"];
      for (const unit of imperialUnits) {
        if (content.toLowerCase().includes(unit)) {
          issues.push(`Imperial unit '${unit}' found in content for metric locale`);
        }
      }
    }

    // Check reading level complexity (basic heuristic)
    const words = content.split(/\s+/);
    const avgWordLength = words.reduce((sum, word) => sum + word.length, 0) / words.length;
    
    if (policy.readingLevel === ReadingLevel.ELEMENTARY && avgWordLength > 5) {
      issues.push("Average word length exceeds elementary reading level");
    }
    if (policy.readingLevel === ReadingLevel.PROFESSIONAL && avgWordLength < 6) {
      issues.push("Average word length below professional reading level");
    }

    // Check date format
    const datePattern = /\d{1,4}[\/\-\.]\d{1,2}[\/\-\.]\d{1,4}/;
    const dates = content.match(datePattern);
    if (dates) {
      for (const date of dates) {
        if (!this.matchesDateFormat(date, policy.dateFormat)) {
          issues.push(`Date '${date}' does not match expected format '${policy.dateFormat}'`);
        }
      }
    }

    return {
      valid: issues.length === 0,
      issues,
    };
  }

  /**
   * Get supported locales
   */
  getSupportedLocales(): string[] {
    return Object.keys(DEFAULT_POLICIES);
  }

  /**
   * Get policy for a specific locale
   */
  getPolicyForLocale(locale: string): LocalePolicy | null {
    if (this.customPolicies.has(locale)) {
      return this.customPolicies.get(locale)!;
    }
    const defaultPolicy = DEFAULT_POLICIES[locale];
    if (defaultPolicy) {
      return this.completePolicy(defaultPolicy);
    }
    return null;
  }

  // ============================================================================
  // Private Helpers
  // ============================================================================

  private mergePolicy(base: Partial<LocalePolicy>, override: Partial<LocalePolicy>): LocalePolicy {
    return {
      ...base,
      ...override,
      locale: override.locale || base.locale || "en",
      language: override.language || base.language || "English",
      unitSystem: override.unitSystem || base.unitSystem || UnitSystem.IMPERIAL,
      readingLevel: override.readingLevel || base.readingLevel || ReadingLevel.MIDDLE,
      culturalContext: override.culturalContext || base.culturalContext || [],
      dateFormat: override.dateFormat || base.dateFormat || "MM/DD/YYYY",
      numberFormat: override.numberFormat || base.numberFormat || "#,##0.###",
      currencyCode: override.currencyCode || base.currencyCode || "USD",
    } as LocalePolicy;
  }

  private completePolicy(partial: Partial<LocalePolicy>): LocalePolicy {
    return {
      locale: partial.locale || "en",
      language: partial.language || "English",
      unitSystem: partial.unitSystem || UnitSystem.IMPERIAL,
      readingLevel: partial.readingLevel || ReadingLevel.MIDDLE,
      culturalContext: partial.culturalContext || [],
      dateFormat: partial.dateFormat || "MM/DD/YYYY",
      numberFormat: partial.numberFormat || "#,##0.###",
      currencyCode: partial.currencyCode || "USD",
    };
  }

  private matchesDateFormat(date: string, format: string): boolean {
    // Basic format matching - can be enhanced with proper date parsing
    if (format === "MM/DD/YYYY") {
      return /^\d{1,2}\/\d{1,2}\/\d{4}$/.test(date);
    }
    if (format === "DD/MM/YYYY") {
      return /^\d{1,2}\/\d{1,2}\/\d{4}$/.test(date);
    }
    if (format === "YYYY/MM/DD") {
      return /^\d{4}\/\d{1,2}\/\d{1,2}$/.test(date);
    }
    return true; // Default to accepting if format not recognized
  }
}

// Singleton instance
export function getAiLocalePolicy(): AiLocalePolicy {
  return AiLocalePolicy.getInstance();
}

// ============================================================================
// Zod Schemas
// ============================================================================

export const LocalePolicySchema = z.object({
  locale: z.string().min(2),
  language: z.string().min(1),
  unitSystem: z.enum([UnitSystem.METRIC, UnitSystem.IMPERIAL]),
  readingLevel: z.enum([ReadingLevel.ELEMENTARY, ReadingLevel.MIDDLE, ReadingLevel.HIGH, ReadingLevel.COLLEGE, ReadingLevel.PROFESSIONAL]),
  culturalContext: z.array(z.string()),
  dateFormat: z.string(),
  numberFormat: z.string(),
  currencyCode: z.string().length(3),
});

export const LocalePolicyRequestSchema = z.object({
  locale: z.string().min(2),
  userId: z.string().optional(),
  tenantId: z.string().optional(),
  context: z.enum(["tutor", "assessment", "content", "simulation"]).optional(),
});
