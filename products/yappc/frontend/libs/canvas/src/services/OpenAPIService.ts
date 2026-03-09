/**
 * OpenAPIService
 * 
 * Generates OpenAPI 3.0 specifications from canvas API nodes.
 * Supports schema inference, authentication, and multi-endpoint specs.
 * 
 * @doc.type service
 * @doc.purpose OpenAPI 3.0 YAML generation
 * @doc.layer product
 * @doc.pattern Service
 */

import type { Node } from '@xyflow/react';
import yaml from 'js-yaml';

/**
 * HTTP methods supported by OpenAPI
 */
export type HTTPMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'OPTIONS' | 'HEAD';

/**
 * Authentication types
 */
export type AuthenticationType = 'none' | 'jwt' | 'apiKey' | 'oauth2' | 'basic';

/**
 * Parameter location
 */
export type ParameterLocation = 'query' | 'path' | 'header' | 'cookie';

/**
 * OpenAPI parameter definition
 */
export interface OpenAPIParameter {
    name: string;
    in: ParameterLocation;
    description?: string;
    required?: boolean;
    schema: OpenAPISchema;
}

/**
 * OpenAPI schema definition
 */
export interface OpenAPISchema {
    type: 'string' | 'number' | 'integer' | 'boolean' | 'array' | 'object';
    format?: string;
    items?: OpenAPISchema;
    properties?: Record<string, OpenAPISchema>;
    required?: string[];
    example?: unknown;
    description?: string;
    enum?: unknown[];
}

/**
 * OpenAPI request body
 */
export interface OpenAPIRequestBody {
    description?: string;
    required?: boolean;
    content: {
        [mediaType: string]: {
            schema: OpenAPISchema;
        };
    };
}

/**
 * OpenAPI response
 */
export interface OpenAPIResponse {
    description: string;
    content?: {
        [mediaType: string]: {
            schema: OpenAPISchema;
        };
    };
}

/**
 * OpenAPI endpoint definition
 */
export interface OpenAPIEndpoint {
    path: string;
    method: HTTPMethod;
    summary?: string;
    description?: string;
    tags?: string[];
    parameters?: OpenAPIParameter[];
    requestBody?: OpenAPIRequestBody;
    responses: Record<string, OpenAPIResponse>;
    security?: Array<Record<string, string[]>>;
}

/**
 * OpenAPI specification
 */
export interface OpenAPISpec {
    openapi: '3.0.0' | '3.0.1' | '3.0.2' | '3.0.3' | '3.1.0';
    info: {
        title: string;
        version: string;
        description?: string;
        contact?: {
            name?: string;
            email?: string;
            url?: string;
        };
        license?: {
            name: string;
            url?: string;
        };
    };
    servers?: Array<{
        url: string;
        description?: string;
    }>;
    paths: Record<string, Record<string, unknown>>;
    components?: {
        schemas?: Record<string, OpenAPISchema>;
        securitySchemes?: Record<string, unknown>;
    };
    security?: Array<Record<string, string[]>>;
}

/**
 * API node data interface
 */
export interface APINodeData {
    label?: string;
    apiPath?: string;
    method?: HTTPMethod;
    authentication?: AuthenticationType;
    requestSchema?: string;
    responseSchema?: string;
    description?: string;
    tags?: string[];
    parameters?: OpenAPIParameter[];
    rateLimit?: number;
}

/**
 * OpenAPI generation options
 */
export interface OpenAPIGenerationOptions {
    title?: string;
    version?: string;
    description?: string;
    serverUrl?: string;
    includeExamples?: boolean;
    includeSchemas?: boolean;
}

/**
 * OpenAPI Service
 */
