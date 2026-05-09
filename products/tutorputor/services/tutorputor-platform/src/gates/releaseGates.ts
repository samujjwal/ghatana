/**
 * Release Gates
 *
 * Native release gates for privacy, security, i18n, a11y, and observability.
 * These gates run automatically during release rather than requiring manual review.
 *
 * @doc.type module
 * @doc.purpose Native release gates for production readiness
 * @doc.layer platform
 * @doc.pattern Gate
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";

const logger = createStandaloneLogger({ component: "ReleaseGates" });

// ============================================================================
// Gate Types
// ============================================================================

export interface GateResult {
  gate: string;
  passed: boolean;
  severity: "BLOCKER" | "ERROR" | "WARNING" | "INFO";
  detail: string;
}

export interface ReleaseGateReport {
  releaseId: string;
  environment: string;
  passed: boolean;
  gates: GateResult[];
  blockers: string[];
  errors: string[];
  warnings: string[];
  validatedAt: string;
}

// ============================================================================
// Privacy Gates
// ============================================================================

class PrivacyGates {
  /**
   * Check that all data collection has consent
   */
  static consentGate(hasConsent: boolean, dataType: string): GateResult {
    return {
      gate: "privacy_consent",
      passed: hasConsent,
      severity: "BLOCKER",
      detail: hasConsent
        ? `Consent verified for ${dataType} ✓`
        : `Missing consent for ${dataType} - cannot release`,
    };
  }

  /**
   * Check that PII is properly encrypted at rest
   */
  static encryptionGate(isEncrypted: boolean, dataType: string): GateResult {
    return {
      gate: "privacy_encryption",
      passed: isEncrypted,
      severity: "BLOCKER",
      detail: isEncrypted
        ? `${dataType} encrypted at rest ✓`
        : `${dataType} not encrypted at rest - violates privacy policy`,
    };
  }

  /**
   * Check data retention policy compliance
   */
  static retentionGate(compliant: boolean, dataType: string): GateResult {
    return {
      gate: "privacy_retention",
      passed: compliant,
      severity: "ERROR",
      detail: compliant
        ? `${dataType} retention policy compliant ✓`
        : `${dataType} retention policy violation detected`,
    };
  }

  /**
   * Check that data export functionality works
   */
  static dataExportGate(works: boolean): GateResult {
    return {
      gate: "privacy_data_export",
      passed: works,
      severity: "BLOCKER",
      detail: works
        ? "Data export functionality working ✓"
        : "Data export functionality broken - violates GDPR/CCPA",
    };
  }

  /**
   * Check that data deletion functionality works
   */
  static dataDeletionGate(works: boolean): GateResult {
    return {
      gate: "privacy_data_deletion",
      passed: works,
      severity: "BLOCKER",
      detail: works
        ? "Data deletion functionality working ✓"
        : "Data deletion functionality broken - violates GDPR/CCPA",
    };
  }
}

// ============================================================================
// Security Gates
// ============================================================================

class SecurityGates {
  /**
   * Check that JWT secrets are strong
   */
  static jwtSecretGate(strength: "strong" | "weak"): GateResult {
    return {
      gate: "security_jwt_secret",
      passed: strength === "strong",
      severity: "BLOCKER",
      detail: strength === "strong"
        ? "JWT secret strength verified ✓"
        : "JWT secret too weak - must be at least 32 characters",
    };
  }

  /**
   * Check that database uses SSL/TLS
   */
  static databaseSslGate(enabled: boolean): GateResult {
    return {
      gate: "security_database_ssl",
      passed: enabled,
      severity: "BLOCKER",
      detail: enabled
        ? "Database SSL/TLS enabled ✓"
        : "Database SSL/TLS disabled - data in transit not protected",
    };
  }

  /**
   * Check that Redis uses secure connection
   */
  static redisSecurityGate(secure: boolean): GateResult {
    return {
      gate: "security_redis",
      passed: secure,
      severity: "BLOCKER",
      detail: secure
        ? "Redis secure connection verified ✓"
        : "Redis insecure connection detected",
    };
  }

  /**
   * Check that CORS is properly configured
   */
  static corsGate(configured: boolean): GateResult {
    return {
      gate: "security_cors",
      passed: configured,
      severity: "BLOCKER",
      detail: configured
        ? "CORS properly configured ✓"
        : "CORS misconfigured - potential security risk",
    };
  }

  /**
   * Check that rate limiting is enabled
   */
  static rateLimitGate(enabled: boolean): GateResult {
    return {
      gate: "security_rate_limit",
      passed: enabled,
      severity: "ERROR",
      detail: enabled
        ? "Rate limiting enabled ✓"
        : "Rate limiting disabled - DoS risk",
    };
  }

  /**
   * Check for no hardcoded secrets in code
   */
  static secretsGate(noHardcoded: boolean): GateResult {
    return {
      gate: "security_no_hardcoded_secrets",
      passed: noHardcoded,
      severity: "BLOCKER",
      detail: noHardcoded
        ? "No hardcoded secrets detected ✓"
        : "Hardcoded secrets detected in code",
    };
  }
}

