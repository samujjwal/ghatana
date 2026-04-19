/**
 * Compliance Checker
 *
 * Check interface compliance against API schema.
 *
 * @packageDocumentation
 */

import type { ApiDefinition } from './ApiSchemaParser';

/**
 * @doc.type service
 * @doc.purpose Check interface compliance against API schema
 * @doc.layer product
 * @doc.pattern Service
 */
export class ComplianceChecker {
  /**
   * Check if generated interfaces comply with API schema.
   *
   * @param apiDefinition - API definition
   * @param generatedCode - Generated TypeScript code
   * @returns Compliance report
   */
  checkCompliance(apiDefinition: ApiDefinition, generatedCode: string): ComplianceReport {
    const issues: ComplianceIssue[] = [];

    // Check if all endpoints are represented
    for (const endpoint of apiDefinition.endpoints) {
      const interfaceName = this.toPascalCase(endpoint.operationId);

      if (!generatedCode.includes(`interface ${interfaceName}Request`)) {
        issues.push({
          severity: 'error',
          message: `Missing interface for endpoint: ${endpoint.operationId}`,
          endpoint: endpoint.operationId,
        });
      }

      // Check parameters
      for (const param of endpoint.parameters) {
        if (param.required && !generatedCode.includes(`${param.name}:`)) {
          issues.push({
            severity: 'error',
            message: `Missing required parameter: ${param.name}`,
            endpoint: endpoint.operationId,
          });
        }
      }
    }

    return {
      compliant: issues.length === 0,
      issues,
      totalEndpoints: apiDefinition.endpoints.length,
      compliantEndpoints: apiDefinition.endpoints.length - issues.filter((i) => i.severity === 'error').length,
    };
  }

  /**
   * Validate a specific interface against schema.
   *
   * @param interfaceCode - Interface TypeScript code
   * @param schema - API schema
   * @returns Validation result
   */
  validateInterface(interfaceCode: string, schema: Record<string, unknown>): ValidationResult {
    const errors: string[] = [];

    // Basic validation - check for required properties
    if (schema.required && Array.isArray(schema.required)) {
      for (const required of schema.required) {
        if (!interfaceCode.includes(required as string)) {
          errors.push(`Missing required property: ${required}`);
        }
      }
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }

  private toPascalCase(str: string): string {
    return str
      .replace(/[-_]/g, ' ')
      .split(' ')
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join('');
  }
}

export interface ComplianceReport {
  compliant: boolean;
  issues: ComplianceIssue[];
  totalEndpoints: number;
  compliantEndpoints: number;
}

export interface ComplianceIssue {
  severity: 'error' | 'warning' | 'info';
  message: string;
  endpoint: string;
}

export interface ValidationResult {
  valid: boolean;
  errors: string[];
}
