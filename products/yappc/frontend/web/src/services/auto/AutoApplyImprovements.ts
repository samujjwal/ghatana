/**
 * Auto-Apply Safe Improvements
 *
 * Automatically applies safe improvements with confidence, source, rationale, and rollback.
 *
 * @doc.type service
 * @doc.purpose Auto-apply safe improvements
 * @doc.layer product
 */

export interface Improvement {
  /** Improvement ID */
  id: string;
  /** Improvement type */
  type: 'accessibility' | 'performance' | 'security' | 'best-practice' | 'consistency';
  /** Improvement title */
  title: string;
  /** Improvement description */
  description: string;
  /** Confidence score (0-1) */
  confidence: number;
  /** Source of suggestion */
  source: string;
  /** Rationale */
  rationale: string;
  /** Apply function */
  apply: (current: unknown) => unknown;
  /** Rollback function */
  rollback: (applied: unknown) => unknown;
  /** Risk level */
  risk: 'none' | 'low' | 'medium' | 'high';
  /** Requires approval flag */
  requiresApproval: boolean;
}

export interface ImprovementApplication {
  /** Improvement ID */
  improvementId: string;
  /** Original value */
  originalValue: unknown;
  /** Applied value */
  appliedValue: unknown;
  /** Application timestamp */
  appliedAt: string;
  /** User ID */
  userId?: string;
  /** Approved by */
  approvedBy?: string;
  /** Rollback flag */
  rolledBack: boolean;
  /** Rollback timestamp */
  rolledBackAt?: string;
}

export interface AutoApplyOptions {
  /** Minimum confidence threshold */
  minConfidence?: number;
  /** Maximum risk level */
  maxRisk?: 'none' | 'low' | 'medium';
  /** Require approval flag */
  requireApproval?: boolean;
  /** Auto-apply flag */
  autoApply?: boolean;
}

