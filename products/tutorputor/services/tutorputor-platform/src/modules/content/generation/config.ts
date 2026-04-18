/**
 * Content Generation Configuration
 *
 * Configuration for duplicate detection and content generation settings.
 *
 * @doc.type config
 * @doc.purpose Configure similarity thresholds and generation settings
 * @doc.layer product
 * @doc.pattern Config
 */

export interface SimilarityThresholdConfig {
  default: number;
  byContentType: Record<string, number>;
  byDomain: Record<string, number>;
}

export interface GenerationConfig {
  similarity: SimilarityThresholdConfig;
  maxRetries: number;
  timeoutSeconds: number;
}

// Default similarity thresholds
export const DEFAULT_SIMILARITY_THRESHOLD = 0.85;

// Content-type specific thresholds
export const DEFAULT_CONTENT_TYPE_THRESHOLDS: Record<string, number> = {
  simulation: 0.80,
  animation: 0.75,
  assessment: 0.90,
  quiz: 0.85,
  explainer: 0.80,
  worked_example: 0.85,
  claim: 0.85,
  evaluation: 0.85,
};

// Domain-specific thresholds
export const DEFAULT_DOMAIN_THRESHOLDS: Record<string, number> = {
  MATHEMATICS: 0.80,
  PHYSICS: 0.80,
  CHEMISTRY: 0.80,
  BIOLOGY: 0.80,
  COMPUTER_SCIENCE: 0.75,
  HISTORY: 0.85,
  LITERATURE: 0.85,
  LANGUAGES: 0.85,
};

// Default generation configuration
export const DEFAULT_GENERATION_CONFIG: GenerationConfig = {
  similarity: {
    default: DEFAULT_SIMILARITY_THRESHOLD,
    byContentType: DEFAULT_CONTENT_TYPE_THRESHOLDS,
    byDomain: DEFAULT_DOMAIN_THRESHOLDS,
  },
  maxRetries: 3,
  timeoutSeconds: 300,
};

/**
 * Get similarity threshold for content generation
 */
export function getSimilarityThreshold(
  config: GenerationConfig,
  contentType?: string,
  domain?: string,
): number {
  // Check domain-specific threshold first
  if (domain && config.similarity.byDomain[domain]) {
    return config.similarity.byDomain[domain];
  }

  // Check content-type specific threshold
  if (contentType && config.similarity.byContentType[contentType]) {
    return config.similarity.byContentType[contentType];
  }

  // Return default
  return config.similarity.default;
}

/**
 * Validate and sanitize similarity threshold
 */
export function validateSimilarityThreshold(threshold: number): number {
  // Clamp between 0.5 and 0.99
  return Math.max(0.5, Math.min(0.99, threshold));
}
