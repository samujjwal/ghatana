/**
 * Governance Metadata Enforcer
 *
 * Enforces governance metadata on component properties including:
 * - a11y props
 * - privacy
 * - telemetry
 * - review-required
 * - preview trust
 * - suggested improvements
 *
 * @doc.type service
 * @doc.purpose Governance metadata enforcement
 * @doc.layer product
 */

export interface GovernanceMetadata {
  /** Accessibility requirements */
  a11y?: {
    /** Required ARIA label */
    ariaLabel?: string;
    /** Required ARIA described by */
    ariaDescribedBy?: string;
    /** Required role */
    role?: string;
    /** Keyboard navigable */
    keyboardNavigable?: boolean;
    /** Screen reader compatible */
    screenReaderCompatible?: boolean;
    /** Minimum contrast ratio */
    minContrastRatio?: number;
  };
  /** Privacy settings */
  privacy?: {
    /** Contains PII */
    containsPII?: boolean;
    /** Data classification */
    dataClassification?: 'public' | 'internal' | 'confidential' | 'restricted';
    /** Encryption required */
    encryptionRequired?: boolean;
    /** Audit logging required */
    auditLoggingRequired?: boolean;
  };
  /** Telemetry settings */
  telemetry?: {
    /** Track interactions */
    trackInteractions?: boolean;
    /** Track performance */
    trackPerformance?: boolean;
    /** Event name prefix */
    eventPrefix?: string;
    /** Sampling rate */
    samplingRate?: number;
  };
  /** Review requirements */
  review?: {
    /** Review required before use */
    required?: boolean;
    /** Reviewer role */
    reviewerRole?: string;
    /** Review deadline */
    reviewDeadline?: string;
    /** Auto-approve threshold */
    autoApproveThreshold?: number;
  };
  /** Preview trust settings */
  previewTrust?: {
    /** Trusted for preview */
    trusted?: boolean;
    /** Sandbox required */
    sandboxRequired?: boolean;
    /** Maximum preview duration */
    maxPreviewDuration?: number;
  };
  /** Suggested improvements */
  suggestions?: GovernanceSuggestion[];
}

export interface GovernanceSuggestion {
  /** Suggestion ID */
  id: string;
  /** Suggestion type */
  type: 'a11y' | 'privacy' | 'performance' | 'security' | 'best-practice';
  /** Suggestion title */
  title: string;
  /** Suggestion description */
  description: string;
  /** Suggestion severity */
  severity: 'critical' | 'high' | 'medium' | 'low' | 'info';
  /** Suggestion confidence */
  confidence?: number;
  /** Suggested action */
  action?: string;
  /** Source of suggestion */
  source?: string;
  /** Applied flag */
  applied?: boolean;
}

export interface GovernanceValidationResult {
  /** Whether metadata is valid */
  valid: boolean;
  /** Validation errors */
  errors: string[];
  /** Warnings */
  warnings: string[];
  /** Suggestions */
  suggestions: GovernanceSuggestion[];
  /** Required metadata fields */
  requiredFields: string[];
  /** Missing metadata fields */
  missingFields: string[];
}

/**
 * Enforce governance metadata on component
 */
export function enforceGovernanceMetadata(
  componentId: string,
  currentMetadata: GovernanceMetadata,
  requiredMetadata: Partial<GovernanceMetadata>
): GovernanceValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];
  const suggestions: GovernanceSuggestion[] = [];
  const requiredFields: string[] = [];
  const missingFields: string[] = [];

  // Check accessibility requirements
  if (requiredMetadata.a11y) {
    if (requiredMetadata.a11y.ariaLabel && !currentMetadata.a11y?.ariaLabel) {
      missingFields.push('a11y.ariaLabel');
      errors.push('Missing required ariaLabel');
    }
    if (requiredMetadata.a11y.keyboardNavigable && !currentMetadata.a11y?.keyboardNavigable) {
      missingFields.push('a11y.keyboardNavigable');
      warnings.push('Component should be keyboard navigable');
    }
    if (requiredMetadata.a11y.minContrastRatio) {
      requiredFields.push('a11y.minContrastRatio');
    }
  }

  // Check privacy requirements
  if (requiredMetadata.privacy) {
    if (requiredMetadata.privacy.dataClassification && !currentMetadata.privacy?.dataClassification) {
      missingFields.push('privacy.dataClassification');
      errors.push('Missing data classification');
    }
    if (requiredMetadata.privacy.auditLoggingRequired && !currentMetadata.privacy?.auditLoggingRequired) {
      missingFields.push('privacy.auditLoggingRequired');
      warnings.push('Audit logging should be enabled');
    }
  }

  // Check telemetry requirements
  if (requiredMetadata.telemetry) {
    if (requiredMetadata.telemetry.trackInteractions && !currentMetadata.telemetry?.trackInteractions) {
      missingFields.push('telemetry.trackInteractions');
      warnings.push('Interaction tracking should be enabled');
    }
    if (requiredMetadata.telemetry.eventPrefix && !currentMetadata.telemetry?.eventPrefix) {
      missingFields.push('telemetry.eventPrefix');
      suggestions.push({
        id: `telemetry-prefix-${componentId}`,
        type: 'best-practice',
        title: 'Add telemetry event prefix',
        description: 'Add a consistent event prefix for better telemetry organization',
        severity: 'low',
        action: 'Set telemetry.eventPrefix',
      });
    }
  }

  // Check review requirements
  if (requiredMetadata.review?.required && !currentMetadata.review?.required) {
    missingFields.push('review.required');
    warnings.push('Component requires review before use');
  }

  // Check preview trust requirements
  if (requiredMetadata.previewTrust?.sandboxRequired && !currentMetadata.previewTrust?.sandboxRequired) {
    missingFields.push('previewTrust.sandboxRequired');
    errors.push('Component requires sandbox for preview');
  }

  // Generate suggestions based on current metadata
  suggestions.push(...generateSuggestions(componentId, currentMetadata));

  const valid = errors.length === 0;

  return {
    valid,
    errors,
    warnings,
    suggestions,
    requiredFields,
    missingFields,
  };
}

