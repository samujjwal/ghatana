export interface ProductRouteContract {
  product: string;
  version: string;
  routes: ProductRoute[];
}

export interface ProductRoute {
  path: string;
  label: string;
  apiEndpoint?: string;
  policyId?: string;
  testId?: string;
  metadata?: Record<string, unknown>;
}

export interface ApiClientConfig {
  productId: string;
  routePath: string;
  routeLabel: string;
  apiEndpoint: string;
  policyId: string;
  httpMethod: 'GET' | 'POST' | 'PUT' | 'DELETE';
}

export interface GeneratedApiClient {
  filePath: string;
  functionName: string;
  code: string;
  imports: string[];
  dependencies: string[];
}

export class ApiClientGenerator {
  generateApiClientSkeletons(contract: ProductRouteContract): GeneratedApiClient[] {
    return contract.routes
      .filter((route: ProductRoute): boolean => this.shouldGenerateApiClient(route))
      .map((route: ProductRoute): GeneratedApiClient => this.generateApiClient(contract.product, route));
  }

  private shouldGenerateApiClient(route: ProductRoute): boolean {
    const apiEndpoint = this.apiEndpoint(route);
    return apiEndpoint !== null && apiEndpoint.startsWith('/api/');
  }

  private generateApiClient(productId: string, route: ProductRoute): GeneratedApiClient {
    const apiEndpoint = this.apiEndpoint(route);
    if (apiEndpoint === null) {
      throw new Error(`Cannot generate API client without apiEndpoint for route ${route.path}`);
    }

    const policyId = this.policyId(route) ?? '';
    const httpMethod = this.inferHttpMethod(apiEndpoint);
    const functionName = this.endpointToFunctionName(apiEndpoint);
    const filePath = this.endpointToFilePath(apiEndpoint);
    const config: ApiClientConfig = {
      productId,
      routePath: route.path,
      routeLabel: route.label,
      apiEndpoint,
      policyId,
      httpMethod,
    };

    return {
      filePath,
      functionName,
      code: this.generateClientCode(config, functionName),
      imports: ['../requestApi'],
      dependencies: [],
    };
  }

  private inferHttpMethod(apiEndpoint: string): ApiClientConfig['httpMethod'] {
    if (apiEndpoint.includes('/create') || apiEndpoint.includes('/add')) {
      return 'POST';
    }
    if (apiEndpoint.includes('/update') || apiEndpoint.includes('/edit')) {
      return 'PUT';
    }
    if (apiEndpoint.includes('/delete') || apiEndpoint.includes('/remove')) {
      return 'DELETE';
    }
    return 'GET';
  }

  private endpointToFunctionName(apiEndpoint: string): string {
    const segments = apiEndpoint
      .split('/')
      .filter((segment: string): boolean => segment.length > 0 && segment !== 'api' && segment !== 'v1');

    return segments
      .map((segment: string): string => segment
        .split('-')
        .map((word: string, index: number): string => (index === 0 ? word : word.charAt(0).toUpperCase() + word.slice(1)))
        .join(''))
      .join('');
  }

  private endpointToFilePath(apiEndpoint: string): string {
    const fileName = this.endpointToFunctionName(apiEndpoint);
    return fileName.length === 0 ? 'src/api/generatedApi.ts' : `src/api/generated/${fileName}.ts`;
  }

  private generateClientCode(config: ApiClientConfig, functionName: string): string {
    const hasBody = config.httpMethod === 'POST' || config.httpMethod === 'PUT';
    const requestDataParameter = hasBody ? `requestData: ${this.capitalize(functionName)}RequestData,\n  ` : '';
    const requestBodyLine = hasBody ? '        body: JSON.stringify(requestData),\n' : '';

    return `import { productFetch, ProductApiError } from '../requestApi';
import type { RequestContext } from '../requestApi';

/**
 * ${config.routeLabel} API Client
 *
 * Product: ${config.productId}
 * Route: ${config.routePath}
 * Endpoint: ${config.apiEndpoint}
 * Policy: ${config.policyId}
 * Method: ${config.httpMethod}
 *
 * Auto-generated from Kernel product route contract.
 */

export interface ${this.capitalize(functionName)}RequestData {
  data: unknown;
}

export interface ${this.capitalize(functionName)}Response {
  data: unknown;
}

export async function ${functionName}(
  ${requestDataParameter}context?: RequestContext
): Promise<${this.capitalize(functionName)}Response> {
  try {
    const response = await productFetch<${this.capitalize(functionName)}Response>(
      '${config.apiEndpoint}',
      {
        method: '${config.httpMethod}',
${requestBodyLine}        context,
      }
    );
    return response;
  } catch (error: unknown) {
    if (error instanceof ProductApiError) {
      throw error;
    }
    throw new ProductApiError(
      \`Failed to fetch ${config.routeLabel}: \${error instanceof Error ? error.message : 'Unknown error'}\`,
      500
    );
  }
}
`;
  }

  private apiEndpoint(route: ProductRoute): string | null {
    return metadataString(route, 'apiEndpoint', route.apiEndpoint);
  }

  private policyId(route: ProductRoute): string | null {
    return metadataString(route, 'policyId', route.policyId);
  }

  private capitalize(value: string): string {
    return value.charAt(0).toUpperCase() + value.slice(1);
  }
}

function metadataString(route: ProductRoute, key: string, directValue: string | undefined): string | null {
  if (directValue !== undefined && directValue.trim().length > 0) {
    return directValue;
  }
  const value = route.metadata?.[key];
  return typeof value === 'string' && value.trim().length > 0 ? value : null;
}

export function createApiClientGenerator(): ApiClientGenerator {
  return new ApiClientGenerator();
}

export function generateApiClientSkeletons(contract: ProductRouteContract): GeneratedApiClient[] {
  return createApiClientGenerator().generateApiClientSkeletons(contract);
}
