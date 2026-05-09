/**
 * Locale-Aware Backend Contracts
 *
 * Provides locale-aware types for content title/body, assessment prompts,
 * and simulation labels to support internationalization at the backend level.
 *
 * @doc.type module
 * @doc.purpose Define locale-aware types for backend contracts
 * @doc.layer contracts
 * @doc.pattern Schema
 */

// =============================================================================
// Locale-Aware Types
// =============================================================================

/**
 * Locale code type matching the i18n provider.
 */
export type Locale = "en" | "es" | "hi" | "zh";
export type SupportedLocale = Locale;

/**
 * Default locale fallback.
 */
export const DEFAULT_LOCALE: SupportedLocale = "en";

/**
 * Locale-aware string field.
 * Maps locale codes to translated content.
 */
export type LocaleString = Record<SupportedLocale, string>;

/**
 * Locale-aware content with fallback to default locale.
 */
export interface LocaleAwareContent {
  /** Locale-specific content */
  localized: LocaleString;
  /** Fallback content (typically English) */
  fallback?: string;
}

/**
 * Locale-aware field that can be either a single string (default locale)
 * or a locale-aware object.
 */
export type LocaleAwareField = string | LocaleString;

// =============================================================================
// Content Locale Types
// =============================================================================

/**
 * Locale-aware content metadata.
 */
export interface LocaleAwareMetadata {
  /** Primary locale of the content */
  primaryLocale: SupportedLocale;
  /** Available locales for this content */
  availableLocales: SupportedLocale[];
  /** Last translation update per locale */
  lastTranslationUpdate?: Record<SupportedLocale, string>;
  /** Translation quality score per locale (0-1) */
  translationQuality?: Record<SupportedLocale, number>;
}

/**
 * Locale-aware domain concept.
 */
export interface LocaleAwareDomainConcept {
  /** Concept ID */
  id: string;
  /** Locale-aware name */
  name: LocaleString;
  /** Locale-aware description */
  description: LocaleString;
  /** Locale metadata */
  localeMetadata: LocaleAwareMetadata;
}

/**
 * Locale-aware curriculum.
 */
export interface LocaleAwareCurriculum {
  /** Curriculum ID */
  id: string;
  /** Locale-aware title */
  title: LocaleString;
  /** Locale-aware description */
  description?: LocaleString;
  /** Locale metadata */
  localeMetadata: LocaleAwareMetadata;
}

/**
 * Locale-aware learning unit/module.
 */
export interface LocaleAwareLearningUnit {
  /** Unit ID */
  id: string;
  /** Locale-aware title */
  title: LocaleString;
  /** Locale-aware description */
  description?: LocaleString;
  /** Locale-aware instructions */
  instructions?: LocaleString;
  /** Locale metadata */
  localeMetadata: LocaleAwareMetadata;
}

// =============================================================================
// Assessment Locale Types
// =============================================================================

/**
 * Locale-aware assessment item.
 */
export interface LocaleAwareAssessmentItem {
  /** Item ID */
  id: string;
  /** Locale-aware prompt */
  prompt: LocaleString;
  /** Locale-aware stimulus/context */
  stimulus?: LocaleString;
  /** Locale-aware hints */
  hints?: Array<{
    hintId: string;
    content: LocaleString;
  }>;
  /** Locale-aware feedback */
  feedback?: {
    showCorrectAnswer: boolean;
    showExplanation: boolean;
    customFeedbackByScore?: Array<{
      minScore: number;
      maxScore: number;
      message: LocaleString;
    }>;
  };
  /** Locale metadata */
  localeMetadata: LocaleAwareMetadata;
}

/**
 * Locale-aware assessment rubric criterion.
 */
export interface LocaleAwareRubricCriterion {
  id: string;
  /** Locale-aware description */
  description: LocaleString;
  maxPoints: number;
  /** Locale-aware level descriptions */
  levels: Array<{
    level: number;
    description: LocaleString;
    points: number;
  }>;
}

/**
 * Locale-aware grading explanation.
 */
export interface LocaleAwareGradingExplanation {
  /** Locale-aware overall feedback */
  feedback: LocaleString;
  /** Locale-aware criterion-specific feedback */
  criterionFeedback?: Array<{
    criterionId: string;
    criterionName: LocaleString;
    feedback: LocaleString;
  }>;
}

// =============================================================================
// Simulation Locale Types
// =============================================================================

/**
 * Locale-aware simulation manifest.
 */