// ============================================================================
// i18n Gates
// ============================================================================

class I18nGates {
  /**
   * Check that all user-facing strings are externalized
   */
  static externalizationGate(externalized: boolean): GateResult {
    return {
      gate: "i18n_externalized",
      passed: externalized,
      severity: "ERROR",
      detail: externalized
        ? "All user-facing strings externalized ✓"
        : "Hardcoded user-facing strings detected",
    };
  }

  /**
   * Check that translation completeness meets threshold
   */
  static translationCompletenessGate(complete: boolean, locale: string): GateResult {
    return {
      gate: `i18n_translation_${locale}`,
      passed: complete,
      severity: "WARNING",
      detail: complete
        ? `Translation completeness for ${locale} verified ✓`
        : `Translation completeness for ${locale} below threshold`,
    };
  }

  /**
   * Check that date/time formats are locale-aware
   */
  static localeFormatsGate(localeAware: boolean): GateResult {
    return {
      gate: "i18n_locale_formats",
      passed: localeAware,
      severity: "ERROR",
      detail: localeAware
        ? "Date/time formats locale-aware ✓"
        : "Date/time formats not locale-aware",
    };
  }
}

// ============================================================================
// Accessibility Gates
// ============================================================================

class AccessibilityGates {
  /**
   * Check that all images have alt text
   */
  static altTextGate(hasAltText: boolean): GateResult {
    return {
      gate: "a11y_alt_text",
      passed: hasAltText,
      severity: "ERROR",
      detail: hasAltText
        ? "All images have alt text ✓"
        : "Missing alt text detected on images",
    };
  }

  /**
   * Check that color contrast meets WCAG AA
   */
  static colorContrastGate(passes: boolean): GateResult {
    return {
      gate: "a11y_color_contrast",
      passed: passes,
      severity: "ERROR",
      detail: passes
        ? "Color contrast meets WCAG AA ✓"
        : "Color contrast fails WCAG AA",
    };
  }

  /**
   * Check that keyboard navigation works
   */
  static keyboardNavigationGate(works: boolean): GateResult {
    return {
      gate: "a11y_keyboard",
      passed: works,
      severity: "ERROR",
      detail: works
        ? "Keyboard navigation functional ✓"
        : "Keyboard navigation broken",
    };
  }

  /**
   * Check that screen reader compatibility exists
   */
  static screenReaderGate(compatible: boolean): GateResult {
    return {
      gate: "a11y_screen_reader",
      passed: compatible,
      severity: "ERROR",
      detail: compatible
        ? "Screen reader compatible ✓"
        : "Screen reader compatibility issues detected",
    };
  }

  /**
   * Check that reduced motion support exists
   */
  static reducedMotionGate(supported: boolean): GateResult {
    return {
      gate: "a11y_reduced_motion",
      passed: supported,
      severity: "WARNING",
      detail: supported
        ? "Reduced motion support ✓"
        : "Reduced motion preference not respected",
    };
  }
}

// ============================================================================
// Observability Gates
// ============================================================================

class ObservabilityGates {
  /**
   * Check that structured logging is in place
   */
  static structuredLoggingGate(inPlace: boolean): GateResult {
    return {
      gate: "observability_logging",
      passed: inPlace,
      severity: "ERROR",
      detail: inPlace
        ? "Structured logging in place ✓"
        : "Structured logging missing",
    };
  }

  /**
   * Check that error tracking is configured
   */
  static errorTrackingGate(configured: boolean): GateResult {
    return {
      gate: "observability_error_tracking",
      passed: configured,
      severity: "ERROR",
      detail: configured
        ? "Error tracking configured ✓"
        : "Error tracking not configured",
    };
  }

  /**
   * Check that metrics are being emitted
   */
  static metricsGate(emitting: boolean): GateResult {
    return {
      gate: "observability_metrics",
      passed: emitting,
      severity: "WARNING",
      detail: emitting
        ? "Metrics being emitted ✓"
        : "Metrics not being emitted",
    };
  }

  /**
   * Check that health endpoints exist
   */
  static healthEndpointGate(exists: boolean): GateResult {
    return {
      gate: "observability_health",
      passed: exists,
      severity: "ERROR",
      detail: exists
        ? "Health endpoints configured ✓"
        : "Health endpoints missing",
    };
  }

  /**
   * Check that tracing is enabled
   */
  static tracingGate(enabled: boolean): GateResult {
    return {
      gate: "observability_tracing",
      passed: enabled,
      severity: "WARNING",
      detail: enabled
        ? "Distributed tracing enabled ✓"
        : "Distributed tracing not enabled",
    };
  }
}

// ============================================================================
// Release Gate Orchestrator
// ============================================================================

