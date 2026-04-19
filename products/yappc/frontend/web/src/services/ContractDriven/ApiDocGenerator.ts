/**
 * API Documentation Generator
 *
 * Generate API documentation from schema.
 *
 * @packageDocumentation
 */

import type { ApiDefinition, ApiEndpoint } from './ApiSchemaParser';

/**
 * @doc.type service
 * @doc.purpose Generate API documentation from schema
 * @doc.layer product
 * @doc.pattern Service
 */
export class ApiDocGenerator {
  /**
   * Generate Markdown documentation from API definition.
   *
   * @param apiDefinition - API definition
   * @returns Generated Markdown documentation
   */
  generateMarkdown(apiDefinition: ApiDefinition): string {
    const lines: string[] = [];

    lines.push(`# ${apiDefinition.name} API Documentation`);
    lines.push('');
    lines.push(`Version: ${apiDefinition.version}`);
    lines.push(`Base URL: ${apiDefinition.baseUrl}`);
    lines.push('');
    lines.push('---');
    lines.push('');

    // Generate endpoint documentation
    for (const endpoint of apiDefinition.endpoints) {
      lines.push(this.generateEndpointDoc(endpoint));
      lines.push('');
      lines.push('---');
      lines.push('');
    }

    return lines.join('\n');
  }

  /**
   * Generate documentation for a single endpoint.
   */
  private generateEndpointDoc(endpoint: ApiEndpoint): string {
    const lines: string[] = [];

    lines.push(`## ${endpoint.method} ${endpoint.path}`);
    lines.push('');
    lines.push(`**Operation ID:** \`${endpoint.operationId}\``);
    lines.push('');
    lines.push(`**Summary:** ${endpoint.summary || 'No summary'}`);
    lines.push('');

    if (endpoint.description) {
      lines.push(`**Description:** ${endpoint.description}`);
      lines.push('');
    }

    // Parameters
    if (endpoint.parameters.length > 0) {
      lines.push('### Parameters');
      lines.push('');
      lines.push('| Name | In | Required | Type | Description |');
      lines.push('|------|-----|----------|------|-------------|');

      for (const param of endpoint.parameters) {
        lines.push(`| ${param.name} | ${param.in} | ${param.required ? 'Yes' : 'No'} | ${param.type} | ${param.description || '-'} |`);
      }

      lines.push('');
    }

    // Request Body
    if (endpoint.requestBody) {
      lines.push('### Request Body');
      lines.push('');
      lines.push(`**Content-Type:** ${endpoint.requestBody.contentType}`);
      lines.push(`**Required:** ${endpoint.requestBody.required ? 'Yes' : 'No'}`);

      if (endpoint.requestBody.description) {
        lines.push(`**Description:** ${endpoint.requestBody.description}`);
      }

      lines.push('');
    }

    // Responses
    if (endpoint.responses.length > 0) {
      lines.push('### Responses');
      lines.push('');

      for (const response of endpoint.responses) {
        lines.push(`#### ${response.statusCode}`);
        lines.push('');
        lines.push(`**Description:** ${response.description}`);
        lines.push('');
      }
    }

    return lines.join('\n');
  }

  /**
   * Generate HTML documentation from API definition.
   *
   * @param apiDefinition - API definition
   * @returns Generated HTML documentation
   */
  generateHtml(apiDefinition: ApiDefinition): string {
    const lines: string[] = [];

    lines.push('<!DOCTYPE html>');
    lines.push('<html>');
    lines.push('<head>');
    lines.push(`  <title>${apiDefinition.name} API Documentation</title>`);
    lines.push('  <style>');
    lines.push('    body { font-family: Arial, sans-serif; max-width: 1200px; margin: 0 auto; padding: 20px; }');
    lines.push('    h1, h2, h3 { color: #333; }');
    lines.push('    table { border-collapse: collapse; width: 100%; }');
    lines.push('    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }');
    lines.push('    th { background-color: #f5f5f5; }');
    lines.push('    code { background-color: #f4f4f4; padding: 2px 4px; border-radius: 3px; }');
    lines.push('  </style>');
    lines.push('</head>');
    lines.push('<body>');

    lines.push(`<h1>${apiDefinition.name} API Documentation</h1>`);
    lines.push(`<p>Version: ${apiDefinition.version}</p>`);
    lines.push(`<p>Base URL: ${apiDefinition.baseUrl}</p>`);
    lines.push('<hr>');

    for (const endpoint of apiDefinition.endpoints) {
      lines.push(`<h2>${endpoint.method} ${endpoint.path}</h2>`);
      lines.push(`<p><strong>Operation ID:</strong> <code>${endpoint.operationId}</code></p>`);
      lines.push(`<p><strong>Summary:</strong> ${endpoint.summary || 'No summary'}</p>`);
    }

    lines.push('</body>');
    lines.push('</html>');

    return lines.join('\n');
  }
}