export class OpenAPIService {
    /**
     * Generate OpenAPI spec from API nodes
     */
    static generateSpec(
        apiNodes: Node<APINodeData>[],
        options: OpenAPIGenerationOptions = {}
    ): OpenAPISpec {
        const {
            title = 'Generated API',
            version = '1.0.0',
            description = 'API generated from YAPPC canvas',
            serverUrl = 'http://localhost:3000',
            includeSchemas = true,
        } = options;

        const spec: OpenAPISpec = {
            openapi: '3.0.3',
            info: {
                title,
                version,
                description,
            },
            servers: [
                {
                    url: serverUrl,
                    description: 'Development server',
                },
            ],
            paths: {},
            components: includeSchemas ? { schemas: {}, securitySchemes: {} } : undefined,
        };

        // Group nodes by authentication type for security schemes
        const authTypes = new Set<AuthenticationType>();

        // Process each API node
        for (const node of apiNodes) {
            const data = node.data;
            if (!data.apiPath || !data.method) continue;

            const endpoint = this.nodeToEndpoint(node, options);
            const path = data.apiPath;
            const method = data.method.toLowerCase();

            // Initialize path if not exists
            if (!spec.paths[path]) {
                spec.paths[path] = {};
            }

            // Add endpoint
            spec.paths[path][method] = {
                summary: endpoint.summary,
                description: endpoint.description,
                tags: endpoint.tags,
                parameters: endpoint.parameters,
                requestBody: endpoint.requestBody,
                responses: endpoint.responses,
                security: endpoint.security,
            };

            // Track auth types
            if (data.authentication && data.authentication !== 'none') {
                authTypes.add(data.authentication);
            }
        }

        // Add security schemes
        if (spec.components?.securitySchemes) {
            for (const authType of authTypes) {
                spec.components.securitySchemes[authType] = this.getSecurityScheme(authType);
            }
        }

        return spec;
    }

    /**
     * Convert node to OpenAPI endpoint
     */
    private static nodeToEndpoint(
        node: Node<APINodeData>,
        options: OpenAPIGenerationOptions
    ): OpenAPIEndpoint {
        const data = node.data;
        const endpoint: OpenAPIEndpoint = {
            path: data.apiPath || '/unknown',
            method: data.method || 'GET',
            summary: data.label || `${data.method} ${data.apiPath}`,
            description: data.description,
            tags: data.tags || ['default'],
            parameters: data.parameters || this.inferParameters(data.apiPath || ''),
            responses: this.generateResponses(data, options),
        };

        // Add request body for POST/PUT/PATCH
        if (['POST', 'PUT', 'PATCH'].includes(endpoint.method)) {
            endpoint.requestBody = this.generateRequestBody(data, options);
        }

        // Add security
        if (data.authentication && data.authentication !== 'none') {
            endpoint.security = [{ [data.authentication]: [] }];
        }

        return endpoint;
    }

    /**
     * Infer path parameters from path string
     */
    private static inferParameters(path: string): OpenAPIParameter[] {
        const params: OpenAPIParameter[] = [];
        const pathParamRegex = /{([^}]+)}/g;
        let match;

        while ((match = pathParamRegex.exec(path)) !== null) {
            params.push({
                name: match[1],
                in: 'path',
                required: true,
                schema: {
                    type: 'string',
                },
            });
        }