export class ReleaseGateOrchestrator {
  /**
   * Run all release gates and return a comprehensive report
   */
  static async runAllGates(
    releaseId: string,
    environment: string,
    config: {
      // Privacy config
      consentEnabled: boolean;
      piiEncrypted: boolean;
      retentionCompliant: boolean;
      dataExportWorks: boolean;
      dataDeletionWorks: boolean;

      // Security config
      jwtSecretStrength: "strong" | "weak";
      databaseSslEnabled: boolean;
      redisSecure: boolean;
      corsConfigured: boolean;
      rateLimitEnabled: boolean;
      noHardcodedSecrets: boolean;

      // i18n config
      stringsExternalized: boolean;
      translationComplete: boolean;
      localeFormatsAware: boolean;

      // Accessibility config
      altTextPresent: boolean;
      colorContrastPasses: boolean;
      keyboardNavigationWorks: boolean;
      screenReaderCompatible: boolean;
      reducedMotionSupported: boolean;

      // Observability config
      structuredLogging: boolean;
      errorTrackingConfigured: boolean;
      metricsEmitting: boolean;
      healthEndpointsExist: boolean;
      tracingEnabled: boolean;
    },
  ): Promise<ReleaseGateReport> {
    const gates: GateResult[] = [];

    // Privacy gates
    gates.push(PrivacyGates.consentGate(config.consentEnabled, "all data"));
    gates.push(PrivacyGates.encryptionGate(config.piiEncrypted, "PII"));
    gates.push(PrivacyGates.retentionGate(config.retentionCompliant, "all data"));
    gates.push(PrivacyGates.dataExportGate(config.dataExportWorks));
    gates.push(PrivacyGates.dataDeletionGate(config.dataDeletionWorks));

    // Security gates
    gates.push(SecurityGates.jwtSecretGate(config.jwtSecretStrength));
    gates.push(SecurityGates.databaseSslGate(config.databaseSslEnabled));
    gates.push(SecurityGates.redisSecurityGate(config.redisSecure));
    gates.push(SecurityGates.corsGate(config.corsConfigured));
    gates.push(SecurityGates.rateLimitGate(config.rateLimitEnabled));
    gates.push(SecurityGates.secretsGate(config.noHardcodedSecrets));

    // i18n gates
    gates.push(I18nGates.externalizationGate(config.stringsExternalized));
    gates.push(I18nGates.translationCompletenessGate(config.translationComplete, "en"));
    gates.push(I18nGates.localeFormatsGate(config.localeFormatsAware));

    // Accessibility gates
    gates.push(AccessibilityGates.altTextGate(config.altTextPresent));
    gates.push(AccessibilityGates.colorContrastGate(config.colorContrastPasses));
    gates.push(AccessibilityGates.keyboardNavigationGate(config.keyboardNavigationWorks));
    gates.push(AccessibilityGates.screenReaderGate(config.screenReaderCompatible));
    gates.push(AccessibilityGates.reducedMotionGate(config.reducedMotionSupported));

    // Observability gates
    gates.push(ObservabilityGates.structuredLoggingGate(config.structuredLogging));
    gates.push(ObservabilityGates.errorTrackingGate(config.errorTrackingConfigured));
    gates.push(ObservabilityGates.metricsGate(config.metricsEmitting));
    gates.push(ObservabilityGates.healthEndpointGate(config.healthEndpointsExist));
    gates.push(ObservabilityGates.tracingGate(config.tracingEnabled));

    const blockers = gates.filter((g) => !g.passed && g.severity === "BLOCKER").map((g) => g.detail);
    const errors = gates.filter((g) => !g.passed && g.severity === "ERROR").map((g) => g.detail);
    const warnings = gates.filter((g) => !g.passed && g.severity === "WARNING").map((g) => g.detail);

    const passed = blockers.length === 0;

    const report: ReleaseGateReport = {
      releaseId,
      environment,
      passed,
      gates,
      blockers,
      errors,
      warnings,
      validatedAt: new Date().toISOString(),
    };

    logger.info({
      message: "Release gates executed",
      releaseId,
      environment,
      passed,
      blockers: blockers.length,
      errors: errors.length,
      warnings: warnings.length,
    }, "ReleaseGateOrchestrator");

    return report;
  }

  /**
   * Run gates for production environment (strict mode)
   */
  static async runProductionGates(
    releaseId: string,
    config: Parameters<typeof ReleaseGateOrchestrator.runAllGates>[2],
  ): Promise<ReleaseGateReport> {
    logger.info(
      { releaseId, mode: "production" },
      "Running production release gates in strict mode",
    );
    return ReleaseGateOrchestrator.runAllGates(releaseId, "production", config);
  }

  /**
   * Run gates for staging environment (relaxed mode)
   */
  static async runStagingGates(
    releaseId: string,
    config: Parameters<typeof ReleaseGateOrchestrator.runAllGates>[2],
  ): Promise<ReleaseGateReport> {
    logger.info(
      { releaseId, mode: "staging" },
      "Running staging release gates in relaxed mode",
    );
    return ReleaseGateOrchestrator.runAllGates(releaseId, "staging", config);
  }
}
