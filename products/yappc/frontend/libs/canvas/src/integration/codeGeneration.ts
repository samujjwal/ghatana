/**
 * Code Generation Service
 *
 * Generates code scaffolding from canvas nodes using AI.
 * Follows YAPPC_USER_JOURNEYS.md Developer Journey 3.1.
 *
 * @doc.type module
 * @doc.purpose AI-powered code generation from canvas nodes
 * @doc.layer product
 * @doc.pattern Service
 */

import type {
    ServiceNodeData,
    APIEndpointNodeData,
    DatabaseNodeData,
} from './node-types';

// ============================================================================
// TYPE DEFINITIONS
// ============================================================================

export interface CodeGenerationRequest {
    nodeType: 'service' | 'apiEndpoint' | 'database';
    nodeData: ServiceNodeData | APIEndpointNodeData | DatabaseNodeData;
    options: CodeGenerationOptions;
}

export interface CodeGenerationOptions {
    language: 'typescript' | 'java' | 'python' | 'go';
    framework?: string;
    includeTests?: boolean;
    includeDocumentation?: boolean;
    outputFormat?: 'files' | 'zip' | 'inline';
}

export interface GeneratedFile {
    path: string;
    content: string;
    language: string;
    type: 'source' | 'test' | 'config' | 'documentation';
}

export interface CodeGenerationResult {
    success: boolean;
    files: GeneratedFile[];
    summary: string;
    errors?: string[];
}

// ============================================================================
// CODE GENERATION TEMPLATES
// ============================================================================

/**
 * Generate TypeScript service scaffolding
 */
function generateTypeScriptService(data: ServiceNodeData): GeneratedFile[] {
    const serviceName = data.label.replace(/\s+/g, '');
    const serviceNameLower = serviceName.charAt(0).toLowerCase() + serviceName.slice(1);

    const files: GeneratedFile[] = [];

    // Service class
    files.push({
        path: `src/services/${serviceNameLower}.service.ts`,
        language: 'typescript',
        type: 'source',
        content: `/**
 * ${data.label} Service
 *
 * @doc.type class
 * @doc.purpose ${data.description || 'Service implementation'}
 * @doc.layer product
 * @doc.pattern Service
 */

import { prisma } from '../db/client.js';

export class ${serviceName}Service {
    /**
     * List all resources
     */
    async list(options: { limit?: number; offset?: number } = {}): Promise<unknown[]> {
        const { limit = 50, offset = 0 } = options;
        // TODO: Implement list logic
        return [];
    }

    /**
     * Get resource by ID
     */
    async getById(id: string): Promise<unknown | null> {
        // TODO: Implement get logic
        return null;
    }

    /**
     * Create new resource
     */
    async create(data: Record<string, unknown>): Promise<unknown> {
        // TODO: Implement create logic
        throw new Error('Not implemented');
    }

    /**
     * Update resource
     */
    async update(id: string, data: Record<string, unknown>): Promise<unknown> {
        // TODO: Implement update logic
        throw new Error('Not implemented');
    }

    /**
     * Delete resource
     */
    async delete(id: string): Promise<void> {
        // TODO: Implement delete logic
    }
}

export const ${serviceNameLower}Service = new ${serviceName}Service();
`,
    });

    // Route file
    files.push({
        path: `src/routes/${serviceNameLower}.ts`,
        language: 'typescript',
        type: 'source',
        content: `/**
 * ${data.label} API Routes
 *
 * @doc.type module
 * @doc.purpose REST API endpoints for ${data.label}
 * @doc.layer product
 * @doc.pattern Router
 */

import { FastifyInstance } from 'fastify';
import { ${serviceNameLower}Service } from '../services/${serviceNameLower}.service.js';

export default async function ${serviceNameLower}Routes(fastify: FastifyInstance) {
    /**
     * List resources
     */
    fastify.get('/${serviceNameLower}', async (request, reply) => {
        const items = await ${serviceNameLower}Service.list();
        return { data: items };
    });

    /**
     * Get resource by ID
     */
    fastify.get('/${serviceNameLower}/:id', async (request, reply) => {
        const { id } = request.params as { id: string };
        const item = await ${serviceNameLower}Service.getById(id);
        if (!item) {
            reply.code(404);
            return { error: 'Not found' };
        }
        return item;
    });

    /**
     * Create resource
     */
    fastify.post('/${serviceNameLower}', async (request, reply) => {
        const data = request.body as Record<string, unknown>;
        const item = await ${serviceNameLower}Service.create(data);
        reply.code(201);
        return item;
    });

    /**
     * Update resource
     */
    fastify.put('/${serviceNameLower}/:id', async (request, reply) => {
        const { id } = request.params as { id: string };
        const data = request.body as Record<string, unknown>;
        const item = await ${serviceNameLower}Service.update(id, data);
        return item;
    });

    /**
     * Delete resource
     */
    fastify.delete('/${serviceNameLower}/:id', async (request, reply) => {
        const { id } = request.params as { id: string };
        await ${serviceNameLower}Service.delete(id);
        reply.code(204);
    });
}
`,
    });

    // Test file
    files.push({
        path: `src/services/__tests__/${serviceNameLower}.service.test.ts`,
        language: 'typescript',
        type: 'test',
        content: `/**
 * ${data.label} Service Tests
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ${serviceName}Service } from '../${serviceNameLower}.service.js';

describe('${serviceName}Service', () => {
    let service: ${serviceName}Service;

    beforeEach(() => {
        service = new ${serviceName}Service();
    });

    describe('list', () => {
        it('should return an array', async () => {
            const result = await service.list();
            expect(Array.isArray(result)).toBe(true);
        });
    });

    describe('getById', () => {
        it('should return null for non-existent ID', async () => {
            const result = await service.getById('non-existent');
            expect(result).toBeNull();
        });
    });

    // TODO: Add more tests
});
`,
    });

    return files;
}