export interface LocaleAwareSimulationManifest {
  /** Simulation ID */
  id: string;
  /** Locale-aware title */
  title: LocaleString;
  /** Locale-aware description */
  description?: LocaleString;
  /** Locale-aware entity labels */
  entityLabels?: Record<string, LocaleString>;
  /** Locale-aware parameter labels */
  parameterLabels?: Record<string, LocaleString>;
  /** Locale-aware unit labels */
  unitLabels?: Record<string, LocaleString>;
  /** Locale metadata */
  localeMetadata: LocaleAwareMetadata;
}

/**
 * Locale-aware simulation instruction.
 */
export interface LocaleAwareSimulationInstruction {
  /** Instruction ID */
  id: string;
  /** Locale-aware instruction text */
  text: LocaleString;
  /** Locale-aware step labels */
  stepLabels?: Array<{
    stepId: string;
    label: LocaleString;
  }>;
}

/**
 * Locale-aware simulation entity.
 */
export interface LocaleAwareSimulationEntity {
  /** Entity ID */
  id: string;
  /** Locale-aware display name */
  displayName: LocaleString;
  /** Locale-aware description */
  description?: LocaleString;
  /** Locale-aware property labels */
  propertyLabels?: Record<string, LocaleString>;
}

// =============================================================================
// Helper Functions
// =============================================================================

/**
 * Get content for a specific locale with fallback.
 */
export function getLocalizedContent(
  content: LocaleString | string,
  locale: SupportedLocale,
  fallbackLocale: SupportedLocale = DEFAULT_LOCALE,
): string {
  if (typeof content === "string") {
    return content;
  }

  // Try requested locale
  if (content[locale]) {
    return content[locale];
  }

  // Try fallback locale
  if (content[fallbackLocale]) {
    return content[fallbackLocale];
  }

  // Return first available locale as last resort
  const firstAvailable = Object.values(content)[0];
  if (firstAvailable) {
    return firstAvailable;
  }

  return "";
}

/**
 * Check if content is available for a locale.
 */
export function isLocaleAvailable(
  content: LocaleString | string,
  locale: SupportedLocale,
): boolean {
  if (typeof content === "string") {
    return locale === DEFAULT_LOCALE;
  }
  return !!content[locale];
}

/**
 * Get all available locales for content.
 */
export function getAvailableLocales(content: LocaleString | string): SupportedLocale[] {
  if (typeof content === "string") {
    return [DEFAULT_LOCALE];
  }
  return Object.keys(content) as SupportedLocale[];
}

/**
 * Create a locale string from a single string (default locale).
 */
export function createLocaleString(
  content: string,
  locale: SupportedLocale = DEFAULT_LOCALE,
): LocaleString {
  const localeString: Partial<LocaleString> = {};
  localeString[locale] = content;
  return localeString as LocaleString;
}

/**
 * Merge locale strings with preference for the second argument.
 */
export function mergeLocaleStrings(
  base: LocaleString,
  override: Partial<LocaleString>,
): LocaleString {
  return { ...base, ...override } as LocaleString;
}

/**
 * Validate locale-aware content has required locales.
 */
export function validateLocaleContent(
  content: LocaleString,
  requiredLocales: SupportedLocale[],
): { valid: boolean; missing: SupportedLocale[] } {
  const missing = requiredLocales.filter((locale) => !content[locale]);
  return {
    valid: missing.length === 0,
    missing,
  };
}

// =============================================================================
// Request/Response Types
// =============================================================================

/**
 * Locale-aware content request.
 */
export interface LocaleAwareContentRequest {
  /** Requested locale */
  locale: SupportedLocale;
  /** Fallback locale if requested locale not available */
  fallbackLocale?: SupportedLocale;
  /** Whether to return all locales or just requested */
  returnAllLocales?: boolean;
}

/**
 * Locale-aware content response.
 */
export interface LocaleAwareContentResponse<T> {
  /** Content in requested locale */
  content: T;
  /** All available locales if requested */
  allLocales?: Record<SupportedLocale, T>;
  /** Metadata about available locales */
  localeMetadata: LocaleAwareMetadata;
  /** Actual locale returned */
  actualLocale: SupportedLocale;
  /** Whether fallback was used */
  usedFallback: boolean;
}

/**
 * Batch locale-aware content request.
 */
export interface BatchLocaleAwareRequest {
  /** Requested locale */
  locale: SupportedLocale;
  /** Content IDs to fetch */
  contentIds: string[];
  /** Content type */
  contentType: "concept" | "curriculum" | "assessment" | "simulation";
}

/**
 * Batch locale-aware content response.
 */
export interface BatchLocaleAwareResponse<T> {
  /** Content by ID */
  content: Record<string, LocaleAwareContentResponse<T>>;
  /** IDs that were not found */
  notFound: string[];
  /** IDs that had missing locale translations */
  missingLocale: string[];
}
