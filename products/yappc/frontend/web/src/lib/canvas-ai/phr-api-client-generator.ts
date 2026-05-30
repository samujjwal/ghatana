/**
 * YAPPC-T04: PHR Web API Client Skeleton Generator
 * 
 * Generates PHR web API client skeletons using phrFetch.
 * Produces TypeScript API client code with proper typing, error handling, and phrFetch integration.
 */

// Use flexible types to handle actual contract structure
export interface PhrRouteContract {
  product: string;
  version: string;
  routes: PhrRoute[];
}

export interface PhrRoute {
  path: string;
  label: string;
  apiEndpoint?: string;
  policyId?: string;
  testId?: string;
  metadata?: Record<string, unknown>;
}

export interface ApiClientConfig {
  routePath: string;
  routeLabel: string;
  apiEndpoint: string;
  policyId: string;
  httpMethod: string;
  requestSchema?: string;
  responseSchema?: string;
}

export interface GeneratedApiClient {
  filePath: string;
  functionName: string;
  code: string;
  imports: string[];
  dependencies: string[];
}

/**
 * Generates TypeScript API client code using phrFetch.
 */
export class PhrApiClientGenerator {
  /**
   * Generates API client skeletons for all routes with API endpoints.
   */
  generateApiClientSkeletons(contract: PhrRouteContract): GeneratedApiClient[] {
    return contract.routes
      .filter(route => this.shouldGenerateApiClient(route))
      .map(route => this.generateApiClient(route));
  }

  /**
   * Determines if an API client should be generated for the route.
   */
  private shouldGenerateApiClient(route: PhrRoute): boolean {
    const apiEndpoint = route.apiEndpoint;
    if (apiEndpoint && typeof apiEndpoint === 'string' && apiEndpoint.startsWith('/api/')) {
      return true;
    }
    
    const metadataEndpoint = route.metadata?.apiEndpoint;
    if (metadataEndpoint && typeof metadataEndpoint === 'string' && metadataEndpoint.startsWith('/api/')) {
      return true;
    }
    
    return false;
  }

  /**
   * Generates a single API client skeleton.
   */
  private generateApiClient(route: PhrRoute): GeneratedApiClient {
    const apiEndpoint = route.apiEndpoint || (route.metadata?.apiEndpoint as string) || '';
    const policyId = route.policyId || (route.metadata?.policyId as string) || '';
    const httpMethod = this.inferHttpMethod(apiEndpoint, route.path);
    const functionName = this.endpointToFunctionName(apiEndpoint);
    const filePath = this.endpointToFilePath(apiEndpoint);

    const config: ApiClientConfig = {
      routePath: route.path,
      routeLabel: route.label,
      apiEndpoint,
      policyId,
      httpMethod,
    };

    const code = this.generateClientCode(config, functionName);
    const imports = this.extractImports(config);
    const dependencies = this.extractDependencies(config);

    return {
      filePath,
      functionName,
      code,
      imports,
      dependencies,
    };
  }

  /**
   * Infers HTTP method from endpoint pattern.
   */
  private inferHttpMethod(apiEndpoint: string, routePath: string): string {
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

  /**
   * Converts API endpoint to function name.
   */
  private endpointToFunctionName(apiEndpoint: string): string {
    const segments = apiEndpoint
      .split('/')
      .filter(segment => segment.length > 0 && segment !== 'api' && segment !== 'v1');
    
    return segments
      .map(segment => {
        // Convert kebab-case to camelCase
        return segment
          .split('-')
          .map((word, index) => index === 0 ? word : word.charAt(0).toUpperCase() + word.slice(1))
          .join('');
      })
      .join('');
  }

  /**
   * Converts API endpoint to file path.
   */
  private endpointToFilePath(apiEndpoint: string): string {
    const segments = apiEndpoint
      .split('/')
      .filter(segment => segment.length > 0 && segment !== 'api' && segment !== 'v1');
    
    if (segments.length === 0) {
      return 'src/api/generatedApi.ts';
    }
    
    const fileName = this.endpointToFunctionName(apiEndpoint);
    return `src/api/generated/${fileName}.ts`;
  }

  /**
   * Generates TypeScript API client code.
   */
  private generateClientCode(config: ApiClientConfig, functionName: string): string {
    const method = config.httpMethod;
    const endpoint = config.apiEndpoint;
    const hasBody = method === 'POST' || method === 'PUT';

    return `import { phrFetch, PhrApiError } from '../requestApi';
import type { RequestContext } from '../requestApi';

/**
 * ${config.routeLabel} API Client
 *
 * Route: ${config.routePath}
 * Endpoint: ${endpoint}
 * Policy: ${config.policyId}
 * Method: ${method}
 *
 * Auto-generated from PHR route contract.
 * Do not edit manually - regenerate from contract.
 */

export interface ${this.capitalize(functionName)}Request${hasBody ? 'Data' : ''} {
  // TODO: Add request data fields based on API contract
  ${hasBody ? 'data: unknown;' : ''}
}

export interface ${this.capitalize(functionName)}Response {
  // TODO: Add response fields based on API contract
  data: unknown;
}

export async function ${functionName}(
  ${hasBody ? 'requestData: ' + this.capitalize(functionName) + 'RequestData,' : ''}
  context?: RequestContext
): Promise<${this.capitalize(functionName)}Response> {
  try {
    const response = await phrFetch<${this.capitalize(functionName)}Response>(
      '${endpoint}',
      {
        method: '${method}',
        ${hasBody ? 'body: JSON.stringify(requestData),' : ''}
        context,
      }
    );
    return response;
  } catch (error) {
    if (error instanceof PhrApiError) {
      // TODO: Add specific error handling for ${config.routeLabel}
      throw error;
    }
    throw new PhrApiError(
      \`Failed to fetch ${config.routeLabel}: \${error instanceof Error ? error.message : 'Unknown error'}\`,
      500
    );
  }
}
`;
  }

  /**
   * Capitalizes first letter of string.
   */
  private capitalize(str: string): string {
    return str.charAt(0).toUpperCase() + str.slice(1);
  }

  /**
   * Extracts required imports from config.
   */
  private extractImports(config: ApiClientConfig): string[] {
    return [
      '../requestApi',
    ];
  }

  /**
   * Extracts required dependencies from config.
   */
  private extractDependencies(config: ApiClientConfig): string[] {
    return [];
  }
}

/**
 * Creates a PHR API client generator instance.
 */
export function createPhrApiClientGenerator(): PhrApiClientGenerator {
  return new PhrApiClientGenerator();
}

/**
 * Generates API client skeletons from PHR route contract.
 */
export function generatePhrApiClientSkeletons(contract: PhrRouteContract): GeneratedApiClient[] {
  return createPhrApiClientGenerator().generateApiClientSkeletons(contract);
}