/**
 * Generate governance suggestions
 */
function generateSuggestions(componentId: string, metadata: GovernanceMetadata): GovernanceSuggestion[] {
  const suggestions: GovernanceSuggestion[] = [];

  // Accessibility suggestions
  if (!metadata.a11y?.ariaLabel) {
    suggestions.push({
      id: `a11y-label-${componentId}`,
      type: 'a11y',
      title: 'Add ariaLabel for accessibility',
      description: 'Components should have an ariaLabel for screen reader compatibility',
      severity: 'high',
      action: 'Add ariaLabel prop',
    });
  }

  if (!metadata.a11y?.keyboardNavigable) {
    suggestions.push({
      id: `a11y-keyboard-${componentId}`,
      type: 'a11y',
      title: 'Make component keyboard navigable',
      description: 'Interactive components should be accessible via keyboard',
      severity: 'medium',
      action: 'Add keyboard event handlers',
    });
  }

  // Privacy suggestions
  if (!metadata.privacy?.dataClassification) {
    suggestions.push({
      id: `privacy-classification-${componentId}`,
      type: 'privacy',
      title: 'Add data classification',
      description: 'Components should specify data classification for proper handling',
      severity: 'medium',
      action: 'Set privacy.dataClassification',
    });
  }

  // Telemetry suggestions
  if (!metadata.telemetry?.trackInteractions) {
    suggestions.push({
      id: `telemetry-interactions-${componentId}`,
      type: 'best-practice',
      title: 'Enable interaction tracking',
      description: 'Tracking interactions helps understand component usage',
      severity: 'low',
      action: 'Set telemetry.trackInteractions to true',
    });
  }

  // Preview trust suggestions
  if (!metadata.previewTrust?.trusted) {
    suggestions.push({
      id: `preview-trust-${componentId}`,
      type: 'security',
      title: 'Review preview trust settings',
      description: 'Components should be marked as trusted for safe preview',
      severity: 'medium',
      action: 'Set previewTrust.trusted after review',
    });
  }

  return suggestions;
}

/**
 * Apply governance suggestion
 */
export function applyGovernanceSuggestion(
  metadata: GovernanceMetadata,
  suggestion: GovernanceSuggestion
): GovernanceMetadata {
  const updatedMetadata = { ...metadata };

  switch (suggestion.type) {
    case 'a11y':
      if (!updatedMetadata.a11y) {
        updatedMetadata.a11y = {};
      }
      if (suggestion.action?.includes('ariaLabel')) {
        updatedMetadata.a11y.ariaLabel = '';
      }
      if (suggestion.action?.includes('keyboard')) {
        updatedMetadata.a11y.keyboardNavigable = true;
      }
      break;
    case 'privacy':
      if (!updatedMetadata.privacy) {
        updatedMetadata.privacy = {};
      }
      if (suggestion.action?.includes('dataClassification')) {
        updatedMetadata.privacy.dataClassification = 'internal';
      }
      break;
    case 'best-practice':
      if (!updatedMetadata.telemetry) {
        updatedMetadata.telemetry = {};
      }
      if (suggestion.action?.includes('trackInteractions')) {
        updatedMetadata.telemetry.trackInteractions = true;
      }
      if (suggestion.action?.includes('eventPrefix')) {
        updatedMetadata.telemetry.eventPrefix = 'component';
      }
      break;
    case 'security':
      if (!updatedMetadata.previewTrust) {
        updatedMetadata.previewTrust = {};
      }
      if (suggestion.action?.includes('trusted')) {
        updatedMetadata.previewTrust.trusted = true;
      }
      break;
  }

  // Mark suggestion as applied
  suggestion.applied = true;

  return updatedMetadata;
}

/**
 * Get default governance metadata
 */
export function getDefaultGovernanceMetadata(): GovernanceMetadata {
  return {
    a11y: {
      keyboardNavigable: false,
      screenReaderCompatible: false,
      minContrastRatio: 4.5,
    },
    privacy: {
      containsPII: false,
      dataClassification: 'internal',
      encryptionRequired: false,
      auditLoggingRequired: false,
    },
    telemetry: {
      trackInteractions: false,
      trackPerformance: false,
      samplingRate: 1.0,
    },
    review: {
      required: false,
      autoApproveThreshold: 0.9,
    },
    previewTrust: {
      trusted: false,
      sandboxRequired: true,
      maxPreviewDuration: 300000, // 5 minutes
    },
    suggestions: [],
  };
}

/**
 * Merge governance metadata with defaults
 */
export function mergeGovernanceMetadata(
  metadata: Partial<GovernanceMetadata>
): GovernanceMetadata {
  const defaults = getDefaultGovernanceMetadata();
  return {
    a11y: { ...defaults.a11y, ...metadata.a11y },
    privacy: { ...defaults.privacy, ...metadata.privacy },
    telemetry: { ...defaults.telemetry, ...metadata.telemetry },
    review: { ...defaults.review, ...metadata.review },
    previewTrust: { ...defaults.previewTrust, ...metadata.previewTrust },
    suggestions: metadata.suggestions || [],
  };
}

export default {
  enforceGovernanceMetadata,
  generateSuggestions,
  applyGovernanceSuggestion,
  getDefaultGovernanceMetadata,
  mergeGovernanceMetadata,
};