        return params;
    }

    /**
     * Generate request body from node data
     */
    private static generateRequestBody(
        data: APINodeData,
        options: OpenAPIGenerationOptions
    ): OpenAPIRequestBody {
        let schema: OpenAPISchema;

        if (data.requestSchema) {
            try {
                schema = JSON.parse(data.requestSchema);
            } catch {
                schema = this.inferSchemaFromString(data.requestSchema);
            }
        } else {
            schema = {
                type: 'object',
                properties: {},
                description: 'Request body schema',
            };
        }

        if (options.includeExamples) {
            schema.example = this.generateExample(schema);
        }

        return {
            description: 'Request body',
            required: true,
            content: {
                'application/json': {
                    schema,
                },
            },
        };
    }

    /**
     * Generate responses from node data
     */
    private static generateResponses(
        data: APINodeData,
        options: OpenAPIGenerationOptions
    ): Record<string, OpenAPIResponse> {
        const responses: Record<string, OpenAPIResponse> = {
            '200': {
                description: 'Successful response',
            },
            '400': {
                description: 'Bad request',
            },
            '401': {
                description: 'Unauthorized',
            },
            '500': {
                description: 'Internal server error',
            },
        };

        // Add response schema for 200
        if (data.responseSchema) {
            let schema: OpenAPISchema;

            try {
                schema = JSON.parse(data.responseSchema);
            } catch {
                schema = this.inferSchemaFromString(data.responseSchema);
            }

            if (options.includeExamples) {
                schema.example = this.generateExample(schema);
            }

            responses['200'].content = {
                'application/json': {
                    schema,
                },
            };
        }

        return responses;
    }

    /**
     * Infer schema from string description
     */
    private static inferSchemaFromString(description: string): OpenAPISchema {
        // Simple inference based on common patterns
        if (description.includes('array') || description.includes('list')) {
            return {
                type: 'array',
                items: {
                    type: 'object',
                    properties: {},
                },
            };
        }

        return {
            type: 'object',
            properties: {},
            description,
        };
    }

    /**
     * Generate example from schema
     */
    private static generateExample(schema: OpenAPISchema): unknown {
        switch (schema.type) {
            case 'string':
                return schema.enum ? schema.enum[0] : 'string';
            case 'number':
                return 42;
            case 'integer':
                return 42;
            case 'boolean':
                return true;
            case 'array':
                return schema.items ? [this.generateExample(schema.items)] : [];
            case 'object':
                if (schema.properties) {
                    const example: Record<string, unknown> = {};
                    for (const [key, propSchema] of Object.entries(schema.properties)) {
                        example[key] = this.generateExample(propSchema);
                    }
                    return example;
                }
                return {};
            default:
                return null;
        }
    }

    /**
     * Get security scheme for authentication type
     */
    private static getSecurityScheme(authType: AuthenticationType): unknown {
        switch (authType) {
            case 'jwt':
                return {
                    type: 'http',
                    scheme: 'bearer',
                    bearerFormat: 'JWT',
                    description: 'JWT bearer token authentication',
                };
            case 'apiKey':
                return {
                    type: 'apiKey',
                    in: 'header',
                    name: 'X-API-Key',
                    description: 'API key authentication',
                };
            case 'oauth2':
                return {
                    type: 'oauth2',
                    flows: {
                        authorizationCode: {
                            authorizationUrl: 'https://example.com/oauth/authorize',
                            tokenUrl: 'https://example.com/oauth/token',
                            scopes: {
                                read: 'Read access',
                                write: 'Write access',
                            },
                        },
                    },
                    description: 'OAuth 2.0 authentication',
                };
            case 'basic':
                return {
                    type: 'http',
                    scheme: 'basic',
                    description: 'Basic HTTP authentication',
                };
            default:
                return undefined;
        }
    }

    /**
     * Convert OpenAPI spec to YAML
     */
    static toYAML(spec: OpenAPISpec): string {
        return yaml.dump(spec, {
            indent: 2,
            lineWidth: 120,
            noRefs: true,
        });
    }

    /**
     * Convert OpenAPI spec to JSON
     */
    static toJSON(spec: OpenAPISpec, pretty = true): string {
        return JSON.stringify(spec, null, pretty ? 2 : 0);
    }

    /**
     * Validate OpenAPI spec
     */
    static validate(spec: OpenAPISpec): { valid: boolean; errors: string[] } {
        const errors: string[] = [];

        // Basic validation
        if (!spec.openapi) {
            errors.push('Missing openapi version');
        }

        if (!spec.info?.title) {
            errors.push('Missing info.title');
        }

        if (!spec.info?.version) {
            errors.push('Missing info.version');
        }

        if (!spec.paths || Object.keys(spec.paths).length === 0) {
            errors.push('No paths defined');
        }

        // Validate paths
        for (const [path, methods] of Object.entries(spec.paths)) {
            if (!path.startsWith('/')) {
                errors.push(`Path must start with /: ${path}`);
            }

            for (const method of Object.keys(methods)) {
                if (!['get', 'post', 'put', 'delete', 'patch', 'options', 'head'].includes(method)) {
                    errors.push(`Invalid HTTP method: ${method} in ${path}`);
                }
            }
        }

        return {
            valid: errors.length === 0,
            errors,
        };
    }
}
