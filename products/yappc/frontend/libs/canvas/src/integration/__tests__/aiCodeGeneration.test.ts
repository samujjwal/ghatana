/**
 * AI Code Generation Integration Tests
 * 
 * Tests for AI-powered code generation from persona nodes.
 * Verifies prompt building, LLM integration, and code extraction.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AICodeGenerationService, createAICodeGenerationService } from '../aiCodeGeneration';
import type { IAIService, CompletionResult, AIProvider, AIServiceConfig, CompletionChunk, CompletionOptions, EmbeddingResponse } from '@ghatana/yappc-ai/core';
import type { ServiceNodeData, DatabaseNodeData, APIEndpointNodeData } from '../../components/PersonaNodes';

// Mock AI Service
class MockAIService implements IAIService {
    provider: AIProvider;
    config: AIServiceConfig;
    stream(prompt: string, options?: CompletionOptions): AsyncIterableIterator<CompletionChunk> {
        throw new Error('Method not implemented.');
    }
    embed(text: string, model?: string): Promise<EmbeddingResponse> {
        throw new Error('Method not implemented.');
    }
    getTokenCount(text: string, model?: string): number {
        throw new Error('Method not implemented.');
    }
    healthCheck(): Promise<boolean> {
        throw new Error('Method not implemented.');
    }
    async complete(prompt: string, options?: unknown): Promise<CompletionResult> {
        // Simulate different responses based on prompt content
        if (prompt.includes('TypeScript service')) {
            return {
                content: '```typescript\nexport class UserService {\n  async getUser(id: string) {\n    return { id, name: "Test" };\n  }\n}\n```',
                usage: { promptTokens: 100, completionTokens: 50, totalTokens: 150 },
                model: 'gpt-4',
            };
        }

        if (prompt.includes('Prisma schema')) {
            return {
                content: '```prisma\nmodel User {\n  id String @id @default(uuid())\n  email String @unique\n  name String\n}\n```',
                usage: { promptTokens: 80, completionTokens: 40, totalTokens: 120 },
                model: 'gpt-4',
            };
        }

        if (prompt.includes('API route')) {
            return {
                content: '```typescript\napp.get("/api/users/:id", async (req, res) => {\n  const user = await userService.getUser(req.params.id);\n  res.json(user);\n});\n```',
                usage: { promptTokens: 90, completionTokens: 45, totalTokens: 135 },
                model: 'gpt-4',
            };
        }

        return {
            content: 'Generated code',
            usage: { promptTokens: 50, completionTokens: 25, totalTokens: 75 },
            model: 'gpt-4',
        };
    }
}

describe('AICodeGenerationService', () => {
    let aiService: IAIService;
    let codeGenService: AICodeGenerationService;

    beforeEach(() => {
        aiService = new MockAIService();
        codeGenService = createAICodeGenerationService(aiService);
    });

    describe('generateService', () => {
        it('should generate TypeScript service code', async () => {
            const serviceNode: ServiceNodeData = {
                label: 'UserService',
                type: 'service',
                persona: 'developer',
                framework: 'fastify',
                config: { port: 3000, replicas: 2, cpu: '500m', memory: '512Mi' },
            };

            const result = await codeGenService.generateService(serviceNode);

            expect(result.success).toBe(true);
            expect(result.files).toHaveLength(1);
            expect(result.files[0].path).toContain('userservice.service.ts');
            expect(result.files[0].content).toContain('class UserService');
            expect(result.files[0].language).toBe('typescript');
            expect(result.summary).toContain('UserService');
        });

        it('should include tests when requested', async () => {
            const serviceNode: ServiceNodeData = {
                label: 'UserService',
                type: 'service',
                persona: 'developer',
                framework: 'fastify',
            };

            const result = await codeGenService.generateService(serviceNode, {
                includeTests: true,
            });

            expect(result.success).toBe(true);
            expect(result.files.length).toBeGreaterThanOrEqual(2);

            const testFile = result.files.find(f => f.path.includes('.test.ts'));
            expect(testFile).toBeDefined();
            expect(testFile?.type).toBe('test');
        });

        it('should handle generation errors gracefully', async () => {
            const errorAIService: IAIService = {
                async complete() {
                    throw new Error('AI service unavailable');
                },
            };

            const errorCodeGenService = createAICodeGenerationService(errorAIService);

            const serviceNode: ServiceNodeData = {
                label: 'FailService',
                type: 'service',
                persona: 'developer',
                framework: 'fastify',
            };

            const result = await errorCodeGenService.generateService(serviceNode);

            expect(result.success).toBe(false);
            expect(result.errors).toBeDefined();
            expect(result.errors?.[0]).toContain('AI service unavailable');
        });
    });

    describe('generateDatabaseSchema', () => {
        it('should generate Prisma schema', async () => {
            const dbNode: DatabaseNodeData = {
                label: 'UserDB',
                type: 'database',
                persona: 'architect',
                engine: 'postgres',
                schema: {
                    tables: [
                        {
                            name: 'users',
                            columns: [
                                { name: 'id', type: 'uuid', nullable: false },
                                { name: 'email', type: 'varchar', nullable: false },
                            ],
                        },
                    ],
                },
            };

            const result = await codeGenService.generateDatabaseSchema(dbNode);

            expect(result.success).toBe(true);
            expect(result.files).toHaveLength(1);
            expect(result.files[0].content).toContain('model User');
            expect(result.files[0].language).toBe('prisma');
        });

        it('should handle different database engines', async () => {
            const engines: Array<'postgres' | 'mysql' | 'mongodb' | 'redis' | 'dynamodb'> = [
                'postgres',
                'mysql',
                'mongodb',
            ];

            for (const engine of engines) {
                const dbNode: DatabaseNodeData = {
                    label: `TestDB`,
                    type: 'database',
                    persona: 'architect',
                    engine,
                    schema: { tables: [] },
                };

                const result = await codeGenService.generateDatabaseSchema(dbNode);
                expect(result.success).toBe(true);
            }
        });
    });

    describe('generateAPIRoute', () => {
        it('should generate Fastify route', async () => {
            const apiNode: APIEndpointNodeData = {
                label: 'GetUser',
                type: 'apiEndpoint',
                persona: 'developer',
                method: 'GET',
                path: '/api/users/:id',
                description: 'Get user by ID',
                requestSchema: { params: { id: 'uuid' } },
                responseSchema: { user: 'User' },
            };

            const result = await codeGenService.generateAPIRoute(apiNode);

            expect(result.success).toBe(true);
            expect(result.files).toHaveLength(1);
            expect(result.files[0].content).toBeDefined();
            expect(result.files[0].language).toBe('typescript');
        });

        it('should support different HTTP methods', async () => {
            const methods: Array<'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'> = [
                'GET',
                'POST',
                'PUT',
                'DELETE',
            ];

            for (const method of methods) {
                const apiNode: APIEndpointNodeData = {
                    label: `${method}Endpoint`,
                    type: 'apiEndpoint',
                    persona: 'developer',
                    method,
                    path: '/api/test',
                };

                const result = await codeGenService.generateAPIRoute(apiNode);
                expect(result.success).toBe(true);
            }
        });
    });

    describe('generateTests', () => {
        it('should generate unit tests', async () => {
            const code = 'export class Calculator { add(a: number, b: number) { return a + b; } }';

            const result = await codeGenService.generateTests(code, 'unit');

            expect(result).toBeDefined();
            expect(typeof result).toBe('string');
            expect(result.length).toBeGreaterThan(0);
        });

        it('should generate integration tests', async () => {
            const code = 'app.get("/api/test", handler);';

            const result = await codeGenService.generateTests(code, 'integration');

            expect(result).toBeDefined();
            expect(typeof result).toBe('string');
        });

        it('should generate e2e tests', async () => {
            const code = 'const app = express();';

            const result = await codeGenService.generateTests(code, 'e2e');

            expect(result).toBeDefined();
            expect(typeof result).toBe('string');
        });
    });

    describe('code extraction', () => {
        it('should extract code from markdown code blocks', async () => {
            const serviceNode: ServiceNodeData = {
                label: 'TestService',
                type: 'service',
                persona: 'developer',
                framework: 'fastify',
            };

            const result = await codeGenService.generateService(serviceNode);

            expect(result.files[0].content).not.toContain('```typescript');
            expect(result.files[0].content).not.toContain('```');
        });
    });

    describe('token tracking', () => {
        it('should track token usage', async () => {
            const serviceNode: ServiceNodeData = {
                label: 'TokenTest',
                type: 'service',
                persona: 'developer',
                framework: 'fastify',
            };

            const result = await codeGenService.generateService(serviceNode);

            expect(result.summary).toContain('tokens');
        });
    });
});