/**
 * Generate API endpoint scaffolding
 */
function generateAPIEndpoint(data: APIEndpointNodeData): GeneratedFile[] {
    const endpointName = data.path.split('/').filter(Boolean).pop()?.replace(/[^a-zA-Z]/g, '') || 'resource';

    const files: GeneratedFile[] = [];

    // OpenAPI spec
    files.push({
        path: `openapi/${endpointName}.yaml`,
        language: 'yaml',
        type: 'documentation',
        content: `# ${data.label} API Specification
openapi: 3.0.3
info:
  title: ${data.label} API
  version: 1.0.0

paths:
  ${data.path}:
    ${data.method.toLowerCase()}:
      summary: ${data.label}
      operationId: ${data.method.toLowerCase()}${endpointName.charAt(0).toUpperCase() + endpointName.slice(1)}
      responses:
        '200':
          description: Successful response
          content:
            application/json:
              schema:
                type: object
        '400':
          description: Bad request
        '404':
          description: Not found
        '500':
          description: Internal server error
`,
    });

    // DTO file
    files.push({
        path: `src/dto/${endpointName}.dto.ts`,
        language: 'typescript',
        type: 'source',
        content: `/**
 * ${data.label} DTOs
 */

export interface ${endpointName.charAt(0).toUpperCase() + endpointName.slice(1)}Request {
    // TODO: Define request schema
}

export interface ${endpointName.charAt(0).toUpperCase() + endpointName.slice(1)}Response {
    // TODO: Define response schema
}
`,
    });

    return files;
}

/**
 * Generate Prisma schema from database node
 */
function generateDatabaseSchema(data: DatabaseNodeData): GeneratedFile[] {
    const files: GeneratedFile[] = [];

    if (!data.schema?.tables) {
        return files;
    }

    let prismaModels = '';
    for (const table of data.schema.tables) {
        prismaModels += `
model ${table.name.charAt(0).toUpperCase() + table.name.slice(1)} {
${table.columns.map((col) => `    ${col.name} ${mapToPrismaType(col.type)}${col.nullable === false ? '' : '?'}`).join('\n')}
}
`;
    }

    files.push({
        path: `prisma/schema.prisma`,
        language: 'prisma',
        type: 'config',
        content: `// Generated Prisma Schema for ${data.label}
// Database: ${data.engine}

generator client {
    provider = "prisma-client-js"
}

datasource db {
    provider = "${mapEngineToPrismaProvider(data.engine)}"
    url      = env("DATABASE_URL")
}
${prismaModels}`,
    });

    return files;
}

/**
 * Map column type to Prisma type
 */
function mapToPrismaType(type: string): string {
    const typeMap: Record<string, string> = {
        uuid: 'String @id @default(uuid())',
        varchar: 'String',
        text: 'String',
        int: 'Int',
        integer: 'Int',
        boolean: 'Boolean',
        timestamp: 'DateTime',
        date: 'DateTime',
        json: 'Json',
        float: 'Float',
        decimal: 'Decimal',
    };
    return typeMap[type.toLowerCase()] || 'String';
}

/**
 * Map database engine to Prisma provider
 */
function mapEngineToPrismaProvider(engine: DatabaseNodeData['engine']): string {
    const providerMap: Record<string, string> = {
        postgres: 'postgresql',
        mysql: 'mysql',
        mongodb: 'mongodb',
        redis: 'postgresql', // Redis not directly supported, fallback
        dynamodb: 'postgresql', // DynamoDB not directly supported, fallback
    };
    return providerMap[engine] || 'postgresql';
}

// ============================================================================
// MAIN CODE GENERATION SERVICE
// ============================================================================

/**
 * Generate code from a canvas node
 */
export async function generateCodeFromNode(
    request: CodeGenerationRequest
): Promise<CodeGenerationResult> {
    const { nodeType, nodeData, options } = request;

    try {
        let files: GeneratedFile[] = [];

        switch (nodeType) {
            case 'service':
                files = generateTypeScriptService(nodeData as ServiceNodeData);
                break;
            case 'apiEndpoint':
                files = generateAPIEndpoint(nodeData as APIEndpointNodeData);
                break;
            case 'database':
                files = generateDatabaseSchema(nodeData as DatabaseNodeData);
                break;
            default:
                return {
                    success: false,
                    files: [],
                    summary: `Unknown node type: ${nodeType}`,
                    errors: [`Unsupported node type: ${nodeType}`],
                };
        }

        return {
            success: true,
            files,
            summary: `Generated ${files.length} files for ${nodeData.label}`,
        };
    } catch (error) {
        return {
            success: false,
            files: [],
            summary: 'Code generation failed',
            errors: [error instanceof Error ? error.message : 'Unknown error'],
        };
    }
}

/**
 * Generate code for multiple connected nodes
 */
export async function generateCodeFromFlow(
    nodes: Array<{ type: string; data: ServiceNodeData | APIEndpointNodeData | DatabaseNodeData }>,
    options: CodeGenerationOptions
): Promise<CodeGenerationResult> {
    const allFiles: GeneratedFile[] = [];
    const errors: string[] = [];

    for (const node of nodes) {
        const result = await generateCodeFromNode({
            nodeType: node.type as 'service' | 'apiEndpoint' | 'database',
            nodeData: node.data,
            options,
        });

        if (result.success) {
            allFiles.push(...result.files);
        } else if (result.errors) {
            errors.push(...result.errors);
        }
    }

    return {
        success: errors.length === 0,
        files: allFiles,
        summary: `Generated ${allFiles.length} files from ${nodes.length} nodes`,
        errors: errors.length > 0 ? errors : undefined,
    };
}
