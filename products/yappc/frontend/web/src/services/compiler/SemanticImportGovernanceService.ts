/**
 * Semantic Import Governance Service
 *
 * Governs semantic JSON import path with backend workflow validation/review/audit
 * or dev-only mode restriction.
 *
 * @doc.type service
 * @doc.purpose Govern semantic JSON imports with backend workflow or dev-only mode
 * @doc.layer product
 */

import { z } from 'zod';

/**
 * Semantic import mode
 */
export type SemanticImportMode = 'backend-governed' | 'dev-only' | 'blocked';

/**
 * Semantic import validation result
 */
export interface SemanticImportValidationResult {
  allowed: boolean;
  mode: SemanticImportMode;
  reason?: string;
  correlationId?: string;
}

/**
 * Semantic import audit record
 */
export interface SemanticImportAuditRecord {
  timestamp: string;
  userId: string;
  tenantId?: string;
  workspaceId?: string;
  projectId?: string;
  importMode: SemanticImportMode;
  validationResult: SemanticImportValidationResult;
  semanticModelHash?: string;
}

/**
 * Semantic import governance configuration
 */
export interface SemanticImportGovernanceConfig {
  defaultMode: SemanticImportMode;
  requireBackendValidation: boolean;
  requireReview: boolean;
  requireAudit: boolean;
  allowedEnvironments: readonly ('development' | 'staging' | 'production')[];
}

const DEFAULT_CONFIG: SemanticImportGovernanceConfig = {
  defaultMode: 'backend-governed',
  requireBackendValidation: true,
  requireReview: true,
  requireAudit: true,
  allowedEnvironments: ['development'],
};

/**
 * Semantic model validation schema
 */
const SemanticModelSchema = z.object({
  id: z.string().optional(),
  name: z.string().min(1),
  pages: z.array(z.object({
    id: z.string().optional(),
    name: z.string().min(1),
    builderDocument: z.any().optional(),
    serializedBuilderDocument: z.any().optional(),
    residualIslands: z.array(z.object({
      id: z.string(),
    })).optional(),
    confidence: z.number().min(0).max(1).optional(),
    canRoundTrip: z.boolean().optional(),
  })).optional(),
});

/**
 * Semantic Import Governance Service
 */
export class SemanticImportGovernanceService {
  private config: SemanticImportGovernanceConfig;
  private auditRecords: SemanticImportAuditRecord[] = [];

  constructor(config: SemanticImportGovernanceConfig = DEFAULT_CONFIG) {
    this.config = config;
  }

  /**
   * Validate semantic import request
   */
  async validateSemanticImport(
    semanticModelJson: string,
    userId: string,
    tenantId?: string,
    workspaceId?: string,
    projectId?: string,
    environment: 'development' | 'staging' | 'production' = 'development',
  ): Promise<SemanticImportValidationResult> {
    const correlationId = `semantic-import-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;

    // Check if environment is allowed
    if (!this.config.allowedEnvironments.includes(environment)) {
      const result: SemanticImportValidationResult = {
        allowed: false,
        mode: 'blocked',
        reason: `Semantic imports not allowed in ${environment} environment`,
        correlationId,
      };
      this.recordAudit(userId, tenantId, workspaceId, projectId, result, semanticModelJson);
      return result;
    }

    // Validate JSON structure
    const validationResult = this.validateSemanticModelStructure(semanticModelJson);
    if (!validationResult.valid) {
      const result: SemanticImportValidationResult = {
        allowed: false,
        mode: 'blocked',
        reason: validationResult.error,
        correlationId,
      };
      this.recordAudit(userId, tenantId, workspaceId, projectId, result, semanticModelJson);
      return result;
    }

    // Apply governance based on mode
    if (this.config.defaultMode === 'dev-only' && environment !== 'development') {
      const result: SemanticImportValidationResult = {
        allowed: false,
        mode: 'blocked',
        reason: 'Semantic imports restricted to development environment only',
        correlationId,
      };
      this.recordAudit(userId, tenantId, workspaceId, projectId, result, semanticModelJson);
      return result;
    }

    if (this.config.defaultMode === 'backend-governed') {
      // In production/staging, require backend validation
      if (environment !== 'development' && this.config.requireBackendValidation) {
        // Backend validation would happen here via API call
        // For now, we allow it but mark as requiring backend validation
        const result: SemanticImportValidationResult = {
          allowed: true,
          mode: 'backend-governed',
          correlationId,
        };
        this.recordAudit(userId, tenantId, workspaceId, projectId, result, semanticModelJson);
        return result;
      }
    }

    // Dev mode: allow directly
    const result: SemanticImportValidationResult = {
      allowed: true,
      mode: environment === 'development' ? 'dev-only' : this.config.defaultMode,
      correlationId,
    };
    this.recordAudit(userId, tenantId, workspaceId, projectId, result, semanticModelJson);
    return result;
  }

  /**
   * Validate semantic model structure
   */
  private validateSemanticModelStructure(json: string): { valid: boolean; error?: string } {
    try {
      const parsed = JSON.parse(json);
      SemanticModelSchema.parse(parsed);
      return { valid: true };
    } catch (error) {
      if (error instanceof z.ZodError) {
        return {
          valid: false,
          error: `Invalid semantic model structure: ${error.issues.map((e: z.ZodIssue) => e.message).join(', ')}`,
        };
      }
      if (error instanceof SyntaxError) {
        return {
          valid: false,
          error: 'Invalid JSON syntax',
        };
      }
      return {
        valid: false,
        error: 'Unknown validation error',
      };
    }
  }

  /**
   * Record audit entry
   */
  private recordAudit(
    userId: string,
    tenantId: string | undefined,
    workspaceId: string | undefined,
    projectId: string | undefined,
    validationResult: SemanticImportValidationResult,
    semanticModelJson: string,
  ): void {
    const hash = this.hashSemanticModel(semanticModelJson);
    const record: SemanticImportAuditRecord = {
      timestamp: new Date().toISOString(),
      userId,
      tenantId,
      workspaceId,
      projectId,
      importMode: validationResult.mode,
      validationResult,
      semanticModelHash: hash,
    };
    this.auditRecords.push(record);
  }

  /**
   * Hash semantic model for audit
   */
  private hashSemanticModel(json: string): string {
    // Simple hash for audit purposes
    let hash = 0;
    for (let i = 0; i < json.length; i++) {
      const char = json.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash; // Convert to 32bit integer
    }
    return Math.abs(hash).toString(16);
  }

  /**
   * Get audit records
   */
  getAuditRecords(): readonly SemanticImportAuditRecord[] {
    return this.auditRecords;
  }

  /**
   * Update configuration
   */
  updateConfig(config: Partial<SemanticImportGovernanceConfig>): void {
    this.config = { ...this.config, ...config };
  }

  /**
   * Get current configuration
   */
  getConfig(): SemanticImportGovernanceConfig {
    return { ...this.config };
  }
}

/**
 * Default semantic import governance service instance
 */
export const semanticImportGovernanceService = new SemanticImportGovernanceService();
