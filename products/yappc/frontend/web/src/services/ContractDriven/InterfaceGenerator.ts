/**
 * Interface Generator
 *
 * Generate TypeScript interfaces from API schemas.
 *
 * @packageDocumentation
 */

import type { ApiDefinition, ApiEndpoint, ApiParameter, ApiResponse } from './ApiSchemaParser';

/**
 * @doc.type service
 * @doc.purpose Generate TypeScript interfaces from API schemas
 * @doc.layer product
 * @doc.pattern Service
 */
export class InterfaceGenerator {
  /**
   * Generate TypeScript interfaces from API definition.
   *
   * @param apiDefinition - Parsed API definition
   * @returns Generated TypeScript code
   */
  generateInterfaces(apiDefinition: ApiDefinition): string {
    const lines: string[] = [];

    // Add header
    lines.push(`// Auto-generated interfaces for ${apiDefinition.name}`);
    lines.push(`// Version: ${apiDefinition.version}`);
    lines.push('');

    // Generate interfaces for each endpoint
    for (const endpoint of apiDefinition.endpoints) {
      lines.push(this.generateEndpointInterface(endpoint));
      lines.push('');
    }

    // Generate request/response type unions
    lines.push(this.generateApiTypes(apiDefinition));

    return lines.join('\n');
  }

  /**
   * Generate interface for a single endpoint.
   */
  private generateEndpointInterface(endpoint: ApiEndpoint): string {
    const lines: string[] = [];
    const interfaceName = this.toPascalCase(endpoint.operationId);

    lines.push(`export interface ${interfaceName}Request {`);
    lines.push(`  /**`);
    lines.push(`   * ${endpoint.description || endpoint.summary}`);
    lines.push(`   */`);

    // Add parameters
    for (const param of endpoint.parameters) {
      const optional = param.required ? '' : '?';
      lines.push(`  ${param.name}${optional}: ${this.mapType(param.type)};`);
    }

    // Add request body
    if (endpoint.requestBody) {
      lines.push(`  body?: ${this.generateBodyInterface(endpoint.operationId, endpoint.requestBody.schema)};`);
    }

    lines.push(`}`);

    // Add response interface
    const successResponse = endpoint.responses.find((r) => r.statusCode >= 200 && r.statusCode < 300);
    if (successResponse && successResponse.schema) {
      lines.push('');
      lines.push(`export interface ${interfaceName}Response {`);
      lines.push(`  data: ${this.generateSchemaInterface(endpoint.operationId, successResponse.schema)};`);
      lines.push(`  status: ${successResponse.statusCode};`);
      lines.push(`}`);
    }

    return lines.join('\n');
  }

  /**
   * Generate API types union.
   */
  private generateApiTypes(apiDefinition: ApiDefinition): string {
    const lines: string[] = [];

    lines.push(`export type ${this.toPascalCase(apiDefinition.name)}Endpoints =`);
    lines.push(`  | ${apiDefinition.endpoints.map((e) => `'${e.operationId}'`).join('\n  | ')};`);

    return lines.join('\n');
  }

  /**
   * Generate body interface from schema.
   */
  private generateBodyInterface(operationId: string, schema: Record<string, unknown>): string {
    const interfaceName = `${this.toPascalCase(operationId)}Body`;

    if (Object.keys(schema).length === 0) {
      return 'Record<string, unknown>';
    }

    return interfaceName;
  }

  /**
   * Generate schema interface.
   */
  private generateSchemaInterface(operationId: string, schema: Record<string, unknown>): string {
    const interfaceName = `${this.toPascalCase(operationId)}Data`;

    if (Object.keys(schema).length === 0) {
      return 'unknown';
    }

    return interfaceName;
  }

  /**
   * Map OpenAPI type to TypeScript type.
   */
  private mapType(type: string): string {
    const typeMap: Record<string, string> = {
      string: 'string',
      number: 'number',
      integer: 'number',
      boolean: 'boolean',
      array: 'unknown[]',
      object: 'Record<string, unknown>',
    };

    return typeMap[type] || 'unknown';
  }

  /**
   * Convert to PascalCase.
   */
  private toPascalCase(str: string): string {
    return str
      .replace(/[-_]/g, ' ')
      .split(' ')
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join('');
  }
}
