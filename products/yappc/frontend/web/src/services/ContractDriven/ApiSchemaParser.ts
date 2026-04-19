/**
 * API Schema Parser
 *
 * Parse API schemas (OpenAPI, GraphQL, JSON Schema) for contract generation.
 *
 * @packageDocumentation
 */

/**
 * @doc.type service
 * @doc.purpose Parse API schemas for contract generation
 * @doc.layer product
 * @doc.pattern Service
 */
export class ApiSchemaParser {
  /**
   * Parse an OpenAPI/Swagger schema.
   *
   * @param schema - OpenAPI schema object
   * @returns Parsed API definition
   */
  parseOpenApi(schema: unknown): ApiDefinition {
    // In production, this would use a proper OpenAPI parser
    const def = schema as Record<string, unknown>;

    return {
      name: def.info?.title as string || 'API',
      version: def.info?.version as string || '1.0.0',
      baseUrl: def.servers?.[0]?.url as string || '',
      endpoints: this.parseEndpoints(def.paths as Record<string, unknown> || {}),
    };
  }

  /**
   * Parse a GraphQL schema.
   *
   * @param schema - GraphQL schema SDL string
   * @returns Parsed API definition
   */
  parseGraphQL(schema: string): ApiDefinition {
    // In production, this would use a GraphQL parser
    return {
      name: 'GraphQL API',
      version: '1.0.0',
      baseUrl: '',
      endpoints: [],
    };
  }

  /**
   * Parse a JSON Schema.
   *
   * @param schema - JSON Schema object
   * @returns Parsed API definition
   */
  parseJsonSchema(schema: unknown): ApiDefinition {
    // In production, this would use a JSON Schema parser
    const def = schema as Record<string, unknown>;

    return {
      name: def.title as string || 'API',
      version: def.version as string || '1.0.0',
      baseUrl: '',
      endpoints: [],
    };
  }

  /**
   * Parse endpoints from OpenAPI paths.
   */
  private parseEndpoints(paths: Record<string, unknown>): ApiEndpoint[] {
    const endpoints: ApiEndpoint[] = [];

    for (const [path, pathItem] of Object.entries(paths)) {
      const item = pathItem as Record<string, unknown>;

      for (const [method, operation] of Object.entries(item)) {
        if (['get', 'post', 'put', 'delete', 'patch'].includes(method)) {
          const op = operation as Record<string, unknown>;

          endpoints.push({
            path,
            method: method.toUpperCase(),
            operationId: op.operationId as string || `${method}${path}`,
            summary: op.summary as string || '',
            description: op.description as string || '',
            parameters: this.parseParameters(op.parameters as unknown[] || []),
            requestBody: this.parseRequestBody(op.requestBody),
            responses: this.parseResponses(op.responses as Record<string, unknown>),
          });
        }
      }
    }

    return endpoints;
  }

  /**
   * Parse parameters from OpenAPI operation.
   */
  private parseParameters(parameters: unknown[]): ApiParameter[] {
    return parameters.map((param) => {
      const p = param as Record<string, unknown>;
      return {
        name: p.name as string,
        in: p.in as string,
        required: p.required as boolean || false,
        type: p.schema?.type as string || 'string',
        description: p.description as string || '',
      };
    });
  }

  /**
   * Parse request body from OpenAPI operation.
   */
  private parseRequestBody(requestBody: unknown): ApiRequestBody | null {
    if (!requestBody) return null;

    const body = requestBody as Record<string, unknown>;
    const content = body.content as Record<string, unknown>;
    const jsonSchema = content?.['application/json'] as Record<string, unknown>;

    return {
      contentType: 'application/json',
      schema: jsonSchema?.schema as Record<string, unknown> || {},
      required: body.required as boolean || false,
      description: body.description as string || '',
    };
  }

  /**
   * Parse responses from OpenAPI operation.
   */
  private parseResponses(responses: Record<string, unknown>): ApiResponse[] {
    const parsed: ApiResponse[] = [];

    for (const [statusCode, response] of Object.entries(responses)) {
      const resp = response as Record<string, unknown>;
      const content = resp.content as Record<string, unknown>;
      const jsonSchema = content?.['application/json'] as Record<string, unknown>;

      parsed.push({
        statusCode: parseInt(statusCode, 10),
        description: resp.description as string || '',
        schema: jsonSchema?.schema as Record<string, unknown> || {},
      });
    }

    return parsed;
  }
}

export interface ApiDefinition {
  name: string;
  version: string;
  baseUrl: string;
  endpoints: ApiEndpoint[];
}

export interface ApiEndpoint {
  path: string;
  method: string;
  operationId: string;
  summary: string;
  description: string;
  parameters: ApiParameter[];
  requestBody: ApiRequestBody | null;
  responses: ApiResponse[];
}

export interface ApiParameter {
  name: string;
  in: string;
  required: boolean;
  type: string;
  description: string;
}

export interface ApiRequestBody {
  contentType: string;
  schema: Record<string, unknown>;
  required: boolean;
  description: string;
}

export interface ApiResponse {
  statusCode: number;
  description: string;
  schema: Record<string, unknown>;
}