function asRecord(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

/**
 * Auto-apply safe improvements
 */
export function autoApplyImprovements(
  currentValue: unknown,
  improvements: Improvement[],
  options: AutoApplyOptions = {}
): { applied: ImprovementApplication[]; skipped: Improvement[]; errors: string[] } {
  const {
    minConfidence = 0.8,
    maxRisk = 'low',
    requireApproval = false,
    autoApply = true,
  } = options;

  const applied: ImprovementApplication[] = [];
  const skipped: Improvement[] = [];
  const errors: string[] = [];

  for (const improvement of improvements) {
    // Check confidence threshold
    if (improvement.confidence < minConfidence) {
      skipped.push(improvement);
      continue;
    }

    // Check risk level
    const riskLevels = ['none', 'low', 'medium', 'high'];
    const currentRiskIndex = riskLevels.indexOf(improvement.risk);
    const maxRiskIndex = riskLevels.indexOf(maxRisk);
    if (currentRiskIndex > maxRiskIndex) {
      skipped.push(improvement);
      continue;
    }

    // Check approval requirement
    if (improvement.requiresApproval && requireApproval) {
      skipped.push(improvement);
      continue;
    }

    // Apply improvement
    try {
      const appliedValue = improvement.apply(currentValue);
      applied.push({
        improvementId: improvement.id,
        originalValue: currentValue,
        appliedValue,
        appliedAt: new Date().toISOString(),
        rolledBack: false,
      });
    } catch (error) {
      errors.push(`Failed to apply improvement ${improvement.id}: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  return { applied, skipped, errors };
}

/**
 * Rollback improvement
 */
export function rollbackImprovement(
  application: ImprovementApplication,
  improvement: Improvement
): { success: boolean; rolledBackValue?: unknown; error?: string } {
  try {
    const rolledBackValue = improvement.rollback(application.appliedValue);
    return {
      success: true,
      rolledBackValue,
    };
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : String(error),
    };
  }
}

/**
 * Get improvement suggestions
 */
export function getImprovementSuggestions(
  context: Record<string, unknown>
): Improvement[] {
  const improvements: Improvement[] = [];

  // Accessibility improvements
  if (context.missingAriaLabels) {
    improvements.push({
      id: 'add-aria-label',
      type: 'accessibility',
      title: 'Add aria-label',
      description: 'Add aria-label attribute for screen reader compatibility',
      confidence: 0.95,
      source: 'a11y-rules',
      rationale: 'Interactive elements should have aria-label for accessibility',
      apply: (value) => ({ ...asRecord(value), ariaLabel: '' }),
      rollback: (value) => {
        const { ariaLabel, ...rest } = value as Record<string, unknown>;
        return rest;
      },
      risk: 'none',
      requiresApproval: false,
    });
  }

  // Performance improvements
  if (context.unoptimizedImages) {
    improvements.push({
      id: 'optimize-images',
      type: 'performance',
      title: 'Optimize images',
      description: 'Add lazy loading and proper sizing to images',
      confidence: 0.9,
      source: 'perf-rules',
      rationale: 'Lazy loading improves initial page load performance',
      apply: (value) => ({ ...asRecord(value), loading: 'lazy' }),
      rollback: (value) => {
        const { loading, ...rest } = value as Record<string, unknown>;
        return rest;
      },
      risk: 'none',
      requiresApproval: false,
    });
  }

  // Security improvements
  if (context.missingCSP) {
    improvements.push({
      id: 'add-csp',
      type: 'security',
      title: 'Add Content Security Policy',
      description: 'Add CSP headers for enhanced security',
      confidence: 0.85,
      source: 'sec-rules',
      rationale: 'CSP helps prevent XSS attacks',
      apply: (value) => ({ ...asRecord(value), csp: 'default-src self;' }),
      rollback: (value) => {
        const { csp, ...rest } = value as Record<string, unknown>;
        return rest;
      },
      risk: 'low',
      requiresApproval: true,
    });
  }

  // Best practice improvements
  if (context.inconsistentNaming) {
    improvements.push({
      id: 'standardize-naming',
      type: 'consistency',
      title: 'Standardize naming convention',
      description: 'Apply consistent naming convention to elements',
      confidence: 0.8,
      source: 'style-rules',
      rationale: 'Consistent naming improves code maintainability',
      apply: (value) => value, // Placeholder
      rollback: (value) => value,
      risk: 'low',
      requiresApproval: true,
    });
  }

  return improvements;
}

/**
 * Calculate improvement confidence
 */
export function calculateImprovementConfidence(
  improvement: Improvement,
  context: Record<string, unknown>
): number {
  let confidence = improvement.confidence;

  // Boost confidence for high-impact, low-risk improvements
  if (improvement.risk === 'none' && improvement.type === 'accessibility') {
    confidence += 0.05;
  }

  // Reduce confidence for changes that might break existing functionality
  if (improvement.type === 'security' && improvement.risk !== 'none') {
    confidence -= 0.1;
  }

  return Math.max(0, Math.min(1, confidence));
}

/**
 * Batch apply improvements
 */
export function batchApplyImprovements(
  currentValue: unknown,
  improvements: Improvement[],
  options: AutoApplyOptions = {}
): { finalValue: unknown; applications: ImprovementApplication[]; rollbackStack: ImprovementApplication[] } {
  let currentValueCopy = currentValue;
  const applications: ImprovementApplication[] = [];
  const rollbackStack: ImprovementApplication[] = [];

  for (const improvement of improvements) {
    try {
      const appliedValue = improvement.apply(currentValueCopy);
      const application: ImprovementApplication = {
        improvementId: improvement.id,
        originalValue: currentValueCopy,
        appliedValue,
        appliedAt: new Date().toISOString(),
        rolledBack: false,
      };

      applications.push(application);
      rollbackStack.push(application);
      currentValueCopy = appliedValue;
    } catch (error) {
      // Skip failed improvements
      console.error(`Failed to apply improvement ${improvement.id}:`, error);
    }
  }

  return {
    finalValue: currentValueCopy,
    applications,
    rollbackStack,
  };
}

/**
 * Batch rollback improvements
 */
export function batchRollbackImprovements(
  rollbackStack: ImprovementApplication[],
  improvements: Map<string, Improvement>
): { finalValue: unknown; success: boolean; errors: string[] } {
  let currentValue = rollbackStack[rollbackStack.length - 1]?.appliedValue;
  const errors: string[] = [];

  // Rollback in reverse order
  for (let i = rollbackStack.length - 1; i >= 0; i--) {
    const application = rollbackStack[i];
    const improvement = improvements.get(application.improvementId);

    if (improvement) {
      const result = rollbackImprovement(application, improvement);
      if (result.success) {
        currentValue = result.rolledBackValue;
        application.rolledBack = true;
        application.rolledBackAt = new Date().toISOString();
      } else {
        errors.push(`Failed to rollback ${application.improvementId}: ${result.error}`);
      }
    }
  }

  return {
    finalValue: currentValue,
    success: errors.length === 0,
    errors,
  };
}

export default {
  autoApplyImprovements,
  rollbackImprovement,
  getImprovementSuggestions,
  calculateImprovementConfidence,
  batchApplyImprovements,
  batchRollbackImprovements,
};
