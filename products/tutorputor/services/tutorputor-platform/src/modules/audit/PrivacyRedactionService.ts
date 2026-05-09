/**
 * Privacy Redaction Service
 *
 * Removes PII (Personally Identifiable Information) from AI audit logs.
 * Uses pattern matching to redact sensitive data before logging.
 *
 * @doc.type class
 * @doc.purpose PII redaction for AI audit logs
 * @doc.layer platform
 * @doc.pattern Service
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";

const logger = createStandaloneLogger({ component: "PrivacyRedactionService" });

export interface RedactionResult {
  redactedText: string;
  redactionCount: number;
  redactionTypes: string[];
}

export interface RedactionConfig {
  redactEmails: boolean;
  redactPhoneNumbers: boolean;
  redactSSN: boolean;
  redactCreditCards: boolean;
  redactIPAddresses: boolean;
  redactNames: boolean;
  redactAddresses: boolean;
  customPatterns?: Array<{
    name: string;
    pattern: RegExp;
    replacement: string;
  }>;
}

const DEFAULT_CONFIG: RedactionConfig = {
  redactEmails: true,
  redactPhoneNumbers: true,
  redactSSN: true,
  redactCreditCards: true,
  redactIPAddresses: true,
  redactNames: false, // Names disabled by default as they're harder to detect accurately
  redactAddresses: false, // Addresses disabled by default as they're harder to detect accurately
};

// PII Patterns
const PATTERNS = {
  // Email addresses
  EMAIL: /\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b/g,
  
  // US phone numbers (various formats)
  PHONE_US: /\b(?:\+?1[-.\s]?)?\(?([0-9]{3})\)?[-.\s]?([0-9]{3})[-.\s]?([0-9]{4})\b/g,
  
  // SSN (Social Security Number) - XXX-XX-XXXX format
  SSN: /\b\d{3}-\d{2}-\d{4}\b/g,
  
  // Credit card numbers (various formats)
  CREDIT_CARD: /\b(?:\d{4}[-\s]?){3}\d{4}\b/g,
  
  // IP addresses (IPv4)
  IP_V4: /\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b/g,
  
  // IP addresses (IPv6) - simplified pattern
  IP_V6: /\b(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\b/g,
  
  // Common name patterns (heuristic - not perfect)
  NAME: /\b[A-Z][a-z]+ [A-Z][a-z]+\b/g,
};

export class PrivacyRedactionService {
  private config: RedactionConfig;

  constructor(config?: Partial<RedactionConfig>) {
    this.config = { ...DEFAULT_CONFIG, ...config };
  }

  /**
   * Redact PII from text
   *
   * @param text Text to redact
   * @returns Redaction result with redacted text and metadata
   */
  redact(text: string): RedactionResult {
    if (!text || typeof text !== "string") {
      return {
        redactedText: text || "",
        redactionCount: 0,
        redactionTypes: [],
      };
    }

    let redactedText = text;
    let redactionCount = 0;
    const redactionTypes: string[] = [];

    // Redact emails
    if (this.config.redactEmails) {
      const before = redactedText;
      redactedText = redactedText.replace(PATTERNS.EMAIL, "[REDACTED_EMAIL]");
      const count = (before.match(PATTERNS.EMAIL) || []).length;
      if (count > 0) {
        redactionCount += count;
        redactionTypes.push("email");
      }
    }

    // Redact phone numbers
    if (this.config.redactPhoneNumbers) {
      const before = redactedText;
      redactedText = redactedText.replace(PATTERNS.PHONE_US, "[REDACTED_PHONE]");
      const count = (before.match(PATTERNS.PHONE_US) || []).length;
      if (count > 0) {
        redactionCount += count;
        redactionTypes.push("phone");
      }
    }

    // Redact SSN
    if (this.config.redactSSN) {
      const before = redactedText;
      redactedText = redactedText.replace(PATTERNS.SSN, "[REDACTED_SSN]");
      const count = (before.match(PATTERNS.SSN) || []).length;
      if (count > 0) {
        redactionCount += count;
        redactionTypes.push("ssn");
      }
    }

    // Redact credit cards
    if (this.config.redactCreditCards) {
      const before = redactedText;
      redactedText = redactedText.replace(PATTERNS.CREDIT_CARD, "[REDACTED_CC]");
      const count = (before.match(PATTERNS.CREDIT_CARD) || []).length;
      if (count > 0) {
        redactionCount += count;
        redactionTypes.push("credit_card");
      }
    }

    // Redact IP addresses
    if (this.config.redactIPAddresses) {
      const before = redactedText;
      redactedText = redactedText.replace(PATTERNS.IP_V4, "[REDACTED_IP]");
      redactedText = redactedText.replace(PATTERNS.IP_V6, "[REDACTED_IP]");
      const countV4 = (before.match(PATTERNS.IP_V4) || []).length;
      const countV6 = (before.match(PATTERNS.IP_V6) || []).length;
      if (countV4 + countV6 > 0) {
        redactionCount += countV4 + countV6;
        redactionTypes.push("ip_address");
      }
    }

    // Redact names (heuristic - may have false positives)
    if (this.config.redactNames) {
      const before = redactedText;
      redactedText = redactedText.replace(PATTERNS.NAME, "[REDACTED_NAME]");
      const count = (before.match(PATTERNS.NAME) || []).length;
      if (count > 0) {
        redactionCount += count;
        redactionTypes.push("name");
      }
    }

    // Apply custom patterns
    if (this.config.customPatterns) {
      for (const custom of this.config.customPatterns) {
        const before = redactedText;
        redactedText = redactedText.replace(custom.pattern, custom.replacement);
        const count = (before.match(custom.pattern) || []).length;
        if (count > 0) {
          redactionCount += count;
          if (!redactionTypes.includes(custom.name)) {
            redactionTypes.push(custom.name);
          }
        }
      }
    }

    if (redactionCount > 0) {
      logger.info({
        message: "PII redacted from text",
        redactionCount,
        redactionTypes,
        originalLength: text.length,
        redactedLength: redactedText.length,
      });
    }

    return {
      redactedText,
      redactionCount,
      redactionTypes,
    };
  }

  /**
   * Redact PII from an object (deep redaction)
   *
   * @param obj Object to redact
   * @returns Redacted object
   */
  redactObject<T>(obj: T): T {
    if (obj === null || obj === undefined) {
      return obj;
    }

    if (typeof obj === "string") {
      return this.redact(obj).redactedText as T;
    }

    if (Array.isArray(obj)) {
      return obj.map((item) => this.redactObject(item)) as T;
    }

    if (typeof obj === "object") {
      const result: any = {};
      for (const [key, value] of Object.entries(obj as object)) {
        result[key] = this.redactObject(value);
      }
      return result as T;
    }

    return obj;
  }

  /**
   * Update redaction configuration
   *
   * @param config New configuration
   */
  updateConfig(config: Partial<RedactionConfig>): void {
    this.config = { ...this.config, ...config };
    logger.info({
      message: "Privacy redaction configuration updated",
      config: this.config,
    });
  }

  /**
   * Get current configuration
   *
   * @returns Current configuration
   */
  getConfig(): RedactionConfig {
    return { ...this.config };
  }

  /**
   * Check if text contains PII
   *
   * @param text Text to check
   * @returns True if PII detected
   */
  containsPII(text: string): boolean {
    const result = this.redact(text);
    return result.redactionCount > 0;
  }

  /**
   * Get PII summary from text
   *
   * @param text Text to analyze
   * @returns Summary of detected PII types
   */
  getPIISummary(text: string): {
    hasPII: boolean;
    piiTypes: string[];
    count: number;
  } {
    const result = this.redact(text);
    return {
      hasPII: result.redactionCount > 0,
      piiTypes: result.redactionTypes,
      count: result.redactionCount,
    };
  }
}

// Singleton instance
let serviceInstance: PrivacyRedactionService | null = null;

export function getPrivacyRedactionService(
  config?: Partial<RedactionConfig>,
): PrivacyRedactionService {
  if (!serviceInstance) {
    serviceInstance = new PrivacyRedactionService(config);
  } else if (config) {
    serviceInstance.updateConfig(config);
  }
  return serviceInstance;
}
