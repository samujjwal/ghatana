/**
 * Semantic Import Governance Service
 *
 * @doc.type service
 * @doc.purpose Govern semantic JSON import paths to ensure backend workflow or dev-only mode
 * @doc.layer product
 * @doc.pattern Service Layer
 */

export interface SemanticImportValidationResult {
  readonly allowed: boolean;
  readonly reason?: string;
  readonly requiresDevMode?: boolean;
}

export interface SemanticImportGovernanceContext {
  readonly isDevMode: boolean;
  readonly projectId?: string;
  readonly auditContext?: {
    readonly tenantId?: string;
    readonly workspaceId?: string;
  };
}

/**
 * Validates that semantic JSON imports follow governance rules
 *
 * Semantic JSON imports must either:
 * 1. Go through the governed backend workflow (with proper tenant/workspace/project context)
 * 2. Be in dev-only mode (for local development)
 *
 * Direct pasting of source commands or unvalidated semantic models is blocked in production.
 */
export function validateSemanticImport(
  input: string,
  context: SemanticImportGovernanceContext
): SemanticImportValidationResult {
  const trimmedInput = input.trim();

  // Check for source command pattern - these must use the governed workflow
  if (trimmedInput.startsWith('source:')) {
    return {
      allowed: false,
      reason: 'Source imports must use the governed source workflow. Choose Governed source instead of pasting source commands.',
    };
  }

  // Check for unvalidated semantic models in production
  if (!context.isDevMode) {
    // In production, semantic model imports require proper governance context
    if (!context.projectId) {
      return {
        allowed: false,
        reason: 'Semantic model imports in production require an active project context.',
      };
    }

    if (!context.auditContext?.tenantId || !context.auditContext?.workspaceId) {
      return {
        allowed: false,
        reason: 'Semantic model imports in production require authenticated tenant and workspace context.',
      };
    }
  }

  // In dev mode, allow with warning
  if (context.isDevMode) {
    return {
      allowed: true,
      requiresDevMode: true,
    };
  }

  // In production with proper context, allow
  return {
    allowed: true,
  };
}

/**
 * Checks if a semantic model import is properly governed
 *
 * This is a stricter check that ensures the import has gone through
 * the proper backend governance pipeline with audit trail.
 */
export function isProperlyGovernedSemanticImport(
  input: string,
  context: SemanticImportGovernanceContext
): boolean {
  const validation = validateSemanticImport(input, context);
  
  // Must be allowed and not require dev mode (i.e., properly governed in production)
  return validation.allowed && !validation.requiresDevMode;
}
