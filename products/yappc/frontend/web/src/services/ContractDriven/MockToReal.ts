/**
 * Mock to Real Data Transition
 *
 * Transition from mock data to real API integration.
 *
 * @packageDocumentation
 */

/**
 * @doc.type service
 * @doc.purpose Transition from mock data to real API integration
 * @doc.layer product
 * @doc.pattern Service
 */
export class MockToReal {
  /**
   * Generate API client code from mock data.
   *
   * @param mockData - Mock data structure
   * @param apiDefinition - API definition
   * @returns Generated API client code
   */
  generateApiClient(mockData: Record<string, unknown>, apiDefinition: unknown): string {
    const lines: string[] = [];

    lines.push(`// Auto-generated API client`);
    lines.push(`// This transitions from mock data to real API integration`);
    lines.push('');

    lines.push(`export class ApiClient {`);
    lines.push(`  private baseUrl: string;`);
    lines.push(`  private useMock: boolean;`);
    lines.push('');

    lines.push(`  constructor(baseUrl: string, useMock = false) {`);
    lines.push(`    this.baseUrl = baseUrl;`);
    lines.push(`    this.useMock = useMock;`);
    lines.push(`  }`);

    // Generate methods from mock data keys
    for (const key of Object.keys(mockData)) {
      const methodName = this.toCamelCase(key);
      lines.push('');
      lines.push(`  async ${methodName}(): Promise<unknown> {`);
      lines.push(`    if (this.useMock) {`);
      lines.push(`      // Return mock data`);
      lines.push(`      return ${JSON.stringify(mockData[key])};`);
      lines.push(`    }`);
      lines.push('');
      lines.push(`    // Call real API`);
      lines.push(`    const response = await fetch(\`\${this.baseUrl}/${key}\`);`);
      lines.push(`    return response.json();`);
      lines.push(`  }`);
    }

    lines.push(`}`);

    return lines.join('\n');
  }

  /**
   * Generate mock data from API schema.
   *
   * @param schema - API schema
   * @returns Generated mock data
   */
  generateMockData(schema: Record<string, unknown>): Record<string, unknown> {
    const mockData: Record<string, unknown> = {};

    for (const [key, value] of Object.entries(schema)) {
      if (typeof value === 'object' && value !== null) {
        mockData[key] = this.generateMockValue(value as Record<string, unknown>);
      } else {
        mockData[key] = this.generateMockValue({ type: value });
      }
    }

    return mockData;
  }

  /**
   * Generate a mock value from type definition.
   */
  private generateMockValue(typeDef: Record<string, unknown>): unknown {
    const type = typeDef.type as string;

    switch (type) {
      case 'string':
        return 'mock-string';
      case 'number':
      case 'integer':
        return 42;
      case 'boolean':
        return true;
      case 'array':
        return [];
      case 'object':
        return {};
      default:
        return null;
    }
  }

  /**
   * Convert to camelCase.
   */
  private toCamelCase(str: string): string {
    return str
      .replace(/[-_]/g, ' ')
      .split(' ')
      .map((word, index) => {
        if (index === 0) return word.toLowerCase();
        return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase();
      })
      .join('');
  }
}
